package com.cloudflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.Hibernate;

@MappedSuperclass
public abstract class IdentifiedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void stampCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IdentifiedEntity that)) {
            return false;
        }
        return id != null
                && Hibernate.getClass(this) == Hibernate.getClass(other)
                && id.equals(that.getId());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(Hibernate.getClass(this));
    }
}
