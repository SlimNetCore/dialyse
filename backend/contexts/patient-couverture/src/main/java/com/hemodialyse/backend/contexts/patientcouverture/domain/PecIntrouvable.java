package com.hemodialyse.backend.contexts.patientcouverture.domain;

/** Aucune PEC de ce centre n'a cet identifiant : 404 {@code PEC_INTROUVABLE} (avant : 400 avec le message du cache, KB-PAT-21). */
public class PecIntrouvable extends RuntimeException {
    public PecIntrouvable() {
        super("PEC introuvable");
    }
}
