package com.hemodialyse.backend.contexts.gmao.application.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.FrequenceMaintenance;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenance;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.StatutPlan;

import java.time.Instant;
import java.util.UUID;

/** Vue de lecture d'un plan de maintenance (jamais l'agrégat) : identités en UUID, instants en UTC. */
public record PlanMaintenanceVue(
        UUID id,
        UUID equipementId,
        UUID centreId,
        String designation,
        String description,
        FrequenceMaintenance frequence,
        StatutPlan statut,
        Instant prochaineDatePrevue,
        Instant derniereDateExecution,
        int nombreExecutions,
        String tachesAEffectuer,
        Instant dateCreation,
        Instant dateModification,
        UUID creePar,
        UUID modifiePar) {

    public static PlanMaintenanceVue depuis(PlanMaintenance plan) {
        return new PlanMaintenanceVue(
                plan.id().valeur(),
                plan.equipementId().valeur(),
                plan.centreId().valeur(),
                plan.designation(),
                plan.description(),
                plan.frequence(),
                plan.statut(),
                plan.prochaineDatePrevue(),
                plan.derniereExecution(),
                plan.nombreExecutions(),
                plan.tachesAEffectuer(),
                plan.creeLe(),
                plan.modifieLe(),
                plan.creePar() == null ? null : plan.creePar().valeur(),
                plan.modifiePar() == null ? null : plan.modifiePar().valeur());
    }
}
