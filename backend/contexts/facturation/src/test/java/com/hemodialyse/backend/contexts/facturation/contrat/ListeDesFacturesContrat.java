package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.application.consultation.FactureListee;
import com.hemodialyse.backend.contexts.facturation.application.consultation.ListeDesFactures;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LigneFacture;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.EtatDeReglement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de la lecture des factures émises (RG-FAC-046, 047), joué par le double en mémoire et par {@code ListeDesFacturesJdbc} (H2) : période
 * bornes incluses, centre étranger invisible, tri date décroissante puis numéro décroissant, somme SIGNÉE des règlements, somme des quantités
 * figées, lignes triées libellé puis prix.
 */
public abstract class ListeDesFacturesContrat {

    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99995900-0000-0000-0000-0000000000a1"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99995900-0000-0000-0000-0000000000b1"));
    protected static final UUID F1 = UUID.fromString("99995900-0000-0000-0000-0000000f0001");
    protected static final UUID F2 = UUID.fromString("99995900-0000-0000-0000-0000000f0002");
    protected static final UUID F3 = UUID.fromString("99995900-0000-0000-0000-0000000f0003");
    protected static final UUID F_AVANT = UUID.fromString("99995900-0000-0000-0000-0000000f0004");
    protected static final UUID F_APRES = UUID.fromString("99995900-0000-0000-0000-0000000f0005");
    protected static final UUID F_B = UUID.fromString("99995900-0000-0000-0000-0000000f00b1");
    protected static final UUID HD = UUID.fromString("99995900-0000-0000-0000-00000000f0d1");
    protected static final UUID PREMIUM = UUID.fromString("99995900-0000-0000-0000-00000000f0d2");
    protected static final LocalDate DU = LocalDate.of(2026, 10, 1);
    protected static final LocalDate AU = LocalDate.of(2026, 10, 31);

    protected abstract ListeDesFactures liste();

    protected abstract void poserLaFacture(CentreId centre, UUID id, String numero, LocalDate date, String ttc);

    protected abstract void poserLaLigne(CentreId centre, UUID facture, UUID forfait, String libelle, String prix, int nombre);

    protected abstract void poserLeReglement(CentreId centre, UUID facture, String montant);

    protected void poserLesFactures() {
        poserLaFacture(CENTRE_A, F1, "FAC-0001", LocalDate.of(2026, 10, 1), "1000.00");
        poserLaFacture(CENTRE_A, F2, "FAC-0002", LocalDate.of(2026, 10, 1), "2000.00");
        poserLaFacture(CENTRE_A, F3, "FAC-0003", LocalDate.of(2026, 10, 31), "3000.00");
        poserLaFacture(CENTRE_A, F_AVANT, "FAC-0000", LocalDate.of(2026, 9, 30), "10.00");
        poserLaFacture(CENTRE_A, F_APRES, "FAC-0009", LocalDate.of(2026, 11, 1), "10.00");
        poserLaFacture(CENTRE_B, F_B, "FAC-B001", LocalDate.of(2026, 10, 15), "10.00");
    }

    @Test
    void should_read_the_invoices_of_the_centre_within_the_period_by_date_then_number_descending() {
        poserLesFactures();

        assertThat(liste().entre(CENTRE_A, DU, AU)).extracting(FactureListee::factureId).containsExactly(F3, F2, F1);
        assertThat(liste().entre(CENTRE_B, DU, AU)).extracting(FactureListee::factureId).containsExactly(F_B);
        assertThat(liste().entre(CENTRE_A, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31))).isEmpty();
    }

    @Test
    void should_sum_the_signed_settlements_and_the_frozen_quantities_and_evaluate_the_state() {
        poserLesFactures();
        poserLeReglement(CENTRE_A, F2, "1500.00");
        poserLeReglement(CENTRE_A, F2, "-500.00");
        poserLeReglement(CENTRE_A, F1, "1000.00");
        poserLeReglement(CENTRE_B, F3, "3000.00");
        poserLaLigne(CENTRE_A, F2, HD, "Hemodialyse standard", "100.00", 12);
        poserLaLigne(CENTRE_A, F2, PREMIUM, "HD Premium", "200.00", 4);
        poserLaLigne(CENTRE_B, F2, HD, "Hemodialyse standard", "100.00", 50);

        FactureListee f2 = liste().deLaFacture(CENTRE_A, F2).orElseThrow();
        FactureListee f3 = liste().deLaFacture(CENTRE_A, F3).orElseThrow();
        FactureListee f1 = liste().deLaFacture(CENTRE_A, F1).orElseThrow();

        assertThat(f2.montantRegle()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(f2.reste()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(f2.etat()).isEqualTo(EtatDeReglement.PARTIELLEMENT_REGLEE);
        assertThat(f2.nombreDeSeances()).isEqualTo(16);
        assertThat(f2.numeroFacture()).isEqualTo("FAC-0002");
        assertThat(f2.dateFacturation()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(f2.montantTtc()).isEqualTo(new BigDecimal("2000.00"));
        assertThat(f3.montantRegle()).isEqualTo(new BigDecimal("0.00"));
        assertThat(f3.etat()).isEqualTo(EtatDeReglement.NON_REGLEE);
        assertThat(f3.nombreDeSeances()).isZero();
        assertThat(f1.etat()).isEqualTo(EtatDeReglement.REGLEE);
        assertThat(f1.reste()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void should_not_see_an_invoice_of_another_centre_nor_its_lines() {
        poserLesFactures();
        poserLaLigne(CENTRE_B, F_B, HD, "Hemodialyse standard", "100.00", 1);

        assertThat(liste().deLaFacture(CENTRE_A, F_B)).isEmpty();
        assertThat(liste().deLaFacture(CENTRE_A, UUID.randomUUID())).isEmpty();
        assertThat(liste().lignes(CENTRE_A, F_B)).isEmpty();
        assertThat(liste().lignes(CENTRE_B, F_B)).hasSize(1);
    }

    @Test
    void should_read_the_frozen_lines_by_label_then_unit_price() {
        poserLesFactures();
        poserLaLigne(CENTRE_A, F1, HD, "Hemodialyse standard", "8000.00", 2);
        poserLaLigne(CENTRE_A, F1, PREMIUM, "Hemodialyse premium", "9500.00", 1);
        poserLaLigne(CENTRE_A, F1, HD, "Hemodialyse standard", "7500.00", 3);

        assertThat(liste().lignes(CENTRE_A, F1)).containsExactly(
                new LigneFacture(PREMIUM, "Hemodialyse premium", new BigDecimal("9500.00"), 1),
                new LigneFacture(HD, "Hemodialyse standard", new BigDecimal("7500.00"), 3),
                new LigneFacture(HD, "Hemodialyse standard", new BigDecimal("8000.00"), 2));
    }
}
