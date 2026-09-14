package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.medical.anemie.port.AlerteObservanceUseCase;
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
 * une fréquence renseignée, compare le nombre de doses réellement administrées sur une fenêtre
 * glissante (dont la durée suit l'unité de fréquence prescrite : 7 jours pour une semaine, 30 pour
 * un mois, etc.) au nombre de doses attendues sur cette même fenêtre. En cas d'écart, ouvre (ou
 * laisse ouverte) une {@code AlerteObservance} et notifie le médecin en temps réel ; si
 * l'observance redevient conforme, l'alerte ouverte est résolue silencieusement.
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
                "SELECT p.patient_id, p.epo_article_id, p.epo_frequence_valeur, p.epo_frequence_unite, "
                        + "p.fer_article_id, p.fer_frequence_valeur, p.fer_frequence_unite "
                        + "FROM prescriptions_medicales p "
                        + "INNER JOIN (SELECT patient_id, MAX(date_prescription) AS max_date "
                        + "            FROM prescriptions_medicales WHERE center_id = ? GROUP BY patient_id) latest "
                        + "  ON latest.patient_id = p.patient_id AND latest.max_date = p.date_prescription "
                        + "WHERE p.center_id = ?",
                centerId.value(), centerId.value());

        for (Map<String, Object> row : prescriptions) {
            UUID patientId = (UUID) row.get("patient_id");
            controlerTraitement(centerId, patientId, TypeTraitementAnemie.EPO,
                    (UUID) row.get("epo_article_id"), asInt(row.get("epo_frequence_valeur")),
                    (String) row.get("epo_frequence_unite"));
            controlerTraitement(centerId, patientId, TypeTraitementAnemie.FER_INJECTABLE,
                    (UUID) row.get("fer_article_id"), asInt(row.get("fer_frequence_valeur")),
                    (String) row.get("fer_frequence_unite"));
        }
    }

    private void controlerTraitement(CenterId centerId, UUID patientId, TypeTraitementAnemie type,
                                     UUID articleId, Integer frequenceValeur, String frequenceUnite) {
        if (articleId == null || frequenceValeur == null || frequenceValeur <= 0 || frequenceUnite == null) {
            return;
        }
        int windowDays = windowDaysFor(frequenceUnite);
        LocalDate periodeFin = LocalDate.now();
        LocalDate periodeDebut = periodeFin.minusDays(windowDays);

        Integer dosesAdministrees = jdbc.queryForObject(
                "SELECT COUNT(1) FROM administrations_anemie WHERE center_id = ? AND patient_id = ? "
                        + "AND type_traitement = ? AND administree = TRUE AND date_administration BETWEEN ? AND ?",
                Integer.class, centerId.value(), patientId, type.name(), periodeDebut, periodeFin);
        int administrees = dosesAdministrees == null ? 0 : dosesAdministrees;

        if (administrees < frequenceValeur) {
            String message = String.format(
                    "Observance non respectee : %d/%d administration(s) de %s attendue(s) entre le %s et le %s",
                    administrees, frequenceValeur, type.name(), periodeDebut, periodeFin);
            alerteUseCase.signalerNonConformite(centerId, patientId, type, periodeDebut, periodeFin,
                    frequenceValeur, administrees, message);
            notificationService.notifyObservanceNonRespectee(centerId.value(), patientId, message);
            log.warn("[OBSERVANCE] Centre {} patient {} {} : {}", centerId.value(), patientId, type, message);
        } else {
            alerteUseCase.resoudreSiConforme(centerId, patientId, type);
        }
    }

    private int windowDaysFor(String unite) {
        return switch (unite) {
            case "HEURE", "JOUR" -> 1;
            case "SEMAINE" -> 7;
            case "MOIS" -> 30;
            case "ANNEE" -> 365;
            default -> 7;
        };
    }

    private Integer asInt(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }
}
