package com.hemodialyse.backend.domain.planning.optimisation.service;

import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementPatient;
import com.hemodialyse.backend.domain.planning.service.PlanificationAffectationService.Violation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MARDI;
import static org.assertj.core.api.Assertions.assertThat;

class VerificationDeplacementsServiceTest {

    private final OptimisationFixture f = new OptimisationFixture();
    private final SalleRef a = f.salle("Salle A", 2);
    private final SalleRef iso = f.salleIsolement("Salle I", 1);
    private final CreneauRef matin = f.creneau("Matin");
    private final CreneauRef soir = f.creneau("Soir");

    private static Poste poste(SalleRef salle, CreneauRef creneau, com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef g) {
        return new Poste(salle.id(), creneau.id(), g.id(), g.code());
    }

    @Test
    void should_accept_a_swap_between_two_patients_that_is_valid_once_both_have_moved() {
        PatientAPlacer p1 = f.patient("P1", false, a, matin, f.generateur(a, 0), LUNDI);
        PatientAPlacer p2 = f.patient("P2", false, a, matin, f.generateur(a, 1), LUNDI);
        DonneesOptimisation donnees = f.build();

        var echange = List.of(
                new DeplacementPatient(p1.patientId(), "P1", p1.actuelle(), p2.actuelle()),
                new DeplacementPatient(p2.patientId(), "P2", p2.actuelle(), p1.actuelle()));

        assertThat(VerificationDeplacementsService.verifier(donnees, echange)).isEmpty();
    }

    @Test
    void should_refuse_two_patients_moved_onto_the_same_generator() {
        PatientAPlacer p1 = f.patient("P1", false, a, matin, f.generateur(a, 0), LUNDI);
        PatientAPlacer p2 = f.patient("P2", false, a, soir, f.generateur(a, 1), LUNDI);
        var g = f.generateur(a, 0);
        var deplacements = List.of(
                new DeplacementPatient(p1.patientId(), "P1", p1.actuelle(), poste(a, soir, g)),
                new DeplacementPatient(p2.patientId(), "P2", p2.actuelle(), poste(a, soir, g)));

        var refus = VerificationDeplacementsService.verifier(f.build(), deplacements);

        assertThat(refus).isNotEmpty();
        assertThat(refus.values().stream().flatMap(List::stream)).contains(Violation.GENERATEUR_OCCUPE);
    }

    @Test
    void should_refuse_a_risk_patient_moved_outside_the_isolation_room() {
        PatientAPlacer risque = f.patient("Risque", true, iso, matin, f.generateur(iso, 0), MARDI);
        var deplacement = new DeplacementPatient(risque.patientId(), "Risque", risque.actuelle(),
                poste(a, matin, f.generateur(a, 0)));

        var refus = VerificationDeplacementsService.verifier(f.build(), List.of(deplacement));

        assertThat(refus.get(risque.patientId())).contains(Violation.ISOLEMENT_REQUIS);
    }

    @Test
    void should_refuse_a_move_of_an_unknown_patient() {
        f.patient("P1", false, a, matin, f.generateur(a, 0), LUNDI);
        UUID inconnu = UUID.randomUUID();
        var deplacement = new DeplacementPatient(inconnu, "?", null, poste(a, matin, f.generateur(a, 1)));

        assertThat(VerificationDeplacementsService.verifier(f.build(), List.of(deplacement)).get(inconnu))
                .containsExactly(Violation.INCOMPLET);
    }

    @Test
    void should_accept_the_placement_of_a_patient_who_had_none() {
        PatientAPlacer nouveau = f.patientNonPlace("Nouveau", false, LUNDI);
        var deplacement = new DeplacementPatient(nouveau.patientId(), "Nouveau", null, poste(a, matin, f.generateur(a, 0)));

        assertThat(VerificationDeplacementsService.verifier(f.build(), List.of(deplacement))).isEmpty();
    }

    @Test
    void should_drop_the_invalid_moves_and_keep_the_valid_ones() {
        PatientAPlacer p1 = f.patient("P1", false, a, matin, f.generateur(a, 0), LUNDI);
        PatientAPlacer p2 = f.patient("P2", false, a, soir, f.generateur(a, 1), LUNDI);
        PatientAPlacer p3 = f.patient("P3", false, a, matin, f.generateur(a, 1), MARDI);
        var g1 = f.generateur(a, 0);
        var valide = new DeplacementPatient(p3.patientId(), "P3", p3.actuelle(), poste(a, soir, f.generateur(a, 0)));
        // P1 et P2 visent le même générateur au même créneau le même jour : les deux sont refusés
        var conflitA = new DeplacementPatient(p1.patientId(), "P1", p1.actuelle(), poste(a, soir, g1));
        var conflitB = new DeplacementPatient(p2.patientId(), "P2", p2.actuelle(), poste(a, soir, g1));

        var retenus = VerificationDeplacementsService.retirerInvalides(f.build(), List.of(conflitA, conflitB, valide));

        assertThat(retenus).extracting(DeplacementPatient::patientId).containsExactly(p3.patientId());
    }

    @Test
    void should_keep_everything_when_all_the_moves_are_valid() {
        PatientAPlacer p1 = f.patient("P1", false, a, matin, f.generateur(a, 0), LUNDI);
        var deplacement = new DeplacementPatient(p1.patientId(), "P1", p1.actuelle(), poste(a, soir, f.generateur(a, 1)));

        assertThat(VerificationDeplacementsService.retirerInvalides(f.build(), List.of(deplacement)))
                .containsExactly(deplacement);
    }
}
