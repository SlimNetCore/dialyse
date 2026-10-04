package com.hemodialyse.backend.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GmaoTablesMigrationTest {

    @Test
    void createsCostTableBeforeAddingAutomaticFlag() throws Exception {
        String databaseName = "gmao_cost_migration_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL", "sa", "");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V8__gmao_rectification_intervention.sql"));

            try (ResultSet result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = 'PUBLIC'
                      AND TABLE_NAME = 'GMAO_LIGNES_COUT_INTERVENTION'
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(8);
            }
        }
    }

    @Test
    void createsGmaoTablesWithForeignKeysToExistingCenterAndUserTables() throws Exception {
        String databaseName = "gmao_migration_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL", "sa", "");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE centers (id UUID PRIMARY KEY)");
            statement.execute("CREATE TABLE app_user (id UUID PRIMARY KEY)");

            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V5__gmao_tables.sql"));

            try (ResultSet result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                    WHERE TABLE_SCHEMA = 'PUBLIC'
                      AND TABLE_NAME LIKE 'GMAO_%'
                      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(19);
            }
        }
    }
}
