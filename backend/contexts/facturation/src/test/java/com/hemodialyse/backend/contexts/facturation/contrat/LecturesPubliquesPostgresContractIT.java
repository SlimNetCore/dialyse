package com.hemodialyse.backend.contexts.facturation.contrat;

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
 * Contrat des lectures publiées sur un PostgreSQL 16 RÉEL (schéma Flyway V1..V41) : mêmes tests que {@link LecturesPubliquesContrat}, plus la sensibilité à la casse du préfixe de reprise.
 * Base JETABLE uniquement : {@code -Dfacturation.pg16.url=jdbc:postgresql://…}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${facturation.pg16.url}",
        "spring.datasource.username=${facturation.pg16.user:facturation_test}",
        "spring.datasource.password=${facturation.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "facturation.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LecturesPubliquesPostgresContractIT extends LecturesPubliquesJdbcContrat {

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
    void should_exclude_only_the_exact_upper_case_prefix_of_the_carried_over_invoices() {
        facture(CENTRE, PATIENT_1, "reprise-minuscule", "2026-03-01", "2026-03-31", "2026-03-31");
        facture(CENTRE, PATIENT_1, "REPRISE-MAJUSCULE", "2026-03-01", "2026-03-31", "2026-03-31");
        facture(CENTRE, PATIENT_1, "NOUVELLE-REPRISE-1", "2026-03-01", "2026-03-31", "2026-03-31");

        assertThat(facturesEmises().emisesEntre(CENTRE, java.time.LocalDate.of(2026, 3, 1), java.time.LocalDate.of(2026, 3, 31)))
                .extracting(f -> f.numero()).containsExactlyInAnyOrder("reprise-minuscule", "NOUVELLE-REPRISE-1");
    }
}
