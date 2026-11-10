package com.hemodialyse.backend.contexts.seance.contrat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat SE7d (ADR 0098) sur un PostgreSQL 16 RÉEL (schéma Flyway V1..Vn) : création nulle classée en PREMIER (KB-SEA-79). Base JETABLE
 * uniquement : {@code -Dseance.pg16.url=jdbc:postgresql://…} (script {@code seance-se7d-pg16-rejeu.sh}, mot de passe par la variable
 * d'environnement {@code SEANCE_PG16_PASSWORD}, jamais le port 5432).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${seance.pg16.url}",
        "spring.datasource.username=${seance.pg16.user:seance_test}",
        "spring.datasource.password=${seance.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "seance.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SeancesRealiseesEtConstantesPostgresContractIT extends SeancesRealiseesEtConstantesJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        preparer();
    }

    @AfterEach
    void apres() {
        vider();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Test
    void la_base_est_bien_postgresql_16() {
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 16");
    }
}
