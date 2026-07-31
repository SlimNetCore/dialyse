package com.hemodialyse.backend.domain.stock.model;

import com.hemodialyse.backend.domain.shared.vo.Money;
import com.hemodialyse.backend.domain.shared.vo.Quantite;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class StockMovement {
    private UUID id;
    private UUID centerId;
    private UUID articleId;
    private UUID seanceId;
    private UUID lotId;
    private StockMovementType movementType;
    private BigDecimal quantite;
    private BigDecimal prixUnitaire;
    private BigDecimal pmpApres;
    private String createdBy;
    private OffsetDateTime createdAt;

    public static StockMovement sortie(UUID centerId, UUID articleId, UUID seanceId, BigDecimal quantite, String createdBy) {
        return sortie(centerId, articleId, seanceId, quantite, createdBy, OffsetDateTime.now());
    }

    public static StockMovement sortie(UUID centerId, UUID articleId, UUID seanceId,
                                       BigDecimal quantite, String createdBy, OffsetDateTime createdAt) {
        // Aggregate invariant (Shared Kernel): a movement quantity is a valid Quantite.
        Quantite.of(quantite);
        StockMovement movement = new StockMovement();
        movement.setId(UUID.randomUUID());
        movement.setCenterId(centerId);
        movement.setArticleId(articleId);
        movement.setSeanceId(seanceId);
        movement.setMovementType(StockMovementType.SORTIE);
        movement.setQuantite(quantite);
        movement.setCreatedBy(createdBy);
        movement.setCreatedAt(createdAt != null ? createdAt : OffsetDateTime.now());
        return movement;
    }

    public static StockMovement entree(UUID centerId, UUID articleId, UUID lotId,
                                       BigDecimal quantite, BigDecimal prixUnitaire, String createdBy) {
        // Aggregate invariants (Shared Kernel): valid quantity and non-negative unit price.
        Quantite.of(quantite);
        if (prixUnitaire != null) {
            Money.of(prixUnitaire);
        }
        StockMovement movement = new StockMovement();
        movement.setId(UUID.randomUUID());
        movement.setCenterId(centerId);
        movement.setArticleId(articleId);
        movement.setLotId(lotId);
        movement.setMovementType(StockMovementType.ENTREE);
        movement.setQuantite(quantite);
        movement.setPrixUnitaire(prixUnitaire);
        movement.setCreatedBy(createdBy);
        movement.setCreatedAt(OffsetDateTime.now());
        return movement;
    }

    public static StockMovement sortieLot(UUID centerId, UUID articleId, UUID seanceId, UUID lotId,
                                          BigDecimal quantite, BigDecimal pmpApplique, String createdBy) {
        StockMovement movement = sortie(centerId, articleId, seanceId, quantite, createdBy);
        movement.setLotId(lotId);
        if (pmpApplique != null) {
            Money.of(pmpApplique);
        }
        movement.setPrixUnitaire(pmpApplique);
        return movement;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public UUID getArticleId() {
        return articleId;
    }

    public void setArticleId(UUID articleId) {
        this.articleId = articleId;
    }

    public UUID getSeanceId() {
        return seanceId;
    }

    public void setSeanceId(UUID seanceId) {
        this.seanceId = seanceId;
    }

    public StockMovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(StockMovementType movementType) {
        this.movementType = movementType;
    }

    public BigDecimal getQuantite() {
        return quantite;
    }

    public void setQuantite(BigDecimal quantite) {
        this.quantite = quantite;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getLotId() {
        return lotId;
    }

    public void setLotId(UUID lotId) {
        this.lotId = lotId;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public void setPrixUnitaire(BigDecimal prixUnitaire) {
        this.prixUnitaire = prixUnitaire;
    }

    public BigDecimal getPmpApres() {
        return pmpApres;
    }

    public void setPmpApres(BigDecimal pmpApres) {
        this.pmpApres = pmpApres;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

