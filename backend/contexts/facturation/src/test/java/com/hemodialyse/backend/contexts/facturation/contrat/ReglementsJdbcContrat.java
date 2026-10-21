package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.application.reglement.SituationsDesFactures;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.ModeDeReglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.Reglements;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.ReglementsJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.SituationsDesFacturesJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le contrat des règlements exécuté contre les adaptateurs JDBC, plus la garantie de la base : l'unicité de la clé d'idempotence
 * par (centre, facture), qui laisse libres les règlements sans clé. Les variantes H2 et PostgreSQL 16 ne diffèrent que par leur
 * base.
 */
public abstract class ReglementsJdbcContrat extends ReglementsContrat {

    protected abstract JdbcTemplate jdbc();

    protected void viderLesLignesDeTest() {
        for (String table : new String[]{"facture_reglements", "factures"}) {
            jdbc().update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE_A.valeur(), CENTRE_B.valeur());
        }
    }

    @Override
    protected Reglements reglements() {
        return new ReglementsJdbc(jdbc());
    }

    @Override
    protected SituationsDesFactures situations() {
        return new SituationsDesFacturesJdbc(jdbc());
    }

    @Override
    protected void poserLaFacture(CentreId centre, UUID id, String numero, LocalDate dateFacturation, String total) {
        BigDecimal ttc = new BigDecimal(total);
        jdbc().update("INSERT INTO factures (id, center_id, patient_id, numero_facture, period_start, period_end, date_facturation, "
                        + "tva_rate, total_ht, total_tva, total_ttc) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, 0, ?)",
                id, centre.valeur(), UUID.randomUUID(), numero, Date.valueOf(dateFacturation), Date.valueOf(dateFacturation),
                Date.valueOf(dateFacturation), ttc, ttc);
    }

    @Test
    void should_refuse_a_second_payment_under_the_same_key_for_the_same_invoice() {
        poserLesFactures();
        reglements().enregistrer(reglement(F1, "100.00", "2026-02-10", null, null, "cle-A"));

        assertThatThrownBy(() -> reglements().enregistrer(reglement(F1, "100.00", "2026-02-10", null, null, "cle-A")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void should_allow_the_same_key_on_another_invoice_and_unlimited_payments_without_key() {
        poserLesFactures();
        reglements().enregistrer(reglement(F1, "100.00", "2026-02-10", ModeDeReglement.CAISSE, null, "cle-A"));
        reglements().enregistrer(reglement(F2, "100.00", "2026-02-10", null, null, "cle-A"));
        reglements().enregistrer(reglement(F1, "100.00", "2026-02-10", null, null, null));
        reglements().enregistrer(reglement(F1, "100.00", "2026-02-10", null, null, null));

        assertThat(reglements().historique(CENTRE_A, F1)).hasSize(3);
    }

    @Test
    void should_store_the_mode_as_its_name_and_leave_the_new_columns_empty_for_a_payment_without_them() {
        poserLesFactures();
        reglements().enregistrer(reglement(F1, "100.00", "2026-02-10", ModeDeReglement.ESPECES, "RECU-1", null));
        reglements().enregistrer(reglement(F2, "100.00", "2026-02-10", null, null, null));

        assertThat(jdbc().queryForObject("SELECT mode_reglement FROM facture_reglements WHERE facture_id = ?", String.class, F1))
                .isEqualTo("ESPECES");
        assertThat(jdbc().queryForObject("SELECT reference_paiement FROM facture_reglements WHERE facture_id = ?", String.class, F1))
                .isEqualTo("RECU-1");
        assertThat(jdbc().queryForObject("SELECT mode_reglement FROM facture_reglements WHERE facture_id = ?", String.class, F2))
                .isNull();
    }

    @Test
    void should_read_a_payment_inserted_without_the_new_columns_like_the_reprise_of_data_does() {
        poserLesFactures();
        jdbc().update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement, saisi_par, code_reglement) "
                + "VALUES (?, ?, ?, 120.00, ?, 'MIGRATION', 'REP-1')", UUID.randomUUID(), F1, CENTRE_A.valeur(), Date.valueOf("2026-02-01"));

        var lu = reglements().historique(CENTRE_A, F1).getFirst();

        assertThat(lu.code()).isEqualTo("REP-1");
        assertThat(lu.auteur()).isEqualTo("MIGRATION");
        assertThat(lu.mode()).isNull();
        assertThat(reglements().factureARegler(CENTRE_A, F1).orElseThrow().dejaRegle()).isEqualByComparingTo("120.00");
    }
}
