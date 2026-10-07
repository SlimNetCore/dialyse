package com.hemodialyse.backend.infrastructure.scheduling;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Mesure de l'observance d'un traitement (EPO, fer) sur une période : ce qui est prescrit et ce qui a été administré.
 * Calcul commun à l'écran de l'infirmier ({@code ObservanceAnemieQueryService}) et aux alertes planifiées
 * ({@link ObservancePrescriptionScheduler}), pour qu'ils ne se contredisent jamais.
 * <p>
 * Quand la prescription porte une <b>dose</b> (8000 UI d'EPO, 200 mg de fer), l'observance se mesure en <b>quantité</b> :
 * attendu = dose × fréquence ; administré = somme des doses effectivement administrées (même unité). Une administration de
 * 4000 UI ne solde donc pas une prescription passée à 8000 UI : il reste 4000 UI. Sans dose prescrite, on compte les
 * administrations, comme avant.
 */
public final class ObservanceMesure {

    private ObservanceMesure() {
    }

    /**
     * Calcul pur : {@code doseAdministree} est la somme des doses administrées dans l'unité prescrite.
     */
    public static Mesure calculer(int frequenceValeur, Integer dosePrescrite, String unite, int administrations,
                                  long doseAdministree) {
        if (dosePrescrite == null || dosePrescrite <= 0 || unite == null || unite.isBlank()) {
            int restantes = Math.max(0, frequenceValeur - administrations);
            return new Mesure(administrations, null, null, frequenceValeur, administrations, restantes, restantes);
        }
        int attendu = Math.multiplyExact(dosePrescrite, frequenceValeur);
        int administre = (int) Math.min(Integer.MAX_VALUE, Math.max(0, doseAdministree));
        int restant = Math.max(0, attendu - administre);
        int restantes = restant == 0 ? 0 : (int) Math.ceil((double) restant / dosePrescrite);
        return new Mesure(administrations, unite, dosePrescrite, attendu, administre, restant, restantes);
    }

    /**
     * Mesure sur la base : administrations effectives du patient, de ce traitement, dans la période. Seules les doses
     * exprimées dans l'unité prescrite comptent.
     */
    public static Mesure mesurer(JdbcTemplate jdbc, UUID centerId, UUID patientId, String typeTraitement,
                                 LocalDate debut, LocalDate fin, int frequenceValeur, Integer dosePrescrite) {
        String unite = uniteDe(typeTraitement);
        Integer administrations = jdbc.queryForObject(
                "SELECT COUNT(1) FROM administrations_anemie WHERE center_id = ? AND patient_id = ? "
                        + "AND type_traitement = ? AND administree = TRUE AND date_administration BETWEEN ? AND ?",
                Integer.class, centerId, patientId, typeTraitement, debut, fin);
        Long dose = jdbc.queryForObject(
                "SELECT COALESCE(SUM(dose), 0) FROM administrations_anemie WHERE center_id = ? AND patient_id = ? "
                        + "AND type_traitement = ? AND administree = TRUE AND date_administration BETWEEN ? AND ? "
                        + "AND UPPER(unite_dose) = UPPER(?)",
                Long.class, centerId, patientId, typeTraitement, debut, fin, unite);
        return calculer(frequenceValeur, dosePrescrite, unite, administrations == null ? 0 : administrations,
                dose == null ? 0 : dose);
    }

    /**
     * Unité de la dose d'un traitement : EPO en unités internationales, fer en milligrammes.
     */
    static String uniteDe(String typeTraitement) {
        return "EPO".equals(typeTraitement) ? "UI" : "mg";
    }

    /**
     * @param administrations nombre d'administrations effectives de la période
     * @param unite           unité de la dose prescrite ({@code null} : mesure en nombre d'administrations)
     * @param dosePrescrite   dose prescrite à chaque administration ({@code null} sans dose)
     * @param attendu         quantité attendue (dose × fréquence), ou nombre d'administrations attendues sans dose
     * @param administre      quantité administrée (même unité que {@code attendu})
     * @param restant         ce qu'il reste à administrer (≥ 0), même unité que {@code attendu}
     * @param restantes       administrations qu'il reste à faire (≥ 1 tant que {@code restant} > 0)
     */
    public record Mesure(int administrations, String unite, Integer dosePrescrite, int attendu, int administre,
                         int restant, int restantes) {

        public boolean enDose() {
            return unite != null;
        }

        /**
         * Vrai tant que la prescription n'est pas intégralement administrée.
         */
        public boolean enRetard() {
            return restant > 0;
        }
    }
}
