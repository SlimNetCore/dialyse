package com.hemodialyse.backend.contexts.documents.domain;

/** Le commentaire libre d'un dépôt : espaces de bord retirés, 500 caractères au plus (le surplus est coupé, jamais refusé) ; absent reste absent. */
final class CommentaireDeDepot {

    static final int LONGUEUR_MAXIMALE = 500;

    private CommentaireDeDepot() {
    }

    static String nettoye(String brut) {
        if (brut == null) {
            return null;
        }
        String net = brut.trim();
        return net.length() > LONGUEUR_MAXIMALE ? net.substring(0, LONGUEUR_MAXIMALE) : net;
    }
}
