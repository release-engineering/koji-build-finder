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
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.vfs2.AllFileSelector;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;

public class DistributionAnalyzer implements Callable<Map<KojiChecksumType, MultiValuedMap<String, String>>> {
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

    private Map<KojiChecksumType, MultiValuedMap<String, String>> map;

    private MultiValuedMap<String, Checksum> inverseMap;

    private StandardFileSystemManager sfs;

    private String root;

    private BuildConfig config;

    private BlockingQueue<Checksum> queue;

    private Map<KojiChecksumType, Cache<String, MultiValuedMap<String, String>>> fileCaches;

    private EmbeddedCacheManager cacheManager;

    private AtomicInteger level;

    private ExecutorService pool;

    private Set<KojiChecksumType> checksumTypesToCheck;

    private Set<String> filesInError;

    public DistributionAnalyzer(List<File> files, BuildConfig config) {
        this(files, config, null);
    }

    public DistributionAnalyzer(List<File> files, BuildConfig config, EmbeddedCacheManager cacheManager) {
        this.files = files;
        this.config = config;
        this.checksumTypesToCheck = new HashSet<>(config.getChecksumTypes());

        this.map = new EnumMap<>(KojiChecksumType.class);

        checksumTypesToCheck.forEach(checksumType -> this.map.put(checksumType, new HashSetValuedHashMap<>()));

        this.inverseMap = new HashSetValuedHashMap<>();

        this.cacheManager = cacheManager;

        this.fileCaches = new EnumMap<>(KojiChecksumType.class);

        checksumTypesToCheck.forEach(checksumType -> {
            if (cacheManager != null) {
                this.fileCaches.put(checksumType, cacheManager.getCache("files-" + checksumType));
            }
        });

        this.level = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.filesInError = new HashSet<>();
    }

    public Map<KojiChecksumType, MultiValuedMap<String, String>> checksumFiles() throws IOException {
        Instant startTime = Instant.now();
        FileObject fo = null;
        sfs = new StandardFileSystemManager();

        try {
            sfs.init();

            for (File file : files) {
                    fo = sfs.resolveFile(file.getAbsolutePath());
                    root = fo.getName().getFriendlyURI().substring(0, fo.getName().getFriendlyURI().indexOf(fo.getName().getBaseName()));
                    Set<Checksum> fileChecksums = cacheManager != null ? Checksum.checksum(fo, checksumTypesToCheck, root) : null;

                    if (cacheManager != null) {
                        Iterator<KojiChecksumType> it = checksumTypesToCheck.iterator();

                        while (it.hasNext()) {
                            KojiChecksumType checksumType = it.next();
                            String value = Checksum.findByType(fileChecksums, checksumType).getValue();
                            MultiValuedMap<String, String> localMap = fileCaches.get(checksumType).get(value);

                            if (localMap != null) {
                                this.map.get(checksumType).putAll(localMap);

                                localMap.entries().forEach(entry -> inverseMap.put(entry.getValue(), new Checksum(checksumType, entry.getKey(), entry.getValue())));

                                if (queue != null && checksumType.equals(KojiChecksumType.md5)) {
                                    localMap.entries().forEach(entry -> {
                                        try {
                                            Checksum checksum = new Checksum(checksumType, entry.getKey(), entry.getValue());
                                            queue.put(checksum);
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                    });
                                }

                                it.remove();

                                LOGGER.info("Loaded {} checksums for file: {} (checksum: {}) from cache", green(localMap.size()), green(file.getName()), green(value));
                            } else {
                                LOGGER.info("File: {} (checksum: {}) not found in cache", green(file.getName()), green(value));
                            }
                        }
                    }

                    if (!checksumTypesToCheck.isEmpty()) {
                        LOGGER.info("Finding checksums: {} for file: {}", green(String.join(", ", checksumTypesToCheck.stream().map(String::valueOf).collect(Collectors.toSet()))), green(file.getName()));

                        listChildren(fo);

                        if (cacheManager != null) {
                            checksumTypesToCheck.forEach(checksumType -> fileCaches.get(checksumType).put(Checksum.findByType(fileChecksums, checksumType).getValue(), map.get(checksumType)));
                        }
                    }
            }
        } finally {
            if (fo != null) {
                fo.close();
            }

            sfs.close();

            // XXX: <https://issues.apache.org/jira/browse/VFS-634>
            String tmpDir = System.getProperty("java.io.tmpdir");

            if (tmpDir != null) {
                File vfsCacheDir = new File(tmpDir, "vfs_cache");

                FileUtils.deleteQuietly(vfsCacheDir);
            }

            Utils.shutdownAndAwaitTermination(pool);
        }

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime).abs();

        int numChecksums = map.values().iterator().next().size();

        LOGGER.info("Total number of checksums: {}, time: {}, average: {}", green(numChecksums), green(duration), green(numChecksums > 0D ? duration.dividedBy(numChecksums) : 0D));

        return Collections.unmodifiableMap(map);
    }

    private boolean isArchive(FileObject fo) {
        return (!NON_ARCHIVE_SCHEMES.contains(fo.getName().getExtension()) && Stream.of(sfs.getSchemes()).anyMatch(s -> s.equals(fo.getName().getExtension())));
    }

    private static boolean isJar(FileObject fo) {
        String ext = fo.getName().getExtension();

        return ext.equals("jar") || ext.equals("war") || ext.equals("rar") || ext.equals("ear") || ext.equals("sar") || ext.equals("kar") || ext.equals("jdocbook") || ext.equals("jdocbook-style") || ext.equals("plugin");
    }

    private boolean shouldListArchive(FileObject fo) throws FileSystemException {
        if (!config.getDisableRecursion()) {
            return true;
        }

        return !isJar(fo) && level.intValue() == 1 || level.intValue() == 2 && fo.getParent().isFolder() && fo.getParent().getName().toString().endsWith("!/") && fo.getParent().getChildren().length == 1;
    }

    private void listArchive(FileObject fo) throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Creating file system for: {}", Utils.normalizePath(fo, root));
        }

        FileObject layered = null;
        FileSystem fs = null;

        try {
            layered = sfs.createFileSystem(fo.getName().getExtension(), fo);
            fs = layered.getFileSystem();

            listChildren(layered);
        } catch (IOException e) {
            String filename = Utils.normalizePath(fo, root);

            filesInError.add(filename);

            LOGGER.warn("Unable to process archive/compressed file: {}: {}", red(filename), red(e.getMessage()));
            LOGGER.debug("Error", e);
        } finally {
            if (fs != null) {
                sfs.closeFileSystem(fs);
            }
        }
    }

    private boolean includeFile(FileObject fo) {
        boolean excludeExtension = config.getArchiveExtensions() != null && !config.getArchiveExtensions().isEmpty() && config.getArchiveExtensions().stream().noneMatch(x -> x.equals(fo.getName().getExtension())) && !fo.getName().getExtension().equals("rpm");
        boolean excludeFile = false;

        if (!excludeExtension) {
            String friendlyURI = fo.getName().getFriendlyURI();
            excludeFile = config.getExcludes() != null && !config.getExcludes().isEmpty() && config.getExcludes().stream().map(Pattern::pattern).anyMatch(friendlyURI::matches);
        }

        boolean include = (!excludeFile && !excludeExtension);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Include {}: {}", Utils.normalizePath(fo, root), include);
        }

        return include;
    }

    private Callable<Set<Checksum>> checksumTask(FileObject fo) {
        return new Callable<Set<Checksum>>() {
            public Set<Checksum> call() throws IOException {
                return Checksum.checksum(fo, checksumTypesToCheck, root);
            }
        };
    }

    private void handleFutureChecksum(Future<Set<Checksum>> future) throws IOException {
        try {
            Set<Checksum> checksums = future.get();

            checksumTypesToCheck.forEach(checksumType ->  {
                Checksum checksum = Checksum.findByType(checksums, checksumType);
                map.get(checksumType).put(checksum.getValue(), checksum.getFilename());
            });

            checksums.forEach(checksum -> inverseMap.put(checksum.getFilename(), checksum));

            if (queue != null && config.getChecksumTypes().contains(KojiChecksumType.md5)) {
                try {
                    for (Checksum checksum : checksums) {
                        if (checksum.getType().equals(KojiChecksumType.md5)) {
                            queue.put(checksum);
                        }
                    }
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

    private void listChildren(FileObject fo) throws IOException {
        List<FileObject> localFiles = new ArrayList<>();

        try {
            fo.findFiles(new AllFileSelector(), true, localFiles);
            int numChildren = localFiles.size();
            Collection<Future<Set<Checksum>>> futures = new ArrayList<>(numChildren);
            List<Callable<Set<Checksum>>> tasks = new ArrayList<>(numChildren);

            for (FileObject file : localFiles) {
                if (file.isFile()) {
                    if (includeFile(file)) {
                        if (!file.getName().getScheme().equals("tar")) {
                            tasks.add(checksumTask(file));
                        } else {
                            Future<Set<Checksum>> future = pool.submit(checksumTask(file));
                            handleFutureChecksum(future);
                        }
                    }

                    if (isArchive(file)) {
                        level.incrementAndGet();

                        if (shouldListArchive(file)) {
                            listArchive(file);
                        }

                        level.decrementAndGet();
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

                for (Future<Set<Checksum>> future : futures) {
                    handleFutureChecksum(future);
                }
            }
        } finally {
            for (FileObject file : localFiles) {
                file.close();
            }
        }
    }

    public File getChecksumFile(KojiChecksumType checksumType, File outputDirectory) {
        return new File(outputDirectory, CHECKSUMS_FILENAME_BASENAME + checksumType + ".json");
    }

    public void outputToFile(KojiChecksumType checksumType, File outputDirectory) throws IOException {
        JSONUtils.dumpObjectToFile(getChecksums(checksumType), getChecksumFile(checksumType, outputDirectory));
    }

    public void setFiles(MultiValuedMap<String, Checksum> inverseMap) {
        this.inverseMap = inverseMap;
    }

    public MultiValuedMap<String, Checksum> getFiles() {
        return MultiMapUtils.unmodifiableMultiValuedMap(inverseMap);
    }

    public void setChecksums(Map<KojiChecksumType, MultiValuedMap<String, String>> map) {
        this.map = map;
    }

    public Map<String, Collection<String>> getChecksums(KojiChecksumType checksumType) {
        return Collections.unmodifiableMap(map.get(checksumType).asMap());
    }

    public Collection<String> getFilesInError() {
        return filesInError;
    }

    @Override
    public Map<KojiChecksumType, MultiValuedMap<String, String>> call() throws IOException {
        queue = new LinkedBlockingQueue<>();

        checksumFiles();

        try {
            queue.put(new Checksum());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return Collections.unmodifiableMap(map);
    }

    public BlockingQueue<Checksum> getQueue() {
        return queue;
    }
}
