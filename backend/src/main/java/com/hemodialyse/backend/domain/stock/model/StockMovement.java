package com.hemodialyse.backend.domain.stock.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class StockMovement {
    private UUID id;
    private UUID centerId;
    private UUID articleId;
    private UUID seanceId;
    private StockMovementType movementType;
    private BigDecimal quantite;
    private String createdBy;
    private OffsetDateTime createdAt;

    public static StockMovement sortie(UUID centerId, UUID articleId, UUID seanceId, BigDecimal quantite, String createdBy) {
        StockMovement movement = new StockMovement();
        movement.setId(UUID.randomUUID());
        movement.setCenterId(centerId);
        movement.setArticleId(articleId);
        movement.setSeanceId(seanceId);
        movement.setMovementType(StockMovementType.SORTIE);
        movement.setQuantite(quantite);
        movement.setCreatedBy(createdBy);
        movement.setCreatedAt(OffsetDateTime.now());
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

