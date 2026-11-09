package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.math.BigDecimal;

/**
 * Cibles cliniques KDIGO et leur évaluation (RG-MED-081). Fonction pure et sans état : aucune alerte n'est persistée, tout est recalculé
 * à la demande. Seuils de KDIGO 2012 (anémie), KDIGO 2017 (CKD-MBD), adéquation et nutrition, versionnés avec le code. Repris MOT POUR MOT
 * de {@code KdigoEvaluationPolicy} (bc-medical, retiré par DM9, ADR 0101) ; valeurs NON validées médicalement (question ouverte).
 */
public final class CiblesKdigo {

    public static final RegleDeCible HEMOGLOBINE =
            new RegleDeCible("HEMOGLOBINE", new BigDecimal("10"), new BigDecimal("11.5"), "g/dL", "KDIGO-2012-anemia");
    public static final RegleDeCible FERRITINE = new RegleDeCible("FERRITINE", new BigDecimal("200"), null, "ng/mL", "KDIGO-2012-anemia");
    public static final RegleDeCible COEFFICIENT_DE_SATURATION = new RegleDeCible("CST", new BigDecimal("20"), null, "%", "KDIGO-2012-anemia");
    public static final RegleDeCible KT_V = new RegleDeCible("KT_V", new BigDecimal("1.2"), null, "", "KDIGO-adequacy");
    public static final RegleDeCible PHOSPHORE =
            new RegleDeCible("PHOSPHORE", new BigDecimal("2.5"), new BigDecimal("4.5"), "mg/dL", "KDIGO-2017-CKD-MBD");
    public static final RegleDeCible CALCIUM =
            new RegleDeCible("CALCIUM", new BigDecimal("8.4"), new BigDecimal("10.2"), "mg/dL", "KDIGO-2017-CKD-MBD");
    public static final RegleDeCible PTH = new RegleDeCible("PTH", new BigDecimal("130"), new BigDecimal("600"), "pg/mL", "KDIGO-2017-CKD-MBD");
    public static final RegleDeCible ALBUMINE = new RegleDeCible("ALBUMINE", new BigDecimal("4.0"), null, "g/dL", "KDIGO-nutrition");

    private CiblesKdigo() {
    }

    /** Valeur absente : {@link StatutDeCible#NON_EVALUABLE} ; sous la borne basse : {@code SOUS_CIBLE} ; au-dessus de la haute : {@code AU_DESSUS_CIBLE}. */
    public static EvaluationDeCible evaluer(RegleDeCible regle, BigDecimal valeur) {
        StatutDeCible statut;
        if (valeur == null) {
            statut = StatutDeCible.NON_EVALUABLE;
        } else if (regle.borneMin() != null && valeur.compareTo(regle.borneMin()) < 0) {
            statut = StatutDeCible.SOUS_CIBLE;
        } else if (regle.borneMax() != null && valeur.compareTo(regle.borneMax()) > 0) {
            statut = StatutDeCible.AU_DESSUS_CIBLE;
        } else {
            statut = StatutDeCible.DANS_CIBLE;
        }
        return new EvaluationDeCible(regle.code(), valeur, regle.unite(), statut, regle.borneMin(), regle.borneMax(), regle.reference());
    }
}
