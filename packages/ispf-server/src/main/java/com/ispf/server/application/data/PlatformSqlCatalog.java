package com.ispf.server.application.data;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
public class PlatformSqlCatalog {

    private final String schemaPrefix;

    public PlatformSqlCatalog(DataSource dataSource) {
        this.schemaPrefix = resolvePrefix(dataSource);
    }

    public String table(String name) {
        return schemaPrefix + name;
    }

    private static String resolvePrefix(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if ("H2".equalsIgnoreCase(product)) {
                return "PUBLIC.";
            }
            return "public.";
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to resolve platform SQL catalog", ex);
        }
    }
}
