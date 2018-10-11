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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

public class BuildConfigTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildConfigTest.class);

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void verifyDefaults() throws JsonProcessingException {
        BuildConfig bc = new BuildConfig();

        String s = JSONUtils.dumpString(bc);

        LOGGER.debug("Default configuration:\n{}", s);

        assertNotNull(s);

        assertEquals(ConfigDefaults.ARCHIVE_TYPES, bc.getArchiveTypes());
        assertEquals(ConfigDefaults.CHECKSUM_ONLY, bc.getChecksumOnly());
        assertEquals(ConfigDefaults.CHECKSUM_TYPES, bc.getChecksumTypes());
        assertEquals(ConfigDefaults.EXCLUDES, bc.getExcludes());
        assertEquals(ConfigDefaults.KOJI_HUB_URL, bc.getKojiHubURL());
        assertEquals(ConfigDefaults.KOJI_WEB_URL, bc.getKojiWebURL());
    }

    @Test
    public void verifyMapping() throws IOException {
        String json = "{\"archive-types\":[\"jar\"],\"excludes\":\"^(?!.*/pom\\\\.xml$).*/.*\\\\.xml$\",\"checksum-only\":true,\"checksum-type\":\"md5\"}";
        BuildConfig bc = BuildConfig.load(json);

        assertTrue(bc.getChecksumOnly());
        assertTrue(bc.getArchiveTypes().size() == 1 && bc.getArchiveTypes().get(0).equals("jar"));
    }

    @Test
    public void verifyMappingWithDefaults() throws IOException {
        String json = "{\"koji-hub-url\":\"https://my.url.com/hub\",\"koji-web-url\":\"https://my.url.com/web\"}";

        BuildConfig bc = BuildConfig.load(json);

        assertFalse(bc.getChecksumOnly());
        assertTrue(bc.getKojiHubURL().toExternalForm().equals("https://my.url.com/hub"));
        assertTrue(bc.getKojiWebURL().toExternalForm().equals("https://my.url.com/web"));
    }

    @Test
    public void verifyIgnoreUnknownProperties() throws IOException {
        String json = "{\"foo\":\"bar\"}";

        BuildConfig.load(json);
    }

    @Test
    public void verifySave() throws IOException {
        String json = "{\"archive-types\":[\"jar\"],\"excludes\":\"^(?!.*/pom\\\\.xml$).*/.*\\\\.xml$\",\"checksum-only\":true,\"checksum-type\":\"md5\"}";
        BuildConfig bc = BuildConfig.load(json);
        File file = temp.newFile();

        bc.save(file);

        BuildConfig bc2 = BuildConfig.load(file);

        assertEquals(bc.toString(), bc2.toString());
    }
}
