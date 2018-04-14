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
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final MessageDigest md;

    private StandardFileSystemManager sfs;

    private String rootString;

    private BuildConfig config;

    public DistributionAnalyzer(List<File> files, BuildConfig config) {
        this.files = files;
        this.config = config;
        this.md = DigestUtils.getDigest(config.getChecksumType().getAlgorithm());
        this.map = new ArrayListValuedHashMap<>();
    }

    public MultiValuedMap<String, String> checksumFiles() throws IOException {
        final Instant startTime = Instant.now();
        sfs = new StandardFileSystemManager();

        sfs.init();

        try {
            for (File file : files) {
                try (FileObject fo = sfs.resolveFile(file.getAbsolutePath())) {
                    rootString = fo.getName().getFriendlyURI().substring(0, fo.getName().getFriendlyURI().indexOf(fo.getName().getBaseName()));
                    listChildren(fo);
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

    private void listChildren(FileObject fo) throws IOException {
        FileContent fc = fo.getContent();
        String friendly = fo.getName().getFriendlyURI();
        String found = friendly.substring(friendly.indexOf(rootString) + rootString.length());

        if (fo.getType().equals(FileType.FILE)) {
            boolean excludeExtension = config.getArchiveExtensions() != null && !config.getArchiveExtensions().isEmpty() && !config.getArchiveExtensions().stream().anyMatch(x -> x.equals(fo.getName().getExtension()));
            boolean excludeFile = false;

            if (!excludeExtension) {
                excludeFile = config.getExcludes() != null && !config.getExcludes().isEmpty() && config.getExcludes().stream().anyMatch(friendly::matches);
            }

            if (!excludeFile && !excludeExtension) {
                byte[] digest = DigestUtils.digest(md, fc.getInputStream());
                String checksum = Hex.encodeHexString(digest);
                map.put(checksum, found);
                LOGGER.debug("Checksum: {} {}", checksum, found);
            }
        }

        if (fo.getType().equals(FileType.FOLDER) || fo.getType().equals(FileType.FILE_OR_FOLDER)) {
            for (FileObject file : fo.getChildren()) {
                try {
                    listChildren(file);
                } finally {
                    file.close();
                }
            }
        } else {
            if (Stream.of(sfs.getSchemes()).anyMatch(s -> s.equals(fo.getName().getExtension()) && !NON_ARCHIVE_SCHEMES.contains(fo.getName().getExtension()))) {
                LOGGER.debug("Creating file system for: {}", found);

                try (FileObject layered = sfs.createFileSystem(fo.getName().getExtension(), fo)) {
                    listChildren(layered);
                    sfs.closeFileSystem(layered.getFileSystem());
                } catch (FileSystemException e) {
                    LOGGER.warn("Unable to process archive/compressed file: {}", red(found));
                    LOGGER.debug("Caught file system exception", e);
                }
            }
        }
    }

    public boolean outputToFile(File file) {
        return JSONUtils.dumpObjectToFile(map.asMap(), file);
    }
}
