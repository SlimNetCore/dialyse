package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenance;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceRepository;

import java.util.Optional;

/**
 * Adapter JPA du port {@link PlanMaintenanceRepository}. Le plan chargé reste géré dans la transaction du use case :
 * l'enregistrement met à jour cette instance.
 */
public class PlanMaintenanceRepositoryJpa implements PlanMaintenanceRepository {

    private final PlanMaintenanceSpringData donnees;

    public PlanMaintenanceRepositoryJpa(PlanMaintenanceSpringData donnees) {
        this.donnees = donnees;
    }

    @Override
    public void enregistrer(PlanMaintenance plan) {
        PlanMaintenanceJpa entite = donnees.findById(plan.id().valeur())
                .orElseGet(() -> new PlanMaintenanceJpa(plan.id().valeur()));
        PlanMaintenanceMapper.recopier(plan, entite);
        donnees.saveAndFlush(entite);
    }

    @Override
    public Optional<PlanMaintenance> trouverParId(PlanMaintenanceId id) {
        return donnees.findById(id.valeur()).map(PlanMaintenanceMapper::versDomaine);
    }
}
