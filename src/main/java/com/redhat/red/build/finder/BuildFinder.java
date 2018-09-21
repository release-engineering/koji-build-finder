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

import static com.redhat.red.build.finder.AnsiUtils.green;
import static com.redhat.red.build.finder.AnsiUtils.red;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
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

public class BuildFinder implements Callable<Map<Integer, KojiBuild>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildFinder.class);

    private static final String BUILDS_FILENAME = "builds.json";

    private static final String CHECKSUMS_FILENAME_BASENAME = "checksums-";

    private static String EMPTY_DIGEST;

    private ClientSession session;

    private BuildConfig config;

    private Map<Integer, KojiBuild> builds;

    private File outputDirectory;

    private MultiValuedMap<String, Integer> checksumMap;

    private List<String> archiveExtensions;

    private DistributionAnalyzer analyzer;

    private Cache<String, Integer> checksumCache;

    private Cache<Integer, KojiBuild> buildCache;

    private EmbeddedCacheManager cacheManager;

    public BuildFinder(ClientSession session, BuildConfig config) {
        this(session, config, null, null);
    }

    public BuildFinder(ClientSession session, BuildConfig config, DistributionAnalyzer analyzer) {
        this(session, config, analyzer, null);
    }

    public BuildFinder(ClientSession session, BuildConfig config, DistributionAnalyzer analyzer, EmbeddedCacheManager cacheManager) {
        this.session = session;
        this.config = config;
        this.outputDirectory = new File("");
        this.checksumMap = new ArrayListValuedHashMap<>();
        this.analyzer = analyzer;
        this.cacheManager = cacheManager;

        if (cacheManager != null) {
            this.buildCache = cacheManager.getCache("builds");
            this.checksumCache = cacheManager.getCache("checksums");
        }

        EMPTY_DIGEST = Hex.encodeHexString(DigestUtils.getDigest(config.getChecksumType().getAlgorithm()).digest());

        initBuilds();
    }

    private void initBuilds() {
        builds = new HashMap<>();

        KojiBuildInfo buildInfo = new KojiBuildInfo();

        buildInfo.setId(0);
        buildInfo.setPackageId(0);
        buildInfo.setBuildState(KojiBuildState.ALL);
        buildInfo.setName("not found");
        buildInfo.setVersion("not found");
        buildInfo.setRelease("not found");

        KojiBuild build = new KojiBuild(buildInfo);

        builds.put(0, build);
    }

    private List<String> getArchiveExtensions() throws KojiClientException {
        Map<String, KojiArchiveType> allArchiveTypesMap = session.getArchiveTypeMap();

        List<String> allArchiveTypes = allArchiveTypesMap.values().stream().map(KojiArchiveType::getName).collect(Collectors.toList());
        List<String> archiveTypes = config.getArchiveTypes();
        List<String> archiveTypesToCheck;

        LOGGER.info("Looking up files with extensions matching: {}", green(archiveTypes));

        if (archiveTypes != null && !archiveTypes.isEmpty()) {
            LOGGER.debug("There are {} supplied Koji archive types: {}", archiveTypes.size(), archiveTypes);
            archiveTypesToCheck = archiveTypes.stream().filter(allArchiveTypesMap::containsKey).collect(Collectors.toList());
            LOGGER.debug("There are {} valid supplied Koji archive types: {}", archiveTypes.size(), archiveTypes);
        } else {
            LOGGER.debug("There are {} known Koji archive types: {}", allArchiveTypes.size(), allArchiveTypes);
            LOGGER.warn("Supplied archive types list is empty; defaulting to all known archive types");
            archiveTypesToCheck = allArchiveTypes;
        }

        LOGGER.debug("There are {} Koji archive types to check: {}", archiveTypesToCheck.size(), archiveTypesToCheck);

        List<String> allArchiveExtensions = allArchiveTypesMap.values().stream().flatMap(at -> at.getExtensions().stream()).collect(Collectors.toList());
        List<String> archiveExtensions = config.getArchiveExtensions();
        List<String> archiveExtensionsToCheck;

        if (archiveExtensions != null && !archiveExtensions.isEmpty()) {
            LOGGER.debug("There are {} supplied Koji archive extensions: {}", archiveExtensions.size(), archiveExtensions);
            archiveExtensionsToCheck = archiveExtensions.stream().filter(allArchiveExtensions::contains).collect(Collectors.toList());
            LOGGER.debug("There are {} valid supplied Koji archive extensions: {}", archiveExtensions.size(), archiveExtensions);
        } else {
            LOGGER.debug("There are {} known Koji archive extensions: {}", allArchiveExtensions.size(), allArchiveExtensions.size());
            LOGGER.warn("Supplied archive extensions list is empty; defaulting to all known archive extensions");
            archiveExtensionsToCheck = allArchiveExtensions;
        }

        return archiveExtensionsToCheck;
    }

    private KojiBuild lookupBuild(int buildId, String checksum, KojiArchiveInfo archive, Collection<String> filenames) throws KojiClientException {
        KojiBuild cachedBuild = builds.get(buildId);

        if (cachedBuild != null) {
            LOGGER.debug("Build id: {} checksum: {} archive: {} filenames: {} is in cache", buildId, checksum, archive.getArchiveId(), filenames);

            addArchiveToBuild(cachedBuild, archive, filenames);

            return cachedBuild;
        }

        if (cacheManager != null) {
            cachedBuild = buildCache.get(buildId);

            if (cachedBuild != null) {
                builds.put(buildId, cachedBuild);

                LOGGER.debug("Build id: {} checksum: {} archive: {} filenames: {} is in global cache", buildId, checksum, archive.getArchiveId(), filenames);

                addArchiveToBuild(cachedBuild, archive, filenames);

                return cachedBuild;
            }
        }

        LOGGER.debug("Build id: {} checksum: {} archive: {} filenames: {} is not cached", buildId, checksum, archive.getArchiveId(), filenames);

        KojiBuildInfo buildInfo = session.getBuild(buildId);

        if (buildInfo == null) {
            LOGGER.warn("Build not found for checksum {}. This is never supposed to happen", red(checksum));
            return null;
        }

        List<KojiTagInfo> tags = session.listTags(buildInfo.getId());
        List<KojiArchiveInfo> allArchives = session.listArchives(new KojiArchiveQuery().withBuildId(buildInfo.getId()));

        KojiBuild build = new KojiBuild(buildInfo);

        build.setRemoteArchives(allArchives);

        if (buildInfo.getTaskId() != null) {
            KojiTaskInfo taskInfo = session.getTaskInfo(buildInfo.getTaskId(), true);

            build.setTaskInfo(taskInfo);

            if (taskInfo != null) {
                LOGGER.debug("Found task info task id {} for build id {} using method {}", taskInfo.getTaskId(), buildInfo.getId(), taskInfo.getMethod());

                List<Object> request = taskInfo.getRequest();

                if (request != null) {
                    LOGGER.debug("Got task request for build id {}: {}", buildInfo.getId(), request);

                    KojiTaskRequest taskRequest = new KojiTaskRequest(request);

                    build.setTaskRequest(taskRequest);
                } else {
                    LOGGER.debug("Null task request for build id {} with task id {} and checksum {}", red(buildInfo.getId()), red(taskInfo.getTaskId()), red(checksum));
                }
            } else {
                LOGGER.debug("Task info not found for build id {}", red(buildInfo.getId()));
            }
        } else {
            LOGGER.debug("Found import for build id {} with checksum {} and files {}", red(buildInfo.getId()), red(checksum), red(filenames));
        }

        addArchiveToBuild(build, archive, filenames);

        build.setRemoteArchives(allArchives);
        build.setTags(tags);
        build.setTypes(buildInfo.getTypeNames());

        if (cacheManager != null) {
            LOGGER.debug("Putting build id {} in global cache", buildId);

            buildCache.put(buildId, build);
        }

        return build;
    }

    private void addArchiveWithoutBuild(String checksum, Collection<String> files) {
        KojiBuild buildZero = builds.get(0);
        Optional<KojiLocalArchive> matchingArchive = buildZero.getArchives().stream().filter(a -> a.getArchive().getChecksum().equals(checksum)).findFirst();
        List<String> filenames;

        if (matchingArchive.isPresent()) {
            KojiLocalArchive existingArchive = matchingArchive.get();
            filenames = existingArchive.getFiles();

            LOGGER.debug("Adding not-found checksum {} to existing archive id {}", existingArchive.getArchive().getChecksum(), existingArchive.getArchive().getArchiveId());

            filenames.addAll(files);
        } else {
            filenames = new ArrayList<>(files);

            KojiArchiveInfo tmpArchive = new KojiArchiveInfo();

            tmpArchive.setBuildId(0);
            tmpArchive.setFilename("not found");
            tmpArchive.setChecksum(checksum);
            tmpArchive.setChecksumType(config.getChecksumType());

            tmpArchive.setArchiveId(-1 * (buildZero.getArchives().size() + 1));

            LOGGER.debug("Adding not-found checksum {} to new archive {}", checksum, tmpArchive.getArchiveId());

            buildZero.getArchives().add(new KojiLocalArchive(tmpArchive, filenames));
        }

        filenames.sort(Comparator.naturalOrder());
    }

    private void addArchiveToBuild(KojiBuild build, KojiArchiveInfo archive, Collection<String> files) {
        List<String> filenames = new ArrayList<>(files);

        LOGGER.debug("Adding all filenames with archive id {} with files {}", archive.getArchiveId(), filenames);

        Optional<KojiLocalArchive> matchingArchive = build.getArchives().stream().filter(a -> a.getArchive().getArchiveId().equals(archive.getArchiveId())).findFirst();

        if (matchingArchive.isPresent()) {
            KojiLocalArchive existingArchive = matchingArchive.get();

            if (existingArchive.getFiles() == null) {
                LOGGER.debug("Setting up new filenames with archive id {} with files {}", archive.getArchiveId(), filenames);
                existingArchive.setFiles(filenames);
            } else {
                LOGGER.debug("Adding to existing filenames for archive id {}", archive.getArchiveId());
                existingArchive.getFiles().addAll(filenames);
            }

            existingArchive.getFiles().sort(Comparator.naturalOrder());

            LOGGER.debug("Added to existing archive id {} to build id {} with {} archives", archive.getArchiveId(), archive.getBuildId(), build.getArchives().size());
        } else {
             build.getArchives().add(new KojiLocalArchive(archive, filenames));

             LOGGER.debug("Added new archive id {} to build id {} with {} archives", archive.getArchiveId(), archive.getBuildId(), build.getArchives().size());
        }

        build.getArchives().sort((KojiLocalArchive a1, KojiLocalArchive a2) -> a1.getArchive().getFilename().compareTo(a2.getArchive().getFilename()));
    }

    /**
     * Given a list of builds sorted by id, return the best build chosen in the following order:
     *
     * <ol>
     *   <li>Complete tagged non-imported builds</li>
     *   <li>Complete tagged imported builds</li>
     *   <li>Complete untagged builds</li>
     *   <li>Builds with the highest id</li>
     * </ol>
     *
     * @param foundBuilds the list of builds in order of increasing id
     * @param archives the archives which are contained in the list of found builds
     * @return the best build
     */
    private KojiBuild findBestBuild(List<KojiBuild> foundBuilds, List<KojiArchiveInfo> archives) {
         archives.forEach(archive -> {
            KojiBuild duplicateBuild = builds.get(archive.getBuildId());

            if (duplicateBuild != null) {
                LOGGER.debug("Marking archive id {} as duplicate for build {}", archive.getArchiveId(), duplicateBuild.getBuildInfo().getId());

               if (!duplicateBuild.getDuplicateArchives().contains(archive)) {
                   duplicateBuild.getDuplicateArchives().add(archive);
               }
            }
        });

        List<KojiBuild> cachedBuilds = foundBuilds.stream().filter(b -> builds.get(b.getBuildInfo().getId()) != null).collect(Collectors.toList());

        if (!cachedBuilds.isEmpty()) {
            KojiBuild b = cachedBuilds.get(cachedBuilds.size() - 1);

            LOGGER.debug("Found suitable cached build id {}", b.getBuildInfo().getId());

            return b;
        }

        LOGGER.debug("Found {} builds containing archive with checksum {}", foundBuilds.size(), archives.get(0).getChecksum());

        List<KojiBuild> completedBuilds = foundBuilds.stream().filter(build -> build.getBuildInfo().getBuildState() == KojiBuildState.COMPLETE).collect(Collectors.toList());
        List<KojiBuild> completedTaggedBuilds = completedBuilds.stream().filter(build -> !build.getTags().isEmpty()).collect(Collectors.toList());
        List<KojiBuild> completedTaggedBuiltBuilds = completedTaggedBuilds.stream().filter(build -> !build.isImport()).collect(Collectors.toList());

        if (!completedTaggedBuiltBuilds.isEmpty()) {
            KojiBuild b = completedTaggedBuiltBuilds.get(completedTaggedBuiltBuilds.size() - 1);

            LOGGER.debug("Found suitable completed non-import tagged build {} for checksum {}", b.getBuildInfo().getId(), archives.get(0).getChecksum());

            return b;
        }

        if (!completedTaggedBuilds.isEmpty()) {
            KojiBuild b = completedTaggedBuilds.get(completedTaggedBuilds.size() - 1);

            LOGGER.debug("Found suitable completed tagged build {} for checksum {}", b.getBuildInfo().getId(), archives.get(0).getChecksum());

            return b;
        }

        if (!completedBuilds.isEmpty()) {
            KojiBuild b = completedBuilds.get(completedBuilds.size() - 1);

            LOGGER.debug("Found suitable completed build {} for checksum {}", b.getBuildInfo().getId(), archives.get(0).getChecksum());

            return b;
        }

        KojiBuild b = foundBuilds.get(foundBuilds.size() - 1);

        LOGGER.warn("Could not find suitable build for checksum {} for build id {}. Keeping latest", red(archives.get(0).getChecksum()), red(b.getBuildInfo().getId()));

        return b;
    }

    public Map<Integer, KojiBuild> findBuilds(Map<String, Collection<String>> checksumTable) throws KojiClientException {
        if (checksumTable == null || checksumTable.isEmpty()) {
            LOGGER.warn("Checksum table is empty");
            return Collections.emptyMap();
        }

        if (archiveExtensions == null) {
            LOGGER.info("Asking Koji for valid archive extensions");
            archiveExtensions = getArchiveExtensions();
            LOGGER.info("Using archive extensions: {}", green(archiveExtensions));
        }

        for (Entry<String, Collection<String>> entry : checksumTable.entrySet()) {
              String checksum = entry.getKey();

            if (checksum.equals(EMPTY_DIGEST)) {
                LOGGER.debug("Found empty file for checksum {}", checksum);
                continue;
            }

            Collection<String> filenames = entry.getValue();

            boolean checkFile = filenames.stream().anyMatch(filename -> archiveExtensions.stream().anyMatch(filename::endsWith));

            if (!checkFile) {
                LOGGER.debug("Skipping build lookup for {} due to filename extension", checksum);
                continue;
            }

            LOGGER.debug("Looking up archives for checksum: {}", checksum);

            if (cacheManager != null) {
                Integer cachedBuildId = checksumCache.get(checksum);

                if (cachedBuildId != null) {
                    KojiBuild cachedBuild = buildCache.get(cachedBuildId);

                    if (cachedBuild != null) {
                        List<KojiArchiveInfo> archives = cachedBuild.getRemoteArchives().stream().filter(a -> a.getBuildId().intValue() == cachedBuild.getBuildInfo().getId() && a.getChecksum().equals(checksum)).collect(Collectors.toList());
                        String archiveFilenames = archives.stream().map(KojiArchiveInfo::getFilename).collect(Collectors.joining(", "));

                        archives.forEach(a -> {
                            LOGGER.info("Found build: id: {} nvr: {} checksum: {} archive: {} (cached)", green(cachedBuild.getBuildInfo().getId()), green(cachedBuild.getBuildInfo().getNvr()), green(checksum), green(archiveFilenames));

                            addArchiveToBuild(cachedBuild, a, filenames);
                        });

                        builds.put(cachedBuild.getBuildInfo().getId(), cachedBuild);

                        continue;
                    }
                }
            }

            Collection<Integer> ids = checksumMap.get(checksum);

            if (!ids.isEmpty()) {
                LOGGER.debug("Found cached checksum for checksum {} with ids {}", checksum, ids);

                for (int id : ids) {
                    KojiBuild build = builds.get(id);

                    if (build == null) {
                        LOGGER.debug("Skipping build id {} since it does not exist for checksum {}", id, checksum);
                        continue;
                    }

                    LOGGER.debug("Build id {} exists for checksum {}", id, checksum);

                    List<KojiLocalArchive> matchingArchives = build.getArchives().stream().filter(a -> a != null && a.getArchive().getChecksum().equals(checksum)).collect(Collectors.toList());

                    LOGGER.debug("Build id {} for checksum {} has {} matching archives", id, checksum, matchingArchives.size());

                    for (KojiLocalArchive archive : matchingArchives) {
                        addArchiveToBuild(build, archive.getArchive(), filenames);
                    }
                }

                continue;
            }

            List<KojiArchiveInfo> archives = session.listArchives(new KojiArchiveQuery().withChecksum(checksum));

            if (archives.isEmpty()) {
                LOGGER.debug("Got empty archive list for checksum: {}", checksum);
                addArchiveWithoutBuild(checksum, filenames);
                continue;
            }

            LOGGER.debug("Found {} archives for checksum: {}", archives.size(), checksum);

            List<KojiBuild> foundBuilds = new ArrayList<>();

            for (KojiArchiveInfo archive : archives) {
                if (archive.getChecksumType() != config.getChecksumType()) {
                    LOGGER.warn("Skipping archive id {} as checksum is not {}, but is {}", red(config.getChecksumType()), red(archive.getArchiveId()), red(archive.getChecksumType()));
                    continue;
                }

                KojiBuild build = lookupBuild(archive.getBuildId(), checksum, archive, filenames);

                if (build == null) {
                    continue;
                }

                checksumMap.put(checksum, archive.getBuildId());

                foundBuilds.add(build);
            }

            LOGGER.debug("Found {} builds for checksum {}", foundBuilds.size(), checksum);

            if (foundBuilds.isEmpty()) {
                LOGGER.warn("Did not find any builds for checksum {}", checksum);
                continue;
            }

            KojiBuild bestBuild;

            if (foundBuilds.size() == 1) {
                bestBuild = foundBuilds.get(0);
            } else {
                bestBuild = findBestBuild(foundBuilds, archives);
            }

            if (bestBuild.isImport()) {
                LOGGER.warn("Found import for build id {} with checksum {} and files {}", red(bestBuild.getBuildInfo().getId()), red(checksum), red(filenames));
            }

            String archiveFilenames = archives.stream().filter(a -> a.getBuildId().intValue() == bestBuild.getBuildInfo().getId()).map(KojiArchiveInfo::getFilename).collect(Collectors.joining(", "));

            LOGGER.info("Found build: id: {} nvr: {} checksum: {} archive: {}", green(bestBuild.getBuildInfo().getId()), green(bestBuild.getBuildInfo().getNvr()), green(checksum), green(archiveFilenames));

            builds.put(bestBuild.getBuildInfo().getId(), bestBuild);

            if (cacheManager != null) {
                checksumCache.put(checksum, bestBuild.getBuildInfo().getId());
            }

            LOGGER.debug("Number of builds found: {}", builds.size());
        }

        List<KojiArchiveInfo> archiveInfos = builds.values().stream().filter(b -> b.getBuildInfo().getId() > 0).flatMap(b -> b.getArchives().stream()).map(KojiLocalArchive::getArchive).collect(Collectors.toList());

        session.enrichArchiveTypeInfo(archiveInfos);

        return Collections.unmodifiableMap(builds);
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
        final String jarName = "koji-build-finder";

        String scmRevision = "unknown";

        try {
            Enumeration<URL> resources = BuildFinder.class.getClassLoader().getResources("META-INF/MANIFEST.MF");

            while (resources.hasMoreElements()) {
                URL jarUrl = resources.nextElement();

                if (jarUrl.getFile().contains(jarName)) {
                    Manifest manifest = new Manifest(jarUrl.openStream());
                    String manifestValue = manifest.getMainAttributes().getValue("Scm-Revision");

                    if (manifestValue != null && !manifestValue.isEmpty()) {
                        scmRevision = manifestValue;
                    }

                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Error getting SCM revision: {}", red(e.getMessage()));
        }

        return scmRevision;
    }

    public static String getChecksumFilename(KojiChecksumType checksumType) {
        return CHECKSUMS_FILENAME_BASENAME + checksumType + ".json";
    }

    public static String getBuildsFilename() {
        return BUILDS_FILENAME;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void outputToFile() throws JsonGenerationException, JsonMappingException, IOException {
        JSONUtils.dumpObjectToFile(builds, new File(outputDirectory, getBuildsFilename()));
    }

    @Override
    public Map<Integer, KojiBuild> call() throws KojiClientException {
        Instant startTime = Instant.now();
        MultiValuedMap<String, String> checksumMap = new ArrayListValuedHashMap<>();
        List<Checksum> checksums = new ArrayList<>();
        Checksum checksum = null;
        boolean finished = false;

        while (!finished) {
            try {
                checksum = analyzer.getQueue().take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (checksum.getValue() == null) {
                finished = true;
                break;
            }

            checksums.add(checksum);

            int numElements = analyzer.getQueue().drainTo(checksums);

            LOGGER.debug("Got {} checksums from queue", numElements + 1);

            for (Checksum c : checksums) {
                String checksumValue = c.getValue();
                String filename = c.getFilename();

                if (checksumValue != null) {
                    checksumMap.put(checksumValue, filename);
                } else {
                    finished = true;
                }
            }

            findBuilds(checksumMap.asMap());

            checksumMap.clear();
            checksums.clear();
        }

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime).abs();
        int numChecksums = analyzer.getChecksums().size();
        int numBuilds = builds.size() - 1;

        LOGGER.info("Total number of files: {}, checked: {}, skipped: {}, time: {}, average: {}", green(numChecksums), green(numChecksums - numBuilds), green(numBuilds), green(duration), green(numBuilds > 0 ? duration.dividedBy(numBuilds) : 0));
        LOGGER.info("Found {} builds", green(numBuilds));

        return Collections.unmodifiableMap(builds);
    }
}
