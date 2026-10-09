package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.comptabilite.valueobject.CompteComptable;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant — plan comptable enregistré d'un centre. Toute lecture et toute écriture est bornée au centre.
 */
public interface CompteRepositoryPort {

    /**
     * Nombre de comptes enregistrés ; zéro tant que le centre utilise le plan de départ.
     */
    long count(UUID centerId);

    /**
     * Page de comptes triés par numéro ; {@code recherche} (facultative) porte sur le numéro et le libellé.
     */
    PagedResult<CompteComptable> findPaged(UUID centerId, String recherche, boolean actifsSeulement, int page,
                                           int size);

    Optional<CompteComptable> find(UUID centerId, String numero);

    void save(UUID centerId, CompteComptable compte);

    void delete(UUID centerId, String numero);
}
