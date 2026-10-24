package com.cloudflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "deployment_versions",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_deployment_versions_number",
                        columnNames = {"deployment_id", "version_number"}))
public class DeploymentVersion extends PublicEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false, updatable = false)
    private Deployment deployment;

    @Column(name = "version_number", nullable = false, updatable = false)
    private Integer versionNumber;

    public Deployment getDeployment() {
        return deployment;
    }

    public void setDeployment(Deployment deployment) {
        this.deployment = deployment;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }
}
