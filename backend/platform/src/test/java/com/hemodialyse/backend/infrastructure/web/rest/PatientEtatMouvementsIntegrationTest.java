package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.patient.MouvementPatientQueryService;
import com.hemodialyse.backend.application.patient.PatientApplicationService;
import com.hemodialyse.backend.domain.patient.model.MouvementLigne;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.model.TypeMouvementPatient;
import com.hemodialyse.backend.domain.patient.port.PatientUseCase.CreatePatientCommand;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enregistrement d'une fiche patient : cohérence état / date d'évènement, mouvements produits et libération
 * immédiate de la place lorsque l'échéance est déjà dépassée.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PatientEtatMouvementsIntegrationTest {

    private static final UUID SOC = UUID.fromString("99997000-0000-0000-0000-000000000001");
    private static final UUID C1 = UUID.fromString("99997000-0000-0000-0000-0000000000c1");

    private final LocalDate aujourdhui = LocalDate.now(ZoneOffset.UTC);
    private final LocalDate admission = aujourdhui.minusDays(60);
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PatientApplicationService patients;
    @Autowired
    private MouvementPatientQueryService mouvements;
    private UUID salle;
    private UUID creneau;

    @BeforeEach
    void setup() {
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC, "ZT-PE1", "Société PE1");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", C1, "ZT-PE1-A", "C1", SOC);
        salle = UUID.randomUUID();
        creneau = UUID.randomUUID();
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?,?,?,?)", salle, C1, "ZT-PS1", "Salle PE");
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?,?,?,?)", creneau, C1, "ZT-PP1", "Soir");
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("mouvement_patient", "attestation_droit", "assure_patient", "patients", "salle",
                "position_creneau")) {
            try {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", C1);
            } catch (RuntimeException ignoree) {
                // table absente selon la version du schéma de test
            }
        }
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-PE1%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-PE1%'");
    }

    private CreatePatientCommand commande(String numero, String etat, LocalDate dateEvenement, boolean place) {
        return new CreatePatientCommand(
                // généralités : civilité, nom, prénom, sexe, groupe sanguin, enfants, admission, naissance, lieu, famille
                null, "Nom", "Prenom", "M", null, 0, admission, LocalDate.of(1970, 5, 5), null, null,
                // profession, adresse, téléphones (3), email, sous KT, EPO (2), fer (2)
                null, null, null, null, null, null, false, false, null, false, null,
                // observation, qualité d'assuré, photo, en sommeil
                null, "ASSURE_LUI_MEME", null, false,
                // assurance : numéro, centre payeur, assuré (numéro, 9 champs d'identité), historique, pièces jointes
                numero, null, null, null, null, null, null, null, null, null, null, null, null, null,
                // affectation : médecin, salle, créneau, transporteurs (2), catégorie, générateur
                null, place ? salle : null, place ? creneau : null, null, null, null, null,
                // état, date d'évènement, jours (dim → sam)
                etat, dateEvenement, false, place, false, place, false, false, false,
                // attestation (id, début, fin), PEC (id, début, fin, forfait)
                null, aujourdhui.minusDays(30), aujourdhui.plusDays(300), null, null, null, null);
    }

    private Patient creer(String numero) {
        return patients.createPatient(CenterId.of(C1), commande(numero, "PERMANENT", null, true));
    }

    private List<MouvementLigne> historique(Patient p) {
        return mouvements.lister(C1, p.getId().value(), null, null, null, 0, 50).items();
    }

    @Test
    void the_creation_records_the_admission_movement() {
        Patient p = creer("ASS-PE-0001");

        List<MouvementLigne> h = historique(p);

        assertEquals(1, h.size());
        assertEquals(TypeMouvementPatient.ADMISSION, h.getFirst().mouvement().type());
        assertEquals(admission, h.getFirst().mouvement().dateEffet());
        assertEquals("Salle PE", h.getFirst().salleNom());
    }

    @Test
    void an_exit_state_without_date_is_refused_at_creation_and_update() {
        BusinessException creation = assertThrows(BusinessException.class, () ->
                patients.createPatient(CenterId.of(C1), commande("ASS-PE-0002", "DECEDE", null, false)));
        assertEquals("PATIENT_DATE_EVENEMENT_REQUISE", creation.getCode());

        Patient p = creer("ASS-PE-0003");
        BusinessException modification = assertThrows(BusinessException.class, () -> patients.updatePatient(
                CenterId.of(C1), p.getId().value(), commande("ASS-PE-0003", "TRANSFERE", null, true)));
        assertEquals("PATIENT_DATE_EVENEMENT_REQUISE", modification.getCode());
        assertEquals("PERMANENT", patients.getPatient(CenterId.of(C1), p.getId().value()).getEtatPatient());
    }

    @Test
    void an_event_date_before_the_admission_is_refused() {
        Patient p = creer("ASS-PE-0004");

        BusinessException e = assertThrows(BusinessException.class, () -> patients.updatePatient(CenterId.of(C1),
                p.getId().value(), commande("ASS-PE-0004", "DECEDE", admission.minusDays(1), true)));

        assertEquals("PATIENT_DATE_EVENEMENT_AVANT_ADMISSION", e.getCode());
    }

    @Test
    void a_future_transfer_is_recorded_and_keeps_the_place_until_its_date() {
        Patient p = creer("ASS-PE-0005");
        LocalDate transfert = aujourdhui.plusDays(10);

        Patient maj = patients.updatePatient(CenterId.of(C1), p.getId().value(),
                commande("ASS-PE-0005", "TRANSFERE", transfert, true));

        assertEquals(salle, maj.getSalleId(), "la place reste due jusqu'au transfert");
        MouvementLigne m = historique(p).getFirst();
        assertEquals(TypeMouvementPatient.TRANSFERT, m.mouvement().type());
        assertEquals(transfert, m.mouvement().dateEffet());
        assertEquals("PERMANENT", m.mouvement().etatPrecedent());
        assertEquals(2, historique(p).size());
    }

    @Test
    void a_long_past_exit_frees_the_place_immediately_and_traces_it() {
        Patient p = creer("ASS-PE-0006");

        Patient maj = patients.updatePatient(CenterId.of(C1), p.getId().value(),
                commande("ASS-PE-0006", "DECEDE", aujourdhui.minusDays(10), true));

        assertNull(maj.getSalleId());
        assertNull(maj.getPositionId());
        List<TypeMouvementPatient> types = historique(p).stream().map(l -> l.mouvement().type()).toList();
        assertTrue(types.contains(TypeMouvementPatient.DECES));
        assertTrue(types.contains(TypeMouvementPatient.PLACE_LIBEREE));
        MouvementLigne liberation = historique(p).stream()
                .filter(l -> l.mouvement().type() == TypeMouvementPatient.PLACE_LIBEREE).findFirst().orElseThrow();
        assertEquals(salle, liberation.mouvement().salleId(), "l'affectation quittée est conservée dans l'historique");
        assertEquals("Soir", liberation.creneauLibelle());
    }

    @Test
    void an_exit_dated_yesterday_keeps_the_place_for_the_absence_control() {
        Patient p = creer("ASS-PE-0007");

        Patient maj = patients.updatePatient(CenterId.of(C1), p.getId().value(),
                commande("ASS-PE-0007", "TRANSFERE", aujourdhui.minusDays(1), true));

        assertEquals(salle, maj.getSalleId(), "libération différée à la nuit (après le contrôle des absences)");
    }

    @Test
    void saving_without_any_state_change_adds_no_movement() {
        Patient p = creer("ASS-PE-0008");

        patients.updatePatient(CenterId.of(C1), p.getId().value(), commande("ASS-PE-0008", "PERMANENT", null, true));

        assertEquals(1, historique(p).size());
    }

    @Test
    void a_limited_stay_records_the_temporary_stay_and_its_end_date() {
        Patient p = creer("ASS-PE-0009");
        LocalDate fin = aujourdhui.plusDays(15);

        patients.updatePatient(CenterId.of(C1), p.getId().value(), commande("ASS-PE-0009", "VACANCIER_LOCAL", fin, true));

        MouvementLigne m = historique(p).getFirst();
        assertEquals(TypeMouvementPatient.SEJOUR_TEMPORAIRE, m.mouvement().type());
        assertEquals(fin, m.mouvement().dateEffet());
    }
}
