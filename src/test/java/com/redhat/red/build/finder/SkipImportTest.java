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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.redhat.red.build.koji.KojiClientException;

public class SkipImportTest  {
    @Test
    public void verifyMultiImportsKeepEarliest() throws KojiClientException {
        final String checksum = "2e7e85f0ee97afde716231a6c792492a";
        final List<String> filenames = Collections.unmodifiableList(Arrays.asList("commons-lang-2.6-redhat-2.jar"));
        MockKojiClientSession session = new MockKojiClientSession("skip-import-test");
        BuildConfig config = new BuildConfig();
        BuildFinder finder = new BuildFinder(session, config);
        Map<String, Collection<String>> checksumTable = Collections.singletonMap(checksum, filenames);
        Map<Integer, KojiBuild> builds = finder.findBuilds(checksumTable);

        assertEquals(2, builds.size());
        assertTrue(builds.containsKey(0));
        assertTrue(builds.containsKey(228994));
        assertFalse(builds.containsKey(251444));
    }
}
