package com.hemodialyse.backend.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La migration Flyway {@code V32__comptabilite_stock_journaux.sql} (production, PostgreSQL, {@code ddl-auto: validate})
 * et les entités JPA (développement, H2) doivent décrire les mêmes colonnes : sinon la production refuse de démarrer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ComptabiliteStockMigrationTest {

    private static final String MIGRATION = "db/migration/V32__comptabilite_stock_journaux.sql";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private static String script() throws Exception {
        return new String(new ClassPathResource(MIGRATION).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private List<String> colonnes(String table) {
        return jdbc.queryForList("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = ?",
                String.class, table.toUpperCase()).stream().map(String::toUpperCase).toList();
    }

    @Test
    void every_column_added_by_the_migration_is_mapped_by_a_jpa_entity() throws Exception {
        Matcher matcher = Pattern.compile("ALTER TABLE (\\w+)\\s+ADD COLUMN IF NOT EXISTS (\\w+)").matcher(script());
        List<String> ajoutees = new ArrayList<>();
        while (matcher.find()) {
            ajoutees.add(matcher.group(1) + "." + matcher.group(2));
            assertTrue(colonnes(matcher.group(1)).contains(matcher.group(2).toUpperCase()),
                    "colonne absente côté entités JPA (H2) : " + matcher.group(1) + "." + matcher.group(2));
        }
        assertEquals(13, ajoutees.size(), ajoutees.toString());
    }

    @Test
    void the_journal_table_has_the_same_columns_on_both_sides() {
        assertEquals(List.of("ACTIF", "CENTER_ID", "CODE", "ID", "LIBELLE"),
                colonnes("journaux_comptables").stream().sorted().toList());
    }

    @Test
    void the_migration_script_is_idempotent_and_runs_cleanly() throws Exception {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(MIGRATION));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(MIGRATION));
        }
        assertTrue(jdbc.queryForList("SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES", String.class).stream()
                .anyMatch("IDX_ECRITURE_CENTER_SOURCE"::equalsIgnoreCase));
    }
}
