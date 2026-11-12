package com.hemodialyse.backend.contexts.pilotagedirection.application;

import java.util.List;

/**
 * Le rapport de synthèse de la direction, mis en forme pour l'impression (PL9, ADR 0148) : des textes déjà formatés (« &lt; seuil »,
 * « — », montants en dinars) rangés en tableaux de 13 cellules au plus. Le gabarit d'impression ne contient aucune requête : il ne fait
 * que poser ces lignes.
 *
 * @param titre      le titre du document
 * @param societe    la raison sociale imprimée (papier en-tête de la société, sinon celle du rapport)
 * @param contexte   la ligne de contexte (société, période, horodatage au fuseau d'Alger, seuil d'anonymat)
 * @param horodatage « édité le … » du pied de page (fuseau d'Alger)
 * @param note       la note de bas de rapport
 * @param lignes     les lignes, section par section, dans l'ordre d'impression
 */
public record PageDuRapport(String titre, String societe, String contexte, String horodatage, String note, List<Ligne> lignes) {

    /** Nombre maximal de cellules d'une ligne de tableau. */
    public static final int CELLULES_MAX = 13;

    public PageDuRapport {
        lignes = List.copyOf(lignes);
    }

    /**
     * @param section       identifiant de la section (le tableau), unique dans le rapport
     * @param titreDeSection titre imprimé avant le tableau ; vide pour la suite d'un même thème
     * @param genre         ENTETE, LIGNE, TOTAL (tableaux) ou TEXTE, TEXTE_CRITIQUE (une phrase sur toute la largeur)
     * @param cellules      les textes des cellules ({@link #CELLULES_MAX} au plus)
     */
    public record Ligne(String section, String titreDeSection, Genre genre, List<String> cellules) {
        public Ligne {
            if (cellules.size() > CELLULES_MAX) {
                throw new IllegalArgumentException("Une ligne de tableau compte " + CELLULES_MAX + " cellules au plus");
            }
            cellules = List.copyOf(cellules);
        }
    }

    public enum Genre { ENTETE, LIGNE, TOTAL, TEXTE, TEXTE_CRITIQUE }
}
