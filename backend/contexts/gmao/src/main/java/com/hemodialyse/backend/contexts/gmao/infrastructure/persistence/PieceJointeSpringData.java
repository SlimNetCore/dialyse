package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Accès Spring Data au modèle de persistance des pièces jointes ; détail d'infrastructure. */
public interface PieceJointeSpringData extends JpaRepository<PieceJointeJpa, UUID> {

    long countByInterventionId(UUID interventionId);

    /** Métadonnées seules : la colonne des octets n'est jamais lue pour une liste. Le tri est porté par le {@link Pageable}. */
    @Query(value = "SELECT new com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PieceMetadonnees("
            + "p.id, p.interventionId, p.type, p.nom, p.contentType, p.taille, p.ajouteLe) "
            + "FROM PieceJointeJpa p WHERE p.interventionId = :interventionId",
            countQuery = "SELECT COUNT(p) FROM PieceJointeJpa p WHERE p.interventionId = :interventionId")
    Page<PieceMetadonnees> metadonneesDeLIntervention(@Param("interventionId") UUID interventionId, Pageable pageable);

    /** Une pièce sans ses octets : la colonne {@code contenu} n'est pas lue (retrait). */
    @Query("SELECT new com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PieceEnTete("
            + "p.id, p.interventionId, p.centreId, p.type, p.nom, p.contentType, p.taille, p.ajoutePar, p.ajouteLe) "
            + "FROM PieceJointeJpa p WHERE p.id = :id")
    java.util.Optional<PieceEnTete> enTeteDe(@Param("id") UUID id);

    /** Suppression sans relire les octets. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PieceJointeJpa p WHERE p.id = :id")
    void supprimerParId(@Param("id") UUID id);
}
