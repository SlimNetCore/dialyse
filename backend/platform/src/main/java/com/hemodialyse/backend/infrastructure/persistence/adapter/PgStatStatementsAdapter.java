package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.application.supervision.RequeteStatistique;
import com.hemodialyse.backend.application.supervision.StatutStatistiques;
import com.hemodialyse.backend.application.supervision.TriRequetes;
import com.hemodialyse.backend.application.supervision.port.StatistiquesRequetesPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Lit {@code pg_stat_statements} (extension créée par la migration Flyway V30, chargée par le serveur via
 * {@code shared_preload_libraries}). En développement local (H2) ou si l'extension n'est pas active, le statut le dit
 * sans lever d'erreur : l'écran du propriétaire affiche alors la raison.
 */
@Component
public class PgStatStatementsAdapter implements StatistiquesRequetesPort {

    static final int TEXTE_MAX = 2000;

    /**
     * Base courante seulement ; on exclut la mesure elle-même pour ne pas polluer le classement.
     */
    private static final String FILTRE = "dbid = (SELECT oid FROM pg_database WHERE datname = current_database()) "
            + "AND query NOT ILIKE '%pg_stat_statements%'";

    private final JdbcTemplate jdbc;

    public PgStatStatementsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Colonne triée : liste blanche fermée (jamais une valeur venue du client dans le SQL).
     */
    static String colonneDeTri(TriRequetes tri) {
        return switch (tri) {
            case TEMPS_TOTAL -> "total_exec_time";
            case TEMPS_MOYEN -> "mean_exec_time";
            case APPELS -> "calls";
        };
    }

    static String tronquer(String requete) {
        if (requete == null) {
            return "";
        }
        return requete.length() <= TEXTE_MAX ? requete : requete.substring(0, TEXTE_MAX) + "…";
    }

    @Override
    public StatutStatistiques statut() {
        if (!estPostgres()) {
            return StatutStatistiques.indisponible("BASE_NON_POSTGRESQL");
        }
        Integer installee = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'pg_stat_statements'", Integer.class);
        if (installee == null || installee == 0) {
            return StatutStatistiques.indisponible("EXTENSION_ABSENTE");
        }
        double tempsTotal;
        try {
            Double somme = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(total_exec_time), 0) FROM pg_stat_statements WHERE " + FILTRE, Double.class);
            tempsTotal = somme == null ? 0 : somme;
        } catch (DataAccessException e) {
            // extension créée mais bibliothèque non préchargée : la vue existe, sa lecture échoue
            return StatutStatistiques.indisponible("PRELOAD_ABSENT");
        }
        return new StatutStatistiques(true, null, derniereReinitialisation(), tempsTotal);
    }

    @Override
    public PagedResult<RequeteStatistique> classement(TriRequetes tri, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM pg_stat_statements WHERE " + FILTRE, Long.class);
        List<RequeteStatistique> lignes = jdbc.query(
                "SELECT queryid, query, calls, total_exec_time, mean_exec_time, max_exec_time, rows, "
                        + "shared_blks_hit, shared_blks_read, temp_blks_written "
                        + "FROM pg_stat_statements WHERE " + FILTRE
                        + " ORDER BY " + colonneDeTri(tri) + " DESC, queryid LIMIT ? OFFSET ?",
                (rs, i) -> new RequeteStatistique(
                        rs.getObject("queryid") == null ? "" : String.valueOf(rs.getLong("queryid")),
                        tronquer(rs.getString("query")),
                        rs.getLong("calls"),
                        rs.getDouble("total_exec_time"),
                        rs.getDouble("mean_exec_time"),
                        rs.getDouble("max_exec_time"),
                        rs.getLong("rows"),
                        rs.getLong("shared_blks_hit"),
                        rs.getLong("shared_blks_read"),
                        rs.getLong("temp_blks_written")),
                size, (long) page * size);
        return PagedResult.of(lignes, total == null ? 0 : total, page, size);
    }

    @Override
    public void reinitialiser() {
        jdbc.execute("SELECT pg_stat_statements_reset()");
    }

    private boolean estPostgres() {
        String produit = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        return produit != null && produit.toLowerCase().contains("postgres");
    }

    /**
     * Disponible à partir de PostgreSQL 14 ; sinon la date reste inconnue.
     */
    private Instant derniereReinitialisation() {
        try {
            Timestamp t = jdbc.queryForObject("SELECT stats_reset FROM pg_stat_statements_info", Timestamp.class);
            return t == null ? null : t.toInstant();
        } catch (DataAccessException e) {
            return null;
        }
    }
}
