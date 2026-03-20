package com.obsidian.core.database.orm.model.cast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Attribute casting utilities.
 *
 * In your model, override casts() to define attribute types:
 *
 *   @Override
 *   protected Map<String, String> casts() {
 *       return Map.of(
 *           "active", "boolean",
 *           "settings", "json",
 *           "tags", "array",
 *           "birth_date", "date",
 *           "login_at", "datetime",
 *           "price", "double",
 *           "quantity", "integer"
 *       );
 *   }
 */
public class AttributeCaster {

    private static final Gson gson = new Gson();

    /**
     * Cast a raw database value to the specified type.
     */
    public static Object castGet(Object value, String castType) {
        if (value == null) return null;

        switch (castType.toLowerCase()) {
            case "int":
            case "integer":
                if (value instanceof Number) return ((Number) value).intValue();
                return Integer.parseInt(value.toString());

            case "long":
                if (value instanceof Number) return ((Number) value).longValue();
                return Long.parseLong(value.toString());

            case "float":
                if (value instanceof Number) return ((Number) value).floatValue();
                return Float.parseFloat(value.toString());

            case "double":
            case "decimal":
                if (value instanceof Number) return ((Number) value).doubleValue();
                return Double.parseDouble(value.toString());

            case "string":
                return value.toString();

            case "bool":
            case "boolean":
                if (value instanceof Boolean) return value;
                if (value instanceof Number) return ((Number) value).intValue() != 0;
                return Boolean.parseBoolean(value.toString());

            case "date":
                if (value instanceof LocalDate) return value;
                if (value instanceof java.sql.Date) return ((java.sql.Date) value).toLocalDate();
                return LocalDate.parse(value.toString());

            case "datetime":
            case "timestamp":
                if (value instanceof LocalDateTime) return value;
                if (value instanceof java.sql.Timestamp) return ((java.sql.Timestamp) value).toLocalDateTime();
                return LocalDateTime.parse(value.toString());

            case "json":
            case "object":
                if (value instanceof Map) return value;
                Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
                return gson.fromJson(value.toString(), mapType);

            case "array":
            case "list":
                if (value instanceof List) return value;
                Type listType = new TypeToken<List<Object>>() {}.getType();
                return gson.fromJson(value.toString(), listType);

            default:
                return value;
        }
    }

    /**
     * Cast a value for storage (model -> database).
     */
    public static Object castSet(Object value, String castType) {
        if (value == null) return null;

        switch (castType.toLowerCase()) {
            case "json":
            case "object":
            case "array":
            case "list":
                if (value instanceof String) return value;
                return gson.toJson(value);

            case "bool":
            case "boolean":
                if (value instanceof Boolean) return ((Boolean) value) ? 1 : 0;
                return value;

            case "date":
                if (value instanceof LocalDate) return java.sql.Date.valueOf((LocalDate) value);
                return value;

            case "datetime":
            case "timestamp":
                if (value instanceof LocalDateTime) return java.sql.Timestamp.valueOf((LocalDateTime) value);
                return value;

            default:
                return value;
        }
    }
}
