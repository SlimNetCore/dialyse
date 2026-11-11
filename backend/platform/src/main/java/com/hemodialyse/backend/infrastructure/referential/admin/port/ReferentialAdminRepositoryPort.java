package com.hemodialyse.backend.infrastructure.referential.admin.port;

import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Port Out — persistance des référentiels administrables. Toutes les opérations sont restreintes au centre.
 * <p>
 * Les valeurs sont déjà normalisées par le domaine : décimal en notation « 5600.00 », référence = UUID,
 * {@code null} pour un champ facultatif vide.
 */
public interface ReferentialAdminRepositoryPort {

    PagedResult<ReferentialEntry> findPaged(CentreId centerId, ReferentialKind kind, String search, int page, int size);

    Optional<ReferentialEntry> findById(CentreId centerId, ReferentialKind kind, UUID id);

    /**
     * Toutes les lignes du centre, pour construire les index de clé naturelle (unicité, résolution des codes
     * référencés). Usage interne : les référentiels d'un centre restent de petite taille.
     */
    List<ReferentialEntry> findAllForMatching(CentreId centerId, ReferentialKind kind);

    UUID insert(CentreId centerId, ReferentialKind kind, Map<String, String> values);

    void update(CentreId centerId, ReferentialKind kind, UUID id, Map<String, String> values);

    void delete(CentreId centerId, ReferentialKind kind, UUID id);

    /**
     * Nombre de données (patients, PEC, séances, référentiels enfants…) qui pointent encore sur cette ligne.
     */
    long countUsages(CentreId centerId, ReferentialKind kind, UUID id);
}

