package com.obsidian.core.livecomponents.core;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Type conversion utility for LiveComponent state and action parameters.
 * Handles primitive wrappers, String coercion, and complex types (List, Map, POJOs)
 * via Jackson for safe deserialization.
 */
final class ValueConverter
{
    private ValueConverter() {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Converts a value to the specified target type.
     *
     * Primitive types are converted directly.
     * Complex types (List, Map, POJOs) that arrive as LinkedHashMap from Jackson
     * are re-converted using {@link ObjectMapper#convertValue} to ensure
     * correct field mapping and type safety.
     *
     * @param value      Value to convert
     * @param targetType Target class
     * @return Converted value, or the original if no conversion is needed
     */
    static Object convert(Object value, Class<?> targetType)
    {
        if (value == null || targetType.isInstance(value)) return value;

        // Primitives and String
        if (targetType == Integer.class || targetType == int.class)
            return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
        if (targetType == Long.class || targetType == long.class)
            return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
        if (targetType == Double.class || targetType == double.class)
            return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
        if (targetType == Float.class || targetType == float.class)
            return value instanceof Number ? ((Number) value).floatValue() : Float.parseFloat(value.toString());
        if (targetType == Boolean.class || targetType == boolean.class)
            return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
        if (targetType == String.class)
            return value.toString();

        // Complex types — Jackson arrived as LinkedHashMap or List, re-convert safely
        try {
            return MAPPER.convertValue(value, targetType);
        } catch (IllegalArgumentException e) {
            throw new com.obsidian.core.livecomponents.ComponentException(
                    "Cannot convert value of type " + value.getClass().getSimpleName()
                            + " to " + targetType.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Converts a value to a parameterized type (e.g. List&lt;String&gt;).
     *
     * @param value      Value to convert
     * @param javaType   Target Jackson JavaType (use {@code MAPPER.getTypeFactory()} to build)
     * @return Converted value
     */
    static Object convertToType(Object value, JavaType javaType)
    {
        if (value == null) return null;
        try {
            return MAPPER.convertValue(value, javaType);
        } catch (IllegalArgumentException e) {
            throw new com.obsidian.core.livecomponents.ComponentException(
                    "Cannot convert value to " + javaType + ": " + e.getMessage(), e);
        }
    }
}