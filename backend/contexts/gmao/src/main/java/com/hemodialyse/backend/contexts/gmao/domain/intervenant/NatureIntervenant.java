package com.hemodialyse.backend.contexts.gmao.domain.intervenant;

/**
 * Nature d'un intervenant : technicien interne au centre ou prestataire externe (glossaire gmao ; legacy
 * {@code TypeIntervenant}). Les codes {@code INTERNE} et {@code EXTERNE} sont ceux du contrat observé et de la base.
 */
public enum NatureIntervenant {
    INTERNE,
    EXTERNE;

    /** Reconnaissance exacte du code (casse comprise, comme {@code Enum.valueOf} du legacy) ; sinon {@link NatureInconnue}. */
    public static NatureIntervenant depuis(String code) {
        for (NatureIntervenant nature : values()) {
            if (nature.name().equals(code)) {
                return nature;
            }
        }
        throw new NatureInconnue(code);
    }
}
