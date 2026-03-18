package com.obsidian.core.livecomponents.core;

/**
 * Type conversion utility for LiveComponent state and action parameters.
 * Handles primitive wrappers and String coercion.
 */
final class ValueConverter
{
    private ValueConverter() {}

    /**
     * Converts a value to the specified target type.
     *
     * @param value      Value to convert
     * @param targetType Target class
     * @return Converted value, or the original if no conversion is needed
     */
    static Object convert(Object value, Class<?> targetType)
    {
        if (value == null || targetType.isInstance(value)) return value;

        if (targetType == Integer.class || targetType == int.class)
            return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
        if (targetType == Long.class || targetType == long.class)
            return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
        if (targetType == Double.class || targetType == double.class)
            return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
        if (targetType == Boolean.class || targetType == boolean.class)
            return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
        if (targetType == String.class)
            return value.toString();

        return value;
    }
}