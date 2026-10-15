package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Accès Spring Data au modèle de persistance des journaux ; détail d'infrastructure.
 */
public interface JournalComptableSpringData extends JpaRepository<JournalComptableJpa, UUID> {

    List<JournalComptableJpa> findByCenterIdOrderByCodeAsc(UUID centerId);

    Optional<JournalComptableJpa> findByCenterIdAndCode(UUID centerId, String code);

    @Transactional
    void deleteByCenterIdAndCode(UUID centerId, String code);
}
