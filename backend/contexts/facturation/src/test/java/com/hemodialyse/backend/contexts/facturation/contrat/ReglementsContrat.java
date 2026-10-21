package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.application.reglement.CriteresDeSituation;
import com.hemodialyse.backend.contexts.facturation.application.reglement.LigneDeSituation;
import com.hemodialyse.backend.contexts.facturation.application.reglement.PageDeSituations;
import com.hemodialyse.backend.contexts.facturation.application.reglement.SituationsDesFactures;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.EtatDeReglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.FactureARegler;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.ModeDeReglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.Reglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.Reglements;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.TypeDeSolde;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat partagé des règlements et de leur lecture : double en mémoire, JDBC sur H2 et PostgreSQL 16. Les environnements posent
 * les factures émises ({@link #poserLaFacture}) ; tout le reste passe par les ports.
 */
public abstract class ReglementsContrat {

    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99995700-0000-0000-0000-0000000000a1"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99995700-0000-0000-0000-0000000000b1"));
    protected static final UUID F1 = UUID.fromString("99995700-0000-0000-0000-0000000f0001");
    protected static final UUID F2 = UUID.fromString("99995700-0000-0000-0000-0000000f0002");
    protected static final UUID F3 = UUID.fromString("99995700-0000-0000-0000-0000000f0003");
    protected static final UUID AUTRE_CENTRE_F = UUID.fromString("99995700-0000-0000-0000-0000000f00b1");

    protected abstract Reglements reglements();

    protected abstract SituationsDesFactures situations();

    /**
     * Pose une facture émise (total TTC figé) dans l'environnement.
     */
    protected abstract void poserLaFacture(CentreId centre, UUID id, String numero, LocalDate dateFacturation, String total);

    protected void poserLesFactures() {
        poserLaFacture(CENTRE_A, F1, "FAC-0001", LocalDate.of(2026, 1, 31), "1000.00");
        poserLaFacture(CENTRE_A, F2, "FAC-0002", LocalDate.of(2026, 1, 31), "500.00");
        poserLaFacture(CENTRE_A, F3, "FAC-0003", LocalDate.of(2026, 2, 28), "200.00");
        poserLaFacture(CENTRE_B, AUTRE_CENTRE_F, "FAC-0001", LocalDate.of(2026, 1, 31), "999.00");
    }

    protected Reglement reglement(UUID facture, String montant, String jour, ModeDeReglement mode, String reference, String cle) {
        UUID id = UUID.randomUUID();
        return new Reglement(id, facture, CENTRE_A, new BigDecimal(montant), LocalDate.parse(jour), "auteur-" + id.toString().substring(0, 4),
                Reglement.codeDe(id), mode, reference, cle);
    }

    @Test
    void should_find_an_invoice_to_pay_only_in_its_own_centre_with_the_signed_total_already_paid() {
        poserLesFactures();
        reglements().enregistrer(reglement(F1, "400.00", "2026-02-01", null, null, null));
        reglements().enregistrer(reglement(F1, "-100.00", "2026-02-02", null, null, null));

        FactureARegler facture = reglements().factureARegler(CENTRE_A, F1).orElseThrow();

        assertThat(facture.id()).isEqualTo(F1);
        assertThat(facture.totalTtc()).isEqualByComparingTo("1000.00");
        assertThat(facture.dejaRegle()).isEqualByComparingTo("300.00");
        assertThat(reglements().factureARegler(CENTRE_B, F1)).isEmpty();
        assertThat(reglements().factureARegler(CENTRE_A, UUID.randomUUID())).isEmpty();
        assertThat(reglements().factureARegler(CENTRE_A, F2).orElseThrow().dejaRegle()).isEqualByComparingTo("0.00");
    }

    @Test
    void should_keep_the_mode_the_reference_the_key_the_author_and_the_code_of_a_payment() {
        poserLesFactures();
        Reglement envoye = reglement(F1, "250.50", "2026-02-01", ModeDeReglement.CHEQUE, "CHQ-77", "envoi-1");
        reglements().enregistrer(envoye);

        Reglement lu = reglements().historique(CENTRE_A, F1).getFirst();

        assertThat(lu.id()).isEqualTo(envoye.id());
        assertThat(lu.facture()).isEqualTo(F1);
        assertThat(lu.centre()).isEqualTo(CENTRE_A);
        assertThat(lu.montant()).isEqualByComparingTo("250.50");
        assertThat(lu.date()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(lu.auteur()).isEqualTo(envoye.auteur());
        assertThat(lu.code()).isEqualTo(envoye.code());
        assertThat(lu.mode()).isEqualTo(ModeDeReglement.CHEQUE);
        assertThat(lu.reference()).isEqualTo("CHQ-77");
        assertThat(lu.cleDIdempotence()).isEqualTo("envoi-1");
    }

    @Test
    void should_keep_a_payment_without_mode_reference_or_key() {
        poserLesFactures();
        reglements().enregistrer(reglement(F1, "10.00", "2026-02-01", null, null, null));

        Reglement lu = reglements().historique(CENTRE_A, F1).getFirst();

        assertThat(lu.mode()).isNull();
        assertThat(lu.reference()).isNull();
        assertThat(lu.cleDIdempotence()).isNull();
    }

    @Test
    void should_give_the_history_newest_date_first_only_for_the_invoice_and_the_centre() {
        poserLesFactures();
        reglements().enregistrer(reglement(F1, "100.00", "2026-02-10", null, null, null));
        reglements().enregistrer(reglement(F1, "200.00", "2026-03-05", null, null, null));
        reglements().enregistrer(reglement(F1, "300.00", "2026-02-20", null, null, null));
        reglements().enregistrer(reglement(F2, "50.00", "2026-03-01", null, null, null));

        assertThat(reglements().historique(CENTRE_A, F1)).extracting(r -> r.montant().toPlainString())
                .containsExactly("200.00", "300.00", "100.00");
        assertThat(reglements().historique(CENTRE_B, F1)).isEmpty();
        assertThat(reglements().historique(CENTRE_A, UUID.randomUUID())).isEmpty();
    }

    @Test
    void should_find_a_payment_by_its_idempotency_key_for_the_same_invoice_and_centre_only() {
        poserLesFactures();
        Reglement envoye = reglement(F1, "100.00", "2026-02-10", null, null, "cle-A");
        reglements().enregistrer(envoye);
        reglements().enregistrer(reglement(F1, "100.00", "2026-02-10", null, null, null));

        assertThat(reglements().parCleDIdempotence(CENTRE_A, F1, "cle-A")).map(Reglement::id).contains(envoye.id());
        assertThat(reglements().parCleDIdempotence(CENTRE_A, F1, "cle-B")).isEmpty();
        assertThat(reglements().parCleDIdempotence(CENTRE_A, F2, "cle-A")).isEmpty();
        assertThat(reglements().parCleDIdempotence(CENTRE_B, F1, "cle-A")).isEmpty();
    }

    @Test
    void should_read_the_situation_of_an_invoice_from_the_signed_total_of_its_payments() {
        poserLesFactures();
        assertThat(situations().deLaFacture(CENTRE_A, F1).orElseThrow().etat()).isEqualTo(EtatDeReglement.NON_REGLEE);

        reglements().enregistrer(reglement(F1, "400.00", "2026-02-01", null, null, null));
        LigneDeSituation partielle = situations().deLaFacture(CENTRE_A, F1).orElseThrow();
        assertThat(partielle.etat()).isEqualTo(EtatDeReglement.PARTIELLEMENT_REGLEE);
        assertThat(partielle.montantFacture()).isEqualByComparingTo("1000.00");
        assertThat(partielle.montantRegle()).isEqualByComparingTo("400.00");
        assertThat(partielle.reste()).isEqualByComparingTo("600.00");
        assertThat(partielle.paymentCount()).isEqualTo(1);
        assertThat(partielle.numeroFacture()).isEqualTo("FAC-0001");
        assertThat(partielle.dateFacturation()).isEqualTo(LocalDate.of(2026, 1, 31));

        reglements().enregistrer(reglement(F1, "700.00", "2026-02-02", null, null, null));
        LigneDeSituation trop = situations().deLaFacture(CENTRE_A, F1).orElseThrow();
        assertThat(trop.etat()).isEqualTo(EtatDeReglement.REGLEE);
        assertThat(trop.soldeType()).isEqualTo(TypeDeSolde.TROP_PERCU);
        assertThat(trop.tropPercu()).isEqualByComparingTo("100.00");
        assertThat(trop.paymentCount()).isEqualTo(2);
        assertThat(trop.latestCodeReglement()).startsWith("REG-");
    }

    @Test
    void should_not_read_the_situation_of_an_unknown_invoice_or_of_another_centre() {
        poserLesFactures();

        assertThat(situations().deLaFacture(CENTRE_B, F1)).isEmpty();
        assertThat(situations().deLaFacture(CENTRE_A, UUID.randomUUID())).isEmpty();
    }

    @Test
    void should_search_the_invoices_of_the_period_newest_date_then_newest_number_first_with_the_centre_isolated() {
        poserLesFactures();
        reglements().enregistrer(reglement(F2, "500.00", "2026-02-01", null, null, null));

        PageDeSituations tout = situations().rechercher(new CriteresDeSituation(CENTRE_A, 2026, null, null, null, null), 0, 20);

        assertThat(tout.items()).extracting(LigneDeSituation::numeroFacture).containsExactly("FAC-0003", "FAC-0002", "FAC-0001");
        assertThat(tout.total()).isEqualTo(3);
        assertThat(tout.items().get(1).soldeType()).isEqualTo(TypeDeSolde.REGLE);
        assertThat(situations().rechercher(new CriteresDeSituation(CENTRE_A, 2026, 1, null, null, null), 0, 20).items())
                .extracting(LigneDeSituation::numeroFacture).containsExactly("FAC-0002", "FAC-0001");
        assertThat(situations().rechercher(new CriteresDeSituation(CENTRE_A, 2027, null, null, null, null), 0, 20).items()).isEmpty();
        assertThat(situations().rechercher(new CriteresDeSituation(CENTRE_B, 2026, null, null, null, null), 0, 20).total()).isEqualTo(1);
    }

    @Test
    void should_paginate_the_search_and_give_all_the_lines_of_the_filter() {
        poserLesFactures();
        CriteresDeSituation annee = new CriteresDeSituation(CENTRE_A, 2026, null, null, null, null);

        PageDeSituations deuxieme = situations().rechercher(annee, 1, 2);

        assertThat(deuxieme.items()).extracting(LigneDeSituation::numeroFacture).containsExactly("FAC-0001");
        assertThat(deuxieme.total()).isEqualTo(3);
        assertThat(deuxieme.page()).isEqualTo(1);
        assertThat(deuxieme.size()).isEqualTo(2);
        assertThat(situations().toutes(annee)).hasSize(3);
        assertThat(situations().rechercher(annee, 5, 2).items()).isEmpty();
    }

    @Test
    void should_include_the_first_and_last_day_of_the_month() {
        poserLaFacture(CENTRE_A, F1, "FAC-0001", LocalDate.of(2026, 2, 1), "10.00");
        poserLaFacture(CENTRE_A, F2, "FAC-0002", LocalDate.of(2026, 2, 28), "10.00");
        poserLaFacture(CENTRE_A, F3, "FAC-0003", LocalDate.of(2026, 3, 1), "10.00");

        assertThat(situations().toutes(new CriteresDeSituation(CENTRE_A, 2026, 2, null, null, null))).hasSize(2);
    }
}
