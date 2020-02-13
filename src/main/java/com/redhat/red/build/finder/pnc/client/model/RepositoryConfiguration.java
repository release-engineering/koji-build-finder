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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RepositoryConfiguration implements Serializable {
    private static final long serialVersionUID = -6944872650681001367L;

    private Integer id;

    private String internalUrl;

    private String externalUrl;

    private Boolean preBuildSyncEnabled;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getInternalUrl() {
        return internalUrl;
    }

    public void setInternalUrl(String internalUrl) {
        this.internalUrl = internalUrl;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public Boolean getPreBuildSyncEnabled() {
        return preBuildSyncEnabled;
    }

    public void setPreBuildSyncEnabled(Boolean preBuildSyncEnabled) {
        this.preBuildSyncEnabled = preBuildSyncEnabled;
    }

    @Override
    public String toString() {
        return "RepositoryConfiguration [id=" + id + ", internalUrl=" + internalUrl + ", externalUrl=" + externalUrl
            + ", preBuildSyncEnabled=" + preBuildSyncEnabled + "]";
    }
}
