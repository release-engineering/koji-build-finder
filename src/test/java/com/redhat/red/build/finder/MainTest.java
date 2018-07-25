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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;

import ch.qos.logback.classic.Level;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;

public class MainTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().muteForSuccessfulTests().enableLog();

    public static ParseResult parseCommandLine(Object command, String[] args) throws ParameterException {
        CommandLine cmd = new CommandLine(command);

        ParseResult parseResult = cmd.parseArgs(args);

        return parseResult;
    }

    @Test
    public void verifyHelp() {
        exit.expectSystemExitWithStatus(0);

        String[] args = new String[] {"--help"};

        ParseResult parseResult = parseCommandLine(new Main(), args);

        assertTrue(parseResult.hasMatchedOption("--help"));

        Main.main(args);

        assertTrue(systemOutRule.getLogWithNormalizedLineSeparator().contains("Usage:"));
    }

    @Test
    public void verifyVersion() {
        exit.expectSystemExitWithStatus(0);

        String[] args = new String[] {"--version"};

        ParseResult parseResult = parseCommandLine(new Main(), args);

        assertTrue(parseResult.hasMatchedOption("--version"));

        Main.main(args);

        assertTrue(systemOutRule.getLogWithNormalizedLineSeparator().contains(BuildFinder.getVersion()));
    }

    @Test
    public void verifyParsing() throws IOException {
        File outputDirectory = temp.newFolder();
        File configFile = TestUtils.loadFile("config.json");
        File inputFile = TestUtils.loadFile("nested.war");

        URL hubURL = new URL("http://a.b");
        URL webURL = new URL("http://c.d");
        File krbCcache = temp.newFile();
        File krbKeytab = temp.newFile();
        String krbPassword = "test";
        String krbPrincipal = "test@TEST.ABC";
        String krbService = "testService";

        String[] args = new String[] {"-o", outputDirectory.toString(), "-c", configFile.toString(), "-k", "-t", "sha256", "-x", "", "-a", "jar", "-e", "jar", "--koji-hub-url", hubURL.toString(), "--koji-web-url", webURL.toString(), "--krb-ccache", krbCcache.toString(), "--krb-keytab", krbKeytab.toString(), "--krb-principal", krbPrincipal, "--krb-service", krbService.toString(), "--krb-password", krbPassword.toString(), inputFile.toString()};

        ParseResult parseResult = parseCommandLine(new Main(), args);

        File parsedOutputDirectory = parseResult.matchedOption("--output-directory").getValue();

        assertEquals(outputDirectory, parsedOutputDirectory);

        File parsedConfigFile = parseResult.matchedOption("--config").getValue();

        assertEquals(configFile, parsedConfigFile);

        assertTrue(parseResult.matchedOption("--checksum-only").getValue());

        assertEquals(KojiChecksumType.sha256, parseResult.matchedOption("--checksum-type").getValue());

        List<Pattern> excludes = parseResult.matchedOption("--exclude").getValue();

        assertEquals(1, excludes.size());

        List<String> archiveTypes = parseResult.matchedOption("--archive-type").getValue();

        assertEquals(1, archiveTypes.size());

        List<String> archiveExtensions = parseResult.matchedOption("--archive-extension").getValue();

        assertEquals(1, archiveExtensions.size());

        assertEquals(krbCcache, parseResult.matchedOption("--krb-ccache").getValue());
        assertEquals(krbKeytab, parseResult.matchedOption("--krb-keytab").getValue());
        assertEquals(krbPassword, parseResult.matchedOption("--krb-password").getValue());
        assertEquals(krbPrincipal, parseResult.matchedOption("--krb-principal").getValue());
        assertEquals(krbService, parseResult.matchedOption("--krb-service").getValue());
    }

    @Test
    public void verifyConfig() throws IOException {
        exit.expectSystemExitWithStatus(0);

        File outputDirectory = temp.newFolder();
        File configFile = TestUtils.loadFile("config.json");
        File inputFile = TestUtils.loadFile("nested.war");

        String[] args = new String[] {"--output-directory", outputDirectory.toString(), "--config", configFile.toString(), "--checksum-only", "--checksum-type", "sha256", inputFile.toString()};

        ParseResult parseResult = parseCommandLine(new Main(), args);

        File parsedOutputDirectory = parseResult.matchedOption("--output-directory").getValue();

        assertEquals(outputDirectory, parsedOutputDirectory);

        File parsedConfigFile = parseResult.matchedOption("--config").getValue();

        assertEquals(configFile, parsedConfigFile);

        assertTrue(parseResult.matchedOption("--checksum-only").getValue());

        assertEquals(KojiChecksumType.sha256, parseResult.matchedOption("--checksum-type").getValue());

        Main.main(args);

        assertTrue(systemOutRule.getLogWithNormalizedLineSeparator().contains(".war!"));
    }

    @Test
    public void verifyDebug() throws IOException {
        exit.expectSystemExitWithStatus(0);

        File outputDirectory = temp.newFolder();
        File configFile = TestUtils.loadFile("config.json");
        File inputFile = TestUtils.loadFile("nested.war");

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level level = root.getLevel();

        try {
            root.setLevel(Level.INFO);

            assertFalse(root.isDebugEnabled());

            String[] args = new String[] {"--output-directory", outputDirectory.toString(), "--config", configFile.toString(), "--checksum-only", "--checksum-type", "md5", "--debug", inputFile.toString()};

            ParseResult parseResult = parseCommandLine(new Main(), args);

            File parsedOutputDirectory = parseResult.matchedOption("--output-directory").getValue();

            assertEquals(outputDirectory, parsedOutputDirectory);

            File parsedConfigFile = parseResult.matchedOption("--config").getValue();

            assertEquals(configFile, parsedConfigFile);

            assertTrue(parseResult.matchedOption("--checksum-only").getValue());

            assertEquals(KojiChecksumType.md5, parseResult.matchedOption("--checksum-type").getValue());

            assertTrue(parseResult.matchedOption("--debug").getValue());

            Main.main(args);

            assertTrue(root.isDebugEnabled());

            assertTrue(systemOutRule.getLogWithNormalizedLineSeparator().contains("DEBUG"));
        } finally {
            root.setLevel(level);
        }
    }

    @Test
    public void verifyQuiet() throws IOException {
        exit.expectSystemExitWithStatus(0);

        File outputDirectory = temp.newFolder();
        File configFile = TestUtils.loadFile("config.json");
        File inputFile = TestUtils.loadFile("nested.war");

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level level = root.getLevel();

        try {
            root.setLevel(Level.INFO);

            assertTrue(root.isEnabledFor(Level.INFO));

            String[] args = new String[] {"--output-directory", outputDirectory.toString(), "--config", configFile.toString(), "--checksum-only", "--checksum-type", "md5", "--quiet", inputFile.toString()};

            ParseResult parseResult = parseCommandLine(new Main(), args);

            File parsedOutputDirectory = parseResult.matchedOption("--output-directory").getValue();

            assertEquals(outputDirectory, parsedOutputDirectory);

            File parsedConfigFile = parseResult.matchedOption("--config").getValue();

            assertEquals(configFile, parsedConfigFile);

            assertTrue(parseResult.matchedOption("--checksum-only").getValue());

            assertEquals(KojiChecksumType.md5, parseResult.matchedOption("--checksum-type").getValue());

            assertTrue(parseResult.matchedOption("--quiet").getValue());

            Main.main(args);

            assertTrue(root.isEnabledFor(Level.OFF));

            assertEquals(0, systemOutRule.getLogWithNormalizedLineSeparator().length());
        } finally {
            root.setLevel(level);
        }
    }
}
