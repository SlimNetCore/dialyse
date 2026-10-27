package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.doubles.PlacesEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Occupation;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteDesCentres.CapaciteDuCentre;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacementDesPatients;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacesDesPatients.PlaceDuPatient;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ReaffectationEnIsolement.Issue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

import static com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement.A_RISQUE;
import static com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement.SANS_RISQUE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.*;
import static com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cas d'usage du placement avec le double en memoire : placement depuis la fiche (verrou, refus, ecriture), reaffectation en isolement
 * dans la transaction de la serologie, alerte de generateur indisponible idempotente, propositions, lectures publiees.
 */
class PlacementApplicationTest {

    static final PatientId PAT = patient(1);
    static final PatientId AUTRE = patient(2);
    static final Set<DayOfWeek> LUN = Set.of(DayOfWeek.MONDAY);

    PlacesEnMemoire monde;
    PlacerLePatient placer;
    ReaffecterEnIsolementSiRisque reaffecter;
    AlerterUnGenerateurIndisponible alerter;
    ProposerDesPlaces proposer;

    @BeforeEach
    void preparer() {
        monde = new PlacesEnMemoire(ressources());
        placer = new PlacerLePatient(monde, monde, monde, monde, monde, monde);
        reaffecter = new ReaffecterEnIsolementSiRisque(monde, monde, monde, monde, monde, monde, monde, monde, monde);
        alerter = new AlerterUnGenerateurIndisponible(monde, monde, monde, monde, monde);
        proposer = new ProposerDesPlaces(monde, monde, monde, monde, monde);
    }

    static Occupation occupe(PatientId p, com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId salle,
                             com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId creneau,
                             com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId generateur,
                             com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine... jours) {
        return occupation(p, salle, creneau, generateur, SANS_RISQUE, jours);
    }

    // ── PlacerLePatient ──

    @Test
    void should_accept_a_card_without_place_without_taking_the_lock() {
        monde.patient(PAT, "DUPONT Anne", null);

        placer.verifier(CENTRE.valeur(), PAT.valeur(), null, null, null, Set.of());

        assertThat(monde.verrous).isEmpty();
        assertThat(monde.ecritures).isZero();
    }

    @Test
    void should_check_a_creation_under_the_lock_and_write_nothing() {
        monde.patient(AUTRE, "MARTIN Paul", occupe(AUTRE, A, MATIN, A1, LUNDI));

        placer.verifier(CENTRE.valeur(), null, A.valeur(), MATIN.valeur(), A2.valeur(), LUN);

        assertThat(monde.verrous).containsExactly(CENTRE);
        assertThat(monde.ecritures).isZero();
        assertThatThrownBy(() -> placer.verifier(CENTRE.valeur(), null, A.valeur(), MATIN.valeur(), A1.valeur(), LUN))
                .isInstanceOfSatisfying(PlacementDesPatients.Refus.class, e -> assertThat(e.code()).isEqualTo("PLACEMENT_GENERATEUR_OCCUPE"));
    }

    @Test
    void should_place_the_patient_under_the_lock_and_write_only_the_place_columns() {
        monde.patient(PAT, "DUPONT Anne", null);

        placer.placer(CENTRE.valeur(), PAT.valeur(), A.valeur(), MATIN.valeur(), A1.valeur(), Set.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY));

        assertThat(monde.verrous).containsExactly(CENTRE);
        assertThat(monde.fiches.get(PAT).correspondA(place(A, MATIN, A1, LUNDI, JEUDI))).isTrue();
        assertThat(monde.ecritures).isEqualTo(1);
    }

    @Test
    void should_remove_the_place_when_the_card_has_none_anymore() {
        monde.patient(PAT, "DUPONT Anne", occupe(PAT, A, MATIN, A1, LUNDI));

        placer.placer(CENTRE.valeur(), PAT.valeur(), null, null, null, null);

        assertThat(monde.fiches.get(PAT).estVide()).isTrue();
    }

    @Test
    void should_refuse_a_partial_place_with_a_stable_code() {
        monde.patient(PAT, "DUPONT Anne", null);

        assertThatThrownBy(() -> placer.placer(CENTRE.valeur(), PAT.valeur(), A.valeur(), null, A1.valeur(), LUN))
                .isInstanceOfSatisfying(PlacementDesPatients.Refus.class, e -> assertThat(e.code()).isEqualTo("PLACEMENT_INCOMPLET"));
        assertThat(monde.ecritures).isZero();
    }

    @Test
    void should_deduce_the_risk_from_the_serologies_and_refuse_an_ordinary_room() {
        monde.patient(PAT, "DUPONT Anne", null);
        monde.serologiePositive.add(PAT);

        assertThatThrownBy(() -> placer.placer(CENTRE.valeur(), PAT.valeur(), A.valeur(), MATIN.valeur(), A1.valeur(), LUN))
                .isInstanceOfSatisfying(PlacementDesPatients.Refus.class, e -> assertThat(e.code()).isEqualTo("PLACEMENT_ISOLEMENT_REQUIS"));
        placer.placer(CENTRE.valeur(), PAT.valeur(), ISOLEMENT.valeur(), MATIN.valeur(), I1.valeur(), LUN);
        assertThat(monde.fiches.get(PAT).salle()).isEqualTo(ISOLEMENT);
    }

    @Test
    void should_refuse_a_healthy_patient_in_the_isolation_room() {
        monde.patient(PAT, "DUPONT Anne", null);

        assertThatThrownBy(() -> placer.placer(CENTRE.valeur(), PAT.valeur(), ISOLEMENT.valeur(), MATIN.valeur(), I1.valeur(), LUN))
                .isInstanceOfSatisfying(PlacementDesPatients.Refus.class, e -> assertThat(e.code()).isEqualTo("PLACEMENT_ISOLEMENT_RESERVE"));
    }

    @Test
    void should_accept_an_unchanged_place_even_when_it_became_conflicting() {
        monde.patient(PAT, "DUPONT Anne", occupe(PAT, A, MATIN, A1, LUNDI));
        monde.patient(AUTRE, "MARTIN Paul", occupe(AUTRE, A, MATIN, A1, LUNDI));

        placer.placer(CENTRE.valeur(), PAT.valeur(), A.valeur(), MATIN.valeur(), A1.valeur(), LUN);

        assertThat(monde.ecritures).isZero();
    }

    @Test
    void should_know_the_unchanged_place_of_a_patient_who_left_the_active_list() {
        monde.patient(PAT, "DUPONT Anne", occupe(PAT, A, MATIN, A1, DIMANCHE));
        monde.sortis.add(PAT);

        placer.placer(CENTRE.valeur(), PAT.valeur(), A.valeur(), MATIN.valeur(), A1.valeur(), Set.of(DayOfWeek.SUNDAY));

        assertThat(monde.ecritures).isZero();
    }

    @Test
    void should_refuse_a_patient_of_another_centre_or_unknown() {
        monde.patient(PAT, "DUPONT Anne", null);
        monde.autreCentre.add(PAT);

        assertThatThrownBy(() -> placer.placer(CENTRE.valeur(), PAT.valeur(), A.valeur(), MATIN.valeur(), A1.valeur(), LUN))
                .isInstanceOfSatisfying(PlacementDesPatients.Refus.class, e -> assertThat(e.code()).isEqualTo("PLACEMENT_PATIENT_INTROUVABLE"));
        assertThatThrownBy(() -> placer.placer(CENTRE.valeur(), patient(77).valeur(), A.valeur(), MATIN.valeur(), A1.valeur(), LUN))
                .isInstanceOf(PlacementDesPatients.Refus.class);
    }

    @Test
    void should_answer_409_when_the_lock_of_the_centre_is_not_obtained_and_write_nothing() {
        monde.patient(PAT, "DUPONT Anne", null);
        monde.verrouRefuse = true;

        assertThatThrownBy(() -> placer.placer(CENTRE.valeur(), PAT.valeur(), A.valeur(), MATIN.valeur(), A1.valeur(), LUN))
                .isInstanceOf(PlacementDesPatients.EcritureConcurrente.class);
        assertThatThrownBy(() -> placer.verifier(CENTRE.valeur(), null, A.valeur(), MATIN.valeur(), A1.valeur(), LUN))
                .isInstanceOf(PlacementDesPatients.EcritureConcurrente.class);
        assertThat(monde.ecritures).isZero();
    }

    // ── ReaffecterEnIsolementSiRisque ──

    @Test
    void should_leave_a_patient_without_risk_alone() {
        monde.patient(PAT, "DUPONT Anne", occupe(PAT, A, MATIN, A1, LUNDI));

        assertThat(reaffecter.siRisque(CENTRE.valeur(), PAT.valeur())).isEqualTo(Issue.AUCUN_RISQUE);
        assertThat(monde.alertes).isEmpty();
        assertThat(monde.ecritures).isZero();
    }

    @Test
    void should_refuse_a_serology_of_a_patient_of_another_centre_before_taking_the_lock() {
        monde.patient(PAT, "DUPONT Anne", occupe(PAT, A, MATIN, A1, LUNDI));
        monde.autreCentre.add(PAT);

        assertThatThrownBy(() -> reaffecter.siRisque(CENTRE.valeur(), PAT.valeur()))
                .isInstanceOfSatisfying(PlacementDesPatients.Refus.class, e -> assertThat(e.code()).isEqualTo("PLACEMENT_PATIENT_INTROUVABLE"));
        assertThat(monde.verrous).isEmpty();
    }

    @Test
    void should_move_a_patient_who_became_at_risk_and_notify_with_the_room() {
        monde.patient(PAT, "DUPONT Anne", occupe(PAT, A, SOIR, A1, MARDI, JEUDI));
        monde.serologiePositive.add(PAT);

        assertThat(reaffecter.siRisque(CENTRE.valeur(), PAT.valeur())).isEqualTo(Issue.DEPLACE);

        assertThat(monde.fiches.get(PAT).salle()).isEqualTo(ISOLEMENT);
        assertThat(monde.fiches.get(PAT).jours()).containsExactlyInAnyOrder(MARDI, JEUDI);
        assertThat(monde.alertes).containsExactly("REPLACE_ISOLEMENT|DUPONT Anne|Isolement");
        assertThat(monde.verrous).containsExactly(CENTRE);
    }

    @Test
    void should_roll_back_the_move_with_the_serology_when_the_caller_transaction_fails() {
        monde.patient(PAT, "DUPONT Anne", occupe(PAT, A, SOIR, A1, MARDI));
        monde.serologiePositive.add(PAT);

        assertThatThrownBy(() -> monde.executer(() -> {
            reaffecter.siRisque(CENTRE.valeur(), PAT.valeur());
            throw new IllegalStateException("enregistrement de la serologie impossible");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(monde.fiches.get(PAT).salle()).isEqualTo(A);
        assertThat(monde.alertes).isEmpty();
    }

    @Test
    void should_alert_once_per_day_when_no_isolation_place_is_left() {
        monde.patient(PAT, "DUPONT Anne", occupe(PAT, A, MATIN, A1, LUNDI));
        monde.serologiePositive.add(PAT);
        monde.patient(patient(3), "X", occupation(patient(3), ISOLEMENT, MATIN, I1, A_RISQUE, LUNDI));
        monde.patient(patient(4), "Y", occupation(patient(4), ISOLEMENT, MATIN, I2, A_RISQUE, LUNDI));
        monde.patient(patient(5), "Z", occupation(patient(5), ISOLEMENT, SOIR, I1, A_RISQUE, LUNDI));
        monde.patient(patient(6), "W", occupation(patient(6), ISOLEMENT, SOIR, I2, A_RISQUE, LUNDI));
        monde.serologiePositive.addAll(List.of(patient(3), patient(4), patient(5), patient(6)));

        assertThat(reaffecter.siRisque(CENTRE.valeur(), PAT.valeur())).isEqualTo(Issue.A_REPLANIFIER);
        assertThat(reaffecter.siRisque(CENTRE.valeur(), PAT.valeur())).isEqualTo(Issue.A_REPLANIFIER);
        assertThat(monde.alertes).containsExactly("ISOLEMENT_IMPOSSIBLE|DUPONT Anne");
        assertThat(monde.fiches.get(PAT).salle()).isEqualTo(A);

        monde.aujourdhui = monde.aujourdhui.plusDays(1);
        reaffecter.siRisque(CENTRE.valeur(), PAT.valeur());
        assertThat(monde.alertes).hasSize(2);
    }

    @Test
    void should_propose_the_return_to_an_ordinary_room_after_a_negative_result_and_never_move_automatically() {
        monde.patient(PAT, "DUPONT Anne", occupe(PAT, ISOLEMENT, MATIN, I1, LUNDI, JEUDI));

        assertThat(reaffecter.siRisque(CENTRE.valeur(), PAT.valeur())).isEqualTo(Issue.RETOUR_PROPOSE);
        assertThat(reaffecter.siRisque(CENTRE.valeur(), PAT.valeur())).isEqualTo(Issue.RETOUR_PROPOSE);

        assertThat(monde.fiches.get(PAT).salle()).isEqualTo(ISOLEMENT);
        assertThat(monde.ecritures).isZero();
        assertThat(monde.alertes).hasSize(1).first().asString().startsWith("RETOUR_PROPOSE|DUPONT Anne|Salle ");
    }

    // ── AlerterUnGenerateurIndisponible ──

    private void sept_patients_sur_le_generateur() {
        for (int i = 0; i < 7; i++) {
            PatientId p = patient(10 + i);
            monde.patient(p, "NOM" + i + " Prenom", occupe(p, A, MATIN, A1, i % 2 == 0 ? LUNDI : MARDI));
        }
    }

    @Test
    void should_alert_with_the_five_first_patients_and_the_total_without_moving_anybody() {
        sept_patients_sur_le_generateur();

        boolean emise = alerter.executer(CENTRE, A1, "G-A1", "EN_SERVICE", "HORS_SERVICE");

        assertThat(emise).isTrue();
        assertThat(monde.alertes).containsExactly("GENERATEUR_INDISPONIBLE|G-A1|HORS_SERVICE|7|NOM0 Prenom, NOM1 Prenom, NOM2 Prenom, NOM3 Prenom, NOM4 Prenom");
        assertThat(monde.ecritures).isZero();
        assertThat(monde.fiches.values()).allMatch(o -> A.equals(o.salle()) && A1.equals(o.generateur()));
    }

    @Test
    void should_be_idempotent_per_generator_and_day() {
        sept_patients_sur_le_generateur();

        assertThat(alerter.executer(CENTRE, A1, "G-A1", "EN_SERVICE", "HORS_SERVICE")).isTrue();
        assertThat(alerter.executer(CENTRE, A1, "G-A1", "EN_SERVICE", "HORS_SERVICE")).isFalse();
        assertThat(alerter.executer(CENTRE, A2, "G-A2", "EN_SERVICE", "HORS_SERVICE")).isTrue();
        monde.aujourdhui = monde.aujourdhui.plusDays(1);
        assertThat(alerter.executer(CENTRE, A1, "G-A1", "EN_SERVICE", "REFORME")).isTrue();

        assertThat(monde.alertes).hasSize(3);
    }

    @Test
    void should_alert_even_without_patient_and_ignore_the_patients_who_left() {
        monde.patient(PAT, "DUPONT Anne", occupe(PAT, A, MATIN, A1, LUNDI));
        monde.sortis.add(PAT);

        alerter.executer(CENTRE, A1, "G-A1", "EN_SERVICE", "EN_MAINTENANCE");

        assertThat(monde.alertes).containsExactly("GENERATEUR_INDISPONIBLE|G-A1|EN_MAINTENANCE|0|");
    }

    @Test
    void should_not_alert_for_a_creation_a_return_to_service_or_an_already_unavailable_generator() {
        assertThat(alerter.executer(CENTRE, A1, "G-A1", null, "HORS_SERVICE")).isFalse();
        assertThat(alerter.executer(CENTRE, A1, "G-A1", "EN_SERVICE", "EN_SERVICE")).isFalse();
        assertThat(alerter.executer(CENTRE, A1, "G-A1", "HORS_SERVICE", "REFORME")).isFalse();

        assertThat(monde.alertes).isEmpty();
        assertThat(monde.transactions).isZero();
    }

    // ── ProposerDesPlaces ──

    private ProposerDesPlaces.Commande demande(PatientId patient, Boolean isolement) {
        return new ProposerDesPlaces.Commande(CENTRE, patient, 3, null, null, null, 12, isolement);
    }

    @Test
    void should_propose_places_and_the_grid_without_taking_any_lock() {
        ProposerDesPlaces.Reponse reponse = proposer.executer(demande(null, null));

        assertThat(reponse.propositions()).isNotEmpty();
        assertThat(reponse.grille()).isNotEmpty();
        assertThat(reponse.patientARisque()).isFalse();
        assertThat(monde.verrous).isEmpty();
    }

    @Test
    void should_deduce_the_risk_of_the_patient_unless_the_caller_forces_it() {
        monde.patient(PAT, "DUPONT Anne", null);
        monde.serologiePositive.add(PAT);

        assertThat(proposer.executer(demande(PAT, null)).patientARisque()).isTrue();
        assertThat(proposer.executer(demande(PAT, null)).propositions()).allMatch(p -> p.salle().id().equals(ISOLEMENT));
        assertThat(proposer.executer(demande(PAT, false)).patientARisque()).isFalse();
        assertThat(proposer.executer(demande(null, true)).patientARisque()).isTrue();
    }

    @Test
    void should_free_the_current_place_of_the_patient_to_modify() {
        RessourcesPleines.remplirLeCentre(monde);
        monde.patient(PAT, "DUPONT Anne", occupe(PAT, B, SOIR, B1, LUNDI, MERCREDI, VENDREDI));

        assertThat(proposer.executer(new ProposerDesPlaces.Commande(CENTRE, null, 3, Set.of(LUNDI, MERCREDI, VENDREDI), null, null, 12, false))
                .propositions()).isEmpty();
        assertThat(proposer.executer(new ProposerDesPlaces.Commande(CENTRE, PAT, 3, Set.of(LUNDI, MERCREDI, VENDREDI), null, null, 12, false))
                .propositions()).hasSize(1);
    }

    // ── Lectures publiees ──

    @Test
    void should_publish_the_place_of_a_card_and_of_the_centre() {
        monde.patient(PAT, "DUPONT Anne", occupe(PAT, A, MATIN, A1, LUNDI, JEUDI));
        monde.patient(AUTRE, "MARTIN Paul", null);
        ConsulterLesPlacesDesPatients lecture = new ConsulterLesPlacesDesPatients(monde);

        assertThat(lecture.deLaFiche(CENTRE.valeur(), PAT.valeur())).get().satisfies(p -> {
            assertThat(p.salle()).isEqualTo(A.valeur());
            assertThat(p.generateur()).isEqualTo(A1.valeur());
            assertThat(p.jours()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.THURSDAY);
        });
        assertThat(lecture.deLaFiche(CENTRE.valeur(), AUTRE.valeur())).isEmpty();
        assertThat(lecture.duCentre(CENTRE.valeur(), monde.aujourdhui)).extracting(PlaceDuPatient::patient).containsExactly(PAT.valeur());
    }

    @Test
    void should_publish_the_weekly_capacity() {
        monde.patient(PAT, "DUPONT Anne", occupe(PAT, A, MATIN, A1, LUNDI, JEUDI));

        CapaciteDuCentre capacite = new ConsulterLaCapacite(monde, monde, monde).duCentre(CENTRE.valeur(), monde.aujourdhui);

        assertThat(capacite.generateursEnService()).isEqualTo(5);
        assertThat(capacite.places()).isEqualTo(60);
        assertThat(capacite.occupees()).isEqualTo(2);
        assertThat(capacite.libres()).isEqualTo(58);
    }

    /** Remplit tout le centre le lundi, le mercredi et le vendredi, sauf un seul creneau de la salle B (sans le patient modifie). */
    static final class RessourcesPleines {
        static void remplirLeCentre(PlacesEnMemoire monde) {
            int n = 100;
            for (var salleEtGenerateurs : List.of(
                    new Object[]{A, A1}, new Object[]{A, A2}, new Object[]{B, B1}, new Object[]{ISOLEMENT, I1}, new Object[]{ISOLEMENT, I2})) {
                for (var creneau : List.of(MATIN, SOIR)) {
                    if (salleEtGenerateurs[1] == B1 && creneau == SOIR) continue;
                    PatientId p = patient(n++);
                    monde.patient(p, "P" + n, occupation(p,
                            (com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId) salleEtGenerateurs[0], creneau,
                            (com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId) salleEtGenerateurs[1],
                            salleEtGenerateurs[0] == ISOLEMENT ? A_RISQUE : SANS_RISQUE, LUNDI, MERCREDI, VENDREDI));
                    if (salleEtGenerateurs[0] == ISOLEMENT) monde.serologiePositive.add(p);
                }
            }
        }
    }
}
