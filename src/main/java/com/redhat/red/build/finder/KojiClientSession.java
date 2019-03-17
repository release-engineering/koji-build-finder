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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.commonjava.util.jhttpc.auth.MemoryPasswordManager;
import org.commonjava.util.jhttpc.auth.PasswordManager;

import com.codahale.metrics.MetricRegistry;
import com.redhat.red.build.koji.KojiClient;
import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.KojiClientHelper;
import com.redhat.red.build.koji.config.KojiConfig;
import com.redhat.red.build.koji.config.SimpleKojiConfigBuilder;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveQuery;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveType;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildTypeInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiIdOrName;
import com.redhat.red.build.koji.model.xmlrpc.KojiRpmInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiSessionInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTagInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskRequest;
import com.redhat.red.build.koji.model.xmlrpc.messages.Constants;

public class KojiClientSession extends KojiClient implements ClientSession {
    private static final int DEFAULT_MAX_CONNECTIONS = 13;

    private static final int DEFAULT_THREAD_COUNT = 1;

    private static final int DEFAULT_TIMEOUT = (int) TimeUnit.MINUTES.toSeconds(15L);

    private KojiSessionInfo session;

    private KojiClientHelper helper;

    public KojiClientSession(KojiConfig config, PasswordManager passwordManager, ExecutorService executorService) throws KojiClientException {
        super(config, passwordManager, executorService);
        helper = new KojiClientHelper(this);
    }

    public KojiClientSession(KojiConfig config, PasswordManager passwordManager, ExecutorService executorService, MetricRegistry registry) throws KojiClientException {
        super(config, passwordManager, executorService, registry);
        helper = new KojiClientHelper(this);
    }

    public KojiClientSession(URL url) throws KojiClientException {
        super(new SimpleKojiConfigBuilder().withMaxConnections(DEFAULT_MAX_CONNECTIONS).withTimeout(DEFAULT_TIMEOUT).withConnectionPoolTimeout(DEFAULT_TIMEOUT).withKojiURL(url.toExternalForm()).build(), new MemoryPasswordManager(), Executors.newFixedThreadPool(DEFAULT_THREAD_COUNT));
        helper = new KojiClientHelper(this);
    }

    public KojiClientSession(URL url, String krbService, String krbPrincipal, String krbPassword, File krbCCache, File krbKeytab) throws KojiClientException {
        super(new SimpleKojiConfigBuilder().withMaxConnections(DEFAULT_MAX_CONNECTIONS).withTimeout(DEFAULT_TIMEOUT).withConnectionPoolTimeout(DEFAULT_TIMEOUT).withKojiURL(url != null ? url.toExternalForm() : null).withKrbService(krbService).withKrbCCache(krbCCache != null ? krbCCache.getPath() : null).withKrbKeytab(krbKeytab != null ? krbKeytab.getPath() : null).withKrbPrincipal(krbPrincipal).withKrbPassword(krbPassword).build(), new MemoryPasswordManager(), Executors.newFixedThreadPool(DEFAULT_THREAD_COUNT));
        session = super.login();
        helper = new KojiClientHelper(this);
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
    public List<List<KojiArchiveInfo>> listArchives(List<KojiArchiveQuery> queries) throws KojiClientException {
        return helper.listArchives(queries, session);
    }

    @Override
    public List<KojiBuildInfo> getBuild(List<KojiIdOrName> idsOrNames) throws KojiClientException {
        int size = idsOrNames.size();
        List<Object> args = new ArrayList<>(size);

        for (KojiIdOrName idOrName : idsOrNames) {
            Integer id = idOrName.getId();
            String name = idOrName.getName();

            if (id != null) {
                args.add(id);
            } else if (name != null) {
                args.add(name);
            } else {
                throw new KojiClientException("Invalid KojiIdOrName: " + idOrName);
            }
        }

        List<KojiBuildInfo> buildInfos = super.multiCall(Constants.GET_BUILD, args, KojiBuildInfo.class, session);

        if (buildInfos.isEmpty()) {
            return buildInfos;
        }

        List<KojiBuildTypeInfo> buildTypeInfos = super.multiCall(Constants.GET_BUILD_TYPE, args, KojiBuildTypeInfo.class, session);

        if (buildInfos.size() != buildTypeInfos.size()) {
            throw new KojiClientException("Sizes must be equal");
        }

        Iterator<KojiBuildInfo> it = buildInfos.iterator();
        Iterator<KojiBuildTypeInfo> it2 = buildTypeInfos.iterator();

        while (it.hasNext()) {
            KojiBuildTypeInfo.addBuildTypeInfo(it2.next(), it.next());
        }

        return buildInfos;
    }

    @Override
    public List<KojiRpmInfo> getRPM(List<KojiIdOrName> idsOrNames) throws KojiClientException {
        int size = idsOrNames.size();
        List<Object> args = new ArrayList<>(size);

        for (KojiIdOrName idOrName : idsOrNames) {
            Integer id = idOrName.getId();
            String name = idOrName.getName();

            if (id != null) {
                args.add(id);
            } else if (name != null) {
                args.add(name);
            } else {
                throw new KojiClientException("Invalid KojiIdOrName: " + idOrName);
            }
        }

        return super.multiCall(Constants.GET_RPM, args, KojiRpmInfo.class, session);
    }


    @Override
    public List<List<KojiRpmInfo>> listBuildRPMs(List<KojiIdOrName> idsOrNames) throws KojiClientException {
        return helper.listBuildRPMs(idsOrNames, session);
    }

    @Override
    public List<KojiTaskInfo> getTaskInfo(List<Integer> taskIds, List<Boolean> requests) throws KojiClientException {
        int taskIdsSize = taskIds.size();
        List<Object> args = new ArrayList<>(taskIdsSize);

        for (int i = 0; i < taskIdsSize; i++) {
            List<Object> req = new ArrayList<>(2);

            req.add(taskIds.get(i));
            req.add(requests.get(i));

            args.add(req);
        }

        return super.multiCall(Constants.GET_TASK_INFO, args, KojiTaskInfo.class, session);
    }

    @Override
    public List<List<KojiTagInfo>> listTags(List<KojiIdOrName> idsOrNames) throws KojiClientException {
        List<Integer> buildIds = idsOrNames.stream().map(KojiIdOrName::getId).collect(Collectors.toList());
        return helper.listTagsByIds(buildIds, session);
    }

    @Override
    public void close() {
        super.close();
    }

    public KojiSessionInfo getSession() {
        return session;
    }
}
