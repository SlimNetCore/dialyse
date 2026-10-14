package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ligne de coût d'une intervention (R16) : pièce, main d'œuvre, honoraires d'intervenant ou autre. Valeur immuable : une
 * ligne ne se modifie pas, elle se retire et se saisit à nouveau.
 * <p>
 * Quantité et prix gardent l'échelle reçue ; le montant est leur produit sans arrondi (KB-GMAO-44 : échelle 5 pour une
 * ligne saisie, 4 pour la ligne automatique). La base arrondit quantité (3 décimales) et prix (2) à l'enregistrement.
 * {@code articleStockId} est opaque : GMAO ne sort rien du stock (KB-GMAO-07, Separate Ways).
 */
public final class LigneDeCout {

    private final LigneDeCoutId id;
    private final TypeLigneCout type;
    private final String libelle;
    private final BigDecimal quantite;
    private final BigDecimal prixUnitaire;
    private final UUID articleStockId;
    private final boolean automatique;

    private LigneDeCout(LigneDeCoutId id, TypeLigneCout type, String libelle, BigDecimal quantite, BigDecimal prixUnitaire,
                        UUID articleStockId, boolean automatique) {
        this.id = id;
        this.type = type;
        this.libelle = libelle;
        this.quantite = quantite;
        this.prixUnitaire = prixUnitaire;
        this.articleStockId = articleStockId;
        this.automatique = automatique;
    }

    /** Ligne saisie par l'utilisateur. */
    public static LigneDeCout saisir(LigneDeCoutId id, TypeLigneCout type, String libelle, BigDecimal quantite,
                                     BigDecimal prixUnitaire, UUID articleStockId) {
        return creer(id, type, libelle, quantite, prixUnitaire, articleStockId, false);
    }

    /** Ligne calculée par le système à la clôture (valorisation du temps de l'intervenant, R19). */
    static LigneDeCout calculer(LigneDeCoutId id, TypeLigneCout type, String libelle, BigDecimal quantite,
                                BigDecimal prixUnitaire) {
        return creer(id, type, libelle, quantite, prixUnitaire, null, true);
    }

    /** Reconstitution depuis la persistance (adapters seulement) : aucune règle rejouée. */
    public static LigneDeCout reconstituer(LigneDeCoutId id, TypeLigneCout type, String libelle, BigDecimal quantite,
                                           BigDecimal prixUnitaire, UUID articleStockId, boolean automatique) {
        return new LigneDeCout(id, type, libelle, quantite, prixUnitaire, articleStockId, automatique);
    }

    private static LigneDeCout creer(LigneDeCoutId id, TypeLigneCout type, String libelle, BigDecimal quantite,
                                     BigDecimal prixUnitaire, UUID articleStockId, boolean automatique) {
        if (type == null) throw new ChampInvalide("Type de ligne de coût requis");
        if (libelle == null || libelle.isBlank()) throw new ChampInvalide("Libellé requis");
        if (quantite == null || quantite.signum() <= 0) throw new ChampInvalide("Quantité invalide");
        if (prixUnitaire == null || prixUnitaire.signum() < 0) throw new ChampInvalide("Prix unitaire invalide");
        return new LigneDeCout(id, type, libelle, quantite, prixUnitaire, articleStockId, automatique);
    }

    /** Quantité x prix unitaire, sans arrondi. */
    public BigDecimal montant() {
        return prixUnitaire.multiply(quantite);
    }

    public LigneDeCoutId id() {
        return id;
    }

    public TypeLigneCout type() {
        return type;
    }

    public String libelle() {
        return libelle;
    }

    public BigDecimal quantite() {
        return quantite;
    }

    public BigDecimal prixUnitaire() {
        return prixUnitaire;
    }

    public UUID articleStockId() {
        return articleStockId;
    }

    public boolean estAutomatique() {
        return automatique;
    }
}
