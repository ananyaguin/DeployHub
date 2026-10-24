package com.cloudflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "tenants", uniqueConstraints = @UniqueConstraint(name = "uq_tenants_slug", columnNames = "slug"))
public class Tenant extends TimestampedPublicEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 63)
    private String slug;

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
}
