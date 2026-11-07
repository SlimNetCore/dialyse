package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Le statut du bilan pré-greffe du patient est illisible en base (valeur disparue de l'énumération) : 422
 * {@code BILAN_PRE_GREFFE_ILLISIBLE}, sans donnée de santé dans le message (avant : 400 avec le nom de classe interne, KB-MED-90).
 * Repérage SELECT seule : {@code verifications-production.md} section 51.
 */
public class BilanPreGreffeIllisible extends RuntimeException {
    public BilanPreGreffeIllisible() {
        super("Le bilan pré-greffe de ce patient est illisible en base");
    }
}
