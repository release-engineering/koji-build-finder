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
package com.redhat.red.build.finder.pnc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.redhat.red.build.finder.BuildConfig;
import com.redhat.red.build.finder.pnc.client.model.BuildConfiguration;
import com.redhat.red.build.finder.pnc.client.model.BuildRecord;
import com.redhat.red.build.finder.pnc.client.model.PageParameterBuildConfiguration;
import com.redhat.red.build.finder.pnc.client.model.PageParameterBuildRecord;

/**
 * PncClient14 class for PNC 1.4.x APIs
 */
public class PncClient14 extends BasePncClient implements PncClient {

    /**
     * Create a new PncClient14 object
     *
     * @param config the build configuration
     */
    PncClient14(BuildConfig config) {
        super(config);
    }

    @Override
    public final BuildRecord getBuildRecordById(int id) throws PncClientException {
        try {
            String urlRequest = getBuildRecordByIdUrl(id);
            HttpResponse<PageParameterBuildRecord> buildRecords = Unirest.get(urlRequest)
                                                                         .asObject(PageParameterBuildRecord.class);
            PageParameterBuildRecord buildRecordData = buildRecords.getBody();

            if (buildRecordData == null) {
                return null;
            } else {
                return buildRecordData.getContent();
            }
        } catch (UnirestException e) {
            throw new PncClientException(e);
        }
    }

    @Override
    public final List<BuildRecord> getBuildRecordsById(List<Integer> ids) throws PncClientException {
        List<Future<HttpResponse<PageParameterBuildRecord>>> futures = new ArrayList<>(ids.size());

        for (Integer id : ids) {
            String urlRequest = getBuildRecordByIdUrl(id);
            Future<HttpResponse<PageParameterBuildRecord>> buildRecordFuture = Unirest.get(urlRequest).asObjectAsync(
                PageParameterBuildRecord.class);
            futures.add(buildRecordFuture);
        }

        List<BuildRecord> buildRecordList = new ArrayList<>(ids.size());

        for (Future<HttpResponse<PageParameterBuildRecord>> future : futures) {

            try {
                HttpResponse<PageParameterBuildRecord> buildRecord = future.get();
                PageParameterBuildRecord buildRecordData = buildRecord.getBody();

                if (buildRecordData == null) {
                    buildRecordList.add(null);
                } else {
                    buildRecordList.add(buildRecordData.getContent());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throw new PncClientException(e);
            }
        }

        return buildRecordList;
    }

    @Override
    public final BuildConfiguration getBuildConfigurationById(int id) throws PncClientException {
        try {
            String urlRequest = getBuildConfigurationByIdUrl(id);
            HttpResponse<BuildConfiguration> buildConfigurations = Unirest.get(urlRequest).asObject(
                BuildConfiguration.class);
            return buildConfigurations.getBody();
        } catch (UnirestException e) {
            throw new PncClientException(e);
        }
    }

    @Override
    public final List<BuildConfiguration> getBuildConfigurationsById(List<Integer> ids) throws PncClientException {
        List<Future<HttpResponse<PageParameterBuildConfiguration>>> futures = new ArrayList<>(ids.size());

        for (Integer id : ids) {
            String urlRequest = getBuildConfigurationByIdUrl(id);
            Future<HttpResponse<PageParameterBuildConfiguration>> buildConfigurationFuture = Unirest.get(urlRequest)
                                                                                                    .asObjectAsync(
                                                                                                        PageParameterBuildConfiguration.class);
            futures.add(buildConfigurationFuture);
        }

        List<BuildConfiguration> buildConfigurationList = new ArrayList<>(ids.size());

        for (Future<HttpResponse<PageParameterBuildConfiguration>> future : futures) {

            try {
                HttpResponse<PageParameterBuildConfiguration> buildConfiguration = future.get();
                PageParameterBuildConfiguration buildConfigurationData = buildConfiguration.getBody();

                if (buildConfigurationData == null) {
                    buildConfigurationList.add(null);
                } else {
                    buildConfigurationList.add(buildConfigurationData.getContent());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throw new PncClientException(e);
            }
        }

        return buildConfigurationList;
    }

    private String getBuildRecordByIdUrl(int id) {
        return getConfig().getPncURL() + "/pnc-rest/rest/build-records/" + id;
    }

    @Override
    String getBuiltArtifactsByIdUrl(int id) {
        return getConfig().getPncURL() + "/pnc-rest/rest/build-records/" + id + "/built-artifacts";
    }

    @Override
    String getBuildRecordPushResultByIdUrl(int id) {
        return getConfig().getPncURL() + "/pnc-rest/rest/build-record-push/status/" + id;
    }

    String getProductVersionByIdUrl(int id) {
        return getConfig().getPncURL() + "/pnc-rest/rest/product-versions/" + id;
    }

    private String getBuildConfigurationByIdUrl(int id) {
        return getConfig().getPncURL() + "/pnc-rest/rest/build-configurations/" + id;
    }

    @Override
    String getArtifactsUrl(String key, String value) {
        return getConfig().getPncURL() + "/pnc-rest/rest/artifacts" + "?" + key + "=" + value;
    }
}
