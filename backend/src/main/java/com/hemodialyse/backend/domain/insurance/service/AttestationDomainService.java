package com.hemodialyse.backend.domain.insurance.service;

import com.hemodialyse.backend.domain.insurance.port.AttestationUseCase;
import com.hemodialyse.backend.domain.insurance.model.AttestationDroit;
import com.hemodialyse.backend.domain.insurance.port.AttestationRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Attestation business rules.
 */
@Service
@Transactional
public class AttestationDomainService implements AttestationUseCase {

    private final AttestationRepositoryPort repo;

    public AttestationDomainService(AttestationRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public AttestationDroit create(CenterId centerId, UUID patientId, LocalDate dateDebut, LocalDate dateFin) {
        // Business rule: dateDebut must be before dateFin
        if (dateDebut != null && dateFin != null && dateDebut.isAfter(dateFin)) {
            throw new IllegalArgumentException("La date de début doit être antérieure à la date de fin");
        }
        return repo.save(new AttestationDroit(UUID.randomUUID(), patientId, centerId.value(), dateDebut, dateFin));
    }

    @Override
    public void delete(CenterId centerId, UUID attestationId) {
        repo.deleteById(attestationId);
    }

    @Override
    public List<AttestationDroit> listByPatient(CenterId centerId, UUID patientId) {
        return repo.findByPatient(centerId, patientId);
    }
}

