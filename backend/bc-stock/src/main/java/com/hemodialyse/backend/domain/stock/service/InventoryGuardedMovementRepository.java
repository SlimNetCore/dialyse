package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Inventaire;
import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.port.InventaireRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Décorateur du port des mouvements de stock : par lui passent toutes les entrées, sorties et corrections
 * (bons de réception, bons de sortie, consommations de séance…). Il applique les règles d'inventaire :
 * <ul>
 *   <li><b>inventaire en cours</b> : aucun mouvement ne peut être créé, modifié ni supprimé ;</li>
 *   <li><b>période clôturée</b> : aucun mouvement ne peut être daté au plus tard le jour du dernier inventaire ;</li>
 *   <li><b>mouvement clôturé</b> : il ne peut plus être modifié ni supprimé.</li>
 * </ul>
 * Les lectures et le recalcul du PMP restent permis. L'exception levée annule toute la transaction appelante
 * (lots compris).
 */
public class InventoryGuardedMovementRepository implements StockMovementRepositoryPort {

    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StockMovementRepositoryPort delegate;
    private final InventaireRepositoryPort inventaires;

    public InventoryGuardedMovementRepository(StockMovementRepositoryPort delegate, InventaireRepositoryPort inventaires) {
        this.delegate = delegate;
        this.inventaires = inventaires;
    }

    @Override
    public StockMovement save(StockMovement movement) {
        CenterId centerId = CenterId.of(movement.getCenterId());
        assertNoInventoryInProgress(centerId);
        if (movement.isCloture()) {
            throw new BusinessException("STOCK_MOVEMENT_CLOSED",
                    "Ce mouvement appartient à une période clôturée par inventaire : il ne peut plus être modifié.");
        }
        assertDateOpen(centerId, movement.getCreatedAt());
        return delegate.save(movement);
    }

    @Override
    public void deleteBySeanceAndArticle(CenterId centerId, UUID seanceId, UUID articleId) {
        assertNoInventoryInProgress(centerId);
        if (delegate.findBySeanceAndArticle(centerId, seanceId, articleId).stream().anyMatch(StockMovement::isCloture)) {
            throw new BusinessException("STOCK_MOVEMENT_CLOSED",
                    "Ces consommations appartiennent à une période clôturée par inventaire : elles ne peuvent plus être modifiées.");
        }
        delegate.deleteBySeanceAndArticle(centerId, seanceId, articleId);
    }

    private void assertNoInventoryInProgress(CenterId centerId) {
        Optional<Inventaire> enCours = inventaires.findEnCours(centerId);
        if (enCours.isPresent()) {
            throw new BusinessException("STOCK_INVENTORY_IN_PROGRESS",
                    "Inventaire " + enCours.get().getReference() + " en cours : aucun mouvement de stock n'est permis "
                            + "jusqu'à sa clôture ou son annulation.");
        }
    }

    private void assertDateOpen(CenterId centerId, OffsetDateTime createdAt) {
        Optional<LocalDate> derniere = inventaires.derniereCloture(centerId);
        if (derniere.isEmpty() || createdAt == null) return;
        LocalDate date = createdAt.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        if (!date.isAfter(derniere.get())) {
            throw new BusinessException("STOCK_PERIOD_CLOSED",
                    "Période clôturée : le stock a été inventorié le " + derniere.get().format(FR)
                            + ". Un mouvement doit être daté au plus tôt le " + derniere.get().plusDays(1).format(FR) + ".");
        }
    }

    // ---- Lectures et recalcul : délégation directe --------------------------------------------------------------

    @Override
    public List<StockMovement> findByArticleOrdered(CenterId centerId, UUID articleId) {
        return delegate.findByArticleOrdered(centerId, articleId);
    }

    @Override
    public List<StockMovement> findByArticleBefore(CenterId centerId, UUID articleId, OffsetDateTime before) {
        return delegate.findByArticleBefore(centerId, articleId, before);
    }

    @Override
    public void updatePmpApres(UUID movementId, java.math.BigDecimal pmpApres) {
        delegate.updatePmpApres(movementId, pmpApres);
    }

    @Override
    public Optional<StockMovement> findFirstEntreeByLot(CenterId centerId, UUID lotId) {
        return delegate.findFirstEntreeByLot(centerId, lotId);
    }

    @Override
    public List<StockMovement> findBySeanceAndArticle(CenterId centerId, UUID seanceId, UUID articleId) {
        return delegate.findBySeanceAndArticle(centerId, seanceId, articleId);
    }

    @Override
    public void applyRecalc(List<MovementRecalc> updates) {
        delegate.applyRecalc(updates);
    }
}

