package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Aucune étape avec cet identifiant pour ce patient dans ce centre : 404 {@code ETAPE_BILAN_INTROUVABLE} (écart décidé KB-MED-88 : le
 * legacy répondait 400 « Étape de bilan pré-greffe introuvable »).
 */
public class EtapeDeBilanIntrouvable extends RuntimeException {
    public EtapeDeBilanIntrouvable() {
        super("Étape de bilan pré-greffe introuvable");
    }
}
