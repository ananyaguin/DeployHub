package com.cloudflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "deployment_events")
public class DeploymentEvent extends IdentifiedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false, updatable = false)
    private Deployment deployment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_version_id", updatable = false)
    private DeploymentVersion deploymentVersion;

    @Column(name = "event_type", nullable = false, updatable = false, length = 64)
    private String eventType;

    @Column(nullable = false, updatable = false, length = 1000)
    private String message;

    public Deployment getDeployment() {
        return deployment;
    }

    public void setDeployment(Deployment deployment) {
        this.deployment = deployment;
    }

    public DeploymentVersion getDeploymentVersion() {
        return deploymentVersion;
    }

    public void setDeploymentVersion(DeploymentVersion deploymentVersion) {
        this.deploymentVersion = deploymentVersion;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
