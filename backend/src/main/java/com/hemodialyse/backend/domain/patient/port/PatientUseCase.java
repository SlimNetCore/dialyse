package com.hemodialyse.backend.domain.patient.port;

import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.model.PatientType;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port In — Patient use cases (domain-level).
 */
public interface PatientUseCase {

    Patient createPatient(CenterId centerId, CreatePatientCommand cmd);
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
        String numeroAssurance, PatientType typePatient,
        UUID centrePayeurId,
        String assureSexe, String assureNom, String assurePrenom,
        String assureDateNaissance, String assureTelPersonnel, String assureAdresse,
        String assureGroupeSanguin, String assureTelMobile, String assureTelBureau,

        // Step 3 — Affectation
        UUID medecinTraitantId, UUID salleId, UUID positionId,
        UUID transporteurAllerId, UUID transporteurRetourId, UUID categorieTransportId,
        String etatPatient,
        boolean jourDimanche, boolean jourLundi, boolean jourMardi,
        boolean jourMercredi, boolean jourJeudi, boolean jourVendredi, boolean jourSamedi,

        // Step 4 — Attestation
        LocalDate attestationDebut, LocalDate attestationFin,

        // Step 5 — PEC
        LocalDate pecDateDebutDemande, LocalDate pecDateFinDemande, UUID pecForfaitDemandeId
    ) {}
}

