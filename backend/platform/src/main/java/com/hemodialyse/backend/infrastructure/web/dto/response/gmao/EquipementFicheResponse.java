package com.hemodialyse.backend.infrastructure.web.dto.response.gmao;

import java.math.BigDecimal;

/**
 * Fiche détaillée d'un équipement GMAO — aide à la décision (coût de maintenance, indisponibilité,
 * historique) consultable depuis le parc du centre.
 */
public record EquipementFicheResponse(
        EquipementResponse equipement,
        long nbInterventions,
        double indisponibiliteHeures,
        BigDecimal coutMaintenancePeriode,
        InterventionResponse derniereIntervention,
        PlanMaintenanceResponse prochainePlanMaintenance
) {
    public static EquipementFicheResponse of(
            EquipementResponse equipement, long nbInterventions, double indisponibiliteHeures,
            BigDecimal coutMaintenancePeriode, InterventionResponse derniereIntervention,
            PlanMaintenanceResponse prochainePlanMaintenance) {
        return new EquipementFicheResponse(equipement, nbInterventions, indisponibiliteHeures,
                coutMaintenancePeriode, derniereIntervention, prochainePlanMaintenance);
    }
}
