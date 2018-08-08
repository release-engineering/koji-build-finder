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

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.commonjava.util.jhttpc.auth.MemoryPasswordManager;
import org.commonjava.util.jhttpc.auth.PasswordManager;

import com.codahale.metrics.MetricRegistry;
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

public class KojiClientSession extends KojiClient implements ClientSession {
    private static final int DEFAULT_THREAD_COUNT = 1;

    private KojiSessionInfo session;

    public KojiClientSession(KojiConfig config, PasswordManager passwordManager, ExecutorService executorService) throws KojiClientException {
        super(config, passwordManager, executorService);
    }

    public KojiClientSession(URL url) throws KojiClientException {
        super(new SimpleKojiConfigBuilder().withKojiURL(url.toExternalForm()).build(), new MemoryPasswordManager(), Executors.newFixedThreadPool(DEFAULT_THREAD_COUNT));
    }

    public KojiClientSession(URL url, String krbService, String krbPrincipal, String krbPassword, File krbCCache, File krbKeytab) throws KojiClientException {
        super(new SimpleKojiConfigBuilder().withKojiURL(url != null ? url.toExternalForm() : null).withKrbService(krbService).withKrbCCache(krbCCache != null ? krbCCache.getPath() : null).withKrbKeytab(krbKeytab != null ? krbKeytab.getPath() : null).withKrbPrincipal(krbPrincipal).withKrbPassword(krbPassword).build(), new MemoryPasswordManager(), Executors.newFixedThreadPool(DEFAULT_THREAD_COUNT));
        session = super.login();
    }

    public KojiClientSession(KojiConfig config, PasswordManager passwordManager, ExecutorService executorService, MetricRegistry registry) throws KojiClientException {
        super(config, passwordManager, executorService, registry);
    }

    @Override
    public List<KojiArchiveInfo> listArchives(KojiArchiveQuery query) throws KojiClientException {
        return super.listArchives(query, session);
    }

    @Override
    public Map<String, KojiArchiveType> getArchiveTypeMap() throws KojiClientException {
        return super.getArchiveTypeMap(session);
    }

    @Override
    public KojiBuildInfo getBuild(int buildId) throws KojiClientException {
        return super.getBuildInfo(buildId, session);
    }

    @Override
    public KojiTaskInfo getTaskInfo(int taskId, boolean request) throws KojiClientException {
        return super.getTaskInfo(taskId, request, session);
    }

    @Override
    public KojiTaskRequest getTaskRequest(int taskId) throws KojiClientException {
        return super.getTaskRequest(taskId, session);
    }

    @Override
    public List<KojiTagInfo> listTags(int id) throws KojiClientException {
        return super.listTags(id, session);
    }

    @Override
    public void enrichArchiveTypeInfo(List<KojiArchiveInfo> archiveInfos) throws KojiClientException {
        super.enrichArchiveTypeInfo(archiveInfos, session);
    }

    @Override
    public void close() {
        super.close();
    }
}
