package com.hemodialyse.backend.contexts.patientcouverture.contrat;

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
 * Contrat du carnet de transporteurs sur un PostgreSQL 16 RÉEL (schéma Flyway V1..Vn) : mêmes tests que H2 et que le double. Base
 * JETABLE uniquement : {@code -Dpatientcouverture.pg16.url=jdbc:postgresql://…} (script {@code patient-couverture-rf1-pg16-rejeu.sh}).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${patientcouverture.pg16.url}",
        "spring.datasource.username=${patientcouverture.pg16.user:patient_couverture_test}",
        "spring.datasource.password=${patientcouverture.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "patientcouverture.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransporteursPostgresContractIT extends TransporteursJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
