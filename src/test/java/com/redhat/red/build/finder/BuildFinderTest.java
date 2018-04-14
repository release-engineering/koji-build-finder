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
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;

import ch.qos.logback.classic.Level;

public class BuildFinderTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private File target;

    @Before
    public void setTarget() throws IOException {
        this.target = new File(TestUtils.resolveFileResource("./", "").getParentFile().getParentFile(), "pom.xml");
    }

    @Test
    public void verifyDirectory() throws IOException {
        File folder = temp.newFolder();
        KojiChecksumType checksumType = KojiChecksumType.sha1;
        BuildConfig config = new BuildConfig();

        config.setChecksumOnly(true);
        config.setChecksumType(checksumType);

        DistributionAnalyzer da = new DistributionAnalyzer(Collections.singletonList(new File(target.getAbsolutePath())), config);
        da.checksumFiles();
        da.outputToFile(new File(folder, BuildFinder.getChecksumFilename(checksumType)));

        File[] file = folder.listFiles();

        assertTrue(file != null && file.length == 1);
        assertTrue(file[0].getName().equals(BuildFinder.getChecksumFilename(checksumType)));
    }

    @Test
    public void verifyloadChecksumsFile() throws IOException {
        File folder = temp.newFolder();
        KojiChecksumType checksumType = KojiChecksumType.md5;

        BuildConfig config = new BuildConfig();

        config.setChecksumOnly(true);
        config.setChecksumType(checksumType);

        DistributionAnalyzer da = new DistributionAnalyzer(Collections.singletonList(new File(target.getAbsolutePath())), config);
        da.checksumFiles();
        da.outputToFile(new File(folder, BuildFinder.getChecksumFilename(checksumType)));

        Map<String, Collection<String>> checksums = JSONUtils.loadChecksumsFile(new File(folder, BuildFinder.getChecksumFilename(checksumType)));

        assertEquals(1, checksums.size());
    }

    @Test
    public void verifyDebug() throws IOException {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level level = root.getLevel();

        try {
            root.setLevel(Level.INFO);

            assertFalse(root.isDebugEnabled());

            Main.setDebug();

            assertTrue(root.isDebugEnabled());
        } finally {
            root.setLevel(level);
        }
    }
}
