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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildState;

public class DeletedBuildTest {
    private BuildFinder finder;

    @Before
    public void init() {
        MockKojiClientSession session = new MockKojiClientSession("deleted-build-test");
        BuildConfig config = new BuildConfig();
        finder = new BuildFinder(session, config);
    }

    @Test
    public void verifyDeletedBuild() throws KojiClientException {
        Map<String, Collection<String>> checksumTable = new HashMap<>(2);

        checksumTable.put("706c11702729457f7228f3f1ab4d3791", Collections.singletonList("wildfly-core-security-7.5.9.Final-redhat-2.jar"));

        Map<BuildSystemInteger, KojiBuild> builds = finder.findBuildsSlow(checksumTable);

        assertEquals(2, builds.size());
        assertTrue(builds.containsKey(new BuildSystemInteger(0)));
        assertTrue(builds.containsKey(new BuildSystemInteger(505412, BuildSystem.koji)));
        assertEquals(KojiBuildState.DELETED, builds.get(new BuildSystemInteger(505412, BuildSystem.koji)).getBuildInfo().getBuildState());
    }

    @Test
    public void verifyCompletedBuilds() throws KojiClientException {
        Map<String, Collection<String>> checksumTable = new HashMap<>(2);

        checksumTable.put("e767ccd9be81091d3b10a54a2a402a95", Collections.singletonList("hibernate-validator-6.0.7.Final-redhat-1.pom"));
        checksumTable.put("b59a0c8832966db746fa3943a901f780", Collections.singletonList("hibernate-validator-cdi-6.0.7.Final-redhat-1.pom"));

        Map<BuildSystemInteger, KojiBuild> builds = finder.findBuildsSlow(checksumTable);

        assertEquals(2, builds.size());
        assertTrue(builds.containsKey(new BuildSystemInteger(0)));
        assertTrue(builds.containsKey(new BuildSystemInteger(659231, BuildSystem.koji)));
        assertEquals(KojiBuildState.COMPLETE, builds.get(new BuildSystemInteger(659231, BuildSystem.koji)).getBuildInfo().getBuildState());
    }

    @Test
    public void verifyDeletedAndCompleteBuilds() throws KojiClientException {
        Map<String, Collection<String>> checksumTable = new HashMap<>(3);

        checksumTable.put("36f95ca365830463f581d576eb2f1f84", Collections.singletonList("wildfly-core-security-7.5.9.Final-redhat-2.pom"));
        checksumTable.put("59ef4fa1ef35fc0fc074dbfab196c0cd", Collections.singletonList("wildfly-core-security-7.5.9.Final-redhat-2.jar"));
        checksumTable.put("706c11702729457f7228f3f1ab4d3791", Collections.singletonList("wildfly-core-security-7.5.9.Final-redhat-2.jar"));

        Map<BuildSystemInteger, KojiBuild> builds = finder.findBuildsSlow(checksumTable);

        assertEquals(3, builds.size());
        assertTrue(builds.containsKey(new BuildSystemInteger(0)));
        assertTrue(builds.containsKey(new BuildSystemInteger(500366, BuildSystem.koji)));
        assertTrue(builds.containsKey(new BuildSystemInteger(505412, BuildSystem.koji)));
        assertEquals(KojiBuildState.COMPLETE, builds.get(new BuildSystemInteger(500366, BuildSystem.koji)).getBuildInfo().getBuildState());
        assertEquals(KojiBuildState.DELETED, builds.get(new BuildSystemInteger(505412, BuildSystem.koji)).getBuildInfo().getBuildState());
    }
}
