package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.RepriseDesSoldes;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.RepriseDesSoldes.SoldeDOuverture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de la reprise des soldes d'ouverture (langage publié {@link RepriseDesSoldes}) : le double en mémoire, le JDBC sur H2
 * et sur PostgreSQL 16 passent exactement les mêmes tests.
 */
public abstract class RepriseDesSoldesContrat {

    protected static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000f6a1");
    protected static final UUID AUTRE_CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000f6b2");
    protected static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-00000000a601");

    protected abstract RepriseDesSoldes reprise();

    /** Un règlement saisi dans la plateforme (hors reprise) sur la facture. */
    protected abstract void poserUnReglementDeLaPlateforme(UUID centre, UUID facture, String montant);

    protected abstract int lignes(UUID facture);

    protected abstract int reglementsDeReprise(UUID facture);

    protected abstract BigDecimal totalTtc(UUID facture);

    protected static SoldeDOuverture solde(String numero, String ttc, String regle) {
        return new SoldeDOuverture(PATIENT, numero, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31), new BigDecimal(ttc), regle == null ? null : new BigDecimal(regle),
                LocalDate.of(2026, 2, 10), "Solde d'ouverture - reprise", "P-0001", "Nom Prenom", "PERMANENT", "123456", null);
    }

    @Test
    void should_open_a_carried_over_invoice_with_one_line_and_its_carried_over_payment() {
        UUID facture = reprise().ouvrir(CENTRE, solde("REPRISE-F1", "1000.00", "400.00"));

        assertThat(reprise().factureDuNumero(CENTRE, "REPRISE-F1")).contains(facture);
        assertThat(lignes(facture)).isEqualTo(1);
        assertThat(reglementsDeReprise(facture)).isEqualTo(1);
        assertThat(totalTtc(facture)).isEqualByComparingTo("1000.00");
        assertThat(reprise().reglementsHorsReprise(CENTRE, facture)).isZero();
    }

    @Test
    void should_open_without_payment_when_nothing_was_paid() {
        UUID sans = reprise().ouvrir(CENTRE, solde("REPRISE-F2", "500.00", null));
        UUID zero = reprise().ouvrir(CENTRE, solde("REPRISE-F3", "500.00", "0.00"));

        assertThat(reglementsDeReprise(sans)).isZero();
        assertThat(reglementsDeReprise(zero)).isZero();
    }

    @Test
    void should_isolate_the_invoice_numbers_by_centre() {
        reprise().ouvrir(CENTRE, solde("REPRISE-F4", "500.00", null));

        assertThat(reprise().factureDuNumero(AUTRE_CENTRE, "REPRISE-F4")).isEmpty();
        assertThat(reprise().factureDuNumero(CENTRE, "REPRISE-INCONNUE")).isEmpty();
    }

    @Test
    void should_replace_amounts_line_and_carried_over_payment_in_place() {
        UUID facture = reprise().ouvrir(CENTRE, solde("REPRISE-F5", "1000.00", "400.00"));

        reprise().remplacer(CENTRE, facture, solde("REPRISE-F5", "1500.00", "100.00"));

        assertThat(reprise().factureDuNumero(CENTRE, "REPRISE-F5")).contains(facture);
        assertThat(totalTtc(facture)).isEqualByComparingTo("1500.00");
        assertThat(lignes(facture)).isEqualTo(1);
        assertThat(reglementsDeReprise(facture)).isEqualTo(1);
    }

    @Test
    void should_count_only_the_payments_entered_in_the_platform() {
        UUID facture = reprise().ouvrir(CENTRE, solde("REPRISE-F6", "1000.00", "400.00"));
        poserUnReglementDeLaPlateforme(CENTRE, facture, "100.00");
        poserUnReglementDeLaPlateforme(CENTRE, facture, "50.00");

        assertThat(reprise().reglementsHorsReprise(CENTRE, facture)).isEqualTo(2);
        assertThat(reprise().reglementsHorsReprise(AUTRE_CENTRE, facture)).isZero();
    }

    @Test
    void should_delete_the_invoice_its_lines_and_its_payments() {
        UUID facture = reprise().ouvrir(CENTRE, solde("REPRISE-F7", "1000.00", "400.00"));

        reprise().supprimer(CENTRE, facture);

        assertThat(reprise().factureDuNumero(CENTRE, "REPRISE-F7")).isEmpty();
        assertThat(lignes(facture)).isZero();
        assertThat(reglementsDeReprise(facture)).isZero();
    }

    @Test
    void should_not_delete_an_invoice_of_another_centre() {
        UUID facture = reprise().ouvrir(CENTRE, solde("REPRISE-F8", "1000.00", null));

        reprise().supprimer(AUTRE_CENTRE, facture);

        assertThat(reprise().factureDuNumero(CENTRE, "REPRISE-F8")).contains(facture);
    }

    @Test
    void should_list_the_invoices_of_a_patient_with_lowercase_identifiers() {
        UUID f1 = reprise().ouvrir(CENTRE, solde("REPRISE-F9", "10.00", null));
        UUID f2 = reprise().ouvrir(CENTRE, solde("REPRISE-F10", "20.00", null));

        assertThat(reprise().facturesDuPatient(PATIENT)).containsExactlyInAnyOrder(f1.toString().toLowerCase(),
                f2.toString().toLowerCase());
        assertThat(reprise().facturesDuPatient(UUID.randomUUID())).isEmpty();
    }
}
