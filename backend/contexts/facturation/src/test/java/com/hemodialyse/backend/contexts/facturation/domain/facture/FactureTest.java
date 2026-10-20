package com.hemodialyse.backend.contexts.facturation.domain.facture;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ModeleDeNumerotation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.FactureSimulee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.NumerotationPrevisionnelle;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PatientFacture;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Regroupement;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeanceFacturable;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Simulation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.TauxDeTva;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FactureTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99996900-0000-0000-0000-00000000000a"));
    private static final PeriodeDeFacturation AOUT = new PeriodeDeFacturation(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 20);
    private static final UUID AUTEUR = UUID.fromString("99996900-0000-0000-0000-00000000c001");
    private static final UUID PAYEUR = UUID.fromString("99996900-0000-0000-0000-000000000c01");
    private static final UUID AGENCE = UUID.fromString("99996900-0000-0000-0000-000000000a61");
    private static final PatientFacture AMRANI = new PatientFacture(UUID.fromString("99996900-0000-0000-0000-00000000a001"),
            "P-001", "Amrani", "Lina", "PERMANENT", "ASS-001", PAYEUR, AGENCE);
    private static final ForfaitApplique HD = new ForfaitApplique(UUID.fromString("99996900-0000-0000-0000-0000000f0001"),
            "Forfait HD", new BigDecimal("3500.00"));
    private static final ForfaitApplique PREMIUM = new ForfaitApplique(UUID.fromString("99996900-0000-0000-0000-0000000f0002"),
            "Forfait HD Premium", new BigDecimal("5200.00"));

    private static SeanceFacturable seance(PatientFacture patient, int jour, ForfaitApplique forfait) {
        return new SeanceFacturable(UUID.randomUUID(), patient, LocalDate.of(2026, 8, jour), StatutDeSeance.VALIDEE, forfait);
    }

    private static FactureSimulee groupe(TauxDeTva taux, SeanceFacturable... seances) {
        Simulation simulation = Simulation.etablir(CENTRE, Instant.parse("2026-10-20T08:00:00Z"), AOUT, Regroupement.PAR_PATIENT,
                List.of(seances), taux, new NumerotationPrevisionnelle(ModeleDeNumerotation.PAR_DEFAUT, JOUR, 0));
        return simulation.factures().getFirst();
    }

    private static Facture emettre(FactureSimulee groupe, TauxDeTva taux) {
        return Facture.emettre(UUID.randomUUID(), CENTRE, groupe, "FAC-2026-0042", AOUT, JOUR, taux, AUTEUR);
    }

    @Test
    void should_freeze_the_patient_the_payer_the_period_the_rate_and_the_lines_of_the_simulation() {
        TauxDeTva taux = new TauxDeTva(new BigDecimal("9"));
        SeanceFacturable premiere = seance(AMRANI, 2, HD);
        FactureSimulee groupe = groupe(taux, premiere, seance(AMRANI, 4, HD), seance(AMRANI, 6, PREMIUM));

        Facture facture = emettre(groupe, taux);

        assertThat(facture.numero()).isEqualTo("FAC-2026-0042");
        assertThat(facture.centre()).isEqualTo(CENTRE);
        assertThat(facture.patient()).isEqualTo(AMRANI);
        assertThat(facture.periode()).isEqualTo(AOUT);
        assertThat(facture.dateDeFacturation()).isEqualTo(JOUR);
        assertThat(facture.emisePar()).isEqualTo(AUTEUR);
        assertThat(facture.taux().pourcentage()).isEqualTo(new BigDecimal("9.00"));
        assertThat(facture.lignes()).containsExactly(new LigneFacture(HD.id(), "Forfait HD", new BigDecimal("3500.00"), 2),
                new LigneFacture(PREMIUM.id(), "Forfait HD Premium", new BigDecimal("5200.00"), 1));
        assertThat(facture.seances()).hasSize(3).contains(premiere.id());
    }

    @Test
    void should_keep_the_identity_given_at_the_emission() {
        UUID identite = UUID.randomUUID();
        TauxDeTva taux = TauxDeTva.AUCUN;

        Facture facture = Facture.emettre(identite, CENTRE, groupe(taux, seance(AMRANI, 2, HD)), "FAC-1", AOUT, JOUR, taux, AUTEUR);

        assertThat(facture.id()).isEqualTo(identite);
    }

    @Test
    void should_total_the_lines_and_apply_the_rate_on_the_invoice_total() {
        TauxDeTva taux = new TauxDeTva(new BigDecimal("9"));

        Facture facture = emettre(groupe(taux, seance(AMRANI, 2, HD), seance(AMRANI, 4, HD), seance(AMRANI, 6, PREMIUM)), taux);

        assertThat(facture.totalHt()).isEqualByComparingTo("12200.00");
        assertThat(facture.totalTva()).isEqualByComparingTo("1098.00");
        assertThat(facture.totalTtc()).isEqualByComparingTo("13298.00");
    }

    @Test
    void should_round_the_vat_half_up_on_the_total_not_line_by_line() {
        TauxDeTva taux = new TauxDeTva(new BigDecimal("19"));
        ForfaitApplique centimes = new ForfaitApplique(UUID.randomUUID(), "Forfait test", new BigDecimal("12.50"));

        Facture facture = emettre(groupe(taux, seance(AMRANI, 2, centimes)), taux);

        assertThat(facture.totalHt()).isEqualByComparingTo("12.50");
        assertThat(facture.totalTva()).isEqualByComparingTo("2.38");
        assertThat(facture.totalTtc()).isEqualByComparingTo("14.88");
    }

    @Test
    void should_round_a_vat_just_below_the_half_cent_down() {
        TauxDeTva taux = new TauxDeTva(new BigDecimal("19"));
        ForfaitApplique forfait = new ForfaitApplique(UUID.randomUUID(), "Forfait test", new BigDecimal("99.99"));

        Facture facture = emettre(groupe(taux, seance(AMRANI, 2, forfait)), taux);

        assertThat(facture.totalTva()).isEqualByComparingTo("19.00");
        assertThat(facture.totalTtc()).isEqualByComparingTo("118.99");
    }

    @Test
    void should_have_no_vat_without_a_rate() {
        Facture facture = emettre(groupe(TauxDeTva.AUCUN, seance(AMRANI, 2, HD)), TauxDeTva.AUCUN);

        assertThat(facture.totalTva()).isEqualByComparingTo("0.00");
        assertThat(facture.totalTtc()).isEqualByComparingTo("3500.00");
        assertThat(facture.taux().pourcentage()).isEqualByComparingTo("0");
    }

    @Test
    void should_keep_the_issuer_unknown_when_the_session_does_not_give_one() {
        Facture facture = Facture.emettre(UUID.randomUUID(), CENTRE, groupe(TauxDeTva.AUCUN, seance(AMRANI, 2, HD)), "FAC-1", AOUT,
                JOUR, TauxDeTva.AUCUN, null);

        assertThat(facture.emisePar()).isNull();
    }

    @Test
    void should_refuse_an_invoice_without_number() {
        FactureSimulee groupe = groupe(TauxDeTva.AUCUN, seance(AMRANI, 2, HD));

        assertThatThrownBy(() -> Facture.emettre(UUID.randomUUID(), CENTRE, groupe, " ", AOUT, JOUR, TauxDeTva.AUCUN, AUTEUR))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Le numero de facture est obligatoire");
        assertThatThrownBy(() -> Facture.emettre(UUID.randomUUID(), CENTRE, groupe, null, AOUT, JOUR, TauxDeTva.AUCUN, AUTEUR))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Le numero de facture est obligatoire");
    }

    @Test
    void should_refuse_an_invoice_without_identity_period_date_or_rate() {
        FactureSimulee groupe = groupe(TauxDeTva.AUCUN, seance(AMRANI, 2, HD));

        assertThatThrownBy(() -> Facture.emettre(null, CENTRE, groupe, "FAC-1", AOUT, JOUR, TauxDeTva.AUCUN, AUTEUR))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Les identifiants facture, centre et patient sont obligatoires");
        assertThatThrownBy(() -> Facture.emettre(UUID.randomUUID(), null, groupe, "FAC-1", AOUT, JOUR, TauxDeTva.AUCUN, AUTEUR))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Les identifiants facture, centre et patient sont obligatoires");
        assertThatThrownBy(() -> Facture.emettre(UUID.randomUUID(), CENTRE, groupe, "FAC-1", null, JOUR, TauxDeTva.AUCUN, AUTEUR))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("La periode, la date de facturation et la TVA sont obligatoires");
        assertThatThrownBy(() -> Facture.emettre(UUID.randomUUID(), CENTRE, groupe, "FAC-1", AOUT, null, TauxDeTva.AUCUN, AUTEUR))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("La periode, la date de facturation et la TVA sont obligatoires");
        assertThatThrownBy(() -> Facture.emettre(UUID.randomUUID(), CENTRE, groupe, "FAC-1", AOUT, JOUR, null, AUTEUR))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("La periode, la date de facturation et la TVA sont obligatoires");
    }

    @Test
    void should_refuse_an_invoice_without_line_or_without_seance() {
        FactureSimulee sansLigne = new FactureSimulee("cle", "FAC-1", AMRANI, List.of(), List.of(seance(AMRANI, 2, HD)),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        FactureSimulee sansSeance = new FactureSimulee("cle", "FAC-1", AMRANI,
                groupe(TauxDeTva.AUCUN, seance(AMRANI, 2, HD)).lignes(), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatThrownBy(() -> emettre(sansLigne, TauxDeTva.AUCUN)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Une facture doit contenir au moins une ligne");
        assertThatThrownBy(() -> emettre(sansSeance, TauxDeTva.AUCUN)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Une facture couvre au moins une seance");
    }

    @Test
    void should_refuse_a_line_with_a_negative_price() {
        TauxDeTva taux = TauxDeTva.AUCUN;
        FactureSimulee groupe = groupe(taux,
                seance(AMRANI, 2, new ForfaitApplique(UUID.randomUUID(), "Forfait negatif", new BigDecimal("-1.00"))));

        assertThatThrownBy(() -> emettre(groupe, taux)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le prix unitaire HT est invalide");
    }

    @Test
    void should_not_let_the_caller_change_the_lines_or_the_seances_afterwards() {
        Facture facture = emettre(groupe(TauxDeTva.AUCUN, seance(AMRANI, 2, HD)), TauxDeTva.AUCUN);

        assertThatThrownBy(() -> facture.lignes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> facture.seances().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_refuse_the_emission_while_a_seance_has_no_forfait_and_count_them() {
        List<SeanceFacturable> seances = List.of(seance(AMRANI, 2, HD), seance(AMRANI, 3, ForfaitApplique.NON_DEFINI),
                seance(AMRANI, 4, ForfaitApplique.NON_DEFINI));

        assertThatThrownBy(() -> Facture.exigerUnForfaitPourChaqueSeance(seances)).isInstanceOf(SeancesSansForfait.class)
                .satisfies(e -> assertThat(((SeancesSansForfait) e).nombre()).isEqualTo(2))
                .hasMessageContaining("2 seances eligibles n'ont pas de forfait defini");
    }

    @Test
    void should_name_a_single_seance_without_forfait_in_the_singular() {
        List<SeanceFacturable> seances = List.of(seance(AMRANI, 3, ForfaitApplique.NON_DEFINI));

        assertThatThrownBy(() -> Facture.exigerUnForfaitPourChaqueSeance(seances)).isInstanceOf(SeancesSansForfait.class)
                .hasMessageContaining("1 seance eligible n'a pas de forfait defini");
    }

    @Test
    void should_accept_seances_that_all_have_a_forfait_even_at_zero() {
        ForfaitApplique gratuit = new ForfaitApplique(UUID.randomUUID(), "Forfait offert", BigDecimal.ZERO);

        Facture.exigerUnForfaitPourChaqueSeance(List.of(seance(AMRANI, 2, HD), seance(AMRANI, 3, gratuit)));
    }
}
