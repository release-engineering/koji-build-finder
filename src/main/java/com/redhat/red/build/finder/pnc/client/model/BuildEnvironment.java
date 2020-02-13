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
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BuildEnvironment implements Serializable {
    private static final long serialVersionUID = 183921986004102213L;

    private Integer id;

    private String name;

    private String description;

    private String systemImageRepositoryUrl;

    private String systemImageId;

    private Map<String, String> attributes;

    private SystemImageType systemImageType;

    private boolean deprecated;

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

    public String getSystemImageRepositoryUrl() {
        return systemImageRepositoryUrl;
    }

    public void setSystemImageRepositoryUrl(String systemImageRepositoryUrl) {
        this.systemImageRepositoryUrl = systemImageRepositoryUrl;
    }

    public String getSystemImageId() {
        return systemImageId;
    }

    public void setSystemImageId(String systemImageId) {
        this.systemImageId = systemImageId;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public SystemImageType getSystemImageType() {
        return systemImageType;
    }

    public void setSystemImageType(SystemImageType systemImageType) {
        this.systemImageType = systemImageType;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    @Override
    public String toString() {
        return "BuildEnvironment [id=" + id + ", name=" + name + ", description=" + description
            + ", systemImageRepositoryUrl=" + systemImageRepositoryUrl + ", systemImageId=" + systemImageId
            + ", attributes=" + attributes + ", systemImageType=" + systemImageType + ", deprecated=" + deprecated
            + "]";
    }
}
