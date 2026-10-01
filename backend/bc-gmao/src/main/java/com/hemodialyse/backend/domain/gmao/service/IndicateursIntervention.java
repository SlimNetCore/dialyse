package com.hemodialyse.backend.domain.gmao.service;

import com.hemodialyse.backend.domain.gmao.model.EquipementStatutHistorique;
import com.hemodialyse.backend.domain.gmao.model.Intervention;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service de domaine pur : indicateurs de suivi d'une intervention (réactivité du prestataire).
 * <ul>
 *   <li>délai de prise en charge : de la création de la fiche au début de l'intervention ;</li>
 *   <li>durée de l'intervention (début → fin, ou → maintenant si en cours) ;</li>
 *   <li>indisponibilité de l'équipement pendant l'intervention, comparée à son indisponibilité sur les
 *       12 derniers mois.</li>
 * </ul>
 */
public final class IndicateursIntervention {

    private IndicateursIntervention() {
    }

    public static Resultat calculer(
            Intervention intervention,
            List<EquipementStatutHistorique> historique,
            StatutEquipement statutCourant,
            OffsetDateTime maintenant) {

        long delaiMinutes = 0;
        if (intervention.getDateCreation() != null && intervention.getDateDebut() != null
                && intervention.getDateDebut().isAfter(intervention.getDateCreation())) {
            delaiMinutes = Duration.between(intervention.getDateCreation(), intervention.getDateDebut()).toMinutes();
        }

        OffsetDateTime fin = intervention.getDateFin() != null ? intervention.getDateFin() : maintenant;
        long dureeMinutes = 0;
        long indispoPendantMinutes = 0;
        if (intervention.getDateDebut() != null && fin.isAfter(intervention.getDateDebut())) {
            dureeMinutes = Duration.between(intervention.getDateDebut(), fin).toMinutes();
            indispoPendantMinutes = IndisponibiliteCalculator.calculer(
                    historique, statutCourant, intervention.getDateDebut(), fin).toMinutes();
        }

        long indispo12MoisMinutes = IndisponibiliteCalculator.calculer(
                historique, statutCourant, maintenant.minusMonths(12), maintenant).toMinutes();

        Double part = indispo12MoisMinutes > 0
                ? Math.min(100.0, indispoPendantMinutes * 100.0 / indispo12MoisMinutes)
                : null;

        return new Resultat(delaiMinutes, dureeMinutes, indispoPendantMinutes, indispo12MoisMinutes, part);
    }

    /**
     * @param partIndisponibilite12MoisPct part (0-100) de l'indisponibilité des 12 derniers mois due à cette
     *                                     intervention ; null si l'équipement n'a eu aucune indisponibilité
     */
    public record Resultat(
            long delaiPriseEnChargeMinutes,
            long dureeInterventionMinutes,
            long indisponibiliteInterventionMinutes,
            long indisponibiliteEquipement12MoisMinutes,
            Double partIndisponibilite12MoisPct
    ) {
    }
}
