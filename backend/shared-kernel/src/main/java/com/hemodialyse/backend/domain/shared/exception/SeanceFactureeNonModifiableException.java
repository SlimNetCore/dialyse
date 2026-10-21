package com.hemodialyse.backend.domain.shared.exception;

/**
 * Refus unique d'une modification touchant une séance au statut FACTUREE (date, forfait, validation, signature,
 * consommables, volets, déverrouillage, administration de traitement, bon de sortie). Réponse HTTP 422 de code stable
 * {@link #CODE} (slice F6, décision du 2026-10-21, KB-FAC-70). La suppression garde son propre code
 * {@code SEANCE_FACTUREE_NON_SUPPRIMABLE}.
 */
public class SeanceFactureeNonModifiableException extends IllegalStateException {

    public static final String CODE = "SEANCE_FACTUREE_NON_MODIFIABLE";

    public SeanceFactureeNonModifiableException() {
        super("La seance facturee ne peut plus etre modifiee");
    }
}
