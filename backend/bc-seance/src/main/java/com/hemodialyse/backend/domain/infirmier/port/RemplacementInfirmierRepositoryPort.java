package com.hemodialyse.backend.domain.infirmier.port;

import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;

import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des remplacements ponctuels d'infirmiers.
 */
public interface RemplacementInfirmierRepositoryPort {

    RemplacementInfirmier save(RemplacementInfirmier remplacement);

    Optional<RemplacementInfirmier> findById(UUID centerId, UUID id);

    void delete(UUID centerId, UUID id);
}
