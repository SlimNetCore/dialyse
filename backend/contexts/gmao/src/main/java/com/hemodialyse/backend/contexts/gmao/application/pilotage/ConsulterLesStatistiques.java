package com.hemodialyse.backend.contexts.gmao.application.pilotage;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;

import java.time.Instant;

/** Statistiques GMAO du centre, calculées par comptage côté base, jamais en chargeant les listes. */
public final class ConsulterLesStatistiques {

    private final LectureDesComptages comptages;
    private final Horloge horloge;

    public ConsulterLesStatistiques(LectureDesComptages comptages, Horloge horloge) {
        this.comptages = comptages;
        this.horloge = horloge;
    }

    public StatistiquesGmao executer(CentreId centre) {
        Instant maintenant = horloge.maintenant();
        return new StatistiquesGmao(
                comptages.equipements(centre),
                comptages.equipementsAuStatut(centre, StatutEquipement.EN_SERVICE),
                comptages.equipementsAuStatut(centre, StatutEquipement.EN_MAINTENANCE),
                comptages.equipementsAuStatut(centre, StatutEquipement.HORS_SERVICE),
                comptages.interventions(centre),
                comptages.interventionsAuStatut(centre, StatutIntervention.EN_COURS),
                comptages.interventionsAuStatut(centre, StatutIntervention.TERMINEE),
                comptages.plansActifs(centre),
                comptages.plansEnRetard(centre, maintenant),
                comptages.interventionsARelancer(centre, maintenant));
    }
}
