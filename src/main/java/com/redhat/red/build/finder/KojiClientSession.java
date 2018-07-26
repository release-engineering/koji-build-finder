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

import static com.redhat.red.build.finder.AnsiUtils.green;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.commonjava.util.jhttpc.auth.MemoryPasswordManager;
import org.commonjava.util.jhttpc.auth.PasswordManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.KojiClient;
import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.config.KojiConfig;
import com.redhat.red.build.koji.config.SimpleKojiConfigBuilder;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveQuery;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveType;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiSessionInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTagInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskRequest;

public class KojiClientSession implements ClientSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(KojiClientSession.class);

    private static final int DEFAULT_THREAD_COUNT = 1;

    private KojiClient client;

    private KojiSessionInfo session;

    public KojiClientSession(KojiClient client) {
        this.client = client;
    }

    public KojiClientSession(KojiConfig config, PasswordManager passwordManager, ExecutorService executorService) throws KojiClientException {
        client = new KojiClient(config, passwordManager, executorService);
    }

    public KojiClientSession(String url) throws KojiClientException {
        client = new KojiClient(new SimpleKojiConfigBuilder().withKojiURL(url).build(), new MemoryPasswordManager(), Executors.newFixedThreadPool(DEFAULT_THREAD_COUNT));
    }

    public KojiClientSession(String url, String krbService, String krbPrincipal, String krbPassword, String krbCCache, String krbKeytab) throws KojiClientException {
        client = new KojiClient(new SimpleKojiConfigBuilder().withKojiURL(url).withKrbService(krbService).withKrbCCache(krbCCache).withKrbKeytab(krbKeytab).withKrbPrincipal(krbPrincipal).withKrbPassword(krbPassword).build(), new MemoryPasswordManager(), Executors.newFixedThreadPool(DEFAULT_THREAD_COUNT));

        if (krbService != null && ((krbPrincipal != null && krbPassword != null) || krbCCache != null || krbKeytab != null)) {
            LOGGER.info("Logging into Kerberos service: {}", green(krbService));
            session = client.login();
        }
    }

    @Override
    public List<KojiArchiveInfo> listArchives(KojiArchiveQuery query) throws KojiClientException {
        return client.listArchives(query, session);
    }

    @Override
    public Map<String, KojiArchiveType> getArchiveTypeMap() throws KojiClientException {
        return client.getArchiveTypeMap(session);
    }

    @Override
    public KojiBuildInfo getBuild(int buildId) throws KojiClientException {
        return client.getBuildInfo(buildId, session);
    }

    @Override
    public KojiTaskInfo getTaskInfo(int taskId, boolean request) throws KojiClientException {
        return client.getTaskInfo(taskId, request, session);
    }

    @Override
    public KojiTaskRequest getTaskRequest(int taskId) throws KojiClientException {
        return client.getTaskRequest(taskId, session);
    }

    @Override
    public List<KojiTagInfo> listTags(int id) throws KojiClientException {
        return client.listTags(id, session);
    }

    @Override
    public void enrichArchiveTypeInfo(List<KojiArchiveInfo> archiveInfos) throws KojiClientException {
        client.enrichArchiveTypeInfo(archiveInfos, session);
    }

    @Override
    public void close() {
        client.close();
    }
}
