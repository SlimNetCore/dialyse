package com.hemodialyse.backend.domain.medical.anemie.service;

import com.hemodialyse.backend.domain.medical.anemie.aggregate.AdministrationTraitement;
import com.hemodialyse.backend.domain.medical.anemie.port.AdministrationTraitementRepositoryPort;
import com.hemodialyse.backend.domain.medical.anemie.port.AdministrationTraitementUseCase;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.DoseAdministree;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Administrations de traitement de l'anémie. Classe pure du domaine
 * (AGENTS.md §3), câblée en {@code @Bean} dans {@code infrastructure/config/DomainServiceConfig}.
 */
public class AdministrationTraitementDomainService implements AdministrationTraitementUseCase {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final AdministrationTraitementRepositoryPort repository;

    public AdministrationTraitementDomainService(AdministrationTraitementRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public PagedResult<AdministrationTraitement> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return repository.findPagedByPatientId(patientId, centerId, Math.max(page, 0), safeSize);
    }

    @Override
    public List<AdministrationTraitement> listByPatient(CenterId centerId, UUID patientId) {
        return repository.findByPatientId(patientId, centerId);
    }

    @Override
    public AdministrationTraitement create(CenterId centerId, UUID patientId, UUID prescriptionMedicaleId,
                                           TypeTraitementAnemie typeTraitement, String molecule, DoseAdministree dose,
                                           String voie, LocalDate dateAdministration, UUID seanceId,
                                           String administrePar, boolean administree, String motifNonAdministration) {
        AdministrationTraitement administration = AdministrationTraitement.enregistrer(patientId, centerId.value(),
                prescriptionMedicaleId, typeTraitement, molecule, dose, voie, dateAdministration, seanceId,
                administrePar, administree, motifNonAdministration);
        return repository.save(administration);
    }
}
