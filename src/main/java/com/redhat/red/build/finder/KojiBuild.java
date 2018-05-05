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

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildRequest;
import com.redhat.red.build.koji.model.xmlrpc.KojiTagInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskRequest;

public class KojiBuild {
    private KojiBuildInfo buildInfo;

    private KojiTaskInfo taskInfo;

    private KojiTaskRequest taskRequest;

    private List<KojiLocalArchive> archives;

    private List<KojiArchiveInfo> remoteArchives;

    private List<KojiTagInfo> tags;

    private List<String> types;

    private List<KojiArchiveInfo> duplicateArchives;

    public KojiBuild() {

    }

    public KojiBuild(KojiBuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    public KojiBuild(KojiBuildInfo buildInfo, KojiTaskInfo taskInfo, KojiTaskRequest taskRequest, List<KojiLocalArchive> archives, List<KojiArchiveInfo> remoteArchives, List<KojiTagInfo> tags, List<String> types) {
        this.buildInfo = buildInfo;
        this.taskInfo = taskInfo;
        this.taskRequest = taskRequest;
        this.archives = archives;
        this.remoteArchives = remoteArchives;
        this.tags = tags;
        this.types = types;
    }

    public KojiBuildInfo getBuildInfo() {
        return buildInfo;
    }

    public void setBuildInfo(KojiBuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    public KojiTaskInfo getTaskInfo() {
        return taskInfo;
    }

    public void setTaskInfo(KojiTaskInfo taskInfo) {
        this.taskInfo = taskInfo;
    }

    public KojiTaskRequest getTaskRequest() {
        return taskRequest;
    }

    public void setTaskRequest(KojiTaskRequest taskRequest) {
        this.taskRequest = taskRequest;
    }

    public List<KojiLocalArchive> getArchives() {
        return archives;
    }

    public void setArchives(List<KojiLocalArchive> archives) {
        this.archives = archives;
    }

    public List<KojiArchiveInfo> getRemoteArchives() {
        return remoteArchives;
    }

    public void setRemoteArchives(List<KojiArchiveInfo> remoteArchives) {
        this.remoteArchives = remoteArchives;
    }

    public List<KojiTagInfo> getTags() {
        return tags;
    }

    public void setTags(List<KojiTagInfo> tags) {
        this.tags = tags;
    }

    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }

    public List<KojiArchiveInfo> getDuplicateArchives() {
        return duplicateArchives;
    }

    public void setDuplicateArchives(List<KojiArchiveInfo> duplicateArchives) {
        this.duplicateArchives = duplicateArchives;
    }

    public boolean containsDuplicateArchive(KojiArchiveInfo archive) {
        return (duplicateArchives != null && duplicateArchives.contains(archive));
    }

    @JsonIgnore
    public KojiArchiveInfo getProjectSourcesTgz() {
        String mavenVersion = buildInfo.getMavenVersion();
        KojiArchiveInfo sourcesZip = null;

        if (remoteArchives != null && mavenVersion != null) {
            String sourcesZipFilename = buildInfo.getMavenArtifactId() + "-" + buildInfo.getMavenVersion() + "-project-sources.tar.gz";
            sourcesZip = remoteArchives.stream().filter(sArchive -> sArchive.getFilename().equals(sourcesZipFilename)).findFirst().orElse(null);
            return sourcesZip;
        }

        return null;
    }

    @JsonIgnore
    public KojiArchiveInfo getSourcesZip() {
        String mavenVersion = buildInfo.getMavenVersion();
        KojiArchiveInfo sourcesZip = null;

        if (remoteArchives != null && mavenVersion != null) {
            String sourcesZipFilename = buildInfo.getMavenArtifactId() + "-" + buildInfo.getMavenVersion() + "-scm-sources.zip";
            sourcesZip = remoteArchives.stream().filter(sArchive -> sArchive.getFilename().equals(sourcesZipFilename)).findFirst().orElse(null);
            return sourcesZip;
        }

        return null;
    }

    @JsonIgnore
    public KojiArchiveInfo getPatchesZip() {
        String mavenVersion = buildInfo.getMavenVersion();
        KojiArchiveInfo patchesZip = null;

        if (remoteArchives != null && mavenVersion != null) {
            String patchesZipFilename = buildInfo.getMavenArtifactId() + "-" + buildInfo.getMavenVersion() + "-patches.zip";
            patchesZip = remoteArchives.stream().filter(pArchive -> pArchive.getFilename().equals(patchesZipFilename)).findFirst().orElse(null);
            return patchesZip;
        }

        return null;
    }

    @JsonIgnore
    public boolean isImport() {
        return !((buildInfo != null && buildInfo.getExtra() != null && buildInfo.getExtra().containsKey("build_system")) || (taskInfo != null));
    }

    @JsonIgnore
    public boolean isMaven() {
        return ((buildInfo != null && buildInfo.getExtra() != null && buildInfo.getExtra().containsKey("maven")) || (taskInfo != null && taskInfo.getMethod() != null && taskInfo.getMethod().equals("maven")));
    }

    @JsonIgnore
    public String getSource() {
        if (buildInfo != null) {
            String source = buildInfo.getSource();

            if (source != null) {
                return source;
            }
        }

        if (taskRequest != null) {
            KojiBuildRequest buildRequest = taskRequest.asBuildRequest();

            if (buildRequest != null) {
                String source = buildRequest.getSource();

                if (source != null) {
                    return source;
                }
            }
        }

        return null;
    }

    @JsonIgnore
    public String getMethod() {
        if (taskInfo != null) {
            return taskInfo.getMethod();
        }

        if (buildInfo != null) {
            Map<String, Object> extra = buildInfo.getExtra();

            if (extra == null) {
                return null;
            }

            if (extra.containsKey("build_system")) {
                String buildSystem = (String) extra.get("build_system");

                if (extra.containsKey("version")) {
                    String version = (String) extra.get("version");

                    if (version != null) {
                        buildSystem += (" " + version);
                    }
                }

                return buildSystem;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return "KojiBuild [buildInfo=" + buildInfo + ", taskInfo=" + taskInfo + ", taskRequest=" + taskRequest
                + ", archives=" + archives + ", remoteArchives=" + remoteArchives + ", tags=" + tags
                + ", duplicateArchives=" + duplicateArchives + "]";
    }
}
