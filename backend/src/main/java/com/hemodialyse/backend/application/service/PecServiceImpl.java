package com.hemodialyse.backend.application.service;

import com.hemodialyse.backend.application.port.in.PecUseCase;
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

@Service
@Transactional
public class PecServiceImpl implements PecUseCase {

    private final PecRepositoryPort pecRepo;
    private final PatientRepositoryPort patientRepo;
    private final AttestationRepositoryPort attestationRepo;

    public PecServiceImpl(PecRepositoryPort pecRepo, PatientRepositoryPort patientRepo, AttestationRepositoryPort attestationRepo) {
        this.pecRepo = pecRepo;
        this.patientRepo = patientRepo;
        this.attestationRepo = attestationRepo;
    }

    @Override
    public PriseEnCharge create(CenterId centerId, UUID patientId, LocalDate debutDemande, LocalDate finDemande, UUID forfaitDemandeId) {
        Patient patient = patientRepo.findById(PatientId.of(patientId), centerId)
            .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
        if (patient.getTypePatient() == PatientType.NON_VACANCIER) {
            if (!attestationRepo.existsValidAt(centerId, patientId, LocalDate.now()))
                throw new IllegalStateException("Attestation valide obligatoire pour PEC non-vacancier");
        }
        return pecRepo.save(new PriseEnCharge(UUID.randomUUID(), patientId, centerId.value(), debutDemande, finDemande, forfaitDemandeId));
    }

    @Override
    public PriseEnCharge validate(CenterId centerId, UUID pecId, LocalDate debutEffectif, LocalDate finEffectif, UUID forfaitEffectifId) {
        PriseEnCharge pec = pecRepo.findById(pecId, centerId).orElseThrow(() -> new IllegalArgumentException("PEC introuvable"));
        pec.valider(debutEffectif, finEffectif, forfaitEffectifId);
        return pecRepo.save(pec);
    }

    @Override
    public PriseEnCharge close(CenterId centerId, UUID pecId) {
        PriseEnCharge pec = pecRepo.findById(pecId, centerId).orElseThrow(() -> new IllegalArgumentException("PEC introuvable"));
        pec.cloturer();
        return pecRepo.save(pec);
    }

    @Override
    public boolean canCreateSession(CenterId centerId, UUID pecId) {
        PriseEnCharge pec = pecRepo.findById(pecId, centerId).orElseThrow();
        Patient patient = patientRepo.findById(PatientId.of(pec.getPatientId()), centerId).orElseThrow();
        if (patient.getTypePatient() == PatientType.NON_VACANCIER) {
            if (!attestationRepo.existsValidAt(centerId, patient.getId().value(), LocalDate.now())) return false;
        }
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

