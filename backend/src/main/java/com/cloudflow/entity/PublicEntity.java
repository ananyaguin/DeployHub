package com.cloudflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.util.UUID;

@MappedSuperclass
public abstract class PublicEntity extends IdentifiedEntity {

    @Convert(converter = UuidCharConverter.class)
    @Column(
            name = "public_id",
            nullable = false,
            updatable = false,
            length = 36,
            columnDefinition = "char(36) character set ascii collate ascii_bin")
    private UUID publicId;

    public void assignIdentity() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }

    @PrePersist
    void assignPublicId() {
        assignIdentity();
    }

    public UUID getPublicId() {
        return publicId;
    }
}
