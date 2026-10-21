package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.util.List;

/**
 * Les répartitions de la synthèse en barres de texte : par tranche, le libellé puis deux barres de 10 caractères, une pour les
 * patients et une pour les séances, proportionnelles au maximum de la liste (arrondi à l'entier le plus proche, la moitié
 * vers le haut : 2,5 donne 3).
 */
public final class BarresDeRepartition {

    static final int LARGEUR = 10;

    private BarresDeRepartition() {
    }

    public static String texte(List<SyntheseMensuelle.Repartition> tranches) {
        if (tranches.isEmpty()) {
            return "Aucune donnee pour la periode.";
        }
        long maxPatients = tranches.stream().mapToLong(SyntheseMensuelle.Repartition::patients).max().orElse(1L);
        long maxSeances = tranches.stream().mapToLong(SyntheseMensuelle.Repartition::seances).max().orElse(1L);
        StringBuilder texte = new StringBuilder();
        for (SyntheseMensuelle.Repartition tranche : tranches) {
            if (!texte.isEmpty()) {
                texte.append("\n");
            }
            texte.append(tranche.libelle())
                    .append("\nP ").append(barre(tranche.patients(), maxPatients))
                    .append(" ").append(tranche.patients())
                    .append(" | S ").append(barre(tranche.seances(), maxSeances))
                    .append(" ").append(tranche.seances());
        }
        return texte.toString();
    }

    /** Un maximum nul (aucune séance dans la liste) donne une barre vide. */
    private static String barre(long valeur, long maximum) {
        int rempli = maximum <= 0 ? 0 : (int) Math.round((double) valeur * LARGEUR / (double) maximum);
        rempli = Math.clamp(rempli, 0, LARGEUR);
        return "#".repeat(rempli) + "-".repeat(LARGEUR - rempli);
    }
}
