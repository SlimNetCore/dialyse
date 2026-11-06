package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Une valeur de la requête est inutilisable (absente, illisible, hors borne) : 400 {@code BAD_REQUEST} à l'interface (décision du
 * 06/11, KB-MED-06/07 : plus de 500).
 */
public class SaisieInvalide extends RuntimeException {
    public SaisieInvalide(String message) {
        super(message);
    }
}
