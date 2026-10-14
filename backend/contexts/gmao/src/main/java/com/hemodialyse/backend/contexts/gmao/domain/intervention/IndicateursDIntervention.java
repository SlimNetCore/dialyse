package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.equipement.CalculDIndisponibilite;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.ChangementDeStatut;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Indicateurs de réactivité d'une intervention (RG-GMA-031) : délai de prise en charge (de la création au début), durée
 * (début à fin, ou à maintenant si elle est en cours), indisponibilité de l'équipement pendant l'intervention et sur les douze
 * derniers mois, part de l'une dans l'autre plafonnée à 100 %. Les durées sont tronquées à la minute. Service de domaine pur.
 */
public final class IndicateursDIntervention {

    private IndicateursDIntervention() {
    }

    public static Indicateurs calculer(Intervention intervention, List<ChangementDeStatut> historique,
                                       StatutEquipement statutCourant, Instant maintenant) {
        Instant creation = intervention.creation().le();
        long delai = 0;
        if (creation != null && intervention.debut().isAfter(creation)) {
            delai = Duration.between(creation, intervention.debut()).toMinutes();
        }

        Instant fin = intervention.fin() != null ? intervention.fin() : maintenant;
        long duree = 0;
        long indisponibilitePendant = 0;
        if (fin.isAfter(intervention.debut())) {
            duree = Duration.between(intervention.debut(), fin).toMinutes();
            indisponibilitePendant = CalculDIndisponibilite.calculer(historique, statutCourant, intervention.debut(), fin)
                    .toMinutes();
        }

        Instant ilYADouzeMois = maintenant.atOffset(ZoneOffset.UTC).minusMonths(12).toInstant();
        long indisponibiliteSurDouzeMois = CalculDIndisponibilite.calculer(historique, statutCourant, ilYADouzeMois, maintenant)
                .toMinutes();
        Double part = indisponibiliteSurDouzeMois > 0
                ? Math.min(100.0, indisponibilitePendant * 100.0 / indisponibiliteSurDouzeMois)
                : null;
        return new Indicateurs(delai, duree, indisponibilitePendant, indisponibiliteSurDouzeMois, part);
    }

    /** @param partIndisponibilite12MoisPct de 0 à 100 ; nulle si l'équipement n'a connu aucune indisponibilité en douze mois */
    public record Indicateurs(long delaiPriseEnChargeMinutes, long dureeMinutes, long indisponibiliteInterventionMinutes,
                              long indisponibiliteEquipement12MoisMinutes, Double partIndisponibilite12MoisPct) {
    }
}
