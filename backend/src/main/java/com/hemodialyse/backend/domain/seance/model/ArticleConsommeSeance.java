package com.hemodialyse.backend.domain.seance.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ArticleConsommeSeance {
    private UUID id;
    private UUID voletParamedicalId;
    private UUID centerId;
    private UUID articleId;
    private BigDecimal quantite;
    private String lot;
    private UUID stockMouvementId;
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getVoletParamedicalId() {
        return voletParamedicalId;
    }

    public void setVoletParamedicalId(UUID v) {
        this.voletParamedicalId = v;
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

    public BigDecimal getQuantite() {
        return quantite;
    }

    public void setQuantite(BigDecimal quantite) {
        this.quantite = quantite;
    }

    public String getLot() {
        return lot;
    }

    public void setLot(String lot) {
        this.lot = lot;
    }

    public UUID getStockMouvementId() {
        return stockMouvementId;
    }

    public void setStockMouvementId(UUID stockMouvementId) {
        this.stockMouvementId = stockMouvementId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

