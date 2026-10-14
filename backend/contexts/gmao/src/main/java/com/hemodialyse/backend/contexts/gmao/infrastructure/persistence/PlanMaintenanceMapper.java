package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.PlanMaintenanceVue;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.FrequenceMaintenance;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenance;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.StatutPlan;

/** Mapping explicite agrégat / vue <-> modèle de persistance. */
final class PlanMaintenanceMapper {

    private PlanMaintenanceMapper() {
    }

    static PlanMaintenance versDomaine(PlanMaintenanceJpa e) {
        return PlanMaintenance.reconstituer(
                new PlanMaintenanceId(e.getId()),
                new EquipementId(e.getEquipementId()),
                new CentreId(e.getCentreId()),
                e.getDesignation(),
                e.getDescription(),
                FrequenceMaintenance.valueOf(e.getFrequence()),
                StatutPlan.valueOf(e.getStatut()),
                e.getProchaineDatePrevue(),
                e.getDerniereDateExecution(),
                e.getNombreExecutions(),
                e.getTachesAEffectuer(),
                e.getDateCreation(),
                e.getDateModification(),
                new UtilisateurId(e.getCreePar()),
                e.getModifiePar() == null ? null : new UtilisateurId(e.getModifiePar()));
    }

    /** Recopie l'état de l'agrégat dans le modèle de persistance (neuf ou géré). */
    static void recopier(PlanMaintenance plan, PlanMaintenanceJpa e) {
        e.setEquipementId(plan.equipementId().valeur());
        e.setCentreId(plan.centreId().valeur());
        e.setDesignation(plan.designation());
        e.setDescription(plan.description());
        e.setFrequence(plan.frequence().name());
        e.setStatut(plan.statut().name());
        e.setProchaineDatePrevue(plan.prochaineDatePrevue());
        e.setDerniereDateExecution(plan.derniereExecution());
        e.setNombreExecutions(plan.nombreExecutions());
        e.setTachesAEffectuer(plan.tachesAEffectuer());
        e.setDateCreation(plan.creeLe());
        e.setDateModification(plan.modifieLe());
        e.setCreePar(plan.creePar().valeur());
        e.setModifiePar(plan.modifiePar() == null ? null : plan.modifiePar().valeur());
        // KB-GMAO-17 reproduit : la suppression logique n'est pas portée par l'agrégat ; réécrire le plan l'efface.
        e.setDeletedAt(null);
    }

    static PlanMaintenanceVue versVue(PlanMaintenanceJpa e) {
        return new PlanMaintenanceVue(
                e.getId(),
                e.getEquipementId(),
                e.getCentreId(),
                e.getDesignation(),
                e.getDescription(),
                FrequenceMaintenance.valueOf(e.getFrequence()),
                StatutPlan.valueOf(e.getStatut()),
                e.getProchaineDatePrevue(),
                e.getDerniereDateExecution(),
                e.getNombreExecutions(),
                e.getTachesAEffectuer(),
                e.getDateCreation(),
                e.getDateModification(),
                e.getCreePar(),
                e.getModifiePar());
    }
}
