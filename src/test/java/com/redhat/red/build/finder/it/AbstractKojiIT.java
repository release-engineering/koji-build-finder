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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.commonjava.util.jhttpc.auth.MemoryPasswordManager;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Slf4jReporter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.red.build.finder.BuildConfig;
import com.redhat.red.build.finder.ConfigDefaults;
import com.redhat.red.build.koji.KojiClient;
import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.config.SimpleKojiConfig;
import com.redhat.red.build.koji.config.SimpleKojiConfigBuilder;
import com.redhat.red.build.koji.model.xmlrpc.KojiSessionInfo;

public abstract class AbstractKojiIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractKojiIT.class);

    private static final int DEFAULT_THREAD_COUNT = 1;

    private ScheduledReporter reporter;

    private KojiClient kojiClient;

    private KojiSessionInfo session;

    private BuildConfig config;

    protected static final int MAX_CONNECTIONS = 20;

    protected static final MetricRegistry REGISTRY = new MetricRegistry();

    @Before
    public void setup() throws IOException, KojiClientException {
        final Path configPath = Paths.get(ConfigDefaults.CONFIG);

        final File configFile = configPath.toFile();

        if (!configFile.exists()) {
            throw new IOException("File not found: " + configFile.getAbsolutePath());
        }

        final ObjectMapper mapper = new ObjectMapper();

        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.config = mapper.readValue(configFile, BuildConfig.class);

        final String kojiHubURL = config.getKojiHubURL();

        if (kojiHubURL == null || kojiHubURL.isEmpty()) {
            throw new IOException("You must set koji-hub-url in: " + configFile.getAbsolutePath());
        }

        final SimpleKojiConfig kojiConfig = new SimpleKojiConfigBuilder().withKojiURL(kojiHubURL).withMaxConnections(MAX_CONNECTIONS).build();

        this.kojiClient = new KojiClient(kojiConfig, new MemoryPasswordManager(), Executors.newFixedThreadPool(DEFAULT_THREAD_COUNT), REGISTRY);

        this.reporter = Slf4jReporter.forRegistry(REGISTRY).outputTo(LOGGER).convertRatesTo(TimeUnit.SECONDS).convertDurationsTo(TimeUnit.SECONDS).build();

        reporter.start(600, TimeUnit.SECONDS);
    }

    public KojiClient getKojiClient() {
        return kojiClient;
    }

    public KojiSessionInfo getSession() {
        return session;
    }

    public BuildConfig getConfig() {
        return config;
    }

    @After
    public void tearDown() {
        if (kojiClient != null) {
            kojiClient.close();
        }

        if (reporter != null) {
            reporter.stop();

            reporter.report();
        }
    }
}
