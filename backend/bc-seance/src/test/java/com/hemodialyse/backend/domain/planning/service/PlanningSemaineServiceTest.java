package com.hemodialyse.backend.domain.planning.service;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.Fermeture;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.CellulePlanning;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.Conflit;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.DonneesSemaine;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.SemainePlanning;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.TypeConflit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlanningSemaineServiceTest {

    private static final LocalDate DIMANCHE = LocalDate.of(2026, 9, 27);   // dimanche
    private static final SalleRef SALLE = new SalleRef(UUID.randomUUID(), "Salle A");
    private static final SalleRef ISO = new SalleRef(UUID.randomUUID(), "Salle ISO");
    private static final CreneauRef MATIN = new CreneauRef(UUID.randomUUID(), "Matin", 1);
    private static final GenerateurRef G1 = new GenerateurRef(UUID.randomUUID(), "G01", SALLE.id());
    private static final GenerateurRef G2 = new GenerateurRef(UUID.randomUUID(), "G02", SALLE.id());
    private static final GenerateurRef GI = new GenerateurRef(UUID.randomUUID(), "G90", ISO.id());
    private static final Set<JourSemaine> LMV = EnumSet.of(JourSemaine.LUNDI, JourSemaine.MERCREDI, JourSemaine.VENDREDI);

    private final Map<UUID, String> noms = new HashMap<>();

    private static boolean contient(SemainePlanning s, TypeConflit type) {
        return s.conflits().stream().anyMatch(c -> c.type() == type);
    }

    private static CellulePlanning cellule(SemainePlanning s, SalleRef salle, JourSemaine jour) {
        return s.cellules().stream().filter(c -> c.salleId().equals(salle.id()) && c.jour() == jour).findFirst().orElseThrow();
    }

    private UUID patient(String nom) {
        UUID id = UUID.randomUUID();
        noms.put(id, nom);
        return id;
    }

    private Occupation occupation(UUID patient, SalleRef salle, GenerateurRef g, boolean aRisque, Set<JourSemaine> jours) {
        return new Occupation(patient, salle.id(), MATIN.id(), g == null ? null : g.id(), jours, aRisque);
    }

    private SemainePlanning semaine(List<Occupation> occupations, Set<JourSemaine> ouverts, Set<UUID> isolement,
                                    List<Fermeture> fermetures, List<GenerateurRef> generateurs) {
        DonneesPlanning planning = new DonneesPlanning(List.of(SALLE, ISO), List.of(MATIN), generateurs, occupations,
                ouverts, isolement, fermetures);
        return PlanningSemaineService.construire(new DonneesSemaine(planning, noms, fermetures), DIMANCHE);
    }

    @Test
    void the_week_starts_on_sunday() {
        assertEquals(DIMANCHE, PlanningSemaineService.debutSemaine(LocalDate.of(2026, 10, 2)));   // vendredi
        assertEquals(DIMANCHE, PlanningSemaineService.debutSemaine(DIMANCHE));
        assertEquals(DIMANCHE, PlanningSemaineService.debutSemaine(LocalDate.of(2026, 10, 3)));   // samedi
    }

    @Test
    void should_list_the_patients_of_each_cell_with_their_generator_sorted_by_name() {
        SemainePlanning s = semaine(
                List.of(occupation(patient("Zahra B."), SALLE, G2, false, LMV),
                        occupation(patient("Amine K."), SALLE, G1, false, LMV)),
                EnumSet.allOf(JourSemaine.class), Set.of(), List.of(), List.of(G1, G2, GI));

        CellulePlanning lundi = cellule(s, SALLE, JourSemaine.LUNDI);

        assertEquals(DIMANCHE, s.debut());
        assertEquals(DIMANCHE.plusDays(6), s.fin());
        assertEquals(List.of("Amine K.", "Zahra B."), lundi.occupants().stream().map(o -> o.nom()).toList());
        assertEquals("G01", lundi.occupants().get(0).generateurCode());
        assertEquals(2, lundi.capacite());
        assertTrue(cellule(s, SALLE, JourSemaine.MARDI).occupants().isEmpty());
        assertTrue(s.conflits().isEmpty());
    }

    @Test
    void should_detect_a_double_booked_generator_and_an_overloaded_room() {
        SemainePlanning s = semaine(
                List.of(occupation(patient("A"), SALLE, G1, false, EnumSet.of(JourSemaine.LUNDI)),
                        occupation(patient("B"), SALLE, G1, false, EnumSet.of(JourSemaine.LUNDI)),
                        occupation(patient("C"), SALLE, G2, false, EnumSet.of(JourSemaine.LUNDI))),
                EnumSet.allOf(JourSemaine.class), Set.of(), List.of(), List.of(G1, G2));

        assertTrue(contient(s, TypeConflit.GENERATEUR_DOUBLE));
        assertTrue(contient(s, TypeConflit.SALLE_SURCHARGEE));
        Conflit double_ = s.conflits().stream().filter(c -> c.type() == TypeConflit.GENERATEUR_DOUBLE).findFirst().orElseThrow();
        assertEquals(JourSemaine.LUNDI, double_.jour());
        assertEquals(List.of("A", "B"), double_.patients());
    }

    @Test
    void should_flag_patients_without_generator_or_on_an_unavailable_generator() {
        GenerateurRef enPanne = new GenerateurRef(UUID.randomUUID(), "G77", SALLE.id());   // absent des générateurs en service
        SemainePlanning s = semaine(
                List.of(occupation(patient("Sans"), SALLE, null, false, EnumSet.of(JourSemaine.MARDI)),
                        occupation(patient("Panne"), SALLE, enPanne, false, EnumSet.of(JourSemaine.MARDI))),
                EnumSet.allOf(JourSemaine.class), Set.of(), List.of(), List.of(G1, G2));

        assertTrue(contient(s, TypeConflit.SANS_GENERATEUR));
        assertTrue(contient(s, TypeConflit.GENERATEUR_INDISPONIBLE));
        assertFalse(contient(s, TypeConflit.GENERATEUR_DOUBLE));
    }

    @Test
    void should_mark_closed_days_and_count_the_patients_to_reschedule() {
        LocalDate lundi = DIMANCHE.plusDays(1);
        SemainePlanning s = semaine(
                List.of(occupation(patient("A"), SALLE, G1, false, LMV),
                        occupation(patient("B"), SALLE, G2, false, EnumSet.of(JourSemaine.MARDI))),
                EnumSet.complementOf(EnumSet.of(JourSemaine.VENDREDI)), Set.of(),
                List.of(new Fermeture(lundi, "Fermeture exceptionnelle")), List.of(G1, G2));

        assertTrue(s.jours().get(JourSemaine.LUNDI.ordinal()).ferme());
        assertEquals("Fermeture exceptionnelle", s.jours().get(JourSemaine.LUNDI.ordinal()).fermetureMotif());
        assertTrue(s.jours().get(JourSemaine.VENDREDI.ordinal()).ferme());   // fermeture hebdomadaire
        assertFalse(s.jours().get(JourSemaine.MARDI.ordinal()).ferme());
        assertEquals(0, cellule(s, SALLE, JourSemaine.VENDREDI).capacite());
        assertEquals(1, s.patientsAReplanifier());   // A (lundi fermé et vendredi fermé) ; B n'est pas concerné
    }

    @Test
    void should_detect_isolation_rules_violations_and_mixed_generators() {
        UUID aRisque = patient("Risque");
        SemainePlanning s = semaine(
                List.of(occupation(aRisque, SALLE, G1, true, EnumSet.of(JourSemaine.LUNDI)),     // à risque hors salle d'isolement
                        occupation(patient("Sain"), SALLE, G1, false, EnumSet.of(JourSemaine.MARDI)),   // même générateur
                        occupation(patient("Sain ISO"), ISO, GI, false, EnumSet.of(JourSemaine.JEUDI))), // sain en isolement
                EnumSet.allOf(JourSemaine.class), Set.of(ISO.id()), List.of(), List.of(G1, G2, GI));

        assertTrue(contient(s, TypeConflit.ISOLEMENT_NON_RESPECTE));
        assertTrue(contient(s, TypeConflit.GENERATEUR_MIXTE));
        Conflit mixte = s.conflits().stream().filter(c -> c.type() == TypeConflit.GENERATEUR_MIXTE).findFirst().orElseThrow();
        assertNull(mixte.salleId());
        assertEquals("G01", mixte.patients().get(0));
    }

    @Test
    void a_clean_planning_has_no_conflict_and_nobody_to_reschedule() {
        SemainePlanning s = semaine(
                List.of(occupation(patient("A"), SALLE, G1, false, LMV)),
                EnumSet.allOf(JourSemaine.class), Set.of(ISO.id()), List.of(), List.of(G1, G2, GI));

        assertTrue(s.conflits().isEmpty());
        assertEquals(0, s.patientsAReplanifier());
    }
}
