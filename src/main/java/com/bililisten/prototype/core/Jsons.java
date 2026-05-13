package com.bililisten.prototype.core;

import java.util.List;
import java.util.Map;

public final class Jsons {
    private Jsons() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value, String label) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException(label + " must be a JSON object.");
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object value, String label) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new IllegalArgumentException(label + " must be a JSON array.");
    }

    public static Map<String, Object> getObject(Map<String, Object> object, String key) {
        return asObject(require(object, key), key);
    }

    public static Map<String, Object> optObject(Map<String, Object> object, String key) {
        Object value = object.get(key);
        return value == null ? null : asObject(value, key);
    }

    public static List<Object> getList(Map<String, Object> object, String key) {
        return asList(require(object, key), key);
    }

    public static List<Object> optList(Map<String, Object> object, String key) {
        Object value = object.get(key);
        return value == null ? List.of() : asList(value, key);
    }

    public static String getString(Map<String, Object> object, String key) {
        Object value = require(object, key);
        if (value instanceof String string) {
            return string;
        }
        throw new IllegalArgumentException(key + " must be a string.");
    }

    public static String optString(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        throw new IllegalArgumentException(key + " must be a string.");
    }

    public static long getLong(Map<String, Object> object, String key) {
        Object value = require(object, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException(key + " must be a number.");
    }

    public static long optLong(Map<String, Object> object, String key, long defaultValue) {
        Object value = object.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException(key + " must be a number.");
    }

    public static int getInt(Map<String, Object> object, String key) {
        return Math.toIntExact(getLong(object, key));
    }

    public static int optInt(Map<String, Object> object, String key, int defaultValue) {
        Object value = object.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return Math.toIntExact(number.longValue());
        }
        throw new IllegalArgumentException(key + " must be a number.");
    }

    public static boolean hasNonNull(Map<String, Object> object, String key) {
        return object.containsKey(key) && object.get(key) != null;
    }

    private static Object require(Map<String, Object> object, String key) {
        if (!object.containsKey(key) || object.get(key) == null) {
            throw new IllegalArgumentException("Missing JSON property: " + key);
        }
        return object.get(key);
    }
}
