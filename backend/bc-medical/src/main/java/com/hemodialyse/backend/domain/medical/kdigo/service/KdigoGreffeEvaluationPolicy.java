package com.hemodialyse.backend.domain.medical.kdigo.service;

import com.hemodialyse.backend.domain.medical.kdigo.valueobject.AlerteSerologieKdigo;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.EvaluationEgfr;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.EvaluationRisqueKdigo;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.NiveauRisqueKdigo;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.StadeCkd;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Classe pure du domaine (AGENTS.md §3) — évalue les critères du référentiel KDIGO 2020
 * ("Evaluation and Management of Candidates for Kidney Transplantation") applicables au bilan
 * pré-greffe rénale : risque immunologique (PRA), fonction rénale résiduelle (DFG estimé) et
 * points d'attention sérologiques/infectieux.
 * <p>
 * Volontairement une fonction pure et sans état, dans l'esprit de {@link KdigoEvaluationPolicy} :
 * aucun résultat n'est persisté, tout est recalculé à la demande à partir des dernières valeurs
 * connues. Ces évaluations sont des <strong>aides à la décision</strong>, pas un verdict
 * d'éligibilité : la décision finale reste toujours à la RCP / au médecin.
 */
public final class KdigoGreffeEvaluationPolicy {

    private static final BigDecimal SEUIL_PRA_FAIBLE = new BigDecimal("20");
    private static final BigDecimal SEUIL_PRA_ELEVE = new BigDecimal("80");

    /**
     * Marqueurs dont un résultat {@code POSITIF} appelle une prise en charge spécifique avant
     * greffe (par opposition, par exemple, à une IgG anti-CMV/EBV positive, qui ne traduit qu'une
     * exposition ancienne et ne justifie pas d'alerte).
     */
    private static final Map<String, String> MESSAGES_SEROLOGIE_POSITIVE = Map.of(
            "VIH_AC", "Infection VIH connue : ne contre-indique pas la greffe mais impose un protocole dédié et un avis infectiologue (KDIGO 2020).",
            "AG_HBS", "Réplication virale B à évaluer (charge virale, avis hépatologue) avant la greffe (KDIGO 2020).",
            "AC_VHC", "Sérologie VHC positive : bilan de réplication (ARN VHC) et avis hépatologue avant la greffe (KDIGO 2020).",
            "ARN_VHC", "Réplication virale C active : traitement antiviral à discuter avant ou après la greffe (KDIGO 2020).",
            "TPHA", "Syphilis à traiter avant la greffe (KDIGO 2020)."
    );

    private KdigoGreffeEvaluationPolicy() {
    }

    /**
     * Risque immunologique à partir du PRA (Panel Reactive Antibody) classe I et/ou II — le pire
     * des deux valeurs disponibles est retenu. Seuils usuels de stratification (faible &lt; 20 %,
     * intermédiaire 20-80 %, élevé &gt; 80 %) : KDIGO 2020 ne fixe pas de seuil unique impératif,
     * ces bornes reflètent la pratique courante et servent à orienter la stratégie de matching
     * HLA / désensibilisation, pas à exclure un candidat.
     */
    public static EvaluationRisqueKdigo evaluerRisqueImmunologique(BigDecimal praClasseI, BigDecimal praClasseII) {
        BigDecimal pire = maxIgnorantNull(praClasseI, praClasseII);
        NiveauRisqueKdigo niveau;
        if (pire == null) {
            niveau = NiveauRisqueKdigo.NON_EVALUABLE;
        } else if (pire.compareTo(SEUIL_PRA_FAIBLE) < 0) {
            niveau = NiveauRisqueKdigo.FAIBLE;
        } else if (pire.compareTo(SEUIL_PRA_ELEVE) > 0) {
            niveau = NiveauRisqueKdigo.ELEVE;
        } else {
            niveau = NiveauRisqueKdigo.INTERMEDIAIRE;
        }
        return new EvaluationRisqueKdigo("PRA", pire, "%", niveau, "KDIGO-2020-candidat-greffe");
    }

    /**
     * Débit de filtration glomérulaire estimé (formule CKD-EPI 2021, sans coefficient ethnique) et
     * stade KDIGO correspondant. Peu discriminant chez un patient déjà en dialyse chronique (déjà
     * de facto au stade G5) : affiché à titre informatif, pas comme critère d'orientation.
     */
    public static EvaluationEgfr evaluerFonctionRenale(BigDecimal creatinineMgDl, Integer age, String sexe) {
        if (creatinineMgDl == null || creatinineMgDl.signum() <= 0 || age == null || sexe == null) {
            return new EvaluationEgfr(null, StadeCkd.NON_EVALUABLE, "KDIGO-CKD-EPI-2021");
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
        return new EvaluationEgfr(egfrArrondi, stadeCkdPour(egfrArrondi), "KDIGO-CKD-EPI-2021");
    }

    private static StadeCkd stadeCkdPour(BigDecimal egfr) {
        if (egfr.compareTo(new BigDecimal("90")) >= 0) return StadeCkd.G1;
        if (egfr.compareTo(new BigDecimal("60")) >= 0) return StadeCkd.G2;
        if (egfr.compareTo(new BigDecimal("45")) >= 0) return StadeCkd.G3A;
        if (egfr.compareTo(new BigDecimal("30")) >= 0) return StadeCkd.G3B;
        if (egfr.compareTo(new BigDecimal("15")) >= 0) return StadeCkd.G4;
        return StadeCkd.G5;
    }

    /**
     * Signale les résultats sérologiques positifs qui appellent une prise en charge spécifique
     * avant la greffe (cf. {@link #MESSAGES_SEROLOGIE_POSITIVE}). {@code marqueurResultats}
     * associe le nom du marqueur (ex. {@code "VIH_AC"}) à son résultat (ex. {@code "POSITIF"}) —
     * volontairement des chaînes plutôt que les enums de l'agrégat {@code Serologie}, pour garder
     * cette classe indépendante du cycle de vie de cet agrégat.
     */
    public static List<AlerteSerologieKdigo> evaluerSerologies(Map<String, String> marqueurResultats) {
        List<AlerteSerologieKdigo> alertes = new ArrayList<>();
        if (marqueurResultats == null) {
            return alertes;
        }
        for (Map.Entry<String, String> entry : marqueurResultats.entrySet()) {
            String marqueur = entry.getKey();
            String resultat = entry.getValue();
            String message = MESSAGES_SEROLOGIE_POSITIVE.get(marqueur);
            if (message != null && "POSITIF".equals(resultat)) {
                alertes.add(new AlerteSerologieKdigo(marqueur, resultat, message, "KDIGO-2020-candidat-greffe"));
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
