package com.hemodialyse.backend.domain.planning.optimisation.model;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementTemporairePropose;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.Indicateurs;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.SeanceSansSolution;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationNonPourvue;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationPlanifiee;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MotifPropositionTest {

    private static final LocalDate LUNDI = LocalDate.of(2026, 9, 28);
    private static final Indicateurs VIDE = new Indicateurs(0, 0, 0, 0, 0, 0, 0, 0, 0);

    private static ResultatOptimisation resultat(List<VacationPlanifiee> vacations, List<VacationNonPourvue> manques,
                                                 Indicateurs avant, Indicateurs apres,
                                                 List<DeplacementTemporairePropose> temporaires,
                                                 List<SeanceSansSolution> sansSolution) {
        return new ResultatOptimisation(List.of(), List.of(), List.of(), List.of(), vacations, manques, avant, apres,
                temporaires, sansSolution);
    }

    private static VacationPlanifiee vacation(boolean existante) {
        return new VacationPlanifiee(LUNDI, JourSemaine.LUNDI, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "I", existante);
    }

    @Test
    void should_map_each_automatic_scope_to_its_reason() {
        assertThat(MotifProposition.pour(PerimetreOptimisation.COUVERTURE)).contains(MotifProposition.SOUS_EFFECTIF);
        assertThat(MotifProposition.pour(PerimetreOptimisation.MAINTENANCE)).contains(MotifProposition.MAINTENANCE);
        assertThat(MotifProposition.pour(PerimetreOptimisation.PATIENTS)).contains(MotifProposition.GAIN);
        assertThat(MotifProposition.pour(PerimetreOptimisation.ROULEMENT)).isEmpty();
    }

    @Test
    void should_count_the_vacations_to_fill_for_the_understaffing() {
        ResultatOptimisation r = resultat(List.of(vacation(true), vacation(false)),
                List.of(new VacationNonPourvue(LUNDI, JourSemaine.LUNDI, UUID.randomUUID(), UUID.randomUUID(), 2)),
                VIDE, VIDE, List.of(), List.of());

        assertThat(MotifProposition.SOUS_EFFECTIF.valeur(r)).isEqualTo(3);
        assertThat(MotifProposition.SOUS_EFFECTIF.valeur(resultat(List.of(vacation(true)), List.of(), VIDE, VIDE,
                List.of(), List.of()))).isZero();
    }

    @Test
    void should_count_the_sessions_to_move_for_the_maintenance() {
        Poste poste = new Poste(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "G");
        ResultatOptimisation r = resultat(List.of(), List.of(), VIDE, VIDE,
                List.of(new DeplacementTemporairePropose(UUID.randomUUID(), "P", LUNDI, JourSemaine.LUNDI, poste, poste, "M")),
                List.of(new SeanceSansSolution(UUID.randomUUID(), "Q", LUNDI, JourSemaine.LUNDI, poste, "M")));

        assertThat(MotifProposition.MAINTENANCE.valeur(r)).isEqualTo(2);
    }

    @Test
    void should_measure_the_gain_in_vacations_and_placed_patients() {
        Indicateurs avant = new Indicateurs(4, 4, 6, 0, 1, 0, 0, 0, 0);
        Indicateurs apres = new Indicateurs(4, 2, 4, 0, 0, 0, 0, 0, 0);

        assertThat(MotifProposition.GAIN.valeur(resultat(List.of(), List.of(), avant, apres, List.of(), List.of())))
                .isEqualTo(3);
        assertThat(MotifProposition.GAIN.valeur(resultat(List.of(), List.of(), apres, avant, List.of(), List.of())))
                .as("une dégradation n'est pas un gain").isZero();
    }
}
