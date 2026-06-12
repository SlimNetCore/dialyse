package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
public class StockMovementJpaEntity {
    @Id
    private UUID id;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "article_id", nullable = false)
    private UUID articleId;

    @Column(name = "seance_id")
    private UUID seanceId;

    @Column(name = "mouvement_type", nullable = false)
    private String mouvementType;

    @Column(name = "quantite", nullable = false)
    private BigDecimal quantite;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

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

    public String getMouvementType() {
        return mouvementType;
    }

    public void setMouvementType(String mouvementType) {
        this.mouvementType = mouvementType;
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

