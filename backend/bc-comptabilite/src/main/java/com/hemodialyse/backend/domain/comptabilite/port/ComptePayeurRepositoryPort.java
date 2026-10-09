package com.hemodialyse.backend.domain.comptabilite.port;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant — compte client propre à chaque payeur d'un centre (caisse, mutuelle, entreprise…).
 */
public interface ComptePayeurRepositoryPort {

    Optional<String> find(UUID centerId, UUID payeurId);

    /**
     * Comptes des payeurs demandés (les payeurs sans compte propre sont absents du résultat).
     */
    Map<UUID, String> findAll(UUID centerId, Collection<UUID> payeurIds);

    void save(UUID centerId, UUID payeurId, String compte);

    void delete(UUID centerId, UUID payeurId);

    /**
     * Vrai si au moins un payeur du centre utilise ce compte.
     */
    boolean existsByCompte(UUID centerId, String compte);
}
