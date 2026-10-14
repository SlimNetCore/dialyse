package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Accès Spring Data au modèle de persistance des interventions ; détail d'infrastructure. Supprimées logiquement exclues des listes. */
public interface InterventionSpringData extends JpaRepository<InterventionJpa, UUID> {

    @Query(value = "SELECT i FROM InterventionJpa i WHERE i.centreId = :centreId AND i.deletedAt IS NULL",
            countQuery = "SELECT COUNT(i) FROM InterventionJpa i WHERE i.centreId = :centreId AND i.deletedAt IS NULL")
    Page<InterventionJpa> duCentre(@Param("centreId") UUID centreId, Pageable pageable);

    @Query(value = "SELECT i FROM InterventionJpa i WHERE i.centreId = :centreId AND i.statut = :statut AND i.deletedAt IS NULL",
            countQuery = "SELECT COUNT(i) FROM InterventionJpa i WHERE i.centreId = :centreId AND i.statut = :statut AND i.deletedAt IS NULL")
    Page<InterventionJpa> duCentreParStatut(@Param("centreId") UUID centreId, @Param("statut") String statut, Pageable pageable);

    /** Lit l'intervention en posant un verrou d'écriture jusqu'à la fin de la transaction (commandes d'intervention, quota des pièces jointes KB-GMAO-58). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional
    @Query("SELECT i FROM InterventionJpa i WHERE i.id = :id")
    Optional<InterventionJpa> verrouiller(@Param("id") UUID id);

    /** Une intervention non supprimée logiquement (consultation, KB-GMAO-69). */
    @Query("SELECT i FROM InterventionJpa i WHERE i.id = :id AND i.deletedAt IS NULL")
    Optional<InterventionJpa> nonSupprimee(@Param("id") UUID id);

    @Query("SELECT i FROM InterventionJpa i WHERE i.equipementId = :equipementId AND i.deletedAt IS NULL ORDER BY i.dateDebut DESC")
    List<InterventionJpa> parDebutDecroissant(@Param("equipementId") UUID equipementId, Pageable pageable);

    @Query("SELECT COUNT(i) FROM InterventionJpa i WHERE i.centreId = :centreId AND i.deletedAt IS NULL")
    long compterDuCentre(@Param("centreId") UUID centreId);

    @Query("SELECT COUNT(i) FROM InterventionJpa i WHERE i.centreId = :centreId AND i.statut = :statut AND i.deletedAt IS NULL")
    long compterDuCentreAuStatut(@Param("centreId") UUID centreId, @Param("statut") String statut);

    @Query("SELECT COUNT(i) FROM InterventionJpa i WHERE i.equipementId = :equipementId AND i.deletedAt IS NULL")
    long compterDeLEquipement(@Param("equipementId") UUID equipementId);

    @Query("SELECT COUNT(i) FROM InterventionJpa i WHERE i.centreId = :centreId AND i.deletedAt IS NULL AND ("
            + "(i.statut = 'PLANIFIEE' AND i.dateDebut < :maintenant) OR "
            + "(i.statut IN ('PLANIFIEE', 'EN_COURS') AND i.echeance IS NOT NULL AND i.echeance < :maintenant))")
    long compterARelancer(@Param("centreId") UUID centreId, @Param("maintenant") Instant maintenant);

    @Query(value = "SELECT i FROM InterventionJpa i WHERE i.equipementId = :equipementId AND i.deletedAt IS NULL",
            countQuery = "SELECT COUNT(i) FROM InterventionJpa i WHERE i.equipementId = :equipementId AND i.deletedAt IS NULL")
    Page<InterventionJpa> deLEquipement(@Param("equipementId") UUID equipementId, Pageable pageable);
}
