package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aide à la décision du candidat à la greffe rénale (KDIGO 2020, RG-MED-082) : risque immunologique (PRA), fonction rénale résiduelle
 * (CKD-EPI 2021) et points d'attention sérologiques. Fonctions pures, rien n'est persisté ; la décision d'éligibilité reste à la RCP et au
 * médecin. Repris MOT POUR MOT de {@code KdigoGreffeEvaluationPolicy} (bc-medical, retiré par DM9, ADR 0101) ; seuils NON validés
 * médicalement (question ouverte).
 */
public final class EvaluationKdigoDuCandidat {

    static final String REFERENCE_CANDIDAT = "KDIGO-2020-candidat-greffe";
    static final String REFERENCE_CKD_EPI = "KDIGO-CKD-EPI-2021";
    private static final BigDecimal SEUIL_PRA_FAIBLE = new BigDecimal("20");
    private static final BigDecimal SEUIL_PRA_ELEVE = new BigDecimal("80");

    /**
     * Marqueurs dont un résultat {@code POSITIF} appelle une prise en charge spécifique avant greffe (une IgG anti-CMV ou EBV positive ne
     * traduit qu'une exposition ancienne et ne justifie pas d'alerte).
     */
    private static final Map<String, String> MESSAGES_SEROLOGIE_POSITIVE = Map.of(
            "VIH_AC", "Infection VIH connue : ne contre-indique pas la greffe mais impose un protocole dédié et un avis infectiologue (KDIGO 2020).",
            "AG_HBS", "Réplication virale B à évaluer (charge virale, avis hépatologue) avant la greffe (KDIGO 2020).",
            "AC_VHC", "Sérologie VHC positive : bilan de réplication (ARN VHC) et avis hépatologue avant la greffe (KDIGO 2020).",
            "ARN_VHC", "Réplication virale C active : traitement antiviral à discuter avant ou après la greffe (KDIGO 2020).",
            "TPHA", "Syphilis à traiter avant la greffe (KDIGO 2020)."
    );

    private EvaluationKdigoDuCandidat() {
    }

    /** Le pire des deux PRA (classe I, II) : faible &lt; 20 %, intermédiaire 20-80 %, élevé &gt; 80 % ; non évaluable sans valeur. */
    public static RisqueImmunologique risqueImmunologique(BigDecimal praClasseI, BigDecimal praClasseII) {
        BigDecimal pire = maxIgnorantNull(praClasseI, praClasseII);
        NiveauDeRisqueKdigo niveau;
        if (pire == null) {
            niveau = NiveauDeRisqueKdigo.NON_EVALUABLE;
        } else if (pire.compareTo(SEUIL_PRA_FAIBLE) < 0) {
            niveau = NiveauDeRisqueKdigo.FAIBLE;
        } else if (pire.compareTo(SEUIL_PRA_ELEVE) > 0) {
            niveau = NiveauDeRisqueKdigo.ELEVE;
        } else {
            niveau = NiveauDeRisqueKdigo.INTERMEDIAIRE;
        }
        return new RisqueImmunologique("PRA", pire, "%", niveau, REFERENCE_CANDIDAT);
    }

    /** CKD-EPI 2021 (sans coefficient ethnique) ; non évaluable sans créatinine strictement positive, âge ou sexe. */
    public static FonctionRenale fonctionRenale(BigDecimal creatinineMgDl, Integer age, String sexe) {
        if (creatinineMgDl == null || creatinineMgDl.signum() <= 0 || age == null || sexe == null) {
            return new FonctionRenale(null, StadeDeMaladieRenale.NON_EVALUABLE, REFERENCE_CKD_EPI);
        }
        boolean femme = sexe.equalsIgnoreCase("F") || sexe.equalsIgnoreCase("FEMININ") || sexe.equalsIgnoreCase("FEMME");
        double scr = creatinineMgDl.doubleValue();
        double kappa = femme ? 0.7 : 0.9;
        double alpha = femme ? -0.241 : -0.302;
        double minScrKappa = Math.min(scr / kappa, 1.0);
        double maxScrKappa = Math.max(scr / kappa, 1.0);
        double egfr = 142
                * Math.pow(minScrKappa, alpha)
                * Math.pow(maxScrKappa, -1.200)
                * Math.pow(0.9938, age)
                * (femme ? 1.012 : 1.0);
        BigDecimal egfrArrondi = BigDecimal.valueOf(egfr).setScale(1, RoundingMode.HALF_UP);
        return new FonctionRenale(egfrArrondi, stadePour(egfrArrondi), REFERENCE_CKD_EPI);
    }

    private static StadeDeMaladieRenale stadePour(BigDecimal egfr) {
        if (egfr.compareTo(new BigDecimal("90")) >= 0) return StadeDeMaladieRenale.G1;
        if (egfr.compareTo(new BigDecimal("60")) >= 0) return StadeDeMaladieRenale.G2;
        if (egfr.compareTo(new BigDecimal("45")) >= 0) return StadeDeMaladieRenale.G3A;
        if (egfr.compareTo(new BigDecimal("30")) >= 0) return StadeDeMaladieRenale.G3B;
        if (egfr.compareTo(new BigDecimal("15")) >= 0) return StadeDeMaladieRenale.G4;
        return StadeDeMaladieRenale.G5;
    }

    /**
     * Alertes pour les marqueurs listés dont le résultat est {@code POSITIF} ; {@code marqueurResultats} associe le nom du marqueur à son
     * résultat (chaînes : indépendant du cycle de vie de la sérologie). L'ordre des alertes est celui de l'itération de la carte fournie.
     */
    public static List<AlerteSerologiqueKdigo> alertesSerologiques(Map<String, String> marqueurResultats) {
        List<AlerteSerologiqueKdigo> alertes = new ArrayList<>();
        if (marqueurResultats == null) {
            return alertes;
        }
        for (Map.Entry<String, String> entry : marqueurResultats.entrySet()) {
            String message = MESSAGES_SEROLOGIE_POSITIVE.get(entry.getKey());
            if (message != null && "POSITIF".equals(entry.getValue())) {
                alertes.add(new AlerteSerologiqueKdigo(entry.getKey(), entry.getValue(), message, REFERENCE_CANDIDAT));
            }
        }
        return alertes;
    }

    private static BigDecimal maxIgnorantNull(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.max(b);
    }
}
