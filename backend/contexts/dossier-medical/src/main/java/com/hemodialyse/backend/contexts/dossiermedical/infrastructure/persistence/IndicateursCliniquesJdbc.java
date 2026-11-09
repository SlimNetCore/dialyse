package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.application.LecturesDesIndicateursCliniques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lectures brutes des indicateurs cliniques de la Direction sur les tables réelles (DM9, ADR 0101). Requêtes reprises MOT POUR MOT de
 * {@code platform/.../application/direction/DirectionIndicatorsQueryService.java} (retirées de la plateforme) : dernières valeurs
 * {@code :150-165} (colonnes en liste blanche : jamais un nom venant d'un client), porteurs positifs {@code :170-188}, observance
 * {@code :192-200}, greffes {@code :202-216}. Bornes de la période incluses.
 */
public class IndicateursCliniquesJdbc implements LecturesDesIndicateursCliniques {

    private final JdbcTemplate jdbc;

    public IndicateursCliniquesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Colonnes de {@code resultats_analyses} autorisées (liste blanche). */
    static String colonne(Cible cible) {
        return switch (cible) {
            case KT_V -> "kt_v_mensuel";
            case HEMOGLOBINE -> "hb_g_dl";
            case PHOSPHORE -> "phosphore_mg_dl";
            case PTH -> "pth_pg_ml";
            case ALBUMINE -> "albumine_g_dl";
        };
    }

    private static String marques(List<UUID> ids) {
        return String.join(",", Collections.nCopies(ids.size(), "?"));
    }

    @Override
    public Map<UUID, List<BigDecimal>> dernieresValeurs(Cible cible, List<UUID> centres, LocalDate du, LocalDate au) {
        String col = colonne(cible);
        String sql = "SELECT r.center_id, r." + col + " AS v FROM resultats_analyses r WHERE r.center_id IN (" + marques(centres) + ") "
                + "AND r." + col + " IS NOT NULL AND r.date_prelevement BETWEEN ? AND ? "
                + "AND r.date_prelevement = (SELECT MAX(r2.date_prelevement) FROM resultats_analyses r2 "
                + "WHERE r2.patient_id = r.patient_id AND r2.center_id = r.center_id AND r2." + col + " IS NOT NULL "
                + "AND r2.date_prelevement BETWEEN ? AND ?)";
        List<Object> args = new ArrayList<>(centres);
        Date from = Date.valueOf(du);
        Date to = Date.valueOf(au);
        args.addAll(List.of(from, to, from, to));
        Map<UUID, List<BigDecimal>> valeurs = new HashMap<>();
        jdbc.query(sql, rs -> {
            valeurs.computeIfAbsent(rs.getObject("center_id", UUID.class), k -> new ArrayList<>()).add(rs.getBigDecimal("v"));
        }, args.toArray());
        return valeurs;
    }

    @Override
    public Map<UUID, Long> porteursPositifs(MarqueurSerologique marqueur, List<UUID> centres, LocalDate au) {
        String sql = "SELECT s.center_id, COUNT(DISTINCT s.patient_id) AS n FROM serologies_patient s "
                + "WHERE s.center_id IN (" + marques(centres) + ") AND s.marqueur = ? AND s.resultat = 'POSITIF' "
                + "AND s.date_prelevement = (SELECT MAX(s2.date_prelevement) FROM serologies_patient s2 "
                + "WHERE s2.patient_id = s.patient_id AND s2.center_id = s.center_id AND s2.marqueur = s.marqueur "
                + "AND s2.date_prelevement <= ?) GROUP BY s.center_id";
        List<Object> args = new ArrayList<>(centres);
        args.addAll(List.of(marqueur.name(), Date.valueOf(au)));
        Map<UUID, Long> comptes = new HashMap<>();
        jdbc.query(sql, rs -> {
            comptes.put(rs.getObject("center_id", UUID.class), rs.getLong("n"));
        }, args.toArray());
        return comptes;
    }

    @Override
    public Map<UUID, Long> patientsEnRetardDObservance(List<UUID> centres) {
        Map<UUID, Long> comptes = new HashMap<>();
        jdbc.query("SELECT center_id, COUNT(DISTINCT patient_id) AS n FROM alertes_observance "
                        + "WHERE center_id IN (" + marques(centres) + ") AND resolved_at IS NULL AND type_alerte = 'RETARD_CONSTATE' "
                        + "GROUP BY center_id",
                rs -> {
                    comptes.put(rs.getObject("center_id", UUID.class), rs.getLong("n"));
                }, centres.toArray());
        return comptes;
    }

    @Override
    public Map<UUID, Greffes> greffes(List<UUID> centres, LocalDate du, LocalDate au) {
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(du), Date.valueOf(au)));
        args.addAll(centres);
        Map<UUID, Greffes> comptes = new HashMap<>();
        jdbc.query("SELECT center_id, "
                        + "SUM(CASE WHEN statut = 'INSCRIT_LISTE_ATTENTE' THEN 1 ELSE 0 END) AS attente, "
                        + "SUM(CASE WHEN statut = 'BILAN_EN_COURS' THEN 1 ELSE 0 END) AS bilan, "
                        + "SUM(CASE WHEN date_greffe BETWEEN ? AND ? THEN 1 ELSE 0 END) AS greffes "
                        + "FROM bilans_pre_greffe WHERE center_id IN (" + marques(centres) + ") GROUP BY center_id",
                rs -> {
                    comptes.put(rs.getObject("center_id", UUID.class), new Greffes(rs.getLong("attente"), rs.getLong("bilan"), rs.getLong("greffes")));
                }, args.toArray());
        return comptes;
    }
}
