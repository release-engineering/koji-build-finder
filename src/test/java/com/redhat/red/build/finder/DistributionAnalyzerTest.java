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
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

public class DistributionAnalyzerTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void verifyEmptyList() throws IOException {
        List<File> af = Collections.emptyList();
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(af, config);
        da.checksumFiles();
    }

    @Test
    public void verifySize() throws IOException {
        List<File> af = new ArrayList<>();
        File test = temp.newFile();
        af.add(test);

        try (RandomAccessFile f = new RandomAccessFile(test, "rw")) {
            f.setLength(FileUtils.ONE_GB * 2);

            BuildConfig config = new BuildConfig();
            config.setArchiveExtensions(Collections.emptyList());
            DistributionAnalyzer da = new DistributionAnalyzer(af, config);
            da.checksumFiles();
        }
    }

    @Test
    public void verifyType() throws IOException {
        List<File> af = new ArrayList<>();
        String[] types = {"test.res", "test.ram", "test.tmp", "test.file"};

        for (String s : types) {
            File test = temp.newFile(s);
            af.add(test);
        }

        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(af, config);
        da.checksumFiles();
    }

    @Test
    public void verifyCacheClearance() throws IOException {
        // XXX: Skip on Windows due to <https://issues.apache.org/jira/browse/VFS-634>
        assumeFalse(SystemUtils.IS_OS_WINDOWS);

        File cache = temp.newFolder();
        System.setProperty("java.io.tmpdir", cache.getAbsolutePath());

        Collection<File> ls = FileUtils.listFiles(cache, null, true);
        assertTrue(ls.isEmpty());

        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.zip"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        da.checksumFiles();

        ls = FileUtils.listFiles(cache, null, true);
        assertTrue(ls.isEmpty());
    }

    @Test
    public void loadNestedZip() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.zip"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        MultiValuedMap<String, String> checksums = da.checksumFiles();

        assertEquals(25, checksums.size());
    }

    @Test
    public void loadNestedWar() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.war"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        MultiValuedMap<String, String> checksums = da.checksumFiles();

        assertEquals(7, checksums.size());
    }

    @Test
    public void loadManPageZip() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("symbolic.zip"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        MultiValuedMap<String, String> checksums = da.checksumFiles();

        assertEquals(4, checksums.size());
        assertTrue(systemOutRule.getLog().contains("Unable to process archive/compressed file"));
    }

    @Test
    public void loadNestedZipMultiThreaded() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.zip"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<String, Collection<String>> checksums = da.call();

        assertEquals(9, checksums.size());
    }

    @Test
    public void loadNestedZipNoRecursion() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.zip"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setDisableRecursion(true);
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        MultiValuedMap<String, String> checksums = da.checksumFiles();

        assertEquals(3, checksums.size());
    }

    @Test
    public void loadNested2ZipNoRecursion() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested2.zip"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setDisableRecursion(true);
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        MultiValuedMap<String, String> checksums = da.checksumFiles();

        assertEquals(2, checksums.size());
    }

    @Test
    public void loadNestedWarNoRecusion() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.war"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setDisableRecursion(true);
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        MultiValuedMap<String, String> checksums = da.checksumFiles();

        assertEquals(4, checksums.size());
    }

    @Test
    public void loadNestedTarGzNoRecusion() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.tar.gz"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setDisableRecursion(true);
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        MultiValuedMap<String, String> checksums = da.checksumFiles();

        assertEquals(4, checksums.size());
    }
}
