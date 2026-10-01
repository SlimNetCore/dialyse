package com.hemodialyse.backend.infrastructure.persistence.repository.gmao;

import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.LigneCoutInterventionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface LigneCoutInterventionJpaRepository extends JpaRepository<LigneCoutInterventionEntity, UUID> {

    List<LigneCoutInterventionEntity> findByInterventionId(UUID interventionId);

    void deleteByInterventionId(UUID interventionId);

    /**
     * Somme des coûts (quantité × prix unitaire) des lignes dont l'intervention porte sur l'équipement
     * donné et a démarré sur la période [from, to) — calcul serveur (AGENTS.md §9).
     */
    @Query("SELECT COALESCE(SUM(l.quantite * l.prixUnitaire), 0) FROM LigneCoutInterventionEntity l " +
            "WHERE l.interventionId IN (" +
            "  SELECT i.id FROM InterventionEntity i " +
            "  WHERE i.equipementId = :equipementId AND i.dateDebut >= :from AND i.dateDebut < :to AND i.deletedAt IS NULL" +
            ")")
    BigDecimal sumByEquipementIdAndDateRange(
            @Param("equipementId") UUID equipementId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Somme des coûts de toutes les interventions d'un centre démarrées sur la période [from, to).
     */
    @Query("SELECT COALESCE(SUM(l.quantite * l.prixUnitaire), 0) FROM LigneCoutInterventionEntity l " +
            "WHERE l.interventionId IN (" +
            "  SELECT i.id FROM InterventionEntity i " +
            "  WHERE i.centreId = :centreId AND i.dateDebut >= :from AND i.dateDebut < :to AND i.deletedAt IS NULL" +
            ")")
    BigDecimal sumByCentreIdAndDateRange(
            @Param("centreId") UUID centreId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
