package com.hemodialyse.backend.domain.medical.allergie.service;

import com.hemodialyse.backend.domain.medical.allergie.aggregate.Allergie;
import com.hemodialyse.backend.domain.medical.allergie.port.AllergieRepositoryPort;
import com.hemodialyse.backend.domain.medical.allergie.port.AllergieUseCase;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.CategorieAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.CriticiteAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.StatutVerificationAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.TypeReaction;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Allergies et intolérances. Classe pure du domaine (AGENTS.md §3), câblée
 * en {@code @Bean} dans {@code infrastructure/config/DomainServiceConfig}.
 */
public class AllergieDomainService implements AllergieUseCase {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final AllergieRepositoryPort repository;

    public AllergieDomainService(AllergieRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public PagedResult<Allergie> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return repository.findPagedByPatientId(patientId, centerId, Math.max(page, 0), safeSize);
    }

    @Override
    public List<Allergie> listCritiquesByPatient(CenterId centerId, UUID patientId) {
        return repository.findByPatientId(patientId, centerId).stream()
                .filter(a -> a.getCriticite() == CriticiteAllergie.HAUTE)
                .toList();
    }

    @Override
    public Allergie create(CenterId centerId, UUID patientId, ConceptCode substance, CategorieAllergie categorie,
                           CriticiteAllergie criticite, TypeReaction typeReaction, String manifestations,
                           LocalDate dateConstatation, StatutVerificationAllergie statutVerification) {
        Allergie allergie = Allergie.declarer(patientId, centerId.value(), substance, categorie, criticite,
                typeReaction, manifestations, dateConstatation, statutVerification);
        return repository.save(allergie);
    }

    @Override
    public Allergie update(CenterId centerId, UUID patientId, UUID allergieId, CriticiteAllergie criticite,
                           String manifestations, StatutVerificationAllergie statutVerification) {
        Allergie allergie = repository.findById(allergieId, patientId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Allergie introuvable"));
        allergie.modifier(criticite, manifestations, statutVerification);
        return repository.save(allergie);
    }

    @Override
    public void delete(CenterId centerId, UUID patientId, UUID allergieId) {
        repository.deleteById(allergieId, patientId, centerId);
    }
}
