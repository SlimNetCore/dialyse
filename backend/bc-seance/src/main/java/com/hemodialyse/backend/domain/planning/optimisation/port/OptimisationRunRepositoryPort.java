package com.hemodialyse.backend.domain.planning.optimisation.port;

import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance de l'historique des optimisations d'un centre (toujours borné au centre — AGENTS.md §2).
 */
public interface OptimisationRunRepositoryPort {

    /**
     * Crée ou met à jour l'exécution.
     */
    RunOptimisation save(RunOptimisation run);

    Optional<RunOptimisation> findById(UUID centerId, UUID id);

    /**
     * Exécution en cours du centre, s'il y en a une.
     */
    Optional<RunOptimisation> findEnCours(UUID centerId);

    /**
     * Historique paginé, des plus récentes aux plus anciennes, sans le résultat détaillé (AGENTS.md §9).
     */
    PagedResult<RunOptimisation> findPaged(UUID centerId, int page, int size);

    /**
     * Ne garde que les {@code aGarder} exécutions les plus récentes du centre.
     */
    void purger(UUID centerId, int aGarder);

    /**
     * Supprime une exécution du centre.
     *
     * @return {@code true} si elle existait dans ce centre
     */
    boolean supprimer(UUID centerId, UUID id);

    /**
     * Passe en échec les exécutions restées en cours (arrêt du serveur) ; tous centres confondus, réservé au démarrage.
     *
     * @return nombre d'exécutions interrompues
     */
    int interrompreEnCours(String motif);
}
