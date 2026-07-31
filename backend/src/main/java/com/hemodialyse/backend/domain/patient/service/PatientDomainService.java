package com.hemodialyse.backend.domain.patient.service;

import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.model.PatientType;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.port.PatientUseCase;
import com.hemodialyse.backend.domain.patient.vo.AssureInfo;
import com.hemodialyse.backend.domain.patient.vo.JoursDialyse;
import com.hemodialyse.backend.domain.patient.vo.NumeroAssurance;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Patient aggregate business rules.
 * <p>
 * Pure domain class depending ONLY on the patient bounded context + Shared Kernel
 * (no cross-context coupling — AGENTS.md §14, prépare le découpage en modules Maven
 * indépendants). Cross-aggregate orchestration (assuré, attestation, PEC) lives in
 * {@code application.patient.PatientApplicationService}, which owns the transactional
 * boundary for the {@link PatientUseCase} port.
 */
public class PatientDomainService implements PatientUseCase {

    private final PatientRepositoryPort patientRepo;

    public PatientDomainService(PatientRepositoryPort patientRepo) {
        this.patientRepo = patientRepo;
    }

    @Override
    public Patient createPatient(CenterId centerId, CreatePatientCommand cmd) {
        // Business rule: uniqueness of insurance number within a center
        if (patientRepo.findByNumeroAssurance(centerId, cmd.numeroAssurance()).isPresent()) {
            throw new IllegalStateException("Numero assurance deja utilise pour ce centre");
        }

        // Business rule: attestation mandatory unless patient is vacancier (from etatPatient)
        String etat = cmd.etatPatient() != null ? cmd.etatPatient() : "PERMANENT";
        boolean isVacancier = "VACANCIER_LOCAL".equals(etat) || "VACANCIER_ETRANGER".equals(etat);
        PatientType type = isVacancier ? PatientType.VACANCIER : PatientType.NON_VACANCIER;
        if (!isVacancier) {
            if (cmd.attestationDebut() == null || cmd.attestationFin() == null) {
                throw new IllegalArgumentException("Attestation obligatoire pour un patient non-vacancier");
            }
        }

        Patient patient = Patient.creer(
            centerId, cmd.nom(), cmd.prenom(), cmd.sexe(),
            cmd.dateAdmission(), cmd.dateNaissance(),
            new NumeroAssurance(cmd.numeroAssurance()), type
        );

        hydrate(patient, cmd, etat);

        return patientRepo.save(patient);
    }

    @Override
    public Patient updatePatient(CenterId centerId, UUID patientId, CreatePatientCommand cmd) {
        Patient patient = patientRepo.findById(PatientId.of(patientId), centerId)
            .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));

        String etat = cmd.etatPatient() != null ? cmd.etatPatient() : patient.getEtatPatient();

        patient.setNom(cmd.nom());
        patient.setPrenom(cmd.prenom());
        patient.setSexe(cmd.sexe());
        patient.setDateAdmission(cmd.dateAdmission());
        patient.setDateNaissance(cmd.dateNaissance());
        hydrate(patient, cmd, etat);

        return patientRepo.save(patient);
    }

    @Override
    public Patient getPatient(CenterId centerId, UUID patientId) {
        return patientRepo.findById(PatientId.of(patientId), centerId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
    }

    @Override
    public List<Patient> listPatients(CenterId centerId) {
        return patientRepo.findAllByCenter(centerId);
    }

    /**
     * Hydrates the shared/extended patient fields (identical for create and update).
     */
    private void hydrate(Patient patient, CreatePatientCommand cmd, String etat) {
        patient.setCivilite(cmd.civilite());
        patient.setGroupeSanguin(cmd.groupeSanguin());
        patient.setNombreEnfants(cmd.nombreEnfants());
        patient.setLieuNaissance(cmd.lieuNaissance());
        patient.setSituationFamiliale(cmd.situationFamiliale());
        patient.setProfession(cmd.profession());
        patient.setAdresse(cmd.adresse());
        patient.setTelPersonnel(cmd.telPersonnel());
        patient.setTelMobile(cmd.telMobile());
        patient.setTelBureau(cmd.telBureau());
        patient.setEmail(cmd.email());
        patient.setSousKt(cmd.sousKt());
        patient.setEpoEnabled(cmd.epoEnabled());
        patient.setEpoDate(cmd.epoDate());
        patient.setFerEnabled(cmd.ferEnabled());
        patient.setFerDate(cmd.ferDate());
        patient.setObservation(cmd.observation());
        patient.setQualiteAssure(cmd.qualiteAssure());
        patient.setPhotoBase64(cmd.photoBase64());
        patient.setEnSommeil(cmd.enSommeil());
        patient.setEtatPatient(etat);
        patient.setDateEvenementEtat(cmd.dateEvenementEtat());
        patient.setCentrePayeurId(cmd.centrePayeurId());
        patient.setAssureNumeroAssurance(cmd.assureNumeroAssurance());
        patient.setMedecinTraitantId(cmd.medecinTraitantId());
        patient.setSalleId(cmd.salleId());
        patient.setPositionId(cmd.positionId());
        patient.setTransporteurAllerId(cmd.transporteurAllerId());
        patient.setTransporteurRetourId(cmd.transporteurRetourId());
        patient.setCategorieTransportId(cmd.categorieTransportId());
        patient.setJoursDialyse(new JoursDialyse(
            cmd.jourDimanche(), cmd.jourLundi(), cmd.jourMardi(),
            cmd.jourMercredi(), cmd.jourJeudi(), cmd.jourVendredi(), cmd.jourSamedi()
        ));
        patient.setAssureInfo(new AssureInfo(
            cmd.assureSexe(), cmd.assureNom(), cmd.assurePrenom(),
            cmd.assureDateNaissance(), cmd.assureTelPersonnel(), cmd.assureAdresse(),
            cmd.assureGroupeSanguin(), cmd.assureTelMobile(), cmd.assureTelBureau()
        ));
        patient.setAssureHistoryJson(cmd.assureHistoryJson());
        patient.setPiecesJointesJson(cmd.piecesJointesJson());
    }
}

