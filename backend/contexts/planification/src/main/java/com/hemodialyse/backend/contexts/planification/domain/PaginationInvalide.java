package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Page ou taille de page négative (400 à l'interface, KB-PLN-08).
 */
public class PaginationInvalide extends RuntimeException {
    public PaginationInvalide(String message) {
        super(message);
    }
}
