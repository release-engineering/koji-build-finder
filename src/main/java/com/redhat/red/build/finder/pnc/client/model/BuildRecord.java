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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BuildRecord implements Serializable {
    private static final long serialVersionUID = -1025603827514738142L;

    private Integer id;

    private Instant submitTime;

    private Instant startTime;

    private Instant endTime;

    private Integer buildConfigurationId;

    private String buildConfigurationName;

    private Integer buildConfigurationRev;

    private Integer projectId;

    private String projectName;

    private Integer userId;

    private String username;

    private String scmRepoURL;

    private String scmRevision;

    private Integer buildEnvironmentId;

    private Map<String, String> attributes = new HashMap<>();

    private String liveLogsUri;

    private Integer buildConfigSetRecordId;

    private String buildContentId;

    private Boolean temporaryBuild;

    /**
     * The IDs of the build record sets which represent the builds performed for a milestone to which this build record belongs
     */
    private Integer productMilestoneId;

    private String executionRootName;

    private String executionRootVersion;

    private List<Integer> dependentBuildRecordIds;

    private List<Integer> dependencyBuildRecordIds;

    private BuildConfigurationRevision buildConfigRevision;

    private Project project;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Instant getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(Instant submitTime) {
        this.submitTime = submitTime;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public Integer getBuildConfigurationId() {
        return buildConfigurationId == null
               ? buildConfigRevision.getId() : //PNC2
               buildConfigurationId;
    }

    public void setBuildConfigurationId(Integer buildConfigurationId) {
        this.buildConfigurationId = buildConfigurationId;
    }

    public String getBuildConfigurationName() {
        return buildConfigurationName;
    }

    public void setBuildConfigurationName(String buildConfigurationName) {
        this.buildConfigurationName = buildConfigurationName;
    }

    public Integer getBuildConfigurationRev() {
        return buildConfigurationRev;
    }

    public void setBuildConfigurationRev(Integer buildConfigurationRev) {
        this.buildConfigurationRev = buildConfigurationRev;
    }

    public Integer getProjectId() {
        return projectId == null
               ? project.getId() : //PNC2
               projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName == null
               ? project.getName() : //PNC2
               projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getScmRepoURL() {
        return scmRepoURL;
    }

    public void setScmRepoURL(String scmRepoURL) {
        this.scmRepoURL = scmRepoURL;
    }

    public String getScmRevision() {
        return scmRevision;
    }

    public void setScmRevision(String scmRevision) {
        this.scmRevision = scmRevision;
    }

    public Integer getBuildEnvironmentId() {
        return buildEnvironmentId;
    }

    public void setBuildEnvironmentId(Integer buildEnvironmentId) {
        this.buildEnvironmentId = buildEnvironmentId;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public String getLiveLogsUri() {
        return liveLogsUri;
    }

    public void setLiveLogsUri(String liveLogsUri) {
        this.liveLogsUri = liveLogsUri;
    }

    public Integer getBuildConfigSetRecordId() {
        return buildConfigSetRecordId;
    }

    public void setBuildConfigSetRecordId(Integer buildConfigSetRecordId) {
        this.buildConfigSetRecordId = buildConfigSetRecordId;
    }

    public String getBuildContentId() {
        return buildContentId;
    }

    public void setBuildContentId(String buildContentId) {
        this.buildContentId = buildContentId;
    }

    public Boolean getTemporaryBuild() {
        return temporaryBuild;
    }

    public void setTemporaryBuild(Boolean temporaryBuild) {
        this.temporaryBuild = temporaryBuild;
    }

    public Integer getProductMilestoneId() {
        return productMilestoneId;
    }

    public void setProductMilestoneId(Integer productMilestoneId) {
        this.productMilestoneId = productMilestoneId;
    }

    public String getExecutionRootName() {
        return executionRootName;
    }

    public void setExecutionRootName(String executionRootName) {
        this.executionRootName = executionRootName;
    }

    public String getExecutionRootVersion() {
        return executionRootVersion;
    }

    public void setExecutionRootVersion(String executionRootVersion) {
        this.executionRootVersion = executionRootVersion;
    }

    public List<Integer> getDependentBuildRecordIds() {
        return dependentBuildRecordIds;
    }

    public void setDependentBuildRecordIds(List<Integer> dependentBuildRecordIds) {
        this.dependentBuildRecordIds = dependentBuildRecordIds;
    }

    public List<Integer> getDependencyBuildRecordIds() {
        return dependencyBuildRecordIds;
    }

    public void setDependencyBuildRecordIds(List<Integer> dependencyBuildRecordIds) {
        this.dependencyBuildRecordIds = dependencyBuildRecordIds;
    }

    public BuildConfigurationRevision getBuildConfigRevision() {
        return buildConfigRevision;
    }

    public void setBuildConfigRevision(
        BuildConfigurationRevision buildConfigRevision) {
        this.buildConfigRevision = buildConfigRevision;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    @Override
    public String toString() {
        return "BuildRecord [id=" + id + ", submitTime=" + submitTime + ", startTime=" + startTime + ", endTime="
            + endTime + ", buildConfigurationId=" + buildConfigurationId + ", buildConfigurationName="
            + buildConfigurationName + ", buildConfigurationRev=" + buildConfigurationRev + ", projectId="
            + projectId + ", projectName=" + projectName + ", userId=" + userId + ", username=" + username
            + ", scmRepoURL=" + scmRepoURL + ", scmRevision=" + scmRevision + ", buildEnvironmentId="
            + buildEnvironmentId + ", attributes=" + attributes + ", liveLogsUri=" + liveLogsUri
            + ", buildConfigSetRecordId=" + buildConfigSetRecordId + ", buildContentId=" + buildContentId
            + ", temporaryBuild=" + temporaryBuild + ", productMilestoneId=" + productMilestoneId
            + ", executionRootName=" + executionRootName + ", executionRootVersion=" + executionRootVersion
            + ", dependentBuildRecordIds=" + dependentBuildRecordIds
            + ", dependencyBuildRecordIds=" + dependencyBuildRecordIds + "]";
    }
}
