package com.hemodialyse.backend.contexts.seance.domain;

/**
 * Une ligne de {@code seances} porte une valeur que le modèle ne sait pas lire (statut inconnu, motif inconnu). KB-SEA-07 : le message
 * ne nomme jamais de classe technique ; l'identifiant sert au journal.
 */
public class SeanceIllisible extends RuntimeException {
    private final SeanceId seance;

    public SeanceIllisible(SeanceId seance, String detail) {
        super("La séance " + seance.valeur() + " porte une valeur illisible : " + detail);
        this.seance = seance;
    }

    public SeanceId seance() {
        return seance;
    }
}
