package com.hemodialyse.backend.domain.medical.kdigo.valueobject;

/**
 * Point d'attention KDIGO soulevé par un résultat sérologique positif dans le cadre du bilan
 * pré-greffe (ex. VIH, VHB, VHC) — un signalement, pas un jugement d'éligibilité : la décision
 * reste au médecin / à la RCP.
 */
public record AlerteSerologieKdigo(
        String marqueur,
        String resultat,
        String message,
        String referenceKdigo
) {
}
