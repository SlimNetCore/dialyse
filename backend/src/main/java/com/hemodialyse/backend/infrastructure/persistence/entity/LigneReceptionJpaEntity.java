package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bons_reception_lignes", indexes = {
        @Index(name = "idx_br_ligne_bon", columnList = "bon_reception_id")
})
public class LigneReceptionJpaEntity {
    @Id
    private UUID id;
    @Column(name = "bon_reception_id", nullable = false)
    private UUID bonReceptionId;
    @Column(name = "article_id", nullable = false)
    private UUID articleId;
    @Column(name = "quantite", nullable = false)
    private BigDecimal quantite;
    @Column(name = "prix_unitaire")
    private BigDecimal prixUnitaire;
    @Column(name = "numero_lot")
    private String numeroLot;
    @Column(name = "date_peremption")
    private LocalDate datePeremption;
    @Column(name = "emplacement_id")
    private UUID emplacementId;
    @Column(name = "lot_id")
    private UUID lotId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBonReceptionId() {
        return bonReceptionId;
    }

    public void setBonReceptionId(UUID bonReceptionId) {
        this.bonReceptionId = bonReceptionId;
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

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public void setPrixUnitaire(BigDecimal prixUnitaire) {
        this.prixUnitaire = prixUnitaire;
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

    public UUID getEmplacementId() {
        return emplacementId;
    }

    public void setEmplacementId(UUID emplacementId) {
        this.emplacementId = emplacementId;
    }

    public UUID getLotId() {
        return lotId;
    }

    public void setLotId(UUID lotId) {
        this.lotId = lotId;
    }
}

