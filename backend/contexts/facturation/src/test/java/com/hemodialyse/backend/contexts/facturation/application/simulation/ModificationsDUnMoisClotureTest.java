package com.hemodialyse.backend.contexts.facturation.application.simulation;

import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationClos;
import com.hemodialyse.backend.contexts.facturation.domain.seance.MotifDAbsence;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.AMRANI;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.AOUT;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.AUTRE_CENTRE;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.CENTRE;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.FORFAIT_PREMIUM;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.UTILISATEUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RG-FAC-041 : exclure, réinclure ou changer le forfait d'une séance dont le mois est clos est refusé, sans rien modifier ;
 * la simulation, elle, reste permise.
 */
class ModificationsDUnMoisClotureTest {

    private final SimulationFixture decor = new SimulationFixture();
    private final ExclureUneSeance exclure = new ExclureUneSeance(decor.seances, decor.absences, decor.simuler, decor.unite, decor.garde);
    private final ReinclureUneSeance reinclure = new ReinclureUneSeance(decor.seances, decor.absences, decor.simuler, decor.unite, decor.garde);
    private final ChangerLeForfaitDUneSeance changer =
            new ChangerLeForfaitDUneSeance(decor.seances, decor.seances, decor.simuler, decor.unite, decor.horloge, decor.garde);

    @Test
    void should_refuse_to_exclude_a_seance_of_a_closed_month_and_change_nothing() {
        UUID seance = decor.seance(AMRANI, 2, "VALIDEE");
        decor.moisDeFacturation.poserClos(CENTRE, YearMonth.of(2026, 8));

        assertThatThrownBy(() -> exclure.executer(CENTRE, seance,
                new ExclureUneSeance.Commande(MotifDAbsence.MALADIE, null, UTILISATEUR), AOUT))
                .isInstanceOf(MoisDeFacturationClos.class).hasMessageContaining("2026-08");

        assertThat(decor.seances.trouver(CENTRE, seance).orElseThrow().statut()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThat(decor.absences.declarees()).isEmpty();
    }

    @Test
    void should_refuse_to_reinclude_a_seance_of_a_closed_month() {
        UUID seance = decor.seance(AMRANI, 2, "VALIDEE");
        exclure.executer(CENTRE, seance, new ExclureUneSeance.Commande(MotifDAbsence.MALADIE, null, UTILISATEUR), AOUT);
        decor.moisDeFacturation.poserClos(CENTRE, YearMonth.of(2026, 8));

        assertThatThrownBy(() -> reinclure.executer(CENTRE, seance, AOUT)).isInstanceOf(MoisDeFacturationClos.class);

        assertThat(decor.seances.trouver(CENTRE, seance).orElseThrow().statut()).isEqualTo(StatutDeSeance.ABSENT);
        assertThat(decor.absences.declarees()).hasSize(1);
    }

    @Test
    void should_refuse_to_change_the_forfait_of_a_seance_of_a_closed_month() {
        UUID seance = decor.seance(AMRANI, 2, "VALIDEE");
        decor.moisDeFacturation.poserClos(CENTRE, YearMonth.of(2026, 8));

        assertThatThrownBy(() -> changer.executer(CENTRE, seance, FORFAIT_PREMIUM, "medecin", AOUT))
                .isInstanceOf(MoisDeFacturationClos.class);

        assertThat(decor.seances.auteurDeLaSurcharge(seance)).isNull();
    }

    @Test
    void should_only_look_at_the_month_of_the_date_of_the_seance_not_at_the_requested_period() {
        UUID seance = decor.seance(AMRANI, 2, "VALIDEE");
        decor.moisDeFacturation.poserClos(CENTRE, YearMonth.of(2026, 9));
        decor.moisDeFacturation.poserClos(AUTRE_CENTRE, YearMonth.of(2026, 8));

        changer.executer(CENTRE, seance, FORFAIT_PREMIUM, "medecin", AOUT);

        assertThat(decor.seances.auteurDeLaSurcharge(seance)).isEqualTo("medecin");
    }

    @Test
    void should_still_simulate_a_closed_month() {
        decor.seance(AMRANI, 2, "VALIDEE");
        decor.moisDeFacturation.poserClos(CENTRE, YearMonth.of(2026, 8));

        assertThat(decor.simuler.simuler(CENTRE, AOUT).factures()).hasSize(1);
    }
}
