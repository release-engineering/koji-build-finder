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

import static com.redhat.red.build.finder.AnsiUtils.cyan;
import static com.redhat.red.build.finder.AnsiUtils.green;
import static com.redhat.red.build.finder.AnsiUtils.red;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveQuery;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveType;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildState;
import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;
import com.redhat.red.build.koji.model.xmlrpc.KojiTagInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskRequest;

public class BuildFinder {
    private static final String NAME = "koji-build-finder";

    private static final String CHECKSUMS_FILENAME_BASENAME = "checksums-";

    private static final String BUILDS_FILENAME = "builds.json";

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildFinder.class);

    private ClientSession session;

    private BuildConfig config;

    public BuildFinder(ClientSession session, BuildConfig config) {
        this.session = session;
        this.config = config;
    }

    public Map<Integer, KojiBuild> findBuilds(Map<String, Collection<String>> checksumTable) {
        if (checksumTable == null || checksumTable.isEmpty()) {
            LOGGER.warn("Checksum table is empty");
            return Collections.emptyMap();
        }

        Set<String> extensionsToCheck = new TreeSet<>();
        final Instant startTime = Instant.now();

        try {
            Map<String, KojiArchiveType> allTypesMap = session.getArchiveTypeMap();
            Set<String> allTypes = allTypesMap.values().stream().map(KojiArchiveType::getName).collect(Collectors.toSet());

            LOGGER.debug("There are {} known Koji archive types: {}", allTypes.size(), allTypes);

            List<String> archiveTypes = config.getArchiveTypes();
            Set<String> typesToCheck = null;

            if (archiveTypes != null && !archiveTypes.isEmpty()) {
                typesToCheck = archiveTypes.stream().collect(Collectors.toSet());
            } else {
                LOGGER.warn("Supplied archive types list is empty; defaulting to all known archive types");
                typesToCheck = allTypes;
            }

            LOGGER.debug("There are {} Koji archive types to check: {}", typesToCheck.size(), typesToCheck);

            typesToCheck.stream().filter(allTypesMap::containsKey).map(allTypesMap::get).map(archiveType -> {
                LOGGER.debug("Adding archive type to check: {}", archiveType);
                return archiveType.getExtensions();
            }).forEach(extensionsToCheck::addAll);
        } catch (KojiClientException e) {
            LOGGER.error("Koji client error", e);
            session.close();
            return Collections.emptyMap();
        }

        LOGGER.info("Looking up files with extensions matching: {}", green(extensionsToCheck));

        Map<Integer, KojiBuild> builds = new HashMap<>();
        KojiBuildInfo buildInfo = new KojiBuildInfo();
        buildInfo.setId(0);
        buildInfo.setPackageId(0);
        buildInfo.setBuildState(KojiBuildState.COMPLETE);
        buildInfo.setName("not found");
        buildInfo.setVersion("not found");
        buildInfo.setRelease("not found");
        List<KojiLocalArchive> archiveList = new ArrayList<>();
        KojiBuild build = new KojiBuild();
        build.setBuildInfo(buildInfo);
        build.setArchives(archiveList);
        builds.put(0, build);

        int checked = 0;
        int total = checksumTable.keySet().size();
        int hits = 0;

        final String EMPTY_MD5 = Hex.encodeHexString(DigestUtils.getDigest(config.getChecksumType().getAlgorithm()).digest());

        LOGGER.info("Number of checksums: {}", green(total));

        for (Entry<String, Collection<String>> entry : checksumTable.entrySet()) {
            checked++;

            String checksum = entry.getKey();

            if (checksum.equals(EMPTY_MD5)) {
                LOGGER.debug("Found empty file for checksum", checksum);
                continue;
            }

            Collection<String> filenames = checksumTable.get(checksum);
            boolean foundExt = false;
            List<String> excludes = config.getExcludes();

            for (String filename : filenames) {
                LOGGER.debug("Checking checksum {} and filename {}", checksum, filename);

                boolean exclude = false;

                if (excludes != null && !excludes.isEmpty()) {
                    exclude = excludes.stream().anyMatch(filename::matches);
                }

                if (exclude) {
                    LOGGER.debug("Skipping filename {} because it matches the excludes list", filename);
                    continue;
                }

                for (String ext : extensionsToCheck) {
                    if (filename.endsWith("." + ext)) {
                        foundExt = true;
                        LOGGER.debug("Matched extension {} for checksum {}: {}", checksum, ext);
                        break;
                    }
                }
            }

            if (!foundExt) {
                LOGGER.debug("Skipping {} : {} due to extension not found", checksum, filenames);
                continue;
            }

            List<KojiArchiveInfo> archives;

            try {
                LOGGER.debug("Looking up archives for checksum: {}", checksum);
                archives = session.listArchives(new KojiArchiveQuery().withChecksum(checksum));
            } catch (KojiClientException e) {
                LOGGER.error("Koji client error", e);
                continue;
            }

            if (archives == null || archives.isEmpty()) {
                LOGGER.debug("Got empty archive list for checksum: {}", checksum);

                KojiArchiveInfo tmpArchive = new KojiArchiveInfo();
                tmpArchive.setBuildId(0);
                tmpArchive.setArchiveId(-1);
                tmpArchive.setFilename("not found");
                tmpArchive.setChecksum(checksum);
                tmpArchive.setChecksumType(config.getChecksumType());

                build = builds.get(0);
                tmpArchive.setArchiveId(-1 * (build.getArchives().size() + 1));
                build.getArchives().add(new KojiLocalArchive(tmpArchive, new ArrayList<>(filenames)));

                continue;
            }

            List<KojiArchiveInfo> archivesToRemove = new ArrayList<>();

            LOGGER.debug("Found {} archives for checksum: {}", archives.size(), checksum);

            int firstBuildId = -1;

            for (KojiArchiveInfo archive : archives) {
                if (archive.getChecksumType() != config.getChecksumType()) {
                    LOGGER.warn("Skipping archive id {} as checksum is not {}, but is {}", config.getChecksumType(), archive.getArchiveId(), archive.getChecksumType());
                    archivesToRemove.add(archive);
                    continue;
                }

                KojiTaskInfo taskInfo = null;
                KojiTaskRequest taskRequest = null;
                List<KojiArchiveInfo> allArchives = null;
                List<KojiTagInfo> tags = null;

                try {
                    if (builds.containsKey(archive.getBuildId())) {
                        build = builds.get(archive.getBuildId());
                        buildInfo = build.getBuildInfo();
                        taskInfo = build.getTaskInfo();
                        taskRequest = build.getTaskRequest();
                        archiveList = build.getArchives();
                        tags = build.getTags();
                        hits++;

                        LOGGER.info("Found build: id: {} nvr: {} checksum: {} archive: {} [{} / {} = {}%]", green(buildInfo.getId()), green(buildInfo.getNvr()), green(checksum), green(archive.getFilename()), cyan(checked), cyan(total), cyan(String.format("%.3f", (checked / (double) total) * 100)));

                        if (buildInfo.getBuildState() != KojiBuildState.COMPLETE) {
                            LOGGER.debug("Skipping incomplete build id {}", buildInfo.getId());
                            archivesToRemove.add(archive);
                            continue;
                        }

                        if (tags.isEmpty()) {
                            LOGGER.warn("Skipping build id {} due to no tags", buildInfo.getId());
                            archivesToRemove.add(archive);
                            continue;
                        }

                        /* Ignore imports when the artifact was also found in an earlier build */
                        if (taskInfo == null && firstBuildId != -1 && buildInfo.getId() > firstBuildId) {
                            LOGGER.warn("Skipping import id {} because artifact exists in build id {}", buildInfo.getId(), firstBuildId);
                            continue;
                        }

                        if (archiveList == null) {
                            LOGGER.warn("Null archive list for archive id {} to build id {}", archive.getArchiveId(), buildInfo.getId());
                            archiveList = new ArrayList<>();
                        }

                        KojiLocalArchive kla = new KojiLocalArchive(archive, null);

                        if (!archiveList.contains(kla)) {
                            LOGGER.debug("Adding archive id {} to build id {} already in table with {} archives", archive.getArchiveId(), archive.getBuildId(), build.getArchives().size());
                            archiveList.add(new KojiLocalArchive(archive, new ArrayList<>(filenames)));

                        } else {
                            int archiveIndex = archiveList.indexOf(kla);
                            KojiLocalArchive aArchive = archiveList.get(archiveIndex);

                            if (aArchive.getFiles() == null) {
                                aArchive.setFiles(new ArrayList<>(filenames));
                            }
                        }
                    } else {
                        LOGGER.debug("Build id {} not in table, looking up", archive.getBuildId());
                        buildInfo = session.getBuild(archive.getBuildId());

                        if (buildInfo != null) {
                            if (buildInfo.getBuildState() != KojiBuildState.COMPLETE) {
                                LOGGER.debug("Skipping incomplete build id {}, nvr {} archive file {} with checksum {}, skipping", buildInfo.getId(), buildInfo.getNvr(), archive.getFilename(), checksum);

                                archivesToRemove.add(archive);

                                build = new KojiBuild();
                                build.setBuildInfo(buildInfo);
                                builds.put(archive.getBuildId(), build);

                                continue;
                            }

                            tags = session.listTags(buildInfo.getId());

                            if (tags.isEmpty()) {
                                LOGGER.debug("Skipping build id {} due to no tags", buildInfo.getId());
                                archivesToRemove.add(archive);
                                continue;
                            }

                            allArchives = session.listArchives(new KojiArchiveQuery().withBuildId(buildInfo.getId()));

                            if (buildInfo.getTaskId() != null) {
                                boolean useTaskRequest = true;

                                if (!useTaskRequest) {
                                    taskInfo = session.getTaskInfo(buildInfo.getTaskId(), true);
                                } else {
                                    taskInfo = session.getTaskInfo(buildInfo.getTaskId(), false);
                                }

                                if (taskInfo != null) {
                                    LOGGER.debug("Found task info task id {} for build id {} using method {}", taskInfo.getTaskId(), buildInfo.getId(), taskInfo.getMethod());

                                    /* Track first build that is not an import */
                                    if (firstBuildId == -1 || buildInfo.getId() < firstBuildId) {
                                        firstBuildId = buildInfo.getId();
                                    }

                                    if (!useTaskRequest) {
                                        List<Object> request = taskInfo.getRequest();

                                        if (request != null) {
                                            LOGGER.debug("Got task request for build id {}: {}", buildInfo.getId(), request);
                                            taskRequest = new KojiTaskRequest(request);
                                        } else {
                                            LOGGER.warn("Null task request for build id {} with task id {} and checksum {}", buildInfo.getId(), taskInfo.getTaskId(), checksum);
                                        }
                                    } else {
                                        taskRequest = session.getTaskRequest(buildInfo.getTaskId());
                                    }
                                } else {
                                    LOGGER.warn("Task info not found for build id {}", buildInfo.getId());
                                }
                            } else {
                                LOGGER.warn("Found import for build id {} with checksum {} and files {}", red(buildInfo.getId()), red(checksum), red(checksumTable.get(checksum)));
                            }

                            /* Ignore imports when the artifact was also found in an earlier build */
                            if (buildInfo.getTaskId() == null && firstBuildId != -1 && buildInfo.getId() > firstBuildId) {
                                LOGGER.warn("Skipping import id {} because artifact exists in build id {}", red(buildInfo.getId()), red(firstBuildId));
                            } else {
                                archiveList = new ArrayList<>();
                                archiveList.add(new KojiLocalArchive(archive, new ArrayList<>(filenames)));

                                List<String> buildTypes = null;

                                if (buildInfo.getTypeNames() != null) {
                                    buildTypes = new ArrayList<>();
                                    buildTypes.addAll(buildInfo.getTypeNames());
                                }

                                build = new KojiBuild(buildInfo, taskInfo, taskRequest, archiveList, allArchives, tags, buildTypes);
                                builds.put(archive.getBuildId(), build);
                                LOGGER.info("Found build: id: {} nvr: {} checksum: {} archive: {} [{} / {} = {}%]", green(buildInfo.getId()), green(buildInfo.getNvr()), green(checksum), green(archive.getFilename()), cyan(checked), cyan(total), cyan(String.format("%.3f", (checked / (double) total) * 100)));
                            }
                        } else {
                            LOGGER.warn("Build not found for checksum {}. This is never supposed to happen", checksum);
                        }
                    }
                } catch (KojiClientException e) {
                    LOGGER.error("Koji client error", e);
                    continue;
                }
            }

            archives.removeAll(archivesToRemove);

            if (archives.size() != 1) {
                LOGGER.warn("Found {} archives with checksum {}", red(archives.size()), red(checksum));

                archives.forEach(archive -> {
                    KojiBuild duplicateBuild = builds.get(archive.getBuildId());

                    if (duplicateBuild != null) {
                        if (duplicateBuild.getDuplicateArchives() == null) {
                            List<KojiArchiveInfo> duplicateArchiveList = new ArrayList<>();
                            duplicateArchiveList.add(archive);
                            duplicateBuild.setDuplicateArchives(duplicateArchiveList);
                        } else {
                            if (!duplicateBuild.getDuplicateArchives().contains(archive)) {
                                duplicateBuild.getDuplicateArchives().add(archive);
                            }
                        }
                    }
                });
            }
        }

        session.close();

        final Instant endTime = Instant.now();
        final Duration duration = Duration.between(startTime, endTime).abs();
        int numChecksums = checksumTable.keySet().size();
        int numBuilds = builds.keySet().size() - 1;

        LOGGER.info("Total number of files: {}, checked: {}, skipped: {}, hits: {}, time: {}, average: {}", green(numChecksums), green(numBuilds), green(numChecksums - numBuilds), green(hits), green(duration), green(numBuilds > 0 ? duration.dividedBy(numBuilds) : 0));

        LOGGER.debug("Found {} total builds", numBuilds);

        builds.values().removeIf(b -> b.getBuildInfo().getBuildState() != KojiBuildState.COMPLETE);

        numBuilds = builds.keySet().size() - 1;

        LOGGER.info("Found {} builds", green(numBuilds));

        return builds;
    }

    public static String getName() {
        return NAME;
    }

    public static String getVersion() {
        Package p = BuildFinder.class.getPackage();

        return ((p == null || p.getImplementationVersion() == null) ? "unknown" : p.getImplementationVersion());
    }

    /**
     * Retrieves the SHA this was built with.
     *
     * @return the GIT sha of this codebase.
     */
    public static String getScmRevision() {
        String scmRevision = "unknown";

        try {
            Enumeration<URL> resources = BuildFinder.class.getClassLoader().getResources("META-INF/MANIFEST.MF");

            while (resources.hasMoreElements()) {
                URL jarUrl = resources.nextElement();

                if (jarUrl.getFile().contains(NAME)) {
                    Manifest manifest = new Manifest(jarUrl.openStream());
                    String manifestValue = manifest.getMainAttributes().getValue("Scm-Revision");

                    if (manifestValue != null && !manifestValue.isEmpty()) {
                        scmRevision = manifestValue;
                    }

                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.error("Unexpected exception processing jar file", e);
        }

        return scmRevision;
    }

    public static String getChecksumFilename(KojiChecksumType checksumType) {
        return CHECKSUMS_FILENAME_BASENAME + checksumType + ".json";
    }

    public static String getBuildsFilename() {
        return BUILDS_FILENAME;
    }
}
