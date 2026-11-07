package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Aucun donneur vivant avec cet identifiant pour ce patient dans ce centre : 404 {@code DONNEUR_VIVANT_INTROUVABLE} (écart décidé
 * KB-MED-88 : le legacy répondait 400 « Donneur vivant introuvable »).
 */
public class DonneurVivantIntrouvable extends RuntimeException {
    public DonneurVivantIntrouvable() {
        super("Donneur vivant introuvable");
    }
}
