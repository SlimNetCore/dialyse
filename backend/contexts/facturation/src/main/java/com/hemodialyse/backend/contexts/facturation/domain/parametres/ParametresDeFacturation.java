package com.hemodialyse.backend.contexts.facturation.domain.parametres;

import java.time.Instant;

/**
 * Paramètres de facturation d'un centre : modèle de numérotation des factures (RG-FAC-010) et regroupement multi-forfait
 * (RG-FAC-011 : une facture par patient et par période, sinon une par patient et par forfait). Un centre qui n'a rien
 * enregistré a les {@linkplain #PAR_DEFAUT valeurs par défaut}, sans date de mise à jour. Le taux de TVA n'en fait pas partie
 * (règles de TVA de la Comptabilité, ADR 0030).
 *
 * @param misAJourLe {@code null} tant que le centre n'a rien enregistré
 */
public record ParametresDeFacturation(ModeleDeNumerotation modele, boolean regroupementMultiForfait, Instant misAJourLe) {

    public static final ParametresDeFacturation PAR_DEFAUT =
            new ParametresDeFacturation(ModeleDeNumerotation.PAR_DEFAUT, true, null);

    public ParametresDeFacturation {
        if (modele == null) {
            throw new IllegalArgumentException("Le code de facturation est obligatoire");
        }
    }

    /**
     * Les paramètres saisis par l'administrateur à cet instant (le modèle est contrôlé : {@link ModeleDeNumerotation#saisi}).
     */
    public static ParametresDeFacturation saisis(String modele, boolean regroupementMultiForfait, Instant maintenant) {
        return new ParametresDeFacturation(ModeleDeNumerotation.saisi(modele), regroupementMultiForfait, maintenant);
    }
}
