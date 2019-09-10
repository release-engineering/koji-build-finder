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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.redhat.red.build.finder.pnc.client.model.Artifact;
import com.redhat.red.build.finder.pnc.client.model.BuildConfiguration;
import com.redhat.red.build.finder.pnc.client.model.BuildRecord;
import com.redhat.red.build.finder.pnc.client.model.BuildRecordPushResult;
import com.redhat.red.build.finder.pnc.client.model.ProductVersion;

public class PncBuild implements Serializable {
    private static final long serialVersionUID = 4500090728323371691L;

    private BuildRecord buildRecord;

    private BuildRecordPushResult buildRecordPushResult;

    private BuildConfiguration buildConfiguration;

    private ProductVersion productVersion;

    private List<Artifact> artifacts;

    private List<Artifact> remoteArtifacts;

    public PncBuild() {
        this.artifacts = new ArrayList<>();
    }

    public PncBuild(BuildRecord buildRecord) {
        this.buildRecord = buildRecord;
        this.artifacts = new ArrayList<>();
    }

    public PncBuild(BuildRecord buildRecord, BuildRecordPushResult buildRecordPushResult, BuildConfiguration buildConfiguration, ProductVersion productVersion, List<Artifact> artifacts, List<Artifact> remoteArtifacts) {
        this.buildRecord = buildRecord;
        this.buildRecordPushResult = buildRecordPushResult;
        this.buildConfiguration = buildConfiguration;
        this.productVersion = productVersion;
        this.artifacts = artifacts;
        this.remoteArtifacts = remoteArtifacts;
    }

    public BuildRecord getBuildRecord() {
        return buildRecord;
    }

    public void setBuildRecordPushResult(BuildRecordPushResult buildRecordPushResult) {
        this.buildRecordPushResult = buildRecordPushResult;
    }

    public BuildRecordPushResult getBuildRecordPushResult() {
        return buildRecordPushResult;
    }

    public void setBuildRecord(BuildRecord buildRecord) {
        this.buildRecord = buildRecord;
    }

    public BuildConfiguration getBuildConfiguration() {
        return buildConfiguration;
    }

    public void setBuildConfiguration(BuildConfiguration buildConfiguration) {
        this.buildConfiguration = buildConfiguration;
    }

    public ProductVersion getProductVersion() {
        return productVersion;
    }

    public void setProductVersion(ProductVersion productVersion) {
        this.productVersion = productVersion;
    }

    public List<Artifact> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<Artifact> artifacts) {
        this.artifacts = artifacts;
    }

    public List<Artifact> getRemoteArtifacts() {
        return remoteArtifacts;
    }

    public void setRemoteArtifacts(List<Artifact> remoteArtifacts) {
        this.remoteArtifacts = remoteArtifacts;
    }

    @JsonIgnore
    public boolean isImport() {
        return buildRecord == null || buildRecord.getScmRepoURL() == null;
    }

    @JsonIgnore
    public boolean isMaven() {
        return true;
    }

    @JsonIgnore
    public String getSource() {
        if (buildRecord != null) {
            return buildRecord.getScmRepoURL();
        }

        return null;
    }

    @Override
    public String toString() {
        return "PncBuild [buildRecord=" + buildRecord + ", buildRecordPushResult=" + buildRecordPushResult + ", artifacts=" + artifacts + ", remoteArtifacts=" + remoteArtifacts + "]";
    }
}
