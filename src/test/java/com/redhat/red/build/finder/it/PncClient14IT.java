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
package com.redhat.red.build.finder.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import com.redhat.red.build.finder.pnc.client.PncClient14;
import com.redhat.red.build.finder.pnc.client.PncClientException;
import com.redhat.red.build.finder.pnc.client.model.Artifact;
import com.redhat.red.build.finder.pnc.client.model.BuildConfiguration;
import com.redhat.red.build.finder.pnc.client.model.BuildRecord;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PncClient14IT {
    private static final Logger LOGGER = LoggerFactory.getLogger(PncClient14IT.class);
    private static final String PROPERTY = "com.redhat.red.build.finder.it.pnc.url";
    private static final String PNC_URL = System.getProperty(PROPERTY);
    private static final int CONNECTION_TIMEOUT = 300000;
    private static final int READ_TIMEOUT = 900000;

    @Before
    public void verify() {
        assertNotNull("You must set -Dcom.redhat.red.build.finder.it.pnc.url=<pnc server>", PNC_URL);
    }

    @Test
    public void testDefaultPncClient14() throws PncClientException, MalformedURLException {
        PncClient14 client = new PncClient14(new URL(PNC_URL));
        getAnArtifactAndBuildRecord(client);
    }

    @Test
    public void testPncClient14WithTimeouts() throws PncClientException, MalformedURLException {
        PncClient14 client = new PncClient14(new URL(PNC_URL), CONNECTION_TIMEOUT, READ_TIMEOUT);
        getAnArtifactAndBuildRecord(client);
    }

    @Test
    public void testReturnEmptyListIfNoMatchingSha() throws PncClientException, MalformedURLException {
        PncClient14 client = new PncClient14(new URL(PNC_URL));
        List<Artifact> artifactsMd5 = client.getArtifactsByMd5("do-not-exist");
        assertNotNull(artifactsMd5);
        assertTrue(artifactsMd5.isEmpty());

        List<Artifact> artifactsSha1 = client.getArtifactsBySha1("do-not-exist");
        assertNotNull(artifactsSha1);
        assertTrue(artifactsSha1.isEmpty());

        List<Artifact> artifactsSha256 = client.getArtifactsBySha256("do-not-exist");
        assertNotNull(artifactsSha256);
        assertTrue(artifactsSha256.isEmpty());
    }

    @Test
    public void testReturnNullIfNoMatchingBuildRecord() throws PncClientException, MalformedURLException {
        PncClient14 client = new PncClient14(new URL(PNC_URL));
        BuildRecord record = client.getBuildRecordById(-1);
        assertNull(record);
    }

    private void getAnArtifactAndBuildRecord(PncClient14 client) throws PncClientException {
        final String md5 = "6a1a161bb7a696df419d149df034d189";
        final String sha1 = "8a50c8f39257bfe488f578bf52f70e641023b020";
        final String sha256 = "840b8f9f9a516fcc7e74da710480c6c8f5700ce9bdfa52a5cb752bfe9c54fe92";
        final int id = 15821;

        List<Artifact> artifactsMd5 = client.getArtifactsByMd5(md5);
        List<Artifact> artifactsSha1 = client.getArtifactsBySha1(sha1);
        List<Artifact> artifactsSha256 = client.getArtifactsBySha256(sha256);

        assertEquals(1, artifactsMd5.size());
        assertEquals(1, artifactsSha1.size());
        assertEquals(1, artifactsSha256.size());

        Artifact artifactMd5 = artifactsMd5.get(0);
        Artifact artifactSha1 = artifactsSha1.get(0);
        Artifact artifactSha256 = artifactsSha256.get(0);

        assertEquals(md5, artifactMd5.getMd5());
        assertEquals(sha1, artifactSha1.getSha1());
        assertEquals(sha256, artifactSha256.getSha256());

        assertEquals(artifactMd5.getId(), artifactSha1.getId());
        assertEquals(artifactSha1.getId(), artifactSha256.getId());

        LOGGER.debug("Artifact name: {}", artifactMd5.getFilename());

        List<Integer> buildRecordIds = artifactMd5.getBuildRecordIds();

        Collections.sort(buildRecordIds);

        assertTrue(buildRecordIds.contains(id));

        BuildRecord record = client.getBuildRecordById(id);
        BuildConfiguration configuration = client.getBuildConfigurationById(record.getBuildConfigurationId());

        assertEquals("hibernate-hibernate-core", record.getProjectName());

        assertEquals("hibernate-hibernate-core", configuration.getName());

        LOGGER.debug("Build configuration: {}", configuration.toString());

        List<Artifact> artifacts = client.getBuiltArtifactsById(record.getProjectId());

        assertFalse(artifacts.isEmpty());
    }
}
