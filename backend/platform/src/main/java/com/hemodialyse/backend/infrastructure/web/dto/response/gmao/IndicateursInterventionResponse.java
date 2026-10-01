package com.hemodialyse.backend.infrastructure.web.dto.response.gmao;

import com.hemodialyse.backend.domain.gmao.service.IndicateursIntervention.Resultat;

/**
 * Indicateurs de réactivité d'une intervention (durées en minutes ; la part d'indisponibilité est en %).
 */
public record IndicateursInterventionResponse(
        long delaiPriseEnChargeMinutes,
        long dureeInterventionMinutes,
        long indisponibiliteInterventionMinutes,
        long indisponibiliteEquipement12MoisMinutes,
        Double partIndisponibilite12MoisPct
) {
    public IndicateursInterventionResponse(Resultat r) {
        this(r.delaiPriseEnChargeMinutes(), r.dureeInterventionMinutes(), r.indisponibiliteInterventionMinutes(),
                r.indisponibiliteEquipement12MoisMinutes(), r.partIndisponibilite12MoisPct());
    }
}
