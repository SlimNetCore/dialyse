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
 * Contrat de la reprise des soldes sur un PostgreSQL 16 RÉEL (schéma Flyway V1..V41) : mêmes tests que {@link RepriseDesSoldesContrat}, plus les propriétés propres à PostgreSQL (arrondi NUMERIC(14,2), identifiants en minuscules).
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
class RepriseDesSoldesPostgresContractIT extends RepriseDesSoldesJdbcContrat {

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
    void should_round_the_carried_over_amounts_to_the_numeric_14_2_scale() {
        java.util.UUID facture = reprise().ouvrir(CENTRE, solde("REPRISE-PG1", "10.005", "1.004"));

        assertThat(totalTtc(facture)).hasToString("10.01");
        assertThat(jdbc.queryForObject("SELECT montant FROM facture_reglements WHERE facture_id = ?", java.math.BigDecimal.class,
                facture)).hasToString("1.00");
    }

    /**
     * L8 (revue L3, m1) : la clé étrangère V54 ({@code seances.facture_id}) interdit de supprimer une facture encore liée à une séance ; la
     * suppression d'une facture de REPRISE (aucune séance liée) reste possible, y compris quand une autre facture du même patient est liée à une séance.
     */
    @Test
    void should_still_delete_a_carried_over_invoice_when_another_invoice_is_linked_to_a_session_under_the_v54_foreign_key() {
        java.util.UUID reprise = reprise().ouvrir(CENTRE, solde("REPRISE-PG3", "10.00", "2.00"));
        java.util.UUID liee = java.util.UUID.randomUUID();
        java.util.UUID seance = java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, period_start, period_end, date_facturation, tva_rate, "
                        + "total_ht, total_tva, total_ttc) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0)", liee, CENTRE, PATIENT, "F-PG3-" + liee,
                java.sql.Date.valueOf("2026-08-01"), java.sql.Date.valueOf("2026-08-31"), java.sql.Date.valueOf("2026-09-01"));
        try {
            jdbc.update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, facture_id) VALUES (?, ?, ?, ?, 'FACTUREE', ?)",
                    seance, CENTRE, PATIENT, java.sql.Date.valueOf("2026-08-02"), liee);

            reprise().supprimer(CENTRE, reprise);

            assertThat(reprise().factureDuNumero(CENTRE, "REPRISE-PG3")).isEmpty();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM factures WHERE id = ?", Integer.class, liee)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM seances WHERE id = ?", seance);
            jdbc.update("DELETE FROM factures WHERE id = ?", liee);
        }
    }

    @Test
    void should_return_the_identifiers_of_the_patient_invoices_in_lowercase_whatever_the_uuid_text_form() {
        java.util.UUID facture = reprise().ouvrir(CENTRE, solde("REPRISE-PG2", "10.00", null));

        assertThat(reprise().facturesDuPatient(PATIENT)).containsExactly(facture.toString());
        assertThat(reprise().facturesDuPatient(PATIENT).get(0)).isEqualTo(reprise().facturesDuPatient(PATIENT).get(0).toLowerCase());
    }
}
