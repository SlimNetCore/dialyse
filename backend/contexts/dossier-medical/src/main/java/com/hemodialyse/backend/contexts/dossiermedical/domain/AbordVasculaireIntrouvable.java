package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Aucun abord vasculaire avec cet identifiant pour ce patient dans ce centre : 404 {@code ABORD_VASCULAIRE_INTROUVABLE}. Écart décidé
 * (KB-MED-62) : le legacy créait l'abord d'un identifiant inconnu et réaffectait celui d'un autre patient ou d'un autre centre.
 */
public class AbordVasculaireIntrouvable extends RuntimeException {
    public AbordVasculaireIntrouvable() {
        super("Abord vasculaire introuvable");
    }
}
