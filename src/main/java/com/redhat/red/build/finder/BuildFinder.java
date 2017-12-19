/**
 * Copyright 2017 Red Hat, Inc.
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
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.red.build.finder.report.GAVReport;
import com.redhat.red.build.finder.report.HTMLReport;
import com.redhat.red.build.finder.report.NVRReport;
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

public class BuildFinder {
    private static final String NAME = "koji-build-finder";

    private static final String CONFIG_FILENAME = "config.json";

    private static final String CHECKSUMS_FILENAME_BASENAME = "checksums-";

    private static final String BUILDS_FILENAME = "builds.json";

    private static final String HTML_FILENAME = "output.html";

    private static final String GAV_FILENAME = "gav.txt";

    private static final String NVR_FILENAME = "nvr.txt";

    private static final int TERM_WIDTH = 80;

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildFinder.class);

    private BuildConfig config;

    private static String krbCCache;

    private static String krbKeytab;

    private static String krbService;

    private static String krbPrincipal;

    private static String krbPassword;

    private ClientSession session;

    private static String outputDir = "";

    public BuildFinder(ClientSession session, BuildConfig config) {
        this.session = session;
        this.config = config;
    }

    public Map<Integer, KojiBuild> findBuilds(Map<String, Collection<String>> checksumTable) {
        LOGGER.info("Ready to find checksums of type {}", config.getChecksumType());

        if (checksumTable == null || checksumTable.size() == 0) {
            LOGGER.warn("Checksum table is empty");
            return Collections.emptyMap();
        }

        final long startTime = System.nanoTime();

        Map<String, KojiArchiveType> types;
        Set<String> extensionsToCheck = new TreeSet<>();

        try {
            types = session.getArchiveTypeMap();
            LOGGER.info("Koji archive types: {}", types.keySet());

            for (String typeToCheck : config.getArchiveTypes()) {
                if (types.containsKey(typeToCheck)) {
                    KojiArchiveType archiveType = types.get(typeToCheck);
                    LOGGER.info("Adding archive type to check: {}", archiveType);
                    extensionsToCheck.addAll(archiveType.getExtensions());
                }
            }
        } catch (KojiClientException e) {
            LOGGER.error("Koji client error", e);
            session.close();
            return Collections.emptyMap();
        }

        LOGGER.info("Checking only files with the following extensions: {}", extensionsToCheck);

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

        LOGGER.info("Checking up to {} different checksums", total);

        for (Entry<String, Collection<String>> entry : checksumTable.entrySet()) {
            String checksum = entry.getKey();
            LOGGER.info("Progress: {} / {} = {}%", checked, total, (checked / (double) total) * 100);

            checked++;

            if (checksum.equals(EMPTY_MD5)) {
                LOGGER.debug("Found empty file for checksum", checksum);
                continue;
            }

            Collection<String> filenames = checksumTable.get(checksum);
            boolean foundExt = false;

            for (String filename : filenames) {
                LOGGER.debug("Checking checksum {} and filename {}", checksum, filename);
                boolean exclude = config.getExcludes().stream().anyMatch(x -> filename.matches(x));

                if (exclude) {
                    LOGGER.info("Skipping filename {} because it matches the excludes list", filename);
                    continue;
                }

                for (String ext : extensionsToCheck) {
                    if (filename.endsWith("." + ext)) {
                        foundExt = true;
                        LOGGER.info("Found extension for {}: {}", checksum, ext);
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
                LOGGER.info("Looking up archives for checksum {}", checksum);
                archives = session.listArchives(new KojiArchiveQuery().withChecksum(checksum));
            } catch (KojiClientException e) {
                LOGGER.error("Koji client error", e);
                continue;
            }

            if (archives == null || archives.isEmpty()) {
                LOGGER.info("Empty archive list for checksum {}. Creating placeholder.", checksum);

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

            LOGGER.info("Found {} archives for checskum {}", archives.size(), checksum);

            int firstBuild = -1;

            for (KojiArchiveInfo archive : archives) {
                if (!archive.getChecksumType().equals(config.getChecksumType())) {
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
                        LOGGER.info("Build id {} already in table with {} archives", archive.getBuildId(), build.getArchives() != null ? build.getArchives().size() : 0);
                        buildInfo = build.getBuildInfo();
                        taskInfo = build.getTaskInfo();
                        taskRequest = build.getTaskRequest();
                        archiveList = build.getArchives();
                        tags = build.getTags();
                        hits++;

                        if (!buildInfo.getBuildState().equals(KojiBuildState.COMPLETE)) {
                            LOGGER.warn("Skipping incomplete build id {}", buildInfo.getId());
                            archivesToRemove.add(archive);
                            continue;
                        }

                        if (tags.size() == 0) {
                            LOGGER.warn("Skipping build id {} due to no tags", buildInfo.getId());
                            archivesToRemove.add(archive);
                            continue;
                        }

                        /* Ignore imports when the artifact was also found in an earlier build */
                        if (taskInfo == null && firstBuild != -1 && buildInfo.getId() > firstBuild) {
                            LOGGER.warn("Skipping import id {} because artifact exists in build id {}", buildInfo.getId(), firstBuild);
                            continue;
                        }

                        if (archiveList == null) {
                            LOGGER.warn("Null archive list for archive id {} to build id {}", archive.getArchiveId(), buildInfo.getId());
                            archiveList = new ArrayList<>();
                        }

                        KojiLocalArchive kla = new KojiLocalArchive(archive, null);

                        if (!archiveList.contains(kla)) {
                            LOGGER.info("Adding archive id {} to build id {} already in table with {} archives", archive.getArchiveId(), archive.getBuildId(), build.getArchives().size());
                            archiveList.add(new KojiLocalArchive(archive, new ArrayList<>(filenames)));

                        } else {
                            int archiveIndex = archiveList.indexOf(kla);
                            KojiLocalArchive aArchive = archiveList.get(archiveIndex);

                            if (aArchive.getFiles() == null) {
                                aArchive.setFiles(new ArrayList<>(filenames));
                            }
                        }
                    } else {
                        LOGGER.info("Build id {} not in table, looking up", archive.getBuildId());
                        buildInfo = session.getBuild(archive.getBuildId());

                        if (buildInfo != null) {
                            if (!buildInfo.getBuildState().equals(KojiBuildState.COMPLETE)) {
                                LOGGER.warn("Found incomplete build id {}, nvr {} archive file {} with checksum {}, skipping", buildInfo.getId(), buildInfo.getNvr(), archive.getFilename(), checksum);

                                archivesToRemove.add(archive);

                                build = new KojiBuild();
                                build.setBuildInfo(buildInfo);
                                builds.put(archive.getBuildId(), build);

                                continue;
                            }

                            tags = session.listTags(buildInfo.getId());

                            if (tags.size() == 0) {
                                LOGGER.warn("Skipping build id {} due to no tags", buildInfo.getId());
                                archivesToRemove.add(archive);
                                continue;
                            }

                            LOGGER.info("Found build: id {}, nvr {} for checksum {}, archive file {}", buildInfo.getId(), buildInfo.getNvr(), checksum, archive.getFilename());

                            allArchives = session.listArchives(new KojiArchiveQuery().withBuildId(buildInfo.getId()));

                            if (buildInfo.getTaskId() != null) {
                                boolean useTaskRequest = true;

                                if (!useTaskRequest) {
                                    taskInfo = session.getTaskInfo(buildInfo.getTaskId(), true);
                                } else {
                                    taskInfo = session.getTaskInfo(buildInfo.getTaskId(), false);
                                }

                                if (taskInfo != null) {
                                    LOGGER.info("Found task info task id {} for build id {} using method {}", taskInfo.getTaskId(), buildInfo.getId(), taskInfo.getMethod());

                                    /* Track first build that is not an import */
                                    if (firstBuild == -1 || buildInfo.getId() < firstBuild) {
                                        firstBuild = buildInfo.getId();
                                    }

                                    if (!useTaskRequest) {
                                        List<Object> request = taskInfo.getRequest();

                                        if (request != null) {
                                            LOGGER.info("Got task request for build id {}: {}", buildInfo.getId(), request);
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
                                LOGGER.warn("Got null task id (import) for build id {} with checksum {} and files {}", buildInfo.getId(), checksum, checksumTable.get(checksum));
                            }

                            /* Ignore imports when the artifact was also found in an earlier build */
                            if (buildInfo.getTaskId() == null && firstBuild != -1 && buildInfo.getId() > firstBuild) {
                                LOGGER.warn("Skipping import id {} because artifact exists in build id {}", buildInfo.getId(), firstBuild);
                            } else {
                                archiveList = new ArrayList<>();
                                archiveList.add(new KojiLocalArchive(archive, new ArrayList<>(filenames)));

                                build = new KojiBuild(buildInfo, taskInfo, taskRequest, archiveList, allArchives, tags);
                                builds.put(archive.getBuildId(), build);
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
                LOGGER.warn("More than one archive ({}) with checksum {}", archives.size(), checksum);

                for (KojiArchiveInfo archive : archives) {
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
                }
            }
        }

        session.close();

        final long endTime = System.nanoTime();
        final long duration = endTime - startTime;
        final double seconds = (duration / 1000000000.00);
        int lookups = builds.size() - 1;

        LOGGER.info("Total number of files: {}, checked: {}, skipped: {}, hits: {}, time: {}m, avg: {}s", checksumTable.keySet().size(), lookups, checksumTable.size() - lookups, hits, seconds / 60.0, (seconds / lookups));

        LOGGER.info("Found {} total builds in this distribution", builds.keySet().size() - 1);

        builds.values().removeIf(b -> !b.getBuildInfo().getBuildState().equals(KojiBuildState.COMPLETE));

        LOGGER.info("Found {} completed builds in this distribution", builds.keySet().size() - 1);

        return builds;
    }

    public static void main(String[] args) {
        LOGGER.info("koji-builder-finder " + getManifestInformation());

        List<File> files = new ArrayList<>();
        Options options = new Options();
        options.addOption(Option.builder("h").longOpt("help").desc("Show this help message.").build());
        options.addOption(Option.builder("d").longOpt("debug").desc("Enable debug logging.").build());
        options.addOption(Option.builder("k").longOpt("checksum-only").numberOfArgs(0).required(false).desc("Only checksum files and do not find sources. Default: "
            + ConfigDefaults.CHECKSUM_ONLY + ".").build());
        options.addOption(Option.builder("t").longOpt("checksum-type").numberOfArgs(1).required(false).type(String.class).desc("Checksum types ("
            + Arrays.stream(KojiChecksumType.values()).map(KojiChecksumType::getAlgorithm).collect(Collectors.joining(",")) + "). Default: "
            + ConfigDefaults.CHECKSUM_TYPE + ".").build());
        options.addOption(Option.builder("a").longOpt("archive-type").numberOfArgs(1).required(false).desc("Add a koji archive type to check. Default: ["
            + ConfigDefaults.ARCHIVE_TYPES.stream().collect(Collectors.joining(",")) + "].").type(List.class).build());
        options
            .addOption(Option.builder("x").longOpt("exclude").numberOfArgs(1).argName("pattern").required(false).desc("Add a pattern to exclude files from source check. Default: ["
                + ConfigDefaults.EXCLUDES.stream().collect(Collectors.joining(",")) + "].").build());
        options.addOption(Option.builder().longOpt("koji-hub-url").numberOfArgs(1).argName("url").required(false).desc("Set the Koji hub URL.").build());
        options.addOption(Option.builder().longOpt("koji-web-url").numberOfArgs(1).argName("url").required(false).desc("Set the Koji web URL.").build());
        options.addOption(Option.builder().longOpt("krb-ccache").numberOfArgs(1).argName("ccache").required(false).desc("Set the location of Kerberos credential cache.").build());
        options.addOption(Option.builder().longOpt("krb-keytab").numberOfArgs(1).argName("keytab").required(false).desc("Set the location of Kerberos keytab.").build());
        options.addOption(Option.builder().longOpt("krb-service").numberOfArgs(1).argName("service").required(false).desc("Set Kerberos client service.").build());
        options.addOption(Option.builder().longOpt("krb-principal").numberOfArgs(1).argName("principal").required(false).desc("Set Kerberos client principal.").build());
        options.addOption(Option.builder().longOpt("krb-password").numberOfArgs(1).argName("password").required(false).desc("Set Kerberos password.").build());
        options.addOption(
            Option.builder("o").longOpt("output-directory").numberOfArgs(1).argName("outputDirectory").required(false).desc("Configure a base outputDir directory.").build());

        Path path = Paths.get(CONFIG_FILENAME);
        String[] unparsedArgs;

        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine line = parser.parse(options, args);

            unparsedArgs = line.getArgs();

            if (line.hasOption("help")) {
                usage(options);
            } else if (unparsedArgs.length == 0) {
                throw new ParseException("Must specify at least one file");
            }

            if (line.hasOption("debug")) {
                final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
                root.setLevel(Level.DEBUG);
            }

            // Initial value taken from configuration value and then allow command line to override.
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

            File f = path.toFile();
            BuildConfig config;

            if (f.exists()) {
                config = mapper.readValue(path.toFile(), BuildConfig.class);
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

            if (line.hasOption("koji-web-url")) {
                config.setKojiWebURL(line.getOptionValue("koji-web-url"));
            }

            if (line.hasOption("krb-ccache")) {
                krbCCache = line.getOptionValue("krb-ccache");
                LOGGER.info("Kerberos ccache: {}", krbCCache);
            }

            if (line.hasOption("krb-keytab")) {
                krbKeytab = line.getOptionValue("krb-keytab");
                LOGGER.info("Kerberos keytab {}", krbKeytab);
            }

            if (line.hasOption("krb-service")) {
                krbService = line.getOptionValue("krb-service");
                LOGGER.info("Kerberos service: {}", krbService);
            }

            if (line.hasOption("krb-principal")) {
                krbPrincipal = line.getOptionValue("krb-principal");
                LOGGER.info("Kerberos principal: {}", krbPrincipal);
            }

            if (line.hasOption("krb-password")) {
                krbPassword = line.getOptionValue("krb-password");
                LOGGER.info("Read Kerberos password");
            }

            if (line.hasOption("output-directory")) {
                outputDir = line.getOptionValue("output-directory") + File.separatorChar;
                LOGGER.info("Read output directory {} ", outputDir);
            }

            LOGGER.debug("Configuration {} ", config);
            // TODO: Decide if we should write out the config file or throw exception for missing file.
            // JSONUtils.dumpFile(path.toFile(), config);

            for (String unparsedArg : unparsedArgs) {
                File file = new File(unparsedArg);

                if (!file.canRead()) {
                    System.err.printf("WARNING: Cannot read file %s", file.getPath());
                    continue;
                }

                if (file.isDirectory()) {
                    LOGGER.info("Adding all files in directory {}", file.getPath());
                    files.addAll(FileUtils.listFiles(file, null, true));
                } else {
                    LOGGER.info("Adding file {}", file.getPath());
                    files.add(new File(unparsedArg));
                }
            }

            File checksumFile = new File(outputDir + CHECKSUMS_FILENAME_BASENAME + config.getChecksumType() + ".json");
            Map<String, Collection<String>> checksums = null;

            if (!checksumFile.exists()) {
                LOGGER.info("Scanning checksums for files: {}", files);
                DistributionAnalyzer pda = new DistributionAnalyzer(files, config.getChecksumType().getAlgorithm());
                pda.checksumFiles();
                checksums = pda.getMap().asMap();
                pda.outputToFile(checksumFile);
            } else {
                LOGGER.info("Loading checksums from file: {}", checksumFile);
                checksums = JSONUtils.loadChecksumsFile(checksumFile);
            }

            if (config.getChecksumOnly()) {
                return;
            }

            File buildsFile = new File(outputDir + BUILDS_FILENAME);
            Map<Integer, KojiBuild> builds = null;
            KojiClientSession session = null;

            try {
                session = new KojiClientSession(config.getKojiHubURL(), krbService, krbPrincipal, krbPassword, krbCCache, krbKeytab);
            } catch (KojiClientException e) {
                e.printStackTrace();
            }

            if (session == null) {
                LOGGER.info("Creating session failed");
                return;
            }

            if (buildsFile.exists()) {
                LOGGER.info("Attempting to load existing builds file {}", buildsFile.getAbsolutePath());
                builds = JSONUtils.loadBuildsFile(buildsFile);
            } else {
                BuildFinder bf = new BuildFinder(session, config);
                builds = bf.findBuilds(checksums);
                JSONUtils.dumpFile(buildsFile, builds);
            }

            if (builds != null && builds.size() > 0) {
                LOGGER.info("Generating reports");
                List<KojiBuild> buildList = new ArrayList<>(builds.values());

                Collections.sort(buildList, (b1, b2) -> Integer.compare(b1.getBuildInfo().getId(), b2.getBuildInfo().getId()));
                buildList = Collections.unmodifiableList(buildList);

                Report htmlReport = new HTMLReport(files, buildList, config.getKojiWebURL());
                htmlReport.outputToFile(new File(outputDir + HTML_FILENAME));

                Report nvrReport = new NVRReport(buildList);
                nvrReport.outputToFile(new File(outputDir + NVR_FILENAME));

                Report gavReport = new GAVReport(buildList);
                gavReport.outputToFile(new File(outputDir + GAV_FILENAME));
            } else {
                LOGGER.warn("Could not generate reports since list of builds was empty");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
            usage(options);
        }
    }


    private static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setSyntaxPrefix("Usage: ");
        formatter.setWidth(TERM_WIDTH);
        formatter.printHelp(NAME + " <files>", options);
        System.exit(1);
    }

    /**
     * Retrieves the SHA this was built with.
     *
     * @return the GIT sha of this codebase.
     */
    private static String getManifestInformation() {
        String result = "";

        try {
            final Enumeration<URL> resources;
            resources = BuildFinder.class.getClassLoader()
                .getResources("META-INF/MANIFEST.MF");

            while (resources.hasMoreElements()) {
                final URL jarUrl = resources.nextElement();

                if (jarUrl.getFile()
                    .contains("koji-build-finder")) {
                    final Manifest manifest = new Manifest(jarUrl.openStream());

                    result = manifest.getMainAttributes()
                        .getValue("Implementation-Version");
                    result += " ( SHA: " + manifest.getMainAttributes()
                        .getValue("Scm-Revision") + " ) ";
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Unexpected exception processing jar file.", e);
        }
        return result;
    }
}
