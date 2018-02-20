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

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
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

    public DistributionAnalyzer(List<File> files, String algorithm) {
        this.files = files;
        this.md = DigestUtils.getDigest(algorithm);
        this.map = new ArrayListValuedHashMap<>();
    }

    public void checksumFiles() throws IOException {
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
    }

    private void listChildren(FileObject fo) throws IOException {
        FileContent fc = fo.getContent();
        String friendly = fo.getName().getFriendlyURI();
        String found = friendly.substring(friendly.indexOf(rootString) + rootString.length());

        if (fo.getType().getName().equals(FileType.FILE.getName())) {
            byte[] digest = DigestUtils.digest(md, fc.getInputStream());
            String checksum = Hex.encodeHexString(digest);
            map.put(checksum, found);
            LOGGER.debug("Checksum: {} {}", checksum, found);
        }

        if (fo.getType().getName().equals(FileType.FOLDER.getName()) || fo.getType().getName().equals(FileType.FILE_OR_FOLDER.getName())) {
            for (FileObject fileO : fo.getChildren()) {
                try {
                    listChildren(fileO);
                } finally {
                    fileO.close();
                }
            }
        } else {
            if (Stream.of(sfs.getSchemes()).anyMatch(s -> s.equals(fo.getName().getExtension()) && !NON_ARCHIVE_SCHEMES.contains(fo.getName().getExtension()))) {
                LOGGER.debug("Creating file system for: {}", found);

                try (FileObject layered = sfs.createFileSystem(fo.getName().getExtension(), fo)) {
                    listChildren(layered);
                    sfs.closeFileSystem(layered.getFileSystem());
                } catch (FileSystemException e) {
                    LOGGER.warn("Unable to process archive/compressed file: {}", found);
                    LOGGER.debug("Caught file system exception", e);
                }
            }
        }
    }

    public boolean outputToFile(File file) {
        return JSONUtils.dumpObjectToFile(map.asMap(), file);
    }

    public MultiValuedMap<String, String> getMap() {
        return map;
    }

    public void setMap(MultiValuedMap<String, String> map) {
        this.map = map;
    }
}
