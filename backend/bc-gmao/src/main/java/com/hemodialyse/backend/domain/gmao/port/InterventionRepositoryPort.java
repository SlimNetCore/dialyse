package com.hemodialyse.backend.domain.gmao.port;

import com.hemodialyse.backend.domain.gmao.model.Intervention;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (interface) de persistance pour Intervention
 * Contrat hexagonal : le domaine ne connaît pas l'implémentation
 */
public interface InterventionRepositoryPort {

    /**
     * Sauvegarde une intervention (création ou mise à jour)
     */
    void save(Intervention intervention);

    /**
     * Récupère une intervention par son ID
     */
    Optional<Intervention> findById(UUID id);

    /**
     * Récupère toutes les interventions d'un équipement
     */
    List<Intervention> findByEquipementId(UUID equipementId);

    /**
     * Récupère toutes les interventions d'un centre
     */
    List<Intervention> findByCentreId(UUID centreId);

    /**
     * Récupère les interventions d'un centre par statut
     */
    List<Intervention> findByCentreIdAndStatut(UUID centreId, String statut);

    /**
     * Début de période « depuis l'installation » pour les cumuls.
     */
    OffsetDateTime DEPUIS_TOUJOURS = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    /**
     * Récupère les interventions d'un intervenant
     */
    List<Intervention> findByIntervenantId(UUID intervenantId);

    /**
     * Récupère les interventions planifiées ou en cours d'un équipement
     */
    List<Intervention> findPendingByEquipementId(UUID equipementId);

    /**
     * Supprime une intervention (soft delete recommandé)
     */
    void delete(UUID id);

    /**
     * Compte les interventions d'un centre
     */
    long countByCentreId(UUID centreId);

    /**
     * Compte les interventions en cours
     */
    long countByCentreIdAndStatutEnCours(UUID centreId);

    /**
     * Compte les interventions d'un centre par statut (statistiques du dashboard GMAO)
     */
    long countByCentreIdAndStatut(UUID centreId, String statut);

    /**
     * Page des interventions d'un centre, filtrée par statut si fourni (pagination obligatoire — AGENTS.md §9)
     */
    PagedResult<Intervention> findPaged(UUID centreId, String statut, int page, int size);

    /**
     * Page des interventions d'un équipement (pagination obligatoire — AGENTS.md §9)
     */
    PagedResult<Intervention> findPagedByEquipementId(UUID equipementId, int page, int size);

    /**
     * Compte les interventions d'un équipement (fiche équipement — aide à la décision)
     */
    long countByEquipementId(UUID equipementId);

    /**
     * Dernière intervention (par date de début) d'un équipement, s'il en existe une
     */
    Optional<Intervention> findLatestByEquipementId(UUID equipementId);
    /**
     * Fin de période « sans limite » pour les cumuls.
     */
    OffsetDateTime SANS_LIMITE = OffsetDateTime.of(9999, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    /**
     * Récupère les interventions d'un centre dans une période donnée
     */
    List<Intervention> findByCentreIdAndDateRange(UUID centreId, OffsetDateTime debut, OffsetDateTime fin);

    /**
     * Somme des coûts (toutes lignes de coût) des interventions d'un équipement démarrées sur la
     * période [from, to) — calculée côté serveur (AGENTS.md §9 : jamais en sommant une liste paginée
     * côté client).
     */
    BigDecimal sumCoutByEquipementIdAndDateRange(UUID equipementId, OffsetDateTime from, OffsetDateTime to);

    /**
     * Somme des coûts de toutes les interventions d'un centre démarrées sur la période [from, to)
     * (tableau de bord Direction — agrégat société, calcul serveur).
     */
    BigDecimal sumCoutByCentreIdAndDateRange(UUID centreId, OffsetDateTime from, OffsetDateTime to);

    /**
     * Coût de maintenance par équipement d'un centre sur la période [from, to) (classement Direction,
     * détection des équipements à réformer). Les équipements sans coût sont absents de la map.
     */
    Map<UUID, BigDecimal> sumCoutParEquipement(UUID centreId, OffsetDateTime from, OffsetDateTime to);

    /**
     * Compte les interventions à relancer d'un centre : planifiées dont l'heure de début est passée, ou
     * dont l'échéance est dépassée (rappel sur le tableau de bord GMAO).
     */
    long countEnRetardByCentreId(UUID centreId, OffsetDateTime maintenant);

    /**
     * Maintenance cumulée d'un équipement depuis son installation.
     */
    default BigDecimal sumCoutCumuleByEquipementId(UUID equipementId) {
        return sumCoutByEquipementIdAndDateRange(equipementId, DEPUIS_TOUJOURS, SANS_LIMITE);
    }

    /**
     * Maintenance cumulée par équipement d'un centre depuis l'installation.
     */
    default Map<UUID, BigDecimal> sumCoutCumuleParEquipement(UUID centreId) {
        return sumCoutParEquipement(centreId, DEPUIS_TOUJOURS, SANS_LIMITE);
    }
}

