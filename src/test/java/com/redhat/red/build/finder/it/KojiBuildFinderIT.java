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

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.redhat.red.build.finder.BuildFinder;
import com.redhat.red.build.finder.ClientSession;
import com.redhat.red.build.finder.DistributionAnalyzer;
import com.redhat.red.build.finder.KojiClientSession;
import com.redhat.red.build.koji.KojiClientException;

public class KojiBuildFinderIT extends AbstractKojiIT {
    private static final String PROPERTY = "com.redhat.red.build.finder.it.distribution.url";

    private static final String URL = System.getProperty(PROPERTY);

    private static final int CONNECTION_TIMEOUT = 300000;

    private static final int READ_TIMEOUT = 900000;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testChecksumsAndFindBuilds() throws KojiClientException, IOException {
        assertNotNull("You must set the property " + PROPERTY + " pointing to the URL of the distribution to test with", URL);

        final URL url = new URL(URL);
        final File file = new File(folder.newFolder(), url.getPath());

        FileUtils.copyURLToFile(url, file, CONNECTION_TIMEOUT, READ_TIMEOUT);

        final Timer timer = REGISTRY.timer(MetricRegistry.name(KojiBuildFinderIT.class, "checksums"));

        final Timer.Context context = timer.time();

        final Map<String, Collection<String>> map;

        try {
            final DistributionAnalyzer da = new DistributionAnalyzer(Collections.singletonList(file), getConfig());
            map = da.checksumFiles().asMap();
        } finally {
            context.stop();
        }

        final Timer timer2 = REGISTRY.timer(MetricRegistry.name(KojiBuildFinderIT.class, "builds"));

        final Timer.Context context2 = timer2.time();

        try {
            final ClientSession session = new KojiClientSession(getKojiClient());
            final BuildFinder finder = new BuildFinder(session, getConfig());

            finder.findBuilds(map);
        } finally {
            context2.stop();
        }
    }
}
