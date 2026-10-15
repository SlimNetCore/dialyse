package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Accès Spring Data au modèle de persistance des comptes de payeurs ; détail d'infrastructure.
 */
public interface ComptePayeurSpringData extends JpaRepository<ComptePayeurJpa, ComptePayeurJpa.Cle> {

    @Transactional
    void deleteByCenterIdAndPayeurId(UUID centerId, UUID payeurId);
}
