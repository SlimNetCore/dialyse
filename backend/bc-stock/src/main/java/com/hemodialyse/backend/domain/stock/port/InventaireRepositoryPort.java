package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Inventaire;
import com.hemodialyse.backend.domain.stock.model.InventaireStatut;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Port Out — persistance des inventaires et clôture des mouvements de stock.
 */
public interface InventaireRepositoryPort {

    /**
     * Enregistre l'en-tête et remplace toutes les lignes.
     */
    Inventaire save(Inventaire inventaire);

    Optional<Inventaire> findById(CenterId centerId, UUID id);

    Optional<Inventaire> findEnCours(CenterId centerId);

    /**
     * Date du dernier inventaire clôturé : aucun mouvement ne peut plus être daté jusqu'à ce jour inclus.
     */
    Optional<LocalDate> derniereCloture(CenterId centerId);

    PagedResult<InventaireResume> findPaged(CenterId centerId, int page, int size);

    /**
     * Existe-t-il des mouvements datés après cet instant ?
     */
    boolean hasMovementsAfter(CenterId centerId, OffsetDateTime instant);

    /**
     * Clôture (rattache à l'inventaire) tous les mouvements encore ouverts datés jusqu'à cet instant inclus.
     */
    int cloturerMouvements(CenterId centerId, UUID inventaireId, OffsetDateTime jusqua);

    /**
     * Synthèse d'un inventaire pour l'historique (sans les lignes).
     */
    record InventaireResume(UUID id, String reference, LocalDate dateInventaire, InventaireStatut statut,
                            int lignes, int comptees, int ecarts, BigDecimal valeurEcarts,
                            String createdBy, OffsetDateTime createdAt, String closedBy, OffsetDateTime closedAt) {
    }
}

