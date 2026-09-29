package com.hemodialyse.backend;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

class ProductionSeedScriptTest {

    @Test
    void production_seed_script_executes_without_demo_data() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:production-seed-script;MODE=PostgreSQL", "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/seed-production.sql"));
        }
    }
}
