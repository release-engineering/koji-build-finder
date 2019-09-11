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
package com.redhat.red.build.finder.pnc.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.redhat.red.build.finder.KojiBuild;
import com.redhat.red.build.finder.PncBuild;
import com.redhat.red.build.finder.pnc.client.model.Artifact;
import com.redhat.red.build.finder.pnc.client.model.BuildRecord;
import com.redhat.red.build.finder.pnc.client.model.BuildRecordPushResult;
import com.redhat.red.build.finder.pnc.client.model.ProductVersion;
import com.redhat.red.build.koji.model.json.KojiJsonConstants;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildState;
import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;
import com.redhat.red.build.koji.model.xmlrpc.KojiTagInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskRequest;

public final class PncUtils {
    private PncUtils() {
        throw new IllegalArgumentException();
    }

    private static void setMavenBuildInfoFromBuildRecord(BuildRecord record, KojiBuildInfo buildInfo) {
        String executionRootName = record.getExecutionRootName();
        String executionRootVersion = record.getExecutionRootVersion();
        String[] ga = executionRootName.split(":", 2);

        buildInfo.setMavenGroupId(ga[0]);
        buildInfo.setMavenArtifactId(ga[1]);
        buildInfo.setMavenVersion(executionRootVersion);
    }

    public static String getNVRFromBuildRecord(BuildRecord record) {
        return record.getExecutionRootName().replace(':', '-') + "-" + record.getExecutionRootVersion() + "-1";
    }

    public static KojiBuild pncBuildToKojiBuild(PncBuild pncbuild) {
        KojiBuild build = new KojiBuild();

        build.setTypes(Collections.singletonList("maven"));

        KojiBuildInfo buildInfo = new KojiBuildInfo();
        BuildRecord record = pncbuild.getBuildRecord();

        setMavenBuildInfoFromBuildRecord(record, buildInfo);

        buildInfo.setId(record.getId());
        buildInfo.setName(record.getExecutionRootName().replace(':', '-'));
        buildInfo.setVersion(record.getExecutionRootVersion() != null ? record.getExecutionRootVersion() : "0");
        buildInfo.setRelease("1");
        buildInfo.setNvr(buildInfo.getName() + "-" + buildInfo.getVersion().replace("-", "_") + "-" + buildInfo.getRelease().replace("-", "_"));
        buildInfo.setCreationTime(Date.from(record.getStartTime()));
        buildInfo.setCompletionTime(Date.from(record.getEndTime()));
        buildInfo.setBuildState(KojiBuildState.COMPLETE);
        buildInfo.setOwnerName(record.getUsername());
        buildInfo.setSource((record.getScmRepoURL().startsWith("http") ? "git+" : "") + record.getScmRepoURL() + (record.getScmRevision() != null ? "#" + record.getScmRevision() : ""));

        Map<String, Object> extra = new HashMap<>(5);

        extra.put(KojiJsonConstants.BUILD_SYSTEM, "PNC");
        extra.put(KojiJsonConstants.EXTERNAL_BUILD_ID, record.getId());
        // XXX: These aren't used by Koji, but we need them to create the hyperlinks for the HTML report
        extra.put("external_project_id", record.getProjectId());
        extra.put("external_build_configuration_id", record.getBuildConfigurationId());

        BuildRecordPushResult result = pncbuild.getBuildRecordPushResult();

        if (result != null) {
            extra.put("external_brew_build_id", result.getBrewBuildId());
            extra.put("external_brew_build_url", result.getBrewBuildUrl());
        }

        ProductVersion pv = pncbuild.getProductVersion();

        if (pv != null) {
            // XXX: These aren't used by Koji, but we need them to create the hyperlinks for the HTML report
            extra.put("external_product_id", pv.getProductId());
            extra.put("external_version_id", pv.getId());

            List<KojiTagInfo> tags = new ArrayList<>(1);
            KojiTagInfo tag = new KojiTagInfo();

            tag.setId(pv.getId());
            tag.setArches(Collections.singletonList("noarch"));

            String brewName = pv.getAttributes().get("BREW_TAG_PREFIX");
            String tagName = brewName != null ? brewName : pv.getProductName();

            tag.setName(tagName);

            tags.add(tag);

            build.setTags(tags);

            KojiTaskInfo taskInfo = new KojiTaskInfo();
            KojiTaskRequest taskRequest = new KojiTaskRequest();
            List<Object> request = new ArrayList<>(2);

            request.add(pncbuild.getSource());
            request.add(tagName);

            taskRequest.setRequest(request);

            taskInfo.setMethod("build");
            taskInfo.setRequest(request);

            build.setTaskInfo(taskInfo);
            build.setTaskRequest(taskRequest);
        }

        buildInfo.setExtra(extra);

        build.setBuildInfo(buildInfo);

        build.setPnc(true);

        return build;
    }

    public static KojiArchiveInfo artifactToKojiArchiveInfo(PncBuild pncbuild, Artifact artifact) {
        BuildRecord record = pncbuild.getBuildRecord();
        KojiArchiveInfo archiveInfo = new KojiArchiveInfo();

        archiveInfo.setBuildId(record.getId());
        archiveInfo.setArchiveId(artifact.getId());
        archiveInfo.setArch("noarch");
        archiveInfo.setFilename(artifact.getFilename());
        archiveInfo.setBuildType("maven"); // XXX: Pnc also has a gradle build type
        archiveInfo.setChecksumType(KojiChecksumType.md5);
        archiveInfo.setChecksum(artifact.getMd5());
        archiveInfo.setSize(artifact.getSize().intValue()); // XXX: Koji size should be long not int

        String[] gaecv = artifact.getIdentifier().split(":");

        if (gaecv.length >= 3) {
            archiveInfo.setGroupId(gaecv[0]);
            archiveInfo.setArtifactId(gaecv[1]);
            archiveInfo.setExtension(gaecv[2]);
            archiveInfo.setVersion(gaecv[3]);
            archiveInfo.setClassifier(gaecv.length > 4 ? gaecv[4] : null);
        }

        return archiveInfo;
    }

    public static void fixNullVersion(KojiBuild kojibuild, KojiArchiveInfo archiveInfo) {
        KojiBuildInfo buildInfo = kojibuild.getBuildInfo();
        String buildVersion = buildInfo.getVersion();
        String version = archiveInfo.getVersion();

        if (buildVersion == null || buildVersion.equals("0")) {
            buildInfo.setVersion(version);
            buildInfo.setNvr(buildInfo.getName() + "-" + buildInfo.getVersion().replace("-", "_") + "-" + buildInfo.getRelease().replace("-", "_"));
        }
    }
}
