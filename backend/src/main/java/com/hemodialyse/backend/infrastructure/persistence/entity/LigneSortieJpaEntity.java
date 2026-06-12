package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bons_sortie_lignes", indexes = {
        @Index(name = "idx_bs_ligne_bon", columnList = "bon_sortie_id"),
        @Index(name = "idx_bs_ligne_lot", columnList = "lot_id")
})
public class LigneSortieJpaEntity {
    @Id
    private UUID id;
    @Column(name = "bon_sortie_id", nullable = false)
    private UUID bonSortieId;
    @Column(name = "article_id", nullable = false)
    private UUID articleId;
    @Column(name = "lot_id")
    private UUID lotId;
    @Column(name = "quantite", nullable = false)
    private BigDecimal quantite;
    @Column(name = "pmp_applique")
    private BigDecimal pmpApplique;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBonSortieId() {
        return bonSortieId;
    }

    public void setBonSortieId(UUID bonSortieId) {
        this.bonSortieId = bonSortieId;
    }

    public UUID getArticleId() {
        return articleId;
    }

    public void setArticleId(UUID articleId) {
        this.articleId = articleId;
    }

    public UUID getLotId() {
        return lotId;
    }

    public void setLotId(UUID lotId) {
        this.lotId = lotId;
    }

    public BigDecimal getQuantite() {
        return quantite;
    }

    public void setQuantite(BigDecimal quantite) {
        this.quantite = quantite;
    }

    public BigDecimal getPmpApplique() {
        return pmpApplique;
    }

    public void setPmpApplique(BigDecimal pmpApplique) {
        this.pmpApplique = pmpApplique;
    }
}

