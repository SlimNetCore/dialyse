package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Accès Spring Data aux lignes de coût ; détail d'infrastructure. Les sommes ne comptent que les interventions en cours ou
 * terminées, non supprimées, démarrées sur la période [du, au) : ni planifiées ni annulées.
 */
public interface LigneDeCoutSpringData extends JpaRepository<LigneDeCoutJpa, UUID> {

    List<LigneDeCoutJpa> findByInterventionId(UUID interventionId);

    @Query("SELECT COALESCE(SUM(l.quantite * l.prixUnitaire), 0) FROM LigneDeCoutJpa l WHERE l.interventionId IN ("
            + "SELECT i.id FROM InterventionJpa i WHERE i.equipementId = :equipementId AND i.dateDebut >= :du "
            + "AND i.dateDebut < :au AND i.statut IN ('EN_COURS','TERMINEE') AND i.deletedAt IS NULL)")
    BigDecimal sommeDeLEquipement(@Param("equipementId") UUID equipementId, @Param("du") Instant du, @Param("au") Instant au);

    @Query("SELECT COALESCE(SUM(l.quantite * l.prixUnitaire), 0) FROM LigneDeCoutJpa l WHERE l.interventionId IN ("
            + "SELECT i.id FROM InterventionJpa i WHERE i.centreId = :centreId AND i.dateDebut >= :du "
            + "AND i.dateDebut < :au AND i.statut IN ('EN_COURS','TERMINEE') AND i.deletedAt IS NULL)")
    BigDecimal sommeDuCentre(@Param("centreId") UUID centreId, @Param("du") Instant du, @Param("au") Instant au);

    /** Lignes {@code [equipementId, montant]}. */
    @Query("SELECT i.equipementId, SUM(l.quantite * l.prixUnitaire) FROM LigneDeCoutJpa l, InterventionJpa i "
            + "WHERE l.interventionId = i.id AND i.centreId = :centreId AND i.dateDebut >= :du AND i.dateDebut < :au "
            + "AND i.statut IN ('EN_COURS','TERMINEE') AND i.deletedAt IS NULL GROUP BY i.equipementId")
    List<Object[]> sommesParEquipement(@Param("centreId") UUID centreId, @Param("du") Instant du, @Param("au") Instant au);
}
