package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionVue;
import com.hemodialyse.backend.contexts.gmao.application.intervention.LectureInterventions;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.LectureDesComptages;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.LectureDesCouts;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.LecturePlans;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.PlanMaintenanceVue;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.AnalyseDeDecision;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CalculDIndisponibilite;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.StatutPlan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;

/**
 * Fiche d'un équipement du centre (RG-GMA-061) sur les douze derniers mois. Équipement inconnu ou d'un autre centre :
 * {@link EquipementIntrouvable} (400) ; supprimé logiquement : servi (KB-GMAO-34). Interventions, coûts et plans sont lus par
 * équipement, sans filtre de centre (KB-GMAO-71, reproduit).
 */
public final class ConsulterLaFiche {

    private final LectureEquipements equipements;
    private final HistoriqueDesStatuts historique;
    private final LectureDesComptages comptages;
    private final LectureDesCouts couts;
    private final LectureInterventions interventions;
    private final LecturePlans plans;
    private final AnalyseDeDecision analyse;
    private final Horloge horloge;

    public ConsulterLaFiche(LectureEquipements equipements, HistoriqueDesStatuts historique, LectureDesComptages comptages,
                            LectureDesCouts couts, LectureInterventions interventions, LecturePlans plans,
                            AnalyseDeDecision analyse, Horloge horloge) {
        this.equipements = equipements;
        this.historique = historique;
        this.comptages = comptages;
        this.couts = couts;
        this.interventions = interventions;
        this.plans = plans;
        this.analyse = analyse;
        this.horloge = horloge;
    }

    public FicheDEquipement executer(EquipementId id, CentreId centre) {
        EquipementVue equipement = equipements.parId(id).filter(e -> e.centreId().equals(centre.valeur()))
                .orElseThrow(EquipementIntrouvable::new);
        Instant au = horloge.maintenant();
        Instant du = au.atOffset(ZoneOffset.UTC).minusMonths(12).toInstant();

        double heuresIndisponibles = CalculDIndisponibilite
                .calculer(historique.deLEquipement(id), equipement.statut(), du, au).toMinutes() / 60.0;
        BigDecimal coutDeLaPeriode = couts.deLEquipement(id, du, au);
        BigDecimal coutCumule = couts.cumuleDeLEquipement(id);
        InterventionVue derniere = interventions.derniereDeLEquipement(id).map(i -> InterventionVue.depuis(i, au)).orElse(null);
        PlanMaintenanceVue prochainPlan = plans.deEquipement(id).stream()
                .filter(p -> p.statut() == StatutPlan.ACTIF && p.prochaineDatePrevue() != null)
                .min(Comparator.comparing(PlanMaintenanceVue::prochaineDatePrevue)).orElse(null);

        return new FicheDEquipement(equipement, comptages.interventionsDeLEquipement(id), heuresIndisponibles,
                coutDeLaPeriode, coutCumule,
                analyse.analyser(equipement.statut(), equipement.prixAcquisition(), coutCumule, coutDeLaPeriode,
                        heuresIndisponibles),
                analyse.seuil(), derniere, prochainPlan);
    }
}
