package com.chasehorse.erp.common;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Null-safe ResultSet readers. */
public final class Rs {
    private Rs() {
    }

    public static Double dbl(ResultSet rs, String col) throws SQLException {
        BigDecimal b = rs.getBigDecimal(col);
        return b == null ? null : b.doubleValue();
    }

    public static Long lng(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    public static Integer integer(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    public static OffsetDateTime ts(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, OffsetDateTime.class);
    }

    public static UUID uuid(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, UUID.class);
    }
}
