package com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.CompetenceInfirmier;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class InfirmiersConstraintProviderTest {

    private static final LocalDate LUNDI = LocalDate.of(2026, 9, 28);
    private static final UUID SALLE_A = UUID.randomUUID();
    private static final UUID SALLE_B = UUID.randomUUID();
    private static final UUID MATIN = UUID.randomUUID();
    private static final UUID SOIR = UUID.randomUUID();

    private final ConstraintVerifier<InfirmiersConstraintProvider, PlanInfirmiers> verifier =
            ConstraintVerifier.build(new InfirmiersConstraintProvider(), PlanInfirmiers.class, Vacation.class);

    private static InfirmierPlan infirmier(String nom, int maxJour, int maxSemaine, boolean aideSoignant) {
        return new InfirmierPlan(UUID.nameUUIDFromBytes(nom.getBytes()), nom, aideSoignant, false, maxJour, maxSemaine,
                Set.of(), Set.of(), Set.of());
    }

    private static InfirmierPlan infirmier(String nom) {
        return infirmier(nom, 2, 6, false);
    }

    private static Vacation vacation(String id, LocalDate date, UUID salle, UUID creneau, InfirmierPlan infirmier) {
        return new Vacation(id, date, JourSemaine.de(date.getDayOfWeek()), 0, salle, creneau, false, false, List.of(),
                infirmier);
    }

    @Test
    void should_never_put_a_nurse_in_two_rooms_on_the_same_slot_the_same_day() {
        InfirmierPlan marie = infirmier("Marie");
        verifier.verifyThat(InfirmiersConstraintProvider::vacationDoubleCreneau)
                .given(vacation("1", LUNDI, SALLE_A, MATIN, marie), vacation("2", LUNDI, SALLE_B, MATIN, marie))
                .penalizesBy(1);
    }

    @Test
    void should_accept_two_slots_the_same_day_or_the_same_slot_on_two_days() {
        InfirmierPlan marie = infirmier("Marie");
        verifier.verifyThat(InfirmiersConstraintProvider::vacationDoubleCreneau)
                .given(vacation("1", LUNDI, SALLE_A, MATIN, marie), vacation("2", LUNDI, SALLE_A, SOIR, marie),
                        vacation("3", LUNDI.plusDays(1), SALLE_A, MATIN, marie))
                .penalizesBy(0);
    }

    @Test
    void should_enforce_the_daily_vacation_limit_of_each_nurse() {
        InfirmierPlan marie = infirmier("Marie", 1, 6, false);
        verifier.verifyThat(InfirmiersConstraintProvider::maxVacationsJour)
                .given(vacation("1", LUNDI, SALLE_A, MATIN, marie), vacation("2", LUNDI, SALLE_A, SOIR, marie))
                .penalizesBy(1);
    }

    @Test
    void should_penalize_each_unfilled_vacation() {
        InfirmierPlan marie = infirmier("Marie");
        verifier.verifyThat(InfirmiersConstraintProvider::vacationNonPourvue)
                .given(vacation("1", LUNDI, SALLE_A, MATIN, null), vacation("2", LUNDI, SALLE_A, SOIR, null),
                        vacation("3", LUNDI, SALLE_B, SOIR, marie))
                .penalizesBy(2);
    }

    @Test
    void should_penalize_the_vacations_above_the_weekly_maximum() {
        InfirmierPlan marie = infirmier("Marie", 2, 2, false);
        verifier.verifyThat(InfirmiersConstraintProvider::depassementHebdomadaire)
                .given(vacation("1", LUNDI, SALLE_A, MATIN, marie), vacation("2", LUNDI.plusDays(1), SALLE_A, MATIN, marie),
                        vacation("3", LUNDI.plusDays(2), SALLE_A, MATIN, marie),
                        vacation("4", LUNDI.plusDays(3), SALLE_A, MATIN, marie))
                .penalizesBy(2);
    }

    @Test
    void should_prefer_an_even_split_of_the_vacations() {
        InfirmierPlan marie = infirmier("Marie");
        InfirmierPlan paul = infirmier("Paul");
        // 3 + 1 : 9 + 1 = 10
        verifier.verifyThat(InfirmiersConstraintProvider::equiteCharge)
                .given(vacation("1", LUNDI, SALLE_A, MATIN, marie), vacation("2", LUNDI.plusDays(1), SALLE_A, MATIN, marie),
                        vacation("3", LUNDI.plusDays(2), SALLE_A, MATIN, marie),
                        vacation("4", LUNDI, SALLE_A, SOIR, paul))
                .penalizesBy(10);
        // 2 + 2 : 4 + 4 = 8
        verifier.verifyThat(InfirmiersConstraintProvider::equiteCharge)
                .given(vacation("1", LUNDI, SALLE_A, MATIN, marie), vacation("2", LUNDI.plusDays(1), SALLE_A, MATIN, marie),
                        vacation("3", LUNDI, SALLE_A, SOIR, paul), vacation("4", LUNDI.plusDays(1), SALLE_A, SOIR, paul))
                .penalizesBy(8);
    }

    @Test
    void should_count_each_mobilised_nurse_once() {
        InfirmierPlan marie = infirmier("Marie");
        InfirmierPlan paul = infirmier("Paul");
        verifier.verifyThat(InfirmiersConstraintProvider::infirmiersMobilises)
                .given(vacation("1", LUNDI, SALLE_A, MATIN, marie), vacation("2", LUNDI.plusDays(1), SALLE_A, MATIN, marie),
                        vacation("3", LUNDI, SALLE_A, SOIR, paul))
                .penalizesBy(2);
    }

    @Test
    void should_penalize_a_double_vacation_in_the_day() {
        InfirmierPlan marie = infirmier("Marie");
        verifier.verifyThat(InfirmiersConstraintProvider::doubleVacation)
                .given(vacation("1", LUNDI, SALLE_A, MATIN, marie), vacation("2", LUNDI, SALLE_A, SOIR, marie),
                        vacation("3", LUNDI.plusDays(1), SALLE_A, MATIN, marie))
                .penalizesBy(1);
    }

    @Test
    void should_penalize_the_extra_rooms_of_a_nurse() {
        InfirmierPlan marie = infirmier("Marie");
        verifier.verifyThat(InfirmiersConstraintProvider::continuiteSalle)
                .given(vacation("1", LUNDI, SALLE_A, MATIN, marie), vacation("2", LUNDI.plusDays(1), SALLE_B, MATIN, marie),
                        vacation("3", LUNDI.plusDays(2), SALLE_A, MATIN, marie))
                .penalizesBy(1);
    }

    @Test
    void should_penalize_a_room_and_a_slot_the_nurse_does_not_know() {
        InfirmierPlan connue = new InfirmierPlan(UUID.randomUUID(), "Connue", false, false, 2, 6, Set.of(SALLE_A),
                Set.of(MATIN), Set.of());
        verifier.verifyThat(InfirmiersConstraintProvider::affinite)
                .given(vacation("1", LUNDI, SALLE_A, MATIN, connue), // connue : 0
                        vacation("2", LUNDI, SALLE_A, SOIR, connue), // créneau inhabituel : 1
                        vacation("3", LUNDI, SALLE_B, SOIR, connue)) // salle et créneau inconnus : 2
                .penalizesBy(3);
    }

    @Test
    void should_penalize_a_vacation_that_is_not_in_the_current_rotation() {
        JourSemaine lundi = JourSemaine.LUNDI;
        InfirmierPlan marie = new InfirmierPlan(UUID.randomUUID(), "Marie", false, false, 2, 6, Set.of(), Set.of(),
                Set.of(SALLE_A + "|" + MATIN + "|" + lundi));
        verifier.verifyThat(InfirmiersConstraintProvider::stabiliteRoulement)
                .given(vacation("1", LUNDI, SALLE_A, MATIN, marie), vacation("2", LUNDI, SALLE_B, MATIN, marie))
                .penalizesBy(1);
    }

    @Test
    void should_penalize_a_nursing_assistant_where_a_qualified_nurse_is_preferred() {
        InfirmierPlan assistant = infirmier("Aide", 2, 6, true);
        InfirmierPlan infirmier = infirmier("Marie");
        Vacation preferee = new Vacation("1", LUNDI, JourSemaine.LUNDI, 0, SALLE_A, MATIN, true, false, List.of(), assistant);
        Vacation libre = new Vacation("2", LUNDI, JourSemaine.LUNDI, 0, SALLE_B, MATIN, false, false, List.of(), assistant);
        Vacation qualifiee = new Vacation("3", LUNDI, JourSemaine.LUNDI, 0, SALLE_A, SOIR, true, false, List.of(), infirmier);

        verifier.verifyThat(InfirmiersConstraintProvider::qualification)
                .given(preferee, libre, qualifiee)
                .penalizesBy(1);
    }

    private static InfirmierPlan profil(String nom, int joursMax, int quota, CompetenceInfirmier... competences) {
        return new InfirmierPlan(UUID.nameUUIDFromBytes(nom.getBytes()), nom, false, false, 2, 14, Set.of(), Set.of(),
                Set.of(), joursMax, 5, quota, Set.of(competences));
    }

    @Test
    void should_penalize_each_worked_day_beyond_the_weekly_rest() {
        InfirmierPlan marie = profil("Marie", 2, 100);
        verifier.verifyThat(InfirmiersConstraintProvider::reposHebdomadaire)
                .given(vacation("1", LUNDI, SALLE_A, MATIN, marie), vacation("2", LUNDI, SALLE_A, SOIR, marie),
                        vacation("3", LUNDI.plusDays(1), SALLE_A, MATIN, marie),
                        vacation("4", LUNDI.plusDays(2), SALLE_A, MATIN, marie),
                        vacation("5", LUNDI.plusDays(3), SALLE_A, MATIN, marie))
                .penalizesBy(2);
    }

    @Test
    void should_penalize_each_hour_beyond_the_part_time_quota() {
        InfirmierPlan partiel = profil("Partiel", 7, 8);
        verifier.verifyThat(InfirmiersConstraintProvider::quotaHeures)
                .given(vacation("1", LUNDI, SALLE_A, MATIN, partiel), vacation("2", LUNDI.plusDays(1), SALLE_A, MATIN, partiel))
                .penalizesBy(2);
        verifier.verifyThat(InfirmiersConstraintProvider::quotaHeures)
                .given(vacation("1", LUNDI, SALLE_A, MATIN, partiel))
                .penalizesBy(0);
    }

    @Test
    void should_penalize_a_case_whose_patients_need_a_skill_no_nurse_of_the_case_has() {
        InfirmierPlan pediatre = profil("Pediatre", 7, 100, CompetenceInfirmier.PEDIATRIE);
        InfirmierPlan generaliste = profil("Generaliste", 7, 100);
        Vacation a = vacation("1", LUNDI, SALLE_A, MATIN, generaliste);
        Vacation b = vacation("2", LUNDI, SALLE_B, MATIN, pediatre);
        ExigenceCompetence pediatrieA = new ExigenceCompetence(a.cleCase(), CompetenceInfirmier.PEDIATRIE);
        ExigenceCompetence pediatrieB = new ExigenceCompetence(b.cleCase(), CompetenceInfirmier.PEDIATRIE);
        ExigenceCompetence catheterB = new ExigenceCompetence(b.cleCase(), CompetenceInfirmier.CATHETER);
        verifier.verifyThat(InfirmiersConstraintProvider::competence)
                .given(a, b, pediatrieA, pediatrieB, catheterB)
                .penalizesBy(2);
    }
}
