package com.cloudflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "deployments",
        uniqueConstraints =
                @UniqueConstraint(name = "uq_deployments_tenant_slug", columnNames = {"tenant_id", "slug"}))
public class Deployment extends TimestampedPublicEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeploymentStatus status;

    @Column(name = "current_version_number")
    private Integer currentVersionNumber;

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public DeploymentStatus getStatus() {
        return status;
    }

    public void setStatus(DeploymentStatus status) {
        this.status = status;
    }

    public Integer getCurrentVersionNumber() {
        return currentVersionNumber;
    }

    public void setCurrentVersionNumber(Integer currentVersionNumber) {
        this.currentVersionNumber = currentVersionNumber;
    }
}
