package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.IndisponibiliteDatee;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.DeplacementTemporaireId;
import com.hemodialyse.backend.contexts.planification.domain.MaintenanceDesGenerateurs.SeanceImpactee;
import com.hemodialyse.backend.contexts.planification.domain.SchemasDeJours.Schema;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.LMV;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.poste;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A2;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.CENTRE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.MATIN;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.SOIR;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.id;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.patient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RG-PLN-093 (schémas de jours candidats d'un patient dont l'optimisation choisit les jours) et RG-PLN-094 / 096 (séances touchées par
 * l'indisponibilité d'un générateur, places réellement occupées à une date).
 */
class SchemasEtMaintenanceDesGenerateursTest {

    private static final LocalDate LUNDI = LocalDate.of(2026, 11, 2);

    // ── schémas de jours ──

    private static JeuDOptimisation jeu() {
        JeuDOptimisation jeu = new JeuDOptimisation()
                .patient(1, LMV, false, poste(A, MATIN, A1))
                .patient(2, LMV, true, poste(B, SOIR, B1));
        jeu.indisponibilites.add(new IndisponibiliteDatee(A1, LUNDI, LUNDI.plusDays(1), "PREVENTIVE"));
        return jeu;
    }

    @Test
    void should_propose_the_best_spaced_schemas_first_within_the_tolerance() {
        List<Schema> candidats = SchemasDeJours.candidats(3, JeuDePlaces.OUVERTS);

        assertThat(candidats).isNotEmpty().hasSizeLessThanOrEqualTo(SchemasDeJours.SCHEMAS_MAX);
        assertThat(candidats.getFirst().jours()).isEqualTo(EnumSet.of(JourDeLaSemaine.LUNDI, JourDeLaSemaine.MERCREDI, JourDeLaSemaine.VENDREDI));
        int meilleur = candidats.getFirst().espacement();
        assertThat(candidats).allSatisfy(s -> {
            assertThat(s.jours()).hasSize(3);
            assertThat(s.espacement()).isGreaterThanOrEqualTo(meilleur - SchemasDeJours.ECART_ESPACEMENT_TOLERE);
        });
        assertThat(candidats).isSortedAccordingTo((a, b) -> Integer.compare(b.espacement(), a.espacement()));
    }

    @Test
    void should_only_use_the_open_days() {
        Set<JourDeLaSemaine> ouverts = EnumSet.of(JourDeLaSemaine.MARDI, JourDeLaSemaine.JEUDI, JourDeLaSemaine.SAMEDI);
        assertThat(SchemasDeJours.candidats(3, ouverts)).singleElement().satisfies(s -> assertThat(s.jours()).isEqualTo(ouverts));
        assertThat(SchemasDeJours.candidats(4, ouverts)).isEmpty();
    }

    // ── maintenance des générateurs ──

    @Test
    void should_refuse_a_number_of_sessions_outside_one_to_seven() {
        assertThatThrownBy(() -> SchemasDeJours.candidats(0, JeuDePlaces.OUVERTS)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> SchemasDeJours.candidats(8, JeuDePlaces.OUVERTS)).isInstanceOf(SaisieInvalide.class);
        assertThat(SchemasDeJours.candidats(7, EnumSet.allOf(JourDeLaSemaine.class))).hasSize(1);
    }

    @Test
    void should_list_the_sessions_of_the_patients_whose_generator_is_unavailable_that_day_with_the_reason() {
        List<SeanceImpactee> impactees = MaintenanceDesGenerateurs.seancesImpactees(jeu().donnees(), LUNDI, LUNDI.plusDays(6));

        assertThat(impactees).singleElement().satisfies(s -> {
            assertThat(s.patient()).isEqualTo(patient(1));
            assertThat(s.date()).isEqualTo(LUNDI);
            assertThat(s.jour()).isEqualTo(JourDeLaSemaine.LUNDI);
            assertThat(s.poste().generateur()).isEqualTo(A1);
            assertThat(s.poste().generateurCode()).isEqualTo("G-A1");
            assertThat(s.motif()).isEqualTo("PREVENTIVE");
            assertThat(s.aRisque()).isFalse();
        });
    }

    @Test
    void should_ignore_the_closed_days_and_the_days_the_patient_does_not_dialyse() {
        JeuDOptimisation jeu = jeu();
        jeu.ressources = JeuDePlaces.ressources(JeuDePlaces.OUVERTS, List.of(new RessourcesDuCentre.Fermeture(LUNDI, "Férié")));
        assertThat(MaintenanceDesGenerateurs.seancesImpactees(jeu.donnees(), LUNDI, LUNDI.plusDays(1))).isEmpty();
        // mardi : le patient ne dialyse pas
        assertThat(MaintenanceDesGenerateurs.seancesImpactees(jeu().donnees(), LUNDI.plusDays(1), LUNDI.plusDays(1))).isEmpty();
    }

    @Test
    void should_ignore_a_session_outside_the_stay_of_the_patient() {
        JeuDOptimisation jeu = jeu();
        jeu.patients.replaceAll(p -> p.patient().equals(patient(1)) ? new DonneesDOptimisation.PatientAPlacer(p.patient(), p.nom(), p.jours(),
                p.aRisque(), p.actuelle(), LUNDI.plusDays(3), null) : p);
        assertThat(MaintenanceDesGenerateurs.seancesImpactees(jeu.donnees(), LUNDI, LUNDI.plusDays(1))).isEmpty();
    }

    @Test
    void should_take_the_temporary_move_as_the_real_place_of_the_date() {
        JeuDOptimisation jeu = jeu();
        jeu.temporaires.add(DeplacementTemporaire.reconstituer(new DeplacementTemporaireId(id(900)), CENTRE, patient(1), LUNDI, A, MATIN,
                A2, null));

        // déjà déplacé sur un générateur disponible : plus de séance touchée ce jour-là
        assertThat(MaintenanceDesGenerateurs.seancesImpactees(jeu.donnees(), LUNDI, LUNDI)).isEmpty();
        assertThat(MaintenanceDesGenerateurs.postesOccupes(jeu.donnees(), LUNDI, Set.of()))
                .contains(MaintenanceDesGenerateurs.cle(A2, MATIN), MaintenanceDesGenerateurs.cle(B1, SOIR))
                .doesNotContain(MaintenanceDesGenerateurs.cle(A1, MATIN));
    }

    @Test
    void should_list_the_occupied_places_of_a_date_without_the_patients_being_moved() {
        assertThat(MaintenanceDesGenerateurs.postesOccupes(jeu().donnees(), LUNDI, Set.of()))
                .containsExactlyInAnyOrder(MaintenanceDesGenerateurs.cle(A1, MATIN), MaintenanceDesGenerateurs.cle(B1, SOIR));
        assertThat(MaintenanceDesGenerateurs.postesOccupes(jeu().donnees(), LUNDI, Set.of(patient(1))))
                .containsExactly(MaintenanceDesGenerateurs.cle(B1, SOIR));
        assertThat(MaintenanceDesGenerateurs.postesOccupes(jeu().donnees(), LUNDI.plusDays(1), Set.of())).isEmpty();
    }

    @Test
    void should_know_whether_a_generator_is_unavailable_at_a_date() {
        DonneesDOptimisation donnees = jeu().donnees();
        assertThat(MaintenanceDesGenerateurs.indisponibilite(donnees, A1, LUNDI)).isPresent();
        assertThat(MaintenanceDesGenerateurs.indisponibilite(donnees, A1, LUNDI.plusDays(2))).isEmpty();
        assertThat(MaintenanceDesGenerateurs.indisponibilite(donnees, B1, LUNDI)).isEmpty();
    }

    @Test
    void should_refuse_an_invalid_unavailability_period() {
        assertThatThrownBy(() -> new IndisponibiliteDatee(A1, LUNDI, LUNDI.minusDays(1), "x")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new IndisponibiliteDatee(null, LUNDI, LUNDI, "x")).isInstanceOf(SaisieInvalide.class);
        assertThat(new IndisponibiliteDatee(A1, LUNDI, LUNDI, "x").couvre(LUNDI)).isTrue();
    }
}
