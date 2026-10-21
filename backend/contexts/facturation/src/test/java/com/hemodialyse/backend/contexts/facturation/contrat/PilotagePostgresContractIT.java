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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des lectures de pilotage sur un PostgreSQL 16 RÉEL (schéma Flyway V1..V41) : mêmes tests que {@link PilotageContrat},
 * plus les propriétés propres à PostgreSQL des requêtes de lecture (arrondi des sommes, extraction année/mois, comptage
 * distinct, ordre). Base JETABLE uniquement : {@code -Dfacturation.pg16.url=jdbc:postgresql://…}.
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
class PilotagePostgresContractIT extends PilotageJdbcContrat {

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
    void should_keep_the_numeric_14_2_scale_of_the_sums_of_stored_totals() {
        facture(CENTRE_A, PATIENT_1, "2026-02-10", "100.10", "19.02", "119.12", null, null, null);
        facture(CENTRE_A, PATIENT_2, "2026-02-11", "0.05", "0.01", "0.06", null, null, null);

        var tableau = tableau().du(CENTRE_A, YearMonth.of(2026, 2));
        var synthese = synthese().de(CENTRE_A, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        assertThat(tableau.chiffreDAffairesTtc()).hasToString("119.18");
        assertThat(tableau.chiffreDAffairesHt()).hasToString("100.15");
        assertThat(synthese.totalTva()).hasToString("19.03");
    }

    @Test
    void should_extract_the_year_and_the_month_of_the_billing_date_across_a_year_end() {
        facture(CENTRE_A, PATIENT_1, "2025-12-31", "10.00", "0.00", "10.00", null, null, null);
        facture(CENTRE_A, PATIENT_1, "2026-01-01", "20.00", "0.00", "20.00", null, null, null);

        var mois = finance().chiffresParMois(List.of(CENTRE_A.valeur()), LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 31));

        assertThat(mois).extracting(m -> m.annee() * 100 + m.mois()).containsExactlyInAnyOrder(202512, 202601);
    }

    @Test
    void should_count_each_session_once_per_invoice_even_with_several_sessions_of_the_same_patient() {
        UUID f1 = facture(CENTRE_A, PATIENT_1, "2026-02-10", "10.00", "0.00", "10.00", "CNAS", "CNAS Centre", "PERMANENT");
        poserLaSeance(CENTRE_A.valeur(), f1, LocalDate.of(2026, 2, 1), "FACTUREE");
        poserLaSeance(CENTRE_A.valeur(), f1, LocalDate.of(2026, 2, 2), "FACTUREE");
        poserLaSeance(CENTRE_A.valeur(), f1, LocalDate.of(2026, 2, 3), "FACTUREE");

        var synthese = synthese().de(CENTRE_A, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        assertThat(synthese.seances()).isEqualTo(3);
        assertThat(synthese.patients()).isEqualTo(1);
        assertThat(synthese.factures()).isEqualTo(1);
        assertThat(synthese.parCaisse()).singleElement().satisfies(r -> assertThat(r.seances()).isEqualTo(3));
    }
}
