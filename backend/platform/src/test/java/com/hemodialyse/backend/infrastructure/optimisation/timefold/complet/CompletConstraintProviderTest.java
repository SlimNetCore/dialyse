package com.hemodialyse.backend.infrastructure.optimisation.timefold.complet;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.CompetenceInfirmier;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.InfirmierPlan;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.Vacation;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.ContexteCentre;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PlacementPatient;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PosteSerie;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.SchemaJours;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;

class CompletConstraintProviderTest {

    private static final LocalDate DATE_LUNDI = LocalDate.of(2026, 9, 28);
    private static final UUID SALLE_A = UUID.randomUUID();
    private static final UUID SALLE_B = UUID.randomUUID();
    private static final UUID MATIN = UUID.randomUUID();

    private final ConstraintVerifier<CompletConstraintProvider, PlanComplet> verifier =
            ConstraintVerifier.build(new CompletConstraintProvider(), PlanComplet.class, PlacementPatient.class,
                    Vacation.class);

    private static PlacementPatient patient(UUID salle, Set<CompetenceInfirmier> competences) {
        PlacementPatient p = new PlacementPatient(UUID.randomUUID(), "P", false, List.of(LUNDI),
                List.of(SchemaJours.fixe(List.of(LUNDI))), null, null, null, List.of(), null, Set.of(), competences);
        p.setPoste(new PosteSerie(UUID.randomUUID(), "G", salle, MATIN, false));
        return p;
    }

    private static PlacementPatient patient(UUID salle) {
        return patient(salle, Set.of());
    }

    private static InfirmierPlan infirmier(String nom, boolean aideSoignant, CompetenceInfirmier... competences) {
        return new InfirmierPlan(UUID.nameUUIDFromBytes(nom.getBytes()), nom, aideSoignant, false, 2, 6, Set.of(),
                Set.of(), Set.of(), JourSemaine.NB_JOURS, 5, 40, Set.of(competences));
    }

    private static Vacation vacation(String id, UUID salle, InfirmierPlan infirmier) {
        return new Vacation(id, DATE_LUNDI, LUNDI, 0, salle, MATIN, false, false, List.of(), infirmier);
    }

    @Test
    void should_require_one_nurse_per_started_ratio_of_placed_patients() {
        ContexteCentre ratio2 = new ContexteCentre(2, 8, 1);
        InfirmierPlan marie = infirmier("Marie", false);
        verifier.verifyThat(CompletConstraintProvider::couvertureManquante)
                .given(ratio2, patient(SALLE_A), patient(SALLE_A), patient(SALLE_A), vacation("1", SALLE_A, marie),
                        vacation("2", SALLE_A, null), patient(SALLE_B))
                .penalizesBy(2);
    }

    @Test
    void should_penalize_a_nurse_posted_where_no_patient_needs_her() {
        ContexteCentre ratio4 = new ContexteCentre(4, 8, 1);
        InfirmierPlan marie = infirmier("Marie", false);
        InfirmierPlan paul = infirmier("Paul", false);
        verifier.verifyThat(CompletConstraintProvider::vacationInutile)
                .given(ratio4, patient(SALLE_A), vacation("1", SALLE_A, marie), vacation("2", SALLE_A, paul),
                        vacation("3", SALLE_B, infirmier("Lina", false)))
                .penalizesBy(2);
    }

    @Test
    void should_penalize_a_case_served_only_by_nursing_assistants() {
        verifier.verifyThat(CompletConstraintProvider::qualificationCase)
                .given(vacation("1", SALLE_A, infirmier("Aide", true)),
                        vacation("2", SALLE_B, infirmier("Aide2", true)), vacation("3", SALLE_B, infirmier("Ide", false)))
                .penalizesBy(1);
    }

    @Test
    void should_derive_the_skills_needed_from_the_patients_placed_in_the_case() {
        verifier.verifyThat(CompletConstraintProvider::competence)
                .given(patient(SALLE_A, Set.of(CompetenceInfirmier.CATHETER)),
                        patient(SALLE_A, Set.of(CompetenceInfirmier.CATHETER)),
                        patient(SALLE_B, Set.of(CompetenceInfirmier.PEDIATRIE)),
                        vacation("1", SALLE_A, infirmier("Marie", false)),
                        vacation("2", SALLE_B, infirmier("Paul", false, CompetenceInfirmier.PEDIATRIE)))
                .penalizesBy(1);
    }
}
