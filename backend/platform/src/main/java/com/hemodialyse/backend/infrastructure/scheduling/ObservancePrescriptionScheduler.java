package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.medical.anemie.port.AlerteObservanceUseCase;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeAlerteObservance;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Job quotidien de contrôle d'observance des prescriptions EPO / fer injectable.
 * <p>
 * Pour chaque patient ayant une prescription active référençant un article EPO et/ou fer avec
 * une fréquence renseignée, découpe le temps en périodes successives ancrées sur la date de
 * prescription ({@link ObservancePeriodMath}) et :
 * <ul>
 *   <li>vérifie la période qui vient de se clore (hier était son dernier jour) : si le nombre
 *   d'administrations réelles y est inférieur à la prescription, ouvre une alerte
 *   {@link TypeAlerteObservance#RETARD_CONSTATE} — un fait acquis, que le médecin acquitte
 *   manuellement (la période est close, il n'y a plus rien à rattraper dedans) ;</li>
 *   <li>vérifie la période en cours : s'il reste des doses à administrer et que l'échéance
 *   approche (moins de {@code seuilRappelJours} jours restants), ouvre/maintient une alerte
 *   {@link TypeAlerteObservance#RAPPEL_ECHEANCE} — se résout automatiquement dès que l'infirmier
 *   rattrape le retard avant la fin de la période.</li>
 * </ul>
 */
@Component
public class ObservancePrescriptionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ObservancePrescriptionScheduler.class);

    private final JdbcTemplate jdbc;
    private final AlerteObservanceUseCase alerteUseCase;
    private final NotificationService notificationService;

    public ObservancePrescriptionScheduler(JdbcTemplate jdbc, AlerteObservanceUseCase alerteUseCase,
                                           NotificationService notificationService) {
        this.jdbc = jdbc;
        this.alerteUseCase = alerteUseCase;
        this.notificationService = notificationService;
    }

    /**
     * Tous les jours à 06:30.
     */
    @Scheduled(cron = "0 30 6 * * *")
    public void controlerObservance() {
        List<UUID> centers = jdbc.query("SELECT id FROM centers", (rs, i) -> UUID.fromString(rs.getString(1)));
        for (UUID centerId : centers) {
            controlerCentre(CenterId.of(centerId));
        }
    }

    private void controlerCentre(CenterId centerId) {
        List<Map<String, Object>> prescriptions = jdbc.queryForList(
                "SELECT p.patient_id, p.date_prescription, p.epo_article_id, p.epo_frequence_valeur, "
                        + "p.epo_frequence_unite, p.fer_article_id, p.fer_frequence_valeur, p.fer_frequence_unite "
                        + "FROM prescriptions_medicales p "
                        + "INNER JOIN (SELECT patient_id, MAX(date_prescription) AS max_date "
                        + "            FROM prescriptions_medicales WHERE center_id = ? GROUP BY patient_id) latest "
                        + "  ON latest.patient_id = p.patient_id AND latest.max_date = p.date_prescription "
                        + "WHERE p.center_id = ?",
                centerId.value(), centerId.value());

        for (Map<String, Object> row : prescriptions) {
            UUID patientId = (UUID) row.get("patient_id");
            LocalDate datePrescription = ((java.sql.Date) row.get("date_prescription")).toLocalDate();
            controlerTraitement(centerId, patientId, TypeTraitementAnemie.EPO, datePrescription,
                    (UUID) row.get("epo_article_id"), asInt(row.get("epo_frequence_valeur")),
                    (String) row.get("epo_frequence_unite"));
            controlerTraitement(centerId, patientId, TypeTraitementAnemie.FER_INJECTABLE, datePrescription,
                    (UUID) row.get("fer_article_id"), asInt(row.get("fer_frequence_valeur")),
                    (String) row.get("fer_frequence_unite"));
        }
    }

    private void controlerTraitement(CenterId centerId, UUID patientId, TypeTraitementAnemie type,
                                     LocalDate datePrescription, UUID articleId, Integer frequenceValeur,
                                     String frequenceUnite) {
        if (articleId == null || frequenceValeur == null || frequenceValeur <= 0 || frequenceUnite == null) {
            return;
        }
        LocalDate aujourdHui = LocalDate.now();
        int windowDays = ObservancePeriodMath.windowDaysFor(frequenceUnite);

        controlerPeriodePrecedente(centerId, patientId, type, datePrescription, windowDays, frequenceValeur, aujourdHui);
        controlerPeriodeEnCours(centerId, patientId, type, datePrescription, windowDays, frequenceValeur, aujourdHui);
    }

    private void controlerPeriodePrecedente(CenterId centerId, UUID patientId, TypeTraitementAnemie type,
                                            LocalDate datePrescription, int windowDays, int frequenceValeur,
                                            LocalDate aujourdHui) {
        ObservancePeriodMath.Periode precedente =
                ObservancePeriodMath.periodePrecedente(datePrescription, windowDays, aujourdHui);
        if (precedente == null) {
            return;
        }
        int administrees = compterAdministrations(centerId, patientId, type, precedente.debut(), precedente.fin());
        if (administrees < frequenceValeur) {
            String message = String.format(
                    "Observance non respectee : %d/%d administration(s) de %s entre le %s et le %s",
                    administrees, frequenceValeur, type.name(), precedente.debut(), precedente.fin());
            alerteUseCase.signalerNonConformite(centerId, patientId, type, TypeAlerteObservance.RETARD_CONSTATE,
                    precedente.debut(), precedente.fin(), frequenceValeur, administrees, message);
            notificationService.notifyObservanceNonRespectee(centerId.value(), patientId, message);
            log.warn("[OBSERVANCE][RETARD] Centre {} patient {} {} : {}", centerId.value(), patientId, type, message);
        }
    }

    private void controlerPeriodeEnCours(CenterId centerId, UUID patientId, TypeTraitementAnemie type,
                                         LocalDate datePrescription, int windowDays, int frequenceValeur,
                                         LocalDate aujourdHui) {
        ObservancePeriodMath.Periode courante =
                ObservancePeriodMath.periodeCourante(datePrescription, windowDays, aujourdHui);
        int administrees = compterAdministrations(centerId, patientId, type, courante.debut(), courante.fin());
        int restantes = Math.max(0, frequenceValeur - administrees);
        long joursRestants = courante.joursRestants(aujourdHui);
        int seuil = ObservancePeriodMath.seuilRappelJours(windowDays);

        if (restantes > 0 && joursRestants <= seuil) {
            String message = String.format(
                    "Il reste %d dose(s) de %s a administrer avant la fin de la periode (%s), echeance le %s",
                    restantes, type.name(), courante.fin(), courante.fin());
            alerteUseCase.signalerNonConformite(centerId, patientId, type, TypeAlerteObservance.RAPPEL_ECHEANCE,
                    courante.debut(), courante.fin(), frequenceValeur, administrees, message);
            notificationService.notifyObservanceNonRespectee(centerId.value(), patientId, message);
            log.warn("[OBSERVANCE][RAPPEL] Centre {} patient {} {} : {}", centerId.value(), patientId, type, message);
        } else {
            alerteUseCase.resoudreSiConforme(centerId, patientId, type, TypeAlerteObservance.RAPPEL_ECHEANCE);
        }
    }

    private int compterAdministrations(CenterId centerId, UUID patientId, TypeTraitementAnemie type,
                                       LocalDate debut, LocalDate fin) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM administrations_anemie WHERE center_id = ? AND patient_id = ? "
                        + "AND type_traitement = ? AND administree = TRUE AND date_administration BETWEEN ? AND ?",
                Integer.class, centerId.value(), patientId, type.name(), debut, fin);
        return count == null ? 0 : count;
    }

    private Integer asInt(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }
}
