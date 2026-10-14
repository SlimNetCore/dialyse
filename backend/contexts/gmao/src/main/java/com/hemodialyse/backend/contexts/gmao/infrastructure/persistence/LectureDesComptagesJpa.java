package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.application.pilotage.LectureDesComptages;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Adapter JPA du read model {@link LectureDesComptages} : des comptages, jamais de listes chargées. */
public class LectureDesComptagesJpa implements LectureDesComptages {

    private final EquipementSpringData equipements;
    private final InterventionSpringData interventions;
    private final PlanMaintenanceSpringData plans;

    public LectureDesComptagesJpa(EquipementSpringData equipements, InterventionSpringData interventions,
                                  PlanMaintenanceSpringData plans) {
        this.equipements = equipements;
        this.interventions = interventions;
        this.plans = plans;
    }

    @Override
    public long equipements(CentreId centre) {
        return equipements.compterDuCentre(centre.valeur());
    }

    @Override
    public long equipementsAuStatut(CentreId centre, StatutEquipement statut) {
        return equipements.compterDuCentreAuStatut(centre.valeur(), statut.name());
    }

    @Override
    public long interventions(CentreId centre) {
        return interventions.compterDuCentre(centre.valeur());
    }

    @Override
    public long interventionsAuStatut(CentreId centre, StatutIntervention statut) {
        return interventions.compterDuCentreAuStatut(centre.valeur(), statut.name());
    }

    @Override
    public long interventionsDeLEquipement(EquipementId equipement) {
        return interventions.compterDeLEquipement(equipement.valeur());
    }

    @Override
    public Map<UUID, Long> generateursInstallesAvant(Collection<UUID> centres, Instant avant) {
        Map<UUID, Long> parCentre = new HashMap<>();
        for (Object[] ligne : equipements.compterGenerateursInstalles(centres, avant)) {
            parCentre.put((UUID) ligne[0], ((Number) ligne[1]).longValue());
        }
        return parCentre;
    }

    @Override
    public long plansActifs(CentreId centre) {
        return plans.compterActifs(centre.valeur());
    }

    @Override
    public long plansEnRetard(CentreId centre, Instant maintenant) {
        return plans.compterEnRetard(centre.valeur(), maintenant);
    }

    @Override
    public long interventionsARelancer(CentreId centre, Instant maintenant) {
        return interventions.compterARelancer(centre.valeur(), maintenant);
    }
}
