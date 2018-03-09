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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.red.build.finder.report.BuildStatisticsReport;
import com.redhat.red.build.finder.report.GAVReport;
import com.redhat.red.build.finder.report.HTMLReport;
import com.redhat.red.build.finder.report.NVRReport;
import com.redhat.red.build.finder.report.ProductReport;
import com.redhat.red.build.finder.report.Report;
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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;

public class BuildFinder {
    private static final String NAME = "koji-build-finder";

    private static final String CHECKSUMS_FILENAME_BASENAME = "checksums-";

    private static final String BUILDS_FILENAME = "builds.json";

    private static final int TERM_WIDTH = 80;

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildFinder.class);

    private static String krbCCache;

    private static String krbKeytab;

    private static String krbService;

    private static String krbPrincipal;

    private static String krbPassword;

    private static File outputDirectory;

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
        long numBuilds = builds.keySet().stream().count() - 1L;

        LOGGER.info("Total number of files: {}, checked: {}, skipped: {}, hits: {}, time: {}, average: {}", green(checksumTable.keySet().size()), green(numBuilds), green(checksumTable.size() - numBuilds), green(hits), green(duration), green(duration.dividedBy(numBuilds)));

        LOGGER.debug("Found {} total builds", numBuilds);

        builds.values().removeIf(b -> b.getBuildInfo().getBuildState() != KojiBuildState.COMPLETE);

        numBuilds = builds.keySet().stream().count() - 1L;

        LOGGER.info("Found {} builds", green(numBuilds));

        return builds;
    }

    public static void main(String[] args) {
        Options options = new Options();

        options.addOption(Option.builder("h").longOpt("help").desc("Show this help message.").build());
        options.addOption(Option.builder("c").longOpt("config").numberOfArgs(1).argName("file").required(false).desc("Specify configuration file to use. Default: " + ConfigDefaults.CONFIG + ".").build());
        options.addOption(Option.builder("d").longOpt("debug").desc("Enable debug logging.").build());
        options.addOption(Option.builder("k").longOpt("checksum-only").numberOfArgs(0).required(false).desc("Only checksum files and do not find sources. Default: " + ConfigDefaults.CHECKSUM_ONLY + ".").build());
        options.addOption(Option.builder("t").longOpt("checksum-type").argName("type").numberOfArgs(1).required(false).type(String.class).desc("Checksum types (" + Arrays.stream(KojiChecksumType.values()).map(KojiChecksumType::getAlgorithm).collect(Collectors.joining(",")) + "). Default: " + ConfigDefaults.CHECKSUM_TYPE + ".").build());
        options.addOption(Option.builder("a").longOpt("archive-type").argName("type").numberOfArgs(1).required(false).desc("Add a koji archive type to check. Default: [" + ConfigDefaults.ARCHIVE_TYPES.stream().collect(Collectors.joining(",")) + "].").type(List.class).build());
        options.addOption(Option.builder("x").longOpt("exclude").numberOfArgs(1).argName("pattern").required(false).desc("Add a pattern to exclude files from source check. Default: [" + ConfigDefaults.EXCLUDES.stream().collect(Collectors.joining(",")) + "].").build());
        options.addOption(Option.builder().longOpt("koji-hub-url").numberOfArgs(1).argName("url").required(false).desc("Set the Koji hub URL.").build());
        options.addOption(Option.builder().longOpt("koji-web-url").numberOfArgs(1).argName("url").required(false).desc("Set the Koji web URL.").build());
        options.addOption(Option.builder().longOpt("krb-ccache").numberOfArgs(1).argName("ccache").required(false).desc("Set the location of Kerberos credential cache.").build());
        options.addOption(Option.builder().longOpt("krb-keytab").numberOfArgs(1).argName("keytab").required(false).desc("Set the location of Kerberos keytab.").build());
        options.addOption(Option.builder().longOpt("krb-service").numberOfArgs(1).argName("service").required(false).desc("Set Kerberos client service.").build());
        options.addOption(Option.builder().longOpt("krb-principal").numberOfArgs(1).argName("principal").required(false).desc("Set Kerberos client principal.").build());
        options.addOption(Option.builder().longOpt("krb-password").numberOfArgs(1).argName("password").required(false).desc("Set Kerberos password.").build());
        options.addOption(Option.builder("o").longOpt("output-directory").numberOfArgs(1).argName("directory").required(false).desc("Set output directory.").build());

        try {
            AnsiConsole.systemInstall();

            List<File> files = new ArrayList<>();

            String[] unparsedArgs;

            CommandLineParser parser = new DefaultParser();

            CommandLine line = parser.parse(options, args);

            unparsedArgs = line.getArgs();

            if (line.hasOption("help")) {
                usage(options);
            } else if (unparsedArgs.length == 0) {
                throw new ParseException("Must specify at least one file");
            }

            if (line.hasOption("debug")) {
                ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
                rootLogger.setLevel(Level.DEBUG);

                LoggerContext loggerContext = rootLogger.getLoggerContext();

                PatternLayoutEncoder encoder = new PatternLayoutEncoder();
                encoder.setContext(loggerContext);
                encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
                encoder.start();

                ConsoleAppender<ILoggingEvent> appender = (ConsoleAppender<ILoggingEvent>) rootLogger.getAppender("STDOUT");

                if (appender != null) {
                    appender.setContext(loggerContext);
                    appender.setEncoder(encoder);
                    appender.start();
                }
            }

            LOGGER.info("{} {} (SHA: {})", boldYellow(NAME), boldYellow(getVersion()), cyan(getScmRevision()));

            // Initial value taken from configuration value and then allow command line to override.
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

            Path configPath = null;

            if (line.hasOption("config")) {
                configPath = Paths.get(line.getOptionValue("config"));
            } else {
                configPath = Paths.get(ConfigDefaults.CONFIG);
            }

            File configFile = configPath.toFile();
            BuildConfig config;

            if (configFile.exists()) {
                config = mapper.readValue(configPath.toFile(), BuildConfig.class);
            } else {
                LOGGER.debug("Configuration does not exist. Implicitly creating with defaults.");
                config = new BuildConfig();
            }

            if (line.hasOption("checksum-only")) {
                config.setChecksumOnly(Boolean.TRUE);
            }

            if (line.hasOption("checksum-type")) {
                config.setChecksumType(KojiChecksumType.valueOf(line.getOptionValue("checksum-type")));
            }

            if (line.hasOption("archive-type")) {
                @SuppressWarnings("unchecked")
                List<String> a = (List<String>) line.getParsedOptionValue("archive-types");
                config.setArchiveTypes(a);
            }

            if (line.hasOption("exclude")) {
                @SuppressWarnings("unchecked")
                List<String> e = (List<String>) line.getParsedOptionValue("exclude");
                config.setExcludes(e);
            }

            if (line.hasOption("koji-hub-url")) {
                config.setKojiHubURL(line.getOptionValue("koji-hub-url"));
            }

            verifyURL("koji-hub-url", config.getKojiHubURL(), line, configFile);

            if (line.hasOption("koji-web-url")) {
                config.setKojiWebURL(line.getOptionValue("koji-web-url"));
            }

            verifyURL("koji-web-url", config.getKojiWebURL(), line, configFile);

            if (line.hasOption("krb-ccache")) {
                krbCCache = line.getOptionValue("krb-ccache");
                LOGGER.debug("Kerberos ccache: {}", krbCCache);
            }

            if (line.hasOption("krb-keytab")) {
                krbKeytab = line.getOptionValue("krb-keytab");
                LOGGER.debug("Kerberos keytab {}", krbKeytab);
            }

            if (line.hasOption("krb-service")) {
                krbService = line.getOptionValue("krb-service");
                LOGGER.debug("Kerberos service: {}", krbService);
            }

            if (line.hasOption("krb-principal")) {
                krbPrincipal = line.getOptionValue("krb-principal");
                LOGGER.debug("Kerberos principal: {}", krbPrincipal);
            }

            if (line.hasOption("krb-password")) {
                krbPassword = line.getOptionValue("krb-password");
                LOGGER.debug("Read Kerberos password");
            }

            if (line.hasOption("output-directory")) {
                outputDirectory = new File(line.getOptionValue("output-directory"));
                LOGGER.info("Output will be stored in directory: {}", green(outputDirectory));
            }

            LOGGER.debug("Configuration {} ", config);

            if (!configFile.exists()) {
                File configDir = configPath.toFile().getParentFile();

                if (configDir != null && !configDir.exists()) {
                    boolean created = configDir.mkdirs();

                    if (!created) {
                        LOGGER.warn("Failed to create directory: {}", configDir);
                    }
                }

                JSONUtils.dumpObjectToFile(config, configPath.toFile());
            }

            for (String unparsedArg : unparsedArgs) {
                File file = new File(unparsedArg);

                if (!file.canRead()) {
                    LOGGER.warn("Could not read file: {}", file.getPath());
                    continue;
                }

                if (file.isDirectory()) {
                    LOGGER.debug("Adding all files in directory: {}", file.getPath());
                    files.addAll(FileUtils.listFiles(file, null, true));
                } else {
                    LOGGER.debug("Adding file: {}", file.getPath());
                    files.add(new File(unparsedArg));
                }
            }

            File checksumFile = new File(outputDirectory, getChecksumFilename(config.getChecksumType()));
            Map<String, Collection<String>> checksums = null;

            LOGGER.info("Checksum type: {}", green(config.getChecksumType()));

            if (!checksumFile.exists()) {
                LOGGER.info("Calculating checksums for files: {}", green(files));
                DistributionAnalyzer pda = new DistributionAnalyzer(files, config.getChecksumType().getAlgorithm());
                pda.checksumFiles();
                checksums = pda.getMap().asMap();
                pda.outputToFile(checksumFile);
            } else {
                LOGGER.info("Loading checksums from file: {}", green(checksumFile));
                checksums = JSONUtils.loadChecksumsFile(checksumFile);
            }

            if (config.getChecksumOnly()) {
                return;
            }

            File buildsFile = new File(outputDirectory, BUILDS_FILENAME);
            Map<Integer, KojiBuild> builds = null;
            KojiClientSession session = null;

            try {
                session = new KojiClientSession(config.getKojiHubURL(), krbService, krbPrincipal, krbPassword, krbCCache, krbKeytab);
            } catch (KojiClientException e) {
                e.printStackTrace();
            }

            if (session == null) {
                LOGGER.warn("Creating session failed");
                return;
            }

            if (buildsFile.exists()) {
                LOGGER.info("Loading builds from file: {}", green(buildsFile.getPath()));
                builds = JSONUtils.loadBuildsFile(buildsFile);
            } else {
                BuildFinder bf = new BuildFinder(session, config);
                builds = bf.findBuilds(checksums);
                JSONUtils.dumpObjectToFile(builds, buildsFile);
            }

            if (builds != null && builds.size() > 0) {
                LOGGER.info("Generating reports");
                List<KojiBuild> buildList = new ArrayList<>(builds.values());

                Collections.sort(buildList, (b1, b2) -> Integer.compare(b1.getBuildInfo().getId(), b2.getBuildInfo().getId()));
                buildList = Collections.unmodifiableList(buildList);

                List<Report> reports = new ArrayList<>();
                reports.add(new BuildStatisticsReport(outputDirectory, buildList));
                reports.add(new ProductReport(outputDirectory, buildList));
                reports.add(new NVRReport(outputDirectory, buildList));
                reports.add(new GAVReport(outputDirectory, buildList));
                reports.forEach(Report::outputText);

                new HTMLReport(outputDirectory, files, buildList, config.getKojiWebURL(), Collections.unmodifiableList(reports)).outputHTML();

                LOGGER.info("{}", boldYellow("DONE"));
            } else {
                LOGGER.warn("Could not generate reports since list of builds was empty");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
            usage(options);
        } finally {
            AnsiConsole.systemUninstall();
        }
    }

    private static void verifyURL(String key, String value, CommandLine line, File configFile) throws ParseException {
        String location = null;

        if (line.hasOption(key)) {
            location = "on the command line";
        } else {
            location = "in the configuration file";

            if (configFile != null) {
                location += " (" + configFile.getAbsolutePath() + ")";
            }
        }

        if (value == null || value.isEmpty()) {
            throw new ParseException("You must specify a non-empty value for " + key + " " + location + ".");
        }

        try {
            new URL(value);
        } catch (MalformedURLException e) {
            throw new ParseException("The value specified for " + key + " (" + value + ") " + location + " is malformed.");
        }
    }

    private static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();

        formatter.setSyntaxPrefix("Usage: ");
        formatter.setWidth(TERM_WIDTH);
        formatter.printHelp(NAME + " <files>", options);

        System.exit(1);
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

    public static String getChecksumFilename() {
        return CHECKSUMS_FILENAME_BASENAME + ConfigDefaults.CHECKSUM_TYPE + ".json";
    }

    public static String getChecksumFilename(KojiChecksumType checksumType) {
        return CHECKSUMS_FILENAME_BASENAME + checksumType + ".json";
    }

    private static Object cyan(Object o) {
        return new Object() {
            @Override
            public String toString() {
                return Ansi.ansi().fgCyan().a(o).reset().toString();
            }
        };
    }

    private static Object green(Object o) {
        return new Object() {
            @Override
            public String toString() {
                return Ansi.ansi().fgGreen().a(o).reset().toString();
            }
        };
    }

    private static Object red(Object o) {
        return new Object() {
            @Override
            public String toString() {
                return Ansi.ansi().fgRed().a(o).reset().toString();
            }
        };

    }

    private static Object boldYellow(Object o) {
        return new Object() {
            @Override
            public String toString() {
                return Ansi.ansi().fgYellow().bold().a(o).reset().toString();
            }
        };

    }
}
