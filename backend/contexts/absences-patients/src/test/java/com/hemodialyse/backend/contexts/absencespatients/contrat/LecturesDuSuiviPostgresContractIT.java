package com.hemodialyse.backend.contexts.absencespatients.contrat;

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
 * Contrat des lectures du suivi sur un PostgreSQL 16 RÉEL (schéma Flyway V1..V41) : mêmes tests que
 * {@link LecturesDuSuiviContrat}, plus l'ordre à égalité de date et l'échelle des montants propres à PostgreSQL. Base
 * JETABLE uniquement : {@code -Dabsences.pg16.url=jdbc:postgresql://…} (script {@code absences-pg16-rejeu.sh}).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${absences.pg16.url}",
        "spring.datasource.username=${absences.pg16.user:absences_test}",
        "spring.datasource.password=${absences.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "absences.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LecturesDuSuiviPostgresContractIT extends LecturesDuSuiviJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void viderLesLignes() {
        viderLesLignesDeTest();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Test
    void la_base_est_bien_postgresql_et_les_montants_gardent_leur_echelle() {
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 16");
        poserLePatient(CENTRE, AMRANI, "Karim", "Amrani");
        poserLAbsence(CENTRE, absence(1, AMRANI, java.time.LocalDate.of(2026, 3, 10), com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence.A_QUALIFIER));

        var lue = lecture().absence(CENTRE, id(1)).orElseThrow();

        assertThat(lue.montantHt().scale()).isEqualTo(2);
        assertThat(lue.tauxDeTva().scale()).isEqualTo(2);
        assertThat(lue.montantTtc()).isEqualByComparingTo("11900.00");
    }
}
