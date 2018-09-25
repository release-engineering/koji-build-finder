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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.redhat.red.build.koji.KojiClientException;

public class EmptyBuildsTest  {
    @Test
    public void verifyEmptyChecksums() throws IOException, KojiClientException {
        BuildConfig config = new BuildConfig();
        DistributionAnalyzer da = new DistributionAnalyzer(Collections.emptyList(), config);

        da.checksumFiles();

        MockKojiClientSession session = new MockKojiClientSession("empty-builds-test");
        BuildFinder finder = new BuildFinder(session, config);
        finder.findBuildsSlow(Collections.emptyMap());
    }

    @Test
    public void verifyEmptyBuilds() throws KojiClientException {
        final String checksum = "abc";
        final List<String> filenames = Collections.unmodifiableList(Arrays.asList("test.abc"));
        MockKojiClientSession session = new MockKojiClientSession("empty-builds-test");
        BuildConfig config = new BuildConfig();
        BuildFinder finder = new BuildFinder(session, config);
        Map<String, Collection<String>> checksumTable = Collections.singletonMap(checksum, filenames);
        Map<Integer, KojiBuild> builds = finder.findBuildsSlow(checksumTable);

        assertEquals(1, builds.size());
        assertTrue(builds.containsKey(0));
    }
}
