package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Accès Spring Data au modèle de persistance des équipements ; détail d'infrastructure. */
public interface EquipementSpringData extends JpaRepository<EquipementJpa, UUID> {

    /** Parc d'un centre, supprimés logiquement exclus ; le tri (création décroissante) est porté par le {@link Pageable}. */
    @Query(value = "SELECT e FROM EquipementJpa e WHERE e.centreId = :centreId AND e.deletedAt IS NULL",
            countQuery = "SELECT COUNT(e) FROM EquipementJpa e WHERE e.centreId = :centreId AND e.deletedAt IS NULL")
    Page<EquipementJpa> duCentre(@Param("centreId") UUID centreId, Pageable pageable);

    @Query(value = "SELECT e FROM EquipementJpa e WHERE e.centreId = :centreId AND e.statut = :statut AND e.deletedAt IS NULL",
            countQuery = "SELECT COUNT(e) FROM EquipementJpa e WHERE e.centreId = :centreId AND e.statut = :statut AND e.deletedAt IS NULL")
    Page<EquipementJpa> duCentreParStatut(@Param("centreId") UUID centreId, @Param("statut") String statut, Pageable pageable);

    @Query("SELECT e FROM EquipementJpa e WHERE e.centreId = :centreId AND e.deletedAt IS NULL")
    List<EquipementJpa> parcDuCentre(@Param("centreId") UUID centreId);

    @Query("SELECT COUNT(e) FROM EquipementJpa e WHERE e.centreId = :centreId AND e.deletedAt IS NULL")
    long compterDuCentre(@Param("centreId") UUID centreId);

    @Query("SELECT COUNT(e) FROM EquipementJpa e WHERE e.centreId = :centreId AND e.statut = :statut AND e.deletedAt IS NULL")
    long compterDuCentreAuStatut(@Param("centreId") UUID centreId, @Param("statut") String statut);

    /** Couples (centre, nombre) des générateurs de dialyse installés avant {@code avant}, ni réformés ni désactivés ni supprimés. */
    @Query("SELECT e.centreId, COUNT(e) FROM EquipementJpa e WHERE e.type = 'GENERATEUR_DIALYSE' AND e.deletedAt IS NULL "
            + "AND e.statut NOT IN ('REFORME', 'DESACTIF') AND e.dateInstallation < :avant AND e.centreId IN :centres "
            + "GROUP BY e.centreId")
    List<Object[]> compterGenerateursInstalles(@Param("centres") Collection<UUID> centres, @Param("avant") Instant avant);

    /** Lignes (id, code, salle, statut) des générateurs de dialyse d'un centre, tous statuts, supprimés exclus, par code. */
    @Query("SELECT e.id, e.code, e.salleId, e.statut FROM EquipementJpa e WHERE e.centreId = :centreId "
            + "AND e.type = 'GENERATEUR_DIALYSE' AND e.deletedAt IS NULL ORDER BY e.code")
    List<Object[]> generateursDuCentre(@Param("centreId") UUID centreId);

    @Query("SELECT COUNT(e) FROM EquipementJpa e WHERE e.centreId = :centreId AND e.code = :code AND e.deletedAt IS NULL")
    long compterCodeNonSupprime(@Param("centreId") UUID centreId, @Param("code") String code);
}
