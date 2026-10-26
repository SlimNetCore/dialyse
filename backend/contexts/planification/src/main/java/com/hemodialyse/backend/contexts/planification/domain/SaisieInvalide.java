package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Une valeur saisie est inutilisable (hors borne, absente, illisible) : 400 {@code BAD_REQUEST} à l'interface. Le message
 * est celui des règles de gestion RG-PLN-001 à 006, 093, 097 et 098.
 */
public class SaisieInvalide extends RuntimeException {
    public SaisieInvalide(String message) {
        super(message);
    }
}
