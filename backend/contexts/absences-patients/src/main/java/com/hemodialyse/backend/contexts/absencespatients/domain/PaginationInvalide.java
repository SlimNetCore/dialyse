package com.hemodialyse.backend.contexts.absencespatients.domain;

/**
 * Page ou taille de page négative (400 à l'interface, KB-ABS-02).
 */
public class PaginationInvalide extends RuntimeException {
    public PaginationInvalide(String message) {
        super(message);
    }
}
