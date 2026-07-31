package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bons_commande_lignes", indexes = {
        @Index(name = "idx_bl_ligne_bon", columnList = "bon_commande_id")
})
public class LigneBonCommandeJpaEntity {
    @Id
    private UUID id;
    @Column(name = "bon_commande_id", nullable = false)
    private UUID bonCommandeId;
    @Column(name = "article_id", nullable = false)
    private UUID articleId;
    @Column(name = "quantite", nullable = false)
    private BigDecimal quantite;
    @Column(name = "prix_unitaire")
    private BigDecimal prixUnitaire;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBonCommandeId() {
        return bonCommandeId;
    }

    public void setBonCommandeId(UUID bonCommandeId) {
        this.bonCommandeId = bonCommandeId;
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
}

