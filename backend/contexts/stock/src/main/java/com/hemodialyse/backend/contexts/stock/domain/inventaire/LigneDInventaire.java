package com.hemodialyse.backend.contexts.stock.domain.inventaire;

import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Ligne d'inventaire : un lot d'un article, sa quantité théorique (stock informatique figé à l'ouverture) et la quantité
 * réellement comptée ; l'écart doit être justifié avant la clôture. Un lot sans {@code lotId} est un lot trouvé en rayon et
 * inconnu du système (créé à la clôture). Valeur immuable.
 *
 * @param pmp     PMP de l'article à l'ouverture : valorisation des écarts et du stock de départ
 * @param ajoutee ligne ajoutée pendant le comptage (lot non prévu) : seule celle-là peut être retirée
 */
public record LigneDInventaire(UUID id, UUID articleId, String articleCode, String articleLibelle, String unite,
                               UUID lotId,
                               String numeroLot, LocalDate datePeremption, BigDecimal quantiteTheorique,
                               BigDecimal quantiteComptee,
                               BigDecimal pmp, String motifEcart, String comptePar, OffsetDateTime compteLe,
                               boolean ajoutee) {

    public LigneDInventaire {
        Objects.requireNonNull(id, "L'identifiant de la ligne est obligatoire");
        Objects.requireNonNull(articleId, "L'article est obligatoire");
        quantiteTheorique = quantiteTheorique != null ? quantiteTheorique : BigDecimal.ZERO;
        pmp = pmp != null ? pmp : BigDecimal.ZERO;
    }

    /**
     * Ligne du stock théorique, pas encore comptée.
     */
    public static LigneDInventaire theorique(UUID id, UUID articleId, String articleCode, String articleLibelle, String unite,
                                             UUID lotId, String numeroLot, LocalDate datePeremption, BigDecimal quantiteTheorique,
                                             BigDecimal pmp) {
        return new LigneDInventaire(id, articleId, articleCode, articleLibelle, unite, lotId, numeroLot, datePeremption,
                quantiteTheorique, null, pmp, null, null, null, false);
    }

    /**
     * @throws RefusDuReferentiel {@code BUSINESS_ERROR} (quantité négative, code générique du legacy, KB-STK-51) ;
     *                            {@code INVENTORY_QUANTITY_SCALE} (plus de 3 décimales)
     */
    public LigneDInventaire compter(BigDecimal quantite, String motif, String par, OffsetDateTime le) {
        Objects.requireNonNull(quantite, "La quantité comptée est obligatoire");
        if (quantite.signum() < 0) {
            throw new RefusDuReferentiel("BUSINESS_ERROR", "La quantité ne peut pas être négative");
        }
        if (quantite.scale() > 3) {
            throw new RefusDuReferentiel("INVENTORY_QUANTITY_SCALE", "La quantité comptée accepte 3 décimales au plus.");
        }
        return new LigneDInventaire(id, articleId, articleCode, articleLibelle, unite, lotId, numeroLot, datePeremption,
                quantiteTheorique, quantite, pmp, motif == null || motif.isBlank() ? null : motif.trim(), par, le, ajoutee);
    }

    public LigneDInventaire commeAjoutee() {
        return new LigneDInventaire(id, articleId, articleCode, articleLibelle, unite, lotId, numeroLot, datePeremption,
                quantiteTheorique, quantiteComptee, pmp, motifEcart, comptePar, compteLe, true);
    }

    /**
     * Rattache la ligne au lot créé à la clôture pour un lot trouvé en rayon.
     */
    public LigneDInventaire avecLot(UUID nouveauLot, String numero) {
        return new LigneDInventaire(id, articleId, articleCode, articleLibelle, unite, nouveauLot, numero, datePeremption,
                quantiteTheorique, quantiteComptee, pmp, motifEcart, comptePar, compteLe, ajoutee);
    }

    public boolean estComptee() {
        return quantiteComptee != null;
    }

    /**
     * Compté − théorique ({@code null} tant que la ligne n'est pas comptée).
     */
    public BigDecimal ecart() {
        return quantiteComptee == null ? null : quantiteComptee.subtract(quantiteTheorique);
    }

    public boolean aUnEcart() {
        BigDecimal ecart = ecart();
        return ecart != null && ecart.signum() != 0;
    }

    public BigDecimal valeurEcart() {
        BigDecimal ecart = ecart();
        return ecart == null ? null : ecart.multiply(pmp).setScale(2, RoundingMode.HALF_UP);
    }
}

