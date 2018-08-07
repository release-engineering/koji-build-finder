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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;

import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;

public class KojiLocalArchive implements Externalizable {
    private static final long serialVersionUID = -6388497659567932834L;

    private static int VERSION = 1;

    private KojiArchiveInfo archive;

    private List<String> files;

    public KojiLocalArchive() {

    }

    public KojiLocalArchive(KojiArchiveInfo archive, List<String> files) {
        this.archive = archive;
        this.files = files;
    }

    public KojiArchiveInfo getArchive() {
        return archive;
    }

    public void setArchive(KojiArchiveInfo archive) {
        this.archive = archive;
    }

    public List<String> getFiles() {
        return files;
    }

    public void setFiles(List<String> files) {
        this.files = files;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(VERSION);
        out.writeObject(archive);
        out.writeObject(files);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int version = in.readInt();

        if (version != 1) {
            throw new IOException("Invalid version: " + version);
        }

        archive = (KojiArchiveInfo) in.readObject();
        files = (List<String>) in.readObject();
    }
}
