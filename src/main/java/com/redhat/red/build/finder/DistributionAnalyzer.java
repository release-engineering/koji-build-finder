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
import org.apache.commons.vfs2.AllFileSelector;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystem;
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
            this.fileCache = cacheManager.getCache("files-" + config.getChecksumType());
        }

        this.level = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public MultiValuedMap<String, String> checksumFiles() throws IOException {
        Instant startTime = Instant.now();
        FileObject fo = null;
        sfs = new StandardFileSystemManager();

        try {
            sfs.init();

            for (File file : files) {
                    fo = sfs.resolveFile(file.getAbsolutePath());
                    root = fo.getName().getFriendlyURI().substring(0, fo.getName().getFriendlyURI().indexOf(fo.getName().getBaseName()));
                    MultiValuedMap<String, String> map = null;
                    String value = null;

                    if (cacheManager != null) {
                        Checksum fileChecksum = new Checksum(fo, config.getChecksumType().getAlgorithm(), root);
                        value = fileChecksum.getValue();
                        map = fileCache.get(value);

                        if (map != null) {
                            this.map.putAll(map);

                            if (queue != null) {
                                map.entries().forEach(entry -> {
                                    try {
                                        Checksum checksum = new Checksum(entry.getKey(), entry.getValue());
                                        queue.put(checksum);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                });
                            }

                            LOGGER.info("Loaded {} checksums for file: {} (checksum: {}) from cache", green(map.keySet().size()), green(file.getName()), green(value));
                        } else {
                            LOGGER.info("File: {} (checksum: {}) not found in cache", green(file.getName()), green(value));
                        }
                    }

                    if (map == null) {
                        LOGGER.info("Finding checksums for file: {}", green(file.getName()));
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
        } finally {
            if (fo != null) {
                fo.close();
            }

            sfs.close();

            Utils.shutdownAndAwaitTermination(pool);
        }

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime).abs();
        int numChecksums = map.size();

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
        FileSystem fs = null;

        try {
            layered = sfs.createFileSystem(fo.getName().getExtension(), fo);
            fs = layered.getFileSystem();
            listChildren(layered, map);
        } catch (IOException e) {
            // TODO: store checksum/filename/error so that we can flag the file
            LOGGER.warn("Unable to process archive/compressed file: {}: {}", red(Utils.normalizePath(fo, root)), red(e.getMessage()));
            LOGGER.debug("Error", e);
        } finally {
            if (fs != null) {
                sfs.closeFileSystem(fs);
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
                LOGGER.debug("{}:{} {}", checksum.getAlgorithm(), checksum.getValue(), checksum.getFilename());
                return checksum;
            }
        };
    }

    private void handleFutureChecksum(Future<Checksum> future, MultiValuedMap<String, String> map) throws IOException {
        try {
            Checksum checksum = future.get();
            String value = checksum.getValue();
            String filename = checksum.getFilename();

            map.put(value, filename);

            if (queue != null) {
                try {
                    queue.put(checksum);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new IOException(e);
        }
    }

    private void listChildren(FileObject fo, MultiValuedMap<String, String> map) throws IOException {
        FileObject[] files = null;

        try {
            files = fo.findFiles(new AllFileSelector());

            int numChildren = files.length;
            Collection<Future<Checksum>> futures = new ArrayList<>(numChildren);
            List<Callable<Checksum>> tasks = new ArrayList<>(numChildren);

            for (FileObject file : files) {
                if (file.isFile()) {
                    if (isArchive(file)) {
                        level.incrementAndGet();

                        if (shouldListArchive(file)) {
                            listArchive(file, map);
                        }

                        level.decrementAndGet();
                    }

                    if (includeFile(file)) {
                        if (!file.getName().getScheme().equals("tar")) {
                            tasks.add(checksumTask(file));
                        } else {
                            Future<Checksum> future = pool.submit(checksumTask(file));
                            handleFutureChecksum(future, map);
                        }
                    }
                }
            }

            int tasksSize = tasks.size();

            if (tasksSize > 0) {
                LOGGER.debug("Number of checksum tasks: {}", tasksSize);

                try {
                    futures = pool.invokeAll(tasks);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                for (Future<Checksum> future : futures) {
                    handleFutureChecksum(future, map);
                }
            }
        } finally {
            if (files != null) {
                for (FileObject file : files) {
                    file.close();
                }
            }
        }
    }

    public File getChecksumFile(File outputDirectory) {
        return new File(outputDirectory, CHECKSUMS_FILENAME_BASENAME + config.getChecksumType() + ".json");
    }

    public void outputToFile(File outputDirectory) throws JsonGenerationException, JsonMappingException, IOException {
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
