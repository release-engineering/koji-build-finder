package com.redhat.red.build.finder.pnc.client;

import java.util.List;

import com.redhat.red.build.finder.pnc.client.model.Artifact;
import com.redhat.red.build.finder.pnc.client.model.BuildConfiguration;
import com.redhat.red.build.finder.pnc.client.model.BuildRecord;
import com.redhat.red.build.finder.pnc.client.model.BuildRecordPushResult;
import com.redhat.red.build.finder.pnc.client.model.ProductVersion;

/**
 * Interface to interact with PNC server
 *
 * @author Pedro Ruivo
 */
public interface PncClient {

    /**
     * Get a list of artifacts with matching md5. Returns empty list if no matching artifacts
     *
     * @param value md5 value
     * @return list of artifacts
     * @throws PncClientException in case something goes wrong
     */
    List<Artifact> getArtifactsByMd5(String value) throws PncClientException;

    /**
     * Get a list of artifacts with matching sha1. Returns empty list if no matching artifacts
     *
     * @param value sha1 value
     * @return list of artifacts
     * @throws PncClientException in case something goes wrong
     */
    List<Artifact> getArtifactsBySha1(String value) throws PncClientException;

    /**
     * Get a list of artifacts with matching sha256. Returns empty list if no matching artifacts
     *
     * @param value sha256 value
     * @return list of artifacts
     * @throws PncClientException in case something goes wrong
     */
    List<Artifact> getArtifactsBySha256(String value) throws PncClientException;

    List<List<Artifact>> getArtifactsByMd5(List<String> values) throws PncClientException;

    List<List<Artifact>> getArtifactsBySha1(List<String> values) throws PncClientException;

    List<List<Artifact>> getArtifactsBySha256(List<String> values) throws PncClientException;

    /**
     * Get a list of artifacts with matching id. Returns empty list if no matching artifacts
     *
     * @param id buildrecord id
     * @return list of artifacts
     * @throws PncClientException in case something goes wrong
     */
    List<Artifact> getBuiltArtifactsById(int id) throws PncClientException;

    List<List<Artifact>> getBuiltArtifactsById(List<Integer> ids) throws PncClientException;

    List<BuildConfiguration> getBuildConfigurationsById(List<Integer> ids) throws PncClientException;

    /**
     * Get the BuildRecord object from the buildrecord id. Returns null if no buildrecord found
     *
     * @param id buildrecord id
     * @return buildrecord DTO
     * @throws PncClientException if something goes wrong
     */
    BuildRecord getBuildRecordById(int id) throws PncClientException;

    List<BuildRecord> getBuildRecordsById(List<Integer> ids) throws PncClientException;

    /**
     * Get the BuildRecordPushResult object from the buildrecord id. Returns null if no buildrecord push result found
     *
     * @param id buildrecord id
     * @return buildrecord push result DTO
     * @throws PncClientException if something goes wrong
     */
    BuildRecordPushResult getBuildRecordPushResultById(int id) throws PncClientException;


    /**
     * Get the BuildConfiguration object from the buildconfiguration id. Returns null if no buildconfiguration found
     *
     * @param id buildconfiguration id
     * @return buildconfiguration DTO
     * @throws PncClientException if something goes wrong
     */
    BuildConfiguration getBuildConfigurationById(int id) throws PncClientException;

    /**
     * Get the ProductVersion object from the productversion id. Returns null if no productversion found
     *
     * @param id productversion id
     * @return productversion DTO
     * @throws PncClientException if something goes wrong
     */
    ProductVersion getProductVersionById(int id) throws PncClientException;

    List<ProductVersion> getProductVersionsById(List<Integer> ids) throws PncClientException;

    List<BuildRecordPushResult> getBuildRecordPushResultsById(List<Integer> ids) throws PncClientException;


}
