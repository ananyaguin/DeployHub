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

    @PrePersist
    void assignPublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }

    public UUID getPublicId() {
        return publicId;
    }
}
