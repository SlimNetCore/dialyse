package com.hemodialyse.backend.domain.stock.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.Quantite;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ligne d'inventaire : un lot d'un article, sa quantité théorique (stock informatique à l'ouverture) et la
 * quantité réellement comptée. L'écart doit être justifié avant la clôture.
 */
public class LigneInventaire {

    private UUID id;
    private UUID articleId;
    private String articleCode;
    private String articleLibelle;
    private String unite;
    /**
     * {@code null} : lot trouvé physiquement mais inconnu du système (créé à la clôture).
     */
    private UUID lotId;
    private String numeroLot;
    private LocalDate datePeremption;
    private BigDecimal quantiteTheorique;
    private BigDecimal quantiteComptee;
    /**
     * PMP de l'article à l'ouverture : valorisation des écarts et du stock de départ.
     */
    private BigDecimal pmp;
    private String motifEcart;
    private String comptePar;
    private OffsetDateTime compteLe;
    /**
     * Ligne ajoutée pendant le comptage (lot non prévu) : elle peut être retirée.
     */
    private boolean ajoutee;

    public static LigneInventaire theorique(UUID articleId, String articleCode, String articleLibelle, String unite,
                                            UUID lotId, String numeroLot, LocalDate datePeremption,
                                            BigDecimal quantiteTheorique, BigDecimal pmp) {
        LigneInventaire ligne = new LigneInventaire();
        ligne.id = UUID.randomUUID();
        ligne.articleId = articleId;
        ligne.articleCode = articleCode;
        ligne.articleLibelle = articleLibelle;
        ligne.unite = unite;
        ligne.lotId = lotId;
        ligne.numeroLot = numeroLot;
        ligne.datePeremption = datePeremption;
        ligne.quantiteTheorique = quantiteTheorique != null ? quantiteTheorique : BigDecimal.ZERO;
        ligne.pmp = pmp != null ? pmp : BigDecimal.ZERO;
        return ligne;
    }

    public void compter(BigDecimal quantite, String motif, String par, OffsetDateTime le) {
        Quantite.of(quantite);
        if (quantite.scale() > 3) {
            throw new BusinessException("INVENTORY_QUANTITY_SCALE", "La quantité comptée accepte 3 décimales au plus.");
        }
        this.quantiteComptee = quantite;
        this.motifEcart = motif == null || motif.isBlank() ? null : motif.trim();
        this.comptePar = par;
        this.compteLe = le;
    }

    public boolean isComptee() {
        return quantiteComptee != null;
    }

    /**
     * Compté − théorique ({@code null} tant que la ligne n'est pas comptée).
     */
    public BigDecimal ecart() {
        return quantiteComptee == null ? null : quantiteComptee.subtract(quantiteTheorique);
    }

    public boolean hasEcart() {
        BigDecimal ecart = ecart();
        return ecart != null && ecart.signum() != 0;
    }

    public BigDecimal valeurEcart() {
        BigDecimal ecart = ecart();
        return ecart == null ? null : ecart.multiply(pmp).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getArticleId() {
        return articleId;
    }

    public void setArticleId(UUID articleId) {
        this.articleId = articleId;
    }

    public String getArticleCode() {
        return articleCode;
    }

    public void setArticleCode(String articleCode) {
        this.articleCode = articleCode;
    }

    public String getArticleLibelle() {
        return articleLibelle;
    }

    public void setArticleLibelle(String articleLibelle) {
        this.articleLibelle = articleLibelle;
    }

    public String getUnite() {
        return unite;
    }

    public void setUnite(String unite) {
        this.unite = unite;
    }

    public UUID getLotId() {
        return lotId;
    }

    public void setLotId(UUID lotId) {
        this.lotId = lotId;
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

    public BigDecimal getQuantiteTheorique() {
        return quantiteTheorique;
    }

    public void setQuantiteTheorique(BigDecimal quantiteTheorique) {
        this.quantiteTheorique = quantiteTheorique;
    }

    public BigDecimal getQuantiteComptee() {
        return quantiteComptee;
    }

    public void setQuantiteComptee(BigDecimal quantiteComptee) {
        this.quantiteComptee = quantiteComptee;
    }

    public BigDecimal getPmp() {
        return pmp;
    }

    public void setPmp(BigDecimal pmp) {
        this.pmp = pmp;
    }

    public String getMotifEcart() {
        return motifEcart;
    }

    public void setMotifEcart(String motifEcart) {
        this.motifEcart = motifEcart;
    }

    public String getComptePar() {
        return comptePar;
    }

    public void setComptePar(String comptePar) {
        this.comptePar = comptePar;
    }

    public OffsetDateTime getCompteLe() {
        return compteLe;
    }

    public void setCompteLe(OffsetDateTime compteLe) {
        this.compteLe = compteLe;
    }

    public boolean isAjoutee() {
        return ajoutee;
    }

    public void setAjoutee(boolean ajoutee) {
        this.ajoutee = ajoutee;
    }
}

