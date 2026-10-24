package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.lectures.HistoriqueDesMouvements;
import com.hemodialyse.backend.contexts.stock.domain.lectures.MouvementDeStock;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Historique complet des mouvements ({@code stock_movements}), inventaires clôturés compris ; requête simple compatible H2 et
 * PostgreSQL, tri du rejeu en mémoire.
 */
public class HistoriqueDesMouvementsJdbc implements HistoriqueDesMouvements {

    private final NamedParameterJdbcTemplate jdbc;

    public HistoriqueDesMouvementsJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, List<MouvementDeStock>> parArticle(CentreId centre, Set<UUID> articles) {
        Map<UUID, List<MouvementDeStock>> resultat = new HashMap<>();
        if (articles == null || articles.isEmpty()) {
            return resultat;
        }
        jdbc.query("SELECT id, article_id, lot_id, seance_id, mouvement_type, quantite, prix_unitaire, created_at, saisi_le "
                        + "FROM stock_movements WHERE center_id = :centre AND article_id IN (:articles) ORDER BY created_at, id",
                new MapSqlParameterSource("centre", centre.valeur()).addValue("articles", articles),
                rs -> {
                    MouvementDeStock m = new MouvementDeStock(rs.getObject("id", UUID.class), rs.getObject("article_id", UUID.class),
                            rs.getObject("lot_id", UUID.class), rs.getObject("seance_id", UUID.class),
                            MouvementDeStock.Type.valueOf(rs.getString("mouvement_type")), rs.getBigDecimal("quantite"),
                            rs.getBigDecimal("prix_unitaire"), rs.getObject("created_at", OffsetDateTime.class),
                            rs.getObject("saisi_le", OffsetDateTime.class));
                    resultat.computeIfAbsent(m.articleId(), k -> new ArrayList<>()).add(m);
                });
        resultat.values().forEach(l -> l.sort(MouvementDeStock.CHRONOLOGIE));
        return resultat;
    }
}
