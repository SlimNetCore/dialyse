package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Accès Spring Data aux tâches d'intervention ; détail d'infrastructure. */
public interface TacheSpringData extends JpaRepository<TacheJpa, UUID> {

    List<TacheJpa> findByInterventionIdOrderByRangAsc(UUID interventionId);
}
