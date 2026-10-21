package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.FacturesEmises;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ReglementsEncaisses;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationDuPatient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des lectures publiées de la Facturation (période facturée d'un patient, factures émises et règlements encaissés
 * fournis à la Comptabilité) : le double en mémoire, le JDBC sur H2 et sur PostgreSQL 16 passent exactement les mêmes tests.
 * Les factures de reprise des soldes d'ouverture ({@code REPRISE-…}) sont exclues de ce qui est fourni à la Comptabilité
 * (KB-FAC-67, décision du 2026-10-21).
 */
public abstract class LecturesPubliquesContrat {

    protected static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000f6c1");
    protected static final UUID AUTRE_CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000f6d2");
    protected static final UUID PATIENT_1 = UUID.fromString("00000000-0000-0000-0000-00000000a611");
    protected static final UUID PATIENT_2 = UUID.fromString("00000000-0000-0000-0000-00000000a612");
    protected static final UUID PAYEUR = UUID.fromString("00000000-0000-0000-0000-00000000a6e1");

    protected abstract FacturationDuPatient facturationDuPatient();

    protected abstract FacturesEmises facturesEmises();

    protected abstract ReglementsEncaisses reglementsEncaisses();

    protected abstract void poserLaFacture(UUID id, UUID centre, UUID patient, String numero, String debut, String fin,
                                           String dateFacturation, String ht, String tva, String ttc, UUID payeur);

    protected abstract void poserLeReglement(UUID id, UUID centre, UUID facture, String montant, String date, String mode, String auteur);

    protected UUID facture(UUID centre, UUID patient, String numero, String debut, String fin, String dateFacturation) {
        UUID id = UUID.randomUUID();
        poserLaFacture(id, centre, patient, numero, debut, fin, dateFacturation, "100.00", "19.00", "119.00", null);
        return id;
    }

    // ───────────────────────────── Période facturée d'un patient ─────────────────────────────

    @Test
    void should_tell_that_the_period_is_billed_on_both_bounds_included() {
        facture(CENTRE, PATIENT_1, "FAC-1", "2026-03-01", "2026-03-31", "2026-03-31");

        assertThat(facturationDuPatient().periodeFacturee(CENTRE, PATIENT_1, LocalDate.of(2026, 3, 1))).isTrue();
        assertThat(facturationDuPatient().periodeFacturee(CENTRE, PATIENT_1, LocalDate.of(2026, 3, 15))).isTrue();
        assertThat(facturationDuPatient().periodeFacturee(CENTRE, PATIENT_1, LocalDate.of(2026, 3, 31))).isTrue();
    }

    @Test
    void should_tell_that_the_period_is_not_billed_the_day_before_and_the_day_after() {
        facture(CENTRE, PATIENT_1, "FAC-2", "2026-03-01", "2026-03-31", "2026-03-31");

        assertThat(facturationDuPatient().periodeFacturee(CENTRE, PATIENT_1, LocalDate.of(2026, 2, 28))).isFalse();
        assertThat(facturationDuPatient().periodeFacturee(CENTRE, PATIENT_1, LocalDate.of(2026, 4, 1))).isFalse();
    }

    @Test
    void should_ignore_the_invoices_of_another_patient_or_another_centre() {
        facture(CENTRE, PATIENT_2, "FAC-3", "2026-03-01", "2026-03-31", "2026-03-31");
        facture(AUTRE_CENTRE, PATIENT_1, "FAC-3", "2026-03-01", "2026-03-31", "2026-03-31");

        assertThat(facturationDuPatient().periodeFacturee(CENTRE, PATIENT_1, LocalDate.of(2026, 3, 15))).isFalse();
    }

    // ───────────────────────────── Factures émises (Comptabilité) ─────────────────────────────

    @Test
    void should_provide_the_invoices_issued_in_the_bounds_in_date_then_number_order_with_their_stored_totals() {
        UUID b = facture(CENTRE, PATIENT_1, "FAC-B", "2026-03-01", "2026-03-31", "2026-04-02");
        poserLaFacture(UUID.randomUUID(), CENTRE, PATIENT_2, "FAC-A", "2026-03-01", "2026-03-31", "2026-04-02", "10.00", "1.90",
                "11.90", PAYEUR);
        facture(CENTRE, PATIENT_1, "FAC-C", "2026-03-01", "2026-03-31", "2026-04-30");
        facture(CENTRE, PATIENT_1, "FAC-HORS", "2026-03-01", "2026-03-31", "2026-05-01");

        var emises = facturesEmises().emisesEntre(CENTRE, LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 30));

        assertThat(emises).extracting(FacturesEmises.FactureEmise::numero).containsExactly("FAC-A", "FAC-B", "FAC-C");
        assertThat(emises.get(0).payeur()).isEqualTo(PAYEUR);
        assertThat(emises.get(0).totalTtc()).isEqualByComparingTo("11.90");
        assertThat(emises.get(1).id()).isEqualTo(b);
        assertThat(emises.get(1).payeur()).isNull();
        assertThat(emises.get(1).finDePeriode()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void should_provide_the_invoices_whose_period_is_included_in_the_bounds() {
        facture(CENTRE, PATIENT_1, "FAC-D", "2026-03-01", "2026-03-31", "2026-04-02");
        facture(CENTRE, PATIENT_1, "FAC-E", "2026-02-15", "2026-03-15", "2026-04-02");
        facture(AUTRE_CENTRE, PATIENT_1, "FAC-F", "2026-03-01", "2026-03-31", "2026-04-02");

        var emises = facturesEmises().dontLaPeriodeEstIncluseDans(CENTRE, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(emises).extracting(FacturesEmises.FactureEmise::numero).containsExactly("FAC-D");
    }

    @Test
    void should_not_provide_the_carried_over_invoices_of_the_opening_balances() {
        facture(CENTRE, PATIENT_1, "REPRISE-F2026-01", "2026-03-01", "2026-03-31", "2026-03-31");
        facture(CENTRE, PATIENT_1, "FAC-G", "2026-03-01", "2026-03-31", "2026-03-31");

        assertThat(facturesEmises().emisesEntre(CENTRE, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .extracting(FacturesEmises.FactureEmise::numero).containsExactly("FAC-G");
        assertThat(facturesEmises().dontLaPeriodeEstIncluseDans(CENTRE, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .extracting(FacturesEmises.FactureEmise::numero).containsExactly("FAC-G");
    }

    // ───────────────────────────── Règlements encaissés (Comptabilité) ─────────────────────────────

    @Test
    void should_provide_the_payments_in_the_bounds_by_date_with_their_signed_amount_and_mode() {
        UUID f = facture(CENTRE, PATIENT_1, "FAC-H", "2026-03-01", "2026-03-31", "2026-03-31");
        poserLeReglement(UUID.randomUUID(), CENTRE, f, "119.00", "2026-04-05", "ESPECES", null);
        poserLeReglement(UUID.randomUUID(), CENTRE, f, "-19.00", "2026-04-02", "VIREMENT", null);
        poserLeReglement(UUID.randomUUID(), CENTRE, f, "5.00", "2026-04-03", null, null);
        poserLeReglement(UUID.randomUUID(), CENTRE, f, "7.00", "2026-05-01", "CHEQUE", null);

        var encaisses = reglementsEncaisses().encaissesEntre(CENTRE, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertThat(encaisses).extracting(r -> r.montant().intValue()).containsExactly(-19, 5, 119);
        assertThat(encaisses).extracting(ReglementsEncaisses.ReglementEncaisse::mode).containsExactly("VIREMENT", null, "ESPECES");
        assertThat(encaisses).extracting(ReglementsEncaisses.ReglementEncaisse::facture).containsOnly(f);
    }

    @Test
    void should_not_provide_the_payments_of_another_centre() {
        UUID f = facture(AUTRE_CENTRE, PATIENT_1, "FAC-I", "2026-03-01", "2026-03-31", "2026-03-31");
        poserLeReglement(UUID.randomUUID(), AUTRE_CENTRE, f, "10.00", "2026-04-05", null, null);

        assertThat(reglementsEncaisses().encaissesEntre(CENTRE, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30))).isEmpty();
    }

    @Test
    void should_not_provide_the_payments_of_the_carried_over_invoices() {
        UUID reprise = facture(CENTRE, PATIENT_1, "REPRISE-F2026-02", "2026-03-01", "2026-03-31", "2026-03-31");
        UUID ordinaire = facture(CENTRE, PATIENT_1, "FAC-J", "2026-03-01", "2026-03-31", "2026-03-31");
        poserLeReglement(UUID.randomUUID(), CENTRE, reprise, "10.00", "2026-04-05", null, "REPRISE");
        poserLeReglement(UUID.randomUUID(), CENTRE, ordinaire, "20.00", "2026-04-05", null, null);

        assertThat(reglementsEncaisses().encaissesEntre(CENTRE, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                .extracting(ReglementsEncaisses.ReglementEncaisse::facture).containsExactly(ordinaire);
    }

    @Test
    void should_still_provide_a_payment_entered_in_the_platform_on_a_carried_over_invoice() {
        UUID reprise = facture(CENTRE, PATIENT_1, "REPRISE-F2026-03", "2026-03-01", "2026-03-31", "2026-03-31");
        poserLeReglement(UUID.randomUUID(), CENTRE, reprise, "10.00", "2026-04-05", null, "caissier");
        poserLeReglement(UUID.randomUUID(), CENTRE, reprise, "11.00", "2026-04-06", null, null);

        assertThat(reglementsEncaisses().encaissesEntre(CENTRE, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                .extracting(r -> r.montant().intValue()).containsExactly(10, 11);
    }
}
