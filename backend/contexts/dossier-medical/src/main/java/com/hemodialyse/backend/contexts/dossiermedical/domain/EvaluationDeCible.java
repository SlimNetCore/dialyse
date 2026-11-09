package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.math.BigDecimal;

/**
 * Une valeur biologique évaluée face à sa cible KDIGO ({@link CiblesKdigo}, RG-MED-081). Depuis DM9 (ADR 0101) les cibles ne sont plus
 * copiées ici : la politique du legacy est retirée avec {@code bc-medical}.
 */
public record EvaluationDeCible(String code, BigDecimal valeur, String unite, StatutDeCible statut, BigDecimal borneMin, BigDecimal borneMax,
                                String referenceKdigo) {

    public static EvaluationDeCible hemoglobine(BigDecimal valeur) {
        return CiblesKdigo.evaluer(CiblesKdigo.HEMOGLOBINE, valeur);
    }

    public static EvaluationDeCible ferritine(BigDecimal valeur) {
        return CiblesKdigo.evaluer(CiblesKdigo.FERRITINE, valeur);
    }

    public static EvaluationDeCible coefficientDeSaturation(BigDecimal valeur) {
        return CiblesKdigo.evaluer(CiblesKdigo.COEFFICIENT_DE_SATURATION, valeur);
    }

    public static EvaluationDeCible albumine(BigDecimal valeur) {
        return CiblesKdigo.evaluer(CiblesKdigo.ALBUMINE, valeur);
    }

    public static EvaluationDeCible ktV(BigDecimal valeur) {
        return CiblesKdigo.evaluer(CiblesKdigo.KT_V, valeur);
    }

    public static EvaluationDeCible phosphore(BigDecimal valeur) {
        return CiblesKdigo.evaluer(CiblesKdigo.PHOSPHORE, valeur);
    }

    public static EvaluationDeCible calcium(BigDecimal valeur) {
        return CiblesKdigo.evaluer(CiblesKdigo.CALCIUM, valeur);
    }

    public static EvaluationDeCible pth(BigDecimal valeur) {
        return CiblesKdigo.evaluer(CiblesKdigo.PTH, valeur);
    }
}
