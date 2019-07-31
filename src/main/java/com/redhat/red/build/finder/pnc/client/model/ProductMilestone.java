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
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductMilestone implements Serializable {
    private static final long serialVersionUID = 3681651958081100768L;

    private Integer id;

    private String version;

    private Instant endDate;

    private Instant startingDate;

    private Instant plannedEndDate;

    private String downloadUrl;

    private String issueTrackerUrl;

    private Integer productVersionId;

    private Set<Integer> performedBuilds;

    private Set<Integer> distributedArtifactIds;

    private Integer productReleaseId;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public Instant getStartingDate() {
        return startingDate;
    }

    public void setStartingDate(Instant startingDate) {
        this.startingDate = startingDate;
    }

    public Instant getPlannedEndDate() {
        return plannedEndDate;
    }

    public void setPlannedEndDate(Instant plannedEndDate) {
        this.plannedEndDate = plannedEndDate;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getIssueTrackerUrl() {
        return issueTrackerUrl;
    }

    public void setIssueTrackerUrl(String issueTrackerUrl) {
        this.issueTrackerUrl = issueTrackerUrl;
    }

    public Integer getProductVersionId() {
        return productVersionId;
    }

    public void setProductVersionId(Integer productVersionId) {
        this.productVersionId = productVersionId;
    }

    public Set<Integer> getPerformedBuilds() {
        return performedBuilds;
    }

    public void setPerformedBuilds(Set<Integer> performedBuilds) {
        this.performedBuilds = performedBuilds;
    }

    public Set<Integer> getDistributedArtifactIds() {
        return distributedArtifactIds;
    }

    public void setDistributedArtifactIds(Set<Integer> distributedArtifactIds) {
        this.distributedArtifactIds = distributedArtifactIds;
    }

    public Integer getProductReleaseId() {
        return productReleaseId;
    }

    public void setProductReleaseId(Integer productReleaseId) {
        this.productReleaseId = productReleaseId;
    }

    @Override
    public String toString() {
        return "ProductMilestone [id=" + id + ", version=" + version + ", endDate=" + endDate + ", startingDate="
                + startingDate + ", plannedEndDate=" + plannedEndDate + ", downloadUrl=" + downloadUrl
                + ", issueTrackerUrl=" + issueTrackerUrl + ", productVersionId=" + productVersionId
                + ", performedBuilds=" + performedBuilds + ", distributedArtifactIds=" + distributedArtifactIds
                + ", productReleaseId=" + productReleaseId + "]";
    }
}
