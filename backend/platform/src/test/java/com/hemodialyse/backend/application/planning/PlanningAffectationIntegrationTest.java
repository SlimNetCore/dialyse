package com.hemodialyse.backend.application.planning;

import com.hemodialyse.backend.application.planning.PlanningAffectationQueryService.Resultat;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CaseGrille;
import com.hemodialyse.backend.domain.planning.model.Planning.Proposition;
import com.hemodialyse.backend.domain.planning.model.PlanningParametres;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.SemainePlanning;
import com.hemodialyse.backend.domain.planning.port.PlanningParametresPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aide au placement sur base réelle : salles, créneaux, générateurs en service et patients actifs lus du centre
 * uniquement ; un patient transféré libère sa place ; les générateurs hors service ne sont jamais proposés.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PlanningAffectationIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99997000-0000-0000-0000-00000000000a");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99997000-0000-0000-0000-00000000000b");
    private static final UUID SALLE = UUID.fromString("99997000-0000-0000-0000-0000000000a1");
    private static final UUID SALLE_AUTRE = UUID.fromString("99997000-0000-0000-0000-0000000000b1");
    private static final UUID MATIN = UUID.fromString("99997000-0000-0000-0000-0000000000c1");
    private static final UUID SOIR = UUID.fromString("99997000-0000-0000-0000-0000000000c2");
    private static final UUID G_OK = UUID.fromString("99997000-0000-0000-0000-0000000000d1");
    private static final UUID G_PANNE = UUID.fromString("99997000-0000-0000-0000-0000000000d2");
    private static final UUID G_AUTRE_CENTRE = UUID.fromString("99997000-0000-0000-0000-0000000000d3");

    private static final UUID G_ISO = UUID.fromString("99997000-0000-0000-0000-0000000000d4");
    private static final UUID SALLE_ISO = UUID.fromString("99997000-0000-0000-0000-0000000000a2");

    @Autowired
    private PlanningAffectationQueryService service;
    @Autowired
    private PlanningSemaineQueryService semaineService;
    @Autowired
    private PlanningParametresService parametresService;
    @Autowired
    private PlanningParametresPort parametres;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanup();
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'PL-S1', 'Salle test')", SALLE, CENTRE);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'PL-S2', 'Salle autre centre')", SALLE_AUTRE, AUTRE_CENTRE);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'PL1', 'Matin')", MATIN, CENTRE);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'PL2', 'Soir')", SOIR, CENTRE);
        insertGenerateur(G_OK, CENTRE, SALLE, "PL-G01", "EN_SERVICE");
        insertGenerateur(G_PANNE, CENTRE, SALLE, "PL-G02", "HORS_SERVICE");
        insertGenerateur(G_AUTRE_CENTRE, AUTRE_CENTRE, SALLE_AUTRE, "PL-G03", "EN_SERVICE");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM serologies_patient WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        jdbc.update("DELETE FROM patients WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        jdbc.update("DELETE FROM center_closure_day WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        jdbc.update("DELETE FROM planning_parametres WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        jdbc.update("DELETE FROM gmao_equipements WHERE id IN (?, ?, ?, ?)", G_OK, G_PANNE, G_AUTRE_CENTRE, G_ISO);
        jdbc.update("DELETE FROM position_creneau WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        jdbc.update("DELETE FROM salle WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
    }

    @Test
    void only_the_center_data_and_working_generators_are_used() {
        Resultat r = proposer(null, null, 20, null, null);

        assertEquals(1, r.salles().size());
        assertEquals(2, r.creneaux().size());
        assertFalse(r.propositions().isEmpty());
        assertTrue(r.propositions().stream().allMatch(p -> p.generateur().id().equals(G_OK)),
                "seul le générateur en service du centre peut être proposé");
        assertEquals(1, r.grille().stream().mapToInt(CaseGrille::capacite).max().orElse(0));
    }

    @Test
    void an_active_patient_takes_his_place_and_a_transferred_patient_frees_it() {
        UUID patient = UUID.randomUUID();
        insertPatient(patient, "PERMANENT", G_OK, MATIN, EnumSet.of(JourSemaine.LUNDI, JourSemaine.MERCREDI, JourSemaine.VENDREDI));

        Resultat occupe = proposer(null, MATIN, 20, null, null);
        assertTrue(occupe.propositions().stream().filter(p -> p.creneau().id().equals(MATIN))
                .allMatch(p -> p.jours().stream().noneMatch(Set.of(JourSemaine.LUNDI, JourSemaine.MERCREDI, JourSemaine.VENDREDI)::contains)));

        jdbc.update("UPDATE patients SET etat_patient = 'TRANSFERE' WHERE id = ?", patient);
        Resultat libere = proposer(null, MATIN, 20, null, null);
        assertTrue(libere.propositions().stream().anyMatch(p -> p.creneau().id().equals(MATIN)
                && Set.copyOf(p.jours()).equals(Set.of(JourSemaine.LUNDI, JourSemaine.MERCREDI, JourSemaine.VENDREDI))));
    }

    @Test
    void editing_a_patient_releases_his_own_current_placement() {
        UUID patient = UUID.randomUUID();
        Set<JourSemaine> lmv = EnumSet.of(JourSemaine.LUNDI, JourSemaine.MERCREDI, JourSemaine.VENDREDI);
        insertPatient(patient, "PERMANENT", G_OK, MATIN, lmv);

        Resultat avecLuiMeme = proposer(lmv, MATIN, 20, null, null);
        Resultat enModification = proposer(lmv, MATIN, 20, patient, null);

        assertTrue(avecLuiMeme.propositions().stream().noneMatch(p -> p.creneau().id().equals(MATIN)));
        assertTrue(enModification.propositions().stream().anyMatch(p -> p.creneau().id().equals(MATIN)));
    }

    @Test
    void patients_of_another_center_never_take_a_place_in_this_center() {
        insertPatientAutreCentre();

        Resultat r = proposer(null, null, 20, null, null);

        Proposition meilleure = r.propositions().get(0);
        assertTrue(meilleure.score() >= 90);
        assertEquals(0, r.grille().stream().mapToInt(CaseGrille::occupes).sum());
    }

    // ───────────────────────────── Paramétrage, isolement, fermetures, semaine ─────────────────────────────

    @Test
    void weekly_closed_days_are_never_proposed_and_are_scoped_to_the_center() {
        parametres.enregistrer(CENTRE, new PlanningParametres(EnumSet.complementOf(EnumSet.of(JourSemaine.VENDREDI)), Set.of()));

        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'PL3', 'Matin')",
                UUID.randomUUID(), AUTRE_CENTRE);

        Resultat r = proposer(null, null, 30, null, null);
        Resultat autreCentre = service.proposer(AUTRE_CENTRE, 3, null, null, null, 30, null, null);

        assertFalse(r.propositions().isEmpty());
        assertTrue(r.propositions().stream().noneMatch(p -> p.jours().contains(JourSemaine.VENDREDI)));
        assertTrue(autreCentre.propositions().stream().anyMatch(p -> p.jours().contains(JourSemaine.VENDREDI)),
                "le paramétrage d'un centre ne s'applique pas aux autres");
    }

    @Test
    void a_patient_at_risk_detected_from_his_latest_serology_is_only_placed_in_the_isolation_room() {
        UUID salleIso = SALLE_ISO;
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'PL-S3', 'Isolement')", salleIso, CENTRE);
        insertGenerateur(G_ISO, CENTRE, salleIso, "PL-G09", "EN_SERVICE");
        parametres.enregistrer(CENTRE, new PlanningParametres(EnumSet.allOf(JourSemaine.class), Set.of(salleIso)));
        UUID patient = UUID.randomUUID();
        insertPatient(patient, "PERMANENT", null, MATIN, EnumSet.of(JourSemaine.DIMANCHE));
        insertSerologie(patient, "AG_HBS", "NEGATIF", LocalDate.of(2025, 1, 1));
        insertSerologie(patient, "AG_HBS", "POSITIF", LocalDate.of(2026, 1, 1));

        Resultat auto = proposer(null, null, 20, patient, null);
        Resultat sain = proposer(null, null, 20, patient, false);

        assertTrue(auto.patientARisque());
        assertFalse(auto.propositions().isEmpty());
        assertTrue(auto.propositions().stream().allMatch(p -> p.salle().id().equals(salleIso)));
        assertFalse(sain.patientARisque());
        assertTrue(sain.propositions().stream().noneMatch(p -> p.salle().id().equals(salleIso)));

        insertSerologie(patient, "AG_HBS", "NEGATIF", LocalDate.of(2026, 6, 1));   // guérison : le risque disparaît
        assertFalse(proposer(null, null, 20, patient, null).patientARisque());
    }

    @Test
    void a_patient_at_risk_without_isolation_room_gets_an_alert() {
        Resultat r = proposer(null, null, 20, null, true);

        assertTrue(r.propositions().isEmpty());
        assertFalse(r.alertes().isEmpty());
    }

    @Test
    void dated_closures_of_the_center_are_reported_on_proposals_but_not_those_of_another_center() {
        LocalDate lundi = LocalDate.now(ZoneOffset.UTC).plusDays(10).with(java.time.DayOfWeek.MONDAY);
        if (!lundi.isAfter(LocalDate.now(ZoneOffset.UTC))) lundi = lundi.plusWeeks(1);
        jdbc.update("INSERT INTO center_closure_day (id, center_id, day_date, reason) VALUES (?, ?, ?, 'Travaux')",
                UUID.randomUUID(), CENTRE, lundi);
        jdbc.update("INSERT INTO center_closure_day (id, center_id, day_date, reason) VALUES (?, ?, ?, 'Autre')",
                UUID.randomUUID(), AUTRE_CENTRE, lundi);

        Resultat r = proposer(EnumSet.of(JourSemaine.LUNDI, JourSemaine.MERCREDI, JourSemaine.VENDREDI), MATIN, 5, null, null);

        Proposition p = r.propositions().get(0);
        assertEquals(1, p.fermetures().size());
        assertEquals("Travaux", p.fermetures().get(0).motif());
        assertEquals(lundi, p.fermetures().get(0).date());
    }

    @Test
    void the_weekly_planning_lists_the_patients_of_the_center_and_flags_closed_days() {
        UUID patient = UUID.randomUUID();
        insertPatient(patient, "PERMANENT", G_OK, MATIN, EnumSet.of(JourSemaine.LUNDI));
        insertPatientAutreCentre();
        parametres.enregistrer(CENTRE, new PlanningParametres(EnumSet.complementOf(EnumSet.of(JourSemaine.SAMEDI)), Set.of()));
        LocalDate dimanche = LocalDate.of(2026, 9, 27);

        SemainePlanning s = semaineService.semaine(CENTRE, dimanche.plusDays(3));   // mercredi

        assertEquals(dimanche, s.debut());
        assertTrue(s.jours().get(JourSemaine.SAMEDI.ordinal()).ferme());
        long occupants = s.cellules().stream().mapToLong(c -> c.occupants().size()).sum();
        assertEquals(1, occupants, "seul le patient du centre apparaît");
        assertEquals("Plan Test", s.cellules().stream().flatMap(c -> c.occupants().stream()).findFirst().orElseThrow().nom());
        assertTrue(s.conflits().isEmpty());
    }

    @Test
    void isolation_rooms_must_belong_to_the_center() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> parametresService.enregistrer(CENTRE, EnumSet.allOf(JourSemaine.class), Set.of(SALLE_AUTRE), 4, 3));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> parametresService.enregistrer(CENTRE, Set.of(), Set.of(), 4, 3));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> parametresService.enregistrer(CENTRE, EnumSet.of(JourSemaine.LUNDI), Set.of(SALLE), 4, 0));
        parametresService.enregistrer(CENTRE, EnumSet.of(JourSemaine.LUNDI), Set.of(SALLE), 4, 5);
        assertEquals(Set.of(SALLE), parametresService.lire(CENTRE).sallesIsolement());
        assertEquals(5, parametresService.lire(CENTRE).patientsParPosteEtSerie(),
                "le nombre de patients par poste et par série est paramétrable par centre");
        assertEquals(3, parametresService.lire(AUTRE_CENTRE).patientsParPosteEtSerie(), "défaut pour un autre centre");
    }

    private Resultat proposer(Set<JourSemaine> jours, UUID creneau, int limite, UUID patient, Boolean isolement) {
        return service.proposer(CENTRE, 3, jours, creneau, null, limite, patient, isolement);
    }

    private void insertSerologie(UUID patient, String marqueur, String resultat, LocalDate date) {
        jdbc.update("INSERT INTO serologies_patient (id, center_id, date_prelevement, marqueur, patient_id, resultat, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), CENTRE, date, marqueur, patient, resultat,
                OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void insertGenerateur(UUID id, UUID centre, UUID salle, String code, String statut) {
        jdbc.update("INSERT INTO gmao_equipements (id, code, designation, type, centre_id, statut, date_installation, "
                        + "salle_id, date_creation, cree_par) VALUES (?, ?, ?, 'GENERATEUR_DIALYSE', ?, ?, ?, ?, ?, ?)",
                id, code, "Générateur " + code, centre, statut, OffsetDateTime.now(ZoneOffset.UTC), salle,
                OffsetDateTime.now(ZoneOffset.UTC), new UUID(0, 0));
    }

    private void insertPatient(UUID id, String etat, UUID generateur, UUID creneau, Set<JourSemaine> jours) {
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, salle_id, position_id, generateur_id, jour_dimanche, jour_lundi, jour_mardi, "
                        + "jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi, created_at) "
                        + "VALUES (?, ?, ?, 'Test', 'Plan', 'M', ?, ?, 'NON_VACANCIER', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, CENTRE, "PL-" + id.toString().substring(0, 8), LocalDate.of(2026, 1, 1),
                "PL-ASS-" + id.toString().substring(0, 8), etat, SALLE, creneau, generateur,
                jours.contains(JourSemaine.DIMANCHE), jours.contains(JourSemaine.LUNDI), jours.contains(JourSemaine.MARDI),
                jours.contains(JourSemaine.MERCREDI), jours.contains(JourSemaine.JEUDI), jours.contains(JourSemaine.VENDREDI),
                jours.contains(JourSemaine.SAMEDI), OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void insertPatientAutreCentre() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, salle_id, position_id, generateur_id, jour_lundi, jour_mercredi, jour_vendredi, created_at) "
                        + "VALUES (?, ?, ?, 'Autre', 'Centre', 'F', ?, ?, 'NON_VACANCIER', ?, ?, ?, TRUE, TRUE, TRUE, ?)",
                id, AUTRE_CENTRE, "PL-AC-" + id.toString().substring(0, 6), LocalDate.of(2026, 1, 1),
                "PL-ASS-AC-" + id.toString().substring(0, 6), SALLE_AUTRE, MATIN, G_AUTRE_CENTRE,
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
