package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Accès Spring Data aux rectifications ; détail d'infrastructure. */
public interface RectificationSpringData extends JpaRepository<RectificationJpa, UUID> {

    List<RectificationJpa> findByInterventionIdOrderByLeAsc(UUID interventionId);
}
