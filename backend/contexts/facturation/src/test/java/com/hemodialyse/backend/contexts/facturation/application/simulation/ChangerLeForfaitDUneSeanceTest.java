package com.hemodialyse.backend.contexts.facturation.application.simulation;

import com.hemodialyse.backend.contexts.facturation.domain.simulation.Simulation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.AMRANI;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.AOUT;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.AUTRE_CENTRE;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.CENTRE;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.FORFAIT_PREMIUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangerLeForfaitDUneSeanceTest {

    private final SimulationFixture decor = new SimulationFixture();
    private final ChangerLeForfaitDUneSeance changer =
            new ChangerLeForfaitDUneSeance(decor.seances, decor.seances, decor.simuler, decor.unite, decor.horloge);

    @Test
    void should_impose_the_forfait_on_the_seance_and_recompute_the_simulation() {
        UUID changee = decor.seance(AMRANI, 2, "VALIDEE");
        decor.seance(AMRANI, 3, "VALIDEE");

        Simulation simulation = changer.executer(CENTRE, changee, FORFAIT_PREMIUM, "medecin", AOUT);

        assertThat(simulation.factures().getFirst().lignes()).extracting(l -> l.forfait().libelle(), l -> l.nombreDeSeances())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Forfait HD Premium", 1),
                        org.assertj.core.groups.Tuple.tuple("Forfait HD", 1));
        assertThat(simulation.totalHt()).isEqualByComparingTo(new BigDecimal("8700.00"));
        assertThat(decor.seances.auteurDeLaSurcharge(changee)).isEqualTo("medecin");
    }

    @Test
    void should_refuse_a_forfait_unknown_or_of_another_centre_before_anything_else() {
        UUID seance = decor.seance(AMRANI, 2, "VALIDEE");

        assertThatThrownBy(() -> changer.executer(CENTRE, seance, UUID.randomUUID(), "medecin", AOUT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Forfait introuvable pour ce centre");
        assertThatThrownBy(() -> changer.executer(AUTRE_CENTRE, UUID.randomUUID(), FORFAIT_PREMIUM, "medecin", AOUT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Forfait introuvable pour ce centre");
    }

    @Test
    void should_refuse_a_seance_unknown_invoiced_excluded_or_not_validated() {
        UUID facturee = UUID.randomUUID();
        decor.seances.seance(facturee, CENTRE, AMRANI, java.time.LocalDate.of(2026, 8, 4), "VALIDEE", true);
        UUID brouillon = decor.seance(AMRANI, 2, "BROUILLON");

        for (UUID seance : new UUID[]{UUID.randomUUID(), facturee, brouillon}) {
            assertThatThrownBy(() -> changer.executer(CENTRE, seance, FORFAIT_PREMIUM, "medecin", AOUT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Seance introuvable ou non modifiable pour changement forfait");
        }
    }

    @Test
    void should_change_nothing_when_the_period_is_missing() {
        UUID seance = decor.seance(AMRANI, 2, "VALIDEE");

        assertThatThrownBy(() -> changer.executer(CENTRE, seance, FORFAIT_PREMIUM, "medecin",
                new DemandeDeSimulation(null, null, null, true))).isInstanceOf(IllegalArgumentException.class);
        assertThat(decor.seances.auteurDeLaSurcharge(seance)).isNull();
    }
}
