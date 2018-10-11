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

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.vfs2.FileObject;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;

public class Checksum {
    private static int BUFFER_SIZE = 1024;

    private KojiChecksumType type;

    private String value;

    @JsonIgnore
    private String filename;

    public Checksum() {

    }

    public Checksum(KojiChecksumType type, String value, String filename) {
        this.type = type;
        this.value = value;
        this.filename = filename;
    }

    public static Set<Checksum> checksum(FileObject fo, Set<KojiChecksumType> checksumTypes, String root) throws IOException {
        int checksumTypesSize = checksumTypes.size();
        Map<KojiChecksumType, MessageDigest> mds = new HashMap<>(checksumTypesSize);

        for (KojiChecksumType checksumType : checksumTypes) {
            try {
                mds.put(checksumType, MessageDigest.getInstance(checksumType.getAlgorithm()));
            } catch (NoSuchAlgorithmException e) {
                throw new IOException(e);
            }
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>(checksumTypesSize);

        int len1;
        InputStream input = fo.getContent().getInputStream();
        byte[] buffer = new byte[BUFFER_SIZE];

        while ((len1 = input.read(buffer)) > 0) {
            final int len = len1;

            checksumTypes.forEach(checksumType -> {
                CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
                    MessageDigest md =  mds.get(checksumType);
                    md.update(buffer, 0, len);
                    return null;
                });

                futures.add(future);
            });

            for (CompletableFuture<Void> future : futures) {
                future.join();
            }

            futures.clear();
        }

        Map<KojiChecksumType, CompletableFuture<Checksum>> futures2 = new HashMap<>(checksumTypesSize);

        checksumTypes.forEach(checksumType -> {
            CompletableFuture<Checksum> future = CompletableFuture.supplyAsync(() -> {
                MessageDigest md = mds.get(checksumType);
                return new Checksum(checksumType, Hex.encodeHexString(md.digest()), Utils.normalizePath(fo, root));
            });

            futures2.put(checksumType, future);
        });

        Set<Checksum> results = new HashSet<>(checksumTypesSize);

        for (KojiChecksumType checksumType : checksumTypes) {
            try {
                results.add(futures2.get(checksumType).get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throw new IOException(e);
            }
        }

        return results;
    }

    public static Checksum findByType(Set<Checksum> checksums, KojiChecksumType type) {
        List<Checksum> list = checksums.stream().filter(checksum -> checksum.getType().equals(type)).collect(Collectors.toList());
        int size = list.size();

        if (size == 0) {
            return null;
        }

        return list.get(0);
    }

    public KojiChecksumType getType() {
        return type;
    }

    public void setType(KojiChecksumType type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
