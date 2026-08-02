package com.hemodialyse.backend.domain.patient.port;

import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port In — Patient use cases (domain-level).
 */
public interface PatientUseCase {

    Patient createPatient(CenterId centerId, CreatePatientCommand cmd);
    Patient updatePatient(CenterId centerId, UUID patientId, CreatePatientCommand cmd);
    Patient getPatient(CenterId centerId, UUID patientId);
    List<Patient> listPatients(CenterId centerId);

    /** Command object carrying all wizard data */
    record CreatePatientCommand(
        // Step 1 — Generalites
        String civilite, String nom, String prenom, String sexe,
        String groupeSanguin, int nombreEnfants,
        LocalDate dateAdmission, LocalDate dateNaissance,
        String lieuNaissance, String situationFamiliale,
        String profession, String adresse,
        String telPersonnel, String telMobile, String telBureau, String email,
        boolean sousKt, boolean epoEnabled, LocalDate epoDate,
        boolean ferEnabled, LocalDate ferDate,
        String observation, String qualiteAssure, String photoBase64,
        boolean enSommeil,

        // Step 2 — Assurance
        String numeroAssurance,
        UUID centrePayeurId,
        String assureNumeroAssurance,
        String assureSexe, String assureNom, String assurePrenom,
        String assureDateNaissance, String assureTelPersonnel, String assureAdresse,
        String assureGroupeSanguin, String assureTelMobile, String assureTelBureau,
        String assureHistoryJson,

        // Step 6 — Pièces jointes
        String piecesJointesJson,

        // Step 3 — Affectation
        UUID medecinTraitantId, UUID salleId, UUID positionId,
        UUID transporteurAllerId, UUID transporteurRetourId, UUID categorieTransportId,
        UUID generateurId,
        String etatPatient,
        LocalDate dateEvenementEtat,
        boolean jourDimanche, boolean jourLundi, boolean jourMardi,
        boolean jourMercredi, boolean jourJeudi, boolean jourVendredi, boolean jourSamedi,

        // Step 4 — Attestation
        UUID attestationId,
        LocalDate attestationDebut, LocalDate attestationFin,

        // Step 5 — PEC
        UUID pecId,
        LocalDate pecDateDebutDemande, LocalDate pecDateFinDemande, UUID pecForfaitDemandeId
    ) {}
}
