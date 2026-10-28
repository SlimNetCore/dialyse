package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.PatientAPlacer;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementDePatient;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.LMV;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.poste;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A2;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.CENTRE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.I1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.ISOLEMENT;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.MATIN;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.SOIR;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.patient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P22 : les nouvelles places d'une proposition sont jugées face à l'ÉTAT FINAL, tout ou rien (PlacesDuCentre.replacerLesPatients),
 * et la vérification des déplacements (VerificationDesDeplacements) rend le même verdict.
 */
class ReplacementDesPatientsDUnePropositionTest {

    private static final PatientId P1 = patient(1);
    private static final PatientId P2 = patient(2);
    private static final PatientId P3 = patient(3);

    private static Occupation occupation(PatientId p, Poste poste, CategorieDIsolement c) {
        return new Occupation(p, poste.salle(), poste.creneau(), poste.generateur(), LMV, c);
    }

    private static PlacesDuCentre places(Occupation... occupations) {
        return PlacesDuCentre.de(CENTRE, JeuDePlaces.ressources(), List.of(occupations));
    }

    private static String code(Runnable action) {
        try {
            action.run();
        } catch (RegleDePlanificationViolee e) {
            return e.code();
        }
        return null;
    }

    @Test
    void should_allow_two_patients_to_swap_their_generators_because_the_final_state_is_judged() {
        PlacesDuCentre places = places(occupation(P1, poste(A, MATIN, A1), CategorieDIsolement.SANS_RISQUE),
                occupation(P2, poste(A, MATIN, A2), CategorieDIsolement.SANS_RISQUE));

        places.replacerLesPatients(Map.of(P1, new Place(A, MATIN, A2, LMV), P2, new Place(A, MATIN, A1, LMV)),
                Map.of(P1, CategorieDIsolement.SANS_RISQUE, P2, CategorieDIsolement.SANS_RISQUE));

        assertThat(places.changements()).hasSize(2);
        assertThat(places.occupationDe(P1).orElseThrow().generateur()).isEqualTo(A2);
        assertThat(places.occupationDe(P2).orElseThrow().generateur()).isEqualTo(A1);
        assertThat(places.evenements()).hasSize(2);
    }

    @Test
    void should_write_the_new_days_with_the_place() {
        PlacesDuCentre places = places(occupation(P1, poste(A, MATIN, A1), CategorieDIsolement.SANS_RISQUE));
        Set<JourDeLaSemaine> mardiJeudiSamedi = EnumSet.of(JourDeLaSemaine.MARDI, JourDeLaSemaine.JEUDI, JourDeLaSemaine.SAMEDI);

        places.replacerLesPatients(Map.of(P1, new Place(A, MATIN, A1, mardiJeudiSamedi)), Map.of());

        assertThat(places.changements().getFirst().place().orElseThrow().jours()).isEqualTo(mardiJeudiSamedi);
    }

    @Test
    void should_place_a_patient_who_had_no_place_and_raise_the_placement_event() {
        PlacesDuCentre places = places(new Occupation(P1, null, null, null, null, null));

        places.replacerLesPatients(Map.of(P1, new Place(B, SOIR, B1, LMV)), Map.of());

        assertThat(places.evenements()).singleElement().isInstanceOf(EvenementDePlacement.PatientPlace.class);
    }

    @Test
    void should_refuse_everything_when_one_place_is_taken_by_a_patient_who_stays() {
        PlacesDuCentre places = places(occupation(P1, poste(A, MATIN, A1), CategorieDIsolement.SANS_RISQUE),
                occupation(P2, poste(A, MATIN, A2), CategorieDIsolement.SANS_RISQUE),
                occupation(P3, poste(B, SOIR, B1), CategorieDIsolement.SANS_RISQUE));

        String refus = code(() -> places.replacerLesPatients(
                Map.of(P1, new Place(B, MATIN, B1, LMV), P2, new Place(B, SOIR, B1, LMV)), Map.of()));

        assertThat(refus).isEqualTo("PLACEMENT_GENERATEUR_OCCUPE");
        assertThat(places.changements()).isEmpty();
        assertThat(places.occupationDe(P1).orElseThrow().generateur()).isEqualTo(A1);
        assertThat(places.evenements()).isEmpty();
    }

    @Test
    void should_refuse_a_patient_at_risk_outside_an_isolation_room_and_one_without_risk_inside() {
        PlacesDuCentre places = places(occupation(P1, poste(A, MATIN, A1), CategorieDIsolement.A_RISQUE));
        assertThat(code(() -> places.replacerLesPatients(Map.of(P1, new Place(B, MATIN, B1, LMV)),
                Map.of(P1, CategorieDIsolement.A_RISQUE)))).isEqualTo("PLACEMENT_ISOLEMENT_REQUIS");

        PlacesDuCentre autres = places(occupation(P2, poste(A, MATIN, A1), CategorieDIsolement.SANS_RISQUE));
        assertThat(code(() -> autres.replacerLesPatients(Map.of(P2, new Place(ISOLEMENT, MATIN, I1, LMV)),
                Map.of(P2, CategorieDIsolement.SANS_RISQUE)))).isEqualTo("PLACEMENT_ISOLEMENT_RESERVE");
    }

    @Test
    void should_refuse_an_unknown_room_and_a_closed_day() {
        PlacesDuCentre places = places(occupation(P1, poste(A, MATIN, A1), CategorieDIsolement.SANS_RISQUE));
        assertThat(code(() -> places.replacerLesPatients(Map.of(P1, new Place(new Identifiants.SalleId(JeuDePlaces.id(99)), MATIN, A1,
                LMV)), Map.of()))).isEqualTo("PLACEMENT_SALLE_INCONNUE");
        assertThat(code(() -> places.replacerLesPatients(Map.of(P1, new Place(A, MATIN, A1, EnumSet.of(JourDeLaSemaine.DIMANCHE))),
                Map.of()))).isEqualTo("PLACEMENT_JOUR_FERME");
    }

    // ── VerificationDesDeplacements : même juge, sur les données de l'optimisation ──

    private static DonneesDOptimisation donnees() {
        return new JeuDOptimisation()
                .patient(1, LMV, false, poste(A, MATIN, A1))
                .patient(2, LMV, false, poste(A, MATIN, A2))
                .patient(3, LMV, true, null)
                .donnees();
    }

    private static DeplacementDePatient vers(PatientId p, Poste poste) {
        return new DeplacementDePatient(p, "?", null, poste);
    }

    @Test
    void should_accept_moves_that_are_consistent_in_the_final_state() {
        List<DeplacementDePatient> echange = List.of(vers(P1, poste(A, MATIN, A2)), vers(P2, poste(A, MATIN, A1)));
        assertThat(VerificationDesDeplacements.verifier(donnees(), echange)).isEmpty();
    }

    @Test
    void should_name_the_broken_rules_with_the_first_one_leading() {
        Map<PatientId, List<String>> refus = VerificationDesDeplacements.verifier(donnees(), List.of(vers(P3, poste(A, MATIN, A1))));
        assertThat(refus).containsOnlyKeys(P3);
        assertThat(refus.get(P3).getFirst()).isEqualTo("ISOLEMENT_REQUIS");
        assertThat(refus.get(P3)).contains("GENERATEUR_OCCUPE");
    }

    @Test
    void should_refuse_an_incomplete_place_and_an_unknown_patient_as_incomplete() {
        PatientId inconnu = patient(99);
        Map<PatientId, List<String>> refus = VerificationDesDeplacements.verifier(donnees(),
                List.of(vers(P1, new Poste(A, null, A1, "G")), vers(inconnu, poste(A, MATIN, A1))));
        assertThat(refus.get(P1)).containsExactly("INCOMPLET");
        assertThat(refus.get(inconnu)).containsExactly("INCOMPLET");
    }

    @Test
    void should_refuse_a_patient_without_days_as_incomplete_unless_the_proposition_chooses_them() {
        DonneesDOptimisation sansJours = new JeuDOptimisation().patient(1, Set.of(), false, null).donnees();
        assertThat(VerificationDesDeplacements.verifier(sansJours, List.of(vers(P1, poste(A, MATIN, A1))))).containsKey(P1);

        DeplacementDePatient avecJours = new DeplacementDePatient(P1, "?", null, poste(A, MATIN, A1),
                List.of(JourDeLaSemaine.LUNDI, JourDeLaSemaine.JEUDI));
        assertThat(VerificationDesDeplacements.verifier(sansJours, List.of(avecJours))).isEmpty();
    }

    @Test
    void should_use_the_days_chosen_by_the_proposition_when_judging_the_generator() {
        // P2 occupe A2 le matin lundi-mercredi-vendredi ; P1 choisit mardi-jeudi-samedi sur A2 : pas de conflit de jours
        DeplacementDePatient autresJours = new DeplacementDePatient(P1, "?", null, poste(A, MATIN, A2),
                List.of(JourDeLaSemaine.MARDI, JourDeLaSemaine.JEUDI, JourDeLaSemaine.SAMEDI));
        assertThat(VerificationDesDeplacements.verifier(donnees(), List.of(autresJours))).isEmpty();
    }

    @Test
    void should_drop_invalid_moves_until_the_rest_holds_together() {
        // P3 (à risque) vers A1 est invalide ; P1 vers B1 est valide seulement si P3 n'est pas là : retiré en cascade n'est pas requis
        List<DeplacementDePatient> propositions = List.of(vers(P3, poste(A, MATIN, A1)), vers(P1, poste(B, MATIN, B1)));
        List<DeplacementDePatient> retenus = VerificationDesDeplacements.retirerInvalides(donnees(), propositions);
        assertThat(retenus).extracting(DeplacementDePatient::patient).containsExactly(P1);
    }

    @Test
    void should_keep_the_current_days_of_a_patient_when_the_proposition_chooses_none() {
        PatientAPlacer p1 = donnees().patients().getFirst();
        assertThat(VerificationDesDeplacements.joursFinaux(p1, vers(P1, poste(A, MATIN, A1)))).isEqualTo(LMV);
        assertThat(VerificationDesDeplacements.joursFinaux(p1, new DeplacementDePatient(P1, "?", null, poste(A, MATIN, A1),
                List.of(JourDeLaSemaine.SAMEDI)))).containsExactly(JourDeLaSemaine.SAMEDI);
    }

    @Test
    void should_refuse_a_null_centre_in_the_data() {
        assertThatThrownBy(() -> new DonneesDOptimisation(null, JeuDePlaces.ressources(), new Presence.Personnel(List.of(), List.of(),
                List.of(), List.of(), RatioDeLaPresence.depuisLaBase(null)), List.of(), Map.of(), List.of(), List.of(), List.of(),
                List.of())).isInstanceOf(SaisieInvalide.class);
    }
}
