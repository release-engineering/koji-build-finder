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

import java.util.List;
import java.util.Map;

import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveQuery;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveType;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTagInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskRequest;

public interface ClientSession {
    List<KojiArchiveInfo> listArchives(KojiArchiveQuery query) throws KojiClientException;

    Map<String, KojiArchiveType> getArchiveTypeMap() throws KojiClientException;

    KojiBuildInfo getBuild(int buildId) throws KojiClientException;

    KojiTaskInfo getTaskInfo(int taskId, boolean request) throws KojiClientException;

    KojiTaskRequest getTaskRequest(int taskId) throws KojiClientException;

    List<KojiTagInfo> listTags(int id) throws KojiClientException;

    void enrichArchiveTypeInfo(List<KojiArchiveInfo> archiveInfos) throws KojiClientException;

    void close();
}
