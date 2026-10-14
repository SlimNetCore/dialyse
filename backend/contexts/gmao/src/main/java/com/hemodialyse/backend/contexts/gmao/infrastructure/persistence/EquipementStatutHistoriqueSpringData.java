package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Accès Spring Data à l'historique de statuts ; détail d'infrastructure. */
public interface EquipementStatutHistoriqueSpringData extends JpaRepository<EquipementStatutHistoriqueJpa, UUID> {

    @Query("SELECT h FROM EquipementStatutHistoriqueJpa h WHERE h.equipementId = :equipementId ORDER BY h.changedAt ASC")
    List<EquipementStatutHistoriqueJpa> duPlusAncienAuPlusRecent(@Param("equipementId") UUID equipementId);

    /** L'historique des équipements du centre (supprimés logiquement exclus), du plus ancien au plus récent. */
    @Query("SELECT h FROM EquipementStatutHistoriqueJpa h WHERE h.equipementId IN ("
            + "SELECT e.id FROM EquipementJpa e WHERE e.centreId = :centreId AND e.deletedAt IS NULL) ORDER BY h.changedAt ASC")
    List<EquipementStatutHistoriqueJpa> duCentre(@Param("centreId") UUID centreId);
}
