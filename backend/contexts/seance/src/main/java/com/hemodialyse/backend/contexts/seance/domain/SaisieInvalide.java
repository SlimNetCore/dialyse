package com.hemodialyse.backend.contexts.seance.domain;

/**
 * Une valeur de la requête est inutilisable (absente, illisible, hors borne) : 400 {@code BAD_REQUEST} à l'interface (décision SE1,
 * KB-SEA-04 : plus de 500).
 */
public class SaisieInvalide extends RuntimeException {
    public SaisieInvalide(String message) {
        super(message);
    }
}
