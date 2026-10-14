package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.LecturePlans;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.PlanMaintenanceVue;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenance;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceRepository;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.StatutPlan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Double in-memory des ports de persistance et de lecture des plans. Fidèle au contrat partagé
 * (PlanMaintenanceContrat) : les mêmes tests s'exécutent contre l'adapter JPA.
 */
public class PlanMaintenanceEnMemoire implements PlanMaintenanceRepository, LecturePlans {

    private final Map<PlanMaintenanceId, PlanMaintenance> plans = new LinkedHashMap<>();
    private final Set<PlanMaintenanceId> supprimes = new LinkedHashSet<>();

    @Override
    public void enregistrer(PlanMaintenance plan) {
        // KB-GMAO-17 reproduit : enregistrer ne conserve pas la suppression logique (le plan réapparaît)
        supprimes.remove(plan.id());
        plans.put(plan.id(), copie(plan));
    }

    @Override
    public Optional<PlanMaintenance> trouverParId(PlanMaintenanceId id) {
        return Optional.ofNullable(plans.get(id)).map(PlanMaintenanceEnMemoire::copie);
    }

    /** Suppression logique posée hors du domaine (legacy, SQL) : aucune commande ne la produit. */
    public void supprimerLogiquement(PlanMaintenanceId id) {
        supprimes.add(id);
    }

    @Override
    public Optional<PlanMaintenanceVue> parId(PlanMaintenanceId id) {
        return Optional.ofNullable(plans.get(id)).map(PlanMaintenanceVue::depuis);
    }

    @Override
    public List<PlanMaintenanceVue> duCentre(CentreId centre) {
        return selection(p -> p.centreId().equals(centre));
    }

    @Override
    public List<PlanMaintenanceVue> actifsDuCentre(CentreId centre) {
        return selection(p -> p.centreId().equals(centre) && p.statut() == StatutPlan.ACTIF);
    }

    @Override
    public List<PlanMaintenanceVue> enRetardDuCentre(CentreId centre, Instant maintenant) {
        return selection(p -> p.centreId().equals(centre) && p.estEnRetard(maintenant));
    }

    @Override
    public List<PlanMaintenanceVue> deEquipement(EquipementId equipement) {
        return selection(p -> p.equipementId().equals(equipement));
    }

    private List<PlanMaintenanceVue> selection(java.util.function.Predicate<PlanMaintenance> filtre) {
        List<PlanMaintenanceVue> resultat = new ArrayList<>();
        plans.values().stream()
                .filter(p -> !supprimes.contains(p.id()))
                .filter(filtre)
                .map(PlanMaintenanceVue::depuis)
                .forEach(resultat::add);
        return resultat;
    }

    private static PlanMaintenance copie(PlanMaintenance p) {
        return PlanMaintenance.reconstituer(p.id(), p.equipementId(), p.centreId(), p.designation(), p.description(),
                p.frequence(), p.statut(), p.prochaineDatePrevue(), p.derniereExecution(), p.nombreExecutions(),
                p.tachesAEffectuer(), p.creeLe(), p.modifieLe(), p.creePar(), p.modifiePar());
    }
}
