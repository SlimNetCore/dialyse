package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionAlertPolicy.Alert;
import com.hemodialyse.backend.application.direction.DirectionAlertPolicy.Severity;
import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.CentreInfo;
import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.SocieteInfo;
import com.hemodialyse.backend.domain.gmao.model.Equipement;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.EquipementStatutHistoriqueRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import com.hemodialyse.backend.domain.gmao.service.AideDecisionMaintenance;
import com.hemodialyse.backend.domain.gmao.service.AideDecisionMaintenance.AnalyseCout;
import com.hemodialyse.backend.domain.gmao.service.IndisponibiliteCalculator;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * Enrichissement GMAO (aide à la décision) du tableau de bord de la direction : coût de maintenance,
 * état du parc d'équipements et temps d'indisponibilité, par centre et consolidés pour la société —
 * même patron que {@link DirectionIndicatorsQueryService} (par-centre + totaux, aucune anonymisation
 * nécessaire ici car il ne s'agit pas de données patient nominatives, hors sécurité patient ci-dessous).
 */
@Service
public class DirectionGmaoQueryService {

    private static final int MAX_YEARS = 5;

    private final JdbcTemplate jdbc;
    private final DirectionDashboardQueryService dashboard;
    private final EquipementRepositoryPort equipementRepository;
    private final InterventionRepositoryPort interventionRepository;
    private static final int TOP_EQUIPEMENTS = 5;

    private final EquipementStatutHistoriqueRepositoryPort historiqueRepository;
    private final AideDecisionMaintenance aideDecision;

    public DirectionGmaoQueryService(
            JdbcTemplate jdbc,
            DirectionDashboardQueryService dashboard,
            EquipementRepositoryPort equipementRepository,
            InterventionRepositoryPort interventionRepository,
            EquipementStatutHistoriqueRepositoryPort historiqueRepository,
            AideDecisionMaintenance aideDecision) {
        this.aideDecision = aideDecision;
        this.jdbc = jdbc;
        this.dashboard = dashboard;
        this.equipementRepository = equipementRepository;
        this.interventionRepository = interventionRepository;
        this.historiqueRepository = historiqueRepository;
    }

    public GmaoOverview gmao(UUID societeId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : LocalDate.of(end.getYear(), 1, 1);
        if (start.isAfter(end)) {
            throw new BusinessException("PERIODE_INVALIDE", "La date de début doit précéder la date de fin");
        }
        if (start.plusYears(MAX_YEARS).isBefore(end)) {
            throw new BusinessException("PERIODE_TROP_LONGUE", "La période ne peut pas dépasser " + MAX_YEARS + " ans");
        }

        SocieteInfo societe = dashboard.societe(societeId);
        LocalDateTime fromDt = start.atStartOfDay();
        LocalDateTime toDt = end.plusDays(1).atStartOfDay();
        OffsetDateTime generatedAt = OffsetDateTime.now(ZoneOffset.UTC);

        List<CentreGmao> centres = new ArrayList<>();
        List<Alert> alertes = new ArrayList<>();
        for (CentreInfo c : societe.centres()) {
            CentreGmao centreGmao = buildForCentre(c.id(), c.nom(), fromDt, toDt);
            centres.add(centreGmao);
            alertes.addAll(evaluateAlerts(c.id(), c.nom(), centreGmao));
        }

        return new GmaoOverview(societeId, start, end, generatedAt, centres, totaux(centres), alertes);
    }

    private CentreGmao buildForCentre(UUID centreId, String nom, LocalDateTime from, LocalDateTime to) {
        long nbEquipements = equipementRepository.countByCentreId(centreId);
        long nbHorsService = equipementRepository.countByCentreIdAndStatut(centreId, StatutEquipement.HORS_SERVICE.name());
        long nbEnMaintenance = equipementRepository.countByCentreIdAndStatut(centreId, StatutEquipement.EN_MAINTENANCE.name());
        long nbReformes = equipementRepository.countByCentreIdAndStatut(centreId, StatutEquipement.REFORME.name());
        long interventionsEnCours = interventionRepository.countByCentreIdAndStatut(centreId, "EN_COURS");
        BigDecimal coutMaintenancePeriode = interventionRepository.sumCoutByCentreIdAndDateRange(centreId, from, to);
        long patientsSurEquipementIndisponible = countPatientsSurEquipementIndisponible(centreId);

        List<Equipement> equipements = equipementRepository.findByCentreId(centreId);
        Map<UUID, BigDecimal> coutsPeriode = interventionRepository.sumCoutParEquipement(centreId, from, to);
        Map<UUID, BigDecimal> coutsCumules = interventionRepository.sumCoutCumuleParEquipement(centreId);
        double indisponibiliteHeures = 0;
        List<EquipementCout> classement = new ArrayList<>();
        for (Equipement e : equipements) {
            double heures = indisponibiliteHeures(e, from, to);
            indisponibiliteHeures += heures;
            BigDecimal coutPeriode = coutsPeriode.getOrDefault(e.getId(), BigDecimal.ZERO);
            BigDecimal coutCumule = coutsCumules.getOrDefault(e.getId(), BigDecimal.ZERO);
            AnalyseCout analyse = aideDecision.analyser(
                    e.getStatut(), e.getPrixAcquisition(), coutCumule, coutPeriode, heures);
            classement.add(new EquipementCout(e.getId(), e.getCode(), e.getDesignation(), e.getStatut().name(),
                    coutPeriode, coutCumule, e.getPrixAcquisition(), analyse.ratioMaintenance(), heures,
                    analyse.reformeRecommandee()));
        }
        long nbReformeRecommandee = classement.stream().filter(EquipementCout::reformeRecommandee).count();

        return new CentreGmao(centreId, nom, nbEquipements, nbHorsService, nbEnMaintenance, nbReformes,
                interventionsEnCours, coutMaintenancePeriode, indisponibiliteHeures, patientsSurEquipementIndisponible,
                nbReformeRecommandee, topEquipements(classement));
    }

    private double indisponibiliteHeures(Equipement e, LocalDateTime from, LocalDateTime to) {
        Duration d = IndisponibiliteCalculator.calculer(
                historiqueRepository.findByEquipementIdOrderByChangedAtAsc(e.getId()), e.getStatut(), from, to);
        return d.toMinutes() / 60.0;
    }

    /**
     * Classement des équipements les plus coûteux sur la période, complété par tous ceux dont la
     * réforme est recommandée (même sans coût sur la période).
     */
    private List<EquipementCout> topEquipements(List<EquipementCout> all) {
        List<EquipementCout> top = new ArrayList<>(all.stream()
                .filter(e -> e.coutPeriode().signum() > 0)
                .sorted(Comparator.comparing(EquipementCout::coutPeriode).reversed())
                .limit(TOP_EQUIPEMENTS)
                .toList());
        all.stream().filter(EquipementCout::reformeRecommandee).filter(e -> !top.contains(e)).forEach(top::add);
        return top;
    }

    /**
     * Patients actuellement affectés à un générateur indisponible (en maintenance, en attente de pièce,
     * hors service, réformé) ou à réformer — alerte de sécurité patient (module GMAO v2).
     */
    private long countPatientsSurEquipementIndisponible(UUID centreId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT p.id) FROM patients p " +
                        "JOIN gmao_equipements g ON g.id = p.generateur_id AND g.deleted_at IS NULL " +
                        "WHERE p.center_id = ? AND (" +
                        "  g.statut IN ('EN_MAINTENANCE','EN_ATTENTE_PIECE','HORS_SERVICE','A_REFORMER','REFORME')" +
                        ")",
                Long.class, centreId);
        return count == null ? 0 : count;
    }

    private List<Alert> evaluateAlerts(UUID centreId, String nom, CentreGmao c) {
        List<Alert> alerts = new ArrayList<>();
        if (c.patientsSurEquipementIndisponible() > 0) {
            alerts.add(new Alert(centreId, nom, "GMAO_PATIENT_SUR_EQUIPEMENT_EN_MAINTENANCE", Severity.CRITICAL,
                    BigDecimal.valueOf(c.patientsSurEquipementIndisponible())));
        }
        if (c.nbHorsService() > 0) {
            alerts.add(new Alert(centreId, nom, "GMAO_GENERATEURS_HORS_SERVICE", Severity.WARNING,
                    BigDecimal.valueOf(c.nbHorsService())));
        }
        if (c.nbReformeRecommandee() > 0) {
            alerts.add(new Alert(centreId, nom, "GMAO_REFORME_RECOMMANDEE", Severity.WARNING,
                    BigDecimal.valueOf(c.nbReformeRecommandee())));
        }
        return alerts;
    }

    private CentreGmao totaux(List<CentreGmao> centres) {
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
        return new CentreGmao(null, null, nbEquipements, nbHorsService, nbEnMaintenance, nbReformes,
                interventionsEnCours, coutMaintenancePeriode, indisponibiliteHeures, patientsSurEquipementIndisponible,
                nbReformeRecommandee, List.of());
    }

    public record CentreGmao(
            UUID centerId,
            String nom,
            long nbEquipements,
            long nbHorsService,
            long nbEnMaintenance,
            long nbReformes,
            long interventionsEnCours,
            BigDecimal coutMaintenancePeriode,
            double indisponibiliteHeuresCumulees,
            long patientsSurEquipementIndisponible,
            long nbReformeRecommandee,
            List<EquipementCout> topEquipements
    ) {
    }

    /**
     * Ligne du classement des équipements : coût de maintenance (période et cumulé), ratio sur le prix
     * d'acquisition (null si prix inconnu) et signal de réforme.
     */
    public record EquipementCout(
            UUID equipementId,
            String code,
            String designation,
            String statut,
            BigDecimal coutPeriode,
            BigDecimal coutCumule,
            BigDecimal prixAcquisition,
            BigDecimal ratioMaintenance,
            double indisponibiliteHeures,
            boolean reformeRecommandee
    ) {
    }

    public record GmaoOverview(
            UUID societeId,
            LocalDate from,
            LocalDate to,
            OffsetDateTime generatedAt,
            List<CentreGmao> centres,
            CentreGmao totaux,
            List<Alert> alertes
    ) {
    }
}
