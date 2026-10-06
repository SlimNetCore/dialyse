package com.hemodialyse.backend.infrastructure.optimisation.timefold.patients;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.JEUDI;
import static org.assertj.core.api.Assertions.assertThat;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MARDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MERCREDI;

class PatientsConstraintProviderTest {

    private static final UUID SALLE_A = UUID.randomUUID();
    private static final UUID SALLE_B = UUID.randomUUID();
    private static final UUID ISO = UUID.randomUUID();
    private static final UUID MATIN = UUID.randomUUID();
    private static final UUID SOIR = UUID.randomUUID();

    private final ConstraintVerifier<PatientsConstraintProvider, PlanPatients> verifier =
            ConstraintVerifier.build(new PatientsConstraintProvider(), PlanPatients.class, PlacementPatient.class);

    private static PosteSerie poste(String code, UUID salle, UUID creneau, boolean isolement) {
        return new PosteSerie(UUID.nameUUIDFromBytes(code.getBytes()), code, salle, creneau, isolement);
    }

    private static PlacementPatient patient(boolean risque, PosteSerie poste, JourSemaine... jours) {
        PlacementPatient p = new PlacementPatient(UUID.randomUUID(), "P", risque, List.of(jours), null, null, null, List.of());
        p.setPoste(poste);
        return p;
    }

    private static ContexteCentre contexte(int ratio) {
        return new ContexteCentre(ratio, 16, 2);
    }

    @Test
    void should_penalize_two_patients_sharing_a_generator_on_a_common_day() {
        PosteSerie g1 = poste("A1", SALLE_A, MATIN, false);
        verifier.verifyThat(PatientsConstraintProvider::generateurDouble)
                .given(patient(false, g1, LUNDI, MERCREDI), patient(false, g1, MERCREDI, JEUDI))
                .penalizesBy(1);
    }

    @Test
    void should_allow_complementary_days_on_the_same_generator_and_slot() {
        PosteSerie g1 = poste("A1", SALLE_A, MATIN, false);
        verifier.verifyThat(PatientsConstraintProvider::generateurDouble)
                .given(patient(false, g1, LUNDI, MERCREDI), patient(false, g1, MARDI, JEUDI))
                .penalizesBy(0);
    }

    @Test
    void should_allow_the_same_generator_on_two_different_slots() {
        verifier.verifyThat(PatientsConstraintProvider::generateurDouble)
                .given(patient(false, poste("A1", SALLE_A, MATIN, false), LUNDI),
                        patient(false, poste("A1", SALLE_A, SOIR, false), LUNDI))
                .penalizesBy(0);
    }

    @Test
    void should_penalize_a_risk_patient_outside_isolation_and_a_healthy_one_inside() {
        verifier.verifyThat(PatientsConstraintProvider::isolement)
                .given(patient(true, poste("A1", SALLE_A, MATIN, false), LUNDI),
                        patient(false, poste("I1", ISO, MATIN, true), LUNDI),
                        patient(true, poste("I2", ISO, MATIN, true), LUNDI))
                .penalizesBy(2);
    }

    @Test
    void should_penalize_each_unplaced_patient() {
        verifier.verifyThat(PatientsConstraintProvider::patientNonPlace)
                .given(patient(false, null, LUNDI), patient(false, null, MARDI),
                        patient(false, poste("A1", SALLE_A, MATIN, false), LUNDI))
                .penalizesBy(2);
    }

    @Test
    void should_require_one_nurse_vacation_per_slice_of_patients_per_case() {
        // ratio 4 : 5 patients le lundi matin salle A = 2 vacations ; 1 patient le lundi matin salle B = 1 vacation
        List<PlacementPatient> cinq = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> patient(false, poste("A" + i, SALLE_A, MATIN, false), LUNDI)).toList();
        PlacementPatient seul = patient(false, poste("B1", SALLE_B, MATIN, false), LUNDI);
        List<Object> faits = new java.util.ArrayList<>(cinq);
        faits.add(seul);
        faits.add(contexte(4));

        verifier.verifyThat(PatientsConstraintProvider::infirmiersRequis).given(faits.toArray()).penalizesBy(3);
    }

    @Test
    void should_count_the_cases_per_day_for_the_open_rooms() {
        // un patient lundi-mercredi en salle A + un autre lundi en salle A = 2 cases ouvertes (lundi, mercredi)
        verifier.verifyThat(PatientsConstraintProvider::sallesOuvertes)
                .given(patient(false, poste("A1", SALLE_A, MATIN, false), LUNDI, MERCREDI),
                        patient(false, poste("A2", SALLE_A, MATIN, false), LUNDI))
                .penalizesBy(2);
    }

    @Test
    void should_count_each_generator_once() {
        PosteSerie g1 = poste("A1", SALLE_A, MATIN, false);
        verifier.verifyThat(PatientsConstraintProvider::generateursUtilises)
                .given(patient(false, g1, LUNDI), patient(false, g1, MARDI),
                        patient(false, poste("A2", SALLE_A, MATIN, false), LUNDI))
                .penalizesBy(2);
    }

    @Test
    void should_penalize_the_missing_backup_generators() {
        // 16 générateurs dont 2 de secours : 14 exploitables ; 15 patients le lundi matin = 1 de trop
        List<Object> faits = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) faits.add(patient(false, poste("G" + i, SALLE_A, MATIN, false), LUNDI));
        faits.add(contexte(4));

        verifier.verifyThat(PatientsConstraintProvider::reserveSecours).given(faits.toArray()).penalizesBy(1);
    }

    @Test
    void should_keep_the_backup_reserve_when_the_center_has_enough_free_generators() {
        List<Object> faits = new java.util.ArrayList<>();
        for (int i = 0; i < 14; i++) faits.add(patient(false, poste("G" + i, SALLE_A, MATIN, false), LUNDI));
        faits.add(contexte(4));

        verifier.verifyThat(PatientsConstraintProvider::reserveSecours).given(faits.toArray()).penalizesBy(0);
    }

    @Test
    void should_weigh_a_slot_change_more_than_a_room_change_more_than_a_generator_change() {
        PosteSerie initial = poste("A1", SALLE_A, MATIN, false);
        PlacementPatient creneau = new PlacementPatient(UUID.randomUUID(), "C", false, List.of(LUNDI), SALLE_A, MATIN,
                initial.generateurId(), List.of());
        creneau.setPoste(poste("A1", SALLE_A, SOIR, false));
        PlacementPatient salle = new PlacementPatient(UUID.randomUUID(), "S", false, List.of(LUNDI), SALLE_A, MATIN,
                initial.generateurId(), List.of());
        salle.setPoste(poste("B1", SALLE_B, MATIN, false));
        PlacementPatient generateur = new PlacementPatient(UUID.randomUUID(), "G", false, List.of(LUNDI), SALLE_A, MATIN,
                initial.generateurId(), List.of());
        generateur.setPoste(poste("A2", SALLE_A, MATIN, false));
        PlacementPatient inchange = new PlacementPatient(UUID.randomUUID(), "I", false, List.of(LUNDI), SALLE_A, MATIN,
                initial.generateurId(), List.of());
        inchange.setPoste(initial);

        verifier.verifyThat(PatientsConstraintProvider::stabilite)
                .given(creneau, salle, generateur, inchange)
                .penalizesBy(3 + 2 + 1);
    }

    private static PlacementPatient avecPreferences(PosteSerie poste, List<JourSemaine> actuels, List<SchemaJours> schemas,
                                                    UUID prefere, Set<UUID> transporteurs) {
        PlacementPatient p = new PlacementPatient(UUID.randomUUID(), "P", false, actuels, schemas, null, null, null,
                List.of(), prefere, transporteurs, Set.of());
        p.setPoste(poste);
        return p;
    }

    @Test
    void should_penalize_a_less_well_spaced_day_pattern() {
        SchemaJours bon = new SchemaJours(List.of(LUNDI, MERCREDI), 0);
        SchemaJours serre = new SchemaJours(List.of(LUNDI, MARDI), 12);
        PlacementPatient p = avecPreferences(poste("A1", SALLE_A, MATIN, false), List.of(), List.of(bon, serre), null,
                Set.of());
        p.setSchema(serre);
        verifier.verifyThat(PatientsConstraintProvider::espacementJours).given(p).penalizesBy(12);
        p.setSchema(bon);
        verifier.verifyThat(PatientsConstraintProvider::espacementJours).given(p).penalizesBy(0);
    }

    @Test
    void should_count_each_usual_day_dropped_when_days_are_revised() {
        SchemaJours actuel = new SchemaJours(List.of(LUNDI, MERCREDI), 0);
        SchemaJours autre = new SchemaJours(List.of(MARDI, JEUDI), 0);
        SchemaJours proche = new SchemaJours(List.of(LUNDI, JEUDI), 0);
        PlacementPatient p = avecPreferences(poste("A1", SALLE_A, MATIN, false), List.of(LUNDI, MERCREDI),
                List.of(actuel, autre, proche), null, Set.of());
        assertThat(p.getSchema()).as("démarre sur ses jours actuels").isEqualTo(actuel);
        verifier.verifyThat(PatientsConstraintProvider::changementJours).given(p).penalizesBy(0);
        p.setSchema(autre);
        verifier.verifyThat(PatientsConstraintProvider::changementJours).given(p).penalizesBy(2);
        p.setSchema(proche);
        verifier.verifyThat(PatientsConstraintProvider::changementJours).given(p).penalizesBy(1);
    }

    @Test
    void should_penalize_a_patient_outside_the_preferred_slot() {
        List<SchemaJours> lundi = List.of(SchemaJours.fixe(List.of(LUNDI)));
        verifier.verifyThat(PatientsConstraintProvider::creneauPrefere)
                .given(avecPreferences(poste("A1", SALLE_A, MATIN, false), List.of(LUNDI), lundi, SOIR, Set.of()),
                        avecPreferences(poste("A2", SALLE_A, SOIR, false), List.of(LUNDI), lundi, SOIR, Set.of()),
                        avecPreferences(poste("A3", SALLE_A, MATIN, false), List.of(LUNDI), lundi, null, Set.of()))
                .penalizesBy(1);
    }

    @Test
    void should_penalize_patients_of_a_same_transporter_split_across_slots_on_common_days() {
        UUID ambulance = UUID.randomUUID();
        List<SchemaJours> lunMer = List.of(SchemaJours.fixe(List.of(LUNDI, MERCREDI)));
        List<SchemaJours> merJeu = List.of(SchemaJours.fixe(List.of(MERCREDI, JEUDI)));
        verifier.verifyThat(PatientsConstraintProvider::transportPartage)
                .given(avecPreferences(poste("A1", SALLE_A, MATIN, false), List.of(), lunMer, null, Set.of(ambulance)),
                        avecPreferences(poste("A2", SALLE_A, SOIR, false), List.of(), merJeu, null, Set.of(ambulance)),
                        avecPreferences(poste("A3", SALLE_A, SOIR, false), List.of(), lunMer, null, Set.of()))
                .penalizesBy(1);
        verifier.verifyThat(PatientsConstraintProvider::transportPartage)
                .given(avecPreferences(poste("A1", SALLE_A, MATIN, false), List.of(), lunMer, null, Set.of(ambulance)),
                        avecPreferences(poste("B1", SALLE_B, MATIN, false), List.of(), lunMer, null, Set.of(ambulance)))
                .penalizesBy(0);
    }
}
