package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.StatistiquesMedicalesPubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Statistiques médicales du patient sur les tables réelles {@code resultats_analyses} et {@code prescriptions_medicales} (DM9, ADR 0101).
 * Requêtes reprises MOT POUR MOT de {@code platform/.../application/query/PatientStatsQueryService.java} (retirées de la plateforme) :
 * moyennes {@code :58-60}, tendance des bilans {@code :62-67}, tendance EPO {@code :69-76}, lignes d'export {@code :88-94}, poids sec
 * {@code :317-321}, filtres de période {@code :261-273} (concaténation de {@code LocalDate} typés : pas d'injection). Lignes servies telles
 * que le pilote les rend (clés en MAJUSCULES, KB-MED-103) ; ordre par la seule date (KB-MED-109) ; moyenne absente rendue {@code 0.0}.
 */
public class StatistiquesMedicalesJdbc implements StatistiquesMedicalesPubliees {

    private final JdbcTemplate jdbc;

    public StatistiquesMedicalesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Moyennes moyennes(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        String baseFrom = " FROM resultats_analyses ra WHERE ra.center_id = ? AND ra.patient_id = ? " + filtreDesAnalyses(du, au);
        return new Moyennes(moyenne("SELECT AVG(ra.hb_g_dl)" + baseFrom, centre, patient),
                moyenne("SELECT AVG(ra.kt_v_mensuel)" + baseFrom, centre, patient),
                moyenne("SELECT AVG(ra.ferritine_ng_ml)" + baseFrom, centre, patient));
    }

    @Override
    public List<Map<String, Object>> tendanceDesBilans(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return jdbc.queryForList("SELECT ra.date_prelevement, ra.hb_g_dl, ra.kt_v_mensuel, ra.ferritine_ng_ml "
                        + " FROM resultats_analyses ra WHERE ra.center_id = ? AND ra.patient_id = ? " + filtreDesAnalyses(du, au)
                        + " ORDER BY ra.date_prelevement ASC", centre, patient);
    }

    @Override
    public List<Map<String, Object>> tendanceDesPrescriptionsDEpo(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return jdbc.queryForList("SELECT p.date_prescription, p.epo_dose_ui, p.epo_article_id, p.epo_voie, "
                        + "p.epo_frequence_valeur, p.epo_frequence_unite "
                        + "FROM prescriptions_medicales p "
                        + "WHERE p.center_id = ? AND p.patient_id = ? " + filtreDesPrescriptions(du, au)
                        + " ORDER BY p.date_prescription ASC", centre, patient);
    }

    @Override
    public List<Map<String, Object>> lignesDExport(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return jdbc.queryForList("SELECT ra.date_prelevement, ra.hb_g_dl, ra.kt_v_mensuel, ra.ferritine_ng_ml, ra.phosphore_mg_dl, ra.calcium_mg_dl "
                        + "FROM resultats_analyses ra "
                        + "WHERE ra.center_id = ? AND ra.patient_id = ? " + filtreDesAnalyses(du, au)
                        + " ORDER BY ra.date_prelevement ASC", centre, patient);
    }

    @Override
    public Optional<Double> poidsSecCibleEnVigueur(UUID centre, UUID patient, LocalDate au) {
        List<Double> values = jdbc.queryForList("SELECT pm.poids_sec_cible_kg FROM prescriptions_medicales pm "
                        + "WHERE pm.center_id = ? AND pm.patient_id = ? AND pm.poids_sec_cible_kg IS NOT NULL "
                        + "AND pm.date_prescription <= ? ORDER BY pm.date_prescription DESC, pm.updated_at DESC LIMIT 1",
                Double.class, centre, patient, au);
        return values.isEmpty() ? Optional.empty() : Optional.ofNullable(values.get(0));
    }

    private Double moyenne(String sql, UUID centre, UUID patient) {
        Double value = jdbc.queryForObject(sql, Double.class, centre, patient);
        return value != null ? value : 0.0d;
    }

    private static String filtreDesAnalyses(LocalDate from, LocalDate to) {
        if (from != null && to != null) return " AND ra.date_prelevement BETWEEN '" + from + "' AND '" + to + "'";
        if (from != null) return " AND ra.date_prelevement >= '" + from + "'";
        if (to != null) return " AND ra.date_prelevement <= '" + to + "'";
        return "";
    }

    private static String filtreDesPrescriptions(LocalDate from, LocalDate to) {
        if (from != null && to != null) return " AND p.date_prescription BETWEEN '" + from + "' AND '" + to + "'";
        if (from != null) return " AND p.date_prescription >= '" + from + "'";
        if (to != null) return " AND p.date_prescription <= '" + to + "'";
        return "";
    }
}
