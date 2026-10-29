package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariants P20 (cycle de vie, une seule en cours, suppression) et P22 (applicable une fois, empreinte).
 */
class ExecutionDOptimisationTest {

    private static final Instant T0 = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-02T08:05:00Z");
    private static final Empreinte EMPREINTE = new Empreinte("abc");

    private static ExecutionDOptimisation lancee() {
        return ExecutionDOptimisation.lancer(new ExecutionId(UUID.randomUUID()), JeuDePlaces.CENTRE,
                JeuDOptimisation.parametres(Perimetre.PATIENTS), "admin", EMPREINTE, T0);
    }

    private static ResultatDOptimisation resultat() {
        return new ResultatDOptimisation(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
    }

    private static void refuse(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RegleDePlanificationViolee.class)
                .extracting(e -> ((RegleDePlanificationViolee) e).code()).isEqualTo(code);
    }

    @Test
    void should_start_in_progress_without_result_nor_application() {
        ExecutionDOptimisation e = lancee();
        assertThat(e.statut()).isEqualTo(ExecutionDOptimisation.Statut.EN_COURS);
        assertThat(e.enCours()).isTrue();
        assertThat(e.resultat()).isNull();
        assertThat(e.appliqueeLe()).isNull();
        assertThat(e.creeLe()).isEqualTo(T0);
    }

    @Test
    void should_refuse_a_second_calculation_while_one_is_in_progress() {
        ExecutionDOptimisation.exigerAucuneEnCours(false);
        refuse(() -> ExecutionDOptimisation.exigerAucuneEnCours(true), "OPTIMISATION_DEJA_EN_COURS");
    }

    @Test
    void should_record_progress_without_changing_the_status() {
        ExecutionDOptimisation e = lancee().progresser("PATIENTS", "0hard/-3soft");
        assertThat(e.phase()).isEqualTo("PATIENTS");
        assertThat(e.score()).isEqualTo("0hard/-3soft");
        assertThat(e.enCours()).isTrue();
    }

    @Test
    void should_keep_the_best_solution_when_finished_and_summarize_it() {
        ExecutionDOptimisation e = lancee().terminer(resultat(), "0hard/0soft", T1);
        assertThat(e.statut()).isEqualTo(ExecutionDOptimisation.Statut.TERMINEE);
        assertThat(e.termineLe()).isEqualTo(T1);
        assertThat(e.resume()).isEqualTo(resultat().resume());
        assertThat(e.erreur()).isNull();
    }

    @Test
    void should_fail_with_the_reason_and_no_result() {
        ExecutionDOptimisation e = lancee().echouer("boom", T1);
        assertThat(e.statut()).isEqualTo(ExecutionDOptimisation.Statut.ECHEC);
        assertThat(e.erreur()).isEqualTo("boom");
        assertThat(e.resultat()).isNull();
        assertThat(e.resume()).isNull();
    }

    @Test
    void should_never_leave_a_final_state() {
        ExecutionDOptimisation echec = lancee().echouer("interrompue", T1);
        ExecutionDOptimisation terminee = lancee().terminer(resultat(), "s", T1);

        for (ExecutionDOptimisation finale : List.of(echec, terminee)) {
            refuse(() -> finale.terminer(resultat(), "s", T1), "OPTIMISATION_TRANSITION_INTERDITE");
            refuse(() -> finale.echouer("x", T1), "OPTIMISATION_TRANSITION_INTERDITE");
            refuse(() -> finale.progresser("PATIENTS", "s"), "OPTIMISATION_TRANSITION_INTERDITE");
        }
        assertThat(echec.statut()).isEqualTo(ExecutionDOptimisation.Statut.ECHEC);
    }

    @Test
    void should_refuse_to_apply_a_calculation_that_is_not_finished_with_a_result() {
        refuse(() -> lancee().appliquer(EMPREINTE, T1), "OPTIMISATION_NON_APPLICABLE");
        refuse(() -> lancee().echouer("x", T1).appliquer(EMPREINTE, T1), "OPTIMISATION_NON_APPLICABLE");
    }

    @Test
    void should_apply_once_when_the_centre_is_unchanged() {
        ExecutionDOptimisation terminee = lancee().terminer(resultat(), "s", T1);
        ExecutionDOptimisation appliquee = terminee.appliquer(EMPREINTE, T1);
        assertThat(appliquee.appliqueeLe()).isEqualTo(T1);
        refuse(() -> appliquee.appliquer(EMPREINTE, T1), "OPTIMISATION_DEJA_APPLIQUEE");
    }

    @Test
    void should_report_not_applicable_before_already_applied_before_outdated() {
        ExecutionDOptimisation appliquee = lancee().terminer(resultat(), "s", T1).appliquer(EMPREINTE, T1);
        // déjà appliquée l'emporte sur périmée
        refuse(() -> appliquee.appliquer(new Empreinte("autre"), T1), "OPTIMISATION_DEJA_APPLIQUEE");
    }

    @Test
    void should_refuse_a_proposition_when_the_centre_changed_since_the_calculation() {
        refuse(() -> lancee().terminer(resultat(), "s", T1).appliquer(new Empreinte("autre"), T1), "OPTIMISATION_PERIMEE");
    }

    @Test
    void should_refuse_to_delete_a_calculation_in_progress_only() {
        refuse(() -> lancee().exigerSupprimable(), "OPTIMISATION_SUPPRESSION_EN_COURS");
        lancee().terminer(resultat(), "s", T1).exigerSupprimable();
        lancee().echouer("x", T1).exigerSupprimable();
    }

    @Test
    void should_refuse_an_execution_without_identity_centre_status_or_parameters() {
        assertThatThrownBy(() -> new ExecutionDOptimisation(null, JeuDePlaces.CENTRE, ExecutionDOptimisation.Statut.EN_COURS,
                JeuDOptimisation.parametres(Perimetre.PATIENTS), T0, null, null, null, null, EMPREINTE, null, null, null, null))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new ExecutionDOptimisation(new ExecutionId(UUID.randomUUID()), null,
                ExecutionDOptimisation.Statut.EN_COURS, JeuDOptimisation.parametres(Perimetre.PATIENTS), T0, null, null, null, null,
                EMPREINTE, null, null, null, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new ExecutionDOptimisation(new ExecutionId(UUID.randomUUID()), JeuDePlaces.CENTRE, null,
                JeuDOptimisation.parametres(Perimetre.PATIENTS), T0, null, null, null, null, EMPREINTE, null, null, null, null))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new ExecutionDOptimisation(new ExecutionId(UUID.randomUUID()), JeuDePlaces.CENTRE,
                ExecutionDOptimisation.Statut.EN_COURS, null, T0, null, null, null, null, EMPREINTE, null, null, null, null))
                .isInstanceOf(SaisieInvalide.class);
    }
}
