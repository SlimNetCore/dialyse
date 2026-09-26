package com.hemodialyse.backend.domain.organisation.port;

import com.hemodialyse.backend.domain.organisation.model.Societe;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Port Out — persistance de l'agrégat {@link Societe} (société et centres qui la composent).
 */
public interface SocieteRepositoryPort {

    Optional<Societe> findById(UUID id);

    /**
     * Enregistre la société et ses centres (création ou mise à jour).
     */
    Societe save(Societe societe);

    boolean existsSocieteCode(String code, UUID excludedSocieteId);

    /**
     * Le code d'un centre est unique dans toute l'application.
     */
    boolean existsCentreCode(String code, UUID excludedCentreId);

    /**
     * Recherche paginée (code ou raison sociale contenant {@code search}, insensible à la casse).
     */
    PagedResult<Societe> findPaged(String search, int page, int size);
}
