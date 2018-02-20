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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;

public class BuildConfig {
    @JsonProperty("archive-types")
    private List<String> archiveTypes;

    @JsonProperty("checksum-only")
    private Boolean checksumOnly;

    @JsonProperty("checksum-type")
    private KojiChecksumType checksumType;

    @JsonProperty("excludes")
    private List<String> excludes;

    @JsonProperty("koji-hub-url")
    private String kojiHubURL;

    @JsonProperty("koji-web-url")
    private String kojiWebURL;

    public List<String> getArchiveTypes() {
        if (archiveTypes == null) {
            archiveTypes = ConfigDefaults.ARCHIVE_TYPES;
        }

        return archiveTypes;
    }

    public void setArchiveTypes(List<String> archiveTypes) {
        this.archiveTypes = archiveTypes;
    }

    public boolean getChecksumOnly() {
        if (checksumOnly == null) {
            checksumOnly = ConfigDefaults.CHECKSUM_ONLY;
        }

        return checksumOnly;
    }

    public void setChecksumOnly(boolean checksumOnly) {
        this.checksumOnly = checksumOnly;
    }

    public KojiChecksumType getChecksumType() {
        if (checksumType == null) {
            checksumType = ConfigDefaults.CHECKSUM_TYPE;
        }

        return checksumType;
    }

    public void setChecksumType(KojiChecksumType checksumType) {
        this.checksumType = checksumType;
    }

    public List<String> getExcludes() {
        if (excludes == null) {
            excludes = ConfigDefaults.EXCLUDES;
        }

        return excludes;
    }

    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    public String getKojiHubURL() {
        if (kojiHubURL == null) {
            kojiHubURL = ConfigDefaults.KOJI_HUB_URL;
        }

        return kojiHubURL;
    }

    public void setKojiHubURL(String kojiHubURL) {
        this.kojiHubURL = kojiHubURL;
    }

    public String getKojiWebURL() {
        if (kojiWebURL == null) {
            kojiWebURL = ConfigDefaults.KOJI_WEB_URL;
        }

        return kojiWebURL;
    }

    public void setKojiWebURL(String kojiWebURL) {
        this.kojiWebURL = kojiWebURL;
    }

    @Override
    public String toString() {
        return "BuildConfig{"
            + "\n\tarchiveTypes=" + getArchiveTypes()
            + ", \n\tchecksumOnly=" + getChecksumOnly()
            + ", \n\tchecksumType=" + getChecksumType()
            + ", \n\texcludes=" + getExcludes()
            + ", \n\tkojiHubURL='" + getKojiHubURL()
            + '\'' + ", \n\tkojiWebURL='" + getKojiWebURL()
            + '\'' + '}';
    }
}
