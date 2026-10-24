package com.cloudflow.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidCharConverterTest {

    private final UuidCharConverter converter = new UuidCharConverter();

    @Test
    void roundTripsCanonicalForm() {
        UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String stored = converter.convertToDatabaseColumn(id);

        assertEquals("550e8400-e29b-41d4-a716-446655440000", stored);
        assertEquals(id, converter.convertToEntityAttribute(stored));
    }

    @Test
    void preservesNull() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
