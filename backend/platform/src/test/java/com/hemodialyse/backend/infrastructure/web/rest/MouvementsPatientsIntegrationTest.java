package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.patient.LiberationPlacesService;
import com.hemodialyse.backend.application.patient.MouvementPatientQueryService;
import com.hemodialyse.backend.domain.patient.model.MouvementLigne;
import com.hemodialyse.backend.domain.patient.model.TypeMouvementPatient;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.port.PlanningDonneesPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Libération des places des patients sortis et historique des mouvements : échéance respectée (la place du transféré
 * reste due jusqu'à sa date), idempotence, isolation entre centres, trace de l'affectation quittée et lecture du
 * planning par le patient « à libérer ».
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MouvementsPatientsIntegrationTest {

    private static final UUID SOC = UUID.fromString("99996000-0000-0000-0000-000000000001");
    private static final UUID C1 = UUID.fromString("99996000-0000-0000-0000-0000000000c1");
    private static final UUID C2 = UUID.fromString("99996000-0000-0000-0000-0000000000c2");

    private final LocalDate aujourdhui = LocalDate.now(ZoneOffset.UTC);
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private LiberationPlacesService liberation;
    @Autowired
    private MouvementPatientQueryService mouvements;
    @Autowired
    private PlanningDonneesPort planning;

    private UUID salle;
    private UUID creneau;
    private UUID decede;
    private UUID transfereBientot;
    private UUID permanent;
    private UUID autreCentre;

    private static Occupation occupation(List<Occupation> occupations, UUID patient) {
        return occupations.stream().filter(o -> o.patientId().equals(patient)).findFirst().orElseThrow();
    }

    @BeforeEach
    void setup() {
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC, "ZT-MV1", "Société MV1");
        centre(C1, "ZT-MV1-A");
        centre(C2, "ZT-MV1-B");
        salle = UUID.randomUUID();
        creneau = UUID.randomUUID();
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?,?,?,?)", salle, C1, "ZT-S1", "Salle 1");
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?,?,?,?)", creneau, C1, "ZT-P1",
                "Matin");
        decede = patient(C1, "DECEDE", aujourdhui.minusDays(3), true);
        transfereBientot = patient(C1, "TRANSFERE", aujourdhui.plusDays(5), true);
        permanent = patient(C1, "PERMANENT", null, true);
        autreCentre = patient(C2, "DECEDE", aujourdhui.minusDays(3), true);
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("mouvement_patient", "patients", "salle", "position_creneau")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", C1, C2);
        }
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-MV1%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-MV1%'");
    }

    @Test
    void releases_only_the_expired_places_of_the_center_and_keeps_the_assignment_in_the_history() {
        assertEquals(1, liberation.libererCentre(CenterId.of(C1)), "seul le décédé a une place échue");

        assertNull(salleDe(decede));
        assertFalse(joursActifs(decede), "les jours de dialyse sont retirés");
        assertNotNull(salleDe(transfereBientot), "transfert futur : place encore due");
        assertNotNull(salleDe(permanent));
        assertNotNull(salleDe(autreCentre), "un autre centre n'est jamais touché");

        MouvementLigne m = mouvements.lister(C1, decede, null, null, null, 0, 20).items().getFirst();
        assertEquals(TypeMouvementPatient.PLACE_LIBEREE, m.mouvement().type());
        assertTrue(m.mouvement().automatique());
        assertEquals(salle, m.mouvement().salleId());
        assertEquals("Salle 1", m.salleNom());
        assertEquals("Matin", m.creneauLibelle());
        assertEquals("LUNDI,MERCREDI", m.mouvement().joursDialyse());
        assertEquals("DECEDE", m.mouvement().etatNouveau());
    }

    @Test
    void the_release_is_idempotent() {
        assertEquals(1, liberation.libererCentre(CenterId.of(C1)));
        assertEquals(0, liberation.libererCentre(CenterId.of(C1)));

        assertEquals(1, mouvements.lister(C1, decede, null, null, null, 0, 20).total());
    }

    @Test
    void the_history_is_scoped_to_the_center_and_paginated() {
        liberation.libererCentre(CenterId.of(C1));
        liberation.libererCentre(CenterId.of(C2));

        PagedResult<MouvementLigne> c1 = mouvements.lister(C1, null, null, null, null, 0, 20);
        PagedResult<MouvementLigne> c2 = mouvements.lister(C2, null, null, null, null, 0, 20);

        assertEquals(1, c1.total());
        assertEquals(decede, c1.items().getFirst().mouvement().patientId());
        assertEquals(1, c2.total());
        assertEquals(autreCentre, c2.items().getFirst().mouvement().patientId());
        assertEquals(0, mouvements.lister(C1, null, TypeMouvementPatient.DECES, null, null, 0, 20).total());
        assertEquals(1, mouvements.lister(C1, null, TypeMouvementPatient.PLACE_LIBEREE, aujourdhui, aujourdhui, 0, 1)
                .items().size());
        assertEquals(0, mouvements.lister(C1, null, null, aujourdhui.plusDays(1), null, 0, 20).total());
    }

    @Test
    void the_planning_keeps_a_future_exit_until_its_last_day_and_drops_a_past_one() {
        List<Occupation> occupations = planning.charger(C1, null).occupations();

        Occupation futur = occupation(occupations, transfereBientot);
        assertEquals(aujourdhui.plusDays(5), futur.dernierJour(), "le transféré garde sa place jusqu'à son transfert");
        assertEquals(LocalDate.of(2026, 1, 2), futur.premierJour(), "le séjour commence à l'admission");
        assertNull(occupation(occupations, permanent).dernierJour());
        assertTrue(occupations.stream().noneMatch(o -> o.patientId().equals(decede)),
                "le décédé (3 jours) n'occupe plus la place");
        assertTrue(occupations.stream().noneMatch(o -> o.patientId().equals(autreCentre)), "isolation par centre");
    }

    private UUID salleDe(UUID patient) {
        return jdbc.queryForObject("SELECT salle_id FROM patients WHERE id = ?", UUID.class, patient);
    }

    private boolean joursActifs(UUID patient) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE id = ? AND (jour_lundi = TRUE OR "
                + "jour_mercredi = TRUE)", Integer.class, patient);
        return n != null && n > 0;
    }

    private void centre(UUID id, String code) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", id, code, code, SOC);
    }

    private UUID patient(UUID centre, String etat, LocalDate dateEvenement, boolean place) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, numero_assurance, "
                        + "date_admission, type_patient, etat_patient, date_evenement_etat, qualite_assure, sous_kt, "
                        + "epo_enabled, salle_id, position_id, jour_lundi, jour_mercredi, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,?,?,TRUE,TRUE,CURRENT_TIMESTAMP)",
                id, centre, "ZT-" + id.toString().substring(0, 8), "NOM", "Prenom", "M", Date.valueOf("1970-05-05"),
                "ASS-" + id.toString().substring(0, 8), Date.valueOf("2026-01-02"), "NON_VACANCIER", etat,
                dateEvenement == null ? null : Date.valueOf(dateEvenement), "ASSURE", false,
                place ? salle : null, place ? creneau : null);
        return id;
    }
}
