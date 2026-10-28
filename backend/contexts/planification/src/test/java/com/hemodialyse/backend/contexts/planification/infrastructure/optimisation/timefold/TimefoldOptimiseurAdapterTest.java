package com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold;

import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.hemodialyse.backend.contexts.planification.application.Optimiseur;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces;
import com.hemodialyse.backend.contexts.planification.domain.ObjectifDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.CauseNonPlace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.LMV;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.LUNDI;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.poste;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.MATIN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le solveur Timefold derrière le port {@link Optimiseur} (ADR 0050) : un problème vide rend aussitôt une proposition vide, un problème
 * minuscule est résolu au plancher de 2 s, l'arrêt anticipé garde la meilleure solution, un échec du moteur est rendu à l'écouteur. Les
 * contraintes et les poids sont ceux du legacy, déplacés tels quels : leur arbitrage fin n'est pas verrouillé ici (le calcul dépend du
 * temps, RG-PLN-090), et ce paquet est exclu de la mutation.
 */
class TimefoldOptimiseurAdapterTest {

    private final TimefoldOptimiseurAdapter adapter = new TimefoldOptimiseurAdapter(2);

    private static ParametresDOptimisation parametres(Perimetre perimetre) {
        return new ParametresDOptimisation(perimetre, LUNDI, 1, 2, 5, ObjectifDesInfirmiers.EQUITE, 2, 6);
    }

    @AfterEach
    void arreter() {
        adapter.destroy();
    }

    private Fin lancer(DonneesDOptimisation donnees, ParametresDOptimisation parametres) {
        Fin fin = new Fin();
        adapter.demarrer(new ExecutionId(UUID.randomUUID()), donnees, parametres, fin);
        return fin;
    }

    @Test
    void should_answer_an_empty_proposition_at_once_for_a_centre_with_nothing_to_plan() throws Exception {
        DonneesDOptimisation vide = new JeuDOptimisation().donnees();
        for (Perimetre perimetre : Perimetre.values()) {
            Fin fin = lancer(vide, parametres(perimetre));
            ResultatDOptimisation r = fin.resultat.get(20, TimeUnit.SECONDS);
            assertThat(r.deplacements()).as(perimetre.name()).isEmpty();
            assertThat(r.nonPlaces()).as(perimetre.name()).isEmpty();
            assertThat(r.vacations()).as(perimetre.name()).isEmpty();
            assertThat(r.temporaires()).as(perimetre.name()).isEmpty();
            assertThat(r.avant()).as(perimetre.name()).isNotNull();
            assertThat(r.apres()).as(perimetre.name()).isNotNull();
            assertThat(r.salles()).as(perimetre.name()).hasSize(3);
            assertThat(r.creneaux()).as(perimetre.name()).hasSize(2);
        }
    }

    @Test
    void should_place_a_waiting_patient_on_a_usable_generator_of_an_ordinary_room() throws Exception {
        DonneesDOptimisation donnees = new JeuDOptimisation().patient(1, LMV, false, null).donnees();

        Fin fin = lancer(donnees, parametres(Perimetre.PATIENTS));
        ResultatDOptimisation r = fin.resultat.get(40, TimeUnit.SECONDS);

        assertThat(r.nonPlaces()).isEmpty();
        assertThat(r.deplacements()).singleElement().satisfies(d -> {
            assertThat(d.patient()).isEqualTo(JeuDePlaces.patient(1));
            assertThat(d.de()).isNull();
            assertThat(d.vers().estComplet()).isTrue();
            assertThat(d.vers().salle()).isNotEqualTo(JeuDePlaces.ISOLEMENT);
        });
        assertThat(r.avant().patientsNonPlaces()).isEqualTo(1);
        assertThat(r.apres().patientsNonPlaces()).isZero();
        assertThat(fin.score).startsWith("PATIENTS ");
    }

    @Test
    void should_place_a_patient_at_risk_only_in_an_isolation_room() throws Exception {
        DonneesDOptimisation donnees = new JeuDOptimisation().patient(1, LMV, true, null).donnees();

        ResultatDOptimisation r = lancer(donnees, parametres(Perimetre.PATIENTS)).resultat.get(40, TimeUnit.SECONDS);

        assertThat(r.deplacements()).singleElement().satisfies(d -> assertThat(d.vers().salle()).isEqualTo(JeuDePlaces.ISOLEMENT));
    }

    @Test
    void should_report_a_patient_without_any_open_day_as_not_placed_because_of_closed_days() throws Exception {
        DonneesDOptimisation donnees = new JeuDOptimisation().patient(1, EnumSet.of(com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine.DIMANCHE),
                false, null).donnees();

        ResultatDOptimisation r = lancer(donnees, parametres(Perimetre.PATIENTS)).resultat.get(40, TimeUnit.SECONDS);

        assertThat(r.nonPlaces()).singleElement().satisfies(n -> assertThat(n.cause()).isEqualTo(CauseNonPlace.JOURS_FERMES));
        assertThat(r.deplacements()).isEmpty();
    }

    @Test
    void should_keep_the_current_place_of_a_patient_who_is_already_well_placed() throws Exception {
        DonneesDOptimisation donnees = new JeuDOptimisation().patient(1, LMV, false, poste(A, MATIN, A1)).donnees();

        ResultatDOptimisation r = lancer(donnees, parametres(Perimetre.PATIENTS)).resultat.get(40, TimeUnit.SECONDS);

        assertThat(r.nonPlaces()).isEmpty();
        assertThat(r.apres().patientsNonPlaces()).isZero();
    }

    @Test
    void should_stop_early_and_keep_the_best_solution() throws Exception {
        DonneesDOptimisation donnees = new JeuDOptimisation().patient(1, LMV, false, null).donnees();
        ParametresDOptimisation longue = new ParametresDOptimisation(Perimetre.PATIENTS, LUNDI, 1, 300, 5, ObjectifDesInfirmiers.EQUITE, 2, 6);
        ExecutionId id = new ExecutionId(UUID.randomUUID());
        Fin fin = new Fin();

        adapter.demarrer(id, donnees, longue, fin);
        assertThat(adapter.arreter(id)).isTrue();

        ResultatDOptimisation r = fin.resultat.get(30, TimeUnit.SECONDS);
        assertThat(r.nonPlaces()).isEmpty();
        assertThat(r.deplacements()).hasSize(1);
    }

    @Test
    void should_not_know_a_calculation_that_is_not_running() {
        assertThat(adapter.arreter(new ExecutionId(UUID.randomUUID()))).isFalse();
    }

    @Test
    void should_hand_a_failure_of_the_engine_to_the_listener() throws Exception {
        Fin fin = lancer(new JeuDOptimisation().donnees(), null);

        assertThat(fin.echec.get(20, TimeUnit.SECONDS)).isNotBlank();
        assertThat(fin.resultat).isNotDone();
    }

    @Test
    void should_report_a_failure_when_the_pool_is_shut_down() throws Exception {
        adapter.destroy();
        Fin fin = lancer(new JeuDOptimisation().donnees(), parametres(Perimetre.PATIENTS));
        assertThat(fin.echec.get(5, TimeUnit.SECONDS)).isEqualTo("Moteur d'optimisation indisponible");
    }

    @Test
    void should_bound_the_search_by_the_duration_and_stop_when_it_no_longer_improves() {
        TerminationConfig court = TimefoldOptimiseurAdapter.terminaison(Duration.ofSeconds(2));
        TerminationConfig long_ = TimefoldOptimiseurAdapter.terminaison(Duration.ofSeconds(60));
        assertThat(court.getSpentLimit()).isEqualTo(Duration.ofSeconds(2));
        assertThat(court.getUnimprovedSpentLimit()).isEqualTo(Duration.ofSeconds(2));
        assertThat(long_.getSpentLimit()).isEqualTo(Duration.ofSeconds(60));
        assertThat(long_.getUnimprovedSpentLimit()).isEqualTo(Duration.ofSeconds(20));
    }

    /**
     * Écouteur qui garde la fin du calcul.
     */
    private static final class Fin implements Optimiseur.Ecouteur {
        final CompletableFuture<ResultatDOptimisation> resultat = new CompletableFuture<>();
        final CompletableFuture<String> echec = new CompletableFuture<>();
        final List<String> progressions = new ArrayList<>();
        volatile String score;

        @Override
        public void progression(String phase, String score) {
            progressions.add(phase + " " + score);
        }

        @Override
        public void termine(ResultatDOptimisation r, String score) {
            this.score = score;
            resultat.complete(r);
        }

        @Override
        public void echec(String message) {
            echec.complete(message);
        }
    }
}
