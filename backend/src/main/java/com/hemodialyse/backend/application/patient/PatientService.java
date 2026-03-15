package com.hemodialyse.backend.application.patient;

import com.hemodialyse.backend.domain.insurance.AttestationDroit;
import com.hemodialyse.backend.domain.insurance.AttestationDroitRepository;
import com.hemodialyse.backend.domain.patient.Patient;
import com.hemodialyse.backend.domain.patient.PatientRepository;
import com.hemodialyse.backend.domain.patient.PatientType;
import com.hemodialyse.backend.domain.shared.TenantScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class PatientService {

    private final PatientRepository repo;
    private final AttestationDroitRepository attestationRepo;

    public PatientService(PatientRepository repo, AttestationDroitRepository attestationRepo) {
        this.repo = repo;
        this.attestationRepo = attestationRepo;
    }

    public Patient createPatient(
        TenantScope scope,
        String nom,
        String prenom,
        String sexe,
        LocalDate dateAdmission,
        LocalDate dateNaissance,
        String numeroAssurance,
        PatientType typePatient,
        LocalDate attestationDebut,
        LocalDate attestationFin
    ) {
        // enforce center scoping and unicite
        if (repo.findByCenterIdAndNumeroAssurance(scope.centerId(), numeroAssurance).isPresent()) {
            throw new IllegalStateException("Numero assurance deja utilise pour ce centre");
        }

        // Implement vacancier/non-vacancier rule
        if (typePatient == PatientType.NON_VACANCIER) {
            if (attestationDebut == null || attestationFin == null) {
                throw new IllegalArgumentException("Attestation obligatoire pour un patient non-vacancier");
            }
            if (attestationFin.isBefore(attestationDebut)) {
                throw new IllegalArgumentException("Periode d'attestation invalide");
            }
        }

        Patient patient = new Patient(
            UUID.randomUUID(),
            scope.centerId(),
            nom,
            prenom,
            sexe,
            dateAdmission,
            dateNaissance,
            numeroAssurance,
            typePatient
        );
        Patient saved = repo.save(patient);

        if (attestationDebut != null && attestationFin != null) {
            attestationRepo.save(new AttestationDroit(
                UUID.randomUUID(),
                saved.getId(),
                scope.centerId(),
                attestationDebut,
                attestationFin
            ));
        }

        return saved;
    }

    public Optional<Patient> findById(TenantScope scope, UUID id) {
        return repo.findByIdAndCenterId(id, scope.centerId());
    }
}
