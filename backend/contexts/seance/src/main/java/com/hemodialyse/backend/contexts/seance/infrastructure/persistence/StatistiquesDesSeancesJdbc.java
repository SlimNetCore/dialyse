package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.PoidsSecDesPrescriptions;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.StatistiquesDesSeancesPubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lecture publiée des séances et de leur volet paramédical pour les statistiques du patient (ADR 0099, SE7e), lecture seule. Le poids sec
 * cible vient du Dossier médical par {@link PoidsSecDesPrescriptions} (LEC-PLN-GMAO c, ADR 0180) : plus aucune lecture de
 * {@code prescriptions_medicales} ici. Requêtes
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

    /** Ordre déterministe (KB-SEA-86) : date, création connue d'abord (la plus ancienne créée), puis identifiant. */
    private static final String ORDRE_DES_SEANCES = " ORDER BY s.date_seance, s.created_at NULLS LAST, s.id";

    private final JdbcTemplate jdbc;
    private final PoidsSecDesPrescriptions poidsSecs;

    public StatistiquesDesSeancesJdbc(JdbcTemplate jdbc, PoidsSecDesPrescriptions poidsSecs) {
        this.jdbc = jdbc;
        this.poidsSecs = poidsSecs;
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
                        + UF_REELLE_EXPR + " AS uf_reelle_ml"
                        + baseFrom(du, au)
                        + ORDRE_DES_SEANCES,
                centre, patient
        );
        if (lignes.isEmpty()) return List.of();
        // LEC-PLN-GMAO (c), ADR 0180 : le poids sec en vigueur le jour de la séance est la PREMIÈRE prescription (la plus récente) datée au
        // plus tard de ce jour, lue dans le Dossier médical ; l'ordre des séances et des colonnes est celui de l'ancienne requête
        List<PoidsSecDesPrescriptions.PoidsSecPrescrit> prescrits = poidsSecs.poidsSecsCiblesPrescrits(centre, patient);
        // KB-SEA-86 corrigé (ADR 0117) : une seule casse de clé, les minuscules (contrat de production, PostgreSQL), quel que soit le SGBD
        return lignes.stream().map(ligne -> enMinuscules(ligne, prescrits)).toList();
    }

    private static Map<String, Object> enMinuscules(Map<String, Object> ligne, List<PoidsSecDesPrescriptions.PoidsSecPrescrit> prescrits) {
        Map<String, Object> normalisee = new LinkedHashMap<>();
        ligne.forEach((cle, valeur) -> normalisee.put(cle.toLowerCase(Locale.ROOT), valeur));
        Object jour = normalisee.get("date_seance");
        BigDecimal poidsSec = jour == null ? null : prescrits.stream()
                .filter(p -> !p.datePrescription().isAfter(((Date) jour).toLocalDate()))
                .map(PoidsSecDesPrescriptions.PoidsSecPrescrit::poidsSecCibleKg).findFirst().orElse(null);
        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("date_seance", normalisee.get("date_seance"));
        resultat.put("poids_avant_kg", normalisee.get("poids_avant_kg"));
        resultat.put("poids_apres_kg", normalisee.get("poids_apres_kg"));
        resultat.put("poids_sec_cible_kg", poidsSec);
        resultat.put("uf_reelle_ml", normalisee.get("uf_reelle_ml"));
        return resultat;
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
