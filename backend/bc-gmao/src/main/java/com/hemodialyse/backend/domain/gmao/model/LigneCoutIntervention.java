package com.hemodialyse.backend.domain.gmao.model;

import com.hemodialyse.backend.domain.shared.vo.Money;
import com.hemodialyse.backend.domain.shared.vo.Quantite;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ligne de coût d'une intervention GMAO (pièce, main d'œuvre, intervenant...) — aide à la décision
 * sur le coût réel de maintenance d'un équipement. Patron {@code LigneBonCommande} (bc-stock).
 * <p>
 * Contrairement à {@link TacheIntervention} (actuellement non persistée — défaut préexistant hors
 * scope), cette entité enfant doit être explicitement mappée par l'adaptateur de persistance.
 */
public final class LigneCoutIntervention {

    private final UUID id;
    private final TypeLigneCout type;
    private final String libelle;
    private final BigDecimal quantite;
    private final BigDecimal prixUnitaire;
    private final UUID articleStockId;

    private LigneCoutIntervention(UUID id, TypeLigneCout type, String libelle, BigDecimal quantite,
                                  BigDecimal prixUnitaire, UUID articleStockId) {
        this.id = id;
        this.type = type;
        this.libelle = libelle;
        this.quantite = quantite;
        this.prixUnitaire = prixUnitaire;
        this.articleStockId = articleStockId;
    }

    public static LigneCoutIntervention creer(
            TypeLigneCout type, String libelle, BigDecimal quantite, BigDecimal prixUnitaire, UUID articleStockId) {
        if (type == null) throw new IllegalArgumentException("Type de ligne de coût requis");
        if (libelle == null || libelle.isBlank()) throw new IllegalArgumentException("Libellé requis");
        if (quantite == null || quantite.signum() <= 0) throw new IllegalArgumentException("Quantité invalide");
        if (prixUnitaire == null || prixUnitaire.signum() < 0)
            throw new IllegalArgumentException("Prix unitaire invalide");
        return new LigneCoutIntervention(UUID.randomUUID(), type, libelle, quantite, prixUnitaire, articleStockId);
    }

    public static LigneCoutIntervention reconstruct(
            UUID id, TypeLigneCout type, String libelle, BigDecimal quantite, BigDecimal prixUnitaire, UUID articleStockId) {
        return new LigneCoutIntervention(id, type, libelle, quantite, prixUnitaire, articleStockId);
    }

    /**
     * Montant de la ligne (quantité × prix unitaire), jamais négatif.
     */
    public BigDecimal montant() {
        return Money.of(prixUnitaire).times(Quantite.of(quantite)).amount();
    }

    public UUID getId() {
        return id;
    }

    public TypeLigneCout getType() {
        return type;
    }

    public String getLibelle() {
        return libelle;
    }

    public BigDecimal getQuantite() {
        return quantite;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public UUID getArticleStockId() {
        return articleStockId;
    }
}
