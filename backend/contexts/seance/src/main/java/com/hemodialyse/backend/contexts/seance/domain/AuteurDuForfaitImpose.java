package com.hemodialyse.backend.contexts.seance.domain;

/**
 * Compte au nom duquel un forfait est imposé à une séance : nettoyé des espaces de bord, jamais vide, au plus 100 caractères (colonne
 * {@code varchar(100)}). KB-SEA-42 (reproduit, à faire confirmer métiers / DPO avant la mise en production) : c'est un champ libre du
 * corps de la requête, jamais le compte de session. KB-SEA-44 (corrigé) : au-delà de 100 caractères, 400 et non plus 500.
 */
public record AuteurDuForfaitImpose(String valeur) {

    public static final int LONGUEUR_MAXIMALE = 100;

    public AuteurDuForfaitImpose {
        if (valeur == null || valeur.isBlank()) throw new SaisieInvalide("Auteur requis");
        valeur = valeur.trim();
        if (valeur.length() > LONGUEUR_MAXIMALE) throw new SaisieInvalide("Auteur trop long (100 caractères au plus)");
    }
}
