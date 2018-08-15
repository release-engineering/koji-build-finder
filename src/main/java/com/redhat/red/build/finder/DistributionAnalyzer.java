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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
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

public class DistributionAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DistributionAnalyzer.class);

    /**
     * Ideally we should be able to use FileSystemManager::canCreateFileSystem but that relies on
     * an accurate extension map in providers.xml. Either fix that up manually or exclude non-viable
     * archive schemes. Further without overriding the URLConnection.getFileNameMap the wrong results
     * will be returned for zip/gz which is classified as application/octet-stream.
     */
    private static final List<String> NON_ARCHIVE_SCHEMES = Collections.unmodifiableList(Arrays.asList("tmp", "res", "ram", "file"));

    private final List<File> files;

    private MultiValuedMap<String, String> map;

    private StandardFileSystemManager sfs;

    private String rootString;

    private BuildConfig config;

    private Cache<String, MultiValuedMap<String, String>> fileCache;

    private EmbeddedCacheManager cacheManager;

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
    }

    public MultiValuedMap<String, String> checksumFiles() throws IOException {
        final Instant startTime = Instant.now();
        sfs = new StandardFileSystemManager();

        sfs.init();

        try {
            for (File file : files) {
                try (FileObject fo = sfs.resolveFile(file.getAbsolutePath())) {
                    String checksum = null;
                    MultiValuedMap<String, String> map = null;

                    if (cacheManager != null) {
                        checksum = checksum(fo);
                        map = fileCache.get(checksum);

                        if (map != null) {
                            this.map.putAll(map);
                            LOGGER.info("Loaded checksums for file {} (checksum {}) from cache", green(file.getName()), green(checksum));
                        } else {
                            LOGGER.info("File {} (checksum {}) not found in cache", green(file.getName()), green(checksum));
                        }
                    }

                    if (map == null) {
                        LOGGER.info("Finding checksums for file: {}", green(file.getName()));
                        rootString = fo.getName().getFriendlyURI().substring(0, fo.getName().getFriendlyURI().indexOf(fo.getName().getBaseName()));
                        map = new ArrayListValuedHashMap<>();
                        listChildren(fo, map);

                        if (cacheManager != null) {
                            fileCache.put(checksum, map);
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

    private String checksum(FileObject fo) throws IOException {
        return Hex.encodeHexString(DigestUtils.digest(DigestUtils.getDigest(config.getChecksumType().getAlgorithm()), fo.getContent().getInputStream()));
    }

    private boolean isArchive(FileObject fo) {
        return (!NON_ARCHIVE_SCHEMES.contains(fo.getName().getExtension()) && Stream.of(sfs.getSchemes()).anyMatch(s -> s.equals(fo.getName().getExtension())));
    }

    private String normalizePath(FileObject fo) {
        String friendlyURI = fo.getName().getFriendlyURI();
        String normalizedPath = friendlyURI.substring(friendlyURI.indexOf(rootString) + rootString.length());

        return normalizedPath;
    }

    private void listArchive(FileObject fo, MultiValuedMap<String, String> map) throws IOException {
        LOGGER.debug("Creating file system for: {}", normalizePath(fo));

        try (FileObject layered = sfs.createFileSystem(fo.getName().getExtension(), fo)) {
            listChildren(layered, map);
            sfs.closeFileSystem(layered.getFileSystem());
        } catch (FileSystemException e) {
            // TODO: store checksum/filename/error so that we can flag the file
            LOGGER.warn("Unable to process archive/compressed file: {}: {}", red(normalizePath(fo)), red(e.getMessage()));
            LOGGER.debug("Caught file system exception", e);
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

    private void listChildren(FileObject fo, MultiValuedMap<String, String> map) throws IOException {
        if (fo.isFile()) {
            if (isArchive(fo)) {
                listArchive(fo, map);
            }

            if (includeFile(fo)) {
                String checksum = checksum(fo);
                String found = normalizePath(fo);

                LOGGER.debug("Checksum: {} {}", checksum, found);

                map.put(checksum, found);
            }
        } else {
            for (FileObject file : fo.getChildren()) {
                try {
                    listChildren(file, map);
                } finally {
                    file.close();
                }
            }
        }
    }

    public void outputToFile(File file) throws JsonGenerationException, JsonMappingException, IOException {
        JSONUtils.dumpObjectToFile(map.asMap(), file);
    }
}
