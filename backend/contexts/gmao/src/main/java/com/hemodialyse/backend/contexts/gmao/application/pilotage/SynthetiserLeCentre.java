package com.hemodialyse.backend.contexts.gmao.application.pilotage;

import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementVue;
import com.hemodialyse.backend.contexts.gmao.application.equipement.HistoriqueDesStatuts;
import com.hemodialyse.backend.contexts.gmao.application.equipement.LectureEquipements;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.AnalyseDeDecision;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CalculDIndisponibilite;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.ChangementDeStatut;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.SyntheseGmaoDuCentre;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Synthèse GMAO d'un centre sur une période, publiée au pilotage de la direction. L'historique de tous les équipements est lu
 * d'un coup (le legacy en lisait un par équipement). Les coûts sont ceux des interventions démarrées sur la période.
 */
public final class SynthetiserLeCentre implements SyntheseGmaoDuCentre {

    private final LectureEquipements parc;
    private final HistoriqueDesStatuts historique;
    private final LectureDesComptages comptages;
    private final LectureDesCouts couts;
    private final AnalyseDeDecision analyse;

    public SynthetiserLeCentre(LectureEquipements parc, HistoriqueDesStatuts historique, LectureDesComptages comptages,
                               LectureDesCouts couts, AnalyseDeDecision analyse) {
        this.parc = parc;
        this.historique = historique;
        this.comptages = comptages;
        this.couts = couts;
        this.analyse = analyse;
    }

    @Override
    public Synthese duCentre(UUID centreId, Instant du, Instant au) {
        CentreId centre = new CentreId(centreId);
        Map<EquipementId, List<ChangementDeStatut>> historiques = historique.desEquipementsDuCentre(centre);
        Map<EquipementId, BigDecimal> coutsDeLaPeriode = couts.parEquipement(centre, du, au);
        Map<EquipementId, BigDecimal> coutsCumules = couts.cumuleParEquipement(centre);

        double heuresTotales = 0;
        List<Equipement> equipements = new ArrayList<>();
        for (EquipementVue e : parc.parcDuCentre(centre)) {
            EquipementId id = new EquipementId(e.id());
            double heures = CalculDIndisponibilite.calculer(historiques.getOrDefault(id, List.of()), e.statut(), du, au)
                    .toMinutes() / 60.0;
            heuresTotales += heures;
            BigDecimal coutDeLaPeriode = coutsDeLaPeriode.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal coutCumule = coutsCumules.getOrDefault(id, BigDecimal.ZERO);
            AnalyseDeDecision.Analyse a = analyse.analyser(e.statut(), e.prixAcquisition(), coutCumule, coutDeLaPeriode, heures);
            equipements.add(new Equipement(e.id(), e.code(), e.designation(), e.statut().name(), coutDeLaPeriode, coutCumule,
                    e.prixAcquisition(), a.ratioMaintenance(), heures, a.reformeRecommandee()));
        }

        return new Synthese(comptages.equipements(centre),
                comptages.equipementsAuStatut(centre, StatutEquipement.HORS_SERVICE),
                comptages.equipementsAuStatut(centre, StatutEquipement.EN_MAINTENANCE),
                comptages.equipementsAuStatut(centre, StatutEquipement.REFORME),
                comptages.interventionsAuStatut(centre, StatutIntervention.EN_COURS),
                couts.duCentre(centre, du, au), heuresTotales, equipements);
    }
}
