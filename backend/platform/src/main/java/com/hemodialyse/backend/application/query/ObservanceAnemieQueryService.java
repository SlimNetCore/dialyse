package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.infrastructure.scheduling.ObservancePeriodMath;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Calcule, pour la prescription EPO/fer active d'un patient, où en est l'observance sur la
 * période en cours : doses attendues, déjà administrées, restantes, et échéance de la période.
 * Utilisé par le volet séance pour que l'infirmier sache exactement ce qu'il reste à administrer
 * avant la fin de la semaine/du mois prescrit — même calcul de périodes que
 * {@link com.hemodialyse.backend.infrastructure.scheduling.ObservancePrescriptionScheduler}, pour
 * qu'affichage en séance et alertes planifiées restent strictement cohérents.
 */
@Service
public class ObservanceAnemieQueryService {

    private final JdbcTemplate jdbc;

    public ObservanceAnemieQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ObservanceAnemie getObservanceActuelle(UUID centerId, UUID patientId) {
        Map<String, Object> row = jdbc.queryForList(
                "SELECT p.date_prescription, p.epo_article_id, p.epo_frequence_valeur, p.epo_frequence_unite, "
                        + "p.fer_article_id, p.fer_frequence_valeur, p.fer_frequence_unite "
                        + "FROM prescriptions_medicales p "
                        + "WHERE p.center_id = ? AND p.patient_id = ? "
                        + "ORDER BY p.date_prescription DESC LIMIT 1",
                centerId, patientId
        ).stream().findFirst().orElse(null);

        if (row == null) {
            return new ObservanceAnemie(null, null);
        }

        LocalDate datePrescription = ((java.sql.Date) row.get("date_prescription")).toLocalDate();
        ObservanceTraitement epo = calculer(centerId, patientId, "EPO", datePrescription,
                (UUID) row.get("epo_article_id"), asInt(row.get("epo_frequence_valeur")),
                (String) row.get("epo_frequence_unite"));
        ObservanceTraitement fer = calculer(centerId, patientId, "FER_INJECTABLE", datePrescription,
                (UUID) row.get("fer_article_id"), asInt(row.get("fer_frequence_valeur")),
                (String) row.get("fer_frequence_unite"));
        return new ObservanceAnemie(epo, fer);
    }

    private ObservanceTraitement calculer(UUID centerId, UUID patientId, String type, LocalDate datePrescription,
                                          UUID articleId, Integer frequenceValeur, String frequenceUnite) {
        if (articleId == null || frequenceValeur == null || frequenceValeur <= 0 || frequenceUnite == null) {
            return null;
        }
        LocalDate aujourdHui = LocalDate.now();
        int windowDays = ObservancePeriodMath.windowDaysFor(frequenceUnite);
        ObservancePeriodMath.Periode courante = ObservancePeriodMath.periodeCourante(datePrescription, windowDays, aujourdHui);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM administrations_anemie WHERE center_id = ? AND patient_id = ? "
                        + "AND type_traitement = ? AND administree = TRUE AND date_administration BETWEEN ? AND ?",
                Integer.class, centerId, patientId, type, courante.debut(), courante.fin());
        int administrees = count == null ? 0 : count;
        int restantes = Math.max(0, frequenceValeur - administrees);

        return new ObservanceTraitement(courante.debut(), courante.fin(), frequenceValeur, administrees, restantes,
                courante.joursRestants(aujourdHui));
    }

    private Integer asInt(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    public record ObservanceTraitement(LocalDate periodeDebut, LocalDate periodeFin, int dosesAttendues,
                                       int dosesAdministrees, int dosesRestantes, long joursRestants) {
    }

    public record ObservanceAnemie(ObservanceTraitement epo, ObservanceTraitement fer) {
    }
}
