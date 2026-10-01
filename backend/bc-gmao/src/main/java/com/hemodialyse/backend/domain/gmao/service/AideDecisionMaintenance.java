package com.hemodialyse.backend.domain.gmao.service;

import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service de domaine pur (aide à la décision) : indicateurs de coût de possession d'un équipement et
 * signal de réforme. Le seuil de réforme est le ratio « maintenance cumulée / prix d'acquisition » à
 * partir duquel on recommande d'étudier la réforme de l'équipement.
 */
public record AideDecisionMaintenance(BigDecimal seuilRatio) {

    public static final BigDecimal SEUIL_RATIO_DEFAUT = new BigDecimal("0.60");

    public AideDecisionMaintenance {
        if (seuilRatio == null || seuilRatio.signum() <= 0) {
            throw new IllegalArgumentException("Le seuil de réforme doit être strictement positif");
        }
    }

    /**
     * @param statut               statut courant (un équipement déjà réformé/désactivé n'est plus recommandé à la réforme ; « À réformer » l'est d'office)
     * @param prixAcquisition      prix d'achat, éventuellement inconnu
     * @param coutCumule           maintenance cumulée depuis l'installation
     * @param coutPeriode          maintenance sur la période d'analyse (ex. 12 mois)
     * @param heuresIndispoPeriode indisponibilité sur la même période, en heures
     */
    public AnalyseCout analyser(StatutEquipement statut, BigDecimal prixAcquisition, BigDecimal coutCumule,
                                BigDecimal coutPeriode, double heuresIndispoPeriode) {
        BigDecimal prix = prixAcquisition == null ? BigDecimal.ZERO : prixAcquisition;
        BigDecimal cumule = coutCumule == null ? BigDecimal.ZERO : coutCumule;
        BigDecimal periode = coutPeriode == null ? BigDecimal.ZERO : coutPeriode;

        BigDecimal coutPossession = prix.add(cumule);
        BigDecimal ratio = prix.signum() > 0 ? cumule.divide(prix, 4, RoundingMode.HALF_UP) : null;
        BigDecimal coutParHeure = heuresIndispoPeriode > 0
                ? periode.divide(BigDecimal.valueOf(heuresIndispoPeriode), 2, RoundingMode.HALF_UP)
                : null;
        boolean actif = statut != StatutEquipement.REFORME && statut != StatutEquipement.DESACTIF;
        boolean reformeRecommandee = actif && (statut == StatutEquipement.A_REFORMER
                || (ratio != null && ratio.compareTo(seuilRatio) >= 0));

        return new AnalyseCout(coutPossession, ratio, coutParHeure, reformeRecommandee);
    }

    /**
     * @param ratioMaintenance            null si le prix d'acquisition est inconnu
     * @param coutParHeureIndisponibilite null si aucune indisponibilité sur la période
     */
    public record AnalyseCout(
            BigDecimal coutPossession,
            BigDecimal ratioMaintenance,
            BigDecimal coutParHeureIndisponibilite,
            boolean reformeRecommandee
    ) {
    }
}
