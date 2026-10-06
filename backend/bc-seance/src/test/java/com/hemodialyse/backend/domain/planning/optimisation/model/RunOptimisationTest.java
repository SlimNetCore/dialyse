package com.hemodialyse.backend.domain.planning.optimisation.model;

import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.Indicateurs;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation.StatutRun;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunOptimisationTest {

    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");
    private static final ParametresOptimisation PARAMS =
            ParametresOptimisation.parDefaut(PerimetreOptimisation.PATIENTS, LocalDate.of(2026, 10, 1));

    private static ResultatOptimisation resultat() {
        Indicateurs vide = new Indicateurs(0, 0, 0, 0, 0, 0, 0, 0, 0);
        return new ResultatOptimisation(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), vide, vide);
    }

    private RunOptimisation termine() {
        return RunOptimisation.demarrer(UUID.randomUUID(), PARAMS, "admin", "abc", T0)
                .terminer(resultat(), "0hard/0medium/-5soft", T0.plusSeconds(20));
    }

    @Test
    void should_start_in_progress_without_result() {
        RunOptimisation run = RunOptimisation.demarrer(UUID.randomUUID(), PARAMS, "admin", "abc", T0);

        assertThat(run.enCours()).isTrue();
        assertThat(run.resultat()).isNull();
        assertThat(run.appliqueLe()).isNull();
    }

    @Test
    void should_keep_the_result_and_score_when_finished() {
        RunOptimisation run = termine();

        assertThat(run.statut()).isEqualTo(StatutRun.TERMINEE);
        assertThat(run.score()).isEqualTo("0hard/0medium/-5soft");
        assertThat(run.termineLe()).isEqualTo(T0.plusSeconds(20));
        assertThat(run.enCours()).isFalse();
    }

    @Test
    void should_record_progress_then_failure() {
        RunOptimisation run = RunOptimisation.demarrer(UUID.randomUUID(), PARAMS, "admin", "abc", T0)
                .progression("PATIENTS", "0hard/0medium/-9soft")
                .echouer("boom", T0.plusSeconds(3));

        assertThat(run.statut()).isEqualTo(StatutRun.ECHEC);
        assertThat(run.phase()).isEqualTo("PATIENTS");
        assertThat(run.erreur()).isEqualTo("boom");
    }

    @Test
    void should_apply_a_finished_run_only_once() {
        RunOptimisation applique = termine().appliquer(T0.plusSeconds(60));

        assertThat(applique.appliqueLe()).isEqualTo(T0.plusSeconds(60));
        assertThatThrownBy(() -> applique.appliquer(T0.plusSeconds(90)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("OPTIMISATION_DEJA_APPLIQUEE");
    }

    @Test
    void should_refuse_to_apply_a_run_that_is_not_finished() {
        RunOptimisation enCours = RunOptimisation.demarrer(UUID.randomUUID(), PARAMS, "admin", "abc", T0);
        RunOptimisation echec = enCours.echouer("boom", T0);

        for (RunOptimisation run : List.of(enCours, echec)) {
            assertThatThrownBy(() -> run.appliquer(T0))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("OPTIMISATION_NON_APPLICABLE");
        }
    }
}
