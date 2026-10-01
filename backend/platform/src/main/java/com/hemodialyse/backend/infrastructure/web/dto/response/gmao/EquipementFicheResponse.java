package com.hemodialyse.backend.infrastructure.web.dto.response.gmao;

import com.hemodialyse.backend.domain.gmao.service.AideDecisionMaintenance.AnalyseCout;

import java.math.BigDecimal;

/**
 * Fiche détaillée d'un équipement GMAO — aide à la décision (coût de maintenance, indisponibilité,
 * coût de possession, signal de réforme) consultable depuis le parc du centre.
 */
public record EquipementFicheResponse(
        EquipementResponse equipement,
        long nbInterventions,
        double indisponibiliteHeures,
        BigDecimal coutMaintenancePeriode,
        BigDecimal coutMaintenanceCumule,
        BigDecimal coutPossession,
        BigDecimal ratioMaintenance,
        BigDecimal coutParHeureIndisponibilite,
        boolean reformeRecommandee,
        BigDecimal seuilReforme,
        InterventionResponse derniereIntervention,
        PlanMaintenanceResponse prochainePlanMaintenance
) {
    public static EquipementFicheResponse of(
            EquipementResponse equipement, long nbInterventions, double indisponibiliteHeures,
            BigDecimal coutMaintenancePeriode, BigDecimal coutMaintenanceCumule, AnalyseCout analyse,
            BigDecimal seuilReforme, InterventionResponse derniereIntervention,
            PlanMaintenanceResponse prochainePlanMaintenance) {
        return new EquipementFicheResponse(equipement, nbInterventions, indisponibiliteHeures,
                coutMaintenancePeriode, coutMaintenanceCumule, analyse.coutPossession(),
                analyse.ratioMaintenance(), analyse.coutParHeureIndisponibilite(), analyse.reformeRecommandee(),
                seuilReforme, derniereIntervention, prochainePlanMaintenance);
    }
}
