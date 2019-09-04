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
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BuildRecordPushResult implements Serializable {
    private static final long serialVersionUID = -2714111898928592350L;

    private Integer id;

    private Integer buildRecordId;

    private Status status;

    private String log;

    private List<ArtifactImportError> artifactImportErrors;

    private Integer brewBuildId;

    private String brewBuildUrl;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getBuildRecordId() {
        return buildRecordId;
    }

    public void setBuildRecordId(Integer buildRecordId) {
        this.buildRecordId = buildRecordId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public List<ArtifactImportError> getArtifactImportErrors() {
        return artifactImportErrors;
    }

    public void setArtifactImportErrors(List<ArtifactImportError> artifactImportErrors) {
        this.artifactImportErrors = artifactImportErrors;
    }

    public Integer getBrewBuildId() {
        return brewBuildId;
    }

    public void setBrewBuildId(Integer brewBuildId) {
        this.brewBuildId = brewBuildId;
    }

    public String getBrewBuildUrl() {
        return brewBuildUrl;
    }

    public void setBrewBuildUrl(String brewBuildUrl) {
        this.brewBuildUrl = brewBuildUrl;
    }

    @Override
    public String toString() {
        return "BuildRecordPushResult [id=" + id + ", buildRecordId=" + buildRecordId + ", status=" + status + ", log="
                + log + ", artifactImportErrors=" + artifactImportErrors + ", brewBuildId=" + brewBuildId
                + ", brewBuildUrl=" + brewBuildUrl + "]";
    }

    public enum Status {
        SUCCESS,
        FAILED,
        SYSTEM_ERROR,
        CANCELED;
    }
}
