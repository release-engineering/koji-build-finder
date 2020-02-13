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
import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BuildConfigurationSet implements Serializable {
    private static final long serialVersionUID = -6281571480871225209L;

    private Integer id;

    private String name;

    private Integer productVersionId;

    private List<Integer> buildConfigurationIds = new LinkedList<>();

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

    public Integer getProductVersionId() {
        return productVersionId;
    }

    public void setProductVersionId(Integer productVersionId) {
        this.productVersionId = productVersionId;
    }

    public List<Integer> getBuildConfigurationIds() {
        return buildConfigurationIds;
    }

    public void setBuildConfigurationIds(List<Integer> buildConfigurationIds) {
        this.buildConfigurationIds = buildConfigurationIds;
    }

    @Override
    public String toString() {
        return "BuildConfigurationSet [id=" + id + ", name=" + name + ", productVersionId=" + productVersionId
            + ", buildConfigurationIds=" + buildConfigurationIds + "]";
    }
}
