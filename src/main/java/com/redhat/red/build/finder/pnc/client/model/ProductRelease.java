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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductRelease implements Serializable {
    private static final long serialVersionUID = -1435218246640235650L;

    private Integer id;

    private String version;

    private Instant releaseDate;

    private String downloadUrl;

    private String issueTrackerUrl;

    private Integer productVersionId;

    private Integer productMilestoneId;

    private SupportLevel supportLevel;

    public enum SupportLevel {
        UNRELEASED, EARLYACCESS, SUPPORTED, EXTENDED_SUPPORT, EOL
    }

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

    public Instant getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(Instant releaseDate) {
        this.releaseDate = releaseDate;
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

    public Integer getProductMilestoneId() {
        return productMilestoneId;
    }

    public void setProductMilestoneId(Integer productMilestoneId) {
        this.productMilestoneId = productMilestoneId;
    }

    public SupportLevel getSupportLevel() {
        return supportLevel;
    }

    public void setSupportLevel(SupportLevel supportLevel) {
        this.supportLevel = supportLevel;
    }

    @Override
    public String toString() {
        return "ProductRelease [id=" + id + ", version=" + version + ", releaseDate=" + releaseDate + ", downloadUrl="
            + downloadUrl + ", issueTrackerUrl=" + issueTrackerUrl + ", productVersionId=" + productVersionId
            + ", productMilestoneId=" + productMilestoneId + ", supportLevel=" + supportLevel + "]";
    }
}
