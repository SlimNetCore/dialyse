package com.hemodialyse.backend.contexts.pilotagedirection.contrat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectures du Pilotage direction sur un PostgreSQL 16 RÉEL (schéma Flyway V1..Vn). Base JETABLE uniquement :
 * {@code -Dpilotagedirection.pg16.url=jdbc:postgresql://…} (script {@code pilotage-direction-pg16-rejeu.sh}, mot de passe par
 * {@code PD_PG16_PASSWORD} relu dans {@code pilotagedirection.pg16.password}).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${pilotagedirection.pg16.url}",
        "spring.datasource.username=${pilotagedirection.pg16.user:pilotage_direction_test}",
        "spring.datasource.password=${pilotagedirection.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "pilotagedirection.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LecturesDuPilotagePostgresContractIT extends LecturesDuPilotageContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        preparer();
    }

    @AfterEach
    void apres() {
        nettoyer();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
