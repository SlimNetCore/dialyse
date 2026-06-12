package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lots", indexes = {
        @Index(name = "idx_lot_article_peremption", columnList = "article_id, date_peremption"),
        @Index(name = "idx_lot_article", columnList = "article_id"),
        @Index(name = "idx_lot_center", columnList = "center_id")
})
public class LotJpaEntity {
    @Id
    private UUID id;
    @Column(name = "center_id", nullable = false)
    private UUID centerId;
    @Column(name = "article_id", nullable = false)
    private UUID articleId;
    @Column(name = "bon_reception_id")
    private UUID bonReceptionId;
    @Column(name = "emplacement_id")
    private UUID emplacementId;
    @Column(name = "numero_lot", nullable = false)
    private String numeroLot;
    @Column(name = "date_peremption")
    private LocalDate datePeremption;
    @Column(name = "quantite_initiale", nullable = false)
    private BigDecimal quantiteInitiale;
    @Column(name = "quantite_restante", nullable = false)
    private BigDecimal quantiteRestante;
    @Column(name = "pmp")
    private BigDecimal pmp;
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

    public UUID getBonReceptionId() {
        return bonReceptionId;
    }

    public void setBonReceptionId(UUID bonReceptionId) {
        this.bonReceptionId = bonReceptionId;
    }

    public UUID getEmplacementId() {
        return emplacementId;
    }

    public void setEmplacementId(UUID emplacementId) {
        this.emplacementId = emplacementId;
    }

    public String getNumeroLot() {
        return numeroLot;
    }

    public void setNumeroLot(String numeroLot) {
        this.numeroLot = numeroLot;
    }

    public LocalDate getDatePeremption() {
        return datePeremption;
    }

    public void setDatePeremption(LocalDate datePeremption) {
        this.datePeremption = datePeremption;
    }

    public BigDecimal getQuantiteInitiale() {
        return quantiteInitiale;
    }

    public void setQuantiteInitiale(BigDecimal quantiteInitiale) {
        this.quantiteInitiale = quantiteInitiale;
    }

    public BigDecimal getQuantiteRestante() {
        return quantiteRestante;
    }

    public void setQuantiteRestante(BigDecimal quantiteRestante) {
        this.quantiteRestante = quantiteRestante;
    }

    public BigDecimal getPmp() {
        return pmp;
    }

    public void setPmp(BigDecimal pmp) {
        this.pmp = pmp;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

