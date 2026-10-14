package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R21 : tâches d'une intervention (fonctionnalité nouvelle, KB-GMAO-55). */
class TachesDInterventionTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000000c"));
    private static final EquipementId EQUIPEMENT = new EquipementId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"));
    private static final InterventionId ID = new InterventionId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UtilisateurId PAR = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
    private static final Instant MAINTENANT = Instant.parse("2026-03-10T07:00:00Z");
    private static final Instant DEBUT = Instant.parse("2026-03-10T08:00:00Z");
    private static final Instant PLUS_TARD = Instant.parse("2026-03-10T09:00:00Z");

    private static int sequence = 1;

    private static TacheId nouvelleTache() {
        return new TacheId(UUID.fromString(String.format("00000000-0000-0000-0000-%012d", 7000 + sequence++)));
    }

    private static Intervention enStatut(StatutIntervention statut) {
        return Intervention.reconstituer(ID, EQUIPEMENT, CENTRE, TypeIntervention.CURATIVE, statut, DEBUT, null,
                new IntervenantId(UUID.randomUUID()), "Fuite", null, null, null, StatutEquipement.EN_MAINTENANCE, null,
                PrioriteIntervention.NORMALE, null, null, null, new Jalon(PAR, MAINTENANT), null, null, null, null,
                List.of(), List.of());
    }

    private static Intervention enCours() {
        return enStatut(StatutIntervention.EN_COURS);
    }

    private static void amener(Intervention intervention, TacheId tache, StatutTache cible) {
        intervention.ajouterTache(tache, "Tâche", PAR, MAINTENANT);
        if (cible != StatutTache.A_FAIRE) intervention.demarrerTache(tache, PAR, MAINTENANT);
        if (cible == StatutTache.COMPLETEE) intervention.acheverTache(tache, PAR, MAINTENANT);
    }

    @Test
    void should_add_a_task_to_do_and_raise_the_event_when_the_intervention_is_open() {
        Intervention intervention = enStatut(StatutIntervention.PLANIFIEE);
        TacheId tache = nouvelleTache();

        intervention.ajouterTache(tache, "  Changer le joint  ", PAR, PLUS_TARD);

        assertThat(intervention.taches()).singleElement().satisfies(t -> {
            assertThat(t.id()).isEqualTo(tache);
            assertThat(t.description()).isEqualTo("Changer le joint");
            assertThat(t.statut()).isEqualTo(StatutTache.A_FAIRE);
            assertThat(t.creation()).isEqualTo(new Jalon(PAR, PLUS_TARD));
            assertThat(t.demarrage()).isNull();
            assertThat(t.achevement()).isNull();
        });
        assertThat(intervention.modification()).isEqualTo(new Jalon(PAR, PLUS_TARD));
        assertThat(intervention.tirerEvenements())
                .containsExactly(new TacheAjoutee(ID, tache, EQUIPEMENT, CENTRE, PAR, PLUS_TARD));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void should_reject_a_task_without_description(String description) {
        Intervention intervention = enCours();

        assertThatThrownBy(() -> intervention.ajouterTache(nouvelleTache(), description, PAR, PLUS_TARD))
                .isInstanceOf(ChampInvalide.class).hasMessage("Description de la tâche requise");
        assertThat(intervention.taches()).isEmpty();
        assertThat(intervention.tirerEvenements()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = StatutIntervention.class, names = {"TERMINEE", "ANNULEE"})
    void should_reject_adding_a_task_to_a_closed_intervention(StatutIntervention statut) {
        Intervention intervention = enStatut(statut);

        assertThatThrownBy(() -> intervention.ajouterTache(nouvelleTache(), "Contrôler", PAR, PLUS_TARD))
                .isInstanceOf(StatutIncompatible.class);
        assertThat(intervention.taches()).isEmpty();
    }

    @Test
    void should_accept_the_thirtieth_task_and_reject_the_next_one() {
        Intervention intervention = enCours();
        IntStream.rangeClosed(1, Intervention.LIMITE_DE_TACHES)
                .forEach(i -> intervention.ajouterTache(nouvelleTache(), "Tâche " + i, PAR, PLUS_TARD));
        intervention.tirerEvenements();

        assertThat(intervention.taches()).hasSize(30);
        assertThatThrownBy(() -> intervention.ajouterTache(nouvelleTache(), "De trop", PAR, PLUS_TARD))
                .isInstanceOf(LimiteDeTachesAtteinte.class);
        assertThat(intervention.taches()).hasSize(30);
        assertThat(intervention.tirerEvenements()).isEmpty();
    }

    @Test
    void should_walk_a_task_from_to_do_to_completed_and_keep_who_did_what() {
        Intervention intervention = enCours();
        TacheId tache = nouvelleTache();
        intervention.ajouterTache(tache, "Changer le joint", PAR, MAINTENANT);
        intervention.tirerEvenements();

        intervention.demarrerTache(tache, PAR, DEBUT);
        assertThat(intervention.taches().getFirst().statut()).isEqualTo(StatutTache.EN_COURS);
        assertThat(intervention.taches().getFirst().demarrage()).isEqualTo(new Jalon(PAR, DEBUT));

        intervention.acheverTache(tache, PAR, PLUS_TARD);
        assertThat(intervention.taches().getFirst().statut()).isEqualTo(StatutTache.COMPLETEE);
        assertThat(intervention.taches().getFirst().achevement()).isEqualTo(new Jalon(PAR, PLUS_TARD));
        assertThat(intervention.tirerEvenements()).containsExactly(
                new TacheDemarree(ID, tache, EQUIPEMENT, CENTRE, PAR, DEBUT),
                new TacheAchevee(ID, tache, EQUIPEMENT, CENTRE, PAR, PLUS_TARD));
    }

    /** Table des transitions : seule une tâche à faire démarre. */
    @ParameterizedTest
    @EnumSource(StatutTache.class)
    void should_only_start_a_task_to_do(StatutTache depart) {
        Intervention intervention = enCours();
        TacheId tache = nouvelleTache();
        amener(intervention, tache, depart);
        intervention.tirerEvenements();

        if (depart == StatutTache.A_FAIRE) {
            intervention.demarrerTache(tache, PAR, PLUS_TARD);
            assertThat(intervention.taches().getFirst().statut()).isEqualTo(StatutTache.EN_COURS);
        } else {
            assertThatThrownBy(() -> intervention.demarrerTache(tache, PAR, PLUS_TARD))
                    .isInstanceOf(TransitionDeTacheInvalide.class);
            assertThat(intervention.taches().getFirst().statut()).isEqualTo(depart);
            assertThat(intervention.tirerEvenements()).isEmpty();
        }
    }

    /** Table des transitions : seule une tâche en cours s'achève. */
    @ParameterizedTest
    @EnumSource(StatutTache.class)
    void should_only_complete_a_task_in_progress(StatutTache depart) {
        Intervention intervention = enCours();
        TacheId tache = nouvelleTache();
        amener(intervention, tache, depart);
        intervention.tirerEvenements();

        if (depart == StatutTache.EN_COURS) {
            intervention.acheverTache(tache, PAR, PLUS_TARD);
            assertThat(intervention.taches().getFirst().statut()).isEqualTo(StatutTache.COMPLETEE);
        } else {
            assertThatThrownBy(() -> intervention.acheverTache(tache, PAR, PLUS_TARD))
                    .isInstanceOf(TransitionDeTacheInvalide.class);
            assertThat(intervention.taches().getFirst().statut()).isEqualTo(depart);
            assertThat(intervention.tirerEvenements()).isEmpty();
        }
    }

    @Test
    void should_reject_an_unknown_task() {
        Intervention intervention = enCours();

        assertThatThrownBy(() -> intervention.demarrerTache(nouvelleTache(), PAR, PLUS_TARD)).isInstanceOf(TacheInconnue.class);
        assertThatThrownBy(() -> intervention.acheverTache(nouvelleTache(), PAR, PLUS_TARD)).isInstanceOf(TacheInconnue.class);
    }

    @ParameterizedTest
    @EnumSource(value = StatutIntervention.class, names = {"PLANIFIEE", "TERMINEE", "ANNULEE"})
    void should_reject_working_a_task_unless_the_intervention_is_in_progress(StatutIntervention statut) {
        Intervention intervention = enStatut(statut);
        TacheId tache = nouvelleTache();

        assertThatThrownBy(() -> intervention.demarrerTache(tache, PAR, PLUS_TARD)).isInstanceOf(StatutIncompatible.class);
        assertThatThrownBy(() -> intervention.acheverTache(tache, PAR, PLUS_TARD)).isInstanceOf(StatutIncompatible.class);
    }

    @Test
    void should_keep_the_tasks_when_the_intervention_is_closed_and_not_expose_them_for_modification() {
        Intervention intervention = enCours();
        intervention.ajouterTache(nouvelleTache(), "Contrôler", PAR, MAINTENANT);

        assertThatThrownBy(() -> intervention.taches().clear()).isInstanceOf(UnsupportedOperationException.class);
        intervention.terminer("Fait", StatutEquipement.EN_SERVICE, PLUS_TARD, null, null, null, PAR, PLUS_TARD);
        assertThat(intervention.taches()).hasSize(1);
    }
}
