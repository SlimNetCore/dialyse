package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDuTableauDeBord;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.TableauDeBordDuMois;
import com.hemodialyse.backend.contexts.facturation.doubles.PilotageEnMemoire.Facture;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres.ChiffresDuCentre;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des trois lectures de pilotage (tableau de bord, synthèse, synthèse financière publiée) : le double en mémoire, la
 * lecture JDBC sur H2 et sur PostgreSQL 16 passent exactement les mêmes tests. Les montants se comparent par valeur (l'échelle
 * dépend de la base).
 */
public abstract class PilotageContrat {

    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000f5a1"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000f5b2"));
    protected static final UUID PATIENT_1 = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    protected static final UUID PATIENT_2 = UUID.fromString("00000000-0000-0000-0000-00000000a002");
    protected static final UUID PATIENT_3 = UUID.fromString("00000000-0000-0000-0000-00000000a003");

    protected abstract LectureDuTableauDeBord tableau();

    protected abstract LectureDeLaSynthese synthese();

    protected abstract SyntheseFinanciereDesCentres finance();

    protected abstract void poserLaFacture(Facture facture);

    protected abstract void poserLaSeance(UUID centre, UUID facture, LocalDate date, String statut);

    protected abstract void poserLeReglement(UUID centre, UUID facture, String montant);

    protected UUID facture(CentreId centre, UUID patient, String date, String ht, String tva, String ttc, String codeCaisse,
                           String nomCaisse, String situation) {
        UUID id = UUID.randomUUID();
        poserLaFacture(new Facture(id, centre.valeur(), patient, LocalDate.parse(date), new BigDecimal(ht), new BigDecimal(tva),
                new BigDecimal(ttc), codeCaisse, nomCaisse, situation));
        return id;
    }

    // ───────────────────────────── Tableau de bord ─────────────────────────────

    @Test
    void should_sum_the_stored_totals_of_the_invoices_issued_in_the_month_bounds_included() {
        facture(CENTRE_A, PATIENT_1, "2026-02-01", "100.00", "19.00", "119.00", "CNAS", "CNAS Centre", "PERMANENT");
        facture(CENTRE_A, PATIENT_2, "2026-02-28", "200.00", "1.00", "555.55", "CNAS", "CNAS Centre", "PERMANENT");
        facture(CENTRE_A, PATIENT_1, "2026-01-31", "5000.00", "0.00", "5000.00", "CNAS", "CNAS Centre", "PERMANENT");
        facture(CENTRE_A, PATIENT_1, "2026-03-01", "6000.00", "0.00", "6000.00", "CNAS", "CNAS Centre", "PERMANENT");
        facture(CENTRE_B, PATIENT_3, "2026-02-10", "9999.00", "0.00", "9999.00", "CNAS", "CNAS Centre", "PERMANENT");

        TableauDeBordDuMois fevrier = tableau().du(CENTRE_A, YearMonth.of(2026, 2));

        assertThat(fevrier.chiffreDAffairesHt()).isEqualByComparingTo("300.00");
        assertThat(fevrier.chiffreDAffairesTtc()).isEqualByComparingTo("674.55");
        assertThat(fevrier.facturesEmises()).isEqualTo(2);
        assertThat(fevrier.patientsFactures()).isEqualTo(2);
        assertThat(fevrier.centre()).isEqualTo(CENTRE_A.valeur());
    }

    @Test
    void should_return_zeros_and_no_distribution_for_a_month_without_invoice() {
        TableauDeBordDuMois vide = tableau().du(CENTRE_A, YearMonth.of(2026, 5));

        assertThat(vide.chiffreDAffairesHt()).isEqualByComparingTo("0");
        assertThat(vide.chiffreDAffairesTtc()).isEqualByComparingTo("0");
        assertThat(vide.seancesFacturees()).isZero();
        assertThat(vide.facturesEmises()).isZero();
        assertThat(vide.parCaisse()).isEmpty();
        assertThat(vide.parSituation()).isEmpty();
    }

    @Test
    void should_count_billed_sessions_by_session_date_but_distributions_by_invoice() {
        UUID f1 = facture(CENTRE_A, PATIENT_1, "2026-02-10", "100.00", "0.00", "100.00", "CNAS", "CNAS Centre", "PERMANENT");
        poserLaSeance(CENTRE_A.valeur(), f1, LocalDate.of(2026, 2, 3), "FACTUREE");
        poserLaSeance(CENTRE_A.valeur(), f1, LocalDate.of(2026, 1, 30), "FACTUREE");
        poserLaSeance(CENTRE_A.valeur(), null, LocalDate.of(2026, 2, 4), "FACTUREE");
        poserLaSeance(CENTRE_A.valeur(), null, LocalDate.of(2026, 2, 5), "VALIDEE");
        poserLaSeance(CENTRE_B.valeur(), null, LocalDate.of(2026, 2, 6), "FACTUREE");

        TableauDeBordDuMois fevrier = tableau().du(CENTRE_A, YearMonth.of(2026, 2));

        assertThat(fevrier.seancesFacturees()).isEqualTo(2);
        assertThat(fevrier.parCaisse()).singleElement().satisfies(c -> {
            assertThat(c.seances()).isEqualTo(2);
            assertThat(c.patients()).isEqualTo(1);
        });
        assertThat(fevrier.parSituation()).singleElement().satisfies(s -> assertThat(s.seances()).isEqualTo(2));
    }

    @Test
    void should_rank_funds_by_patients_then_code_and_situations_by_sessions_then_code() {
        facture(CENTRE_A, PATIENT_1, "2026-02-10", "10.00", "0.00", "10.00", "ZZZ", "Zeta", "VACANCIER");
        facture(CENTRE_A, PATIENT_2, "2026-02-11", "10.00", "0.00", "10.00", "ZZZ", "Zeta", "VACANCIER");
        facture(CENTRE_A, PATIENT_3, "2026-02-12", "10.00", "0.00", "10.00", "AAA", "Alpha", "PERMANENT");
        facture(CENTRE_A, PATIENT_3, "2026-02-13", "10.00", "0.00", "10.00", "BBB", "Beta", "PERMANENT");

        TableauDeBordDuMois fevrier = tableau().du(CENTRE_A, YearMonth.of(2026, 2));

        assertThat(fevrier.parCaisse()).extracting(TableauDeBordDuMois.RepartitionParCaisse::code)
                .containsExactly("ZZZ", "AAA", "BBB");
        assertThat(fevrier.parSituation()).extracting(TableauDeBordDuMois.RepartitionParSituation::code)
                .containsExactly("PERMANENT", "VACANCIER");
    }

    @Test
    void should_label_an_invoice_without_fund_or_situation_as_unknown() {
        facture(CENTRE_A, PATIENT_1, "2026-02-10", "10.00", "0.00", "10.00", null, null, null);

        TableauDeBordDuMois fevrier = tableau().du(CENTRE_A, YearMonth.of(2026, 2));

        assertThat(fevrier.parCaisse()).singleElement().satisfies(c -> {
            assertThat(c.code()).isEqualTo("INCONNU");
            assertThat(c.libelle()).isEqualTo("Inconnu");
        });
        assertThat(fevrier.parSituation()).singleElement().satisfies(s -> {
            assertThat(s.code()).isEqualTo("INCONNU");
            assertThat(s.libelle()).isEqualTo("Inconnu");
        });
    }

    // ───────────────────────────── Synthèse ─────────────────────────────

    @Test
    void should_give_the_stored_totals_and_the_indicators_of_the_period() {
        UUID f1 = facture(CENTRE_A, PATIENT_1, "2026-02-01", "1000.00", "190.00", "1190.00", "CNAS", "CNAS Centre", "PERMANENT");
        facture(CENTRE_A, PATIENT_2, "2026-02-28", "77.00", "0.00", "77.00", null, null, null);
        facture(CENTRE_A, PATIENT_1, "2026-03-01", "6000.00", "0.00", "6000.00", "CNAS", "CNAS Centre", "PERMANENT");
        poserLaSeance(CENTRE_A.valeur(), f1, LocalDate.of(2026, 2, 1), "FACTUREE");
        poserLaSeance(CENTRE_A.valeur(), f1, LocalDate.of(2026, 2, 2), "FACTUREE");

        SyntheseMensuelle synthese = synthese().de(CENTRE_A, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        assertThat(synthese.totalHt()).isEqualByComparingTo("1077.00");
        assertThat(synthese.totalTva()).isEqualByComparingTo("190.00");
        assertThat(synthese.totalTtc()).isEqualByComparingTo("1267.00");
        assertThat(synthese.factures()).isEqualTo(2);
        assertThat(synthese.patients()).isEqualTo(2);
        assertThat(synthese.seances()).isEqualTo(2);
    }

    @Test
    void should_order_fund_columns_by_label_and_trim_blank_codes_and_situations_into_unknown() {
        facture(CENTRE_A, PATIENT_1, "2026-02-10", "10.00", "0.00", "10.00", "CNAS", "CNAS Centre", "PERMANENT");
        facture(CENTRE_A, PATIENT_2, "2026-02-11", "10.00", "0.00", "10.00", "CASNOS", "Casnos Centre", "  ");
        facture(CENTRE_A, PATIENT_3, "2026-02-12", "10.00", "0.00", "10.00", null, null, null);

        SyntheseMensuelle synthese = synthese().de(CENTRE_A, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        assertThat(synthese.caisses()).extracting(SyntheseMensuelle.Caisse::libelle)
                .containsExactly("CNAS Centre", "Casnos Centre", "Inconnu");
        assertThat(synthese.parSituation()).extracting(SyntheseMensuelle.Repartition::code)
                .containsExactlyInAnyOrder("PERMANENT", "INCONNU");
        assertThat(synthese.parSituation()).filteredOn(r -> r.code().equals("INCONNU")).singleElement()
                .satisfies(r -> assertThat(r.patients()).isEqualTo(2));
    }

    @Test
    void should_return_zero_totals_and_a_single_unknown_column_for_a_period_without_invoice() {
        SyntheseMensuelle vide = synthese().de(CENTRE_A, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertThat(vide.totalHt()).isEqualByComparingTo("0");
        assertThat(vide.totalTva()).isEqualByComparingTo("0");
        assertThat(vide.totalTtc()).isEqualByComparingTo("0");
        assertThat(vide.factures()).isZero();
        assertThat(vide.parCaisse()).isEmpty();
        assertThat(vide.colonnes()).singleElement().satisfies(c -> assertThat(c.code()).isEqualTo("INCONNU"));
    }

    // ───────────────────────────── Synthèse financière publiée ─────────────────────────────

    @Test
    void should_give_per_centre_invoices_turnover_and_collected_amount_with_late_payments_and_refunds() {
        UUID f1 = facture(CENTRE_A, PATIENT_1, "2026-02-10", "100.00", "19.00", "119.00", "CNAS", "CNAS Centre", "PERMANENT");
        UUID f2 = facture(CENTRE_A, PATIENT_2, "2026-02-11", "200.00", "38.00", "238.00", "CNAS", "CNAS Centre", "PERMANENT");
        facture(CENTRE_A, PATIENT_2, "2026-03-11", "9000.00", "0.00", "9000.00", "CNAS", "CNAS Centre", "PERMANENT");
        poserLeReglement(CENTRE_A.valeur(), f1, "119.00");
        poserLeReglement(CENTRE_A.valeur(), f2, "300.00");
        poserLeReglement(CENTRE_A.valeur(), f2, "-50.00");
        facture(CENTRE_B, PATIENT_3, "2026-02-12", "10.00", "0.00", "10.00", "CNAS", "CNAS Centre", "PERMANENT");

        Map<UUID, ChiffresDuCentre> chiffres = finance().chiffresParCentre(List.of(CENTRE_A.valeur(), CENTRE_B.valeur()),
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        assertThat(chiffres).containsOnlyKeys(CENTRE_A.valeur(), CENTRE_B.valeur());
        ChiffresDuCentre a = chiffres.get(CENTRE_A.valeur());
        assertThat(a.factures()).isEqualTo(2);
        assertThat(a.chiffreDAffairesHt()).isEqualByComparingTo("300.00");
        assertThat(a.chiffreDAffairesTtc()).isEqualByComparingTo("357.00");
        assertThat(a.encaisse()).isEqualByComparingTo("369.00");
        assertThat(chiffres.get(CENTRE_B.valeur()).encaisse()).isEqualByComparingTo("0");
    }

    @Test
    void should_leave_out_a_centre_without_invoice_in_the_period_and_accept_an_empty_list() {
        facture(CENTRE_A, PATIENT_1, "2026-02-10", "100.00", "0.00", "100.00", null, null, null);

        assertThat(finance().chiffresParCentre(List.of(CENTRE_B.valeur()), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .isEmpty();
        assertThat(finance().chiffresParCentre(List.of(), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))).isEmpty();
        assertThat(finance().chiffresParMois(List.of(), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))).isEmpty();
        assertThat(finance().chiffreDAffairesParCaisse(List.of(), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))).isEmpty();
    }

    @Test
    void should_cumulate_the_figures_of_all_the_centres_and_give_zero_without_invoice() {
        facture(CENTRE_A, PATIENT_1, "2026-02-10", "100.00", "19.00", "119.00", null, null, null);
        facture(CENTRE_B, PATIENT_3, "2026-02-12", "10.00", "0.00", "10.00", null, null, null);

        ChiffresDuCentre cumul = finance().chiffresCumules(List.of(CENTRE_A.valeur(), CENTRE_B.valeur()),
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        ChiffresDuCentre vide = finance().chiffresCumules(List.of(CENTRE_A.valeur()), LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31));

        assertThat(cumul.factures()).isEqualTo(2);
        assertThat(cumul.chiffreDAffairesTtc()).isEqualByComparingTo("129.00");
        assertThat(vide.factures()).isZero();
        assertThat(vide.chiffreDAffairesTtc()).isEqualByComparingTo("0");
    }

    @Test
    void should_give_the_turnover_per_centre_and_month() {
        facture(CENTRE_A, PATIENT_1, "2026-01-31", "100.00", "0.00", "100.00", null, null, null);
        facture(CENTRE_A, PATIENT_1, "2026-02-01", "200.00", "0.00", "210.00", null, null, null);
        facture(CENTRE_A, PATIENT_2, "2026-02-28", "50.00", "0.00", "50.00", null, null, null);

        var mois = finance().chiffresParMois(List.of(CENTRE_A.valeur()), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28));

        assertThat(mois).hasSize(2);
        assertThat(mois).filteredOn(m -> m.annee() == 2026 && m.mois() == 2).singleElement().satisfies(m -> {
            assertThat(m.chiffreDAffairesHt()).isEqualByComparingTo("250.00");
            assertThat(m.chiffreDAffairesTtc()).isEqualByComparingTo("260.00");
            assertThat(m.centre()).isEqualTo(CENTRE_A.valeur());
        });
        assertThat(mois).filteredOn(m -> m.mois() == 1).singleElement()
                .satisfies(m -> assertThat(m.chiffreDAffairesHt()).isEqualByComparingTo("100.00"));
    }

    @Test
    void should_give_the_ht_turnover_per_fund_with_an_empty_code_for_invoices_without_fund() {
        facture(CENTRE_A, PATIENT_1, "2026-02-10", "100.00", "0.00", "100.00", "CNAS", "CNAS Centre", null);
        facture(CENTRE_A, PATIENT_2, "2026-02-11", "30.00", "0.00", "30.00", "CNAS", "CNAS Centre", null);
        facture(CENTRE_A, PATIENT_3, "2026-02-12", "7.00", "0.00", "7.00", null, null, null);

        var caisses = finance().chiffreDAffairesParCaisse(List.of(CENTRE_A.valeur()), LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28));

        assertThat(caisses).hasSize(2);
        assertThat(caisses).filteredOn(c -> c.codeCaisse().equals("CNAS")).singleElement().satisfies(c -> {
            assertThat(c.chiffreDAffairesHt()).isEqualByComparingTo("130.00");
            assertThat(c.nomCaisse()).isEqualTo("CNAS Centre");
        });
        assertThat(caisses).filteredOn(c -> c.codeCaisse().isEmpty()).singleElement()
                .satisfies(c -> assertThat(c.chiffreDAffairesHt()).isEqualByComparingTo("7.00"));
    }
}
