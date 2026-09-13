package com.hemodialyse.backend.domain.medical.examen.service;

import com.hemodialyse.backend.domain.medical.examen.aggregate.DemandeExamen;
import com.hemodialyse.backend.domain.medical.examen.entity.LigneDemandeExamen;
import com.hemodialyse.backend.domain.medical.examen.port.DemandeExamenRepositoryPort;
import com.hemodialyse.backend.domain.medical.examen.port.DemandeExamenUseCase;
import com.hemodialyse.backend.domain.medical.examen.valueobject.CategorieExamen;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Demandes d'examen. Classe pure du domaine (AGENTS.md §3), câblée en
 * {@code @Bean} dans {@code infrastructure/config/DomainServiceConfig}.
 */
public class DemandeExamenDomainService implements DemandeExamenUseCase {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final DemandeExamenRepositoryPort repository;

    public DemandeExamenDomainService(DemandeExamenRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public PagedResult<DemandeExamen> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return repository.findPagedByPatientId(patientId, centerId, Math.max(page, 0), safeSize);
    }

    @Override
    public DemandeExamen create(CenterId centerId, UUID patientId, String prescripteurId, LocalDate dateDemande,
                                CategorieExamen categorie, boolean urgent, String motif,
                                List<LigneDemandeExamen> lignes) {
        DemandeExamen demande = DemandeExamen.creer(
                patientId, centerId.value(), prescripteurId, dateDemande, categorie, urgent, motif, lignes);
        return repository.save(demande);
    }

    @Override
    public DemandeExamen preleve(CenterId centerId, UUID patientId, UUID demandeId) {
        DemandeExamen demande = find(centerId, patientId, demandeId);
        demande.preleve();
        return repository.save(demande);
    }

    @Override
    public DemandeExamen marquerResultatDisponible(CenterId centerId, UUID patientId, UUID demandeId) {
        DemandeExamen demande = find(centerId, patientId, demandeId);
        demande.marquerResultatDisponible();
        return repository.save(demande);
    }

    @Override
    public DemandeExamen valider(CenterId centerId, UUID patientId, UUID demandeId, String conclusion) {
        DemandeExamen demande = find(centerId, patientId, demandeId);
        demande.valider(conclusion);
        return repository.save(demande);
    }

    @Override
    public DemandeExamen annuler(CenterId centerId, UUID patientId, UUID demandeId) {
        DemandeExamen demande = find(centerId, patientId, demandeId);
        demande.annuler();
        return repository.save(demande);
    }

    private DemandeExamen find(CenterId centerId, UUID patientId, UUID demandeId) {
        return repository.findById(demandeId, patientId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Demande d'examen introuvable"));
    }
}
