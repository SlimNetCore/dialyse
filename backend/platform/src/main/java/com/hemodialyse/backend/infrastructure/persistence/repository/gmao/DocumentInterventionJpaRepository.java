package com.hemodialyse.backend.infrastructure.persistence.repository.gmao;

import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.DocumentInterventionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentInterventionJpaRepository extends JpaRepository<DocumentInterventionEntity, UUID> {

    /**
     * Métadonnées seules (le contenu binaire n'est jamais chargé pour une liste) :
     * lignes {@code [id, interventionId, centreId, type, nom, contentType, taille, ajoutePar, ajouteLe]}.
     */
    @Query("SELECT d.id, d.interventionId, d.centreId, d.type, d.nom, d.contentType, d.taille, d.ajoutePar, d.ajouteLe " +
            "FROM DocumentInterventionEntity d WHERE d.interventionId = :interventionId ORDER BY d.ajouteLe DESC")
    List<Object[]> findMetaByInterventionId(@Param("interventionId") UUID interventionId, Pageable pageable);

    long countByInterventionId(UUID interventionId);
}
