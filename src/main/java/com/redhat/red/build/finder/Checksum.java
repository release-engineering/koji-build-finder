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

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.vfs2.FileObject;

public class Checksum {
    private FileObject fileObject;

    private String algorithm;

    private String value;

    private String filename;

    public Checksum() {

    }

    public Checksum(String value, String filename) {
        this.value = value;
        this.filename = filename;
    }

    public Checksum(FileObject fo, String algorithm, String root) throws IOException {
        this.fileObject = fo;
        this.algorithm = algorithm;
        this.value = checksum(fo, algorithm);
        this.filename = Utils.normalizePath(fileObject, root);
    }

    private String checksum(FileObject fo, String algorithm) throws IOException {
        return Hex.encodeHexString(DigestUtils.digest(DigestUtils.getDigest(algorithm), fo.getContent().getInputStream()));
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public FileObject getFileObject() {
        return fileObject;
    }

    public void setFileObject(FileObject fileObject) {
        this.fileObject = fileObject;
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
