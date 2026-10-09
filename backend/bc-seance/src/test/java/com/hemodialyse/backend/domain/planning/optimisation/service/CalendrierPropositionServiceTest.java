package com.hemodialyse.backend.domain.planning.optimisation.service;

import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Fermeture;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.CaseCalendrier;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.JourCalendrier;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.SituationInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.CauseNonPlace;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementPatient;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementTemporairePropose;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.PatientNonPlace;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationPlanifiee;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MARDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MERCREDI;
import static com.hemodialyse.backend.domain.planning.optimisation.service.OptimisationFixture.DIMANCHE;
import static org.assertj.core.api.Assertions.assertThat;

class CalendrierPropositionServiceTest {

    private static final LocalDate LUNDI_DATE = DIMANCHE.plusDays(1);
    private static final LocalDate MARDI_DATE = DIMANCHE.plusDays(2);

    private static ResultatOptimisation resultat(List<DeplacementPatient> deplacements, List<PatientNonPlace> nonPlaces,
                                                 List<VacationPlanifiee> vacations,
                                                 List<DeplacementTemporairePropose> temporaires) {
        return new ResultatOptimisation(List.of(), List.of(), deplacements, nonPlaces, vacations, List.of(), null, null,
                temporaires, List.of());
    }

    private static JourCalendrier jour(List<CaseCalendrier> cases, SalleRef salle, CreneauRef creneau, LocalDate date) {
        return cases.stream().filter(c -> c.salleId().equals(salle.id()) && c.creneauId().equals(creneau.id()))
                .flatMap(c -> c.jours().stream()).filter(j -> j.date().equals(date)).findFirst().orElseThrow();
    }

    private static List<CaseCalendrier> construire(OptimisationFixture f, ResultatOptimisation r,
                                                   PerimetreOptimisation perimetre) {
        return CalendrierPropositionService.construire(f.build(), r, ParametresOptimisation.parDefaut(perimetre, DIMANCHE));
    }

    @Test
    void should_list_patients_with_their_generator_in_the_rooms_and_slots_where_they_dialyse() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        f.patient("Benali", false, a, matin, f.generateur(a, 0), LUNDI, MERCREDI);
        f.patient("Amrani", true, a, matin, f.generateur(a, 1), LUNDI);

        List<CaseCalendrier> cases = construire(f, resultat(List.of(), List.of(), List.of(), List.of()),
                PerimetreOptimisation.PATIENTS);

        assertThat(cases).hasSize(1);
        JourCalendrier lundi = jour(cases, a, matin, LUNDI_DATE);
        assertThat(lundi.patients()).extracting("nom").containsExactly("Amrani", "Benali");
        assertThat(lundi.patients()).extracting("generateurCode").containsExactly("A-G2", "A-G1");
        assertThat(lundi.patients().get(0).aRisque()).isTrue();
        assertThat(jour(cases, a, matin, MARDI_DATE).patients()).isEmpty();
        assertThat(jour(cases, a, matin, DIMANCHE.plusDays(3)).patients()).extracting("nom").containsExactly("Benali");
    }

    @Test
    void should_place_moved_patients_in_their_new_place_and_flag_them() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        SalleRef b = f.salle("Salle B", 2);
        CreneauRef matin = f.creneau("Matin");
        var p = f.patient("Benali", false, b, matin, f.generateur(b, 0), LUNDI);
        f.patient("Amrani", false, a, matin, f.generateur(a, 0), LUNDI);
        Poste vers = new Poste(a.id(), matin.id(), f.generateur(a, 1).id(), "A-G2");

        List<CaseCalendrier> cases = construire(f, resultat(
                List.of(new DeplacementPatient(p.patientId(), "Benali", p.actuelle(), vers)),
                List.of(), List.of(), List.of()), PerimetreOptimisation.PATIENTS);

        JourCalendrier salleA = jour(cases, a, matin, LUNDI_DATE);
        assertThat(salleA.patients()).extracting("nom").containsExactly("Amrani", "Benali");
        assertThat(salleA.patients().get(1).deplace()).isTrue();
        assertThat(salleA.patients().get(1).generateurCode()).isEqualTo("A-G2");
        assertThat(salleA.patients().get(1).avant()).as("place avant la proposition").startsWith("Salle B · Matin · ");
        assertThat(salleA.patients().get(0).avant()).as("un patient non déplacé n'a pas de place avant").isNull();
        assertThat(cases).as("la salle B n'accueille plus personne : plus de ligne").noneMatch(c -> c.salleId().equals(b.id()));
    }

    @Test
    void should_apply_the_days_chosen_by_the_optimisation_and_drop_unplaced_patients() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        var choisi = f.patient("Benali", false, a, matin, f.generateur(a, 0), LUNDI, MERCREDI);
        var retire = f.patient("Amrani", false, a, matin, f.generateur(a, 1), LUNDI);

        List<CaseCalendrier> cases = construire(f, resultat(
                List.of(new DeplacementPatient(choisi.patientId(), "Benali", choisi.actuelle(), choisi.actuelle(),
                        List.of(MARDI))),
                List.of(new PatientNonPlace(retire.patientId(), "Amrani", CauseNonPlace.AUCUNE_PLACE)),
                List.of(), List.of()), PerimetreOptimisation.PATIENTS);

        assertThat(jour(cases, a, matin, LUNDI_DATE).patients()).isEmpty();
        assertThat(jour(cases, a, matin, MARDI_DATE).patients()).extracting("nom").containsExactly("Benali");
        assertThat(jour(cases, a, matin, DIMANCHE.plusDays(3)).patients()).isEmpty();
    }

    @Test
    void should_show_a_temporary_move_only_on_its_date() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        SalleRef b = f.salle("Salle B", 2);
        CreneauRef matin = f.creneau("Matin");
        var p = f.patient("Benali", false, a, matin, f.generateur(a, 0), LUNDI, MARDI);
        f.patient("Amrani", false, b, matin, f.generateur(b, 0), MARDI);
        Poste temporaire = new Poste(b.id(), matin.id(), f.generateur(b, 1).id(), "B-G2");

        List<CaseCalendrier> cases = construire(f, resultat(List.of(), List.of(), List.of(),
                List.of(new DeplacementTemporairePropose(p.patientId(), "Benali", MARDI_DATE, MARDI, p.actuelle(),
                        temporaire, "Générateur en maintenance"))), PerimetreOptimisation.MAINTENANCE);

        assertThat(jour(cases, a, matin, LUNDI_DATE).patients()).extracting("nom").containsExactly("Benali");
        assertThat(jour(cases, a, matin, MARDI_DATE).patients()).isEmpty();
        JourCalendrier salleB = jour(cases, b, matin, MARDI_DATE);
        assertThat(salleB.patients()).extracting("nom").containsExactly("Amrani", "Benali");
        assertThat(salleB.patients().get(1).temporaire()).isTrue();
        assertThat(salleB.patients().get(1).generateurCode()).isEqualTo("B-G2");
        assertThat(salleB.patients().get(1).avant()).as("place habituelle").startsWith("Salle A · Matin · ");
    }

    @Test
    void should_grey_out_closed_days_and_show_no_patient_there() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        f.patient("Benali", false, a, matin, f.generateur(a, 0), LUNDI, MARDI);
        f.fermetures.add(new Fermeture(MARDI_DATE, "Jour férié"));

        List<CaseCalendrier> cases = construire(f, resultat(List.of(), List.of(), List.of(), List.of()),
                PerimetreOptimisation.PATIENTS);

        JourCalendrier mardi = jour(cases, a, matin, MARDI_DATE);
        assertThat(mardi.ferme()).isTrue();
        assertThat(mardi.motifFermeture()).isEqualTo("Jour férié");
        assertThat(mardi.patients()).isEmpty();
    }

    @Test
    void should_use_the_current_roster_when_the_scope_does_not_plan_nurses_and_flag_absentees() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        f.patient("Benali", false, a, matin, f.generateur(a, 0), LUNDI);
        InfirmierRef sara = f.infirmier("Sara");
        InfirmierRef karim = f.infirmier("Karim");
        f.affecter(sara, a, matin, LUNDI).affecter(karim, a, matin, LUNDI);
        f.absence(karim, LUNDI_DATE, LUNDI_DATE);

        List<CaseCalendrier> cases = construire(f, resultat(List.of(), List.of(), List.of(), List.of()),
                PerimetreOptimisation.PATIENTS);

        JourCalendrier lundi = jour(cases, a, matin, LUNDI_DATE);
        assertThat(lundi.infirmiers()).extracting("nom").containsExactlyInAnyOrder("Sara", "Karim");
        assertThat(lundi.infirmiers()).filteredOn(i -> i.nom().equals("Karim"))
                .extracting("situation").containsExactly(SituationInfirmier.ABSENT);
        assertThat(lundi.requis()).isEqualTo(1);
        assertThat(lundi.manque()).as("Sara tient l'unique vacation requise").isZero();
    }

    @Test
    void should_show_the_proposed_nurses_and_the_missing_vacations_when_the_scope_plans_them() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 4);
        CreneauRef matin = f.creneau("Matin");
        for (int i = 0; i < 5; i++) f.patient("P" + i, false, a, matin, f.generateur(a, i % 4), LUNDI);

        List<CaseCalendrier> cases = construire(f, resultat(List.of(), List.of(), List.of(
                        new VacationPlanifiee(LUNDI_DATE, LUNDI, a.id(), matin.id(), java.util.UUID.randomUUID(), "Sara", true),
                        new VacationPlanifiee(LUNDI_DATE, LUNDI, a.id(), matin.id(), java.util.UUID.randomUUID(), "Nadia", false)),
                List.of()), PerimetreOptimisation.COUVERTURE);

        JourCalendrier lundi = jour(cases, a, matin, LUNDI_DATE);
        assertThat(lundi.infirmiers()).extracting("nom").containsExactly("Nadia", "Sara");
        assertThat(lundi.infirmiers()).filteredOn(i -> i.nom().equals("Nadia"))
                .extracting("situation").containsExactly(SituationInfirmier.NOUVEAU);
        assertThat(lundi.requis()).as("5 patients, ratio 4").isEqualTo(2);
        assertThat(lundi.manque()).isZero();
    }

    @Test
    void should_report_the_surplus_when_the_proposal_plans_more_nurses_than_the_patients_require() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 4);
        CreneauRef matin = f.creneau("Matin");
        for (int i = 0; i < 4; i++) f.patient("P" + i, false, a, matin, f.generateur(a, i), LUNDI);

        List<CaseCalendrier> cases = construire(f, resultat(List.of(), List.of(), List.of(
                        new VacationPlanifiee(LUNDI_DATE, LUNDI, a.id(), matin.id(), java.util.UUID.randomUUID(), "Sara", true),
                        new VacationPlanifiee(LUNDI_DATE, LUNDI, a.id(), matin.id(), java.util.UUID.randomUUID(), "Nadia", false),
                        new VacationPlanifiee(LUNDI_DATE, LUNDI, a.id(), matin.id(), java.util.UUID.randomUUID(), "Rym", false)),
                List.of()), PerimetreOptimisation.COUVERTURE);

        JourCalendrier lundi = jour(cases, a, matin, LUNDI_DATE);
        assertThat(lundi.requis()).as("4 patients, ratio 4").isEqualTo(1);
        assertThat(lundi.manque()).isZero();
        assertThat(lundi.surplus()).as("3 infirmiers pour 1 requis").isEqualTo(2);
    }

    @Test
    void should_report_the_surplus_of_the_current_roster_including_a_room_that_the_proposal_empties() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 4);
        SalleRef b = f.salle("Salle B", 4);
        CreneauRef matin = f.creneau("Matin");
        var p = f.patient("Benali", false, b, matin, f.generateur(b, 0), LUNDI);
        f.patient("Amrani", false, a, matin, f.generateur(a, 0), LUNDI);
        f.affecter(f.infirmier("Paul"), b, matin, LUNDI);
        f.affecter(f.infirmier("Marie"), a, matin, LUNDI);
        Poste vers = new Poste(a.id(), matin.id(), f.generateur(a, 1).id(), "A-G2");

        List<CaseCalendrier> cases = construire(f, resultat(
                List.of(new DeplacementPatient(p.patientId(), "Benali", p.actuelle(), vers)),
                List.of(), List.of(), List.of()), PerimetreOptimisation.PATIENTS);

        assertThat(jour(cases, a, matin, LUNDI_DATE).surplus()).as("2 patients, 1 infirmier requis, 1 prévu").isZero();
        assertThat(cases).filteredOn(c -> c.salleId().equals(b.id())).singleElement().satisfies(
                ligne -> assertThat(ligne.jours().get(1).surplus()).as("Paul reste dans une salle vidée").isEqualTo(1));
    }

    @Test
    void should_report_the_missing_nurses_of_a_case() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 4);
        CreneauRef matin = f.creneau("Matin");
        for (int i = 0; i < 5; i++) f.patient("P" + i, false, a, matin, f.generateur(a, i % 4), LUNDI);

        List<CaseCalendrier> cases = construire(f, resultat(List.of(), List.of(), List.of(), List.of()),
                PerimetreOptimisation.PATIENTS);

        JourCalendrier lundi = jour(cases, a, matin, LUNDI_DATE);
        assertThat(lundi.requis()).isEqualTo(2);
        assertThat(lundi.manque()).isEqualTo(2);
    }

    private static ParametresOptimisation horizon(PerimetreOptimisation perimetre, int semaines) {
        return new ParametresOptimisation(perimetre, DIMANCHE, semaines, 20, 5,
                com.hemodialyse.backend.domain.planning.optimisation.model.ObjectifInfirmiers.EQUITE, 2, 6, 6, 40, 2);
    }

    @Test
    void should_project_the_placement_of_a_type_week_on_every_week_of_the_horizon() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        f.patient("Benali", false, a, matin, f.generateur(a, 0), LUNDI);

        List<CaseCalendrier> cases = CalendrierPropositionService.construire(f.build(),
                resultat(List.of(), List.of(), List.of(), List.of()), horizon(PerimetreOptimisation.PATIENTS, 3));

        assertThat(cases).extracting(CaseCalendrier::semaineDebut)
                .containsExactly(DIMANCHE, DIMANCHE.plusWeeks(1), DIMANCHE.plusWeeks(2));
        for (int semaine = 0; semaine < 3; semaine++) {
            assertThat(jour(cases, a, matin, LUNDI_DATE.plusWeeks(semaine)).patients()).extracting("nom")
                    .containsExactly("Benali");
        }
    }

    @Test
    void should_repeat_the_type_week_roster_of_the_proposal_on_every_week_instead_of_the_first_one_only() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 4);
        CreneauRef matin = f.creneau("Matin");
        f.patient("P0", false, a, matin, f.generateur(a, 0), LUNDI);
        var vacations = List.of(
                new VacationPlanifiee(LUNDI_DATE, LUNDI, a.id(), matin.id(), java.util.UUID.randomUUID(), "Sara", true));

        List<CaseCalendrier> cases = CalendrierPropositionService.construire(f.build(),
                resultat(List.of(), List.of(), vacations, List.of()), horizon(PerimetreOptimisation.ROULEMENT, 3));

        for (int semaine = 0; semaine < 3; semaine++) {
            assertThat(jour(cases, a, matin, LUNDI_DATE.plusWeeks(semaine)).infirmiers()).extracting("nom")
                    .as("semaine %d", semaine).containsExactly("Sara");
        }
    }

    @Test
    void should_keep_dated_vacations_on_their_own_week_for_the_coverage() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 4);
        CreneauRef matin = f.creneau("Matin");
        f.patient("P0", false, a, matin, f.generateur(a, 0), LUNDI);
        var vacations = List.of(
                new VacationPlanifiee(LUNDI_DATE.plusWeeks(1), LUNDI, a.id(), matin.id(), java.util.UUID.randomUUID(), "Sara", false));

        List<CaseCalendrier> cases = CalendrierPropositionService.construire(f.build(),
                resultat(List.of(), List.of(), vacations, List.of()), horizon(PerimetreOptimisation.COUVERTURE, 2));

        assertThat(jour(cases, a, matin, LUNDI_DATE).infirmiers()).extracting("nom").doesNotContain("Sara");
        assertThat(jour(cases, a, matin, LUNDI_DATE.plusWeeks(1)).infirmiers()).extracting("nom").contains("Sara");
    }

    @Test
    void should_build_one_block_of_rows_per_week_of_the_horizon() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        f.patient("Benali", false, a, matin, f.generateur(a, 0), LUNDI);
        ParametresOptimisation deuxSemaines = new ParametresOptimisation(PerimetreOptimisation.COUVERTURE, DIMANCHE, 2,
                20, 5, com.hemodialyse.backend.domain.planning.optimisation.model.ObjectifInfirmiers.EQUITE, 2, 6, 6, 40, 2);

        List<CaseCalendrier> cases = CalendrierPropositionService.construire(f.build(),
                resultat(List.of(), List.of(), List.of(), List.of()), deuxSemaines);

        assertThat(cases).extracting(CaseCalendrier::semaineDebut).containsExactly(DIMANCHE, DIMANCHE.plusWeeks(1));
    }
}
