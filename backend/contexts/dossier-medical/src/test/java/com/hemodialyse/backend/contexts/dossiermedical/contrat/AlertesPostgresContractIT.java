package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Alertes;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AlertesJdbc;
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
 * Contrat des alertes d'observance sur un PostgreSQL 16 RÉEL (schéma Flyway V1..V53, dont la migration V53 des colonnes de détail,
 * KB-MED-33). Base JETABLE uniquement : {@code -Ddossiermedical.pg16.url=jdbc:postgresql://…}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${dossiermedical.pg16.url}",
        "spring.datasource.username=${dossiermedical.pg16.user:dossier_medical_test}",
        "spring.datasource.password=${dossiermedical.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "dossiermedical.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AlertesPostgresContractIT extends AlertesContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        AlertesJdbcContratTest.vider(jdbc);
    }

    @AfterEach
    void apres() {
        AlertesJdbcContratTest.vider(jdbc);
    }

    @Override
    protected Alertes alertes() {
        return new AlertesJdbc(jdbc);
    }
}
