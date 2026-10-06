package com.hemodialyse.backend.domain.planning.optimisation.service;

import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.Indicateurs;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationNonPourvue;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationPlanifiee;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MARDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MERCREDI;
import static com.hemodialyse.backend.domain.planning.optimisation.service.OptimisationFixture.DIMANCHE;
import static org.assertj.core.api.Assertions.assertThat;

class IndicateursOptimisationServiceTest {

    private static ParametresOptimisation params(PerimetreOptimisation perimetre) {
        return ParametresOptimisation.parDefaut(perimetre, DIMANCHE);
    }

    @Test
    void should_measure_generators_rooms_and_nurse_vacations_of_the_current_placement() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 4);
        SalleRef b = f.salle("Salle B", 4);
        CreneauRef matin = f.creneau("Matin");
        // 2 patients en salle A, 2 en salle B, mêmes jours : 2 salles ouvertes, chacune exige 1 infirmier (ratio 4)
        f.patient("P1", false, a, matin, f.generateur(a, 0), LUNDI, MERCREDI);
        f.patient("P2", false, a, matin, f.generateur(a, 1), LUNDI, MERCREDI);
        f.patient("P3", false, b, matin, f.generateur(b, 0), LUNDI, MERCREDI);
        f.patient("P4", false, b, matin, f.generateur(b, 1), LUNDI, MERCREDI);

        Indicateurs avant = IndicateursOptimisationService.avant(f.build(), params(PerimetreOptimisation.PATIENTS));

        assertThat(avant.generateursUtilises()).isEqualTo(4);
        assertThat(avant.sallesOuvertes()).isEqualTo(4); // 2 salles x 2 jours
        assertThat(avant.vacationsRequises()).isEqualTo(4);
        assertThat(avant.placesInfirmierInutilisees()).isEqualTo(4 * 4 - 8); // 4 cases : 2 patients pour 4 places
        assertThat(avant.vacationsNonPourvues()).isEqualTo(4); // aucun roulement
        assertThat(avant.infirmiersMobilises()).isZero();
        assertThat(avant.patientsNonPlaces()).isZero();
    }

    @Test
    void should_show_the_saving_when_patients_are_packed_in_one_room() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 4);
        SalleRef b = f.salle("Salle B", 4);
        CreneauRef matin = f.creneau("Matin");
        var p1 = f.patient("P1", false, a, matin, f.generateur(a, 0), LUNDI, MERCREDI);
        var p2 = f.patient("P2", false, a, matin, f.generateur(a, 1), LUNDI, MERCREDI);
        var p3 = f.patient("P3", false, b, matin, f.generateur(b, 0), LUNDI, MERCREDI);
        var p4 = f.patient("P4", false, b, matin, f.generateur(b, 1), LUNDI, MERCREDI);
        DonneesOptimisation donnees = f.build();

        Map<UUID, Poste> regroupes = new HashMap<>();
        regroupes.put(p1.patientId(), p1.actuelle());
        regroupes.put(p2.patientId(), p2.actuelle());
        regroupes.put(p3.patientId(), new Poste(a.id(), matin.id(), f.generateur(a, 2).id(), "A-G3"));
        regroupes.put(p4.patientId(), new Poste(a.id(), matin.id(), f.generateur(a, 3).id(), "A-G4"));

        Indicateurs apres = IndicateursOptimisationService.apres(donnees, regroupes,
                params(PerimetreOptimisation.PATIENTS), null, null);

        assertThat(apres.sallesOuvertes()).isEqualTo(2);
        assertThat(apres.vacationsRequises()).isEqualTo(2);
        assertThat(apres.placesInfirmierInutilisees()).isZero();
        assertThat(apres.vacationsRequises()).isLessThan(IndicateursOptimisationService
                .avant(donnees, params(PerimetreOptimisation.PATIENTS)).vacationsRequises());
    }

    @Test
    void should_count_patients_without_a_complete_place() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        f.patient("Sans générateur", false, a, matin, null, LUNDI);
        f.patientNonPlace("Non placé", false, MARDI);
        f.patient("Placé", false, a, matin, f.generateur(a, 0), LUNDI);

        Indicateurs avant = IndicateursOptimisationService.avant(f.build(), params(PerimetreOptimisation.PATIENTS));

        assertThat(avant.patientsNonPlaces()).isEqualTo(2);
        assertThat(avant.generateursUtilises()).isEqualTo(1);
    }

    @Test
    void should_ignore_dated_absences_for_a_weekly_rotation_but_not_for_the_coverage() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        InfirmierRef marie = f.infirmier("Marie");
        f.affecter(marie, a, matin, LUNDI);
        f.patient("P1", false, a, matin, f.generateur(a, 0), LUNDI);
        f.absence(marie, DIMANCHE, DIMANCHE.plusDays(6));
        DonneesOptimisation donnees = f.build();

        Indicateurs roulement = IndicateursOptimisationService.avant(donnees, params(PerimetreOptimisation.ROULEMENT));
        Indicateurs couverture = IndicateursOptimisationService.avant(donnees, params(PerimetreOptimisation.COUVERTURE));

        assertThat(roulement.vacationsNonPourvues()).isZero();
        assertThat(roulement.infirmiersMobilises()).isEqualTo(1);
        assertThat(couverture.vacationsNonPourvues()).isEqualTo(1);
        assertThat(couverture.infirmiersMobilises()).isZero();
    }

    @Test
    void should_measure_equity_and_weekly_overtime_from_proposed_vacations() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 1);
        CreneauRef matin = f.creneau("Matin");
        InfirmierRef marie = f.infirmier("Marie");
        InfirmierRef paul = f.infirmier("Paul");
        DonneesOptimisation donnees = f.build();
        ParametresOptimisation parametres = new ParametresOptimisation(PerimetreOptimisation.ROULEMENT, DIMANCHE, 1, 10, 5,
                null, 2, 2);
        // Marie : 3 vacations (1 de trop au-delà de 2) ; Paul : 1
        List<VacationPlanifiee> vacations = List.of(
                vacation(DIMANCHE.plusDays(1), LUNDI, a, matin, marie),
                vacation(DIMANCHE.plusDays(2), MARDI, a, matin, marie),
                vacation(DIMANCHE.plusDays(3), MERCREDI, a, matin, marie),
                vacation(DIMANCHE.plusDays(1), LUNDI, a, matin, paul));
        List<VacationNonPourvue> manques = List.of(
                new VacationNonPourvue(DIMANCHE.plusDays(4), JourSemaine.JEUDI, a.id(), matin.id(), 2));

        Indicateurs apres = IndicateursOptimisationService.apres(donnees, Map.of(), parametres, vacations, manques);

        assertThat(apres.infirmiersMobilises()).isEqualTo(2);
        assertThat(apres.ecartCharge()).isEqualTo(2);
        assertThat(apres.depassementsHebdo()).isEqualTo(1);
        assertThat(apres.vacationsNonPourvues()).isEqualTo(2);
    }

    private static VacationPlanifiee vacation(LocalDate date, JourSemaine jour, SalleRef salle, CreneauRef creneau,
                                              InfirmierRef infirmier) {
        return new VacationPlanifiee(date, jour, salle.id(), creneau.id(), infirmier.id(), infirmier.nom(), false);
    }
}
