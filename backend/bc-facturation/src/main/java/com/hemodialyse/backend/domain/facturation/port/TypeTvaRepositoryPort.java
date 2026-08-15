package com.hemodialyse.backend.domain.facturation.port;

import com.hemodialyse.backend.domain.facturation.aggregate.TypeTVA;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant — gestion des types TVA par centre et par période.
 * Implémenté par l'infrastructure (TypeTvaJdbcAdapter).
 */
public interface TypeTvaRepositoryPort {

    /**
     * Retourne le type TVA actif à la date donnée pour un centre et un type de prestation.
     */
    Optional<TypeTVA> findActiveAt(UUID centerId, String typePrestation, LocalDate date);

    /**
     * Liste paginée de tous les types TVA d'un centre (actifs et historiques).
     */
    PagedResult<TypeTVA> findAll(UUID centerId, int page, int size);

    /**
     * Persiste un type TVA (insert ou update selon l'existence de l'ID).
     */
    TypeTVA save(TypeTVA typeTva);

    /**
     * Désactive logiquement un type TVA (actif = false).
     */
    void deactivate(UUID id, UUID centerId);
}

