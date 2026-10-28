package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.StockInventorie;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tables {@code articles}, {@code lots} et {@code stock_movements} vues par l'inventaire : théorique figé, lots, mouvements, et
 * écritures de la clôture. Le gel des mouvements ({@code inventaire_id}) ne concerne que les articles inventoriés (KB-STK-50).
 */
public class StockInventorieJdbc implements StockInventorie {

    private final JdbcTemplate jdbc;

    public StockInventorieJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LigneDInventaire> theorique(CentreId centre) {
        List<ArticleActif> actifs = jdbc.query("SELECT id, code, libelle, unite, stock_quantity, pmp_courant FROM articles "
                        + "WHERE center_id = ? AND active = TRUE ORDER BY LOWER(COALESCE(libelle, '')), code, id",
                (rs, i) -> new ArticleActif(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("libelle"),
                        rs.getString("unite"), rs.getBigDecimal("stock_quantity"), rs.getBigDecimal("pmp_courant")), centre.valeur());
        Map<UUID, List<LotDisponible>> lots = new LinkedHashMap<>();
        jdbc.query("SELECT id, article_id, numero_lot, date_peremption, quantite_restante FROM lots WHERE center_id = ? "
                        + "AND quantite_restante > 0 ORDER BY (date_peremption IS NULL), date_peremption, created_at, id",
                rs -> {
                    lots.computeIfAbsent(rs.getObject("article_id", UUID.class), k -> new ArrayList<>()).add(new LotDisponible(
                            rs.getObject("id", UUID.class), rs.getString("numero_lot"), rs.getObject("date_peremption", LocalDate.class),
                            rs.getBigDecimal("quantite_restante")));
                }, centre.valeur());

        List<LigneDInventaire> lignes = new ArrayList<>();
        for (ArticleActif article : actifs) {
            List<LotDisponible> disponibles = lots.getOrDefault(article.id(), List.of());
            for (LotDisponible lot : disponibles) {
                lignes.add(LigneDInventaire.theorique(UUID.randomUUID(), article.id(), article.code(), article.libelle(),
                        article.unite(), lot.id(), lot.numero(), lot.peremption(), lot.restant(), article.pmp()));
            }
            BigDecimal stock = article.stock() != null ? article.stock() : BigDecimal.ZERO;
            if (disponibles.isEmpty() && stock.signum() > 0) {
                // stock sans lot (données anciennes) : ligne sans lot, un lot sera créé à la clôture
                lignes.add(LigneDInventaire.theorique(UUID.randomUUID(), article.id(), article.code(), article.libelle(),
                        article.unite(), null, null, null, stock, article.pmp()));
            }
        }
        return lignes;
    }

    @Override
    public Optional<LotConnu> lotDeLArticle(CentreId centre, UUID article, String numero) {
        return jdbc.query("SELECT id, date_peremption FROM lots WHERE center_id = ? AND article_id = ? AND UPPER(numero_lot) = UPPER(?) "
                        + "ORDER BY created_at, id",
                (rs, i) -> new LotConnu(rs.getObject("id", UUID.class), rs.getObject("date_peremption", LocalDate.class)),
                centre.valeur(), article, numero).stream().findFirst();
    }

    @Override
    public boolean mouvementsApres(CentreId centre, OffsetDateTime coupure) {
        Long nombre = jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE center_id = ? AND created_at > ?", Long.class,
                centre.valeur(), coupure);
        return nombre != null && nombre > 0;
    }

    @Override
    public boolean poserLeStockDuLot(CentreId centre, UUID lot, BigDecimal quantite) {
        return jdbc.update("UPDATE lots SET quantite_restante = ? WHERE id = ? AND center_id = ?", quantite, lot, centre.valeur()) > 0;
    }

    @Override
    public UUID creerLeLot(CentreId centre, UUID article, String numero, LocalDate datePeremption, BigDecimal quantite, BigDecimal pmp,
                           OffsetDateTime creeLe) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, date_peremption, quantite_initiale, quantite_restante, "
                        + "pmp, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", id, centre.valeur(), article, numero,
                datePeremption == null ? null : Date.valueOf(datePeremption), quantite, quantite, pmp, creeLe);
        return id;
    }

    @Override
    public void figerLesMouvements(CentreId centre, UUID inventaire, OffsetDateTime jusqua, Set<UUID> articles) {
        if (articles.isEmpty()) {
            return;
        }
        String marques = String.join(", ", java.util.Collections.nCopies(articles.size(), "?"));
        List<Object> parametres = new ArrayList<>(List.of(inventaire, centre.valeur(), jusqua));
        parametres.addAll(articles);
        jdbc.update("UPDATE stock_movements SET inventaire_id = ? WHERE center_id = ? AND inventaire_id IS NULL AND created_at <= ? "
                + "AND article_id IN (" + marques + ")", parametres.toArray());
    }

    @Override
    public void poserLeStockDeDepart(CentreId centre, UUID article, UUID lot, BigDecimal quantite, BigDecimal pmp, String par,
                                     OffsetDateTime coupure) {
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, lot_id, mouvement_type, quantite, prix_unitaire, "
                        + "created_by, created_at, saisi_le) VALUES (?, ?, ?, ?, 'INVENTAIRE', ?, ?, ?, ?, ?)", UUID.randomUUID(),
                centre.valeur(), article, lot, quantite, pmp, par, coupure, coupure);
    }

    private record ArticleActif(UUID id, String code, String libelle, String unite, BigDecimal stock, BigDecimal pmp) {
    }

    private record LotDisponible(UUID id, String numero, LocalDate peremption, BigDecimal restant) {
    }
}

