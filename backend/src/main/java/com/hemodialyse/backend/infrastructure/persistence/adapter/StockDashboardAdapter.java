package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.AlerteStock;
import com.hemodialyse.backend.domain.stock.model.StockValoriseItem;
import com.hemodialyse.backend.domain.stock.model.TracabiliteItem;
import com.hemodialyse.backend.domain.stock.port.StockDashboardPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-side adapter for stock dashboards (JdbcTemplate, simple projections).
 */
@Component
public class StockDashboardAdapter implements StockDashboardPort {

    private final JdbcTemplate jdbc;

    public StockDashboardAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<StockValoriseItem> stockValorise(CenterId centerId) {
        return jdbc.query(
                "SELECT id, code, libelle, unite, " +
                        "COALESCE(stock_quantity, 0) AS qte, COALESCE(pmp_courant, 0) AS pmp " +
                        "FROM articles WHERE center_id = ? AND active = true ORDER BY libelle",
                (rs, i) -> {
                    BigDecimal qte = rs.getBigDecimal("qte");
                    BigDecimal pmp = rs.getBigDecimal("pmp");
                    BigDecimal valeur = qte.multiply(pmp);
                    return new StockValoriseItem(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("code"),
                            rs.getString("libelle"),
                            rs.getString("unite"),
                            qte, pmp, valeur);
                },
                centerId.value());
    }

    @Override
    public List<TracabiliteItem> tracabiliteByLot(CenterId centerId, UUID lotId) {
        return jdbc.query(
                "SELECT lot.id AS lot_id, lot.numero_lot, art.id AS article_id, art.libelle AS article_libelle, " +
                        "bs.seance_id, bs.patient_id, bs.date_sortie, bsl.quantite " +
                        "FROM bons_sortie_lignes bsl " +
                        "JOIN bons_sortie bs ON bs.id = bsl.bon_sortie_id " +
                        "JOIN lots lot ON lot.id = bsl.lot_id " +
                        "JOIN articles art ON art.id = bsl.article_id " +
                        "WHERE bsl.lot_id = ? AND bs.center_id = ? " +
                        "ORDER BY bs.date_sortie DESC",
                (rs, i) -> new TracabiliteItem(
                        UUID.fromString(rs.getString("lot_id")),
                        rs.getString("numero_lot"),
                        UUID.fromString(rs.getString("article_id")),
                        rs.getString("article_libelle"),
                        rs.getString("seance_id") != null ? UUID.fromString(rs.getString("seance_id")) : null,
                        rs.getString("patient_id") != null ? UUID.fromString(rs.getString("patient_id")) : null,
                        rs.getObject("date_sortie", LocalDate.class),
                        rs.getBigDecimal("quantite")),
                lotId, centerId.value());
    }

    @Override
    public List<AlerteStock> alertes(CenterId centerId, int joursAvantPeremption) {
        List<AlerteStock> alertes = new ArrayList<>();
        LocalDate threshold = LocalDate.now().plusDays(joursAvantPeremption);

        // Expiry alerts (lots still holding quantity, expiring before threshold)
        alertes.addAll(jdbc.query(
                "SELECT lot.id AS lot_id, lot.numero_lot, lot.date_peremption, lot.quantite_restante, " +
                        "art.id AS article_id, art.libelle " +
                        "FROM lots lot JOIN articles art ON art.id = lot.article_id " +
                        "WHERE lot.center_id = ? AND lot.quantite_restante > 0 " +
                        "AND lot.date_peremption IS NOT NULL AND lot.date_peremption <= ? " +
                        "ORDER BY lot.date_peremption ASC",
                (rs, i) -> new AlerteStock(
                        "PEREMPTION",
                        UUID.fromString(rs.getString("article_id")),
                        rs.getString("libelle"),
                        UUID.fromString(rs.getString("lot_id")),
                        rs.getString("numero_lot"),
                        rs.getObject("date_peremption", LocalDate.class),
                        rs.getBigDecimal("quantite_restante"),
                        null),
                centerId.value(), threshold));

        // Low stock / rupture alerts
        alertes.addAll(jdbc.query(
                "SELECT id, libelle, COALESCE(stock_quantity, 0) AS qte, COALESCE(seuil_alerte, 0) AS seuil " +
                        "FROM articles WHERE center_id = ? AND active = true " +
                        "AND COALESCE(stock_quantity, 0) <= COALESCE(seuil_alerte, 0) " +
                        "ORDER BY libelle",
                (rs, i) -> {
                    BigDecimal qte = rs.getBigDecimal("qte");
                    String type = qte.signum() <= 0 ? "RUPTURE" : "SEUIL";
                    return new AlerteStock(
                            type,
                            UUID.fromString(rs.getString("id")),
                            rs.getString("libelle"),
                            null, null, null,
                            qte,
                            rs.getBigDecimal("seuil"));
                },
                centerId.value()));

        return alertes;
    }
}

