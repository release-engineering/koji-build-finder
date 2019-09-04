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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.redhat.red.build.finder.BuildFinder;
import com.redhat.red.build.finder.BuildSystemInteger;
import com.redhat.red.build.finder.ClientSession;
import com.redhat.red.build.finder.DistributionAnalyzer;
import com.redhat.red.build.finder.KojiBuild;
import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;

public class KojiBuildFinderIT extends AbstractKojiIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(KojiBuildFinderIT.class);

    private static final String PROPERTY = "com.redhat.red.build.finder.it.distribution.url";

    private static final String URL = System.getProperty(PROPERTY);

    private static final int CONNECTION_TIMEOUT = 300000;

    private static final int READ_TIMEOUT = 900000;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testChecksumsAndFindBuilds() throws KojiClientException, IOException, ExecutionException {
        assertNotNull("You must set the property " + PROPERTY + " pointing to the URL of the distribution to test with", URL);

        final URL url = new URL(URL);
        final File file = new File(folder.newFolder(), url.getPath());

        FileUtils.copyURLToFile(url, file, CONNECTION_TIMEOUT, READ_TIMEOUT);

        final Timer timer = REGISTRY.timer(MetricRegistry.name(KojiBuildFinderIT.class, "checksums"));

        final Timer.Context context = timer.time();

        final Map<KojiChecksumType, MultiValuedMap<String, String>> map;

        final ExecutorService pool = Executors.newFixedThreadPool(1 + getConfig().getChecksumTypes().size());

        final DistributionAnalyzer analyzer;

        final Future<Map<KojiChecksumType, MultiValuedMap<String, String>>> futureChecksum;

        try {
            analyzer = new DistributionAnalyzer(Collections.singletonList(file), getConfig());
            futureChecksum = pool.submit(analyzer);
        } finally {
            context.stop();
        }

        final Timer timer2 = REGISTRY.timer(MetricRegistry.name(KojiBuildFinderIT.class, "builds"));

        final Timer.Context context2 = timer2.time();

        try {
            final ClientSession session = getKojiClientSession();
            final BuildFinder finder = new BuildFinder(session, getConfig(), analyzer, null, getPncClient());
            finder.setOutputDirectory(folder.newFolder());
            Future<Map<BuildSystemInteger, KojiBuild>> futureBuilds = pool.submit(finder);
            Map<BuildSystemInteger, KojiBuild> builds = futureBuilds.get();
            map = futureChecksum.get();

            assertEquals(3, map.size());
            assertTrue(builds.size() >= 1);

            LOGGER.info("Map size: {}", map.size());
            LOGGER.info("Builds size: {}", builds.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            context2.stop();
        }
    }
}
