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
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.redhat.red.build.koji.model.json.util.KojiObjectMapper;
import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;

public class BuildConfig {
    @JsonProperty("archive-extensions")
    private List<String> archiveExtensions;

    @JsonProperty("archive-types")
    private List<String> archiveTypes;

    @JsonProperty("build-systems")
    private List<BuildSystem> buildSystems;

    @JsonProperty("cache-lifespan")
    private Long cacheLifespan;

    @JsonProperty("cache-max-idle")
    private Long cacheMaxIdle;

    @JsonProperty("checksum-only")
    private Boolean checksumOnly;

    @JsonProperty("checksum-type")
    private Set<KojiChecksumType> checksumTypes;

    @JsonProperty("disable-cache")
    private Boolean disableCache;

    @JsonProperty("disable-recursion")
    private Boolean disableRecursion;

    @JsonProperty("excludes")
    private List<Pattern> excludes;

    @JsonProperty("koji-hub-url")
    private URL kojiHubURL;

    @JsonProperty("koji-multicall-size")
    private Integer kojiMulticallSize;

    @JsonProperty("koji-num-threads")
    private Integer kojiNumThreads;

    @JsonProperty("koji-web-url")
    private URL kojiWebURL;

    @JsonProperty("output-directory")
    private String outputDirectory;

    @JsonProperty("pnc-connection-timeout")
    private Long pncConnectionTimeout;

    @JsonProperty("pnc-partition-size")
    private Integer pncPartitionSize;

    @JsonProperty("pnc-read-timeout")
    private Long pncReadTimeout;

    @JsonProperty("pnc-url")
    private URL pncURL;

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

    public static BuildConfig load(URL url) throws IOException {
        return getMapper().readValue(url, BuildConfig.class);
    }

    public static BuildConfig load(ClassLoader cl) throws IOException {
        URL url = cl.getResource(ConfigDefaults.CONFIG_FILE);

        if (url != null) {
            return load(url);
        }

        return null;
    }

    public void save(File file) throws IOException {
        JSONUtils.dumpObjectToFile(this, file);
    }

    public static BuildConfig merge(BuildConfig config, File file) throws IOException {
        ObjectReader reader = getMapper().readerForUpdating(config);
        return reader.readValue(file);
    }

    public static BuildConfig merge(BuildConfig config, String json) throws IOException {
        ObjectReader reader = getMapper().readerForUpdating(config);
        return reader.readValue(json);
    }

    public static BuildConfig merge(BuildConfig config, URL url) throws IOException {
        ObjectReader reader = getMapper().readerForUpdating(config);
        return reader.readValue(url);
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

    public List<BuildSystem> getBuildSystems() {
        if (buildSystems == null) {
            buildSystems = ConfigDefaults.BUILD_SYSTEMS;
        }

        return buildSystems;
    }

    public void setBuildSystems(List<BuildSystem> buildSystems) {
        this.buildSystems = buildSystems;
    }

    public Long getCacheLifespan() {
        if (cacheLifespan == null) {
            cacheLifespan = ConfigDefaults.CACHE_LIFESPAN;
        }

        return cacheLifespan;
    }

    public void setCacheLifespan(Long cacheLifespan) {
        this.cacheLifespan = cacheLifespan;
    }

    public Long getCacheMaxIdle() {
        if (cacheMaxIdle == null) {
            cacheMaxIdle = ConfigDefaults.CACHE_MAX_IDLE;
        }

        return cacheMaxIdle;
    }

    public void setCacheMaxIdle(Long cacheMaxIdle) {
        this.cacheMaxIdle = cacheMaxIdle;
    }

    public Boolean getChecksumOnly() {
        if (checksumOnly == null) {
            checksumOnly = ConfigDefaults.CHECKSUM_ONLY;
        }

        return checksumOnly;
    }

    public void setChecksumOnly(Boolean checksumOnly) {
        this.checksumOnly = checksumOnly;
    }

    public Set<KojiChecksumType> getChecksumTypes() {
        if (checksumTypes == null) {
            checksumTypes = ConfigDefaults.CHECKSUM_TYPES;
        }

        return checksumTypes;
    }

    public void setChecksumTypes(Set<KojiChecksumType> checksumTypes) {
        this.checksumTypes = checksumTypes;
    }

    public Boolean getDisableCache() {
        if (disableCache == null) {
            disableCache = ConfigDefaults.DISABLE_CACHE;
        }

        return disableCache;
    }

    public void setDisableCache(Boolean disableCache) {
        this.disableCache = disableCache;
    }

    public Boolean getDisableRecursion() {
        if (disableRecursion == null) {
            disableRecursion = ConfigDefaults.DISABLE_RECURSION;
        }

        return disableRecursion;
    }

    public void setDisableRecursion(Boolean disableRecursion) {
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

    public int getKojiMulticallSize() {
        if (kojiMulticallSize == null) {
            kojiMulticallSize = ConfigDefaults.KOJI_MULTICALL_SIZE;
        }

        return kojiMulticallSize;
    }

    public void setKojiMulticallSize(Integer kojiMulticallSize) {
        this.kojiMulticallSize = kojiMulticallSize;
    }

    public Integer getKojiNumThreads() {
        if (kojiNumThreads == null) {
            kojiNumThreads = ConfigDefaults.KOJI_NUM_THREADS;
        }

        return kojiNumThreads;
    }

    public void setKojiNumThreads(Integer kojiNumThreads) {
        this.kojiNumThreads = kojiNumThreads;
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

    public String getOutputDirectory() {
        if (outputDirectory == null) {
            outputDirectory = ConfigDefaults.OUTPUT_DIR;
        }

        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public Long getPncConnectionTimeout() {
        if (pncConnectionTimeout == null) {
            pncConnectionTimeout = ConfigDefaults.PNC_CONNECTION_TIMEOUT;
        }

        return pncConnectionTimeout;
    }

    public void setPncConnectionTimeout(Long pncConnectionTimeout) {
        this.pncConnectionTimeout = pncConnectionTimeout;
    }

    public Integer getPncPartitionSize() {
        if (pncPartitionSize == null) {
            pncPartitionSize = ConfigDefaults.PNC_PARTITION_SIZE;
        }

        return pncPartitionSize;
    }

    public void setPncPartitionSize(Integer pncPartitionSize) {
        this.pncPartitionSize = pncPartitionSize;
    }

    public Long getPncReadTimeout() {
        if (pncReadTimeout == null) {
            pncReadTimeout = ConfigDefaults.PNC_READ_TIMEOUT;
        }

        return pncReadTimeout;
    }

    public void setPncReadTimeout(Long pncReadTimeout) {
        this.pncReadTimeout = pncReadTimeout;
    }

    public URL getPncURL() {
        if (pncURL == null) {
            pncURL = ConfigDefaults.PNC_URL;
        }

        return pncURL;
    }

    public void setPncURL(URL pncURL) {
        this.pncURL = pncURL;
    }

    public Boolean getUseBuildsFile() {
        if (useBuildsFile == null) {
            useBuildsFile = ConfigDefaults.USE_BUILDS_FILE;
        }

        return useBuildsFile;
    }

    public void setUseBuildsFile(Boolean useBuildsFile) {
        this.useBuildsFile = useBuildsFile;
    }


    public Boolean getUseChecksumsFile() {
        if (useChecksumsFile == null) {
            useChecksumsFile = ConfigDefaults.USE_CHECKSUMS_FILE;
        }

        return useChecksumsFile;
    }

    public void setUseChecksumsFile(Boolean useChecksumsFile) {
        this.useChecksumsFile = useChecksumsFile;
    }

    @Override
    public String toString() {
        return "BuildConfig{"
            + "\n\tarchiveExtensions=" + getArchiveExtensions()
            + ", \n\tarchiveTypes=" + getArchiveTypes()
            + ", \n\tbuildSystems=" + getBuildSystems()
            + ", \n\tcacheLifespan=" + getCacheLifespan()
            + ", \n\tcacheMaxIdle=" + getCacheMaxIdle()
            + ", \n\tchecksumOnly=" + getChecksumOnly()
            + ", \n\tchecksumTypes=" + getChecksumTypes()
            + ", \n\tdisableCache=" + getDisableCache()
            + ", \n\texcludes=" + getExcludes()
            + ", \n\tkojiHubURL=" + getKojiHubURL()
            + ", \n\tkojiMulticallSize=" + getKojiMulticallSize()
            + ", \n\tkojiNumThreads=" + getKojiNumThreads()
            + ", \n\tkojiWebURL=" + getKojiWebURL()
            + ", \n\toutputDirectory=" + getOutputDirectory()
            + ", \n\tpncConnectionTimeout=" + getPncConnectionTimeout()
            + ", \n\tpncPartitionSize=" + getPncPartitionSize()
            + ", \n\tpncReadTimeout=" + getPncReadTimeout()
            + ", \n\tpncURL=" + getPncURL()
            + ", \n\tuseBuildsFile=" + getUseBuildsFile()
            + ", \n\tuseChecksumsFile='" + getUseChecksumsFile()
            + "}";
    }
}
