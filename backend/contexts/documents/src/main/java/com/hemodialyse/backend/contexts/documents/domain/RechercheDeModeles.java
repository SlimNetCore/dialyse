package com.hemodialyse.backend.contexts.documents.domain;

import java.util.UUID;

/**
 * Une page de la recherche des modèles d'un centre, filtrée sur un type (comparé tel quel, sans tenir compte de rien d'autre). La taille est
 * obligatoire et va de 1 à 200, la page est obligatoire et vaut 0 ou plus : hors bornes, la recherche est refusée (jamais ramenée), comme avant.
 */
public record RechercheDeModeles(UUID centre, String type, int page, int taille) {

    public static final int TAILLE_MAXIMALE = 200;

    public static RechercheDeModeles de(UUID centre, String type, Integer page, Integer taille) {
        if (page == null) {
            throw new ModeleRefuse("Champ obligatoire : page");
        }
        if (taille == null) {
            throw new ModeleRefuse("Champ obligatoire : size");
        }
        if (page < 0) {
            throw new ModeleRefuse("La page demandée doit être 0 ou plus");
        }
        if (taille < 1 || taille > TAILLE_MAXIMALE) {
            throw new ModeleRefuse("La taille de page doit aller de 1 à " + TAILLE_MAXIMALE);
        }
        return new RechercheDeModeles(centre, type, page, taille);
    }

    /** Le nombre de modèles à sauter pour atteindre cette page. */
    public long decalage() {
        return (long) page * taille;
    }
}
