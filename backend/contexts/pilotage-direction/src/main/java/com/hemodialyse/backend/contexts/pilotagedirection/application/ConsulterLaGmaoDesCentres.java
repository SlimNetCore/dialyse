package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.application.GmaoDesCentres.SyntheseGmao;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.CentreDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlertesDeLaDirection.Alerte;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlertesDeLaDirection.Gravite;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Consulter l'aide à la décision GMAO de la direction (PL10b2, ADR 0152) : coût de maintenance, état du parc d'équipements et temps
 * d'indisponibilité, par centre et consolidés pour la société. Agrégats uniquement (aucune donnée patient nominative). La fin de période par
 * défaut est le jour d'Alger (KB-PL10b-4 corrigé), comme les autres lectures du Pilotage.
 */
public final class ConsulterLaGmaoDesCentres {

    private static final int TOP_EQUIPEMENTS = 5;

    private final SocietesSuivies societes;
    private final GmaoDesCentres gmao;
    private final HorlogeDuPilotage horloge;

    public ConsulterLaGmaoDesCentres(SocietesSuivies societes, GmaoDesCentres gmao, HorlogeDuPilotage horloge) {
        this.societes = societes;
        this.gmao = gmao;
        this.horloge = horloge;
    }

    public GmaoOverview execute(UUID societeId, LocalDate from, LocalDate to) {
        Periode periode = Periode.demandee(from, to, horloge.aujourdhui());
        List<CentreDeLaSociete> centresDeLaSociete = societes.societe(societeId).centres();
        OffsetDateTime du = periode.debut().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime au = periode.fin().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime generatedAt = horloge.maintenant();

        List<CentreGmao> centres = new ArrayList<>();
        List<Alerte> alertes = new ArrayList<>();
        for (CentreDeLaSociete c : centresDeLaSociete) {
            CentreGmao centreGmao = pourLeCentre(c.id(), c.nom(), du, au);
            centres.add(centreGmao);
            alertes.addAll(alertes(c.id(), c.nom(), centreGmao));
        }
        return new GmaoOverview(societeId, periode.debut(), periode.fin(), generatedAt, centres, totaux(centres), alertes);
    }

    private CentreGmao pourLeCentre(UUID centreId, String nom, OffsetDateTime du, OffsetDateTime au) {
        SyntheseGmao synthese = gmao.duCentre(centreId, du.toInstant(), au.toInstant());
        long patientsSurEquipementIndisponible = gmao.patientsSurGenerateurIndisponible(centreId);
        List<EquipementCout> classement = synthese.equipements().stream()
                .map(e -> new EquipementCout(e.id(), e.code(), e.designation(), e.statut(), e.coutDeLaPeriode(), e.coutCumule(),
                        e.prixAcquisition(), e.ratioMaintenance(), e.indisponibiliteHeures(), e.reformeRecommandee()))
                .toList();
        long nbReformeRecommandee = classement.stream().filter(EquipementCout::reformeRecommandee).count();
        return new CentreGmao(centreId, nom, synthese.nombreDEquipements(), synthese.horsService(), synthese.enMaintenance(),
                synthese.reformes(), synthese.interventionsEnCours(), synthese.coutDeMaintenance(), synthese.indisponibiliteHeures(),
                patientsSurEquipementIndisponible, nbReformeRecommandee, classementDesPlusCouteux(classement));
    }

    /** Les équipements les plus coûteux sur la période, complétés par tous ceux dont la réforme est recommandée (même sans coût). */
    private static List<EquipementCout> classementDesPlusCouteux(List<EquipementCout> tous) {
        List<EquipementCout> top = new ArrayList<>(tous.stream()
                .filter(e -> e.coutPeriode().signum() > 0)
                .sorted(Comparator.comparing(EquipementCout::coutPeriode).reversed())
                .limit(TOP_EQUIPEMENTS)
                .toList());
        tous.stream().filter(EquipementCout::reformeRecommandee).filter(e -> !top.contains(e)).forEach(top::add);
        return top;
    }

    private static List<Alerte> alertes(UUID centreId, String nom, CentreGmao c) {
        List<Alerte> alertes = new ArrayList<>();
        if (c.patientsSurEquipementIndisponible() > 0) {
            alertes.add(new Alerte(centreId, nom, "GMAO_PATIENT_SUR_EQUIPEMENT_EN_MAINTENANCE", Gravite.CRITICAL,
                    BigDecimal.valueOf(c.patientsSurEquipementIndisponible())));
        }
        if (c.nbHorsService() > 0) {
            alertes.add(new Alerte(centreId, nom, "GMAO_GENERATEURS_HORS_SERVICE", Gravite.WARNING, BigDecimal.valueOf(c.nbHorsService())));
        }
        if (c.nbReformeRecommandee() > 0) {
            alertes.add(new Alerte(centreId, nom, "GMAO_REFORME_RECOMMANDEE", Gravite.WARNING, BigDecimal.valueOf(c.nbReformeRecommandee())));
        }
        return alertes;
    }

    private static CentreGmao totaux(List<CentreGmao> centres) {
        long nbEquipements = 0, nbHorsService = 0, nbEnMaintenance = 0, nbReformes = 0, interventionsEnCours = 0,
                patientsSurEquipementIndisponible = 0, nbReformeRecommandee = 0;
        BigDecimal coutMaintenancePeriode = BigDecimal.ZERO;
        double indisponibiliteHeures = 0;
        for (CentreGmao c : centres) {
            nbEquipements += c.nbEquipements();
            nbHorsService += c.nbHorsService();
            nbEnMaintenance += c.nbEnMaintenance();
            nbReformes += c.nbReformes();
            interventionsEnCours += c.interventionsEnCours();
            coutMaintenancePeriode = coutMaintenancePeriode.add(c.coutMaintenancePeriode());
            indisponibiliteHeures += c.indisponibiliteHeuresCumulees();
            patientsSurEquipementIndisponible += c.patientsSurEquipementIndisponible();
            nbReformeRecommandee += c.nbReformeRecommandee();
        }
        return new CentreGmao(null, null, nbEquipements, nbHorsService, nbEnMaintenance, nbReformes, interventionsEnCours,
                coutMaintenancePeriode, indisponibiliteHeures, patientsSurEquipementIndisponible, nbReformeRecommandee, List.of());
    }

    public record CentreGmao(UUID centerId, String nom, long nbEquipements, long nbHorsService, long nbEnMaintenance, long nbReformes,
                             long interventionsEnCours, BigDecimal coutMaintenancePeriode, double indisponibiliteHeuresCumulees,
                             long patientsSurEquipementIndisponible, long nbReformeRecommandee, List<EquipementCout> topEquipements) {
    }

    /** Ligne du classement : coût de maintenance (période et cumulé), ratio sur le prix d'acquisition (null si inconnu), signal de réforme. */
    public record EquipementCout(UUID equipementId, String code, String designation, String statut, BigDecimal coutPeriode,
                                 BigDecimal coutCumule, BigDecimal prixAcquisition, BigDecimal ratioMaintenance,
                                 double indisponibiliteHeures, boolean reformeRecommandee) {
    }

    public record GmaoOverview(UUID societeId, LocalDate from, LocalDate to, OffsetDateTime generatedAt, List<CentreGmao> centres,
                               CentreGmao totaux, List<Alerte> alertes) {
    }
}
