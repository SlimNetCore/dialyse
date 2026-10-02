package com.hemodialyse.backend.domain.infirmier.port;

import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des affectations (roulement) des infirmiers.
 */
public interface AffectationInfirmierRepositoryPort {

    AffectationInfirmier save(AffectationInfirmier affectation);

    Optional<AffectationInfirmier> findById(UUID centerId, UUID id);

    /**
     * Affectations des infirmiers donnés (une page de la liste des infirmiers, donc bornée).
     */
    List<AffectationInfirmier> findByInfirmierIds(UUID centerId, Collection<UUID> infirmierIds);

    void delete(UUID centerId, UUID id);
}
