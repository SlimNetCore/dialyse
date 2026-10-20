package com.hemodialyse.backend.contexts.facturation.application.simulation;

import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.FactureSimulee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Simulation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.TauxDeTva;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.AMRANI;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.AOUT;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.AUTRE_CENTRE;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.BELKACEM;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.CENTRE;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.MAINTENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulerLaFacturationTest {

    private final SimulationFixture decor = new SimulationFixture();

    @Test
    void should_simulate_the_invoices_of_a_month_with_totals_and_provisional_numbers() {
        decor.seance(AMRANI, 2, "VALIDEE");
        decor.seance(AMRANI, 4, "SIGNEE");
        decor.seance(BELKACEM, 3, "VALIDEE");
        decor.parametres.enregistrer(CENTRE, ParametresDeFacturation.saisis("SIM-{SEQ}", true, MAINTENANT), "admin");

        Simulation simulation = decor.simuler.simuler(CENTRE, AOUT);

        assertThat(simulation.periode().debut()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(simulation.periode().fin()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(simulation.etablieLe()).isEqualTo(MAINTENANT);
        assertThat(simulation.factures()).extracting(FactureSimulee::numeroPrevisionnel).containsExactly("SIM-0042", "SIM-0043");
        assertThat(simulation.totalHt()).isEqualByComparingTo("10500.00");
        assertThat(simulation.totalTva()).isEqualByComparingTo("945.00");
        assertThat(simulation.totalTtc()).isEqualByComparingTo("11445.00");
    }

    @Test
    void should_ignore_seances_that_are_not_validated_or_signed_or_out_of_the_period_or_of_another_centre() {
        decor.seance(AMRANI, 2, "BROUILLON");
        decor.seance(AMRANI, 3, "ABSENT");
        decor.seance(AMRANI, 4, "validee");
        decor.seances.seance(UUID.randomUUID(), CENTRE, AMRANI, LocalDate.of(2026, 9, 1), "VALIDEE", false);
        decor.seances.seance(UUID.randomUUID(), CENTRE, AMRANI, LocalDate.of(2026, 8, 5), "VALIDEE", true);
        decor.seances.seance(UUID.randomUUID(), AUTRE_CENTRE, AMRANI, LocalDate.of(2026, 8, 5), "VALIDEE", false);

        assertThat(decor.simuler.simuler(CENTRE, AOUT).factures()).isEmpty();
    }

    @Test
    void should_use_the_vat_rate_in_force_at_the_end_of_the_period_not_today() {
        decor.seance(AMRANI, 2, "VALIDEE");

        decor.simuler.simuler(CENTRE, AOUT);
        decor.simuler.simuler(CENTRE, new DemandeDeSimulation(null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15), true));

        assertThat(decor.datesDeTvaDemandees).containsExactly(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 15));
    }

    @Test
    void should_charge_no_vat_without_a_vat_rule() {
        decor.seance(AMRANI, 2, "VALIDEE");
        decor.tva = TauxDeTva.AUCUN;

        assertThat(decor.simuler.simuler(CENTRE, AOUT).totalTva()).isEqualByComparingTo("0.00");
    }

    @Test
    void should_number_from_the_default_model_when_the_centre_has_no_settings() {
        decor.seance(AMRANI, 2, "VALIDEE");

        assertThat(decor.simuler.simuler(CENTRE, AOUT).factures().getFirst().numeroPrevisionnel()).isEqualTo("FAC-2026-0042");
    }

    @Test
    void should_start_the_numbering_at_one_when_the_centre_has_no_sequence_for_the_year() {
        decor.seance(AMRANI, 2, "VALIDEE");
        decor.sequence.poser(CENTRE, 2026, 0);

        assertThat(decor.simuler.simuler(CENTRE, AOUT).factures().getFirst().numeroPrevisionnel()).isEqualTo("FAC-2026-0001");
    }

    @Test
    void should_group_by_patient_and_forfait_when_the_request_turns_the_multi_forfait_grouping_off() {
        decor.seance(AMRANI, 2, "VALIDEE");
        UUID autre = decor.seance(AMRANI, 3, "VALIDEE");
        decor.seances.surcharge(autre, new com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique(
                SimulationFixture.FORFAIT_PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00")));

        assertThat(decor.simuler.simuler(CENTRE, AOUT).factures()).hasSize(1);
        assertThat(decor.simuler.simuler(CENTRE, new DemandeDeSimulation(YearMonth.of(2026, 8), null, null, false)).factures())
                .hasSize(2);
    }

    @Test
    void should_refuse_a_missing_period_or_an_end_before_the_start() {
        assertThatThrownBy(() -> decor.simuler.simuler(CENTRE, new DemandeDeSimulation(null, null, null, true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("La periode de facturation est obligatoire");
        assertThatThrownBy(() -> decor.simuler.simuler(CENTRE,
                new DemandeDeSimulation(null, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 1), true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("La date de fin ne peut pas etre avant la date de debut");
    }

    @Test
    void should_not_reserve_anything_when_simulating() {
        decor.seance(AMRANI, 2, "VALIDEE");

        decor.simuler.simuler(CENTRE, AOUT);
        decor.simuler.simuler(CENTRE, AOUT);

        assertThat(decor.sequence.courante(CENTRE, 2026)).isEqualTo(41);
        assertThat(decor.parametres.ecritures()).isZero();
        assertThat(decor.absences.declarees()).isEmpty();
    }
}
