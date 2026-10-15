package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Accès Spring Data au modèle de persistance du mapping ; détail d'infrastructure.
 */
public interface MappingComptableSpringData extends JpaRepository<MappingComptableJpa, UUID> {

    Optional<MappingComptableJpa> findByCenterId(UUID centerId);
}
