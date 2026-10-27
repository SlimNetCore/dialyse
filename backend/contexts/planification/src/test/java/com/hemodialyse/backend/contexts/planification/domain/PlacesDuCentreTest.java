package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.EvenementDePlacement.IsolementImpossible;
import com.hemodialyse.backend.contexts.planification.domain.EvenementDePlacement.PatientPlace;
import com.hemodialyse.backend.contexts.planification.domain.EvenementDePlacement.PatientReplaceEnIsolement;
import com.hemodialyse.backend.contexts.planification.domain.EvenementDePlacement.PlaceRetiree;
import com.hemodialyse.backend.contexts.planification.domain.EvenementDePlacement.PlacementModifie;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre.IssueDeReaffectation;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre.IssueDeRetour;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Generateur;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Salle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement.A_RISQUE;
import static com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement.SANS_RISQUE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.*;
import static com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Matrice invariant -> test des places du centre (tactique P1 a P8) : place complete, generateur unique par jour et creneau, isolement,
 * ressources du centre, placement inchange, reaffectation en isolement.
 */
class PlacesDuCentreTest {

    private static final PatientId PAT = patient(1);
    private static final PatientId AUTRE = patient(2);

    private static void refuse(PlacesDuCentre places, PatientId patient, Place place, CategorieDIsolement categorie, String code) {
        assertThatThrownBy(() -> places.placer(patient, Optional.of(place), categorie))
                .isInstanceOfSatisfying(RegleDePlanificationViolee.class, e -> assertThat(e.code()).isEqualTo(code));
        assertThat(places.changements()).isEmpty();
    }

    // ── P1/P2 : place complete ──

    @Test
    void should_refuse_a_partial_place_whichever_part_is_missing() {
        for (int manque = 0; manque < 4; manque++) {
            int m = manque;
            assertThatThrownBy(() -> Place.demandee(m == 0 ? null : A, m == 1 ? null : MATIN, m == 2 ? null : A1,
                    m == 3 ? Set.of() : Set.of(LUNDI)))
                    .isInstanceOfSatisfying(RegleDePlanificationViolee.class, e -> assertThat(e.code()).isEqualTo("PLACEMENT_INCOMPLET"));
        }
    }

    @Test
    void should_accept_no_place_at_all() {
        assertThat(Place.demandee(null, null, null, null)).isEmpty();
        assertThat(Place.demandee(null, null, null, Set.of())).isEmpty();
        PlacesDuCentre places = places();

        assertThat(places.placer(PAT, Optional.empty(), SANS_RISQUE)).isEmpty();
        assertThat(places.changements()).isEmpty();
    }

    @Test
    void should_place_a_patient_and_raise_patient_place() {
        PlacesDuCentre places = places();
        Place place = place(A, MATIN, A1, LUNDI, MERCREDI, VENDREDI);

        Optional<EvenementDePlacement> evenement = places.placer(PAT, Optional.of(place), SANS_RISQUE);

        assertThat(evenement).containsInstanceOf(PatientPlace.class);
        assertThat(places.changements()).containsExactly(new PlacesDuCentre.Changement(PAT, Optional.of(place)));
        assertThat(places.occupationDe(PAT)).get().satisfies(o -> assertThat(o.correspondA(place)).isTrue());
    }

    // ── P3 : un generateur ne sert qu'un patient par jour et par creneau ──

    @Test
    void should_refuse_a_second_patient_on_the_same_generator_slot_and_day() {
        PlacesDuCentre places = places(occupation(AUTRE, A, MATIN, A1, SANS_RISQUE, LUNDI, MERCREDI));

        refuse(places, PAT, place(A, MATIN, A1, MERCREDI), SANS_RISQUE, "PLACEMENT_GENERATEUR_OCCUPE");
    }

    @Test
    void should_accept_the_same_generator_on_another_slot_or_another_day() {
        PlacesDuCentre places = places(occupation(AUTRE, A, MATIN, A1, SANS_RISQUE, LUNDI));

        assertThat(places.placer(PAT, Optional.of(place(A, SOIR, A1, LUNDI)), SANS_RISQUE)).isPresent();
        assertThat(places.placer(patient(3), Optional.of(place(A, MATIN, A1, MARDI)), SANS_RISQUE)).isPresent();
    }

    @Test
    void should_refuse_when_a_patient_without_precise_generator_takes_the_last_place_of_the_room() {
        PlacesDuCentre places = places(occupation(AUTRE, B, MATIN, null, SANS_RISQUE, LUNDI));

        refuse(places, PAT, place(B, MATIN, B1, LUNDI), SANS_RISQUE, "PLACEMENT_GENERATEUR_OCCUPE");
    }

    @Test
    void should_free_the_current_place_of_the_patient_for_the_check() {
        PlacesDuCentre places = places(occupation(PAT, A, MATIN, A1, SANS_RISQUE, LUNDI));

        Optional<EvenementDePlacement> evenement = places.placer(PAT, Optional.of(place(A, MATIN, A1, LUNDI, MERCREDI)), SANS_RISQUE);

        assertThat(evenement).containsInstanceOf(PlacementModifie.class);
    }

    // ── P4 : isolement ──

    @Test
    void should_refuse_a_patient_at_risk_outside_an_isolation_room() {
        refuse(places(), PAT, place(A, MATIN, A1, LUNDI), A_RISQUE, "PLACEMENT_ISOLEMENT_REQUIS");
    }

    @Test
    void should_refuse_a_patient_without_risk_in_an_isolation_room() {
        refuse(places(), PAT, place(ISOLEMENT, MATIN, I1, LUNDI), SANS_RISQUE, "PLACEMENT_ISOLEMENT_RESERVE");
    }

    @Test
    void should_accept_a_patient_at_risk_in_an_isolation_room() {
        assertThat(places().placer(PAT, Optional.of(place(ISOLEMENT, MATIN, I1, LUNDI)), A_RISQUE)).containsInstanceOf(PatientPlace.class);
    }

    @Test
    void should_never_mix_risk_and_no_risk_on_a_generator_even_on_another_day() {
        PlacesDuCentre places = places(occupation(AUTRE, ISOLEMENT, MATIN, I1, SANS_RISQUE, LUNDI));

        refuse(places, PAT, place(ISOLEMENT, MATIN, I1, MARDI), A_RISQUE, "PLACEMENT_GENERATEUR_INCOMPATIBLE");
    }

    @Test
    void should_refuse_a_patient_without_risk_on_a_generator_dedicated_to_a_patient_at_risk() {
        PlacesDuCentre places = places(occupation(AUTRE, A, MATIN, A1, A_RISQUE, LUNDI));

        refuse(places, PAT, place(A, SOIR, A1, MARDI), SANS_RISQUE, "PLACEMENT_GENERATEUR_INCOMPATIBLE");
    }

    // ── P5 : ressources du centre ──

    @Test
    void should_refuse_a_room_a_slot_or_a_generator_unknown_to_the_centre() {
        PlacesDuCentre places = places();
        refuse(places, PAT, place(new Identifiants.SalleId(id(99)), MATIN, A1, LUNDI), SANS_RISQUE, "PLACEMENT_SALLE_INCONNUE");
        refuse(places, PAT, place(A, new Identifiants.CreneauId(id(98)), A1, LUNDI), SANS_RISQUE, "PLACEMENT_CRENEAU_INCONNU");
        refuse(places, PAT, place(A, MATIN, new Identifiants.GenerateurId(id(97)), LUNDI), SANS_RISQUE, "PLACEMENT_GENERATEUR_INCONNU");
    }

    @Test
    void should_refuse_a_generator_out_of_service_or_belonging_to_another_room() {
        PlacesDuCentre places = places();

        refuse(places, PAT, place(A, MATIN, HORS_SERVICE, LUNDI), SANS_RISQUE, "PLACEMENT_GENERATEUR_INCONNU");
        refuse(places, PAT, place(A, MATIN, B1, LUNDI), SANS_RISQUE, "PLACEMENT_GENERATEUR_INCONNU");
    }

    @Test
    void should_refuse_a_closed_day() {
        refuse(places(), PAT, place(A, MATIN, A1, DIMANCHE), SANS_RISQUE, "PLACEMENT_JOUR_FERME");
    }

    @Test
    void should_report_only_the_first_breach_in_the_code() {
        // salle inconnue ET jour ferme : le code est celui de la salle
        refuse(places(), PAT, place(new Identifiants.SalleId(id(99)), MATIN, A1, DIMANCHE), SANS_RISQUE, "PLACEMENT_SALLE_INCONNUE");
    }

    // ── P6 : placement inchange ──

    @Test
    void should_accept_an_unchanged_place_without_any_check_even_if_it_became_conflicting() {
        PlacesDuCentre places = places(occupation(PAT, A, MATIN, A1, SANS_RISQUE, LUNDI),
                occupation(AUTRE, A, MATIN, A1, SANS_RISQUE, LUNDI));

        assertThat(places.placer(PAT, Optional.of(place(A, MATIN, A1, LUNDI)), SANS_RISQUE)).isEmpty();
        assertThat(places.changements()).isEmpty();
    }

    @Test
    void should_check_a_modified_place_again() {
        PlacesDuCentre places = places(occupation(PAT, A, MATIN, A1, SANS_RISQUE, LUNDI),
                occupation(AUTRE, A, MATIN, A1, SANS_RISQUE, LUNDI, MARDI));

        refuse(places, PAT, place(A, MATIN, A1, LUNDI, MARDI), SANS_RISQUE, "PLACEMENT_GENERATEUR_OCCUPE");
    }

    @Test
    void should_check_the_place_of_a_patient_that_was_never_placed_before() {
        PlacesDuCentre places = places();
        places.connaitre(new Occupation(PAT, null, null, null, null, SANS_RISQUE));

        assertThat(places.placer(PAT, Optional.of(place(A, MATIN, A1, LUNDI)), SANS_RISQUE)).containsInstanceOf(PatientPlace.class);
    }

    @Test
    void should_know_the_current_place_of_a_patient_who_left_the_active_list() {
        PlacesDuCentre places = places();
        places.connaitre(occupation(PAT, A, MATIN, A1, SANS_RISQUE, DIMANCHE));

        assertThat(places.placer(PAT, Optional.of(place(A, MATIN, A1, DIMANCHE)), SANS_RISQUE)).isEmpty();
    }

    @Test
    void should_remove_the_place_when_the_card_no_longer_has_one() {
        PlacesDuCentre places = places(occupation(PAT, A, MATIN, A1, SANS_RISQUE, LUNDI));

        Optional<EvenementDePlacement> evenement = places.placer(PAT, Optional.empty(), SANS_RISQUE);

        assertThat(evenement).containsInstanceOf(PlaceRetiree.class);
        assertThat(places.changements()).containsExactly(new PlacesDuCentre.Changement(PAT, Optional.empty()));
        assertThat(places.occupationDe(PAT)).get().satisfies(o -> assertThat(o.estVide()).isTrue());
    }

    @Test
    void should_check_a_creation_without_writing_anything() {
        PlacesDuCentre places = places(occupation(AUTRE, A, MATIN, A1, SANS_RISQUE, LUNDI));

        places.verifierPourUneCreation(Optional.of(place(A, MATIN, A2, LUNDI)), SANS_RISQUE);
        assertThatThrownBy(() -> places.verifierPourUneCreation(Optional.of(place(A, MATIN, A1, LUNDI)), SANS_RISQUE))
                .isInstanceOfSatisfying(RegleDePlanificationViolee.class, e -> assertThat(e.code()).isEqualTo("PLACEMENT_GENERATEUR_OCCUPE"));
        assertThat(places.changements()).isEmpty();
    }

    // ── P8 : reaffectation en isolement ──

    @Test
    void should_not_move_a_patient_who_is_not_at_risk() {
        PlacesDuCentre places = places(occupation(PAT, A, MATIN, A1, SANS_RISQUE, LUNDI));

        assertThat(places.replacerEnIsolement(PAT, SANS_RISQUE).issue()).isEqualTo(IssueDeReaffectation.AUCUN_RISQUE);
        assertThat(places.changements()).isEmpty();
    }

    @Test
    void should_report_a_patient_without_place_or_without_days_as_not_placed() {
        assertThat(places().replacerEnIsolement(PAT, A_RISQUE).issue()).isEqualTo(IssueDeReaffectation.NON_PLACE);
        PlacesDuCentre sansJour = places(occupation(PAT, A, MATIN, A1, A_RISQUE));
        assertThat(sansJour.replacerEnIsolement(PAT, A_RISQUE).issue()).isEqualTo(IssueDeReaffectation.NON_PLACE);
    }

    @Test
    void should_leave_a_patient_already_in_a_conforming_isolation_place() {
        PlacesDuCentre places = places(occupation(PAT, ISOLEMENT, MATIN, I1, A_RISQUE, LUNDI, JEUDI));

        assertThat(places.replacerEnIsolement(PAT, A_RISQUE).issue()).isEqualTo(IssueDeReaffectation.DEJA_CONFORME);
        assertThat(places.changements()).isEmpty();
    }

    @Test
    void should_move_a_patient_to_isolation_keeping_the_days_and_the_slot() {
        PlacesDuCentre places = places(occupation(PAT, A, SOIR, A1, A_RISQUE, MARDI, JEUDI, SAMEDI));

        var resultat = places.replacerEnIsolement(PAT, A_RISQUE);

        assertThat(resultat.issue()).isEqualTo(IssueDeReaffectation.DEPLACE);
        assertThat(resultat.proposition().salle().id()).isEqualTo(ISOLEMENT);
        assertThat(resultat.proposition().creneau().id()).isEqualTo(SOIR);
        Place place = places.changements().getFirst().place().orElseThrow();
        assertThat(place.salle()).isEqualTo(ISOLEMENT);
        assertThat(place.creneau()).isEqualTo(SOIR);
        assertThat(place.jours()).containsExactlyInAnyOrder(MARDI, JEUDI, SAMEDI);
        assertThat(places.evenements()).singleElement().isInstanceOf(PatientReplaceEnIsolement.class);
    }

    @Test
    void should_change_the_slot_when_the_wished_one_is_taken() {
        PlacesDuCentre places = places(occupation(PAT, A, MATIN, A1, A_RISQUE, LUNDI),
                occupation(AUTRE, ISOLEMENT, MATIN, I1, A_RISQUE, LUNDI),
                occupation(patient(3), ISOLEMENT, MATIN, I2, A_RISQUE, LUNDI));

        var resultat = places.replacerEnIsolement(PAT, A_RISQUE);

        assertThat(resultat.issue()).isEqualTo(IssueDeReaffectation.DEPLACE);
        assertThat(resultat.proposition().creneau().id()).isEqualTo(SOIR);
    }

    @Test
    void should_keep_the_patient_in_place_and_raise_isolement_impossible_when_no_isolation_place_is_left() {
        PlacesDuCentre places = places(occupation(PAT, A, MATIN, A1, A_RISQUE, LUNDI),
                occupation(AUTRE, ISOLEMENT, MATIN, I1, A_RISQUE, LUNDI),
                occupation(patient(3), ISOLEMENT, MATIN, I2, A_RISQUE, LUNDI),
                occupation(patient(4), ISOLEMENT, SOIR, I1, A_RISQUE, LUNDI),
                occupation(patient(5), ISOLEMENT, SOIR, I2, A_RISQUE, LUNDI));

        var resultat = places.replacerEnIsolement(PAT, A_RISQUE);

        assertThat(resultat.issue()).isEqualTo(IssueDeReaffectation.A_REPLANIFIER);
        assertThat(places.changements()).isEmpty();
        assertThat(places.occupationDe(PAT)).get().satisfies(o -> assertThat(o.salle()).isEqualTo(A));
        assertThat(places.evenements()).singleElement().isInstanceOf(IsolementImpossible.class);
    }

    @Test
    void should_not_find_isolation_places_when_the_centre_has_no_isolation_room() {
        RessourcesDuCentre sans = new RessourcesDuCentre(List.of(new Salle(A, "A", false)), ressources().creneaux(),
                List.of(new Generateur(A1, "G", A)), OUVERTS, List.of());
        PlacesDuCentre places = PlacesDuCentre.de(CENTRE, sans, List.of(occupation(PAT, A, MATIN, A1, A_RISQUE, LUNDI)));

        assertThat(places.replacerEnIsolement(PAT, A_RISQUE).issue()).isEqualTo(IssueDeReaffectation.A_REPLANIFIER);
    }

    @Test
    void should_replan_a_patient_with_an_incomplete_inherited_place() {
        PlacesDuCentre places = places(occupation(PAT, A, null, null, A_RISQUE, LUNDI));

        assertThat(places.replacerEnIsolement(PAT, A_RISQUE).issue()).isEqualTo(IssueDeReaffectation.DEPLACE);
    }

    // ── Retour en salle ordinaire : propose, ne deplace jamais ──

    @Test
    void should_propose_the_return_to_an_ordinary_room_without_moving_the_patient() {
        PlacesDuCentre places = places(occupation(PAT, ISOLEMENT, MATIN, I1, SANS_RISQUE, LUNDI, JEUDI));

        var retour = places.proposerLeRetourEnSalleOrdinaire(PAT, SANS_RISQUE);

        assertThat(retour.issue()).isEqualTo(IssueDeRetour.PROPOSE);
        assertThat(retour.proposition().salle().isolement()).isFalse();
        assertThat(retour.proposition().creneau().id()).isEqualTo(MATIN);
        assertThat(places.changements()).isEmpty();
        assertThat(places.occupationDe(PAT)).get().satisfies(o -> assertThat(o.salle()).isEqualTo(ISOLEMENT));
    }

    @Test
    void should_not_propose_a_return_when_still_at_risk_or_already_in_an_ordinary_room() {
        PlacesDuCentre places = places(occupation(PAT, ISOLEMENT, MATIN, I1, A_RISQUE, LUNDI),
                occupation(AUTRE, A, MATIN, A1, SANS_RISQUE, LUNDI));

        assertThat(places.proposerLeRetourEnSalleOrdinaire(PAT, A_RISQUE).issue()).isEqualTo(IssueDeRetour.NON_CONCERNE);
        assertThat(places.proposerLeRetourEnSalleOrdinaire(AUTRE, SANS_RISQUE).issue()).isEqualTo(IssueDeRetour.NON_CONCERNE);
        assertThat(places.proposerLeRetourEnSalleOrdinaire(patient(9), SANS_RISQUE).issue()).isEqualTo(IssueDeRetour.NON_CONCERNE);
    }

    // ── Lectures ──

    @Test
    void should_list_the_patients_whose_usual_place_is_a_generator() {
        PlacesDuCentre places = places(occupation(PAT, A, MATIN, A1, SANS_RISQUE, LUNDI),
                occupation(AUTRE, A, SOIR, A1, SANS_RISQUE, MARDI), occupation(patient(3), A, MATIN, A2, SANS_RISQUE, LUNDI));

        assertThat(places.patientsSur(A1)).containsExactlyInAnyOrder(PAT, AUTRE);
        assertThat(places.patientsSur(B1)).isEmpty();
    }

    @Test
    void should_compute_the_weekly_capacity() {
        PlacesDuCentre places = places(occupation(PAT, A, MATIN, A1, SANS_RISQUE, LUNDI, MERCREDI));

        var capacite = places.capacite();

        assertThat(capacite.generateursEnService()).isEqualTo(5);
        assertThat(capacite.creneaux()).isEqualTo(2);
        assertThat(capacite.joursOuverts()).isEqualTo(6);
        assertThat(capacite.places()).isEqualTo(5 * 2 * 6);
        assertThat(capacite.occupees()).isEqualTo(2);
        assertThat(capacite.libres()).isEqualTo(58);
    }

    @Test
    void should_read_without_a_patient_as_if_the_patient_was_not_there() {
        PlacesDuCentre places = places(occupation(PAT, A, MATIN, A1, SANS_RISQUE, LUNDI));

        assertThat(places.sans(PAT).occupationDe(PAT)).isEmpty();
        assertThat(places.occupationDe(PAT)).isPresent();
    }
}
