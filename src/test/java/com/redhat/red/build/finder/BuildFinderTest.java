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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import ch.qos.logback.classic.Level;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.slf4j.LoggerFactory;

public class BuildFinderTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    @Test
    public void verifyDebug() throws IOException {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        Level level = root.getLevel();

        try {
            File folder = temp.newFolder();
            File target = new File(TestUtils.resolveFileResource("./", "").getParentFile().getParentFile(), "pom.xml");

            BuildFinder.main(new String[] {"-d", "-k", "-o", folder.getAbsolutePath(), target.getAbsolutePath()});

            assertTrue(systemOutRule.getLog().contains(" DEBUG "));
        } finally {
            root.setLevel(level);
        }
    }

    @Test
    public void verifyDirectory() throws IOException {
        File target = new File(TestUtils.resolveFileResource("./", "").getParentFile().getParentFile(), "pom.xml");
        File folder = temp.newFolder();

        BuildFinder.main(new String[] {"-k", "-o", folder.getAbsolutePath(), target.getAbsolutePath()});

        File[] f = folder.listFiles();
        assertTrue(f != null && f.length == 1);
        assertTrue(f[0].getName().equals("checksums-md5.json"));
    }

    @Test
    public void verifyloadChecksumsFile() throws IOException {
        File target = new File(TestUtils.resolveFileResource("./", "").getParentFile().getParentFile(), "pom.xml");
        File folder = temp.newFolder();

        BuildFinder.main(new String[] {"-k", "-o", folder.getAbsolutePath(), target.getAbsolutePath()});

        Map<String, Collection<String>> checksums = JSONUtils.loadChecksumsFile(new File(folder, "checksums-md5.json"));

        assertEquals(checksums.keySet().size(), 1);
    }
}
