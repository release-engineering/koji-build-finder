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

import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BuildConfigTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void verifyDefaults() {
        BuildConfig bc = new BuildConfig();

        assertEquals(bc.getArchiveTypes(), ConfigDefaults.ARCHIVE_TYPES);
        assertEquals(bc.getChecksumType(), ConfigDefaults.CHECKSUM_TYPE);
        assertEquals(bc.getChecksumOnly(), ConfigDefaults.CHECKSUM_ONLY);
        assertEquals(bc.getExcludes(), ConfigDefaults.EXCLUDES);
        assertEquals(bc.getKojiHubURL(), ConfigDefaults.KOJI_HUB_URL);
        assertEquals(bc.getKojiWebURL(), ConfigDefaults.KOJI_WEB_URL);
    }

    @Test
    public void verifyMapping() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

        String json = "{\"archive-types\":[\"jar\"],\"excludes\":\"^(?!.*/pom\\\\.xml$).*/.*\\\\.xml$\",\"checksum-only\":true,\"checksum-type\":\"md5\",\"koji-hub-url\":\"\",\"koji-web-url\":\"\"}";
        BuildConfig bc = mapper.readValue(json, BuildConfig.class);

        assertTrue(bc.getChecksumOnly());
        assertTrue(bc.getArchiveTypes().size() == 1 && bc.getArchiveTypes().get(0).equals("jar"));
    }

    @Test
    public void verifyMappingWithDefaults() throws IOException {
        String json = "{\"koji-hub-url\":\"https://my.url.com\",\"koji-web-url\":\"https://my.url.com/brew\"}";

        ObjectMapper mapper = new ObjectMapper();

        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

        BuildConfig bc = mapper.readValue(json, BuildConfig.class);

        assertTrue(bc.getKojiHubURL().equals("https://my.url.com"));
        assertFalse(bc.getChecksumOnly());
    }

    @Test
    public void verifyIgnoreUnknownProperties() throws IOException {
        String json = "{\"foo\":\"bar\"}";

        ObjectMapper mapper = new ObjectMapper();

        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        BuildConfig bc = mapper.readValue(json, BuildConfig.class);
    }
}
