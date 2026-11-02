package com.hemodialyse.backend.contexts.patientcouverture.domain;

/**
 * Une valeur saisie est inutilisable (absente, illisible, hors format) : 400 {@code BAD_REQUEST} à l'interface (décision du
 * 02/11, KB-PAT-01/02 : plus de 500).
 */
public class SaisieInvalide extends RuntimeException {
    public SaisieInvalide(String message) {
        super(message);
    }
}
