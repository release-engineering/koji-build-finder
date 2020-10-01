package com.redhat.red.build.finder.pnc.client.model;

import java.io.Serializable;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Build configuration revision. Available in PNC2
 *
 * @author Pedro Ruivo
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BuildConfigurationRevision implements Serializable {

    private static final long serialVersionUID = -5920335553408051883L;
    private Integer id;
    private Integer rev;
    private String name;
    private String buildScript;
    private String scmRevision;
    private Instant creationTime;
    private Instant modificationTime;
    private String buildType;
    private String defaultAlignmentParams;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getRev() {
        return rev;
    }

    public void setRev(Integer rev) {
        this.rev = rev;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBuildScript() {
        return buildScript;
    }

    public void setBuildScript(String buildScript) {
        this.buildScript = buildScript;
    }

    public String getScmRevision() {
        return scmRevision;
    }

    public void setScmRevision(String scmRevision) {
        this.scmRevision = scmRevision;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Instant creationTime) {
        this.creationTime = creationTime;
    }

    public Instant getModificationTime() {
        return modificationTime;
    }

    public void setModificationTime(Instant modificationTime) {
        this.modificationTime = modificationTime;
    }

    public String getBuildType() {
        return buildType;
    }

    public void setBuildType(String buildType) {
        this.buildType = buildType;
    }

    public String getDefaultAlignmentParams() {
        return defaultAlignmentParams;
    }

    public void setDefaultAlignmentParams(String defaultAlignmentParams) {
        this.defaultAlignmentParams = defaultAlignmentParams;
    }

    @Override
    public String toString() {
        return "BuildConfigurationRevision [id=" + id + ", rev=" + rev + ", name=" + name + ", buildScript="
            + buildScript + ", scmRevision=" + scmRevision + ", creationTime=" + creationTime + ", modificationTime="
            + modificationTime + ", buildType=" + buildType + ", defaultAlignmentParams=" + defaultAlignmentParams
            + "]";
    }
}
