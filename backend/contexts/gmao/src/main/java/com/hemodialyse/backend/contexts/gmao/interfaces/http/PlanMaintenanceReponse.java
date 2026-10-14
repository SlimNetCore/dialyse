package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.PlanMaintenanceVue;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.FrequenceMaintenance;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.StatutPlan;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Réponse JSON d'un plan : mêmes champs, même ordre et mêmes types sérialisés que le legacy
 * ({@code PlanMaintenanceResponse}) ; les instants sont exposés en {@link OffsetDateTime} UTC pour un rendu identique.
 */
public record PlanMaintenanceReponse(
        UUID id,
        UUID equipementId,
        UUID centreId,
        String designation,
        String description,
        FrequenceMaintenance frequence,
        StatutPlan statut,
        OffsetDateTime prochaineDatePrevue,
        OffsetDateTime derniereDateExecution,
        Integer nombreExecutions,
        String tachesAEffectuer,
        OffsetDateTime dateCreation,
        OffsetDateTime dateModification,
        UUID creePar,
        UUID modifiePar) {

    static PlanMaintenanceReponse depuis(PlanMaintenanceVue vue) {
        return new PlanMaintenanceReponse(vue.id(), vue.equipementId(), vue.centreId(), vue.designation(),
                vue.description(), vue.frequence(), vue.statut(), utc(vue.prochaineDatePrevue()),
                utc(vue.derniereDateExecution()), vue.nombreExecutions(), vue.tachesAEffectuer(),
                utc(vue.dateCreation()), utc(vue.dateModification()), vue.creePar(), vue.modifiePar());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
