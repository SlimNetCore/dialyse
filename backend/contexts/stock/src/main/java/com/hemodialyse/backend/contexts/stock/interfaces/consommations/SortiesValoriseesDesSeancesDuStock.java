package com.hemodialyse.backend.contexts.stock.interfaces.consommations;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.SortiesValoriseesDesSeances;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Adapter du langage publié {@link SortiesValoriseesDesSeances} (DOC5a, ADR 0193) sur {@code stock_movements} et {@code articles} : la
 * sous-requête de l'ancien gabarit du cahier de dialyse, reprise MOT POUR MOT (regroupement, signes, valorisation), restreinte à une séance.
 */
public class SortiesValoriseesDesSeancesDuStock implements SortiesValoriseesDesSeances {

    private final JdbcTemplate jdbc;

    public SortiesValoriseesDesSeancesDuStock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LigneDeSortie> pourLaSeance(UUID centre, UUID seance) {
        return jdbc.query("""
                        SELECT c.article_id AS article_id, ac.code AS article_code, ac.libelle AS article_libelle, ac.unite AS article_unite,
                               c.quantite AS quantite, c.prix_unitaire AS prix_unitaire, c.total_valorise AS total_valorise
                        FROM (SELECT sm.center_id AS center_id, sm.seance_id AS seance_id, sm.article_id AS article_id,
                                     sm.prix_unitaire AS prix_unitaire,
                                     SUM(CASE WHEN sm.mouvement_type = 'SORTIE' THEN sm.quantite ELSE -sm.quantite END) AS quantite,
                                     SUM(CASE WHEN sm.mouvement_type = 'SORTIE' THEN 1 ELSE -1 END * COALESCE(sm.quantite, 0) * COALESCE(sm.prix_unitaire, 0)) AS total_valorise
                                FROM stock_movements sm
                               WHERE sm.center_id = ? AND sm.seance_id = ? AND sm.mouvement_type IN ('SORTIE', 'ENTREE', 'AJUSTEMENT')
                               GROUP BY sm.center_id, sm.seance_id, sm.article_id, sm.prix_unitaire) c
                        LEFT JOIN articles ac ON ac.id = c.article_id AND ac.center_id = c.center_id
                        ORDER BY ac.code, c.prix_unitaire
                        """,
                (rs, n) -> new LigneDeSortie(rs.getObject("article_id", UUID.class), rs.getString("article_code"),
                        rs.getString("article_libelle"), rs.getString("article_unite"), rs.getBigDecimal("quantite"),
                        rs.getBigDecimal("prix_unitaire"), rs.getBigDecimal("total_valorise")),
                centre, seance);
    }
}
