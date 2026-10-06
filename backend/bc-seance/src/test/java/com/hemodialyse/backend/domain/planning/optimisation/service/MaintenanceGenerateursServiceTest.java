package com.hemodialyse.backend.domain.planning.optimisation.service;

import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.planning.model.DeplacementTemporaire;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Fermeture;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.IndisponibiliteGenerateur;
import com.hemodialyse.backend.domain.planning.optimisation.service.MaintenanceGenerateursService.SeanceImpactee;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.JEUDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceGenerateursServiceTest {

    private static final LocalDate DIMANCHE = OptimisationFixture.DIMANCHE;
    private static final LocalDate LUNDI_28 = DIMANCHE.plusDays(1);

    private final OptimisationFixture f = new OptimisationFixture();
    private final SalleRef a = f.salle("Salle A", 2);
    private final CreneauRef matin = f.creneau("Matin");
    private final GenerateurRef g1 = f.generateur(a, 0);
    private final GenerateurRef g2 = f.generateur(a, 1);
    private final PatientAPlacer p1 = f.patient("P1", false, a, matin, g1, LUNDI, JEUDI);
    private final PatientAPlacer p2 = f.patient("P2", false, a, matin, g2, LUNDI);

    private static DonneesOptimisation avec(DonneesOptimisation d, List<IndisponibiliteGenerateur> indispos,
                                            List<DeplacementTemporaire> temporaires) {
        return new DonneesOptimisation(d.presence(), d.patients(), Map.of(), indispos, temporaires);
    }

    @Test
    void should_list_only_the_dated_sessions_on_an_unavailable_generator() {
        DonneesOptimisation d = avec(f.build(), List.of(new IndisponibiliteGenerateur(g1.id(), LUNDI_28, LUNDI_28,
                "Révision")), List.of());

        List<SeanceImpactee> impactees = MaintenanceGenerateursService.seancesImpactees(d, DIMANCHE, DIMANCHE.plusDays(13));

        assertThat(impactees).singleElement().satisfies(s -> {
            assertThat(s.patientId()).isEqualTo(p1.patientId());
            assertThat(s.date()).isEqualTo(LUNDI_28);
            assertThat(s.motif()).isEqualTo("Révision");
        });
    }

    @Test
    void should_ignore_closed_days() {
        f.fermetures.add(new Fermeture(LUNDI_28, "Férié"));
        DonneesOptimisation d = avec(f.build(), List.of(new IndisponibiliteGenerateur(g1.id(), LUNDI_28, LUNDI_28,
                "Révision")), List.of());

        assertThat(MaintenanceGenerateursService.seancesImpactees(d, DIMANCHE, DIMANCHE.plusDays(6))).isEmpty();
    }

    @Test
    void should_use_a_recorded_temporary_move_as_the_effective_place() {
        DeplacementTemporaire versG1 = DeplacementTemporaire.creer(OptimisationFixture.CENTRE, p2.patientId(), LUNDI_28,
                a.id(), matin.id(), g1.id(), "Révision G2");
        DonneesOptimisation d = avec(f.build(), List.of(new IndisponibiliteGenerateur(g2.id(), LUNDI_28, LUNDI_28,
                "Révision G2")), List.of(versG1));

        assertThat(MaintenanceGenerateursService.seancesImpactees(d, LUNDI_28, LUNDI_28))
                .as("la séance déjà déplacée n'est plus sur le générateur indisponible").isEmpty();
        assertThat(MaintenanceGenerateursService.postesOccupes(d, LUNDI_28, Set.of()))
                .containsExactly(MaintenanceGenerateursService.cle(g1.id(), matin.id()));
        assertThat(MaintenanceGenerateursService.postesOccupes(d, LUNDI_28, Set.of(p1.patientId(), p2.patientId())))
                .isEmpty();
    }
}
