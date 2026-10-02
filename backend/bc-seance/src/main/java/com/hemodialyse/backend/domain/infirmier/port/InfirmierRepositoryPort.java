package com.hemodialyse.backend.domain.infirmier.port;

import com.hemodialyse.backend.domain.infirmier.model.Infirmier;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance du référentiel des infirmiers (toujours borné au centre — AGENTS.md §2).
 */
public interface InfirmierRepositoryPort {

    Infirmier save(Infirmier infirmier);

    Optional<Infirmier> findById(UUID centerId, UUID id);

    Optional<Infirmier> findByMatricule(UUID centerId, String matricule);

    /**
     * Fiche reliée à un compte utilisateur, dans un centre.
     */
    Optional<Infirmier> findByUserId(UUID centerId, UUID userId);

    /**
     * Toutes les fiches reliées à un compte, tous centres confondus : réservé à la synchronisation de l'état du
     * compte (désactivation, suppression), qui ne dépend pas du centre courant.
     */
    List<Infirmier> findAllByUserId(UUID userId);

    /**
     * Liste paginée des infirmiers d'un centre, par nom (AGENTS.md §9).
     */
    PagedResult<Infirmier> findPaged(UUID centerId, int page, int size);
}
