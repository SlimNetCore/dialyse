package com.hemodialyse.backend.domain.planning.service;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.service.PlanificationAffectationService.Violation;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vérification d'un placement saisi à la main : mêmes règles que les propositions du planificateur.
 */
class PlanificationVerificationTest {

    private static final SalleRef SALLE = new SalleRef(UUID.randomUUID(), "Salle");
    private static final SalleRef ISOLEMENT = new SalleRef(UUID.randomUUID(), "Isolement");
    private static final CreneauRef MATIN = new CreneauRef(UUID.randomUUID(), "Matin", 1);
    private static final GenerateurRef G1 = new GenerateurRef(UUID.randomUUID(), "G01", SALLE.id());
    private static final GenerateurRef G_ISO = new GenerateurRef(UUID.randomUUID(), "G09", ISOLEMENT.id());
    private static final Set<JourSemaine> LUNDI = EnumSet.of(JourSemaine.LUNDI);

    private static DonneesPlanning donnees(List<Occupation> occupations, Set<JourSemaine> joursOuverts) {
        return new DonneesPlanning(List.of(SALLE, ISOLEMENT), List.of(MATIN), List.of(G1, G_ISO), occupations,
                joursOuverts, Set.of(ISOLEMENT.id()), List.of());
    }

    private static List<Violation> verifier(DonneesPlanning d, boolean aRisque, SalleRef salle, GenerateurRef g,
                                            Set<JourSemaine> jours) {
        return PlanificationAffectationService.verifier(d, aRisque, salle.id(), MATIN.id(), g == null ? null : g.id(),
                jours);
    }

    @Test
    void a_free_generator_on_an_open_day_is_valid() {
        assertEquals(List.of(), verifier(donnees(List.of(), EnumSet.allOf(JourSemaine.class)), false, SALLE, G1, LUNDI));
    }

    @Test
    void a_missing_part_or_no_day_makes_the_placement_incomplete() {
        DonneesPlanning d = donnees(List.of(), EnumSet.allOf(JourSemaine.class));
        assertEquals(List.of(Violation.INCOMPLET), verifier(d, false, SALLE, null, LUNDI));
        assertEquals(List.of(Violation.INCOMPLET), verifier(d, false, SALLE, G1, Set.of()));
    }

    @Test
    void a_generator_serving_another_patient_the_same_day_is_refused_but_not_another_day() {
        Occupation autre = new Occupation(UUID.randomUUID(), SALLE.id(), MATIN.id(), G1.id(), LUNDI);
        DonneesPlanning d = donnees(List.of(autre), EnumSet.allOf(JourSemaine.class));

        assertEquals(List.of(Violation.GENERATEUR_OCCUPE), verifier(d, false, SALLE, G1, LUNDI));
        assertEquals(List.of(), verifier(d, false, SALLE, G1, EnumSet.of(JourSemaine.MARDI)));
    }

    @Test
    void a_closed_weekday_is_refused() {
        DonneesPlanning d = donnees(List.of(), EnumSet.complementOf(EnumSet.of(JourSemaine.LUNDI)));
        assertEquals(List.of(Violation.JOUR_FERME), verifier(d, false, SALLE, G1, LUNDI));
    }

    @Test
    void a_patient_at_risk_requires_the_isolation_room() {
        DonneesPlanning d = donnees(List.of(), EnumSet.allOf(JourSemaine.class));
        assertEquals(List.of(Violation.ISOLEMENT_REQUIS), verifier(d, true, SALLE, G1, LUNDI));
        assertEquals(List.of(), verifier(d, true, ISOLEMENT, G_ISO, LUNDI));
    }

    @Test
    void an_isolation_generator_is_never_shared_with_a_patient_without_risk() {
        Occupation sansRisque = new Occupation(UUID.randomUUID(), ISOLEMENT.id(), MATIN.id(), G_ISO.id(),
                EnumSet.of(JourSemaine.MARDI), false);
        DonneesPlanning d = donnees(List.of(sansRisque), EnumSet.allOf(JourSemaine.class));

        assertEquals(List.of(Violation.GENERATEUR_INCOMPATIBLE), verifier(d, true, ISOLEMENT, G_ISO, LUNDI));
    }

    @Test
    void a_generator_of_another_room_is_unknown_for_this_room() {
        DonneesPlanning d = donnees(List.of(), EnumSet.allOf(JourSemaine.class));
        assertEquals(List.of(Violation.GENERATEUR_INCONNU), verifier(d, false, SALLE, G_ISO, LUNDI));
    }

    @Test
    void an_unknown_room_or_slot_is_refused() {
        DonneesPlanning d = donnees(List.of(), EnumSet.allOf(JourSemaine.class));
        List<Violation> v = PlanificationAffectationService.verifier(d, false, UUID.randomUUID(), UUID.randomUUID(),
                G1.id(), LUNDI);
        assertTrue(v.contains(Violation.SALLE_INCONNUE) && v.contains(Violation.CRENEAU_INCONNU));
    }
}
