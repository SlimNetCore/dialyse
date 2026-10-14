package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Aide à la décision sur un équipement (R30, RG-GMA-062 et 063) : coût de possession, ratio de maintenance, coût par heure
 * d'indisponibilité et réforme recommandée. Le seuil est le ratio « maintenance cumulée / prix d'acquisition » à partir duquel
 * on recommande d'étudier la réforme (0,60 par défaut, un seul pour toute l'application, KB-GMAO-05).
 * <p>
 * Le seuil se compare au ratio <b>exact</b> (KB-GMAO-72, corrigé) : l'arrondi à quatre décimales ne sert qu'à l'affichage.
 * Un équipement réformé ou désactivé n'est jamais recommandé ; « à réformer » l'est d'office.
 */
public record AnalyseDeDecision(BigDecimal seuil) {

    public AnalyseDeDecision {
        if (seuil == null || seuil.signum() <= 0) {
            throw new IllegalArgumentException("Le seuil de réforme doit être strictement positif");
        }
    }

    /**
     * @param prixAcquisition       inconnu : nul ; un prix nul ou négatif donne un ratio inconnu
     * @param coutCumule            maintenance cumulée depuis l'installation (nul : zéro)
     * @param coutPeriode           maintenance de la période d'analyse (nul : zéro)
     * @param heuresIndisponibilite indisponibilité de la même période, en heures
     */
    public Analyse analyser(StatutEquipement statut, BigDecimal prixAcquisition, BigDecimal coutCumule,
                            BigDecimal coutPeriode, double heuresIndisponibilite) {
        BigDecimal prix = prixAcquisition == null ? BigDecimal.ZERO : prixAcquisition;
        BigDecimal cumule = coutCumule == null ? BigDecimal.ZERO : coutCumule;
        BigDecimal periode = coutPeriode == null ? BigDecimal.ZERO : coutPeriode;

        boolean ratioConnu = prix.signum() > 0;
        BigDecimal ratio = ratioConnu ? cumule.divide(prix, 4, RoundingMode.HALF_UP) : null;
        BigDecimal coutParHeure = heuresIndisponibilite > 0
                ? periode.divide(BigDecimal.valueOf(heuresIndisponibilite), 2, RoundingMode.HALF_UP)
                : null;
        boolean actif = statut != StatutEquipement.REFORME && statut != StatutEquipement.DESACTIF;
        boolean seuilAtteint = ratioConnu && cumule.compareTo(prix.multiply(seuil)) >= 0;
        return new Analyse(prix.add(cumule), ratio, coutParHeure,
                actif && (statut == StatutEquipement.A_REFORMER || seuilAtteint));
    }

    /**
     * @param ratioMaintenance            arrondi à quatre décimales pour l'affichage ; nul si le prix d'acquisition est inconnu
     * @param coutParHeureIndisponibilite nul si l'équipement n'a pas été indisponible sur la période
     */
    public record Analyse(BigDecimal coutPossession, BigDecimal ratioMaintenance, BigDecimal coutParHeureIndisponibilite,
                          boolean reformeRecommandee) {
    }
}
