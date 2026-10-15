package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Accès Spring Data au modèle de persistance des comptes ; détail d'infrastructure.
 */
public interface CompteComptableSpringData extends JpaRepository<CompteComptableJpa, UUID> {

    long countByCenterId(UUID centerId);

    Optional<CompteComptableJpa> findByCenterIdAndNumero(UUID centerId, String numero);

    @Transactional
    void deleteByCenterIdAndNumero(UUID centerId, String numero);

    /**
     * Page triée par numéro, sans recherche.
     */
    @Query(value = "SELECT c FROM CompteComptableJpa c WHERE c.centerId = :centerId "
            + "AND (:actifsSeulement = false OR c.actif = true) ORDER BY c.numero",
            countQuery = "SELECT COUNT(c) FROM CompteComptableJpa c WHERE c.centerId = :centerId "
                    + "AND (:actifsSeulement = false OR c.actif = true)")
    Page<CompteComptableJpa> tous(@Param("centerId") UUID centerId, @Param("actifsSeulement") boolean actifsSeulement,
                                  Pageable pageable);

    /**
     * Page triée par numéro ; {@code motif} est un motif {@code LIKE} déjà construit, en minuscules : {@code %} et {@code _}
     * saisis par l'utilisateur gardent leur sens de joker (KB-CPT-05).
     */
    @Query(value = "SELECT c FROM CompteComptableJpa c WHERE c.centerId = :centerId "
            + "AND (:actifsSeulement = false OR c.actif = true) "
            + "AND (LOWER(c.numero) LIKE :motif OR LOWER(c.libelle) LIKE :motif) ORDER BY c.numero",
            countQuery = "SELECT COUNT(c) FROM CompteComptableJpa c WHERE c.centerId = :centerId "
                    + "AND (:actifsSeulement = false OR c.actif = true) "
                    + "AND (LOWER(c.numero) LIKE :motif OR LOWER(c.libelle) LIKE :motif)")
    Page<CompteComptableJpa> rechercher(@Param("centerId") UUID centerId, @Param("actifsSeulement") boolean actifsSeulement,
                                        @Param("motif") String motif, Pageable pageable);
}
