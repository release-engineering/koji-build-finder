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

import java.util.Collection;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;

public class KojiLocalArchive {
    private KojiArchiveInfo archive;

    @JsonProperty("files")
    private Collection<String> filenames;

    private Collection<Checksum> checksums;

    @JsonProperty("unmatchedFiles")
    private Collection<String> unmatchedFilenames;

    public KojiLocalArchive() {
        this.filenames = new TreeSet<>();
        this.unmatchedFilenames = new TreeSet<>();
    }

    public KojiLocalArchive(KojiArchiveInfo archive, Collection<String> filenames, Collection<Checksum> checksums) {
        this.archive = archive;
        this.filenames = new TreeSet<>(filenames);
        this.checksums = checksums;
        this.unmatchedFilenames = new TreeSet<>();
    }

    public KojiArchiveInfo getArchive() {
        return archive;
    }

    public void setArchive(KojiArchiveInfo archive) {
        this.archive = archive;
    }

    public Collection<String> getFilenames() {
        return filenames;
    }

    public void setFilenames(Collection<String> filenames) {
        this.filenames = new TreeSet<>(filenames);
    }

    public Collection<Checksum> getChecksums() {
        return checksums;
    }

    public void setChecksums(Collection<Checksum> checksums) {
        this.checksums = checksums;
    }

    public static boolean isMissingBuildTypeInfo(KojiArchiveInfo archive) {
        String archiveBuildType = archive.getBuildType();

        if (archiveBuildType == null) {
            return false;
        }

        switch (archiveBuildType) {
        case "image":
            return archive.getArch() == null;
        case "maven":
            return archive.getGroupId() == null;
        case "rpm":
            return false;
        case "win":
            return archive.getPlatforms() == null;
        default:
            return false;
        }
    }

    @JsonIgnore
    public boolean isBuiltFromSource() {
        return unmatchedFilenames.isEmpty();
    }

    public Collection<String> getUnmatchedFilenames() {
        return unmatchedFilenames;
    }

    public void setUnmatchedFilenames(Collection<String> unmatchFilenames) {
        this.unmatchedFilenames = new TreeSet<>(unmatchedFilenames);
    }

    @Override
    public String toString() {
        return "KojiLocalArchive [archive=" + archive + ", filenames=" + filenames + ", checksums=" + checksums
                + ", unmatchedFilenames=" + unmatchedFilenames + "]";
    }
}
