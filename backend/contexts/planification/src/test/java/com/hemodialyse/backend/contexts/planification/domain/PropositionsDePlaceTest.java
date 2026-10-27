package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.Alerte;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.CaseDeGrille;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.DemandeDePlace;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.Proposition;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.Raison;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.Resultat;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Generateur;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Salle;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement.A_RISQUE;
import static com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement.SANS_RISQUE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.*;
import static com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Propositions, score 0 a 100 (RG-PLN-030), espacement (031), au plus 2 par salle et creneau (033), grille (036), alertes (023, 025). */
class PropositionsDePlaceTest {

    private static DemandeDePlace trois() {
        return new DemandeDePlace(3, null, null, null, SANS_RISQUE);
    }

    private static Resultat chercher(DemandeDePlace demande, int limite, Occupation... occupations) {
        return PropositionsDePlace.rechercher(ressources(), List.of(occupations), demande, limite);
    }

    @Test
    void should_score_100_for_the_ideal_placement_with_well_spaced_days() {
        Resultat resultat = chercher(trois(), 12);

        assertThat(resultat.propositions()).isNotEmpty();
        Proposition meilleure = resultat.propositions().getFirst();
        assertThat(meilleure.score()).isEqualTo(100);
        assertThat(meilleure.raisons()).contains(Raison.JOURS_BIEN_ESPACES, Raison.SALLE_PEU_CHARGEE);
        assertThat(meilleure.jours()).isIn(List.of(LUNDI, MERCREDI, VENDREDI), List.of(MARDI, JEUDI, SAMEDI));
    }

    @Test
    void should_keep_at_most_two_proposals_per_room_and_slot() {
        Resultat resultat = chercher(trois(), 30);

        Map<String, Integer> parSalleEtCreneau = new HashMap<>();
        resultat.propositions().forEach(p -> parSalleEtCreneau.merge(p.salle().id() + "|" + p.creneau().id(), 1, Integer::sum));
        assertThat(parSalleEtCreneau.values()).allMatch(n -> n <= 2);
        assertThat(resultat.propositions()).hasSize(8);   // 2 salles ordinaires x 2 creneaux x 2
    }

    @Test
    void should_cap_the_number_of_proposals_to_the_limit_and_to_30() {
        assertThat(chercher(trois(), 3).propositions()).hasSize(3);
        assertThat(chercher(trois(), 1000).propositions()).hasSizeLessThanOrEqualTo(PropositionsDePlace.LIMITE_MAX);
        assertThat(chercher(trois(), 0).propositions()).hasSize(1);
    }

    @Test
    void should_favour_the_wished_slot_and_room() {
        Resultat resultat = chercher(new DemandeDePlace(3, null, SOIR, B, SANS_RISQUE), 12);

        Proposition meilleure = resultat.propositions().getFirst();
        assertThat(meilleure.creneau().id()).isEqualTo(SOIR);
        assertThat(meilleure.salle().id()).isEqualTo(B);
        assertThat(meilleure.score()).isEqualTo(100);
        assertThat(meilleure.raisons()).contains(Raison.CRENEAU_PREFERE, Raison.SALLE_PREFEREE);
        Proposition matinDeA = resultat.propositions().stream()
                .filter(p -> p.salle().id().equals(A) && p.creneau().id().equals(MATIN)).findFirst().orElseThrow();
        assertThat(matinDeA.score()).isEqualTo(65);   // (55 + 10) / (55 + 10 + 20 + 15)
    }

    @Test
    void should_rank_only_slot_wished_proposals_100_and_others_lower() {
        Resultat resultat = chercher(new DemandeDePlace(3, null, SOIR, null, SANS_RISQUE), 12);

        Proposition matin = resultat.propositions().stream().filter(p -> p.creneau().id().equals(MATIN)).findFirst().orElseThrow();
        assertThat(matin.score()).isEqualTo(76);   // (55 + 10) / (55 + 10 + 20)
    }

    @Test
    void should_impose_the_days_and_say_so() {
        Resultat resultat = chercher(new DemandeDePlace(0, Set.of(LUNDI, JEUDI), null, null, SANS_RISQUE), 12);

        assertThat(resultat.propositions()).isNotEmpty()
                .allMatch(p -> p.jours().equals(List.of(LUNDI, JEUDI)) && p.raisons().contains(Raison.JOURS_IMPOSES_LIBRES));
    }

    @Test
    void should_alert_when_the_imposed_days_are_closed() {
        Resultat resultat = chercher(new DemandeDePlace(0, Set.of(DIMANCHE), null, null, SANS_RISQUE), 12);

        assertThat(resultat.propositions()).isEmpty();
        assertThat(resultat.alertes()).containsExactly(Alerte.JOURS_IMPOSES_FERMES);
    }

    @Test
    void should_only_propose_isolation_rooms_to_a_patient_at_risk_and_never_to_a_patient_without() {
        Resultat aRisque = chercher(new DemandeDePlace(3, null, null, null, A_RISQUE), 30);
        Resultat sain = chercher(trois(), 30);

        assertThat(aRisque.propositions()).isNotEmpty().allMatch(p -> p.salle().id().equals(ISOLEMENT))
                .allMatch(p -> p.raisons().contains(Raison.SALLE_ISOLEMENT));
        assertThat(sain.propositions()).noneMatch(p -> p.salle().id().equals(ISOLEMENT));
    }

    @Test
    void should_alert_when_a_patient_at_risk_has_no_isolation_room() {
        RessourcesDuCentre sansIsolement = new RessourcesDuCentre(List.of(new Salle(A, "A", false)),
                ressources().creneaux(), List.of(new Generateur(A1, "G", A)), OUVERTS, List.of());

        Resultat resultat = PropositionsDePlace.rechercher(sansIsolement, List.of(), new DemandeDePlace(3, null, null, null, A_RISQUE), 5);

        assertThat(resultat.propositions()).isEmpty();
        assertThat(resultat.alertes()).containsExactly(Alerte.AUCUNE_SALLE_ISOLEMENT);
    }

    @Test
    void should_never_propose_a_generator_busy_that_day_and_slot() {
        Resultat resultat = chercher(new DemandeDePlace(0, Set.of(LUNDI), null, null, SANS_RISQUE), 30,
                occupation(patient(1), A, MATIN, A1, SANS_RISQUE, LUNDI),
                occupation(patient(2), A, MATIN, A2, SANS_RISQUE, LUNDI));

        assertThat(resultat.propositions()).noneMatch(p -> p.salle().id().equals(A) && p.creneau().id().equals(MATIN));
        assertThat(resultat.propositions()).anyMatch(p -> p.salle().id().equals(A) && p.creneau().id().equals(SOIR));
    }

    @Test
    void should_count_a_patient_without_a_precise_generator_as_taking_a_place_of_the_room() {
        Resultat resultat = chercher(new DemandeDePlace(0, Set.of(LUNDI), null, null, SANS_RISQUE), 30,
                occupation(patient(1), B, MATIN, null, SANS_RISQUE, LUNDI));

        assertThat(resultat.propositions()).noneMatch(p -> p.salle().id().equals(B) && p.creneau().id().equals(MATIN));
    }

    @Test
    void should_count_a_patient_on_a_generator_out_of_service_as_taking_a_place_of_the_room() {
        Resultat resultat = chercher(new DemandeDePlace(0, Set.of(LUNDI), null, null, SANS_RISQUE), 30,
                occupation(patient(1), B, MATIN, HORS_SERVICE, SANS_RISQUE, LUNDI));

        assertThat(resultat.propositions()).noneMatch(p -> p.salle().id().equals(B) && p.creneau().id().equals(MATIN));
    }

    @Test
    void should_never_mix_patients_at_risk_and_without_risk_on_a_generator_whatever_the_day() {
        Resultat resultat = chercher(new DemandeDePlace(0, Set.of(MARDI), null, null, SANS_RISQUE), 30,
                occupation(patient(1), A, MATIN, A1, A_RISQUE, LUNDI));

        assertThat(resultat.propositions()).filteredOn(p -> p.salle().id().equals(A) && p.creneau().id().equals(MATIN))
                .isNotEmpty().allMatch(p -> p.generateur().id().equals(A2) && p.generateursAlternatifs().isEmpty());
    }

    @Test
    void should_report_the_next_dated_closure_on_a_proposed_day() {
        RessourcesDuCentre avecFermeture = ressources(OUVERTS, List.of(fermeture(LocalDate.of(2026, 11, 9), "Férié"),
                fermeture(LocalDate.of(2026, 11, 2), "Fermeture exceptionnelle")));

        Resultat resultat = PropositionsDePlace.rechercher(avecFermeture, List.of(),
                new DemandeDePlace(0, Set.of(LUNDI, JEUDI), null, null, SANS_RISQUE), 1);

        assertThat(resultat.propositions().getFirst().fermetures()).singleElement().satisfies(f -> {
            assertThat(f.jour()).isEqualTo(LUNDI);
            assertThat(f.date()).isEqualTo(LocalDate.of(2026, 11, 2));
            assertThat(f.motif()).isEqualTo("Fermeture exceptionnelle");
        });
    }

    @Test
    void should_refuse_a_number_of_sessions_outside_1_to_7() {
        assertThatThrownBy(() -> chercher(new DemandeDePlace(0, null, null, null, SANS_RISQUE), 5)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> chercher(new DemandeDePlace(8, null, null, null, SANS_RISQUE), 5)).isInstanceOf(SaisieInvalide.class);
        assertThat(chercher(new DemandeDePlace(7, null, null, null, SANS_RISQUE), 5).propositions()).isEmpty();   // dimanche fermé
    }

    @Test
    void should_score_the_spacing_of_the_days() {
        assertThat(PropositionsDePlace.scoreEspacement(Set.of(LUNDI), 1)).isEqualTo(100);
        assertThat(PropositionsDePlace.scoreEspacement(EnumSet.of(LUNDI, MERCREDI, VENDREDI), 3)).isEqualTo(100);
        // 2 paires consecutives (-60), ecart max 5 pour un ideal de 3 (-16), schema non usuel (-3)
        assertThat(PropositionsDePlace.scoreEspacement(EnumSet.of(LUNDI, MARDI, MERCREDI), 3)).isEqualTo(21);
    }

    @Test
    void should_give_the_grid_with_zero_capacity_on_closed_days_and_the_occupation_of_the_rest() {
        List<CaseDeGrille> grille = PropositionsDePlace.grille(ressources(),
                List.of(occupation(patient(1), A, MATIN, A1, SANS_RISQUE, LUNDI)));

        assertThat(grille).hasSize(3 * 2 * 7);
        assertThat(grille).filteredOn(c -> c.jour() == DIMANCHE).allMatch(c -> c.capacite() == 0 && c.occupes() == 0);
        CaseDeGrille lundiMatinA = grille.stream().filter(c -> c.salle().equals(A) && c.creneau().equals(MATIN) && c.jour() == LUNDI)
                .findFirst().orElseThrow();
        assertThat(lundiMatinA.capacite()).isEqualTo(2);
        assertThat(lundiMatinA.occupes()).isEqualTo(1);
        assertThat(lundiMatinA.libres()).isEqualTo(1);
    }
}
