package com.hemodialyse.backend.contexts.gmao.application.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.application.EquipementExiste;
import com.hemodialyse.backend.contexts.gmao.application.GenerateurIdentifiants;
import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenance;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceRepository;

/** Établit un plan de maintenance préventive pour un équipement du centre. */
public final class EtablirPlan {

    private final PlanMaintenanceRepository plans;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final GenerateurIdentifiants identifiants;
    private final UniteDeTravail uniteDeTravail;
    private final EquipementExiste equipements;

    public EtablirPlan(PlanMaintenanceRepository plans, PublicateurDEvenements publicateur, Horloge horloge,
                       GenerateurIdentifiants identifiants, UniteDeTravail uniteDeTravail, EquipementExiste equipements) {
        this.equipements = equipements;
        this.plans = plans;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.identifiants = identifiants;
        this.uniteDeTravail = uniteDeTravail;
    }

    public PlanMaintenanceVue executer(EtablirPlanCommande commande) {
        return uniteDeTravail.executer(() -> {
            if (!equipements.existe(commande.equipementId())) {
                throw new EquipementInconnu(commande.equipementId());
            }
            PlanMaintenance plan = PlanMaintenance.etablir(
                    new PlanMaintenanceId(identifiants.nouvelIdentifiant()),
                    commande.equipementId(), commande.centreId(), commande.designation(), commande.description(),
                    commande.frequence(), commande.prochaineDatePrevue(), commande.tachesAEffectuer(), commande.par(),
                    horloge.maintenant());
            plans.enregistrer(plan);
            publicateur.publier(plan.tirerEvenements());
            return PlanMaintenanceVue.depuis(plan);
        });
    }
}
