package com.hemodialyse.backend.contexts.stock.domain.sortie;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant : lots et mouvements de sortie (tables {@code lots} et {@code stock_movements}). Toutes les opérations
 * s'exécutent dans la transaction de l'appelant, les articles étant verrouillés (voir
 * {@link com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock#verrouiller}).
 */
public interface SortiesDuStock {

    /**
     * Le lot du centre ; vide s'il est inconnu ou d'un autre centre.
     */
    Optional<LotDuStock> lot(CentreId centre, UUID lotId);

    /**
     * Les lots de l'article à quantité restante positive, par date de péremption croissante (sans date en dernier), puis
     * date de création (FEFO) ; les lots périmés restent proposés.
     */
    List<LotDuStock> lotsDisponibles(CentreId centre, UUID articleId);

    void consommer(CentreId centre, UUID lotId, BigDecimal quantite);

    void restituer(CentreId centre, UUID lotId, BigDecimal quantite);

    /**
     * Enregistre le mouvement de sortie d'un lot, daté du jour à minuit UTC, valorisé au PMP appliqué.
     */
    void enregistrerSortie(CentreId centre, UUID articleId, UUID seanceId, UUID lotId, BigDecimal quantite, BigDecimal prix,
                           String auteur, LocalDate jour, OffsetDateTime saisiLe);

    /**
     * Supprime le mouvement de sortie correspondant à une ligne de bon (même séance, article, lot et quantité), celui saisi à
     * l'heure du bon en priorité ; sans effet s'il n'existe plus.
     *
     * @throws com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel {@code STOCK_MOVEMENT_CLOSED} si un
     *                                                                                      inventaire a clôturé ce mouvement
     */
    void retirerLaSortie(CentreId centre, UUID seanceId, UUID articleId, UUID lotId, BigDecimal quantite, OffsetDateTime saisiLe);

    record LotDuStock(UUID id, UUID articleId, String numero, LocalDate datePeremption, BigDecimal quantiteRestante) {
    }
}

