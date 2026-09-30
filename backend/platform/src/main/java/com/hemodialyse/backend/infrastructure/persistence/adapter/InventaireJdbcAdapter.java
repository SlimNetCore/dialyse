package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Inventaire;
import com.hemodialyse.backend.domain.stock.model.InventaireStatut;
import com.hemodialyse.backend.domain.stock.model.LigneInventaire;
import com.hemodialyse.backend.domain.stock.port.InventaireRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inventaires de stock (JDBC) : en-tête, lignes, clôture des mouvements. Toutes les requêtes sont filtrées sur le centre.
 */
@Component
public class InventaireJdbcAdapter implements InventaireRepositoryPort {

    private static final String HEADER = "id, center_id, reference, date_inventaire, statut, commentaire, created_by, "
            + "created_at, closed_by, closed_at";
    private static final RowMapper<LigneInventaire> LIGNE = (rs, i) -> {
        LigneInventaire l = new LigneInventaire();
        l.setId(rs.getObject("id", UUID.class));
        l.setArticleId(rs.getObject("article_id", UUID.class));
        l.setArticleCode(rs.getString("article_code"));
        l.setArticleLibelle(rs.getString("article_libelle"));
        l.setUnite(rs.getString("unite"));
        l.setLotId(rs.getObject("lot_id", UUID.class));
        l.setNumeroLot(rs.getString("numero_lot"));
        l.setDatePeremption(rs.getObject("date_peremption", LocalDate.class));
        l.setQuantiteTheorique(strip(rs.getBigDecimal("quantite_theorique")));
        l.setQuantiteComptee(strip(rs.getBigDecimal("quantite_comptee")));
        l.setPmp(rs.getBigDecimal("pmp"));
        l.setMotifEcart(rs.getString("motif_ecart"));
        l.setComptePar(rs.getString("compte_par"));
        l.setCompteLe(rs.getObject("compte_le", OffsetDateTime.class));
        l.setAjoutee(rs.getBoolean("ajoutee"));
        return l;
    };
    private final JdbcTemplate jdbc;

    public InventaireJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Inventaire header(java.sql.ResultSet rs, List<LigneInventaire> lignes) throws java.sql.SQLException {
        return Inventaire.restore(rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class),
                rs.getString("reference"), rs.getObject("date_inventaire", LocalDate.class),
                InventaireStatut.valueOf(rs.getString("statut")), rs.getString("commentaire"), rs.getString("created_by"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getString("closed_by"),
                rs.getObject("closed_at", OffsetDateTime.class), lignes);
    }

    private static BigDecimal strip(BigDecimal value) {
        if (value == null) return null;
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    @Override
    public Inventaire save(Inventaire inv) {
        UUID center = inv.getCenterId();
        int updated = jdbc.update("UPDATE inventaires SET statut = ?, commentaire = ?, closed_by = ?, closed_at = ? "
                        + "WHERE id = ? AND center_id = ?",
                inv.getStatut().name(), inv.getCommentaire(), inv.getClosedBy(), inv.getClosedAt(), inv.getId(), center);
        if (updated == 0) {
            jdbc.update("INSERT INTO inventaires (" + HEADER + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    inv.getId(), center, inv.getReference(), Date.valueOf(inv.getDateInventaire()), inv.getStatut().name(),
                    inv.getCommentaire(), inv.getCreatedBy(), inv.getCreatedAt(), inv.getClosedBy(), inv.getClosedAt());
        }
        jdbc.update("DELETE FROM inventaire_lignes WHERE inventaire_id = ? AND center_id = ?", inv.getId(), center);
        List<LigneInventaire> lignes = inv.getLignes();
        jdbc.batchUpdate("INSERT INTO inventaire_lignes (id, inventaire_id, center_id, position_ligne, article_id, article_code, "
                        + "article_libelle, unite, lot_id, numero_lot, date_peremption, quantite_theorique, quantite_comptee, pmp, "
                        + "motif_ecart, compte_par, compte_le, ajoutee) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                lignes, 200, (ps, l) -> {
                    ps.setObject(1, l.getId());
                    ps.setObject(2, inv.getId());
                    ps.setObject(3, center);
                    ps.setInt(4, lignes.indexOf(l));
                    ps.setObject(5, l.getArticleId());
                    ps.setString(6, l.getArticleCode());
                    ps.setString(7, l.getArticleLibelle());
                    ps.setString(8, l.getUnite());
                    ps.setObject(9, l.getLotId());
                    ps.setString(10, l.getNumeroLot());
                    ps.setObject(11, l.getDatePeremption() == null ? null : Date.valueOf(l.getDatePeremption()));
                    ps.setBigDecimal(12, l.getQuantiteTheorique());
                    ps.setBigDecimal(13, l.getQuantiteComptee());
                    ps.setBigDecimal(14, l.getPmp());
                    ps.setString(15, l.getMotifEcart());
                    ps.setString(16, l.getComptePar());
                    ps.setObject(17, l.getCompteLe());
                    ps.setBoolean(18, l.isAjoutee());
                });
        return inv;
    }

    @Override
    public Optional<Inventaire> findById(CenterId centerId, UUID id) {
        return jdbc.query("SELECT " + HEADER + " FROM inventaires WHERE id = ? AND center_id = ?",
                (rs, i) -> header(rs, lignes(id, centerId)), id, centerId.value()).stream().findFirst();
    }

    @Override
    public Optional<Inventaire> findEnCours(CenterId centerId) {
        return jdbc.queryForList("SELECT id FROM inventaires WHERE center_id = ? AND statut = 'EN_COURS'", UUID.class,
                centerId.value()).stream().findFirst().flatMap(id -> findById(centerId, id));
    }

    @Override
    public Optional<LocalDate> derniereCloture(CenterId centerId) {
        return Optional.ofNullable(jdbc.queryForObject("SELECT MAX(date_inventaire) FROM inventaires WHERE center_id = ? "
                + "AND statut = 'CLOTURE'", LocalDate.class, centerId.value()));
    }

    @Override
    public PagedResult<InventaireResume> findPaged(CenterId centerId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM inventaires WHERE center_id = ?", Long.class, centerId.value());
        List<InventaireResume> items = jdbc.query("SELECT i.id, i.reference, i.date_inventaire, i.statut, i.created_by, "
                        + "i.created_at, i.closed_by, i.closed_at, "
                        + "(SELECT COUNT(*) FROM inventaire_lignes l WHERE l.inventaire_id = i.id) AS nb_lignes, "
                        + "(SELECT COUNT(*) FROM inventaire_lignes l WHERE l.inventaire_id = i.id AND l.quantite_comptee IS NOT NULL) AS nb_comptees, "
                        + "(SELECT COUNT(*) FROM inventaire_lignes l WHERE l.inventaire_id = i.id AND l.quantite_comptee IS NOT NULL "
                        + " AND l.quantite_comptee <> l.quantite_theorique) AS nb_ecarts, "
                        + "(SELECT COALESCE(SUM((l.quantite_comptee - l.quantite_theorique) * COALESCE(l.pmp, 0)), 0) "
                        + " FROM inventaire_lignes l WHERE l.inventaire_id = i.id AND l.quantite_comptee IS NOT NULL) AS valeur_ecarts "
                        + "FROM inventaires i WHERE i.center_id = ? ORDER BY i.date_inventaire DESC, i.created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> new InventaireResume(rs.getObject("id", UUID.class), rs.getString("reference"),
                        rs.getObject("date_inventaire", LocalDate.class), InventaireStatut.valueOf(rs.getString("statut")),
                        rs.getInt("nb_lignes"), rs.getInt("nb_comptees"), rs.getInt("nb_ecarts"),
                        rs.getBigDecimal("valeur_ecarts").setScale(2, java.math.RoundingMode.HALF_UP),
                        rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getString("closed_by"), rs.getObject("closed_at", OffsetDateTime.class)),
                centerId.value(), size, (long) page * size);
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }

    @Override
    public boolean hasMovementsAfter(CenterId centerId, OffsetDateTime instant) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE center_id = ? AND created_at > ?",
                Long.class, centerId.value(), instant);
        return count != null && count > 0;
    }

    @Override
    public int cloturerMouvements(CenterId centerId, UUID inventaireId, OffsetDateTime jusqua) {
        return jdbc.update("UPDATE stock_movements SET inventaire_id = ? WHERE center_id = ? AND inventaire_id IS NULL "
                + "AND created_at <= ?", inventaireId, centerId.value(), jusqua);
    }

    private List<LigneInventaire> lignes(UUID inventaireId, CenterId centerId) {
        return jdbc.query("SELECT * FROM inventaire_lignes WHERE inventaire_id = ? AND center_id = ? ORDER BY position_ligne",
                LIGNE, inventaireId, centerId.value());
    }
}

