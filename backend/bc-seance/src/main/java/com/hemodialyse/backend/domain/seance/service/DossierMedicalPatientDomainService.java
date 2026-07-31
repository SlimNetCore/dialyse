package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.DossierMedicalPatient;
import com.hemodialyse.backend.domain.seance.port.DossierMedicalPatientRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.DossierMedicalPatientUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain Service — Dossier médical patient. Pure domain class (hexagonal, AGENTS.md §3);
 * wired in {@code infrastructure/config/DomainServiceConfig}.
 */
public class DossierMedicalPatientDomainService implements DossierMedicalPatientUseCase {

    private final DossierMedicalPatientRepositoryPort repository;

    public DossierMedicalPatientDomainService(DossierMedicalPatientRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Optional<DossierMedicalPatient> getByPatient(CenterId centerId, UUID patientId) {
        return repository.findByPatientId(patientId, centerId);
    }

    @Override
    public DossierMedicalPatient upsert(CenterId centerId,
                                        UUID patientId,
                                        String nephropathieInitiale,
                                        LocalDate dateMiseEnDialyse,
                                        String hepatiteBStatut,
                                        String hepatiteCStatut,
                                        String observationGlobale) {
        DossierMedicalPatient dossier = repository.findByPatientId(patientId, centerId)
                .orElseGet(DossierMedicalPatient::new);

        OffsetDateTime now = OffsetDateTime.now();
        if (dossier.getId() == null) {
            dossier.setId(UUID.randomUUID());
            dossier.setCreatedAt(now);
        }

        dossier.setPatientId(patientId);
        dossier.setCenterId(centerId.value());
        dossier.setNephropathieInitiale(nephropathieInitiale);
        dossier.setDateMiseEnDialyse(dateMiseEnDialyse);
        dossier.setHepatiteBStatut(hepatiteBStatut);
        dossier.setHepatiteCStatut(hepatiteCStatut);
        dossier.setObservationGlobale(observationGlobale);
        dossier.setUpdatedAt(now);

        return repository.save(dossier);
    }
}

