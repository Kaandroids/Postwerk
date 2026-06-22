package com.postwerk.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Hibernate AttributeConverter for pgvector: vector column ↔ float[].
 * Uses pgvector's string representation format: [1.0,2.0,3.0]
 */
@Converter(autoApply = false)
public class VectorType implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < attribute.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(attribute[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        // pgvector returns format: [1.0,2.0,3.0]
        String trimmed = dbData.trim();
        if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (trimmed.isEmpty()) return new float[0];
        String[] parts = trimmed.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
