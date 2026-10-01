package com.hemodialyse.backend.infrastructure.persistence.entity.gmao;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entité JPA pour une ligne de coût d'intervention GMAO (pièce, main d'œuvre, intervenant...).
 * Contrairement aux tâches d'intervention (non persistées — défaut préexistant hors scope), les
 * coûts doivent survivre au rechargement de l'agrégat (aide à la décision).
 */
@Entity
@Table(name = "gmao_lignes_cout_intervention")
public class LigneCoutInterventionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID interventionId;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 255)
    private String libelle;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantite;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal prixUnitaire;

    @Column
    private UUID articleStockId;

    public LigneCoutInterventionEntity() {
    }

    public LigneCoutInterventionEntity(UUID id, UUID interventionId, String type, String libelle,
                                       BigDecimal quantite, BigDecimal prixUnitaire, UUID articleStockId) {
        this.id = id;
        this.interventionId = interventionId;
        this.type = type;
        this.libelle = libelle;
        this.quantite = quantite;
        this.prixUnitaire = prixUnitaire;
        this.articleStockId = articleStockId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getInterventionId() {
        return interventionId;
    }

    public void setInterventionId(UUID interventionId) {
        this.interventionId = interventionId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
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

    public UUID getArticleStockId() {
        return articleStockId;
    }

    public void setArticleStockId(UUID articleStockId) {
        this.articleStockId = articleStockId;
    }
}
