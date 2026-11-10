package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.StatistiquesDesSeancesPubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lecture publiée des séances et de leur volet paramédical pour les statistiques du patient (ADR 0099, SE7e), lecture seule. Requêtes
 * reprises MOT POUR MOT de {@code PatientStatsQueryService.getParamedicalStats} (plateforme) (E6 à E9 de {@code .ddd/safety-net/seance-se7e.md}) :
 * (corrigées par le lot L5b, ADR 0118 : séances RÉALISÉES seulement et volet borné au centre de la séance, KB-SEA-85), dates de période écrites en LITTÉRAL ISO dans la requête comme avant (valeurs
 * {@link LocalDate}, jamais une saisie libre), {@code AVG} rendu en {@code Double} par le pilote (KB-SEA-87 : précision dépendante du SGBD,
 * non corrigeable en code, seules 10 décimales sont garanties). Corrigé le 2026-11-10 (lot L5a, ADR 0117) : tri déterministe date, création
 * connue d'abord puis identifiant, et clés des lignes de l'évolution du poids toutes en minuscules (KB-SEA-86).
 */
public class StatistiquesDesSeancesJdbc implements StatistiquesDesSeancesPubliees {

    /**
     * UF réelle : valeur saisie (ultrafiltration_ml) ou, à défaut, perte de poids per-dialytique × 1000.
     */
    private static final String UF_REELLE_EXPR =
            "COALESCE(vp.ultrafiltration_ml, (vp.poids_avant_kg - vp.poids_apres_kg) * 1000)";

    /**
     * Poids sec cible en vigueur le jour de la séance : dernière prescription médicale portant un poids sec, datée au plus tard du jour de
     * la séance.
     */
    private static final String POIDS_SEC_A_LA_SEANCE_EXPR =
            "(SELECT pm.poids_sec_cible_kg FROM prescriptions_medicales pm "
                    + "WHERE pm.patient_id = s.patient_id AND pm.center_id = s.center_id "
                    + "AND pm.poids_sec_cible_kg IS NOT NULL AND pm.date_prescription <= s.date_seance "
                    + "ORDER BY pm.date_prescription DESC, pm.updated_at DESC NULLS LAST, pm.id DESC LIMIT 1)";   // L8 : colonne nullable, NULLS explicite + identifiant

    /** Ordre déterministe (KB-SEA-86) : date, création connue d'abord (la plus ancienne créée), puis identifiant. */
    private static final String ORDRE_DES_SEANCES = " ORDER BY s.date_seance, s.created_at NULLS LAST, s.id";

    private final JdbcTemplate jdbc;

    public StatistiquesDesSeancesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String buildSeanceDateFilter(LocalDate from, LocalDate to) {
        if (from != null && to != null) return " AND s.date_seance BETWEEN '" + from + "' AND '" + to + "'";
        if (from != null) return " AND s.date_seance >= '" + from + "'";
        if (to != null) return " AND s.date_seance <= '" + to + "'";
        return "";
    }

    private static String baseFrom(LocalDate from, LocalDate to) {
        return " FROM seances s LEFT JOIN volet_paramedical vp ON vp.seance_id = s.id AND vp.center_id = s.center_id "
                + "WHERE s.center_id = ? AND s.patient_id = ? AND s.statut IN ('VALIDEE','SIGNEE','FACTUREE') "
                + buildSeanceDateFilter(from, to);
    }

    @Override
    public long nombreDeSeances(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        Long value = jdbc.queryForObject("SELECT COUNT(1)" + baseFrom(du, au), Long.class, centre, patient);
        return value != null ? value : 0L;
    }

    @Override
    public Double moyenneDuPoidsAvant(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return jdbc.queryForObject("SELECT AVG(vp.poids_avant_kg)" + baseFrom(du, au), Double.class, centre, patient);
    }

    @Override
    public Double moyenneDuPoidsApres(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return jdbc.queryForObject("SELECT AVG(vp.poids_apres_kg)" + baseFrom(du, au), Double.class, centre, patient);
    }

    @Override
    public Double moyenneDeLUltrafiltration(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return jdbc.queryForObject("SELECT AVG(" + UF_REELLE_EXPR + ")" + baseFrom(du, au), Double.class, centre, patient);
    }

    @Override
    public List<Map<String, Object>> evolutionDuPoids(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        List<Map<String, Object>> lignes = jdbc.queryForList(
                "SELECT s.date_seance, vp.poids_avant_kg, vp.poids_apres_kg, "
                        + POIDS_SEC_A_LA_SEANCE_EXPR + " AS poids_sec_cible_kg, "
                        + UF_REELLE_EXPR + " AS uf_reelle_ml"
                        + baseFrom(du, au)
                        + ORDRE_DES_SEANCES,
                centre, patient
        );
        // KB-SEA-86 corrigé (ADR 0117) : une seule casse de clé, les minuscules (contrat de production, PostgreSQL), quel que soit le SGBD
        return lignes.stream().map(StatistiquesDesSeancesJdbc::enMinuscules).toList();
    }

    private static Map<String, Object> enMinuscules(Map<String, Object> ligne) {
        Map<String, Object> normalisee = new LinkedHashMap<>();
        ligne.forEach((cle, valeur) -> normalisee.put(cle.toLowerCase(Locale.ROOT), valeur));
        return normalisee;
    }

    @Override
    public List<TensionsDeSeancePubliees> evolutionDeLaTension(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return jdbc.query(
                "SELECT s.date_seance, vp.ta_avant, vp.ta_apres"
                        + baseFrom(du, au)
                        + ORDRE_DES_SEANCES,
                (rs, rowNum) -> {
                    Date date = rs.getDate("date_seance");
                    return new TensionsDeSeancePubliees(date == null ? null : date.toLocalDate(), rs.getString("ta_avant"),
                            rs.getString("ta_apres"));
                },
                centre, patient
        );
    }
}
