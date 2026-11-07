package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.math.BigDecimal;

/**
 * Une valeur biologique évaluée face à sa cible KDIGO. Les cibles ci-dessous sont COPIÉES de la politique du legacy
 * ({@code KdigoEvaluationPolicy}, bc-medical, encore utilisée par FHIR et l'export) : seuils inchangés, statut {@code SOUS_CIBLE},
 * {@code DANS_CIBLE}, {@code AU_DESSUS_CIBLE} ou {@code NON_EVALUABLE}. Retrait de la copie avec le legacy (DM6/DM8).
 */
public record EvaluationDeCible(String code, BigDecimal valeur, String unite, String statut, BigDecimal borneMin, BigDecimal borneMax,
                                String referenceKdigo) {

    private record Regle(String code, BigDecimal min, BigDecimal max, String unite, String reference) {
    }

    private static final Regle HEMOGLOBINE = new Regle("HEMOGLOBINE", new BigDecimal("10"), new BigDecimal("11.5"), "g/dL", "KDIGO-2012-anemia");
    private static final Regle FERRITINE = new Regle("FERRITINE", new BigDecimal("200"), null, "ng/mL", "KDIGO-2012-anemia");
    private static final Regle CST = new Regle("CST", new BigDecimal("20"), null, "%", "KDIGO-2012-anemia");
    private static final Regle KT_V = new Regle("KT_V", new BigDecimal("1.2"), null, "", "KDIGO-adequacy");
    private static final Regle ALBUMINE = new Regle("ALBUMINE", new BigDecimal("4.0"), null, "g/dL", "KDIGO-nutrition");

    public static EvaluationDeCible hemoglobine(BigDecimal valeur) {
        return evaluer(HEMOGLOBINE, valeur);
    }

    public static EvaluationDeCible ferritine(BigDecimal valeur) {
        return evaluer(FERRITINE, valeur);
    }

    public static EvaluationDeCible coefficientDeSaturation(BigDecimal valeur) {
        return evaluer(CST, valeur);
    }

    public static EvaluationDeCible albumine(BigDecimal valeur) {
        return evaluer(ALBUMINE, valeur);
    }

    public static EvaluationDeCible ktV(BigDecimal valeur) {
        return evaluer(KT_V, valeur);
    }

    private static EvaluationDeCible evaluer(Regle regle, BigDecimal valeur) {
        String statut;
        if (valeur == null) statut = "NON_EVALUABLE";
        else if (regle.min() != null && valeur.compareTo(regle.min()) < 0) statut = "SOUS_CIBLE";
        else if (regle.max() != null && valeur.compareTo(regle.max()) > 0) statut = "AU_DESSUS_CIBLE";
        else statut = "DANS_CIBLE";
        return new EvaluationDeCible(regle.code(), valeur, regle.unite(), statut, regle.min(), regle.max(), regle.reference());
    }
}
