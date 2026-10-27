package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Fermeture;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.Case;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.DeplacementTemporaire;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.Donnees;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.PatientDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.SeanceRealisee;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.Semaine;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.TypeDeConflit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A2;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.MATIN;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.SOIR;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.patient;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.ressources;
import static org.assertj.core.api.Assertions.assertThat;

/** Le planning de la semaine (RG-PLN-040 à 045) : la règle de présence unique, la capacité des jours fermés, les conflits. */
class SemaineDuPlanningTest {

    /** Un dimanche : début de la semaine. */
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 3, 1);
    private static final LocalDate LUNDI = DIMANCHE.plusDays(1);
    private static final LocalDate MARDI = DIMANCHE.plusDays(2);

    private static PatientDeLaSemaine patientPlace(int n, String nom, GenerateurChoix g, CategorieDIsolement categorie,
                                                   SejourDuPatient sejour, JourDeLaSemaine... jours) {
        Set<JourDeLaSemaine> j = EnumSet.noneOf(JourDeLaSemaine.class);
        j.addAll(List.of(jours));
        return new PatientDeLaSemaine(patient(n), nom, new Occupation(patient(n), g.salle, MATIN, g.generateur, j, categorie), sejour);
    }

    private record GenerateurChoix(Identifiants.SalleId salle, Identifiants.GenerateurId generateur) {
    }

    private static final GenerateurChoix SALLE_A_G1 = new GenerateurChoix(A, A1);

    private static Semaine semaine(RessourcesDuCentre ressources, List<PatientDeLaSemaine> patients) {
        return SemaineDuPlanning.construire(new Donnees(ressources, patients, List.of(), List.of()), LUNDI);
    }

    private static Case laCase(Semaine s, Identifiants.SalleId salle, JourDeLaSemaine jour) {
        return s.cases().stream().filter(c -> c.salle().equals(salle) && c.creneau().equals(MATIN) && c.jour() == jour).findFirst().orElseThrow();
    }

    @Test
    void should_start_the_week_on_sunday() {
        assertThat(SemaineDuPlanning.debutDeLaSemaineDe(MARDI)).isEqualTo(DIMANCHE);
        assertThat(SemaineDuPlanning.debutDeLaSemaineDe(DIMANCHE)).isEqualTo(DIMANCHE);
        assertThat(semaine(ressources(), List.of()).fin()).isEqualTo(DIMANCHE.plusDays(6));
    }

    @Test
    void should_list_present_patients_in_their_case_by_name() {
        Semaine s = semaine(ressources(), List.of(
                patientPlace(1, "Zoe", SALLE_A_G1, CategorieDIsolement.SANS_RISQUE, SejourDuPatient.SANS_LIMITE, JourDeLaSemaine.LUNDI),
                patientPlace(2, "Alice", new GenerateurChoix(A, A2), CategorieDIsolement.SANS_RISQUE, SejourDuPatient.SANS_LIMITE,
                        JourDeLaSemaine.LUNDI)));
        assertThat(laCase(s, A, JourDeLaSemaine.LUNDI).occupants()).extracting(o -> o.nom()).containsExactly("Alice", "Zoe");
        assertThat(laCase(s, A, JourDeLaSemaine.LUNDI).capacite()).isEqualTo(2);
        assertThat(s.conflits()).isEmpty();
    }

    @Test
    void should_exclude_a_sleeping_patient_from_the_grid_and_the_conflicts() {
        SejourDuPatient sommeil = new SejourDuPatient(null, null, false, true);
        Semaine s = semaine(ressources(), List.of(
                patientPlace(1, "Alice", SALLE_A_G1, CategorieDIsolement.SANS_RISQUE, SejourDuPatient.SANS_LIMITE, JourDeLaSemaine.LUNDI),
                patientPlace(2, "Boris", SALLE_A_G1, CategorieDIsolement.SANS_RISQUE, sommeil, JourDeLaSemaine.LUNDI)));
        assertThat(laCase(s, A, JourDeLaSemaine.LUNDI).occupants()).extracting(o -> o.nom()).containsExactly("Alice");
        assertThat(s.conflits()).isEmpty();
    }

    @Test
    void should_zero_the_capacity_of_a_holiday_like_a_weekly_closing() {
        Semaine s = semaine(ressources(EnumSet.range(JourDeLaSemaine.LUNDI, JourDeLaSemaine.SAMEDI),
                List.of(new Fermeture(MARDI, "Aïd"))), List.of());
        assertThat(laCase(s, A, JourDeLaSemaine.LUNDI).capacite()).isEqualTo(2);
        assertThat(laCase(s, A, JourDeLaSemaine.MARDI).capacite()).isZero();
        assertThat(laCase(s, A, JourDeLaSemaine.DIMANCHE).capacite()).isZero();
        assertThat(s.jours().get(JourDeLaSemaine.MARDI.ordinal()).fermeture()).isEqualTo("Aïd");
    }

    @Test
    void should_report_an_unlabelled_closing_with_an_empty_reason_and_the_first_one_wins() {
        Semaine s = semaine(ressources(EnumSet.allOf(JourDeLaSemaine.class), List.of(new Fermeture(MARDI, null))), List.of());
        assertThat(s.jours().get(JourDeLaSemaine.MARDI.ordinal()).fermeture()).isEmpty();
        Semaine deux = semaine(ressources(EnumSet.allOf(JourDeLaSemaine.class),
                List.of(new Fermeture(MARDI, "Férié"), new Fermeture(MARDI, "Travaux"))), List.of());
        assertThat(deux.jours().get(JourDeLaSemaine.MARDI.ordinal()).fermeture()).isEqualTo("Férié");
    }

    @Test
    void should_count_patients_to_replan_once_when_placed_on_closed_days() {
        Semaine s = semaine(ressources(EnumSet.range(JourDeLaSemaine.LUNDI, JourDeLaSemaine.SAMEDI), List.of(new Fermeture(MARDI, "Férié"))),
                List.of(patientPlace(1, "Alice", SALLE_A_G1, CategorieDIsolement.SANS_RISQUE, SejourDuPatient.SANS_LIMITE,
                        JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI, JourDeLaSemaine.DIMANCHE)));
        assertThat(s.patientsAReplanifier()).isEqualTo(1);
    }

    @Test
    void should_stop_showing_a_patient_after_the_release_day_and_say_when_it_is_released() {
        SejourDuPatient sortiLundi = new SejourDuPatient(null, LUNDI, true, false);
        Semaine s = semaine(ressources(), List.of(patientPlace(1, "Alice", SALLE_A_G1, CategorieDIsolement.SANS_RISQUE, sortiLundi,
                JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI)));
        assertThat(laCase(s, A, JourDeLaSemaine.LUNDI).occupants()).singleElement().extracting(o -> o.libereLe()).isEqualTo(MARDI);
        assertThat(laCase(s, A, JourDeLaSemaine.MARDI).occupants()).isEmpty();
    }

    @Test
    void should_ignore_a_patient_whose_place_was_released_before_the_week() {
        SejourDuPatient sorti = new SejourDuPatient(null, DIMANCHE.minusDays(1), true, false);
        Semaine s = semaine(ressources(), List.of(patientPlace(1, "Alice", SALLE_A_G1, CategorieDIsolement.SANS_RISQUE, sorti,
                JourDeLaSemaine.LUNDI)));
        assertThat(laCase(s, A, JourDeLaSemaine.LUNDI).occupants()).isEmpty();
    }

    @Test
    void should_not_show_a_patient_before_the_admission_day() {
        SejourDuPatient admisMardi = new SejourDuPatient(MARDI, null, false, false);
        Semaine s = semaine(ressources(), List.of(patientPlace(1, "Alice", SALLE_A_G1, CategorieDIsolement.SANS_RISQUE, admisMardi,
                JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI)));
        assertThat(laCase(s, A, JourDeLaSemaine.LUNDI).occupants()).isEmpty();
        assertThat(laCase(s, A, JourDeLaSemaine.MARDI).occupants()).hasSize(1);
    }

    @Test
    void should_detect_a_double_booked_generator_and_an_overloaded_room() {
        Semaine s = semaine(ressources(), List.of(
                patientPlace(1, "Alice", new GenerateurChoix(B, B1), CategorieDIsolement.SANS_RISQUE, SejourDuPatient.SANS_LIMITE, JourDeLaSemaine.LUNDI),
                patientPlace(2, "Boris", new GenerateurChoix(B, B1), CategorieDIsolement.SANS_RISQUE, SejourDuPatient.SANS_LIMITE, JourDeLaSemaine.LUNDI)));
        assertThat(s.conflits()).extracting(c -> c.type()).containsExactly(TypeDeConflit.GENERATEUR_DOUBLE, TypeDeConflit.SALLE_SURCHARGEE);
    }

    @Test
    void should_detect_an_unavailable_generator_and_a_patient_without_generator() {
        Semaine s = semaine(ressources(), List.of(
                patientPlace(1, "Alice", new GenerateurChoix(A, JeuDePlaces.HORS_SERVICE), CategorieDIsolement.SANS_RISQUE,
                        SejourDuPatient.SANS_LIMITE, JourDeLaSemaine.LUNDI),
                patientPlace(2, "Boris", new GenerateurChoix(B, null), CategorieDIsolement.SANS_RISQUE, SejourDuPatient.SANS_LIMITE,
                        JourDeLaSemaine.LUNDI)));
        assertThat(s.conflits()).extracting(c -> c.type())
                .containsExactlyInAnyOrder(TypeDeConflit.GENERATEUR_INDISPONIBLE, TypeDeConflit.SANS_GENERATEUR);
    }

    @Test
    void should_detect_an_at_risk_patient_outside_isolation_and_a_mixed_generator() {
        Semaine s = semaine(ressources(), List.of(
                patientPlace(1, "Alice", SALLE_A_G1, CategorieDIsolement.A_RISQUE, SejourDuPatient.SANS_LIMITE, JourDeLaSemaine.LUNDI),
                patientPlace(2, "Boris", SALLE_A_G1, CategorieDIsolement.SANS_RISQUE, SejourDuPatient.SANS_LIMITE, JourDeLaSemaine.MARDI)));
        assertThat(s.conflits()).extracting(c -> c.type()).contains(TypeDeConflit.ISOLEMENT_NON_RESPECTE, TypeDeConflit.GENERATEUR_MIXTE);
        assertThat(s.conflits().stream().filter(c -> c.type() == TypeDeConflit.GENERATEUR_MIXTE).findFirst().orElseThrow().patients())
                .startsWith("G-A1").contains("Alice", "Boris");
    }

    @Test
    void should_move_a_patient_to_a_temporary_case_for_the_day_only() {
        PatientDeLaSemaine alice = patientPlace(1, "Alice", SALLE_A_G1, CategorieDIsolement.SANS_RISQUE, SejourDuPatient.SANS_LIMITE,
                JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI);
        Semaine s = SemaineDuPlanning.construire(new Donnees(ressources(), List.of(alice), List.of(),
                List.of(new DeplacementTemporaire(patient(1), LUNDI, B, MATIN, B1))), LUNDI);
        assertThat(laCase(s, A, JourDeLaSemaine.LUNDI).occupants()).isEmpty();
        assertThat(laCase(s, B, JourDeLaSemaine.LUNDI).occupants()).singleElement()
                .satisfies(o -> {
                    assertThat(o.temporaire()).isTrue();
                    assertThat(o.generateur()).isEqualTo("G-B1");
                });
        assertThat(laCase(s, A, JourDeLaSemaine.MARDI).occupants()).hasSize(1);
    }

    @Test
    void should_ignore_a_temporary_move_towards_an_unknown_room() {
        PatientDeLaSemaine alice = patientPlace(1, "Alice", SALLE_A_G1, CategorieDIsolement.SANS_RISQUE, SejourDuPatient.SANS_LIMITE,
                JourDeLaSemaine.LUNDI);
        Semaine s = SemaineDuPlanning.construire(new Donnees(ressources(), List.of(alice), List.of(), List.of(
                new DeplacementTemporaire(patient(1), LUNDI, new Identifiants.SalleId(JeuDePlaces.id(999)), MATIN, B1))), LUNDI);
        assertThat(laCase(s, A, JourDeLaSemaine.LUNDI).occupants()).hasSize(1);
    }

    @Test
    void should_show_a_performed_session_where_it_took_place_without_conflict_and_warn_for_later_days() {
        PatientDeLaSemaine alice = patientPlace(1, "Alice", SALLE_A_G1, CategorieDIsolement.SANS_RISQUE, SejourDuPatient.SANS_LIMITE,
                JourDeLaSemaine.MARDI, JourDeLaSemaine.MERCREDI);
        SeanceRealisee lundi = new SeanceRealisee(patient(1), "Alice", LUNDI, B, MATIN, B1, false);
        Semaine s = SemaineDuPlanning.construire(new Donnees(ressources(), List.of(alice), List.of(lundi), List.of()), LUNDI);
        assertThat(laCase(s, B, JourDeLaSemaine.LUNDI).occupants()).singleElement().satisfies(o -> assertThat(o.realiseeHorsPlanning()).isTrue());
        assertThat(laCase(s, A, JourDeLaSemaine.MARDI).occupants()).singleElement().satisfies(o -> assertThat(o.dejaRealiseeLe()).isEqualTo(LUNDI));
        assertThat(s.conflits()).isEmpty();
        assertThat(s.patientsAReplanifier()).isZero();
    }

    @Test
    void should_list_apart_a_performed_session_no_case_can_host() {
        SeanceRealisee orpheline = new SeanceRealisee(patient(9), "Zed", LUNDI, null, null, null, false);
        Semaine s = SemaineDuPlanning.construire(new Donnees(ressources(), List.of(), List.of(orpheline), List.of()), LUNDI);
        assertThat(s.seancesSansCase()).singleElement().satisfies(x -> {
            assertThat(x.nom()).isEqualTo("Zed");
            assertThat(x.jour()).isEqualTo(JourDeLaSemaine.LUNDI);
        });
    }

    @Test
    void should_ignore_a_patient_without_complete_case_or_days() {
        PatientId sansJours = patient(1);
        PatientDeLaSemaine p = new PatientDeLaSemaine(sansJours, "Alice",
                new Occupation(sansJours, A, SOIR, A1, Set.of(), CategorieDIsolement.SANS_RISQUE), SejourDuPatient.SANS_LIMITE);
        assertThat(semaine(ressources(), List.of(p)).cases()).allSatisfy(c -> assertThat(c.occupants()).isEmpty());
    }
}
