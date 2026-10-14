package com.hemodialyse.backend.contexts.gmao.application.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenance;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceRepository;

/** Suspend un plan de maintenance actif (legacy : désactiver). */
public final class SuspendrePlan {

    private final PlanMaintenanceRepository plans;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final UniteDeTravail uniteDeTravail;

    public SuspendrePlan(PlanMaintenanceRepository plans, PublicateurDEvenements publicateur, Horloge horloge,
                         UniteDeTravail uniteDeTravail) {
        this.plans = plans;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.uniteDeTravail = uniteDeTravail;
    }

    public PlanMaintenanceVue executer(CentreId centre, PlanMaintenanceId id, UtilisateurId par) {
        return uniteDeTravail.executer(() -> {
            PlanMaintenance plan = plans.trouverParId(id).filter(p -> p.centreId().equals(centre)).orElseThrow(PlanIntrouvable::new);
            plan.suspendre(par, horloge.maintenant());
            plans.enregistrer(plan);
            publicateur.publier(plan.tirerEvenements());
            return PlanMaintenanceVue.depuis(plan);
        });
    }
}
