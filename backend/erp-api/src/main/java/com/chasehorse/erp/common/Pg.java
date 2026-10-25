package com.chasehorse.erp.common;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Small PostgreSQL helpers. */
public final class Pg {
    private Pg() {
    }

    /** Builds a text[] literal like {"a","b c"} with proper quoting, for use with CAST(:x AS text[]). */
    public static String textArray(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        return values.stream()
                .map(v -> '"' + v.replace("\\", "\\\\").replace("\"", "\\\"") + '"')
                .collect(Collectors.joining(",", "{", "}"));
    }

    /** Reads a java.sql.Array of text into a list (null-safe). */
    public static List<String> toList(java.sql.Array array) throws java.sql.SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] items = (Object[]) array.getArray();
        return java.util.Arrays.stream(items).map(String::valueOf).toList();
    }
}
