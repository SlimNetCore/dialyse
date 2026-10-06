package com.hemodialyse.backend.infrastructure.optimisation.timefold.maintenance;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PosteSerie;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class MaintenanceConstraintProviderTest {

    private static final LocalDate LUNDI = LocalDate.of(2026, 9, 28);
    private static final UUID SALLE_A = UUID.randomUUID();
    private static final UUID SALLE_B = UUID.randomUUID();
    private static final UUID MATIN = UUID.randomUUID();
    private static final UUID SOIR = UUID.randomUUID();
    private static final Poste HABITUEL = new Poste(SALLE_A, MATIN, UUID.randomUUID(), "A1");

    private final ConstraintVerifier<MaintenanceConstraintProvider, PlanMaintenance> verifier =
            ConstraintVerifier.build(new MaintenanceConstraintProvider(), PlanMaintenance.class, SeanceTemporaire.class);

    private static SeanceTemporaire seance(LocalDate date, PosteSerie poste) {
        SeanceTemporaire s = new SeanceTemporaire(UUID.randomUUID(), date, JourSemaine.de(date.getDayOfWeek()), HABITUEL,
                "Révision", List.of());
        s.setPoste(poste);
        return s;
    }

    private static PosteSerie poste(UUID salle, UUID creneau) {
        return new PosteSerie(UUID.nameUUIDFromBytes((salle + "" + creneau).getBytes()), "G", salle, creneau, false);
    }

    @Test
    void should_never_give_the_same_free_place_twice_on_the_same_day() {
        PosteSerie libre = poste(SALLE_A, MATIN);
        verifier.verifyThat(MaintenanceConstraintProvider::posteDouble)
                .given(seance(LUNDI, libre), seance(LUNDI, libre), seance(LUNDI.plusDays(2), libre))
                .penalizesBy(1);
    }

    @Test
    void should_flag_a_session_without_any_free_place() {
        verifier.verifyThat(MaintenanceConstraintProvider::seanceSansSolution)
                .given(seance(LUNDI, null), seance(LUNDI, poste(SALLE_A, MATIN)))
                .penalizesBy(1);
    }

    @Test
    void should_prefer_the_usual_slot_then_the_usual_room() {
        verifier.verifyThat(MaintenanceConstraintProvider::changementCreneau)
                .given(seance(LUNDI, poste(SALLE_A, SOIR)), seance(LUNDI, poste(SALLE_B, MATIN)))
                .penalizesBy(1);
        verifier.verifyThat(MaintenanceConstraintProvider::changementSalle)
                .given(seance(LUNDI, poste(SALLE_A, SOIR)), seance(LUNDI, poste(SALLE_B, MATIN)))
                .penalizesBy(1);
    }
}
