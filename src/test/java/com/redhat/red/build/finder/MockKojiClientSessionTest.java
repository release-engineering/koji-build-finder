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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class MockKojiClientSessionTest  {
    @Test
    public void verifyMockSession() throws Exception {
        final String CHECKSUM = "f18c45047648e5d6d3ad71319488604e";
        final String FILENAME = "bar-1.1.pom";
        MockKojiClientSession session = new MockKojiClientSession();
        BuildConfig config = new BuildConfig();
        BuildFinder finder = new BuildFinder(session, config);
        Map<String, Collection<String>> checksumTable = new HashMap<>();
        List<String> files = Collections.singletonList(FILENAME);
        checksumTable.put(CHECKSUM, files);
        Map<Integer, KojiBuild> builds = finder.findBuilds(checksumTable);
        assertEquals(2, builds.size());
    }
}
