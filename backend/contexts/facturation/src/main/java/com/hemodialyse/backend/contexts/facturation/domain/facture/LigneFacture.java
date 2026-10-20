package com.hemodialyse.backend.contexts.facturation.domain.facture;

import com.hemodialyse.backend.contexts.facturation.domain.simulation.LigneSimulee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Ligne d'une facture émise (RG-FAC-031) : le forfait, son libellé et son prix unitaire hors taxe tels qu'ils étaient à
 * l'émission, et le nombre de séances qu'il couvre. Ces valeurs sont figées : un changement ultérieur du référentiel des
 * forfaits ne les modifie jamais (F8).
 *
 * @param forfait identifiant du forfait
 */
public record LigneFacture(UUID forfait, String libelle, BigDecimal prixUnitaireHt, int nombreDeSeances) {

    public LigneFacture {
        if (prixUnitaireHt == null || prixUnitaireHt.signum() < 0) {
            throw new IllegalArgumentException("Le prix unitaire HT est invalide");
        }
        if (nombreDeSeances <= 0) {
            throw new IllegalArgumentException("Le nombre de seances doit etre strictement positif");
        }
        libelle = libelle == null ? "Forfait" : libelle;
        prixUnitaireHt = prixUnitaireHt.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * La ligne que la simulation annonçait : même forfait, même prix, même nombre de séances.
     */
    static LigneFacture depuis(LigneSimulee ligne) {
        return new LigneFacture(ligne.forfait().id(), ligne.forfait().libelle(), ligne.prixUnitaireHt(), ligne.nombreDeSeances());
    }

    public BigDecimal montantHt() {
        return prixUnitaireHt.multiply(BigDecimal.valueOf(nombreDeSeances)).setScale(2, RoundingMode.HALF_UP);
    }
}
