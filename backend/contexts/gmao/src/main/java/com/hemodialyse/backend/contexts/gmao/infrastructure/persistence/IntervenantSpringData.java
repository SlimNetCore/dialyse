package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Accès Spring Data au modèle de persistance des intervenants ; détail d'infrastructure. */
public interface IntervenantSpringData extends JpaRepository<IntervenantJpa, UUID> {

    /** Liste d'un centre (désactivés compris) ; le tri (nom croissant) est porté par le {@link Pageable}. */
    @Query(value = "SELECT i FROM IntervenantJpa i WHERE i.centreId = :centreId",
            countQuery = "SELECT COUNT(i) FROM IntervenantJpa i WHERE i.centreId = :centreId")
    Page<IntervenantJpa> duCentre(@Param("centreId") UUID centreId, Pageable pageable);

    @Query("SELECT i FROM IntervenantJpa i WHERE i.centreId = :centreId ORDER BY i.id")
    List<IntervenantJpa> tousDuCentre(@Param("centreId") UUID centreId);
}
