package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ModeleDeNumerotation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99996800-0000-0000-0000-00000000000a"));
    private static final Instant LE = Instant.parse("2026-10-20T08:00:00Z");
    private static final PeriodeDeFacturation AOUT = new PeriodeDeFacturation(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    private static final ForfaitApplique HD = new ForfaitApplique(UUID.fromString("99996800-0000-0000-0000-0000000f0001"),
            "Forfait HD", new BigDecimal("3500.00"));
    private static final ForfaitApplique PREMIUM = new ForfaitApplique(UUID.fromString("99996800-0000-0000-0000-0000000f0002"),
            "Forfait HD Premium", new BigDecimal("5200.00"));
    private static final PatientFacture AMRANI = new PatientFacture(UUID.fromString("99996800-0000-0000-0000-00000000a001"),
            "P-001", "Amrani", "Lina", "PERMANENT");
    private static final PatientFacture BELKACEM = new PatientFacture(UUID.fromString("99996800-0000-0000-0000-00000000a002"),
            "P-002", "Belkacem", "Omar", null);

    private static SeanceFacturable seance(PatientFacture patient, int jour, ForfaitApplique forfait) {
        return new SeanceFacturable(UUID.randomUUID(), patient, LocalDate.of(2026, 8, jour), StatutDeSeance.VALIDEE, forfait);
    }

    private static NumerotationPrevisionnelle numerotation(int courante) {
        return new NumerotationPrevisionnelle(new ModeleDeNumerotation("SIM-{SEQ}"), LocalDate.of(2026, 10, 20), courante);
    }

    private static Simulation simuler(Regroupement regroupement, TauxDeTva taux, int courante, SeanceFacturable... seances) {
        return Simulation.etablir(CENTRE, LE, AOUT, regroupement, List.of(seances), taux, numerotation(courante));
    }

    @Test
    void should_build_one_invoice_per_patient_with_one_line_per_forfait() {
        Simulation simulation = simuler(Regroupement.PAR_PATIENT, new TauxDeTva(new BigDecimal("9")), 41,
                seance(AMRANI, 2, HD), seance(AMRANI, 4, HD), seance(AMRANI, 6, PREMIUM), seance(BELKACEM, 3, HD));

        assertThat(simulation.factures()).hasSize(2);
        FactureSimulee amrani = simulation.factures().getFirst();
        assertThat(amrani.cle()).isEqualTo(AMRANI.id().toString());
        assertThat(amrani.lignes()).extracting(l -> l.forfait().libelle(), LigneSimulee::nombreDeSeances)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Forfait HD", 2), org.assertj.core.groups.Tuple.tuple("Forfait HD Premium", 1));
        assertThat(amrani.lignes().getFirst().montantHt()).isEqualByComparingTo("7000.00");
        assertThat(amrani.totalHt()).isEqualByComparingTo("12200.00");
        assertThat(amrani.totalTva()).isEqualByComparingTo("1098.00");
        assertThat(amrani.totalTtc()).isEqualByComparingTo("13298.00");
        assertThat(amrani.seances()).hasSize(3);
    }

    @Test
    void should_build_one_invoice_per_patient_and_forfait_when_the_multi_forfait_grouping_is_off() {
        Simulation simulation = simuler(Regroupement.PAR_PATIENT_ET_FORFAIT, TauxDeTva.AUCUN, 0,
                seance(AMRANI, 2, HD), seance(AMRANI, 4, PREMIUM), seance(AMRANI, 6, HD));

        assertThat(simulation.factures()).extracting(FactureSimulee::cle)
                .containsExactly(AMRANI.id() + "::" + HD.id(), AMRANI.id() + "::" + PREMIUM.id());
        assertThat(simulation.factures().getFirst().lignes()).hasSize(1);
        assertThat(simulation.factures().getFirst().seances()).hasSize(2);
    }

    @Test
    void should_number_the_invoices_after_the_current_sequence_without_reserving_it() {
        Simulation simulation = simuler(Regroupement.PAR_PATIENT, TauxDeTva.AUCUN, 41,
                seance(AMRANI, 2, HD), seance(BELKACEM, 3, HD));

        assertThat(simulation.factures()).extracting(FactureSimulee::numeroPrevisionnel).containsExactly("SIM-0042", "SIM-0043");
    }

    @Test
    void should_total_the_invoices_with_two_decimals() {
        Simulation simulation = simuler(Regroupement.PAR_PATIENT, new TauxDeTva(new BigDecimal("9")), 0,
                seance(AMRANI, 2, HD), seance(BELKACEM, 3, PREMIUM));

        assertThat(simulation.totalHt().toPlainString()).isEqualTo("8700.00");
        assertThat(simulation.totalTva().toPlainString()).isEqualTo("783.00");
        assertThat(simulation.totalTtc().toPlainString()).isEqualTo("9483.00");
    }

    @Test
    void should_round_the_vat_of_each_invoice_half_up() {
        ForfaitApplique cher = new ForfaitApplique(UUID.randomUUID(), "Forfait", new BigDecimal("33.33"));

        Simulation simulation = simuler(Regroupement.PAR_PATIENT, new TauxDeTva(new BigDecimal("19")), 0, seance(AMRANI, 2, cher));

        assertThat(simulation.factures().getFirst().totalTva().toPlainString()).isEqualTo("6.33");
        assertThat(simulation.factures().getFirst().totalTtc().toPlainString()).isEqualTo("39.66");
    }

    @Test
    void should_be_empty_with_zero_totals_when_there_is_no_seance() {
        Simulation simulation = simuler(Regroupement.PAR_PATIENT, new TauxDeTva(new BigDecimal("9")), 41);

        assertThat(simulation.factures()).isEmpty();
        assertThat(simulation.totalHt().toPlainString()).isEqualTo("0.00");
        assertThat(simulation.totalTva().toPlainString()).isEqualTo("0.00");
        assertThat(simulation.totalTtc().toPlainString()).isEqualTo("0.00");
    }

    @Test
    void should_price_a_seance_without_forfait_at_zero_on_its_own_line() {
        Simulation simulation = simuler(Regroupement.PAR_PATIENT, TauxDeTva.AUCUN, 0,
                seance(AMRANI, 2, ForfaitApplique.NON_DEFINI), seance(AMRANI, 3, HD));

        FactureSimulee facture = simulation.factures().getFirst();
        assertThat(facture.lignes()).extracting(l -> l.forfait().libelle()).containsExactly("Forfait non defini", "Forfait HD");
        assertThat(facture.totalHt()).isEqualByComparingTo("3500.00");
    }

    @Test
    void should_keep_the_period_and_the_instant_of_the_simulation() {
        Simulation simulation = simuler(Regroupement.PAR_PATIENT, TauxDeTva.AUCUN, 0);

        assertThat(simulation.periode()).isEqualTo(AOUT);
        assertThat(simulation.etablieLe()).isEqualTo(LE);
        assertThat(simulation.centre()).isEqualTo(CENTRE);
    }

    @Test
    void should_only_accept_validated_or_signed_seances() {
        assertThatThrownBy(() -> new SeanceFacturable(UUID.randomUUID(), AMRANI, LocalDate.of(2026, 8, 2),
                new StatutDeSeance("BROUILLON"), HD)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SeanceFacturable(UUID.randomUUID(), AMRANI, LocalDate.of(2026, 8, 2),
                new StatutDeSeance("validee"), HD)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new SeanceFacturable(UUID.randomUUID(), AMRANI, LocalDate.of(2026, 8, 2), StatutDeSeance.SIGNEE, HD).statut())
                .isEqualTo(StatutDeSeance.SIGNEE);
    }

    @Test
    void should_present_the_patient_with_dashes_for_missing_data() {
        assertThat(BELKACEM.etatAffiche()).isEqualTo("-");
        assertThat(BELKACEM.codeAffiche()).isEqualTo("P-002");
        assertThat(BELKACEM.nomComplet()).isEqualTo("Belkacem Omar");
        assertThat(new PatientFacture(UUID.randomUUID(), " ", null, null, "").codeAffiche()).isEqualTo("-");
        assertThat(new PatientFacture(UUID.randomUUID(), null, null, null, null).nomComplet()).isEmpty();
    }
}
