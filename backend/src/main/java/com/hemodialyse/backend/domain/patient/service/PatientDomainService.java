package com.hemodialyse.backend.domain.patient.service;

import com.hemodialyse.backend.domain.patient.port.PatientUseCase;
import com.hemodialyse.backend.domain.insurance.model.AttestationDroit;
import com.hemodialyse.backend.domain.insurance.port.AttestationRepositoryPort;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.model.PatientType;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.*;
import com.hemodialyse.backend.domain.pec.model.PriseEnCharge;
import com.hemodialyse.backend.domain.pec.port.PecRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Patient business rules.
 */
@Service
@Transactional
public class PatientDomainService implements PatientUseCase {

    private final PatientRepositoryPort patientRepo;
    private final AttestationRepositoryPort attestationRepo;
    private final PecRepositoryPort pecRepo;

    public PatientDomainService(PatientRepositoryPort patientRepo,
                                AttestationRepositoryPort attestationRepo,
                                PecRepositoryPort pecRepo) {
        this.patientRepo = patientRepo;
        this.attestationRepo = attestationRepo;
        this.pecRepo = pecRepo;
    }

    @Override
    public Patient createPatient(CenterId centerId, CreatePatientCommand cmd) {
        // Business rule: uniqueness of insurance number within a center
        if (patientRepo.findByNumeroAssurance(centerId, cmd.numeroAssurance()).isPresent()) {
            throw new IllegalStateException("Numero assurance deja utilise pour ce centre");
        }

        // Business rule: attestation mandatory for non-vacancier
        PatientType type = cmd.typePatient() != null ? cmd.typePatient() : PatientType.NON_VACANCIER;
        if (type == PatientType.NON_VACANCIER) {
            if (cmd.attestationDebut() == null || cmd.attestationFin() == null) {
                throw new IllegalArgumentException("Attestation obligatoire pour un patient non-vacancier");
            }
        }

        // Create aggregate root via factory method
        Patient patient = Patient.creer(
            centerId, cmd.nom(), cmd.prenom(), cmd.sexe(),
            cmd.dateAdmission(), cmd.dateNaissance(),
            new NumeroAssurance(cmd.numeroAssurance()), type
        );

        // Hydrate value objects and extended fields
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
        patient.setEtatPatient(cmd.etatPatient() != null ? cmd.etatPatient() : "PERMANENT");
        patient.setCentrePayeurId(cmd.centrePayeurId());
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

        Patient saved = patientRepo.save(patient);

        // Create attestation if provided
        if (cmd.attestationDebut() != null && cmd.attestationFin() != null) {
            attestationRepo.save(new AttestationDroit(
                UUID.randomUUID(), saved.getId().value(), centerId.value(),
                cmd.attestationDebut(), cmd.attestationFin()
            ));
        }

        // Create PEC if provided
        if (cmd.pecDateDebutDemande() != null && cmd.pecDateFinDemande() != null) {
            pecRepo.save(new PriseEnCharge(
                UUID.randomUUID(), saved.getId().value(), centerId.value(),
                cmd.pecDateDebutDemande(), cmd.pecDateFinDemande(), cmd.pecForfaitDemandeId()
            ));
        }

        return saved;
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
}

