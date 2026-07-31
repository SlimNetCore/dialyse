package com.hemodialyse.backend.application.patient;

import com.hemodialyse.backend.domain.assure.port.AssurePatientRepositoryPort;
import com.hemodialyse.backend.domain.assure.port.AssureRepositoryPort;
import com.hemodialyse.backend.domain.insurance.port.AttestationRepositoryPort;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.port.PatientUseCase;
import com.hemodialyse.backend.domain.patient.service.PatientDomainService;
import com.hemodialyse.backend.domain.pec.port.PecRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Application Service — transactional boundary for the Patient use cases.
 * <p>
 * The {@link PatientDomainService} holds the pure business rules (no Spring/JPA,
 * hexagonal architecture — AGENTS.md §3). This facade lives in the application
 * layer (orchestration) and owns the {@code @Transactional} boundary, guaranteeing
 * atomicity of the multi-write create/update flows (patient + assuré + attestation
 * + PEC). It is the single Spring bean exposed for the {@link PatientUseCase} port.
 */
@Service
@Transactional
public class PatientApplicationService implements PatientUseCase {

    private final PatientDomainService delegate;

    public PatientApplicationService(PatientRepositoryPort patientRepo,
                                     AttestationRepositoryPort attestationRepo,
                                     PecRepositoryPort pecRepo,
                                     AssureRepositoryPort assureRepo,
                                     AssurePatientRepositoryPort assurePatientRepo) {
        this.delegate = new PatientDomainService(patientRepo, attestationRepo, pecRepo, assureRepo, assurePatientRepo);
    }

    @Override
    public Patient createPatient(CenterId centerId, CreatePatientCommand cmd) {
        return delegate.createPatient(centerId, cmd);
    }

    @Override
    public Patient updatePatient(CenterId centerId, UUID patientId, CreatePatientCommand cmd) {
        return delegate.updatePatient(centerId, patientId, cmd);
    }

    @Override
    @Transactional(readOnly = true)
    public Patient getPatient(CenterId centerId, UUID patientId) {
        return delegate.getPatient(centerId, patientId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Patient> listPatients(CenterId centerId) {
        return delegate.listPatients(centerId);
    }
}

