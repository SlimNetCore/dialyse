package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.domain.sortie.SortiesDuStock;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lots et mouvements de sortie en SQL (tables {@code lots}, {@code stock_movements}), dans la transaction de l'appelant ; les
 * articles sont verrouillés par {@code EcrituresDuStockJdbc} (même verrou de ligne que le legacy restant, S6b).
 */
public class SortiesDuStockJdbc implements SortiesDuStock {

    private static final String COLONNES_DU_LOT = "id, article_id, numero_lot, date_peremption, quantite_restante";

    private final JdbcTemplate jdbc;

    public SortiesDuStockJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static LotDuStock lotDe(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new LotDuStock(rs.getObject("id", UUID.class), rs.getObject("article_id", UUID.class), rs.getString("numero_lot"),
                rs.getObject("date_peremption", LocalDate.class), rs.getBigDecimal("quantite_restante"));
    }

    @Override
    public Optional<LotDuStock> lot(CentreId centre, UUID lotId) {
        return jdbc.query("SELECT " + COLONNES_DU_LOT + " FROM lots WHERE id = ? AND center_id = ?", (rs, i) -> lotDe(rs), lotId,
                centre.valeur()).stream().findFirst();
    }

    @Override
    public List<LotDuStock> lotsDisponibles(CentreId centre, UUID articleId) {
        return jdbc.query("SELECT " + COLONNES_DU_LOT + " FROM lots WHERE center_id = ? AND article_id = ? AND quantite_restante > 0 "
                + "ORDER BY date_peremption ASC NULLS LAST, created_at ASC", (rs, i) -> lotDe(rs), centre.valeur(), articleId);
    }

    @Override
    public void consommer(CentreId centre, UUID lotId, BigDecimal quantite) {
        jdbc.update("UPDATE lots SET quantite_restante = quantite_restante - ? WHERE id = ? AND center_id = ?", quantite, lotId,
                centre.valeur());
    }

    @Override
    public void restituer(CentreId centre, UUID lotId, BigDecimal quantite) {
        jdbc.update("UPDATE lots SET quantite_restante = quantite_restante + ? WHERE id = ? AND center_id = ?", quantite, lotId,
                centre.valeur());
    }

    @Override
    public void enregistrerSortie(CentreId centre, UUID articleId, UUID seanceId, UUID lotId, BigDecimal quantite, BigDecimal prix,
                                  String auteur, LocalDate jour, OffsetDateTime saisiLe) {
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, seance_id, lot_id, mouvement_type, quantite, "
                        + "prix_unitaire, created_by, created_at, saisi_le) VALUES (?, ?, ?, ?, ?, 'SORTIE', ?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre.valeur(), articleId, seanceId, lotId, quantite, prix, auteur,
                jour.atStartOfDay().atOffset(ZoneOffset.UTC), saisiLe);
    }

    @Override
    public void retirerLaSortie(CentreId centre, UUID seanceId, UUID articleId, UUID lotId, BigDecimal quantite,
                                OffsetDateTime saisiLe) {
        List<Mouvement> trouves = jdbc.query("SELECT id, inventaire_id FROM stock_movements WHERE center_id = ? AND seance_id = ? "
                        + "AND article_id = ? AND lot_id = ? AND mouvement_type = 'SORTIE' AND quantite = ? "
                        + "ORDER BY CASE WHEN saisi_le = ? THEN 0 ELSE 1 END, created_at, id LIMIT 1",
                (rs, i) -> new Mouvement(rs.getObject("id", UUID.class), rs.getObject("inventaire_id", UUID.class) != null),
                centre.valeur(), seanceId, articleId, lotId, quantite, saisiLe);
        for (Mouvement mouvement : trouves) {
            if (mouvement.cloture()) {
                throw new RefusDuReferentiel("STOCK_MOVEMENT_CLOSED",
                        "Ces consommations appartiennent à une période clôturée par inventaire : elles ne peuvent plus être modifiées.");
            }
            jdbc.update("DELETE FROM stock_movements WHERE id = ?", mouvement.id());
        }
    }

    private record Mouvement(UUID id, boolean cloture) {
    }
}

