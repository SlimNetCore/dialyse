package com.hemodialyse.backend.domain.pec.service;

import com.hemodialyse.backend.domain.pec.port.PecUseCase;
import com.hemodialyse.backend.domain.insurance.port.AttestationRepositoryPort;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.model.PatientType;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.pec.model.PriseEnCharge;
import com.hemodialyse.backend.domain.pec.port.PecRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — PEC business rules.
 */
@Service
@Transactional
public class PecDomainService implements PecUseCase {

    private final PecRepositoryPort pecRepo;
    private final PatientRepositoryPort patientRepo;
    private final AttestationRepositoryPort attestationRepo;

    public PecDomainService(PecRepositoryPort pecRepo, PatientRepositoryPort patientRepo, AttestationRepositoryPort attestationRepo) {
        this.pecRepo = pecRepo;
        this.patientRepo = patientRepo;
        this.attestationRepo = attestationRepo;
    }

    @Override
    public PriseEnCharge create(CenterId centerId, UUID patientId, LocalDate debutDemande, LocalDate finDemande, UUID forfaitDemandeId) {
        // Business rule: patient must exist
        Patient patient = patientRepo.findById(PatientId.of(patientId), centerId)
            .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
        // Business rule: non-vacancier needs valid attestation
        if (patient.getTypePatient() == PatientType.NON_VACANCIER) {
            if (!attestationRepo.existsValidAt(centerId, patientId, LocalDate.now()))
                throw new IllegalStateException("Attestation valide obligatoire pour PEC non-vacancier");
        }
        return pecRepo.save(new PriseEnCharge(UUID.randomUUID(), patientId, centerId.value(), debutDemande, finDemande, forfaitDemandeId));
    }

    @Override
    public PriseEnCharge validate(CenterId centerId, UUID pecId, LocalDate debutEffectif, LocalDate finEffectif, UUID forfaitEffectifId) {
        PriseEnCharge pec = pecRepo.findById(pecId, centerId).orElseThrow(() -> new IllegalArgumentException("PEC introuvable"));
        // Domain logic: transition from CREE to VALIDEE
        pec.valider(debutEffectif, finEffectif, forfaitEffectifId);
        return pecRepo.save(pec);
    }

    @Override
    public PriseEnCharge close(CenterId centerId, UUID pecId) {
        PriseEnCharge pec = pecRepo.findById(pecId, centerId).orElseThrow(() -> new IllegalArgumentException("PEC introuvable"));
        // Domain logic: close PEC
        pec.cloturer();
        return pecRepo.save(pec);
    }

    @Override
    public void delete(CenterId centerId, UUID pecId) {
        pecRepo.findById(pecId, centerId).orElseThrow(() -> new IllegalArgumentException("PEC introuvable"));
        pecRepo.deleteById(pecId);
    }

    @Override
    public boolean canCreateSession(CenterId centerId, UUID pecId) {
        PriseEnCharge pec = pecRepo.findById(pecId, centerId).orElseThrow();
        Patient patient = patientRepo.findById(PatientId.of(pec.getPatientId()), centerId).orElseThrow();
        // Business rule: non-vacancier needs valid attestation
        if (patient.getTypePatient() == PatientType.NON_VACANCIER) {
            if (!attestationRepo.existsValidAt(centerId, patient.getId().value(), LocalDate.now())) return false;
        }
        // Domain logic on aggregate
        return pec.autoriseSeance();
    }

    @Override
    public List<PriseEnCharge> listByPatient(CenterId centerId, UUID patientId) {
        return pecRepo.findByPatient(centerId, patientId);
    }

    @Override
    public List<PriseEnCharge> listByCenter(CenterId centerId) {
        return pecRepo.findByCenter(centerId);
    }
}

