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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductVersion implements Serializable {
    private static final long serialVersionUID = -5174618882698318165L;

    private Integer id;

    private String version;

    private Integer productId;

    private String productName;

    private Integer currentProductMilestoneId;

    private List<ProductMilestone> productMilestones = new ArrayList<>();

    private List<ProductRelease> productReleases = new ArrayList<>();

    private List<BuildConfigurationSet> buildConfigurationSets = new ArrayList<>();

    private List<BuildConfiguration> buildConfigurations = new ArrayList<>();

    private Map<String, String> attributes = new HashMap<>();

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

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getCurrentProductMilestoneId() {
        return currentProductMilestoneId;
    }

    public void setCurrentProductMilestoneId(Integer currentProductMilestoneId) {
        this.currentProductMilestoneId = currentProductMilestoneId;
    }

    public List<ProductMilestone> getProductMilestones() {
        return productMilestones;
    }

    public void setProductMilestones(List<ProductMilestone> productMilestones) {
        this.productMilestones = productMilestones;
    }

    public List<ProductRelease> getProductReleases() {
        return productReleases;
    }

    public void setProductReleases(List<ProductRelease> productReleases) {
        this.productReleases = productReleases;
    }

    public List<BuildConfigurationSet> getBuildConfigurationSets() {
        return buildConfigurationSets;
    }

    public void setBuildConfigurationSets(List<BuildConfigurationSet> buildConfigurationSets) {
        this.buildConfigurationSets = buildConfigurationSets;
    }

    public List<BuildConfiguration> getBuildConfigurations() {
        return buildConfigurations;
    }

    public void setBuildConfigurations(List<BuildConfiguration> buildConfigurations) {
        this.buildConfigurations = buildConfigurations;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String toString() {
        return "ProductVersion [id=" + id + ", version=" + version + ", productId=" + productId + ", productName="
            + productName + ", currentProductMilestoneId=" + currentProductMilestoneId + ", productMilestones="
            + productMilestones + ", productReleases=" + productReleases + ", buildConfigurationSets="
            + buildConfigurationSets + ", buildConfigurations=" + buildConfigurations + ", attributes=" + attributes
            + "]";
    }
}
