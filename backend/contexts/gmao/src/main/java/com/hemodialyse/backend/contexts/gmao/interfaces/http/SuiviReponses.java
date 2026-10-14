package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import com.hemodialyse.backend.contexts.gmao.application.equipement.DisponibilitePourUnPatient;
import com.hemodialyse.backend.contexts.gmao.application.equipement.FicheDEquipement;
import com.hemodialyse.backend.contexts.gmao.application.intervention.EvenementVu;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.StatistiquesGmao;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.EvenementDeChronologie;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.IndicateursDIntervention.Indicateurs;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Réponses JSON de la consultation et du pilotage : mêmes champs, même ordre et mêmes types sérialisés que le legacy
 * (instants en {@link OffsetDateTime} UTC ; échelles des nombres telles que les calculs les rendent, KB-GMAO-76).
 */
public final class SuiviReponses {

    private SuiviReponses() {
    }

    /** Indicateurs de réactivité d'une intervention : durées en minutes, part d'indisponibilité en pourcentage. */
    public record IndicateursReponse(long delaiPriseEnChargeMinutes, long dureeInterventionMinutes,
                              long indisponibiliteInterventionMinutes, long indisponibiliteEquipement12MoisMinutes,
                              Double partIndisponibilite12MoisPct) {

        static IndicateursReponse depuis(Indicateurs i) {
            return new IndicateursReponse(i.delaiPriseEnChargeMinutes(), i.dureeMinutes(),
                    i.indisponibiliteInterventionMinutes(), i.indisponibiliteEquipement12MoisMinutes(),
                    i.partIndisponibilite12MoisPct());
        }
    }

    /** Entrée de la chronologie : événement, instant UTC, auteur (identifiant et nom). */
    public record EvenementReponse(EvenementDeChronologie.Type type, OffsetDateTime at, UUID parId, String parNom, String detail) {

        static EvenementReponse depuis(EvenementVu e) {
            return new EvenementReponse(e.type(), e.le() == null ? null : e.le().atOffset(ZoneOffset.UTC), e.par(), e.parNom(),
                    e.detail());
        }
    }

    /** Fiche d'un équipement : aide à la décision (coût, indisponibilité, coût de possession, signal de réforme). */
    public record FicheReponse(EquipementReponse equipement, long nbInterventions, double indisponibiliteHeures,
                        BigDecimal coutMaintenancePeriode, BigDecimal coutMaintenanceCumule, BigDecimal coutPossession,
                        BigDecimal ratioMaintenance, BigDecimal coutParHeureIndisponibilite, boolean reformeRecommandee,
                        BigDecimal seuilReforme, InterventionReponse derniereIntervention,
                        PlanMaintenanceReponse prochainePlanMaintenance) {

        static FicheReponse depuis(FicheDEquipement f) {
            return new FicheReponse(EquipementReponse.depuis(f.equipement()), f.nombreDInterventions(),
                    f.indisponibiliteHeures(), f.coutDeLaPeriode(), f.coutCumule(), f.analyse().coutPossession(),
                    f.analyse().ratioMaintenance(), f.analyse().coutParHeureIndisponibilite(),
                    f.analyse().reformeRecommandee(), f.seuilDeReforme(),
                    f.derniereIntervention() == null ? null : InterventionReponse.depuis(f.derniereIntervention()),
                    f.prochainPlan() == null ? null : PlanMaintenanceReponse.depuis(f.prochainPlan()));
        }
    }

    /** Disponibilité d'un équipement avant d'y affecter un patient : avertissement non bloquant. */
    public record DisponibilitePatientReponse(boolean disponible, StatutEquipement statut) {

        static DisponibilitePatientReponse depuis(DisponibilitePourUnPatient d) {
            return new DisponibilitePatientReponse(d.disponible(), d.statut());
        }
    }

    /** Statistiques du tableau de bord GMAO du centre. */
    public record StatistiquesReponse(long totalEquipements, long equipementsEnService, long equipementsEnMaintenance,
                               long equipementsHorsService, long totalInterventions, long interventionsEnCours,
                               long interventionsTerminees, long plansMaintenanceActifs, long plansMaintenanceEnRetard,
                               long interventionsARelancer) {

        static StatistiquesReponse depuis(StatistiquesGmao s) {
            return new StatistiquesReponse(s.totalEquipements(), s.equipementsEnService(), s.equipementsEnMaintenance(),
                    s.equipementsHorsService(), s.totalInterventions(), s.interventionsEnCours(), s.interventionsTerminees(),
                    s.plansMaintenanceActifs(), s.plansMaintenanceEnRetard(), s.interventionsARelancer());
        }
    }
}
