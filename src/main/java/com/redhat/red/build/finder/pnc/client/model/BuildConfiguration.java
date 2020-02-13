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
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BuildConfiguration implements Serializable {
    private static final long serialVersionUID = -2197547437692470686L;

    private Integer id;

    private String name;

    private String description;

    private String buildScript;

    private RepositoryConfiguration repositoryConfiguration;

    private String scmRevision;

    private Instant creationTime;

    private Instant lastModificationTime;

    private boolean archived;

    private Project project;

    private BuildType buildType;

    private BuildEnvironment environment;

    private Set<Integer> dependencyIds;

    private Integer productVersionId;

    private Set<Integer> buildConfigurationSetIds;

    private Map<String, String> genericParameters;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBuildScript() {
        return buildScript;
    }

    public void setBuildScript(String buildScript) {
        this.buildScript = buildScript;
    }

    public RepositoryConfiguration getRepositoryConfiguration() {
        return repositoryConfiguration;
    }

    public void setRepositoryConfiguration(RepositoryConfiguration repositoryConfiguration) {
        this.repositoryConfiguration = repositoryConfiguration;
    }

    public String getScmRevision() {
        return scmRevision;
    }

    public void setScmRevision(String scmRevision) {
        this.scmRevision = scmRevision;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Instant creationTime) {
        this.creationTime = creationTime;
    }

    public Instant getLastModificationTime() {
        return lastModificationTime;
    }

    public void setLastModificationTime(Instant lastModificationTime) {
        this.lastModificationTime = lastModificationTime;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public BuildType getBuildType() {
        return buildType;
    }

    public void setBuildType(BuildType buildType) {
        this.buildType = buildType;
    }

    public BuildEnvironment getEnvironment() {
        return environment;
    }

    public void setEnvironment(BuildEnvironment environment) {
        this.environment = environment;
    }

    public Set<Integer> getDependencyIds() {
        return dependencyIds;
    }

    public void setDependencyIds(Set<Integer> dependencyIds) {
        this.dependencyIds = dependencyIds;
    }

    public Integer getProductVersionId() {
        return productVersionId;
    }

    public void setProductVersionId(Integer productVersionId) {
        this.productVersionId = productVersionId;
    }

    public Set<Integer> getBuildConfigurationSetIds() {
        return buildConfigurationSetIds;
    }

    public void setBuildConfigurationSetIds(Set<Integer> buildConfigurationSetIds) {
        this.buildConfigurationSetIds = buildConfigurationSetIds;
    }

    public Map<String, String> getGenericParameters() {
        return genericParameters;
    }

    public void setGenericParameters(Map<String, String> genericParameters) {
        this.genericParameters = genericParameters;
    }

    @Override
    public String toString() {
        return "BuildConfiguration [id=" + id + ", name=" + name + ", description=" + description + ", buildScript="
            + buildScript + ", repositoryConfiguration=" + repositoryConfiguration + ", scmRevision=" + scmRevision
            + ", creationTime=" + creationTime + ", lastModificationTime=" + lastModificationTime + ", archived="
            + archived + ", project=" + project + ", buildType=" + buildType + ", environment=" + environment
            + ", dependencyIds=" + dependencyIds + ", productVersionId=" + productVersionId
            + ", buildConfigurationSetIds=" + buildConfigurationSetIds + ", genericParameters=" + genericParameters
            + "]";
    }
}
