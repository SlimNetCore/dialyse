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
    /**
     * Ligne générée par le système à la clôture (recalculée après rectification), non saisie par l'utilisateur.
     */
    private final boolean automatique;

    private LigneCoutIntervention(UUID id, TypeLigneCout type, String libelle, BigDecimal quantite,
                                  BigDecimal prixUnitaire, UUID articleStockId, boolean automatique) {
        this.automatique = automatique;
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
        return new LigneCoutIntervention(UUID.randomUUID(), type, libelle, quantite, prixUnitaire, articleStockId, false);
    }

    /**
     * Ligne calculée par le système (honoraires ou main d'œuvre de l'intervenant à la clôture).
     */
    public static LigneCoutIntervention creerAutomatique(
            TypeLigneCout type, String libelle, BigDecimal quantite, BigDecimal prixUnitaire) {
        LigneCoutIntervention l = creer(type, libelle, quantite, prixUnitaire, null);
        return new LigneCoutIntervention(l.id, l.type, l.libelle, l.quantite, l.prixUnitaire, null, true);
    }

    public static LigneCoutIntervention reconstruct(
            UUID id, TypeLigneCout type, String libelle, BigDecimal quantite, BigDecimal prixUnitaire, UUID articleStockId) {
        return new LigneCoutIntervention(id, type, libelle, quantite, prixUnitaire, articleStockId, false);
    }

    public static LigneCoutIntervention reconstruct(
            UUID id, TypeLigneCout type, String libelle, BigDecimal quantite, BigDecimal prixUnitaire,
            UUID articleStockId, boolean automatique) {
        return new LigneCoutIntervention(id, type, libelle, quantite, prixUnitaire, articleStockId, automatique);
    }

    /**
     * Montant de la ligne (quantité × prix unitaire), jamais négatif.
     */
    public BigDecimal montant() {
        return Money.of(prixUnitaire).times(Quantite.of(quantite)).amount();
    }

    public boolean isAutomatique() {
        return automatique;
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
