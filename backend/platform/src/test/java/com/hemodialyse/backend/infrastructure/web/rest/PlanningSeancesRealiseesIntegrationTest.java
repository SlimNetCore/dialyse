package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.planning.PlanningSemaineQueryService;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.CellulePlanning;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.OccupantPlanning;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.SemainePlanning;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Séance validée le lundi 05/10/2026 puis jours de dialyse du patient passés au mercredi : la séance garde la place du
 * jour où elle a eu lieu et reste visible sur le planning de la semaine ; la séance prévue le mercredi est signalée
 * comme peut-être en trop. Les séances d'un autre centre n'apparaissent jamais.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PlanningSeancesRealiseesIntegrationTest {

    private static final UUID SOC = UUID.fromString("99999100-0000-0000-0000-000000000001");
    private static final UUID C1 = UUID.fromString("99999100-0000-0000-0000-0000000000c1");
    private static final UUID C2 = UUID.fromString("99999100-0000-0000-0000-0000000000c2");
    private static final LocalDate LUNDI = LocalDate.of(2026, 10, 5);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SeanceUseCase seances;
    @Autowired
    private PlanningSemaineQueryService planning;

    private UUID salle;
    private UUID matin;
    private UUID g1;
    private UUID g2;
    private UUID patient;
    private UUID patientAutreCentre;

    @BeforeEach
    void setup() {
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC, "ZT-SR1", "Société SR1");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", C1, "ZT-SR1-A",
                "ZT-SR1-A", SOC);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", C2, "ZT-SR1-B",
                "ZT-SR1-B", SOC);
        salle = UUID.randomUUID();
        matin = UUID.randomUUID();
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?,?,?,?)", salle, C1, "ZT-SR", "Salle A");
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?,?,?,?)", matin, C1, "ZT-SRM",
                "Matin");
        g1 = generateur(C1, "ZT-SR-G1", salle);
        g2 = generateur(C1, "ZT-SR-G2", salle);
        patient = patient(C1, "Karim", salle, matin, g1);
        UUID salleAutre = UUID.randomUUID();
        UUID matinAutre = UUID.randomUUID();
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?,?,?,?)", salleAutre, C2, "ZT-SR2", "Salle B");
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?,?,?,?)", matinAutre, C2,
                "ZT-SRM2", "Matin B");
        patientAutreCentre = patient(C2, "Etranger", salleAutre, matinAutre, generateur(C2, "ZT-SR-G9", salleAutre));
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("seances", "patients", "position_creneau", "salle")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", C1, C2);
        }
        jdbc.update("DELETE FROM gmao_equipements WHERE centre_id IN (?, ?)", C1, C2);
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-SR1%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-SR1%'");
    }

    @Test
    void the_monday_session_stays_visible_after_the_days_move_to_wednesday() {
        UUID seance = seanceCreee(C1, patient, LUNDI);
        Seance validee = seances.validate(CenterId.of(C1), seance, "inf-01", List.of());
        assertThat(validee.getPlace()).isNotNull();
        assertThat(validee.getPlace().generateurId()).isEqualTo(g1);
        UUID autre = seanceCreee(C2, patientAutreCentre, LUNDI);
        seances.validate(CenterId.of(C2), autre, "inf-02", List.of());

        // juste après : ses jours passent au mercredi, sur un autre générateur
        jdbc.update("UPDATE patients SET jour_lundi = FALSE, jour_mercredi = TRUE, generateur_id = ? WHERE id = ?",
                g2, patient);

        SemainePlanning semaine = planning.semaine(C1, LUNDI);
        OccupantPlanning lundi = cellule(semaine, JourSemaine.LUNDI).occupants().getFirst();
        assertThat(lundi.patientId()).isEqualTo(patient);
        assertThat(lundi.realiseeHorsPlanning()).isTrue();
        assertThat(lundi.generateurCode()).isEqualTo("ZT-SR-G1");
        OccupantPlanning mercredi = cellule(semaine, JourSemaine.MERCREDI).occupants().getFirst();
        assertThat(mercredi.dejaRealiseeLe()).isEqualTo(LUNDI);
        assertThat(semaine.conflits()).isEmpty();
        assertThat(semaine.cellules()).flatExtracting(CellulePlanning::occupants)
                .extracting(OccupantPlanning::patientId).doesNotContain(patientAutreCentre);
        assertThat(jdbc.queryForObject("SELECT generateur_id FROM seances WHERE id = ?", UUID.class, seance))
                .as("place figée en base").isEqualTo(g1);
    }

    private static CellulePlanning cellule(SemainePlanning s, JourSemaine jour) {
        return s.cellules().stream().filter(c -> c.jour() == jour).findFirst().orElseThrow();
    }

    private UUID seanceCreee(UUID centre, UUID patientId, LocalDate date) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, hors_planning) "
                + "VALUES (?, ?, ?, ?, 'CREE', CURRENT_TIMESTAMP, FALSE)", id, patientId, centre, Date.valueOf(date));
        return id;
    }

    private UUID generateur(UUID centre, String code, UUID salleId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO gmao_equipements (id, code, designation, type, centre_id, statut, salle_id, "
                        + "date_installation, date_creation, cree_par) VALUES (?, ?, ?, 'GENERATEUR_DIALYSE', ?, 'EN_SERVICE', ?, ?, ?, ?)",
                id, code, "Générateur " + code, centre, salleId, OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC), new UUID(0, 0));
        return id;
    }

    private UUID patient(UUID centre, String nom, UUID salleId, UUID creneau, UUID generateur) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, numero_assurance, "
                        + "date_admission, type_patient, etat_patient, qualite_assure, sous_kt, epo_enabled, salle_id, "
                        + "position_id, generateur_id, jour_lundi, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,?,?,?,TRUE,CURRENT_TIMESTAMP)",
                id, centre, "ZT-" + id.toString().substring(0, 8), nom, "Prenom", "M", Date.valueOf("1970-05-05"),
                "ASS-" + id.toString().substring(0, 8), Date.valueOf("2026-01-02"), "NON_VACANCIER", "PERMANENT",
                "ASSURE", false, salleId, creneau, generateur);
        return id;
    }
}
