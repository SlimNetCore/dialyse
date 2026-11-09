package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Point d'attention KDIGO soulevé par un résultat sérologique positif dans le cadre du bilan pré-greffe : un signalement, pas un jugement
 * d'éligibilité (la décision reste au médecin et à la RCP), RG-MED-082.
 */
public record AlerteSerologiqueKdigo(String marqueur, String resultat, String message, String referenceKdigo) {
}
