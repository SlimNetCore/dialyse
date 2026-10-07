package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.medical.anemie.port.AlerteObservanceUseCase;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.DetailObservance;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeAlerteObservance;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.scheduling.ObservanceMesure.Mesure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Job quotidien de contrôle d'observance des prescriptions EPO / fer injectable.
 * <p>
 * Pour chaque patient ayant une prescription active référençant un article EPO et/ou fer avec
 * une fréquence renseignée, découpe le temps en périodes successives ancrées sur la date de
 * prescription ({@link ObservancePeriodMath}) et :
 * <ul>
 *   <li>vérifie la période qui vient de se clore (hier était son dernier jour) : si ce qui y a été administré est
 *   inférieur à la prescription — en <b>quantité</b> quand la prescription porte une dose, en nombre d'administrations
 *   sinon ({@link ObservanceMesure}) — ouvre une alerte {@link TypeAlerteObservance#RETARD_CONSTATE} : un fait acquis, que
 *   le médecin acquitte manuellement (la période est close, il n'y a plus rien à rattraper dedans) ;</li>
 *   <li>vérifie la période en cours : s'il reste à administrer et que l'échéance approche (moins de
 *   {@code seuilRappelJours} jours restants), ouvre/maintient une alerte
 *   {@link TypeAlerteObservance#RAPPEL_ECHEANCE} — se résout automatiquement dès que l'infirmier
 *   rattrape le retard avant la fin de la période.</li>
 * </ul>
 */
@Component
public class ObservancePrescriptionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ObservancePrescriptionScheduler.class);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

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

    /**
     * « Retard constaté » en toutes lettres : quel traitement, quelle période (close), ce que la prescription demandait,
     * ce qui a été administré et ce qui manque.
     */
    static String messageRetard(TypeTraitementAnemie type, Mesure mesure, LocalDate debut, LocalDate fin,
                                int frequenceValeur, String frequenceUnite) {
        return "Retard constaté sur le traitement " + libelle(type) + " : la période du " + JOUR.format(debut)
                + " au " + JOUR.format(fin) + " est terminée et la prescription n'a pas été entièrement administrée. "
                + prescrit(mesure, frequenceValeur, frequenceUnite) + " " + administre(mesure) + " "
                + manque(mesure) + " La période est close : l'acquitter une fois le constat pris en compte.";
    }

    /**
     * Rappel d'échéance : ce qu'il reste à administrer et avant quelle date.
     */
    static String messageRappel(TypeTraitementAnemie type, Mesure mesure, LocalDate fin, long joursRestants) {
        return "Il reste à administrer " + quantite(mesure, mesure.restant(), mesure.restantes()) + " de "
                + libelle(type) + " avant la fin de la période, le " + JOUR.format(fin) + " (" + joursRestants
                + " jour(s) restant(s)). Prescription : " + administre(mesure).toLowerCase(Locale.FRANCE);
    }

    private static String prescrit(Mesure m, int frequenceValeur, String frequenceUnite) {
        return m.enDose()
                ? "Prescription : " + m.dosePrescrite() + " " + m.unite() + " x " + frequenceValeur + " par "
                + frequenceUnite.toLowerCase(Locale.FRANCE) + ", soit " + m.attendu() + " " + m.unite()
                + " attendus sur la période."
                : "Prescription : " + frequenceValeur + " administration(s) par " + frequenceUnite.toLowerCase(Locale.FRANCE)
                + ".";
    }

    private static String administre(Mesure m) {
        return m.enDose()
                ? "Administré : " + m.administre() + " " + m.unite() + " (" + m.administrations() + " administration(s))."
                : "Administré : " + m.administrations() + " administration(s).";
    }

    private static String manque(Mesure m) {
        return "Il manque " + quantite(m, m.restant(), m.restantes()) + ".";
    }

    private static String quantite(Mesure m, int restant, int restantes) {
        return m.enDose() ? restant + " " + m.unite() : restantes + " administration(s)";
    }

    private static String libelle(TypeTraitementAnemie type) {
        return type == TypeTraitementAnemie.EPO ? "EPO" : "fer injectable";
    }

    void controlerCentre(CenterId centerId) {
        List<Map<String, Object>> prescriptions = jdbc.queryForList(
                "SELECT p.patient_id, p.date_prescription, p.epo_article_id, p.epo_dose_ui, p.epo_frequence_valeur, "
                        + "p.epo_frequence_unite, p.fer_article_id, p.fer_dose_mg, p.fer_frequence_valeur, "
                        + "p.fer_frequence_unite "
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
                    (UUID) row.get("epo_article_id"), asInt(row.get("epo_dose_ui")),
                    asInt(row.get("epo_frequence_valeur")), (String) row.get("epo_frequence_unite"));
            controlerTraitement(centerId, patientId, TypeTraitementAnemie.FER_INJECTABLE, datePrescription,
                    (UUID) row.get("fer_article_id"), asInt(row.get("fer_dose_mg")),
                    asInt(row.get("fer_frequence_valeur")), (String) row.get("fer_frequence_unite"));
        }
    }

    private void controlerTraitement(CenterId centerId, UUID patientId, TypeTraitementAnemie type,
                                     LocalDate datePrescription, UUID articleId, Integer dosePrescrite,
                                     Integer frequenceValeur, String frequenceUnite) {
        if (articleId == null || frequenceValeur == null || frequenceValeur <= 0 || frequenceUnite == null) {
            return;
        }
        LocalDate aujourdHui = LocalDate.now();
        int windowDays = ObservancePeriodMath.windowDaysFor(frequenceUnite);

        controlerPeriodePrecedente(centerId, patientId, type, datePrescription, windowDays, frequenceValeur,
                frequenceUnite, dosePrescrite, aujourdHui);
        controlerPeriodeEnCours(centerId, patientId, type, datePrescription, windowDays, frequenceValeur,
                frequenceUnite, dosePrescrite, aujourdHui);
    }

    private void controlerPeriodePrecedente(CenterId centerId, UUID patientId, TypeTraitementAnemie type,
                                            LocalDate datePrescription, int windowDays, int frequenceValeur,
                                            String frequenceUnite, Integer dosePrescrite, LocalDate aujourdHui) {
        ObservancePeriodMath.Periode precedente =
                ObservancePeriodMath.periodePrecedente(datePrescription, windowDays, aujourdHui);
        if (precedente == null) {
            return;
        }
        Mesure mesure = ObservanceMesure.mesurer(jdbc, centerId.value(), patientId, type.name(), precedente.debut(),
                precedente.fin(), frequenceValeur, dosePrescrite);
        if (mesure.enRetard()) {
            String message = messageRetard(type, mesure, precedente.debut(), precedente.fin(), frequenceValeur,
                    frequenceUnite);
            signaler(centerId, patientId, type, TypeAlerteObservance.RETARD_CONSTATE, precedente.debut(),
                    precedente.fin(), frequenceValeur, frequenceUnite, mesure, message);
            log.warn("[OBSERVANCE][RETARD] Centre {} patient {} {} : {}", centerId.value(), patientId, type, message);
        }
    }

    private void controlerPeriodeEnCours(CenterId centerId, UUID patientId, TypeTraitementAnemie type,
                                         LocalDate datePrescription, int windowDays, int frequenceValeur,
                                         String frequenceUnite, Integer dosePrescrite, LocalDate aujourdHui) {
        ObservancePeriodMath.Periode courante =
                ObservancePeriodMath.periodeCourante(datePrescription, windowDays, aujourdHui);
        Mesure mesure = ObservanceMesure.mesurer(jdbc, centerId.value(), patientId, type.name(), courante.debut(),
                courante.fin(), frequenceValeur, dosePrescrite);
        long joursRestants = courante.joursRestants(aujourdHui);
        int seuil = ObservancePeriodMath.seuilRappelJours(windowDays);

        if (mesure.enRetard() && joursRestants <= seuil) {
            String message = messageRappel(type, mesure, courante.fin(), joursRestants);
            signaler(centerId, patientId, type, TypeAlerteObservance.RAPPEL_ECHEANCE, courante.debut(),
                    courante.fin(), frequenceValeur, frequenceUnite, mesure, message);
            log.warn("[OBSERVANCE][RAPPEL] Centre {} patient {} {} : {}", centerId.value(), patientId, type, message);
        } else {
            alerteUseCase.resoudreSiConforme(centerId, patientId, type, TypeAlerteObservance.RAPPEL_ECHEANCE);
        }
    }

    private void signaler(CenterId centerId, UUID patientId, TypeTraitementAnemie type, TypeAlerteObservance nature,
                          LocalDate debut, LocalDate fin, int frequenceValeur, String frequenceUnite, Mesure mesure,
                          String message) {
        DetailObservance detail = new DetailObservance(mesure.unite(), mesure.dosePrescrite(), frequenceValeur,
                frequenceUnite);
        // la colonne du message est limitée à 500 caractères
        String texte = message.length() > 500 ? message.substring(0, 499) + "…" : message;
        alerteUseCase.signalerNonConformite(centerId, patientId, type, nature, debut, fin, mesure.attendu(),
                mesure.administre(), texte, detail);
        Map<String, String> details = new HashMap<>();
        details.put("typeAlerte", nature.name());
        details.put("typeTraitement", type.name());
        details.put("debut", debut.toString());
        details.put("fin", fin.toString());
        details.put("attendu", String.valueOf(mesure.attendu()));
        details.put("administre", String.valueOf(mesure.administre()));
        details.put("unite", mesure.unite() == null ? "" : mesure.unite());
        notificationService.notifyObservanceNonRespectee(centerId.value(), patientId, message, details);
    }

    private Integer asInt(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }
}
