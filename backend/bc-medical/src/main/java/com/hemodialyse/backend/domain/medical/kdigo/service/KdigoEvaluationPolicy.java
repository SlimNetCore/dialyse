package com.hemodialyse.backend.domain.medical.kdigo.service;

import com.hemodialyse.backend.domain.medical.kdigo.valueobject.EvaluationCible;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.RegleCibleKdigo;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.StatutCible;

import java.math.BigDecimal;

/**
 * Classe pure du domaine (AGENTS.md §3) — évalue une valeur clinique face aux cibles KDIGO.
 * <p>
 * Volontairement une fonction pure et sans état : aucune alerte n'est persistée, tout est
 * recalculé à la demande à partir de la dernière valeur connue. Les seuils sont ceux de
 * KDIGO 2012 (anémie du MRC) et KDIGO 2017 (CKD-MBD, phospho-calcique) ; ils sont volontairement
 * codés ici plutôt qu'en base pour rester versionnés avec le code qui les applique — un centre
 * qui voudrait ses propres seuils devra passer par une {@link RegleCibleKdigo} personnalisée
 * fournie en paramètre (voir {@link #evaluer}).
 */
public final class KdigoEvaluationPolicy {

    public static final RegleCibleKdigo HEMOGLOBINE =
            new RegleCibleKdigo("HEMOGLOBINE", new BigDecimal("10"), new BigDecimal("11.5"), "g/dL", "KDIGO-2012-anemia");

    public static final RegleCibleKdigo FERRITINE =
            new RegleCibleKdigo("FERRITINE", new BigDecimal("200"), null, "ng/mL", "KDIGO-2012-anemia");

    public static final RegleCibleKdigo COEFFICIENT_SATURATION_TRANSFERRINE =
            new RegleCibleKdigo("CST", new BigDecimal("20"), null, "%", "KDIGO-2012-anemia");

    public static final RegleCibleKdigo KT_V =
            new RegleCibleKdigo("KT_V", new BigDecimal("1.2"), null, "", "KDIGO-adequacy");

    public static final RegleCibleKdigo PHOSPHORE =
            new RegleCibleKdigo("PHOSPHORE", new BigDecimal("2.5"), new BigDecimal("4.5"), "mg/dL", "KDIGO-2017-CKD-MBD");

    public static final RegleCibleKdigo CALCIUM_CORRIGE =
            new RegleCibleKdigo("CALCIUM", new BigDecimal("8.4"), new BigDecimal("10.2"), "mg/dL", "KDIGO-2017-CKD-MBD");

    public static final RegleCibleKdigo PTH =
            new RegleCibleKdigo("PTH", new BigDecimal("130"), new BigDecimal("600"), "pg/mL", "KDIGO-2017-CKD-MBD");

    public static final RegleCibleKdigo ALBUMINE =
            new RegleCibleKdigo("ALBUMINE", new BigDecimal("4.0"), null, "g/dL", "KDIGO-nutrition");

    private KdigoEvaluationPolicy() {
    }

    /**
     * Évalue une valeur face à une règle. {@code null} → {@link StatutCible#NON_EVALUABLE} (une
     * valeur absente n'est pas une valeur hors cible : on ne peut simplement pas se prononcer).
     */
    public static EvaluationCible evaluer(RegleCibleKdigo regle, BigDecimal valeur) {
        StatutCible statut;
        if (valeur == null) {
            statut = StatutCible.NON_EVALUABLE;
        } else if (regle.borneMin() != null && valeur.compareTo(regle.borneMin()) < 0) {
            statut = StatutCible.SOUS_CIBLE;
        } else if (regle.borneMax() != null && valeur.compareTo(regle.borneMax()) > 0) {
            statut = StatutCible.AU_DESSUS_CIBLE;
        } else {
            statut = StatutCible.DANS_CIBLE;
        }
        return new EvaluationCible(regle.code(), valeur, regle.unite(), statut, regle.borneMin(), regle.borneMax(),
                regle.reference());
    }

    public static EvaluationCible evaluerHemoglobine(BigDecimal hbGDl) {
        return evaluer(HEMOGLOBINE, hbGDl);
    }

    public static EvaluationCible evaluerFerritine(BigDecimal ferritineNgMl) {
        return evaluer(FERRITINE, ferritineNgMl);
    }

    public static EvaluationCible evaluerCoefficientSaturationTransferrine(BigDecimal cstPct) {
        return evaluer(COEFFICIENT_SATURATION_TRANSFERRINE, cstPct);
    }

    public static EvaluationCible evaluerKtV(BigDecimal ktV) {
        return evaluer(KT_V, ktV);
    }

    public static EvaluationCible evaluerPhosphore(BigDecimal phosphoreMgDl) {
        return evaluer(PHOSPHORE, phosphoreMgDl);
    }

    public static EvaluationCible evaluerCalcium(BigDecimal calciumMgDl) {
        return evaluer(CALCIUM_CORRIGE, calciumMgDl);
    }

    public static EvaluationCible evaluerPth(BigDecimal pthPgMl) {
        return evaluer(PTH, pthPgMl);
    }

    public static EvaluationCible evaluerAlbumine(BigDecimal albumineGDl) {
        return evaluer(ALBUMINE, albumineGDl);
    }
}
