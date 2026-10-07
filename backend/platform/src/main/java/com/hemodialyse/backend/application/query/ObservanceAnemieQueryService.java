package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.infrastructure.scheduling.ObservanceMesure;
import com.hemodialyse.backend.infrastructure.scheduling.ObservancePeriodMath;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Calcule, pour la prescription EPO/fer active d'un patient, où en est l'observance sur la
 * période en cours : ce qui est attendu, déjà administré, restant, et échéance de la période.
 * Utilisé par le volet séance pour que l'infirmier sache exactement ce qu'il reste à administrer
 * avant la fin de la semaine/du mois prescrit — même calcul de périodes et de quantités que
 * {@link com.hemodialyse.backend.infrastructure.scheduling.ObservancePrescriptionScheduler}
 * ({@link ObservanceMesure}), pour qu'affichage en séance et alertes planifiées restent strictement cohérents.
 * <p>
 * Quand la prescription porte une dose, le reste à administrer est une <b>quantité</b> (UI, mg) : une prescription
 * passée de 4000 à 8000 UI alors que 4000 UI ont déjà été administrées laisse 4000 UI à administrer.
 */
@Service
public class ObservanceAnemieQueryService {

    private final JdbcTemplate jdbc;

    public ObservanceAnemieQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ObservanceAnemie getObservanceActuelle(UUID centerId, UUID patientId) {
        Map<String, Object> row = jdbc.queryForList(
                "SELECT p.date_prescription, p.epo_article_id, p.epo_dose_ui, p.epo_frequence_valeur, "
                        + "p.epo_frequence_unite, p.fer_article_id, p.fer_dose_mg, p.fer_frequence_valeur, "
                        + "p.fer_frequence_unite "
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
                (UUID) row.get("epo_article_id"), asInt(row.get("epo_dose_ui")),
                asInt(row.get("epo_frequence_valeur")), (String) row.get("epo_frequence_unite"));
        ObservanceTraitement fer = calculer(centerId, patientId, "FER_INJECTABLE", datePrescription,
                (UUID) row.get("fer_article_id"), asInt(row.get("fer_dose_mg")),
                asInt(row.get("fer_frequence_valeur")), (String) row.get("fer_frequence_unite"));
        return new ObservanceAnemie(epo, fer);
    }

    private ObservanceTraitement calculer(UUID centerId, UUID patientId, String type, LocalDate datePrescription,
                                          UUID articleId, Integer dosePrescrite, Integer frequenceValeur,
                                          String frequenceUnite) {
        if (articleId == null || frequenceValeur == null || frequenceValeur <= 0 || frequenceUnite == null) {
            return null;
        }
        LocalDate aujourdHui = LocalDate.now();
        int windowDays = ObservancePeriodMath.windowDaysFor(frequenceUnite);
        ObservancePeriodMath.Periode courante = ObservancePeriodMath.periodeCourante(datePrescription, windowDays, aujourdHui);

        ObservanceMesure.Mesure mesure = ObservanceMesure.mesurer(jdbc, centerId, patientId, type, courante.debut(),
                courante.fin(), frequenceValeur, dosePrescrite);

        return new ObservanceTraitement(courante.debut(), courante.fin(), frequenceValeur, mesure.administrations(),
                mesure.restantes(), courante.joursRestants(aujourdHui), mesure.unite(), mesure.dosePrescrite(),
                mesure.enDose() ? mesure.attendu() : null, mesure.enDose() ? mesure.administre() : null,
                mesure.enDose() ? mesure.restant() : null);
    }

    private Integer asInt(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    /**
     * @param dosesAttendues    administrations prescrites par période
     * @param dosesAdministrees administrations effectives de la période
     * @param dosesRestantes    administrations qu'il reste à faire (≥ 1 tant qu'il reste une quantité à administrer)
     * @param uniteDose         unité de la dose prescrite (« UI », « mg »), {@code null} si la prescription n'a pas de dose
     * @param dosePrescrite     dose prescrite à chaque administration
     * @param doseAttendue      quantité attendue sur la période (dose × fréquence)
     * @param doseAdministree   quantité effectivement administrée sur la période
     * @param doseRestante      quantité qu'il reste à administrer (≥ 0)
     */
    public record ObservanceTraitement(LocalDate periodeDebut, LocalDate periodeFin, int dosesAttendues,
                                       int dosesAdministrees, int dosesRestantes, long joursRestants,
                                       String uniteDose, Integer dosePrescrite, Integer doseAttendue,
                                       Integer doseAdministree, Integer doseRestante) {
    }

    public record ObservanceAnemie(ObservanceTraitement epo, ObservanceTraitement fer) {
    }
}
