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

import static com.redhat.red.build.finder.AnsiUtils.boldRed;
import static com.redhat.red.build.finder.AnsiUtils.boldYellow;
import static com.redhat.red.build.finder.AnsiUtils.cyan;
import static com.redhat.red.build.finder.AnsiUtils.green;
import static com.redhat.red.build.finder.AnsiUtils.red;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.finder.report.BuildStatisticsReport;
import com.redhat.red.build.finder.report.GAVReport;
import com.redhat.red.build.finder.report.HTMLReport;
import com.redhat.red.build.finder.report.NVRReport;
import com.redhat.red.build.finder.report.ProductReport;
import com.redhat.red.build.finder.report.Report;
import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;

@Command(abbreviateSynopsis = true, description = "Finds builds in Koji.", mixinStandardHelpOptions = true, name = "koji-build-finder", showDefaultValues = true, sortOptions = true, versionProvider = Main.ManifestVersionProvider.class)
public final class Main implements Callable<Void> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private ExecutorService pool;

    private EmbeddedCacheManager cacheManager;

    @Spec
    private CommandSpec commandSpec;

    @Option(names = {"-a", "--archive-type"}, paramLabel = "STRING", description = "Add a Koji archive type to check.", converter = FilenameConverter.class)
    private List<String> archiveTypes = ConfigDefaults.ARCHIVE_TYPES;

    @Option(names = {"--cache-lifespan"}, paramLabel = "LONG", description = "Specify cache lifespan.")
    private Long cacheLifespan = ConfigDefaults.CACHE_LIFESPAN;

    @Option(names = {"--cache-max-idle"}, paramLabel = "LONG", description = "Specify cache maximum idle time.")
    private Long cacheMaxIdle = ConfigDefaults.CACHE_MAX_IDLE;

    @Option(names = {"-c", "--config"}, paramLabel = "FILE", description = "Specify configuration file to use.")
    private File configFile = new File(ConfigDefaults.CONFIG);

    @Option(names = {"-d", "--debug"}, description = "Enable debug logging.")
    private boolean debug = false;

    @Option(names = {"--disable-cache"}, description = "Disable local cache.")
    private boolean disableCache = ConfigDefaults.DISABLE_CACHE;

    @Option(names = {"--disable-recursion"}, description = "Disable recursion.")
    private boolean disableRecursion = ConfigDefaults.DISABLE_RECURSION;

    @Option(names = {"-e", "--archive-extension"}, paramLabel = "STRING", description = "Add a Koji archive type extension to check.", converter = FilenameConverter.class)
    private List<String> archiveExtensions = ConfigDefaults.ARCHIVE_EXTENSIONS;

    @Option(names = {"-k", "--checksum-only"}, description = "Only checksum files and do not find builds.")
    private boolean checksumOnly = ConfigDefaults.CHECKSUM_ONLY;

    @Option(names = {"--koji-hub-url"}, paramLabel = "URL", description = "Set Koji hub URL.")
    private URL kojiHubURL;

    @Option(names = {"--koji-web-url"}, paramLabel = "URL", description = "Set Koji web URL.")
    private URL kojiWebURL;

    @Option(names = {"--krb-ccache"}, paramLabel = "FILE", description = "Set location of Kerberos credential cache.")
    private File krbCCache;

    @Option(names = {"--krb-keytab"}, paramLabel = "FILE", description = "Set location of Kerberos keytab.")
    private File krbKeytab;

    @Option(names = {"--krb-password"}, paramLabel = "STRING", description = "Set Kerberos password.")
    private String krbPassword;

    @Option(names = {"--krb-principal"}, paramLabel = "STRING", description = "Set Kerberos client principal.")
    private String krbPrincipal;

    @Option(names = {"--krb-service"}, paramLabel = "STRING", description = "Set Kerberos client service.")
    private String krbService;

    @Option(names = {"-o", "--output-directory"}, paramLabel = "FILE", description = "Set output directory.")
    private File outputDirectory;

    @Option(names = {"-q", "--quiet"}, description = "Disable all logging.")
    private boolean quiet = false;

    @Option(names = {"-t", "--checksum-type"}, paramLabel = "CHECKSUM", description = "Set checksum type (${COMPLETION-CANDIDATES}).")
    private KojiChecksumType checksumType = ConfigDefaults.CHECKSUM_TYPE;

    @Option(names = {"--use-builds-file"}, description = "Use builds file.")
    private boolean useBuildsFile = ConfigDefaults.USE_BUILDS_FILE;

    @Option(names = {"--use-checksums-file"}, description = "Use checksums file.")
    private boolean useChecksumsFile = ConfigDefaults.USE_CHECKSUMS_FILE;

    @Option(names = {"-x", "--exclude"}, paramLabel = "PATTERN", description = "Add a pattern to exclude from build lookup.")
    private List<Pattern> excludes = ConfigDefaults.EXCLUDES;

    @Parameters(arity = "1..*", paramLabel = "FILE", description = "One or more files.")
    private List<File> files;

    public Main() {

    }

    public static void main(String... args) {
        Main main = new Main();

        try {
            Ansi ansi = System.getProperty("picocli.ansi") == null ? Ansi.ON : (Boolean.getBoolean("picocli.ansi") ? Ansi.ON : Ansi.OFF);
            CommandLine.call(main, System.out, ansi, args);
            System.exit(0);
        } catch (picocli.CommandLine.ExecutionException e) {
            LOGGER.error("Error: {}", boldRed(e.getMessage()), e);
            System.exit(1);
        } finally {
            main.closeCaches();
            main.shutdownPool();
        }
    }

    public BuildConfig setupBuildConfig(ParseResult parseResult) throws IOException {
        BuildConfig config;

        if (configFile.exists()) {
            config = BuildConfig.load(configFile);
        } else {
            LOGGER.info("Configuration file {} does not exist. Implicitly creating with defaults.", green(configFile));
            config = new BuildConfig();
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--cache-lifespan")) {
            config.setCacheLifespan(cacheLifespan);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--cache-max-idle")) {
            config.setCacheMaxIdle(cacheMaxIdle);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--disable-cache")) {
            config.setDisableCache(disableCache);
            LOGGER.info("Local cache: {}", green("disabled"));
        } else {
            LOGGER.info("Local cache: {}, lifespan: {}, maxIdle: {}", green("enabled"), green(config.getCacheLifespan()), green(config.getCacheMaxIdle()));
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--disable-recursion")) {
            config.setDisableRecursion(disableRecursion);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("-k")) {
            config.setChecksumOnly(checksumOnly);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("-t")) {
            config.setChecksumType(checksumType);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("-a")) {
            config.setArchiveTypes(archiveTypes);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("-e")) {
            config.setArchiveExtensions(archiveExtensions);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("-x")) {
            config.setExcludes(excludes);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--koji-hub-url")) {
            config.setKojiHubURL(kojiHubURL);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--koji-web-url")) {
            config.setKojiWebURL(kojiWebURL);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--krb-ccache")) {
            LOGGER.debug("Kerberos ccache: {}", krbCCache);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--krb-keytab")) {
            LOGGER.debug("Kerberos keytab {}", krbKeytab);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--krb-service")) {
            LOGGER.debug("Kerberos service: {}", krbService);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--krb-principal")) {
            LOGGER.debug("Kerberos principal: {}", krbPrincipal);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--krb-password")) {
            LOGGER.debug("Read Kerberos password");
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--use-builds-file")) {
            config.setUseBuildsFile(useBuildsFile);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--use-checksums-file")) {
            config.setUseChecksumsFile(useChecksumsFile);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("-o")) {
            LOGGER.info("Output will be stored in directory: {}", green(outputDirectory));
        }

        return config;
    }

    private void initCaches(BuildConfig config) {
        KojiBuild.KojiBuildExternalizer externalizer = new KojiBuild.KojiBuildExternalizer();
        GlobalConfiguration globalConfig = new GlobalConfigurationBuilder().serialization().addAdvancedExternalizer(externalizer.getId(), externalizer).build();

        cacheManager = new DefaultCacheManager(globalConfig);

        String location = new File(ConfigDefaults.CONFIG).getParent();
        Configuration configuration = new ConfigurationBuilder().expiration().lifespan(config.getCacheLifespan()).maxIdle(config.getCacheMaxIdle()).wakeUpInterval(-1L).persistence().passivation(false).addSingleFileStore().shared(false).preload(true).fetchPersistentState(true).purgeOnStartup(false).location(location).build();

        cacheManager.defineConfiguration("files-" + config.getChecksumType(), configuration);
        cacheManager.defineConfiguration("checksums-" + config.getChecksumType(), configuration);
        cacheManager.defineConfiguration("builds", configuration);
    }

    private void closeCaches() {
        if (cacheManager == null) {
            return;
        }

        try {
            cacheManager.close();
        } catch (IOException e) {
            LOGGER.warn("Error closing cache manager: {}", red(e.getMessage()));
            LOGGER.debug("Error", e);
        }
    }

    private void shutdownPool() {
        if (pool == null) {
            return;
        }

        Utils.shutdownAndAwaitTermination(pool);
    }

    public void writeConfiguration(File configFile, BuildConfig config) {
        if (!configFile.exists()) {
            File configDir = configFile.getParentFile();

            if (configDir != null) {
                boolean created = configDir.mkdirs();

                if (!created) {
                    LOGGER.debug("mkdirs returned {} for {}", created, configDir);
                }
            }

            try {
                JSONUtils.dumpObjectToFile(config, configFile);
            } catch (IOException e) {
                LOGGER.warn("Error writing configuration file: {}", red(e.getMessage()));
                LOGGER.debug("Error", e);
            }
        }
    }

    public List<File> createFileList(List<File> files) {
        List<File> inputs = new ArrayList<>();

        for (File file : files) {
            if (!file.canRead()) {
                LOGGER.warn("Could not read file: {}", file.getPath());
                continue;
            }

            if (file.isDirectory()) {
                LOGGER.debug("Adding all files in directory: {}", file.getPath());
                inputs.addAll(FileUtils.listFiles(file, null, true));
            } else {
                LOGGER.debug("Adding file: {}", file.getPath());
                inputs.add(file);
            }
        }

        return inputs;
    }

    @Override
    public Void call() throws Exception {
        if (quiet) {
            disableLogging();
        } else if (debug) {
            enableDebugLogging();
        }

        LOGGER.info("{}", green("                          __  __.         __.__                         "));
        LOGGER.info("{}", green("                         |  |/ _|____    |__|__|                        "));
        LOGGER.info("{}", green("                         |    < /  _ \\   |  |  |                        "));
        LOGGER.info("{}", green("                         |  |  (  <_> )  |  |  |                        "));
        LOGGER.info("{}", green("                         |__|__ \\____/\\__|  |__|                        "));
        LOGGER.info("{}", green("________      .__.__      .___\\__________.__           .___            "));
        LOGGER.info("{}", green("\\____   \\__ __|__|  |   __| _/ \\_   _____|__| ____   __| _/___________ "));
        LOGGER.info("{}", green(" |  |  _|  |  |  |  |  / __ |   |    __) |  |/    \\ / __ _/ __ \\_  __ \\"));
        LOGGER.info("{}", green(" |  |   |  |  |  |  |_/ /_/ |   |     \\  |  |   |  / /_/ \\  ___/|  | \\/)"));
        LOGGER.info("{}", green(" |____  |____/|__|____\\____ |   \\___  /  |__|___|  \\____ |\\___  |__|   "));
        LOGGER.info("{}", green("      \\/                   \\/       \\/           \\/     \\/    \\/       "));
        LOGGER.info("{}{} (SHA: {})", String.format("%" +  Math.max(0, 79 - String.format("%s (SHA: %s)", BuildFinder.getVersion(), BuildFinder.getScmRevision()).length() - 7) + "s", ""), boldYellow(BuildFinder.getVersion()), cyan(BuildFinder.getScmRevision()));
        LOGGER.info("{}", green(""));

        LOGGER.info("Using configuration file: {}", green(configFile));

        BuildConfig config = null;

        try {
            config = setupBuildConfig(commandSpec.commandLine().getParseResult());
        } catch (IOException e) {
            LOGGER.error("Error reading configuration file: {}", boldRed(e.getMessage()));
            LOGGER.debug("Error", e);
            System.exit(1);
        }

        LOGGER.debug("{}", config);

        if (!config.getChecksumOnly()) {
            if (config.getKojiHubURL() == null) {
                LOGGER.error("Must set koji-hub-url");
                System.exit(1);
            }

            if (config.getKojiWebURL() == null) {
                LOGGER.error("Must set koji-web-url");
                System.exit(1);
            }
        }

        writeConfiguration(configFile, config);

        List<File> inputs = createFileList(files);

        boolean created = outputDirectory.mkdirs();

        if (!created) {
            LOGGER.debug("mkdirs returned {} for {}", created, outputDirectory);
        }

        File checksumFile = new File(outputDirectory, BuildFinder.getChecksumFilename(config.getChecksumType()));
        Map<String, Collection<String>> checksums = null;

        LOGGER.info("Checksum type: {}", green(config.getChecksumType()));

        if (config.getUseChecksumsFile() && checksumFile.exists()) {
            try {
                LOGGER.info("Loading checksums from file: {}", green(checksumFile));
                checksums = JSONUtils.loadChecksumsFile(checksumFile);
            } catch (IOException e) {
                LOGGER.error("Error loading checksums file: {}", boldRed(e.getMessage()));
                LOGGER.debug("Error", e);
                System.exit(1);
            }
        }

        if (checksumOnly) {
            if (cacheManager == null && !config.getDisableCache()) {
                initCaches(config);
            }

            DistributionAnalyzer analyzer = new DistributionAnalyzer(inputs, config, cacheManager);
            checksums = analyzer.checksumFiles().asMap();

            try {
                analyzer.outputToFile(outputDirectory);
            } catch (IOException e) {
                LOGGER.error("Error writing checksums file: {}", boldRed(e.getMessage()));
                LOGGER.debug("Error", e);
                System.exit(1);
            }

            if (checksums == null || checksums.isEmpty()) {
                LOGGER.warn("The list of checksums is empty. If this is unexpected, try removing the checksum cache ({}) and try again.", checksumFile.getAbsolutePath());
            }

            System.exit(0);
        }

        BuildFinder finder = null;
        Map<Integer, KojiBuild> builds = null;
        File buildsFile = new File(outputDirectory, BuildFinder.getBuildsFilename());

        if (config.getUseBuildsFile() && buildsFile.exists()) {
            LOGGER.info("Loading builds from file: {}", green(buildsFile.getPath()));

            try {
                builds = JSONUtils.loadBuildsFile(buildsFile);
            } catch (IOException e) {
                LOGGER.error("Error loading builds file: {}", boldRed(e.getMessage()));
                LOGGER.debug("Error", e);
                System.exit(1);
            }
        } else {
            if (cacheManager == null && !config.getDisableCache()) {
                initCaches(config);
            }

            pool = Executors.newFixedThreadPool(2);

            DistributionAnalyzer analyzer = new DistributionAnalyzer(inputs, config, cacheManager);
            Future<Map<String, Collection<String>>> futureChecksums = pool.submit(analyzer);

            boolean isKerberos = krbService != null && krbPrincipal != null && krbPassword != null || krbCCache != null || krbKeytab != null;

            try (KojiClientSession session = isKerberos ? new KojiClientSession(config.getKojiHubURL(), krbService, krbPrincipal, krbPassword, krbCCache, krbKeytab) : new KojiClientSession(config.getKojiHubURL())) {
                if (isKerberos) {
                    LOGGER.info("Using Koji session with Kerberos service: {}", green(krbService));
                } else {
                    LOGGER.info("Using anonymous Koji session");
                }

                if (cacheManager == null && !config.getDisableCache()) {
                    initCaches(config);
                }

                finder = new BuildFinder(session, config, analyzer, cacheManager);

                finder.setOutputDirectory(outputDirectory);

                Future<Map<Integer, KojiBuild>> futureBuilds = pool.submit(finder);

                try {
                    checksums = futureChecksums.get();
                } catch (ExecutionException e) {
                    LOGGER.error("Error getting checksums: {}", boldRed(e.getMessage()), e);
                    LOGGER.debug("Error", e);
                    System.exit(1);
                }

                try {
                    analyzer.outputToFile(outputDirectory);
                } catch (IOException e) {
                    LOGGER.error("Error writing checksums file: {}", boldRed(e.getMessage()));
                    LOGGER.debug("Error", e);
                    System.exit(1);
                }

                if (checksums == null || checksums.isEmpty()) {
                    LOGGER.warn("The list of checksums is empty. If this is unexpected, try removing the checksum cache ({}) and try again.", checksumFile.getAbsolutePath());
                }

                try {
                    builds = futureBuilds.get();
                } catch (ExecutionException e) {
                    LOGGER.error("Error getting builds {}", boldRed(e.getMessage()), e);
                    LOGGER.debug("Error", e);
                    System.exit(1);
                }

                JSONUtils.dumpObjectToFile(builds, buildsFile);
            } catch (KojiClientException e) {
                LOGGER.error("Error finding builds: {}", boldRed(e.getMessage()), e);
                LOGGER.debug("Koji Client Error", e);
                System.exit(1);
            } catch (IOException e) {
                LOGGER.error("Error finding builds: {}", boldRed(e.getMessage()), e);
                LOGGER.debug("Error", e);
                System.exit(1);
            }
        }

        if (builds != null && builds.containsKey(0) && (!builds.get(0).getArchives().isEmpty() || builds.keySet().size() > 1)) {
            List<KojiBuild> buildList = new ArrayList<>(builds.values());
            List<Report> reports = new ArrayList<>();

            reports.add(new BuildStatisticsReport(outputDirectory, buildList));
            reports.add(new ProductReport(outputDirectory, buildList));
            reports.add(new NVRReport(outputDirectory, buildList));
            reports.add(new GAVReport(outputDirectory, buildList));

            LOGGER.info("Generating {} reports", green(reports.size()));

            reports.forEach(report -> {
                try {
                    report.outputText();
                } catch (IOException e) {
                    LOGGER.error("Error writing {} report", boldRed(report.getName()));
                    LOGGER.debug("Report error", e);
                }
            });

            Report report = new HTMLReport(outputDirectory, files, buildList, config.getKojiWebURL(), Collections.unmodifiableList(reports));

            try {
                report.outputHTML();
            } catch (IOException e) {
                LOGGER.error("Error writing {} report", boldRed(report.getName()));
                LOGGER.debug("Report error", e);
            }

            LOGGER.info("{}", boldYellow("DONE"));
        } else {
            LOGGER.warn("Did not generate any reports since the list of builds is empty");
        }

        return null;
    }

    public ExecutorService getPool() {
        return pool;
    }

    public EmbeddedCacheManager getCacheManager() {
        return cacheManager;
    }

    static class ManifestVersionProvider implements IVersionProvider {
        ManifestVersionProvider() {

        }

        @Override
        public String[] getVersion() throws Exception {
            return new String[] {BuildFinder.getVersion() + " (SHA: " + BuildFinder.getScmRevision() + ")"};
        }
    }

   static class FilenameConverter implements ITypeConverter<String> {
        @Override
        public String convert(String value) throws Exception {
            if (value.matches(".*[\\/:\"*?<>|]+.*")) {
                throw new IllegalArgumentException("Invalid name");
            }

            return value;
        }
    }

    private static void disableLogging() {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.OFF);
    }

    private static void enableDebugLogging() {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

        rootLogger.setLevel(Level.DEBUG);

        LoggerContext loggerContext = rootLogger.getLoggerContext();

        loggerContext.getLoggerList().forEach(logger -> logger.setLevel(Level.DEBUG));

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
}
