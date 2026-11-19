package com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.etat.PagesDeLEtatDuStock;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.FiltresDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.FiltresDePage.FiltreNumerique;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.ArticleValorise;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * État du stock paginé en SQL (table {@code articles}, articles actifs du centre) : {@code COUNT(*)} puis
 * {@code ORDER BY … LIMIT ? OFFSET ?} avec les mêmes {@code WHERE}. La valeur est l'expression {@code quantité × PMP} (pour trier et
 * filtrer) et se lit comme la liste existante ({@code quantité.multiply(pmp)} en Java : même échelle).
 */
public class EtatDuStockPagineJdbc implements PagesDeLEtatDuStock {

    private static final String QUANTITE = "COALESCE(stock_quantity, 0)";
    private static final String PMP = "COALESCE(pmp_courant, 0)";
    private static final String VALEUR = "(" + QUANTITE + " * " + PMP + ")";
    private static final Map<String, String> COLONNES = Map.of("code", "code", "libelle", "LOWER(libelle)", "quantite", QUANTITE,
            "pmp", PMP, "valeur", VALEUR);

    private final JdbcTemplate jdbc;

    public EtatDuStockPagineJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static void comparer(StringBuilder where, List<Object> parametres, String expression, Optional<FiltreNumerique> filtre) {
        filtre.ifPresent(f -> {
            if (f.comparateur() == FiltresDePage.Comparateur.ENTRE) {
                where.append(" AND ").append(expression).append(" BETWEEN ? AND ?");
                parametres.add(f.valeur());
                parametres.add(f.borneHaute());
                return;
            }
            String op = switch (f.comparateur()) {
                case EGAL -> "=";
                case DIFFERENT -> "<>";
                case SUPERIEUR -> ">";
                case SUPERIEUR_OU_EGAL -> ">=";
                case INFERIEUR -> "<";
                case INFERIEUR_OU_EGAL -> "<=";
                case ENTRE -> throw new IllegalStateException("ENTRE traite ci-dessus");
            };
            where.append(" AND ").append(expression).append(' ').append(op).append(" ?");
            parametres.add(f.valeur());
        });
    }

    @Override
    public PageDeResultats<ArticleValorise> page(CentreId centre, Filtres filtres, DemandeDePage demande) {
        StringBuilder where = new StringBuilder(" FROM articles WHERE center_id = ? AND active = TRUE");
        List<Object> parametres = new ArrayList<>(List.of(centre.valeur()));
        filtres.code().ifPresent(t -> {
            where.append(" AND LOWER(code) LIKE ? ESCAPE '\\'");
            parametres.add(SqlDePage.contient(t));
        });
        filtres.libelle().ifPresent(t -> {
            where.append(" AND LOWER(libelle) LIKE ? ESCAPE '\\'");
            parametres.add(SqlDePage.contient(t));
        });
        comparer(where, parametres, QUANTITE, filtres.quantite());
        comparer(where, parametres, PMP, filtres.pmp());
        comparer(where, parametres, VALEUR, filtres.valeur());
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, parametres.toArray());
        List<Object> pagines = new ArrayList<>(parametres);
        pagines.add(demande.taille());
        pagines.add(demande.decalage());
        List<ArticleValorise> items = jdbc.query("SELECT id, code, libelle, unite, " + QUANTITE + " AS qte, " + PMP + " AS pmp" + where
                        + SqlDePage.ordre(demande, COLONNES, "code, id") + SqlDePage.limite(),
                (rs, i) -> {
                    BigDecimal quantite = rs.getBigDecimal("qte");
                    BigDecimal pmp = rs.getBigDecimal("pmp");
                    return new ArticleValorise(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("libelle"),
                            rs.getString("unite"), quantite, pmp, quantite.multiply(pmp));
                }, pagines.toArray());
        return new PageDeResultats<>(items, total == null ? 0 : total, demande.page(), demande.taille());
    }
}
