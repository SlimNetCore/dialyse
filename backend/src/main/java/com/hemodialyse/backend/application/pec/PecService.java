package com.hemodialyse.backend.application.pec;

import com.hemodialyse.backend.domain.insurance.AttestationDroitRepository;
import com.hemodialyse.backend.domain.patient.Patient;
import com.hemodialyse.backend.domain.patient.PatientRepository;
import com.hemodialyse.backend.domain.patient.PatientType;
import com.hemodialyse.backend.domain.pec.PriseEnCharge;
import com.hemodialyse.backend.domain.pec.PriseEnChargeRepository;
import com.hemodialyse.backend.domain.shared.TenantScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional
public class PecService {

    private final PriseEnChargeRepository repo;
    private final PatientRepository patientRepository;
    private final AttestationDroitRepository attestationRepository;

    public PecService(
        PriseEnChargeRepository repo,
        PatientRepository patientRepository,
        AttestationDroitRepository attestationRepository
    ) {
        this.repo = repo;
        this.patientRepository = patientRepository;
        this.attestationRepository = attestationRepository;
    }

    public PriseEnCharge create(TenantScope scope, UUID patientId, LocalDate dateDebutDemande, LocalDate dateFinDemande) {
        Patient patient = patientRepository.findByIdAndCenterId(patientId, scope.centerId())
            .orElseThrow(() -> new IllegalArgumentException("Patient introuvable dans ce centre"));

        if (patient.getTypePatient() == PatientType.NON_VACANCIER) {
            boolean hasValidAttestation = attestationRepository.existsValidAt(scope.centerId(), patientId, LocalDate.now());
            if (!hasValidAttestation) {
                throw new IllegalStateException("Attestation valide obligatoire pour creer une PEC non-vacancier");
            }
        }

        PriseEnCharge pec = new PriseEnCharge(UUID.randomUUID(), patientId, scope.centerId(), dateDebutDemande, dateFinDemande);
        return repo.save(pec);
    }

    public PriseEnCharge validate(TenantScope scope, UUID pecId) {
        PriseEnCharge pec = repo.findByIdAndCenterId(pecId, scope.centerId())
            .orElseThrow(() -> new IllegalArgumentException("Prise en charge introuvable"));
        pec.valider();
        return repo.save(pec);
    }

    public PriseEnCharge close(TenantScope scope, UUID pecId) {
        PriseEnCharge pec = repo.findByIdAndCenterId(pecId, scope.centerId())
            .orElseThrow(() -> new IllegalArgumentException("Prise en charge introuvable"));
        pec.cloturer();
        return repo.save(pec);
    }

    public boolean canCreateSession(TenantScope scope, UUID pecId) {
        PriseEnCharge pec = repo.findByIdAndCenterId(pecId, scope.centerId())
            .orElseThrow(() -> new IllegalArgumentException("Prise en charge introuvable"));

        Patient patient = patientRepository.findByIdAndCenterId(pec.getPatientId(), scope.centerId())
            .orElseThrow(() -> new IllegalArgumentException("Patient introuvable dans ce centre"));

        if (patient.getTypePatient() == PatientType.NON_VACANCIER) {
            boolean hasValidAttestation = attestationRepository.existsValidAt(scope.centerId(), patient.getId(), LocalDate.now());
            if (!hasValidAttestation) {
                return false;
            }
        }

        return pec.autoriseSeance();
    }
}
