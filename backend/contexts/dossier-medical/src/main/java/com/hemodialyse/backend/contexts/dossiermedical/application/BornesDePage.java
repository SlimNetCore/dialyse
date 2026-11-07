package com.hemodialyse.backend.contexts.dossiermedical.application;

/**
 * Bornes de lecture REPRODUITES du legacy pour les résultats biologiques (KB-MED-83, à confirmer avec les métiers) : page négative = 0,
 * taille nulle ou négative = 20, plafond 200. Un paramètre illisible est refusé en 400 par l'interface (avant : 500).
 */
final class BornesDePage {
    static final int TAILLE_PAR_DEFAUT = 20;
    static final int TAILLE_MAXIMALE = 200;

    private BornesDePage() {
    }

    static int page(int saisie) {
        return Math.max(saisie, 0);
    }

    static int taille(int saisie) {
        return saisie <= 0 ? TAILLE_PAR_DEFAUT : Math.min(saisie, TAILLE_MAXIMALE);
    }
}
