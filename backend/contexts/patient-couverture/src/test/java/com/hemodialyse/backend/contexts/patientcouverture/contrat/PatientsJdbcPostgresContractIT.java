package com.hemodialyse.backend.contexts.patientcouverture.contrat;

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
 * Contrat des patients sur un PostgreSQL 16 RÉEL (schéma Flyway V1..Vn) : mêmes tests que H2. Base JETABLE uniquement :
 * {@code -Dpatientcouverture.pg16.url=jdbc:postgresql://…} (script {@code patient-couverture-pc1-pg16-rejeu.sh}).
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
class PatientsJdbcPostgresContractIT extends PatientsJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void vider() {
        for (Object centre : new Object[]{CENTRE.valeur(), AUTRE.valeur()}) {
            jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM attestation_droit WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM assure_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM assure WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM mouvement_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", centre);
        }
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
