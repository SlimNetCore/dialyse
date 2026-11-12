package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.application.reglement.CriteresDeSituation;
import com.hemodialyse.backend.contexts.facturation.application.reglement.PageDeSituations;
import com.hemodialyse.backend.contexts.facturation.application.reglement.SituationsDesFactures;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
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
        return new SituationsDesFacturesJdbc(jdbc(),
                new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.PayeursDuCentreJdbc(jdbc()));
    }

    @Override
    protected void poserLaFacture(CentreId centre, UUID id, String numero, LocalDate dateFacturation, String total) {
        BigDecimal ttc = new BigDecimal(total);
        jdbc().update("INSERT INTO factures (id, center_id, patient_id, numero_facture, period_start, period_end, date_facturation, "
                        + "tva_rate, total_ht, total_tva, total_ttc) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, 0, ?)",
                id, centre.valeur(), UUID.randomUUID(), numero, Date.valueOf(dateFacturation), Date.valueOf(dateFacturation),
                Date.valueOf(dateFacturation), ttc, ttc);
    }

    // ---- PL8b : la chaîne des payeurs d'une facture est résolue en mémoire par l'annuaire (instantané figé de la facture)

    private UUID caisse(com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId centre, String code) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO caisse_assurance (id, center_id, code, nom) VALUES (?, ?, ?, ?)", id, centre.valeur(), code, "Caisse " + code);
        return id;
    }

    private UUID agence(com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId centre, UUID caisse, String code) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, ?, ?)", id, centre.valeur(), caisse, code,
                "Agence " + code);
        return id;
    }

    private UUID centrePayeur(com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId centre, UUID agence, String code) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, ?, ?)", id, centre.valeur(), agence,
                code, "Centre " + code);
        return id;
    }

    private void figer(UUID facture, UUID centrePayeur, UUID agence) {
        jdbc().update("UPDATE factures SET centre_payeur_id_snapshot = ?, agence_id_snapshot = ? WHERE id = ?", centrePayeur, agence, facture);
    }

    private void viderLeReferentielDesPayeurs() {
        for (String table : new String[]{"centre_payeur", "agence", "caisse_assurance"}) {
            jdbc().update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE_A.valeur(), CENTRE_B.valeur());
        }
    }

    @org.junit.jupiter.api.AfterEach
    void nettoyerLeReferentielDesPayeurs() {
        viderLeReferentielDesPayeurs();
    }

    @Test
    void should_resolve_the_payer_chain_of_the_frozen_snapshot_filter_on_it_and_page_after_the_filter_in_the_sort_order() {
        viderLeReferentielDesPayeurs();
        poserLesFactures();
        UUID cnas = caisse(CENTRE_A, "CNAS");
        UUID casnos = caisse(CENTRE_A, "CASNOS");
        UUID agCnas = agence(CENTRE_A, cnas, "AG-CNAS");
        UUID agCasnos = agence(CENTRE_A, casnos, "AG-CASNOS");
        UUID cp1 = centrePayeur(CENTRE_A, agCnas, "CP1");
        UUID cp2 = centrePayeur(CENTRE_A, agCasnos, "CP2");
        figer(F1, cp1, null);
        figer(F2, cp2, null);
        figer(F3, cp1, agCasnos);
        var criteres = new CriteresDeSituation(CENTRE_A, 2026, null, null, null, null);

        var toutes = situations().toutes(criteres);

        // tri date de facturation décroissante puis numéro décroissant : F3 (28/02), F2 (31/01, 0002), F1 (31/01, 0001)
        assertThat(toutes).extracting(l -> l.factureId()).containsExactly(F3, F2, F1);
        assertThat(toutes).extracting(l -> l.caisse()).containsExactly("Caisse CASNOS", "Caisse CASNOS", "Caisse CNAS");
        assertThat(toutes).extracting(l -> l.agence()).containsExactly("Agence AG-CASNOS", "Agence AG-CASNOS", "Agence AG-CNAS");
        assertThat(toutes).extracting(l -> l.centrePayeur()).containsExactly("Centre CP1", "Centre CP2", "Centre CP1");
        assertThat(situations().toutes(new CriteresDeSituation(CENTRE_A, 2026, null, cnas, null, null))).extracting(l -> l.factureId())
                .containsExactly(F1);
        assertThat(situations().toutes(new CriteresDeSituation(CENTRE_A, 2026, null, null, agCasnos, null))).extracting(l -> l.factureId())
                .containsExactly(F3, F2);
        assertThat(situations().toutes(new CriteresDeSituation(CENTRE_A, 2026, null, null, null, cp1))).extracting(l -> l.factureId())
                .containsExactly(F3, F1);
        PageDeSituations page = situations().rechercher(new CriteresDeSituation(CENTRE_A, 2026, null, null, null, cp1), 1, 1);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(l -> l.factureId()).containsExactly(F1);
        assertThat(situations().rechercher(criteres, 0, 2).items()).extracting(l -> l.factureId()).containsExactly(F3, F2);
        assertThat(situations().rechercher(criteres, 5, 2).items()).isEmpty();
        assertThat(situations().deLaFacture(CENTRE_A, F3).orElseThrow().caisseId()).isEqualTo(casnos);
    }

    @Test
    void should_break_the_payer_chain_at_a_stage_that_belongs_to_another_centre_and_label_it_not_filled_in() {
        viderLeReferentielDesPayeurs();
        poserLesFactures();
        UUID caisseEtrangere = caisse(CENTRE_B, "CNAS");
        UUID agenceEtrangere = agence(CENTRE_B, caisseEtrangere, "AG-B");
        UUID cpEtranger = centrePayeur(CENTRE_B, agenceEtrangere, "CP-B");
        UUID agenceVersLEtrangere = agence(CENTRE_A, caisseEtrangere, "AG-A");
        figer(F1, cpEtranger, agenceEtrangere);
        figer(F2, null, agenceVersLEtrangere);
        figer(F3, null, null);

        var lignes = situations().toutes(new CriteresDeSituation(CENTRE_A, 2026, null, null, null, null));

        assertThat(lignes).hasSize(3).allSatisfy(l -> {
            assertThat(l.caisseId()).isNull();
            assertThat(l.caisse()).isEqualTo("Non renseignee");
            assertThat(l.centrePayeurId()).isNull();
            assertThat(l.centrePayeur()).isEqualTo("Non renseigne");
        });
        assertThat(lignes).filteredOn(l -> l.factureId().equals(F2)).singleElement().satisfies(l -> {
            assertThat(l.agenceId()).isEqualTo(agenceVersLEtrangere);
            assertThat(l.agence()).isEqualTo("Agence AG-A");
        });
        assertThat(lignes).filteredOn(l -> l.factureId().equals(F1)).singleElement().satisfies(l -> assertThat(l.agenceId()).isNull());    }

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
