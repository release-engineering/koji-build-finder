package com.redhat.red.build.finder.pnc.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.redhat.red.build.finder.BuildConfig;
import com.redhat.red.build.finder.pnc.client.model.BuildConfiguration;
import com.redhat.red.build.finder.pnc.client.model.BuildRecord;

/**
 * {@link PncClient} implementation that knows how to communicate with PNC version 2.
 *
 * @author Pedro Ruivo
 */
public class PncClient20 extends BasePncClient implements PncClient {

    private static final String BUILD_ARTIFACTS = "%s/pnc-rest/v2/builds/%s/artifacts/built";
    private static final String BUILD_RECORD = "%s/pnc-rest/v2/build-records/%s";
    private static final String BREW_PUSH_RESULT = "%s/pnc-rest/v2/builds/%s/brew-push";
    private static final String PRODUCT_VERSIONS = "%s/pnc-rest/v2/products/%s/versions";
    private static final String BUILD_CONFIG = "%s/pnc-rest/v2/build-configs/%s";
    private static final String ARTIFACTS_BY_CHECKSUM = "%s/pnc-rest/v2/artifacts?%s=%s";

    /**
     * Create a new PncClient20 object
     *
     * @param config the build configuration
     */
    PncClient20(BuildConfig config) {
        super(config);
    }

    @Override
    public final List<BuildConfiguration> getBuildConfigurationsById(List<Integer> ids) throws PncClientException {
        return getMultiple(ids, this::getBuildConfigurationByIdAsync);
    }

    @Override
    public final BuildRecord getBuildRecordById(int id) throws PncClientException {
        return getSingle(id, this::getBuildRecordByIdAsync);
    }

    @Override
    public final List<BuildRecord> getBuildRecordsById(List<Integer> ids) throws PncClientException {
        return getMultiple(ids, this::getBuildRecordByIdAsync);
    }

    @Override
    public final BuildConfiguration getBuildConfigurationById(int id) throws PncClientException {
        return getSingle(id, this::getBuildConfigurationByIdAsync);
    }

    private static <K, V> V getSingle(K input, Function<K, Future<HttpResponse<V>>> mapper)
        throws PncClientException {
        try {
            return mapper.apply(input).get().getBody();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new PncClientException(e.getCause());
        }
        return null;
    }

    private Future<HttpResponse<BuildRecord>> getBuildRecordByIdAsync(int id) {
        String urlRequest = getBuildRecordByIdUrl(id);
        return Unirest.get(urlRequest).asObjectAsync(BuildRecord.class);
    }

    private String getBuildRecordByIdUrl(int id) {
        return String.format(BUILD_RECORD, getConfig().getPncURL(), id);
    }

    private static <K, V> List<V> getMultiple(List<K> inputs, Function<K, Future<HttpResponse<V>>> mapper)
        throws PncClientException {
        Iterator<Future<HttpResponse<V>>> iterator = inputs.stream().map(mapper).iterator();
        List<V> outputs = new ArrayList<>(inputs.size());
        while (iterator.hasNext()) {
            try {
                outputs.add(iterator.next().get().getBody());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throw new PncClientException(e.getCause());
            }
        }
        return outputs;
    }

    private Future<HttpResponse<BuildConfiguration>> getBuildConfigurationByIdAsync(int id) {
        String urlRequest = getBuildConfigurationByIdUrl(id);
        return Unirest.get(urlRequest).asObjectAsync(BuildConfiguration.class);
    }

    private String getBuildConfigurationByIdUrl(int id) {
        return String.format(BUILD_CONFIG, getConfig().getPncURL(), id);
    }

    @Override
    String getBuiltArtifactsByIdUrl(int id) {
        return String.format(BUILD_ARTIFACTS, getConfig().getPncURL(), id);
    }

    @Override
    String getBuildRecordPushResultByIdUrl(int id) {
        return String.format(BREW_PUSH_RESULT, getConfig().getPncURL(), id);
    }

    @Override
    String getProductVersionByIdUrl(int id) {
        return String.format(PRODUCT_VERSIONS, getConfig().getPncURL(), id);
    }

    @Override
    String getArtifactsUrl(String checksumMethod, String value) {
        return String.format(ARTIFACTS_BY_CHECKSUM, getConfig().getPncURL(), checksumMethod, value);
    }
}
