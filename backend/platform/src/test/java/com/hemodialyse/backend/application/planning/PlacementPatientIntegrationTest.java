package com.hemodialyse.backend.application.planning;

import com.hemodialyse.backend.application.planning.PlacementPatientService.Issue;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.PlanningParametres;
import com.hemodialyse.backend.domain.planning.port.PlanningParametresPort;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cohérence du placement : seul un placement conforme aux règles de la planification est enregistré, la salle
 * d'isolement est une propriété de la salle, et un patient devenu à risque est replacé en isolement (ou signalé).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PlacementPatientIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99997100-0000-0000-0000-00000000000a");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99997100-0000-0000-0000-00000000000b");
    private static final UUID SALLE = UUID.fromString("99997100-0000-0000-0000-0000000000a1");
    private static final UUID SALLE_ISO = UUID.fromString("99997100-0000-0000-0000-0000000000a2");
    private static final UUID SALLE_AUTRE = UUID.fromString("99997100-0000-0000-0000-0000000000a3");
    private static final UUID MATIN = UUID.fromString("99997100-0000-0000-0000-0000000000c1");
    private static final UUID G_OK = UUID.fromString("99997100-0000-0000-0000-0000000000d1");
    private static final UUID G_PANNE = UUID.fromString("99997100-0000-0000-0000-0000000000d2");
    private static final UUID G_ISO = UUID.fromString("99997100-0000-0000-0000-0000000000d3");
    private static final UUID G_AUTRE = UUID.fromString("99997100-0000-0000-0000-0000000000d4");
    private static final Set<JourSemaine> LUNDI = EnumSet.of(JourSemaine.LUNDI);

    @Autowired
    private PlacementPatientService service;
    @Autowired
    private PlanningParametresPort parametres;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanup();
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'PP-S1', 'Salle normale')", SALLE, CENTRE);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, isolement) VALUES (?, ?, 'PP-S2', 'Isolement', 'OUI')",
                SALLE_ISO, CENTRE);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'PP-S3', 'Autre centre')", SALLE_AUTRE,
                AUTRE_CENTRE);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'PP1', 'Matin')", MATIN, CENTRE);
        insertGenerateur(G_OK, CENTRE, SALLE, "PP-G01", "EN_SERVICE");
        insertGenerateur(G_PANNE, CENTRE, SALLE, "PP-G02", "HORS_SERVICE");
        insertGenerateur(G_ISO, CENTRE, SALLE_ISO, "PP-G03", "EN_SERVICE");
        insertGenerateur(G_AUTRE, AUTRE_CENTRE, SALLE_AUTRE, "PP-G04", "EN_SERVICE");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM serologies_patient WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        jdbc.update("DELETE FROM patients WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        jdbc.update("DELETE FROM planning_parametres WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        jdbc.update("DELETE FROM gmao_equipements WHERE id IN (?, ?, ?, ?)", G_OK, G_PANNE, G_ISO, G_AUTRE);
        jdbc.update("DELETE FROM position_creneau WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        jdbc.update("DELETE FROM salle WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
    }

    @Test
    void the_isolation_flag_belongs_to_the_room_and_is_scoped_to_the_center() {
        assertEquals(Set.of(SALLE_ISO), parametres.lire(CENTRE).sallesIsolement());
        assertEquals(Set.of(), parametres.lire(AUTRE_CENTRE).sallesIsolement());

        parametres.enregistrer(CENTRE, new PlanningParametres(EnumSet.allOf(JourSemaine.class), Set.of(SALLE)));

        assertEquals(Set.of(SALLE), parametres.lire(CENTRE).sallesIsolement());
        assertEquals("NON", jdbc.queryForObject("SELECT isolement FROM salle WHERE id = ?", String.class, SALLE_ISO));
        assertEquals("NON", jdbc.queryForObject("SELECT isolement FROM salle WHERE id = ?", String.class, SALLE_AUTRE),
                "une autre centre n'est pas touché");
    }

    @Test
    void a_fiche_without_any_placement_is_accepted() {
        assertDoesNotThrow(() -> service.verifier(CENTRE, null, null, null, null, Set.of()));
    }

    @Test
    void a_valid_placement_is_accepted() {
        assertDoesNotThrow(() -> service.verifier(CENTRE, null, SALLE, MATIN, G_OK, LUNDI));
    }

    @Test
    void an_incomplete_placement_is_refused() {
        assertEquals("PLACEMENT_INCOMPLET",
                assertThrows(BusinessException.class, () -> service.verifier(CENTRE, null, SALLE, null, null, LUNDI)).getCode());
        assertEquals("PLACEMENT_INCOMPLET",
                assertThrows(BusinessException.class, () -> service.verifier(CENTRE, null, SALLE, MATIN, G_OK, Set.of())).getCode());
    }

    @Test
    void a_generator_already_used_the_same_day_and_slot_is_refused() {
        insertPatient(UUID.randomUUID(), SALLE, G_OK, LUNDI);

        assertEquals("PLACEMENT_GENERATEUR_OCCUPE", assertThrows(BusinessException.class,
                () -> service.verifier(CENTRE, null, SALLE, MATIN, G_OK, LUNDI)).getCode());
        assertDoesNotThrow(() -> service.verifier(CENTRE, null, SALLE, MATIN, G_OK, EnumSet.of(JourSemaine.MARDI)));
    }

    @Test
    void a_generator_out_of_service_or_of_another_room_or_center_is_refused() {
        for (UUID generateur : new UUID[]{G_PANNE, G_ISO, G_AUTRE}) {
            assertEquals("PLACEMENT_GENERATEUR_INCONNU", assertThrows(BusinessException.class,
                    () -> service.verifier(CENTRE, null, SALLE, MATIN, generateur, LUNDI)).getCode());
        }
        assertEquals("PLACEMENT_SALLE_INCONNUE", assertThrows(BusinessException.class,
                () -> service.verifier(CENTRE, null, SALLE_AUTRE, MATIN, G_AUTRE, LUNDI)).getCode());
    }

    @Test
    void a_closed_weekday_is_refused() {
        parametres.enregistrer(CENTRE, new PlanningParametres(EnumSet.complementOf(EnumSet.of(JourSemaine.LUNDI)), Set.of(SALLE_ISO)));

        assertEquals("PLACEMENT_JOUR_FERME", assertThrows(BusinessException.class,
                () -> service.verifier(CENTRE, null, SALLE, MATIN, G_OK, LUNDI)).getCode());
    }

    @Test
    void a_patient_at_risk_can_only_be_placed_in_an_isolation_room() {
        UUID patient = UUID.randomUUID();
        insertPatient(patient, null, null, Set.of());
        insertSerologie(patient, "AG_HBS", "POSITIF", LocalDate.of(2026, 1, 1));

        assertEquals("PLACEMENT_ISOLEMENT_REQUIS", assertThrows(BusinessException.class,
                () -> service.verifier(CENTRE, patient, SALLE, MATIN, G_OK, LUNDI)).getCode());
        assertDoesNotThrow(() -> service.verifier(CENTRE, patient, SALLE_ISO, MATIN, G_ISO, LUNDI));
    }

    @Test
    void an_unchanged_placement_never_blocks_the_edit_of_another_field() {
        UUID patient = UUID.randomUUID();
        insertPatient(patient, SALLE, G_PANNE, LUNDI);   // placement hérité devenu invalide

        assertDoesNotThrow(() -> service.verifier(CENTRE, patient, SALLE, MATIN, G_PANNE, LUNDI));
        assertEquals("PLACEMENT_GENERATEUR_INCONNU", assertThrows(BusinessException.class,
                () -> service.verifier(CENTRE, patient, SALLE, MATIN, G_PANNE, EnumSet.of(JourSemaine.MARDI))).getCode());
    }

    @Test
    void a_patient_who_becomes_positive_is_moved_to_the_isolation_room_keeping_his_days() {
        UUID patient = UUID.randomUUID();
        insertPatient(patient, SALLE, G_OK, LUNDI);
        assertEquals(Issue.AUCUN_RISQUE, service.reaffecterSiRisque(CENTRE, patient));

        insertSerologie(patient, "VIH_AC", "POSITIF", LocalDate.of(2026, 9, 1));
        assertEquals(Issue.DEPLACE, service.reaffecterSiRisque(CENTRE, patient));

        var ligne = jdbc.queryForMap("SELECT salle_id, generateur_id, jour_lundi FROM patients WHERE id = ?", patient);
        assertEquals(SALLE_ISO, ligne.get("SALLE_ID"));
        assertEquals(G_ISO, ligne.get("GENERATEUR_ID"));
        assertEquals(Boolean.TRUE, ligne.get("JOUR_LUNDI"));
        assertEquals(Issue.DEJA_CONFORME, service.reaffecterSiRisque(CENTRE, patient));
    }

    @Test
    void without_an_isolation_slot_the_patient_stays_in_place_and_is_flagged() {
        jdbc.update("UPDATE salle SET isolement = 'NON' WHERE center_id = ?", CENTRE);
        UUID patient = UUID.randomUUID();
        insertPatient(patient, SALLE, G_OK, LUNDI);
        insertSerologie(patient, "AG_HBS", "POSITIF", LocalDate.of(2026, 9, 1));

        assertEquals(Issue.A_REPLANIFIER, service.reaffecterSiRisque(CENTRE, patient));
        assertEquals(SALLE, jdbc.queryForObject("SELECT salle_id FROM patients WHERE id = ?", UUID.class, patient));
    }

    @Test
    void a_patient_without_placement_or_of_another_center_is_left_alone() {
        UUID patient = UUID.randomUUID();
        insertPatient(patient, null, null, Set.of());
        insertSerologie(patient, "AG_HBS", "POSITIF", LocalDate.of(2026, 9, 1));

        assertEquals(Issue.NON_PLACE, service.reaffecterSiRisque(CENTRE, patient));
        assertEquals(Issue.AUCUN_RISQUE, service.reaffecterSiRisque(AUTRE_CENTRE, patient),
                "le patient d'un autre centre n'est pas évalué");
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

    private void insertPatient(UUID id, UUID salle, UUID generateur, Set<JourSemaine> jours) {
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, salle_id, position_id, generateur_id, jour_dimanche, jour_lundi, jour_mardi, "
                        + "jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi, created_at) "
                        + "VALUES (?, ?, ?, 'Test', 'Place', 'M', ?, ?, 'NON_VACANCIER', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, CENTRE, "PP-" + id.toString().substring(0, 8), LocalDate.of(2026, 1, 1),
                "PP-ASS-" + id.toString().substring(0, 8), salle, salle == null ? null : MATIN, generateur,
                jours.contains(JourSemaine.DIMANCHE), jours.contains(JourSemaine.LUNDI), jours.contains(JourSemaine.MARDI),
                jours.contains(JourSemaine.MERCREDI), jours.contains(JourSemaine.JEUDI), jours.contains(JourSemaine.VENDREDI),
                jours.contains(JourSemaine.SAMEDI), OffsetDateTime.now(ZoneOffset.UTC));
    }
}
