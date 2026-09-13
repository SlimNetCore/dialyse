package com.hemodialyse.backend.domain.medical.ordonnance.service;

import com.hemodialyse.backend.domain.medical.ordonnance.aggregate.Ordonnance;
import com.hemodialyse.backend.domain.medical.ordonnance.entity.LigneOrdonnance;
import com.hemodialyse.backend.domain.medical.ordonnance.port.OrdonnanceNumeroGeneratorPort;
import com.hemodialyse.backend.domain.medical.ordonnance.port.OrdonnanceRepositoryPort;
import com.hemodialyse.backend.domain.medical.ordonnance.port.OrdonnanceUseCase;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Ordonnances. Classe pure du domaine (AGENTS.md §3), câblée en {@code @Bean}
 * dans {@code infrastructure/config/DomainServiceConfig}.
 */
public class OrdonnanceDomainService implements OrdonnanceUseCase {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final OrdonnanceRepositoryPort repository;
    private final OrdonnanceNumeroGeneratorPort numeroGenerator;

    public OrdonnanceDomainService(OrdonnanceRepositoryPort repository, OrdonnanceNumeroGeneratorPort numeroGenerator) {
        this.repository = repository;
        this.numeroGenerator = numeroGenerator;
    }

    @Override
    public PagedResult<Ordonnance> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return repository.findPagedByPatientId(patientId, centerId, Math.max(page, 0), safeSize);
    }

    @Override
    public Ordonnance create(CenterId centerId, UUID patientId, String medecinId, LocalDate datePrescription,
                             List<LigneOrdonnance> lignes) {
        Ordonnance ordonnance = Ordonnance.creer(patientId, centerId.value(), medecinId, datePrescription, lignes);
        return repository.save(ordonnance);
    }

    @Override
    public Ordonnance signer(CenterId centerId, UUID patientId, UUID ordonnanceId) {
        Ordonnance ordonnance = find(centerId, patientId, ordonnanceId);
        ordonnance.signer(numeroGenerator.genererNumero(centerId));
        return repository.save(ordonnance);
    }

    @Override
    public Ordonnance marquerImprimee(CenterId centerId, UUID patientId, UUID ordonnanceId) {
        Ordonnance ordonnance = find(centerId, patientId, ordonnanceId);
        ordonnance.marquerImprimee();
        return repository.save(ordonnance);
    }

    @Override
    public Ordonnance annuler(CenterId centerId, UUID patientId, UUID ordonnanceId) {
        Ordonnance ordonnance = find(centerId, patientId, ordonnanceId);
        ordonnance.annuler();
        return repository.save(ordonnance);
    }

    private Ordonnance find(CenterId centerId, UUID patientId, UUID ordonnanceId) {
        return repository.findById(ordonnanceId, patientId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Ordonnance introuvable"));
    }
}
