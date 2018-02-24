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
import static org.junit.Assume.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;

public class DistributionAnalyzerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DistributionAnalyzerTest.class);

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void verifySize() throws IOException {
        ArrayList<File> af = new ArrayList<>();
        File test = temp.newFile();
        af.add(test);

        try (RandomAccessFile f = new RandomAccessFile(test, "rw")) {
            f.setLength(FileUtils.ONE_GB * 2);

            DistributionAnalyzer da = new DistributionAnalyzer(af, KojiChecksumType.md5.getAlgorithm());
            da.checksumFiles();
        }
    }

    @Test
    public void verifyType() throws IOException {
        ArrayList<File> af = new ArrayList<>();
        String[] types = {"test.res", "test.ram", "test.tmp", "test.file"};

        for (String s : types) {
            File test = temp.newFile(s);
            af.add(test);
        }

        DistributionAnalyzer da = new DistributionAnalyzer(af, KojiChecksumType.md5.getAlgorithm());
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
        DistributionAnalyzer da = new DistributionAnalyzer(target, KojiChecksumType.md5.getAlgorithm());
        da.checksumFiles();

        ls = FileUtils.listFiles(cache, null, true);
        assertTrue(ls.isEmpty());
    }

    @Test
    public void loadNestedZIP() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.zip"));
        DistributionAnalyzer da = new DistributionAnalyzer(target, KojiChecksumType.md5.getAlgorithm());
        da.checksumFiles();

        assertEquals(25, da.getMap().size());
        assertFalse(systemOutRule.getLog().contains("zip:zip:file:"));
        assertFalse(systemOutRule.getLog().contains("target/test-classes"));
    }

    @Test
    public void loadNestedWAR() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.war"));
        DistributionAnalyzer da = new DistributionAnalyzer(target, KojiChecksumType.md5.getAlgorithm());
        da.checksumFiles();

        assertEquals(7, da.getMap().size());
    }

    @Test
    public void loadManPageZIP() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("symbolic.zip"));
        DistributionAnalyzer da = new DistributionAnalyzer(target, KojiChecksumType.md5.getAlgorithm());
        da.checksumFiles();

        assertEquals(4, da.getMap().size());
        assertTrue(systemOutRule.getLog().contains("Unable to process archive/compressed file"));
    }

}
