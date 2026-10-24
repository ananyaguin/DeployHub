package com.cloudflow.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

@Converter
public class UuidCharConverter implements AttributeConverter<UUID, String> {

    @Override
    public String convertToDatabaseColumn(UUID value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    @Override
    public UUID convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return UUID.fromString(value);
    }
}
