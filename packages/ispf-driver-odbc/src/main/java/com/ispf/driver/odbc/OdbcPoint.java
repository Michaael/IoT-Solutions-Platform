package com.ispf.driver.odbc;

/**
 * Point mapping: JDBC result column name to read from the configured query.
 */
public record OdbcPoint(String columnName) {

    public static OdbcPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("ODBC point mapping (column name) is blank");
        }
        return new OdbcPoint(raw.trim());
    }
}
