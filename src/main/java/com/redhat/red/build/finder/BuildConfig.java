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
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.red.build.koji.model.json.util.KojiObjectMapper;
import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;

public class BuildConfig {
    @JsonProperty("archive-extensions")
    private List<String> archiveExtensions;

    @JsonProperty("archive-types")
    private List<String> archiveTypes;

    @JsonProperty("cache-lifespan")
    private Long cacheLifespan;

    @JsonProperty("cache-max-idle")
    private Long cacheMaxIdle;

    @JsonProperty("checksum-only")
    private Boolean checksumOnly;

    @JsonProperty("checksum-type")
    private KojiChecksumType checksumType;

    @JsonProperty("disable-cache")
    private Boolean disableCache;

    @JsonProperty("disable-recursion")
    private Boolean disableRecursion;

    @JsonProperty("excludes")
    private List<Pattern> excludes;

    @JsonProperty("koji-hub-url")
    private URL kojiHubURL;

    @JsonProperty("koji-web-url")
    private URL kojiWebURL;

    @JsonProperty("use-builds-file")
    private Boolean useBuildsFile;

    @JsonProperty("use-checksums-file")
    private Boolean useChecksumsFile;

    public BuildConfig() {

    }

    public static BuildConfig load(File file) throws IOException {
        return getMapper().readValue(file, BuildConfig.class);
    }

    public static BuildConfig load(String json) throws IOException {
        return getMapper().readValue(json, BuildConfig.class);
    }

    public void save(File file) throws IOException {
        JSONUtils.dumpObjectToFile(this, file);
    }

    private static ObjectMapper getMapper() {
        ObjectMapper mapper = new KojiObjectMapper();

        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return mapper;
    }

    public List<String> getArchiveExtensions() {
        if (archiveExtensions == null) {
            archiveExtensions = ConfigDefaults.ARCHIVE_EXTENSIONS;
        }

        return archiveExtensions;
    }

    public void setArchiveExtensions(List<String> archiveExtensions) {
        this.archiveExtensions = archiveExtensions;
    }

    public List<String> getArchiveTypes() {
        if (archiveTypes == null) {
            archiveTypes = ConfigDefaults.ARCHIVE_TYPES;
        }

        return archiveTypes;
    }

    public void setArchiveTypes(List<String> archiveTypes) {
        this.archiveTypes = archiveTypes;
    }

    public long getCacheLifespan() {
        if (cacheLifespan == null) {
            cacheLifespan = ConfigDefaults.CACHE_LIFESPAN;
        }

        return cacheLifespan;
    }

    public void setCacheLifespan(long cacheLifespan) {
        this.cacheLifespan = cacheLifespan;
    }

    public long getCacheMaxIdle() {
        if (cacheMaxIdle == null) {
            cacheMaxIdle = ConfigDefaults.CACHE_MAX_IDLE;
        }

        return cacheMaxIdle;
    }

    public void setCacheMaxIdle(long cacheMaxIdle) {
        this.cacheMaxIdle = cacheMaxIdle;
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

    public boolean getDisableCache() {
        if (disableCache == null) {
            disableCache = ConfigDefaults.DISABLE_CACHE;
        }

        return disableCache;
    }

    public void setDisableCache(boolean disableCache) {
        this.disableCache = disableCache;
    }

    public boolean getDisableRecursion() {
        if (disableRecursion == null) {
            disableRecursion = ConfigDefaults.DISABLE_RECURSION;
        }

        return disableRecursion;
    }

    public void setDisableRecursion(boolean disableRecursion) {
        this.disableRecursion = disableRecursion;
    }

    public List<Pattern> getExcludes() {
        if (excludes == null) {
            excludes = ConfigDefaults.EXCLUDES;
        }

        return excludes;
    }

    public void setExcludes(List<Pattern> excludes) {
        this.excludes = excludes;
    }

    public URL getKojiHubURL() {
        if (kojiHubURL == null) {
            kojiHubURL = ConfigDefaults.KOJI_HUB_URL;
        }

        return kojiHubURL;
    }

    public void setKojiHubURL(URL kojiHubURL) {
        this.kojiHubURL = kojiHubURL;
    }

    public URL getKojiWebURL() {
        if (kojiWebURL == null) {
            kojiWebURL = ConfigDefaults.KOJI_WEB_URL;
        }

        return kojiWebURL;
    }

    public void setKojiWebURL(URL kojiWebURL) {
        this.kojiWebURL = kojiWebURL;
    }

    public boolean getUseBuildsFile() {
        if (useBuildsFile == null) {
            useBuildsFile = ConfigDefaults.USE_BUILDS_FILE;
        }

        return useBuildsFile;
    }

    public void setUseBuildsFile(boolean useBuildsFile) {
        this.useBuildsFile = useBuildsFile;
    }


    public boolean getUseChecksumsFile() {
        if (useChecksumsFile == null) {
            useChecksumsFile = ConfigDefaults.USE_CHECKSUMS_FILE;
        }

        return useChecksumsFile;
    }

    public void setUseChecksumsFile(boolean useChecksumsFile) {
        this.useChecksumsFile = useChecksumsFile;
    }

    @Override
    public String toString() {
        return "BuildConfig{"
            + "\n\tarchiveExtensions=" + getArchiveExtensions()
            + ", \n\tarchiveTypes=" + getArchiveTypes()
            + ", \n\tcacheLifespan=" + getCacheLifespan()
            + ", \n\tcacheMaxIdle=" + getCacheMaxIdle()
            + ", \n\tchecksumOnly=" + getChecksumOnly()
            + ", \n\tchecksumType=" + getChecksumType()
            + ", \n\tdisableCache=" + getDisableCache()
            + ", \n\texcludes=" + getExcludes()
            + ", \n\tkojiHubURL=" + getKojiHubURL()
            + ", \n\tkojiWebURL=" + getKojiWebURL()
            + ", \n\tuseBuildsFile=" + getUseBuildsFile()
            + ", \n\tuseChecksumsFile='" + getUseChecksumsFile()
            + "}";
    }
}
