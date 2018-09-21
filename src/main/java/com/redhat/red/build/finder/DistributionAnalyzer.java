/*
 * Copyright (C) 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.red.build.finder;

import static com.redhat.red.build.finder.AnsiUtils.green;
import static com.redhat.red.build.finder.AnsiUtils.red;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class DistributionAnalyzer implements Callable<Map<String, Collection<String>>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DistributionAnalyzer.class);

    /**
     * Ideally we should be able to use FileSystemManager::canCreateFileSystem but that relies on
     * an accurate extension map in providers.xml. Either fix that up manually or exclude non-viable
     * archive schemes. Further without overriding the URLConnection.getFileNameMap the wrong results
     * will be returned for zip/gz which is classified as application/octet-stream.
     */
    private static final List<String> NON_ARCHIVE_SCHEMES = Collections.unmodifiableList(Arrays.asList("tmp", "res", "ram", "file"));

    private static final String CHECKSUMS_FILENAME_BASENAME = "checksums-";

    private final List<File> files;

    private MultiValuedMap<String, String> map;

    private StandardFileSystemManager sfs;

    private String root;

    private BuildConfig config;

    private BlockingQueue<Checksum> queue;

    private Cache<String, MultiValuedMap<String, String>> fileCache;

    private EmbeddedCacheManager cacheManager;

    private AtomicInteger level;

    private ExecutorService pool;

    public DistributionAnalyzer(List<File> files, BuildConfig config) {
        this(files, config, null);
    }

    public DistributionAnalyzer(List<File> files, BuildConfig config, EmbeddedCacheManager cacheManager) {
        this.files = files;
        this.config = config;
        this.map = new ArrayListValuedHashMap<>();

        this.cacheManager = cacheManager;

        if (cacheManager != null) {
            this.fileCache = cacheManager.getCache("files");
        }

        this.level = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public MultiValuedMap<String, String> checksumFiles() throws IOException {
        final Instant startTime = Instant.now();
        sfs = new StandardFileSystemManager();

        sfs.init();

        try {
            for (File file : files) {
                try (FileObject fo = sfs.resolveFile(file.getAbsolutePath())) {
                    MultiValuedMap<String, String> map = null;
                    String value = null;

                    if (cacheManager != null) {
                        Checksum checksum = new Checksum(fo, config.getChecksumType().getAlgorithm(), root);
                        value = checksum.getValue();
                        map = fileCache.get(value);

                        if (map != null) {
                            this.map.putAll(map);
                            LOGGER.info("Loaded checksums for file {} (checksum {}) from cache", green(file.getName()), green(value));
                        } else {
                            LOGGER.info("File {} (checksum {}) not found in cache", green(file.getName()), green(value));
                        }
                    }

                    if (map == null) {
                        LOGGER.info("Finding checksums for file: {}", green(file.getName()));
                        root = fo.getName().getFriendlyURI().substring(0, fo.getName().getFriendlyURI().indexOf(fo.getName().getBaseName()));
                        map = new ArrayListValuedHashMap<>();

                        listChildren(fo, map);

                        if (cacheManager != null) {
                            fileCache.put(value, map);
                        }

                        if (this.map == null) {
                            this.map = map;
                        } else {
                            this.map.putAll(map);
                        }
                    }
                }
            }
        } finally {
            sfs.close();
        }

        final Instant endTime = Instant.now();
        final Duration duration = Duration.between(startTime, endTime).abs();
        final int numChecksums = map.size();

        LOGGER.info("Total number of checksums: {}, time: {}, average: {}", green(numChecksums), green(duration), green(numChecksums > 0D ? duration.dividedBy(numChecksums) : 0D));

        return MultiMapUtils.unmodifiableMultiValuedMap(map);
    }

    private boolean isArchive(FileObject fo) {
        return (!NON_ARCHIVE_SCHEMES.contains(fo.getName().getExtension()) && Stream.of(sfs.getSchemes()).anyMatch(s -> s.equals(fo.getName().getExtension())));
    }

    private boolean shouldListArchive(FileObject fo) throws FileSystemException {
        if (!config.getDisableRecursion()) {
            return true;
        }

        if (level.intValue() == 1 || level.intValue() == 2 && fo.getParent().isFolder() && fo.getParent().getName().toString().endsWith("!/") && fo.getParent().getChildren().length == 1) {
            return true;
        }

        return false;
    }

    private void listArchive(FileObject fo, MultiValuedMap<String, String> map) throws IOException {
        LOGGER.debug("Creating file system for: {}", Utils.normalizePath(fo, root));

        FileObject layered = null;

        try {
            layered = sfs.createFileSystem(fo.getName().getExtension(), fo);
            listChildren(layered, map);
        } catch (FileSystemException e) {
            // TODO: store checksum/filename/error so that we can flag the file
            LOGGER.warn("Unable to process archive/compressed file: {}: {}", red(Utils.normalizePath(fo, root)), red(e.getMessage()));
            LOGGER.debug("Error", e);
        } finally {
            if (layered != null) {
                sfs.closeFileSystem(layered.getFileSystem());
                layered.close();
            }
        }
    }

    private boolean includeFile(FileObject fo) {
        boolean excludeExtension = config.getArchiveExtensions() != null && !config.getArchiveExtensions().isEmpty() && !config.getArchiveExtensions().stream().anyMatch(x -> x.equals(fo.getName().getExtension()));
        boolean excludeFile = false;

        if (!excludeExtension) {
            String friendlyURI = fo.getName().getFriendlyURI();
            excludeFile = config.getExcludes() != null && !config.getExcludes().isEmpty() && config.getExcludes().stream().map(Pattern::pattern).anyMatch(friendlyURI::matches);
        }

        boolean include = (!excludeFile && !excludeExtension);

        return include;
    }

    private Callable<Checksum> checksumTask(FileObject fo) {
        return new Callable<Checksum>() {
            public Checksum call() throws IOException {
                Checksum checksum = new Checksum(fo, config.getChecksumType().getAlgorithm(), root);
                return checksum;
            }
        };
    }

    private void listChildren(FileObject fo, MultiValuedMap<String, String> map) throws IOException {
        if (fo.isFile()) {
            if (isArchive(fo)) {
                level.incrementAndGet();

                if (shouldListArchive(fo)) {
                    listArchive(fo, map);
                }

                level.decrementAndGet();
            }

            if (includeFile(fo)) {
                Checksum checksum = new Checksum(fo, config.getChecksumType().getAlgorithm(), root);
                String value = checksum.getValue();
                String filename = checksum.getFilename();

                LOGGER.debug("Checksum: {} {}", value, filename);

                map.put(value, filename);

                if (queue != null) {
                    try {
                        queue.put(checksum);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } else {
            List<FileObject> children = Arrays.asList(fo.getChildren());
            List<FileObject> folders = new ArrayList<>();
            Collection<Future<Checksum>> futures = new ArrayList<>();
            List<Callable<Checksum>> tasks = new ArrayList<>();

            for (FileObject child : children) {
                if (child.isFile() && !isArchive(child)) {
                    if (includeFile(child)) {
                        tasks.add(checksumTask(child));
                    }
                } else {
                    folders.add(child);
                }
            }

            try {
                futures = pool.invokeAll(tasks);

                for (Future<Checksum> future : futures) {
                    Checksum checksum2 = future.get();
                    String value = checksum2.getValue();
                    String filename = checksum2.getFilename();

                    LOGGER.debug("Checksum: {} {}", value, filename);

                    map.put(value, filename);

                    checksum2.getFileObject().close();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                Throwable t = e.getCause();

                if (t instanceof FileSystemException) {
                    throw new FileSystemException(t);
                } else if (t instanceof IOException) {
                    throw new IOException(t);
                } else {
                    LOGGER.warn("Got unhandled exception", e);
                }
            }

            for (FileObject archive : folders) {
                try {
                    listChildren(archive, map);
                } finally {
                    archive.close();
                }
            }
        }
    }

    public File getChecksumFile(File outputDirectory) {
        return new File(outputDirectory, CHECKSUMS_FILENAME_BASENAME + config.getChecksumType() + ".json");
    }

    public void outputToFile(File outputDirectory) throws JsonGenerationException, JsonMappingException, IOException {
        outputDirectory.mkdirs();

        JSONUtils.dumpObjectToFile(getChecksums(), getChecksumFile(outputDirectory));
    }

    public Map<String, Collection<String>> getChecksums() {
        return Collections.unmodifiableMap(map.asMap());
    }

    @Override
    public Map<String, Collection<String>> call() throws IOException {
        queue = new LinkedBlockingQueue<>();

        checksumFiles();

        try {
            queue.put(new Checksum());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return Collections.unmodifiableMap(map.asMap());
    }

    public BlockingQueue<Checksum> getQueue() {
        return queue;
    }
}
