package com.hemodialyse.backend.domain.medical.observation.service;

import com.hemodialyse.backend.domain.medical.observation.aggregate.ObservationBiologique;
import com.hemodialyse.backend.domain.medical.observation.port.ObservationBiologiqueRepositoryPort;
import com.hemodialyse.backend.domain.medical.observation.port.ObservationBiologiqueUseCase;
import com.hemodialyse.backend.domain.medical.observation.valueobject.StatutObservation;
import com.hemodialyse.backend.domain.medical.observation.valueobject.ValeurMesuree;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Observations biologiques (LOINC). Classe pure du domaine (AGENTS.md §3),
 * câblée en {@code @Bean} dans {@code infrastructure/config/DomainServiceConfig}.
 */
public class ObservationBiologiqueDomainService implements ObservationBiologiqueUseCase {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final ObservationBiologiqueRepositoryPort repository;

    public ObservationBiologiqueDomainService(ObservationBiologiqueRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public PagedResult<ObservationBiologique> listPagedByPatient(CenterId centerId, UUID patientId, String loincCode,
                                                                 LocalDate from, LocalDate to, int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return repository.findPagedByPatientId(patientId, centerId, loincCode, from, to, Math.max(page, 0), safeSize);
    }

    @Override
    public List<ObservationBiologique> listByDemandeExamen(CenterId centerId, UUID demandeExamenId) {
        return repository.findByDemandeExamenId(demandeExamenId, centerId);
    }

    @Override
    public ObservationBiologique create(CenterId centerId, UUID patientId, UUID demandeExamenId, ConceptCode analyte,
                                        ValeurMesuree valeurNum, String valeurTexte, LocalDate datePrelevement,
                                        StatutObservation statut) {
        ObservationBiologique observation = ObservationBiologique.enregistrer(
                patientId, centerId.value(), demandeExamenId, analyte, valeurNum, valeurTexte, datePrelevement, statut);
        return repository.save(observation);
    }

    @Override
    public ObservationBiologique corriger(CenterId centerId, UUID patientId, UUID observationId,
                                          ValeurMesuree valeurNum, String valeurTexte) {
        ObservationBiologique observation = repository.findById(observationId, patientId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Observation introuvable"));
        observation.corriger(valeurNum, valeurTexte);
        return repository.save(observation);
    }

    @Override
    public void delete(CenterId centerId, UUID patientId, UUID observationId) {
        repository.deleteById(observationId, patientId, centerId);
    }
}
