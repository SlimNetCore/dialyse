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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La migration Flyway {@code V31__index_performance.sql} (production, PostgreSQL) et les {@code @Index} des entités JPA
 * (développement, H2) doivent déclarer les mêmes index : sinon le développement local ne reflète plus la production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class IndexPerformanceMigrationTest {

    private static final String MIGRATION = "db/migration/V31__index_performance.sql";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private static List<String> indexDeclares() throws Exception {
        String sql = new String(new ClassPathResource(MIGRATION).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("CREATE INDEX IF NOT EXISTS (\\w+) ON (\\w+)").matcher(sql);
        List<String> noms = new java.util.ArrayList<>();
        while (matcher.find()) {
            noms.add(matcher.group(1).toUpperCase());
        }
        return noms;
    }

    @Test
    void every_index_of_the_migration_is_also_declared_on_the_jpa_entities() throws Exception {
        List<String> existants = jdbc.queryForList("SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES", String.class)
                .stream().map(String::toUpperCase).toList();
        List<String> declares = indexDeclares();

        assertFalse(declares.isEmpty());
        for (String index : declares) {
            assertTrue(existants.contains(index), "index absent côté entités JPA (H2) : " + index);
        }
    }

    @Test
    void the_migration_script_is_idempotent_and_runs_cleanly() throws Exception {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(MIGRATION));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(MIGRATION));
        }
    }
}
