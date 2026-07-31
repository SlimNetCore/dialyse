package com.hemodialyse.backend.domain.article.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class Article {
    private UUID id;
    private UUID centerId;
    private String code;
    private String libelle;
    private String unite;
    private BigDecimal stockQuantity;
    private BigDecimal seuilAlerte;
    private BigDecimal pmpCourant;
    private boolean gereParLot;
    private boolean active;
    private OffsetDateTime createdAt;

    public void debiter(BigDecimal quantite) {
        if (quantite == null || quantite.signum() <= 0) {
            throw new IllegalArgumentException("La quantite consommee doit etre strictement positive");
        }
        BigDecimal current = stockQuantity != null ? stockQuantity : BigDecimal.ZERO;
        if (current.compareTo(quantite) < 0) {
            throw new IllegalStateException("Stock insuffisant pour l'article " + (code != null ? code : id));
        }
        this.stockQuantity = current.subtract(quantite);
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public String getUnite() {
        return unite;
    }

    public void setUnite(String unite) {
        this.unite = unite;
    }

    public BigDecimal getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(BigDecimal stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public BigDecimal getSeuilAlerte() {
        return seuilAlerte;
    }

    public void setSeuilAlerte(BigDecimal seuilAlerte) {
        this.seuilAlerte = seuilAlerte;
    }

    public BigDecimal getPmpCourant() {
        return pmpCourant;
    }

    public void setPmpCourant(BigDecimal pmpCourant) {
        this.pmpCourant = pmpCourant;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isGereParLot() {
        return gereParLot;
    }

    public void setGereParLot(boolean gereParLot) {
        this.gereParLot = gereParLot;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

