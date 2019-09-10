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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.finder.pnc.client.PncClient14;
import com.redhat.red.build.finder.pnc.client.PncClientException;
import com.redhat.red.build.finder.pnc.client.PncUtils;
import com.redhat.red.build.finder.pnc.client.model.Artifact;
import com.redhat.red.build.finder.pnc.client.model.Artifact.Quality;
import com.redhat.red.build.finder.pnc.client.model.BuildConfiguration;
import com.redhat.red.build.finder.pnc.client.model.BuildRecord;
import com.redhat.red.build.finder.pnc.client.model.BuildRecordPushResult;
import com.redhat.red.build.finder.pnc.client.model.ProductVersion;
import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveQuery;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveType;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildState;
import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;
import com.redhat.red.build.koji.model.xmlrpc.KojiIdOrName;
import com.redhat.red.build.koji.model.xmlrpc.KojiNVRA;
import com.redhat.red.build.koji.model.xmlrpc.KojiRpmInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTagInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskRequest;

public class BuildFinder implements Callable<Map<BuildSystemInteger, KojiBuild>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildFinder.class);

    private static final String BUILDS_FILENAME = "builds.json";

    private static final String CHECKSUMS_FILENAME_BASENAME = "checksums-";

    private String emptyDigest;

    private ClientSession session;

    private BuildConfig config;

    private Map<BuildSystemInteger, KojiBuild> builds;

    private List<KojiBuild> buildsList;

    private List<KojiBuild> buildsFoundList;

    private Map<Integer, KojiBuild> allKojiBuilds;

    private Map<Integer, PncBuild> allPncBuilds;

    private File outputDirectory;

    private MultiValuedMap<String, Integer> checksumMap;

    private List<String> archiveExtensions;

    private DistributionAnalyzer analyzer;

    private Cache<String, List<KojiArchiveInfo>> checksumCache;

    private Cache<String, List<Artifact>> pncChecksumCache;

    private Cache<Integer, KojiBuild> buildCache;

    private Cache<String, KojiBuild> rpmCache;

    private Cache<Integer, PncBuild> pncBuildCache;

    private EmbeddedCacheManager cacheManager;

    private PncClient14 pncclient;

    private Map<String, Collection<String>> foundChecksums;

    private Map<String, Collection<String>> notFoundChecksums;

    public BuildFinder(ClientSession session, BuildConfig config) {
        this(session, config, null, null, null);
    }

    public BuildFinder(ClientSession session, BuildConfig config, DistributionAnalyzer analyzer) {
        this(session, config, analyzer, null, null);
    }

    public BuildFinder(ClientSession session, BuildConfig config, DistributionAnalyzer analyzer, EmbeddedCacheManager cacheManager) {
        this(session, config, analyzer, cacheManager, null);
    }

    public BuildFinder(ClientSession session, BuildConfig config, DistributionAnalyzer analyzer, EmbeddedCacheManager cacheManager, PncClient14 pncclient) {
        this.session = session;
        this.config = config;
        this.outputDirectory = new File("");
        this.checksumMap = new ArrayListValuedHashMap<>();
        this.analyzer = analyzer;
        this.cacheManager = cacheManager;
        this.pncclient = pncclient;
        this.allKojiBuilds = new HashMap<>();

        if (pncclient != null) {
            this.allPncBuilds = new HashMap<>();
        }

        // TODO: Allow checksum types other than md5
        if (cacheManager != null) {
            this.buildCache = cacheManager.getCache("builds");
            this.checksumCache = cacheManager.getCache(CHECKSUMS_FILENAME_BASENAME + KojiChecksumType.md5);
            this.rpmCache = cacheManager.getCache("rpms-" + KojiChecksumType.md5);

            if (pncclient != null) {
                this.pncChecksumCache = cacheManager.getCache("checksums-pnc-" + KojiChecksumType.md5);
                this.pncBuildCache = cacheManager.getCache("builds-pnc");
            }
        }

        emptyDigest = Hex.encodeHexString(DigestUtils.getDigest(KojiChecksumType.md5.getAlgorithm()).digest());

        this.foundChecksums = new HashMap<>();
        this.notFoundChecksums = new HashMap<>();

        initBuilds();
    }

    public BuildFinder(Map<BuildSystemInteger, KojiBuild> builds) {
        this.builds = builds;
        buildsList = new ArrayList<>(builds.values());
        buildsList.sort(Comparator.comparingInt(b -> b.getBuildInfo().getId()));
        buildsFoundList = buildsList.size() > 1 ? buildsList.subList(1, buildsList.size()) : Collections.emptyList();
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

        builds.put(new BuildSystemInteger(0), build);
    }

    private List<String> getArchiveExtensions() throws KojiClientException {
        Map<String, KojiArchiveType> allArchiveTypesMap = session.getArchiveTypeMap();

        List<String> allArchiveTypes = allArchiveTypesMap.values().stream().map(KojiArchiveType::getName).collect(Collectors.toList());
        List<String> archiveTypes = config.getArchiveTypes();
        List<String> archiveTypesToCheck;

        LOGGER.debug("Archive types: {}", green(archiveTypes));

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

        List<String> allArchiveExtensions = allArchiveTypesMap.values().stream().map(KojiArchiveType::getExtensions).flatMap(List::stream).collect(Collectors.toList());
        List<String> localArchiveExtensions = config.getArchiveExtensions();
        List<String> archiveExtensionsToCheck;

        if (localArchiveExtensions != null && !localArchiveExtensions.isEmpty()) {
            LOGGER.debug("There are {} supplied Koji archive extensions: {}", localArchiveExtensions.size(), localArchiveExtensions);
            archiveExtensionsToCheck = localArchiveExtensions.stream().filter(allArchiveExtensions::contains).collect(Collectors.toList());
            LOGGER.debug("There are {} valid supplied Koji archive extensions: {}", localArchiveExtensions.size(), localArchiveExtensions);
        } else {
            LOGGER.debug("There are {} known Koji archive extensions: {}", allArchiveExtensions.size(), allArchiveExtensions.size());
            LOGGER.warn("Supplied archive extensions list is empty; defaulting to all known archive extensions");
            archiveExtensionsToCheck = allArchiveExtensions;
        }

        return archiveExtensionsToCheck;
    }

    private KojiBuild lookupBuild(int buildId, String checksum, KojiArchiveInfo archive, Collection<String> filenames) throws KojiClientException {
        KojiBuild cachedBuild = builds.get(new BuildSystemInteger(buildId, BuildSystem.koji));

        if (cachedBuild != null) {
            LOGGER.debug("Build id: {} checksum: {} archive: {} filenames: {} is in cache", buildId, checksum, archive.getArchiveId(), filenames);

            addArchiveToBuild(cachedBuild, archive, filenames);

            return cachedBuild;
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

        return build;
    }

    private void addArchiveWithoutBuild(String checksum, Collection<String> filenames) {
        KojiBuild buildZero = builds.get(new BuildSystemInteger(0, BuildSystem.none));
        Optional<KojiLocalArchive> matchingArchive = buildZero.getArchives().stream().filter(a -> a.getArchive().getChecksum().equals(checksum)).findFirst();

        if (matchingArchive.isPresent()) {
            KojiLocalArchive existingArchive = matchingArchive.get();

            LOGGER.debug("Adding not-found checksum {} to existing archive id {} with filenames {}", existingArchive.getArchive().getChecksum(), existingArchive.getArchive().getArchiveId(), filenames);

            existingArchive.getFilenames().addAll(filenames);
        } else {
            KojiArchiveInfo tmpArchive = new KojiArchiveInfo();

            tmpArchive.setBuildId(0);
            tmpArchive.setFilename("not found");
            tmpArchive.setChecksum(checksum);
            tmpArchive.setChecksumType(KojiChecksumType.md5);

            tmpArchive.setArchiveId(-1 * (buildZero.getArchives().size() + 1));

            LOGGER.debug("Adding not-found checksum {} to new archive id {} with filenames {}", checksum, tmpArchive.getArchiveId(), filenames);

            buildZero.getArchives().add(new KojiLocalArchive(tmpArchive, filenames, analyzer != null ? analyzer.getFiles().get(filenames.iterator().next()) : Collections.emptySet()));
        }
    }

    private void addArchiveToBuild(KojiBuild build, KojiArchiveInfo archive, Collection<String> filenames) {
        LOGGER.debug("Found build id {} for file {} (checksum {}) matching local files {}", build.getBuildInfo().getId(), archive.getFilename(), archive.getChecksum(), filenames);

        Optional<KojiLocalArchive> matchingArchive = build.getArchives().stream().filter(a -> a.getArchive().getArchiveId().equals(archive.getArchiveId())).findFirst();

        if (matchingArchive.isPresent()) {
            LOGGER.debug("Adding existing archive id {} to build id {} with {} archives and filenames {}", archive.getArchiveId(), archive.getBuildId(), build.getArchives().size(), filenames);

            KojiLocalArchive existingArchive = matchingArchive.get();

            existingArchive.getFilenames().addAll(filenames);
        } else {
            LOGGER.debug("Adding new archive id {} to build id {} with {} archives and filenames {}", archive.getArchiveId(), archive.getBuildId(), build.getArchives().size(), filenames);

            KojiLocalArchive localArchive = new KojiLocalArchive(archive, filenames, analyzer != null ? analyzer.getFiles().get(filenames.iterator().next()) : Collections.emptySet());

            build.getArchives().add(localArchive);

            build.getArchives().sort(Comparator.comparing(a -> a.getArchive().getFilename()));
        }
    }

    private void addRpmToBuild(KojiBuild build, KojiRpmInfo rpm, Collection<String> filenames) {
        LOGGER.debug("Found build id {} for file {} (payloadhash {}) matching local files {}", build.getBuildInfo().getId(), rpm.getNvr(), rpm.getPayloadhash(), filenames);

        Optional<KojiLocalArchive> matchingArchive = build.getArchives().stream().filter(a -> a.getRpm().getId().equals(rpm.getId())).findFirst();

        if (matchingArchive.isPresent()) {
            LOGGER.debug("Adding existing rpm id {} to build id {} with {} rpms and filenames {}", rpm.getId(), rpm.getBuildId(), build.getRpms().size(), filenames);

            KojiLocalArchive existingArchive = matchingArchive.get();

            existingArchive.getFilenames().addAll(filenames);
        } else {
            LOGGER.debug("Adding new rpm id {} to build id {} with {} rpms and filenames {}", rpm.getId(), rpm.getBuildId(), build.getRpms().size(), filenames);

            build.getArchives().add(new KojiLocalArchive(rpm, filenames, analyzer != null ? analyzer.getFiles().get(filenames.iterator().next()) : Collections.emptySet()));

            build.getArchives().sort(Comparator.comparing(a -> a.getArchive().getFilename()));
        }
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
     * @param candidates the list of builds in order of increasing id
     * @param archives the archives which are contained in the list of found builds
     * @return the best build
     */
    private KojiBuild findBestBuildFromCandidates(List<KojiBuild> candidates, List<KojiArchiveInfo> archives) {
        int candidatesSize = candidates.size();

        if (candidatesSize == 1) {
            return candidates.get(0);
        }

        String checksum = archives.get(0).getChecksum();
        List<Integer> candidateIds = candidates.stream().map(KojiBuild::getBuildInfo).map(KojiBuildInfo::getId).collect(Collectors.toList());

        LOGGER.debug("Found {} builds containing archive with checksum {}: {}", candidatesSize, checksum, candidateIds);

        archives.forEach(archive -> {
            KojiBuild duplicateBuild = builds.get(new BuildSystemInteger(archive.getBuildId(), BuildSystem.koji));

            if (duplicateBuild != null) {
                LOGGER.debug("Marking archive id {} as duplicate for build id {}", archive.getArchiveId(), duplicateBuild.getBuildInfo().getId());

               if (!duplicateBuild.getDuplicateArchives().contains(archive)) {
                   duplicateBuild.getDuplicateArchives().add(archive);
               }
            }
        });

        List<KojiBuild> cachedBuilds = candidateIds.stream().map(id -> builds.get(new BuildSystemInteger(id, BuildSystem.koji))).filter(Objects::nonNull).collect(Collectors.toList());

        if (!cachedBuilds.isEmpty()) {
            KojiBuild b = cachedBuilds.get(cachedBuilds.size() - 1);

            LOGGER.debug("Found suitable cached build id {}", b.getBuildInfo().getId());

            return b;
        }

        List<KojiBuild> completedBuilds = candidates.stream().filter(build -> build.getBuildInfo().getBuildState() == KojiBuildState.COMPLETE).collect(Collectors.toList());
        List<KojiBuild> completedTaggedBuilds = completedBuilds.stream().filter(build -> build.getTags() != null && !build.getTags().isEmpty()).collect(Collectors.toList());
        List<KojiBuild> completedTaggedBuiltBuilds = completedTaggedBuilds.stream().filter(build -> !build.isImport()).collect(Collectors.toList());

        if (!completedTaggedBuiltBuilds.isEmpty()) {
            KojiBuild b = completedTaggedBuiltBuilds.get(completedTaggedBuiltBuilds.size() - 1);

            LOGGER.debug("Found suitable completed non-import tagged build {} for checksum {}", b.getBuildInfo().getId(), checksum);

            return b;
        }

        if (!completedTaggedBuilds.isEmpty()) {
            KojiBuild b = completedTaggedBuilds.get(completedTaggedBuilds.size() - 1);

            LOGGER.debug("Found suitable completed tagged build {} for checksum {}", b.getBuildInfo().getId(), checksum);

            return b;
        }

        if (!completedBuilds.isEmpty()) {
            KojiBuild b = completedBuilds.get(completedBuilds.size() - 1);

            LOGGER.debug("Found suitable completed build {} for checksum {}", b.getBuildInfo().getId(), checksum);

            return b;
        }

        KojiBuild b = candidates.get(candidatesSize - 1);

        LOGGER.warn("Could not find suitable build for checksum {} for build id {}. Keeping latest", red(checksum), red(b.getBuildInfo().getId()));

        return b;
    }

    /**
     * Find builds with the given checksums, slow version. Does not use cache
     * and may give slightly different results than #findBuilds(Map) due to how
     * the best build is computed when there is more than one match.
     *
     * @param checksumTable the checksum table
     * @return the map of builds
     * @throws KojiClientException if an error occurs
     */
    public Map<BuildSystemInteger, KojiBuild> findBuildsSlow(Map<String, Collection<String>> checksumTable) throws KojiClientException {
        if (checksumTable == null || checksumTable.isEmpty()) {
            LOGGER.warn("Checksum table is empty");
            return Collections.emptyMap();
        }

        if (archiveExtensions == null) {
            LOGGER.info("Getting archive extensions from: {}", green("remote server"));
            archiveExtensions = getArchiveExtensions();
            LOGGER.info("Using archive extensions: {}", green(archiveExtensions));
        }

        for (Entry<String, Collection<String>> entry : checksumTable.entrySet()) {
              String checksum = entry.getKey();

            if (checksum.equals(emptyDigest)) {
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

            Collection<Integer> ids = checksumMap.get(checksum);

            if (!ids.isEmpty()) {
                LOGGER.debug("Found cached checksum for checksum {} with ids {}", checksum, ids);

                for (int id : ids) {
                    KojiBuild build = builds.get(new BuildSystemInteger(id, BuildSystem.koji));

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

            List<KojiBuild> foundBuilds = new ArrayList<>(archives.size());

            for (KojiArchiveInfo archive : archives) {
                if (!archive.getChecksumType().equals(KojiChecksumType.md5)) {
                    LOGGER.warn("Skipping archive id {} as checksum is not {}, but is {}", red(archive.getArchiveId()), red(KojiChecksumType.md5), red(archive.getChecksumType()));
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

            KojiBuild bestBuild = findBestBuildFromCandidates(foundBuilds, archives);

            String archiveFilenames = archives.stream().filter(a -> a.getBuildId() == bestBuild.getBuildInfo().getId()).map(KojiArchiveInfo::getFilename).collect(Collectors.joining(", "));

            LOGGER.info("Found build in Koji: id: {} nvr: {} checksum: {} archive: {}", green(bestBuild.getBuildInfo().getId()), green(bestBuild.getBuildInfo().getNvr()), green(checksum), green(archiveFilenames));

            builds.put(new BuildSystemInteger(bestBuild.getBuildInfo().getId(), BuildSystem.koji), bestBuild);

            LOGGER.debug("Number of builds found: {}", builds.size());
        }

        List<KojiArchiveInfo> archiveInfos = builds.values().stream().filter(b -> b.getBuildInfo().getId() > 0).map(KojiBuild::getArchives).flatMap(List::stream).map(KojiLocalArchive::getArchive).collect(Collectors.toList());

        session.enrichArchiveTypeInfo(archiveInfos);

        return Collections.unmodifiableMap(builds);
    }

    private String handleFileNotFound(String filename) {
        LOGGER.debug("Handle file not found: {}", filename);

        int index = filename.lastIndexOf("!/");

        if (index == -1) {
            index = filename.length();
        }

        String parentFilename = filename.substring(0, index);

        LOGGER.debug("Parent of file not found: {}", parentFilename);

        for (KojiBuild build : builds.values()) {
            List<KojiLocalArchive> as = build.getArchives();
            final String needle = parentFilename;
            Optional<KojiLocalArchive> a = as.stream().filter(ar -> ar.getFilenames().contains(needle)).findFirst();

            if (a.isPresent()) {
                KojiLocalArchive matchedArchive = a.get();
                KojiArchiveInfo archive = matchedArchive.getArchive();

                matchedArchive.getUnmatchedFilenames().add(filename);

                LOGGER.debug("Archive {} ({}) is not build from source since it contains unfound file {} (built from source: {})", archive.getArchiveId(), archive.getFilename(), filename, matchedArchive.isBuiltFromSource());

                return parentFilename;
            }
        }

        if (index == filename.length()) {
            return null;
        }

        return handleFileNotFound(parentFilename);
    }

    private boolean shouldSkipChecksum(String checksum, Collection<String> filenames) {
        if (checksum.equals(emptyDigest)) {
            LOGGER.warn("Skipped empty digest for files: {}", red(filenames));
            return true;
        }

        List<String> newArchiveExtensions = new ArrayList<>(archiveExtensions.size() + 1);
        newArchiveExtensions.addAll(archiveExtensions);
        newArchiveExtensions.add("rpm");

        if (filenames.stream().noneMatch(filename -> newArchiveExtensions.stream().anyMatch(filename::endsWith))) {
            LOGGER.warn("Skipped due to invalid archive extension for files: {}", red(filenames));
            return false;
        }

        return false;
    }

    /**
     * Find builds with the given checksums in Pnc.
     *
     * @param checksumTable the checksum table
     * @return the map of builds
     * @throws PncClientException if an error occurs
     * @throws KojiClientException if an error occurs
     */
    public Map<BuildSystemInteger, KojiBuild> findBuildsPnc(Map<String, Collection<String>> checksumTable) throws PncClientException, KojiClientException {
        if (checksumTable == null || checksumTable.isEmpty()) {
            LOGGER.warn("Checksum table is empty");
            return Collections.emptyMap();
        }

        if (archiveExtensions == null) {
            LOGGER.debug("Asking server for archive extensions");
            archiveExtensions = getArchiveExtensions();
        } else {
            LOGGER.debug("Getting archive extensions from configuration file");
        }

        LOGGER.debug("Archive extensions: {}", green(archiveExtensions));

        Set<Entry<String, Collection<String>>> entries = checksumTable.entrySet();
        int size = entries.size();
        List<String> checksums = new ArrayList<>(size);

        for (Entry<String, Collection<String>> entry : entries) {
            String checksum = entry.getKey();
            Collection<String> filenames = entry.getValue();

            if (shouldSkipChecksum(checksum, filenames)) {
                LOGGER.debug("Skipped checksum {} for filenames {}", checksum, filenames);
                continue;
            }

            LOGGER.debug("PNC: checksum={}", checksum);

            List<Artifact> artifacts = null;

            if (pncChecksumCache != null) {
                artifacts = pncChecksumCache.get(checksum);
            }

            if (artifacts == null) {
                checksums.add(entry.getKey());
            }
        }

        List<List<Artifact>> artifactsList = pncclient.getArtifactsByMd5(checksums);
        Iterator<List<Artifact>> it = artifactsList.iterator();
        Set<Integer> ids = new TreeSet<>();

        for (Entry<String, Collection<String>> entry : entries) {
            String checksum = entry.getKey();
            Collection<String> filenames = entry.getValue();
            List<Artifact> artifacts = null;

            if (pncChecksumCache != null) {
                artifacts = pncChecksumCache.get(checksum);
            }

            if (artifacts == null) {
                artifacts = it.next();

                if (pncChecksumCache != null) {
                    pncChecksumCache.put(checksum, artifacts);
                }
            }

            if (artifacts.isEmpty()) {
                notFoundChecksums.put(checksum, filenames);
                continue;
            }

            Artifact artifact = getBestPncArtifact(artifacts);
            PncBuild pncbuild = null;

            if (pncBuildCache != null) {
                pncbuild = pncBuildCache.get(artifact.getId());
            }

            if (pncbuild == null) {
                Integer id = !artifact.getBuildRecordIds().isEmpty() ? artifact.getBuildRecordIds().get(0) : null;

                if (id != null && !allPncBuilds.containsKey(id)) {
                    ids.add(id);
                }
            }
        }

        List<Integer> idsList = new ArrayList<>(ids);
        List<BuildRecord> records = pncclient.getBuildRecordsById(idsList);
        List<Integer> buildConfigurationIds = records.stream().map(BuildRecord::getBuildConfigurationId).sorted().distinct().collect(Collectors.toList());
        List<List<Artifact>> remoteArtifacts = pncclient.getBuiltArtifactsById(idsList);
        List<BuildConfiguration> buildConfigurations = pncclient.getBuildConfigurationsById(buildConfigurationIds);
        List<Integer> productVersionIds = buildConfigurations.stream().map(BuildConfiguration::getProductVersionId).filter(Objects::nonNull).sorted().distinct().collect(Collectors.toList());
        List<ProductVersion> productVersions = pncclient.getProductVersionsById(productVersionIds);
        List<PncBuild> pncbuilds = records.stream().map(PncBuild::new).collect(Collectors.toList());
        List<BuildRecordPushResult> results = pncclient.getBuildRecordPushResultsById(idsList);
        Map<Integer, BuildRecordPushResult> rMap = results.stream().filter(Objects::nonNull).collect(Collectors.toMap(BuildRecordPushResult::getBuildRecordId, Function.identity()));
        Map<Integer, BuildConfiguration> bcMap = buildConfigurations.stream().collect(Collectors.toMap(BuildConfiguration::getId, Function.identity()));
        Iterator<List<Artifact>> ita = remoteArtifacts.iterator();
        Map<Integer, ProductVersion> pvMap = productVersions.stream().collect(Collectors.toMap(ProductVersion::getId, Function.identity()));

        pncbuilds.stream().forEachOrdered(pncbuild -> {
            BuildRecord record = pncbuild.getBuildRecord();
            BuildRecordPushResult buildRecordPushResult = rMap.get(record.getId());
            BuildConfiguration buildConfiguration = bcMap.get(record.getBuildConfigurationId());

            pncbuild.setBuildRecordPushResult(buildRecordPushResult);
            pncbuild.setBuildConfiguration(buildConfiguration);

            if (buildConfiguration.getProductVersionId() != null) {
                ProductVersion pv = pvMap.get(buildConfiguration.getProductVersionId());

                pncbuild.setProductVersion(pv);
            }

            pncbuild.setArtifacts(ita.next());
        });

        Map<Integer, PncBuild> allPncBuildsTemp = pncbuilds.stream().collect(Collectors.toMap(p -> p.getBuildRecord().getId(), Function.identity()));

        allPncBuilds.putAll(allPncBuildsTemp);

        it = artifactsList.iterator();

        for (Entry<String, Collection<String>> entry : entries) {
            String checksum = entry.getKey();
            Collection<String> filenames = entry.getValue();
            List<Artifact> artifacts;

            if (pncChecksumCache != null) {
                artifacts = pncChecksumCache.get(checksum);

                if (artifacts != null) {
                    LOGGER.debug("Found {} in Pnc checksum cache", checksum);
                }
            } else {
                artifacts = it.next();
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("PNC: number of artifacts={}", artifacts == null ? 0 : artifacts.size());
            }

            Artifact artifact = null;

            if (artifacts != null && !artifacts.isEmpty()) {
                artifact = getBestPncArtifact(artifacts);
            }

            if (artifact != null) {
                List<Integer> buildIds = artifact.getBuildRecordIds();
                Integer buildId;

                if (!buildIds.isEmpty()) {
                    buildId = buildIds.get(0);
                } else {
                    notFoundChecksums.put(checksum, filenames);
                    continue;
                }

                LOGGER.debug("PNC: artifact={}, buildId={}", artifact, buildId);
                PncBuild pncbuild = null;

                if (pncBuildCache != null) {
                    pncbuild = pncBuildCache.get(buildId);

                    if (pncbuild != null) {
                        LOGGER.debug("Found {} in Pnc build cache", buildId);
                    }
                }

                if (pncbuild == null) {
                    pncbuild = allPncBuilds.get(buildId);

                    if (pncBuildCache != null && pncbuild != null) {
                        pncBuildCache.put(pncbuild.getBuildRecord().getId(), pncbuild);
                    }
                }

                if (pncbuild != null) {
                    pncbuild.getArtifacts().add(artifact);
                    BuildRecord record = pncbuild.getBuildRecord();
                    Integer id = record.getId();
                    KojiBuild kojibuild = builds.get(new BuildSystemInteger(id, BuildSystem.pnc));

                    if (kojibuild == null) {
                        kojibuild = PncUtils.pncBuildToKojiBuild(pncbuild);
                        builds.put(new BuildSystemInteger(id, BuildSystem.pnc), kojibuild);
                    }

                    KojiArchiveInfo kojiarchive = PncUtils.artifactToKojiArchiveInfo(pncbuild, artifact);

                    PncUtils.fixNullVersion(kojibuild, kojiarchive);

                    addArchiveToBuild(kojibuild, kojiarchive, filenames);

                    foundChecksums.put(checksum, filenames);
                    notFoundChecksums.remove(checksum);

                    if (pncBuildCache != null) {
                        pncBuildCache.put(id, pncbuild);
                    }

                    KojiBuild buildZero = builds.get(new BuildSystemInteger(0, BuildSystem.none));

                    buildZero.getArchives().removeIf(a -> a.getChecksums().stream().anyMatch(c -> c.getValue().equals(checksum)));

                    LOGGER.info("Found build in Pnc: id: {} nvr: {} checksum: {} archive: {}", green(pncbuild.getBuildRecord().getId()), green(PncUtils.getNVRFromBuildRecord(pncbuild.getBuildRecord())), green(checksum), green(artifact.getFilename()));
                } else {
                    notFoundChecksums.put(checksum, filenames);
                }
            } else {
                notFoundChecksums.put(checksum, filenames);
            }
        }

        return Collections.unmodifiableMap(builds);
    }

    private int getArtifactQuality(Object obj) {
        Artifact a = (Artifact) obj;
        Quality quality = a.getArtifactQuality();

        switch (quality) {
        case NEW:
            return 1;
        case VERIFIED:
            return 2;
        case TESTED:
            return 3;
        case DEPRECATED:
            return -1;
        case BLACKLISTED:
            return -3;
        case DELETED:
            return -4;
        case TEMPORARY:
            return -2;
        default:
            return 0;
        }
    }

    private Artifact getBestPncArtifact(List<Artifact> artifacts) {
        int size = artifacts.size();
        Artifact artifact = artifacts.get(0);

        if (size == 1) {
            return artifact;
        }

        return artifacts.stream().sorted(Comparator.comparing(this::getArtifactQuality).reversed()).filter(a -> !a.getBuildRecordIds().isEmpty()).findFirst().orElse(artifact);
    }

    /**
     * Find builds with the given checksums.
     *
     * @param checksumTable the checksum table
     * @return the map of builds
     * @throws KojiClientException if an error occurs
    */
    public Map<BuildSystemInteger, KojiBuild> findBuilds(Map<String, Collection<String>> checksumTable) throws KojiClientException {
        if (checksumTable == null || checksumTable.isEmpty()) {
            LOGGER.warn("Checksum table is empty");
            return Collections.emptyMap();
        }

        if (archiveExtensions == null) {
            LOGGER.debug("Asking server for archive extensions");
            archiveExtensions = getArchiveExtensions();
        } else {
            LOGGER.debug("Getting archive extensions from configuration file");
        }

        LOGGER.debug("Archive extensions: {}", green(archiveExtensions));

        Set<Entry<String, Collection<String>>> entries = checksumTable.entrySet();
        int numEntries = entries.size();
        List<Entry<String, Collection<String>>> checksums = new ArrayList<>(numEntries);
        List<Entry<String, Collection<String>>> cachedChecksums = new ArrayList<>(numEntries);
        List<List<KojiArchiveInfo>> cachedArchiveInfos = new ArrayList<>(numEntries);
        List<Entry<String, Collection<String>>> rpmEntries = new ArrayList<>(numEntries);

        for (Entry<String, Collection<String>> entry : entries) {
            String checksum = entry.getKey();
            Collection<String> filenames = entry.getValue();

            if (shouldSkipChecksum(checksum, filenames)) {
                LOGGER.debug("Skipped checksum {} for filenames {}", checksum, filenames);
                // FIXME: We must check for a cached copy and remove it if present
                continue;
            }

            KojiBuild cacheRpmBuildInfo;

            List<KojiArchiveInfo> cacheArchiveInfos;

            if (filenames.stream().anyMatch(filename -> filename.endsWith(".rpm"))) {
                if (cacheManager == null || (cacheRpmBuildInfo = rpmCache.get(checksum)) == null) {
                    LOGGER.debug("Add RPM entry {} to list", entry);
                    rpmEntries.add(entry);
                } else {
                    LOGGER.debug("Checksum {} cached with build id {}", green(checksum), green(cacheRpmBuildInfo));
                    rpmCache.put(checksum, cacheRpmBuildInfo);
                    buildCache.put(cacheRpmBuildInfo.getBuildInfo().getId(), cacheRpmBuildInfo);
                }
            } else {
                if (cacheManager == null || (cacheArchiveInfos = checksumCache.get(checksum)) == null) {
                    LOGGER.debug("Add checksum {} to list", checksum);
                    checksums.add(entry);
                } else {
                    LOGGER.debug("Checksum {} cached with build ids {}", green(checksum), green(cacheArchiveInfos.stream().map(KojiArchiveInfo::getBuildId).collect(Collectors.toList())));
                    cachedChecksums.add(entry);
                    cachedArchiveInfos.add(cacheArchiveInfos);
                }
            }
        }

        final int numThreads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        int numChecksums = checksums.size();
        List<List<KojiArchiveInfo>> archives = new ArrayList<>(numChecksums);
        final int chunkSize = 8;
        List<List<Entry<String, Collection<String>>>> chunks = ListUtils.partition(checksums, chunkSize);
        int numChunks = chunks.size();
        List<KojiArchiveQuery> allQueries = new ArrayList<>(numChecksums);

        if (numChecksums > 0) {
            LOGGER.debug("Looking up {} checksums", green(numChecksums));
            LOGGER.debug("Using {} chunks of size {}", green(numChunks), green(chunkSize));

            List<Callable<List<List<KojiArchiveInfo>>>> tasks = new ArrayList<>(numChecksums);

            for (int i = 0; i < numChunks; i++) {
                int chunkNumber = i + 1;
                List<Entry<String, Collection<String>>> chunk = chunks.get(i);
                List<KojiArchiveQuery> queries = new ArrayList<>(numChunks);

                chunk.forEach(entry -> {
                    String checksum = entry.getKey();
                    KojiArchiveQuery query = new KojiArchiveQuery().withChecksum(checksum);
                    LOGGER.debug("Adding query for checksum {}", checksum);
                    queries.add(query);
                });

                if (!queries.isEmpty()) {
                    int querySize = queries.size();

                    LOGGER.debug("Added {} queries", green(querySize));

                    allQueries.addAll(queries);

                    tasks.add(() -> {
                        LOGGER.debug("Looking up checksums for chunk {} / {}", green(chunkNumber), green(numChunks));
                        return session.listArchives(queries);
                    });
                }
            }

            try {
                List<Future<List<List<KojiArchiveInfo>>>> futures = pool.invokeAll(tasks);

                for (Future<List<List<KojiArchiveInfo>>> future : futures) {
                    try {
                        List<List<KojiArchiveInfo>> archiveFutures = future.get();
                        archives.addAll(archiveFutures);
                    } catch (ExecutionException e) {
                        throw new KojiClientException("Error getting archive futures", e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        List<KojiArchiveInfo> archivesToEnrich = archives.stream().flatMap(List::stream).collect(Collectors.toList());

        session.enrichArchiveTypeInfo(archivesToEnrich);

        Iterator<KojiArchiveQuery> itqueries = allQueries.iterator();

        for (List<KojiArchiveInfo> archiveList : archives) {
            String queryChecksum = itqueries.next().getChecksum();

            if (archiveList.isEmpty()) {
                if (cacheManager != null) {
                    checksumCache.put(queryChecksum, Collections.emptyList());
                }
            } else {
                String archiveChecksum = archiveList.get(0).getChecksum();

                if (!queryChecksum.equals(archiveChecksum)) {
                    LOGGER.warn("Checksums {} and {} don't match, but this should never happen", queryChecksum, archiveChecksum);
                }

                if (cacheManager != null) {
                    checksumCache.put(queryChecksum, archiveList);
                }
            }
        }

        Stream<Integer> archiveBuildIds = archives.stream().flatMap(List::stream).map(KojiArchiveInfo::getBuildId);
        Stream<Integer> cachedBuildIds = cachedArchiveInfos.stream().flatMap(List::stream).map(KojiArchiveInfo::getBuildId);
        List<Integer> buildIds = Stream.concat(archiveBuildIds, cachedBuildIds).sorted().distinct().collect(Collectors.toList());
        int buildIdsSize = buildIds.size();

        if (cacheManager != null) {
            Iterator<Integer> it = buildIds.iterator();

            while (it.hasNext()) {
                Integer id = it.next();
                KojiBuild build = buildCache.get(id);

                if (build != null) {
                    LOGGER.debug("Build with id {} and nvr {} has been previously cached", green(id), green(build.getBuildInfo().getNvr()));
                    allKojiBuilds.put(id, build);
                    it.remove();
                }
            }
        }

        List<KojiIdOrName> rpmBuildIdsOrNames = new ArrayList<>(rpmEntries.size());

        if (!rpmEntries.isEmpty()) {
            for (Entry<String, Collection<String>> rpmEntry : rpmEntries) {
                Collection<String> filenames = rpmEntry.getValue();
                Optional<String> rpmFilename = filenames.stream().filter(filename -> filename.endsWith(".rpm")).findFirst();

                if (rpmFilename.isPresent()) {
                    String name = rpmFilename.get();
                    KojiNVRA nvra = KojiNVRA.parseNVRA(name);
                    KojiIdOrName idOrName = KojiIdOrName.getFor(nvra.getName() + "-" + nvra.getVersion() + "-" + nvra.getRelease() + "." + nvra.getArch());

                    rpmBuildIdsOrNames.add(idOrName);

                    LOGGER.debug("Added RPM: {}", rpmBuildIdsOrNames.get(rpmBuildIdsOrNames.size() - 1));
                }
            }

            List<KojiRpmInfo> rpmInfos = session.getRPM(rpmBuildIdsOrNames);
            List<KojiIdOrName> rpmBuildIds = rpmInfos.stream().map(KojiRpmInfo::getBuildId).map(KojiIdOrName::getFor).collect(Collectors.toList());
            List<KojiBuildInfo> rpmBuildInfos = session.getBuild(rpmBuildIds);
            List<List<KojiTagInfo>> rpmTagInfos = session.listTags(rpmBuildIds);
            List<List<KojiRpmInfo>> rpmRpmInfos = session.listBuildRPMs(rpmBuildIds);
            List<KojiTaskInfo> rpmTaskInfos = Collections.emptyList();
            List<Integer> taskIds = rpmBuildInfos.stream().map(KojiBuildInfo::getTaskId).filter(Objects::nonNull).collect(Collectors.toList());
            int taskIdsSize = taskIds.size();

            if (taskIdsSize > 0) {
                Boolean[] a = new Boolean[taskIdsSize];
                Arrays.fill(a, Boolean.TRUE);
                List<Boolean> requests = Arrays.asList(a);
                rpmTaskInfos = session.getTaskInfo(taskIds, requests);
            }

            Iterator<Entry<String, Collection<String>>> it = rpmEntries.iterator();
            Iterator<KojiRpmInfo> itrpm = rpmInfos.iterator();
            Iterator<KojiBuildInfo> itbuilds = rpmBuildInfos.iterator();
            Iterator<List<KojiTagInfo>> ittags = rpmTagInfos.iterator();
            Iterator<List<KojiRpmInfo>> itrpms = rpmRpmInfos.iterator();
            Iterator<KojiTaskInfo> ittasks = rpmTaskInfos.iterator();

            KojiBuild build = new KojiBuild();

            while (it.hasNext()) {
                Entry<String, Collection<String>> entry = it.next();
                String checksum = entry.getKey();
                Collection<String> filenames = entry.getValue();
                KojiRpmInfo rpm = itrpm.next();
                String actual = rpm.getPayloadhash();

                if (!checksum.equals(actual)) {
                    throw new KojiClientException("Mismatched payloadhash: " + checksum + " != " + actual);
                }

                build.setBuildInfo(itbuilds.next());
                build.setTags(ittags.next());
                build.setTaskInfo(ittasks.next());
                build.setRemoteRpms(itrpms.next());

                addRpmToBuild(build, rpm, filenames);

                LOGGER.info("Found build in Koji: id: {} nvr: {} checksum: {} archive: {}", green(build.getBuildInfo().getId()), green(build.getBuildInfo().getNvr()), green(checksum), green(rpm.getName() + "-" + rpm.getVersion() + "-" + rpm.getRelease() + "." + rpm.getArch() + ".rpm"));

                foundChecksums.put(checksum, filenames);
                notFoundChecksums.remove(checksum);

                KojiBuild buildZero = builds.get(new BuildSystemInteger(0, BuildSystem.none));

                buildZero.getArchives().removeIf(a -> a.getChecksums().stream().anyMatch(c -> c.getValue().equals(checksum)));
            }

            Integer id = build.getBuildInfo().getId();

            allKojiBuilds.put(id, build);

            if (cacheManager != null) {
                KojiBuild cachedBuild = buildCache.put(id, build);

                if (cachedBuild != null && !cachedBuild.getBuildInfo().getTypeNames().contains("rpm")) {
                    LOGGER.warn("Build id {} was already cached, but this should never happen", red(id));
                }
            }

            builds.put(new BuildSystemInteger(id, BuildSystem.koji), build);
        }

        if (!buildIds.isEmpty()) {
            List<KojiIdOrName> idsOrNames = buildIds.stream().map(KojiIdOrName::getFor).collect(Collectors.toList());
            Future<List<KojiBuildInfo>> futureArchiveBuilds = pool.submit(() -> session.getBuild(idsOrNames));
            Future<List<List<KojiTagInfo>>> futureTagInfos = pool.submit(() -> session.listTags(idsOrNames));
            List<KojiArchiveQuery> queries = new ArrayList<>(buildIdsSize);

            buildIds.forEach(buildId -> {
                KojiArchiveQuery query = new KojiArchiveQuery().withBuildId(buildId);
                queries.add(query);
            });

            Future<List<List<KojiArchiveInfo>>> futureArchiveInfos = pool.submit(() -> session.listArchives(queries));
            List<KojiBuildInfo> archiveBuilds = null;
            List<List<KojiTagInfo>> tagInfos = null;
            List<List<KojiArchiveInfo>> archiveInfos = null;

            try {
                archiveBuilds = futureArchiveBuilds.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                Utils.shutdownAndAwaitTermination(pool);
                throw new KojiClientException("Error getting archive build futures", e);
            }

            List<Integer> taskIds = archiveBuilds.stream().map(KojiBuildInfo::getTaskId).filter(Objects::nonNull).collect(Collectors.toList());
            int taskIdsSize = taskIds.size();
            Future<List<KojiTaskInfo>> futureTaskInfos = null;

            if (taskIdsSize > 0) {
                Boolean[] a = new Boolean[taskIdsSize];
                Arrays.fill(a, Boolean.TRUE);
                List<Boolean> requests = Arrays.asList(a);
                futureTaskInfos = pool.submit(() -> session.getTaskInfo(taskIds, requests));
            }

            List<KojiTaskInfo> taskInfos = null;

            try {
                tagInfos = futureTagInfos.get();
                archiveInfos = futureArchiveInfos.get();

                if (futureTaskInfos != null) {
                    taskInfos = futureTaskInfos.get();
                } else {
                    taskInfos = Collections.emptyList();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                Utils.shutdownAndAwaitTermination(pool);
                throw new KojiClientException("Error getting tag, archive, or taskinfo futures", e);
            }

            Iterator<KojiBuildInfo> itbuilds = archiveBuilds.iterator();
            Iterator<List<KojiTagInfo>> ittags = tagInfos.iterator();
            Iterator<List<KojiArchiveInfo>> itArchiveInfos = archiveInfos.iterator();
            Iterator<KojiTaskInfo> ittasks = taskInfos.iterator();

            while (itbuilds.hasNext()) {
                KojiBuildInfo buildInfo = itbuilds.next();
                KojiBuild build = new KojiBuild(buildInfo);

                build.setTags(ittags.next());
                build.setRemoteArchives(itArchiveInfos.next());

                if (build.getBuildInfo().getTaskId() != null) {
                    build.setTaskInfo(ittasks.next());
                }

                Integer id = build.getBuildInfo().getId();

                allKojiBuilds.put(id, build);

                if (cacheManager != null) {
                    KojiBuild cachedBuild = buildCache.put(id, build);

                    if (cachedBuild != null) {
                        LOGGER.warn("Build id {} was already cached, but this should never happen", red(id));
                    }
                }
            }

            List<KojiArchiveInfo> archivesToUpdate = new ArrayList<>(3 * archiveBuilds.size());

            for (KojiBuild build : allKojiBuilds.values()) {
                Arrays.asList(new KojiArchiveInfo[] {build.getScmSourcesZip(), build.getProjectSourcesTgz(), build.getPatchesZip()}).forEach(source -> {
                    if (KojiLocalArchive.isMissingBuildTypeInfo(source)) {
                        archivesToUpdate.add(source);
                    }
                });
            }

            if (!archivesToUpdate.isEmpty()) {
                session.enrichArchiveTypeInfo(archivesToUpdate);
            }
        }

        checksums.addAll(cachedChecksums);
        archives.addAll(cachedArchiveInfos);

        LOGGER.debug("Add builds with {} checksums and {} archive lists", checksums.size(), archives.size());

        Iterator<Entry<String, Collection<String>>> itchecksums = checksums.iterator();
        Iterator<List<KojiArchiveInfo>> itarchives = archives.iterator();

        while (itchecksums.hasNext()) {
            Entry<String, Collection<String>> entry = itchecksums.next();
            String checksum = entry.getKey();
            Collection<String> filenames = entry.getValue();
            List<KojiArchiveInfo> localArchiveInfos = itarchives.next();
            int size = localArchiveInfos.size();

            if (size == 0) {
                LOGGER.debug("Got empty archive list for checksum: {}", green(checksum));
                notFoundChecksums.put(checksum, filenames);
                addArchiveWithoutBuild(checksum, filenames);
            } else {
                   if (size == 1) {
                    KojiArchiveInfo archive = localArchiveInfos.get(0);
                    Integer buildId = archive.getBuildId();

                    LOGGER.debug("Singular build id {} found for checksum {}", green(buildId), green(checksum));

                    KojiBuild build = builds.get(new BuildSystemInteger(buildId, BuildSystem.koji));

                    if (build == null) {
                        KojiBuild allBuild = allKojiBuilds.get(buildId);

                        if (allBuild != null) {
                            builds.put(new BuildSystemInteger(allBuild.getBuildInfo().getId(), BuildSystem.koji), allBuild);
                            build = builds.get(new BuildSystemInteger(buildId, BuildSystem.koji));

                            LOGGER.info("Found build in Koji: id: {} nvr: {} checksum: {} archive: {}", green(build.getBuildInfo().getId()), green(build.getBuildInfo().getNvr()), green(checksum), green(archive.getFilename()));

                            foundChecksums.put(checksum, filenames);
                            notFoundChecksums.remove(checksum);

                            KojiBuild buildZero = builds.get(new BuildSystemInteger(0, BuildSystem.none));

                            buildZero.getArchives().removeIf(a -> a.getChecksums().stream().anyMatch(c -> c.getValue().equals(checksum)));
                        }
                    }

                    if (build != null) {
                        addArchiveToBuild(build, archive, filenames);
                    } else {
                        LOGGER.warn("Null build when adding archive id {} and filenames {}", red(archive.getArchiveId()), red(filenames));
                    }
                } else {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Find best build for checksum {} and filenames {} out of {} archives: {}", green(checksum), green(filenames), green(size), localArchiveInfos.stream().map(KojiArchiveInfo::getBuildId).map(String::valueOf).collect(Collectors.joining(", ")));
                    }

                    KojiBuild bestBuild = findBestBuild(allKojiBuilds, localArchiveInfos);
                    Optional<KojiArchiveInfo> optionalArchive = localArchiveInfos.stream().filter(a -> a.getBuildId().equals(bestBuild.getBuildInfo().getId())).findFirst();
                    KojiArchiveInfo archive;

                    if (optionalArchive.isPresent()) {
                        archive = optionalArchive.get();
                    } else {
                        continue;
                    }

                    LOGGER.debug("Build id {} found for checksum {}", green(bestBuild.getBuildInfo().getId()), green(checksum));

                    int buildId = bestBuild.getBuildInfo().getId();
                    KojiBuild build = builds.get(new BuildSystemInteger(buildId, BuildSystem.koji));

                    if (build == null) {
                        build = allKojiBuilds.get(buildId);

                        builds.put(new BuildSystemInteger(build.getBuildInfo().getId(), BuildSystem.koji), build);

                        int id = build.getBuildInfo().getId();

                        String archiveFilenames = localArchiveInfos.stream().filter(a -> a.getBuildId() == id).map(KojiArchiveInfo::getFilename).collect(Collectors.joining(", "));

                        LOGGER.info("Found build in Koji: id: {} nvr: {} checksum: {} archive: {}", green(build.getBuildInfo().getId()), green(build.getBuildInfo().getNvr()), green(checksum), green(archiveFilenames));

                        foundChecksums.put(checksum, filenames);
                        notFoundChecksums.remove(checksum);

                        KojiBuild buildZero = builds.get(new BuildSystemInteger(0, BuildSystem.none));

                        buildZero.getArchives().removeIf(a -> a.getChecksums().stream().anyMatch(c -> c.getValue().equals(checksum)));
                    }

                    addArchiveToBuild(build, archive, filenames);
                }
            }
        }

        KojiBuild buildZero = builds.get(new BuildSystemInteger(0, BuildSystem.none));
        List<KojiLocalArchive> localArchives = buildZero.getArchives();

        if (analyzer != null) {
            analyzer.getFilesInError().forEach(fileInError -> {
                Optional<Checksum> checksum = Checksum.findByType(MultiMapUtils.getValuesAsSet(analyzer.getFiles(), fileInError), KojiChecksumType.md5);

                checksum.ifPresent(value -> addArchiveWithoutBuild(value.getValue(), Collections.singletonList(fileInError)));
            });
         }

        LOGGER.debug("Find parents for {} archives: {}", localArchives.size(), localArchives);

        Iterator<KojiLocalArchive> it = localArchives.iterator();

        while (it.hasNext()) {
            KojiLocalArchive localArchive = it.next();
            Collection<String> filenames = localArchive.getFilenames();

            LOGGER.debug("Handle archive id {} with filenames {}", localArchive.getArchive().getArchiveId(), filenames);

            Iterator<String> it2 = filenames.iterator();

            while (it2.hasNext()) {
                String filename = it2.next();
                String parentFilename = handleFileNotFound(filename);

                if (parentFilename != null && parentFilename.contains("!/")) {
                    LOGGER.debug("Removing {} since we found a parent elsewhere", filename);
                    it2.remove();
                } else {
                    LOGGER.debug("Keeping {} since the parent is the distribution itself", filename);
                }
            }

            if (filenames.isEmpty()) {
                LOGGER.debug("Remove archive since filenames is empty");
                it.remove();
            }
        }

        Utils.shutdownAndAwaitTermination(pool);

        buildsList = new ArrayList<>(builds.values());

        buildsList.sort(Comparator.comparingInt(b -> b.getBuildInfo().getId()));

        buildsFoundList = buildsList.size() > 1 ? buildsList.subList(1, buildsList.size()) : Collections.emptyList();

        return Collections.unmodifiableMap(builds);
    }

    public Map<BuildSystemInteger, KojiBuild> getBuildsMap() {
        if (builds == null) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(builds);
    }

    public List<KojiBuild> getBuildsFound() {
        if (buildsFoundList == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(buildsFoundList);
    }

    public List<KojiBuild> getBuilds() {
        if (buildsList == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(buildsList);
    }

    private KojiBuild findBestBuild(Map<Integer, KojiBuild> allBuilds, List<KojiArchiveInfo> archiveInfos) {
        LOGGER.debug("Find best build for checksum {} filename {} out of {} archives", green(archiveInfos.get(0).getChecksum()), green(archiveInfos.get(0).getFilename()), green(archiveInfos.size()));

        Set<Integer> buildIds = archiveInfos.stream().map(KojiArchiveInfo::getBuildId).collect(Collectors.toSet());
        List<KojiBuild> candidateBuilds = buildIds.stream().map(allBuilds::get).collect(Collectors.toList());
        KojiBuild build = findBestBuildFromCandidates(candidateBuilds, archiveInfos);

        LOGGER.debug("Found best build id {} from {} candidates", green(build.getBuildInfo().getId()), green(candidateBuilds.size()));

        return build;
    }

    public static String getVersion() {
        Package p = BuildFinder.class.getPackage();

        return p == null || p.getImplementationVersion() == null ? "unknown" : p.getImplementationVersion();
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

    public Map<String, Collection<String>> getFoundChecksums() {
        return Collections.unmodifiableMap(foundChecksums);
    }

    public Map<String, Collection<String>> getNotFoundChecksums() {
        return Collections.unmodifiableMap(notFoundChecksums);
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void outputToFile() throws IOException {
        JSONUtils.dumpObjectToFile(builds, new File(outputDirectory, getBuildsFilename()));
    }

    @Override
    public Map<BuildSystemInteger, KojiBuild> call() throws KojiClientException {
        Instant startTime = Instant.now();
        MultiValuedMap<String, String> localchecksumMap = new ArrayListValuedHashMap<>();
        Set<Checksum> checksums = new HashSet<>();
        Checksum checksum = null;
        boolean finished = false;

        while (!finished) {
            try {
                checksum = analyzer.getQueue().take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (checksum == null || checksum.getValue() == null) {
                finished = true;
                break;
            }

            checksums.add(checksum);

            int numElements = analyzer.getQueue().drainTo(checksums);

            LOGGER.debug("Got {} checksums from queue", numElements + 1);

            for (Checksum cksum : checksums) {
                String value = cksum.getValue();

                if (value == null) {
                    finished = true;
                } else {
                    if (cksum.getType().equals(KojiChecksumType.md5)) {
                        String filename = cksum.getFilename();
                        localchecksumMap.put(value, filename);
                    }
                }
            }

            if (config.getBuildSystems().contains(BuildSystem.pnc) && config.getPncURL() != null) {
                try {
                    findBuildsPnc(localchecksumMap.asMap());
                } catch (PncClientException e) {
                    throw new KojiClientException("Pnc error", e);
                }

                if (!notFoundChecksums.isEmpty()) {
                    findBuilds(notFoundChecksums);
                }
            } else {
                findBuilds(localchecksumMap.asMap());
            }

            notFoundChecksums.clear();
            localchecksumMap.clear();
            checksums.clear();
        }

        int numBuilds = builds.size() - 1;
        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime).abs();

        LOGGER.info("Found {} builds in {} (average: {})", green(numBuilds), green(duration), green(numBuilds > 0 ? duration.dividedBy(numBuilds) : 0));

        return builds;
    }
}
