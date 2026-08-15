package com.hemodialyse.backend.domain.facturation.service;

import com.hemodialyse.backend.domain.facturation.aggregate.TypeTVA;
import com.hemodialyse.backend.domain.facturation.port.TypeTvaRepositoryPort;
import com.hemodialyse.backend.domain.facturation.port.TypeTvaUseCase;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service de domaine — gestion des types TVA.
 * Encapsule la logique métier : unicité de période, validation des dates.
 */
public class TypeTvaDomainService implements TypeTvaUseCase {

    private final TypeTvaRepositoryPort repository;

    public TypeTvaDomainService(TypeTvaRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public PagedResult<TypeTVA> listerTypesTva(UUID centerId, int page, int size) {
        return repository.findAll(centerId, page, size);
    }

    @Override
    public TypeTVA creerTypeTva(UUID centerId, String libelle, BigDecimal taux, String typePrestation,
                                boolean exonere, LocalDate dateDebutValidite, LocalDate dateFinValidite,
                                String texteReference, String createdBy) {
        validerDates(dateDebutValidite, dateFinValidite);
        TypeTVA typeTva = TypeTVA.creer(centerId, libelle, taux, typePrestation,
                exonere, dateDebutValidite, dateFinValidite, texteReference, createdBy);
        return repository.save(typeTva);
    }

    @Override
    public TypeTVA modifierTypeTva(UUID id, UUID centerId, String libelle, BigDecimal taux,
                                   String typePrestation, boolean exonere, LocalDate dateDebutValidite,
                                   LocalDate dateFinValidite, String texteReference, String updatedBy) {
        validerDates(dateDebutValidite, dateFinValidite);
        TypeTVA updated = new TypeTVA(id, centerId, libelle, taux, typePrestation, exonere,
                dateDebutValidite, dateFinValidite, texteReference, true,
                null, updatedBy);
        return repository.save(updated);
    }

    @Override
    public void desactiverTypeTva(UUID id, UUID centerId) {
        repository.deactivate(id, centerId);
    }

    private void validerDates(LocalDate debut, LocalDate fin) {
        if (debut == null) throw new IllegalArgumentException("La date de début de validité est obligatoire");
        if (fin != null && !fin.isAfter(debut)) {
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début");
        }
    }
}

