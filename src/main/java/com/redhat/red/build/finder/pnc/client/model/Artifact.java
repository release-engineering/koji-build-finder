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
package com.redhat.red.build.finder.pnc.client.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Artifact implements Serializable {
    private static final long serialVersionUID = 7257116817047942244L;

    private Integer id;

    private String identifier;

    private Quality artifactQuality;

    private String md5;

    private String sha1;

    private String sha256;

    private String filename;

    private String deployPath;

    private List<Integer> buildRecordIds;

    private List<Integer> dependantBuildRecordIds;

    private Instant importDate;

    private String originUrl;

    private Long size;

    /**
     * Internal url to the artifact using internal (cloud) network domain
     */
    private String deployUrl;

    /**
     * Public url to the artifact using public network domain
     */
    private String publicUrl;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Quality getArtifactQuality() {
        return artifactQuality;
    }

    public void setArtifactQuality(Quality artifactQuality) {
        this.artifactQuality = artifactQuality;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public String getSha1() {
        return sha1;
    }

    public void setSha1(String sha1) {
        this.sha1 = sha1;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getDeployPath() {
        return deployPath;
    }

    public void setDeployPath(String deployPath) {
        this.deployPath = deployPath;
    }

    public List<Integer> getBuildRecordIds() {
        return buildRecordIds;
    }

    public void setBuildRecordIds(List<Integer> buildRecordIds) {
        this.buildRecordIds = buildRecordIds;
    }

    public List<Integer> getDependantBuildRecordIds() {
        return dependantBuildRecordIds;
    }

    public void setDependantBuildRecordIds(List<Integer> dependantBuildRecordIds) {
        this.dependantBuildRecordIds = dependantBuildRecordIds;
    }

    public Instant getImportDate() {
        return importDate;
    }

    public void setImportDate(Instant importDate) {
        this.importDate = importDate;
    }

    public String getOriginUrl() {
        return originUrl;
    }

    public void setOriginUrl(String originUrl) {
        this.originUrl = originUrl;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getDeployUrl() {
        return deployUrl;
    }

    public void setDeployUrl(String deployUrl) {
        this.deployUrl = deployUrl;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public enum Quality {
        NEW,
        VERIFIED,
        TESTED,
        DEPRECATED,
        BLACKLISTED,
        DELETED,
        TEMPORARY
    }

    @Override
    public String toString() {
        return "Artifact [id=" + id + ", identifier=" + identifier + ", artifactQuality=" + artifactQuality + ", md5="
            + md5 + ", sha1=" + sha1 + ", sha256=" + sha256 + ", filename=" + filename + ", deployPath="
            + deployPath + ", buildRecordIds=" + buildRecordIds + ", dependantBuildRecordIds="
            + dependantBuildRecordIds + ", importDate=" + importDate + ", originUrl=" + originUrl + ", size=" + size
            + ", deployUrl=" + deployUrl + ", publicUrl=" + publicUrl + "]";
    }
}
