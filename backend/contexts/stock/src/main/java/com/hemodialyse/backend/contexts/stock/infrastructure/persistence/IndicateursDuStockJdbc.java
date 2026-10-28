package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.IndicateursDuStock;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Indicateurs agrégés du stock par centre, lus directement dans les tables du contexte (S9). Seul propriétaire de la lecture
 * pour le pilotage direction.
 */
public class IndicateursDuStockJdbc implements IndicateursDuStock {

    private final JdbcTemplate jdbc;

    public IndicateursDuStockJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, Indicateur> pourCentres(List<UUID> centres, LocalDate aujourdhui) {
        Map<UUID, Indicateur> resultat = new HashMap<>();
        if (centres == null || centres.isEmpty()) return resultat;
        String in = String.join(",", java.util.Collections.nCopies(centres.size(), "?"));
        jdbc.query("SELECT center_id, COUNT(*) AS actifs, "
                        + "SUM(CASE WHEN seuil_alerte > 0 AND stock_quantity <= seuil_alerte THEN 1 ELSE 0 END) AS sous, "
                        + "COALESCE(SUM(stock_quantity * COALESCE(pmp_courant, 0)), 0) AS valeur "
                        + "FROM articles WHERE center_id IN (" + in + ") AND active = TRUE GROUP BY center_id",
                rs -> {
                    UUID c = rs.getObject("center_id", UUID.class);
                    resultat.put(c, new Indicateur(rs.getLong("actifs"), rs.getLong("sous"),
                            rs.getBigDecimal("valeur"), 0, 0));
                }, centres.toArray());
        Date now = Date.valueOf(aujourdhui);
        Date horizon = Date.valueOf(aujourdhui.plusDays(90));
        List<Object> args = new java.util.ArrayList<>(List.of(now, now, horizon));
        args.addAll(centres);
        jdbc.query("SELECT center_id, "
                        + "SUM(CASE WHEN date_peremption < ? THEN 1 ELSE 0 END) AS perimes, "
                        + "SUM(CASE WHEN date_peremption >= ? AND date_peremption <= ? THEN 1 ELSE 0 END) AS proches "
                        + "FROM lots WHERE center_id IN (" + in + ") AND quantite_restante > 0 "
                        + "AND date_peremption IS NOT NULL GROUP BY center_id",
                rs -> {
                    UUID c = rs.getObject("center_id", UUID.class);
                    Indicateur base = resultat.getOrDefault(c, new Indicateur(0, 0, BigDecimal.ZERO, 0, 0));
                    resultat.put(c, new Indicateur(base.articlesActifs(), base.articlesSousSeuil(), base.valeurStock(),
                            rs.getLong("perimes"), rs.getLong("proches")));
                }, args.toArray());
        return resultat;
    }
}
