package com.hemodialyse.backend.domain.planning.service;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CaseGrille;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.DemandePlacement;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.model.Planning.Proposition;
import com.hemodialyse.backend.domain.planning.model.Planning.Raison;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanificationAffectationServiceTest {

    private static final SalleRef SALLE_A = new SalleRef(UUID.randomUUID(), "Salle A");
    private static final SalleRef SALLE_B = new SalleRef(UUID.randomUUID(), "Salle B");
    private static final CreneauRef MATIN = new CreneauRef(UUID.randomUUID(), "Matin", 1);
    private static final CreneauRef SOIR = new CreneauRef(UUID.randomUUID(), "Soir", 2);
    private static final GenerateurRef G1 = new GenerateurRef(UUID.randomUUID(), "G01", SALLE_A.id());
    private static final GenerateurRef G2 = new GenerateurRef(UUID.randomUUID(), "G02", SALLE_A.id());
    private static final GenerateurRef G3 = new GenerateurRef(UUID.randomUUID(), "G03", SALLE_B.id());

    private static final Set<JourSemaine> LMV = EnumSet.of(JourSemaine.LUNDI, JourSemaine.MERCREDI, JourSemaine.VENDREDI);
    private static final Set<JourSemaine> MJS = EnumSet.of(JourSemaine.MARDI, JourSemaine.JEUDI, JourSemaine.SAMEDI);

    private static DonneesPlanning donnees(List<GenerateurRef> generateurs, List<Occupation> occupations) {
        return new DonneesPlanning(List.of(SALLE_A, SALLE_B), List.of(MATIN, SOIR), generateurs, occupations);
    }

    private static Occupation occupation(SalleRef salle, CreneauRef creneau, GenerateurRef g, Set<JourSemaine> jours) {
        return new Occupation(UUID.randomUUID(), salle.id(), creneau.id(), g == null ? null : g.id(), jours);
    }

    private static DemandePlacement trois() {
        return new DemandePlacement(3, Set.of(), null, null);
    }

    @Test
    void should_propose_well_spaced_usual_patterns_first_when_everything_is_free() {
        List<Proposition> p = PlanificationAffectationService.proposer(
                donnees(List.of(G1, G2, G3), List.of()), trois(), 10);

        assertFalse(p.isEmpty());
        assertTrue(p.get(0).score() >= 95);
        assertTrue(p.get(0).raisons().contains(Raison.JOURS_BIEN_ESPACES));
        Set<JourSemaine> premiers = EnumSet.copyOf(p.get(0).jours());
        assertTrue(premiers.equals(LMV) || premiers.equals(MJS), "schéma usuel attendu, obtenu " + premiers);
    }

    @Test
    void should_exclude_days_where_the_generator_is_already_taken_at_that_slot() {
        // G1 occupé lundi-mercredi-vendredi le matin ; G2 n'existe pas ici : seule la salle A (G1) est étudiée
        DonneesPlanning d = new DonneesPlanning(List.of(SALLE_A), List.of(MATIN), List.of(G1),
                List.of(occupation(SALLE_A, MATIN, G1, LMV)));

        List<Proposition> p = PlanificationAffectationService.proposer(d, trois(), 10);

        assertFalse(p.isEmpty());
        for (Proposition prop : p) {
            assertTrue(prop.jours().stream().noneMatch(LMV::contains), "jour déjà occupé proposé : " + prop.jours());
        }
        assertEquals(MJS, EnumSet.copyOf(p.get(0).jours()));
    }

    @Test
    void should_propose_nothing_when_no_generator_has_enough_free_days() {
        Set<JourSemaine> presqueTous = EnumSet.of(JourSemaine.DIMANCHE, JourSemaine.LUNDI, JourSemaine.MARDI,
                JourSemaine.MERCREDI, JourSemaine.JEUDI);
        DonneesPlanning d = new DonneesPlanning(List.of(SALLE_A), List.of(MATIN), List.of(G1),
                List.of(occupation(SALLE_A, MATIN, G1, presqueTous)));

        assertTrue(PlanificationAffectationService.proposer(d, trois(), 10).isEmpty());
    }

    @Test
    void a_patient_placed_in_the_room_without_generator_takes_a_room_seat() {
        // salle A : un seul générateur ; un patient y est placé le matin du lundi sans générateur précis
        DonneesPlanning d = new DonneesPlanning(List.of(SALLE_A), List.of(MATIN), List.of(G1),
                List.of(occupation(SALLE_A, MATIN, null, EnumSet.of(JourSemaine.LUNDI))));

        List<CaseGrille> grille = PlanificationAffectationService.grille(d);
        CaseGrille lundi = grille.stream().filter(c -> c.jour() == JourSemaine.LUNDI).findFirst().orElseThrow();
        CaseGrille mardi = grille.stream().filter(c -> c.jour() == JourSemaine.MARDI).findFirst().orElseThrow();

        assertEquals(1, lundi.capacite());
        assertEquals(0, lundi.libres());
        assertEquals(1, mardi.libres());
        List<Proposition> p = PlanificationAffectationService.proposer(d, trois(), 10);
        assertTrue(p.stream().noneMatch(prop -> prop.jours().contains(JourSemaine.LUNDI)));
    }

    @Test
    void imposed_days_must_all_be_free_otherwise_the_generator_is_skipped() {
        DonneesPlanning d = donnees(List.of(G1, G2),
                List.of(occupation(SALLE_A, MATIN, G1, EnumSet.of(JourSemaine.LUNDI))));
        DemandePlacement demande = new DemandePlacement(3, LMV, MATIN.id(), null);

        List<Proposition> p = PlanificationAffectationService.proposer(d, demande, 10);

        List<Proposition> matinSalleA = p.stream()
                .filter(x -> x.salle().equals(SALLE_A) && x.creneau().equals(MATIN)).toList();
        assertEquals(1, matinSalleA.size());
        assertEquals(G2, matinSalleA.get(0).generateur());           // G1 est pris le lundi
        assertTrue(matinSalleA.get(0).raisons().contains(Raison.JOURS_IMPOSES_LIBRES));
        assertTrue(p.stream().allMatch(x -> EnumSet.copyOf(x.jours()).equals(LMV)));
    }

    @Test
    void preferred_slot_and_room_raise_the_ranking() {
        DonneesPlanning d = donnees(List.of(G1, G3), List.of());
        DemandePlacement demande = new DemandePlacement(3, Set.of(), SOIR.id(), SALLE_B.id());

        List<Proposition> p = PlanificationAffectationService.proposer(d, demande, 10);

        assertEquals(SALLE_B, p.get(0).salle());
        assertEquals(SOIR, p.get(0).creneau());
        assertTrue(p.get(0).raisons().contains(Raison.CRENEAU_PREFERE));
        assertTrue(p.get(0).raisons().contains(Raison.SALLE_PREFEREE));
    }

    @Test
    void other_free_generators_of_the_same_room_are_listed_as_alternatives() {
        DonneesPlanning d = new DonneesPlanning(List.of(SALLE_A), List.of(MATIN), List.of(G1, G2), List.of());

        Proposition premiere = PlanificationAffectationService.proposer(d, trois(), 5).get(0);

        assertEquals(G1, premiere.generateur());
        assertEquals(List.of(G2), premiere.generateursAlternatifs());
    }

    @Test
    void a_room_without_operational_generator_has_no_place_and_zero_capacity() {
        DonneesPlanning d = new DonneesPlanning(List.of(SALLE_A), List.of(MATIN), List.of(), List.of());

        assertTrue(PlanificationAffectationService.proposer(d, trois(), 5).isEmpty());
        assertEquals(0, PlanificationAffectationService.grille(d).get(0).capacite());
    }

    @Test
    void should_keep_at_most_two_proposals_per_room_and_slot_and_respect_the_limit() {
        DonneesPlanning d = donnees(List.of(G1, G2, G3), List.of());

        List<Proposition> p = PlanificationAffectationService.proposer(d, trois(), 100);

        assertTrue(p.size() <= PlanificationAffectationService.LIMITE_MAX);
        long salleAMatin = p.stream().filter(x -> x.salle().equals(SALLE_A) && x.creneau().equals(MATIN)).count();
        assertTrue(salleAMatin <= 2);
        assertEquals(3, PlanificationAffectationService.proposer(d, trois(), 3).size());
    }

    @Test
    void should_reject_an_invalid_number_of_sessions() {
        DonneesPlanning d = donnees(List.of(G1), List.of());

        assertThrows(IllegalArgumentException.class,
                () -> PlanificationAffectationService.proposer(d, new DemandePlacement(0, Set.of(), null, null), 5));
        assertThrows(IllegalArgumentException.class,
                () -> PlanificationAffectationService.proposer(d, new DemandePlacement(8, Set.of(), null, null), 5));
    }

    @Test
    void spacing_score_penalises_consecutive_days_and_favours_usual_patterns() {
        assertEquals(100, PlanificationAffectationService.scoreEspacement(LMV, 3));
        assertEquals(100, PlanificationAffectationService.scoreEspacement(MJS, 3));
        int consecutifs = PlanificationAffectationService.scoreEspacement(
                EnumSet.of(JourSemaine.LUNDI, JourSemaine.MARDI, JourSemaine.MERCREDI), 3);
        assertTrue(consecutifs < 60, "trois jours consécutifs : score " + consecutifs);
        assertEquals(100, PlanificationAffectationService.scoreEspacement(EnumSet.of(JourSemaine.LUNDI), 1));
        // six séances sur sept jours : des jours consécutifs sont inévitables, pas de pénalité excessive
        int six = PlanificationAffectationService.scoreEspacement(
                EnumSet.complementOf(EnumSet.of(JourSemaine.VENDREDI)), 6);
        assertTrue(six >= 60, "six séances : score " + six);
    }

    @Test
    void the_grid_counts_capacity_and_occupation_per_room_slot_and_day() {
        DonneesPlanning d = donnees(List.of(G1, G2, G3),
                List.of(occupation(SALLE_A, MATIN, G1, EnumSet.of(JourSemaine.LUNDI)),
                        occupation(SALLE_A, MATIN, G2, EnumSet.of(JourSemaine.LUNDI))));

        List<CaseGrille> grille = PlanificationAffectationService.grille(d);

        assertEquals(2 * 2 * 7, grille.size());
        CaseGrille lundiMatinA = grille.stream().filter(c -> c.salleId().equals(SALLE_A.id())
                && c.creneauId().equals(MATIN.id()) && c.jour() == JourSemaine.LUNDI).findFirst().orElseThrow();
        assertEquals(2, lundiMatinA.capacite());
        assertEquals(2, lundiMatinA.occupes());
        assertEquals(0, lundiMatinA.libres());
    }
}
