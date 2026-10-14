package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionVue;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.PlanMaintenanceVue;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.AnalyseDeDecision;

import java.math.BigDecimal;

/**
 * Fiche d'un équipement sur les douze derniers mois (RG-GMA-061) : coûts de maintenance de la période et cumulé,
 * indisponibilité en heures, analyse de décision et seuil de réforme, dernière intervention et prochain plan actif.
 */
public record FicheDEquipement(EquipementVue equipement, long nombreDInterventions, double indisponibiliteHeures,
                               BigDecimal coutDeLaPeriode, BigDecimal coutCumule, AnalyseDeDecision.Analyse analyse,
                               BigDecimal seuilDeReforme, InterventionVue derniereIntervention,
                               PlanMaintenanceVue prochainPlan) {
}
