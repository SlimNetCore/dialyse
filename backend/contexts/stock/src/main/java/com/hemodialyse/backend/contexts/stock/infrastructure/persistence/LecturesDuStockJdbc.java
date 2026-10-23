package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock;
import com.hemodialyse.backend.contexts.stock.domain.lectures.MouvementDeStock;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectures du Stock en SQL simple (tables {@code stock_movements}, {@code articles}, {@code lots}, {@code bons_*}), sans
 * aucune écriture. La borne de début d'une fenêtre est minuit UTC du premier jour (comportement conservé, KB-STK-27).
 */
public class LecturesDuStockJdbc implements LecturesDuStock {

    private static final String VALEUR_D_UN_MOUVEMENT =
            "ABS(sm.quantite) * COALESCE(sm.prix_unitaire, sm.pmp_apres, art.pmp_courant, 0)";

    private final JdbcTemplate jdbc;

    public LecturesDuStockJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Timestamp minuitUtc(LocalDate jour) {
        return Timestamp.from(jour.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Override
    public List<PointDeTendance> tendance(CentreId centre, LocalDate premierJour) {
        return jdbc.query("SELECT CAST(sm.created_at AS DATE) AS jour, "
                        + "COALESCE(SUM(CASE WHEN sm.mouvement_type = 'SORTIE' THEN -sm.quantite ELSE sm.quantite END), 0) AS quantite, "
                        + "COALESCE(SUM(" + VALEUR_D_UN_MOUVEMENT + "), 0) AS valeur "
                        + "FROM stock_movements sm JOIN articles art ON art.id = sm.article_id "
                        + "WHERE sm.center_id = ? AND sm.created_at >= ? AND sm.mouvement_type <> 'INVENTAIRE' "
                        + "GROUP BY CAST(sm.created_at AS DATE) ORDER BY jour ASC",
                (rs, i) -> new PointDeTendance(rs.getObject("jour", LocalDate.class), rs.getBigDecimal("quantite"),
                        rs.getBigDecimal("valeur")), centre.valeur(), minuitUtc(premierJour));
    }

    @Override
    public List<ArticleClasse> classement(CentreId centre, LocalDate premierJour, int limite, TriDuClassement tri) {
        String ordre = tri == TriDuClassement.QUANTITE
                ? "quantite DESC, valeur DESC, art.libelle ASC, art.id ASC"
                : "valeur DESC, quantite DESC, art.libelle ASC, art.id ASC";
        return jdbc.query("SELECT art.id AS article_id, art.code, art.libelle, art.unite, "
                        + "COALESCE(SUM(ABS(sm.quantite)), 0) AS quantite, "
                        + "COALESCE(SUM(" + VALEUR_D_UN_MOUVEMENT + "), 0) AS valeur "
                        + "FROM stock_movements sm JOIN articles art ON art.id = sm.article_id "
                        + "WHERE sm.center_id = ? AND sm.created_at >= ? AND sm.mouvement_type <> 'INVENTAIRE' "
                        + "GROUP BY art.id, art.code, art.libelle, art.unite ORDER BY " + ordre + " LIMIT ?",
                (rs, i) -> new ArticleClasse(rs.getObject("article_id", UUID.class), rs.getString("code"),
                        rs.getString("libelle"), rs.getString("unite"), rs.getBigDecimal("quantite"),
                        rs.getBigDecimal("valeur")), centre.valeur(), minuitUtc(premierJour), limite);
    }

    @Override
    public List<ArticleValorise> articlesActifsValorises(CentreId centre) {
        return jdbc.query("SELECT id, code, libelle, unite, COALESCE(stock_quantity, 0) AS qte, COALESCE(pmp_courant, 0) AS pmp "
                        + "FROM articles WHERE center_id = ? AND active = TRUE ORDER BY code",
                (rs, i) -> {
                    var quantite = rs.getBigDecimal("qte");
                    var pmp = rs.getBigDecimal("pmp");
                    return new ArticleValorise(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("libelle"),
                            rs.getString("unite"), quantite, pmp, quantite.multiply(pmp));
                }, centre.valeur());
    }

    @Override
    public List<AlerteDeStock> peremptions(CentreId centre, LocalDate auPlusTard) {
        return jdbc.query("SELECT lot.id AS lot_id, lot.numero_lot, lot.date_peremption, lot.quantite_restante, "
                        + "art.id AS article_id, art.libelle FROM lots lot JOIN articles art ON art.id = lot.article_id "
                        + "WHERE lot.center_id = ? AND lot.quantite_restante > 0 AND lot.date_peremption IS NOT NULL "
                        + "AND lot.date_peremption <= ? ORDER BY lot.date_peremption ASC, lot.numero_lot ASC, lot.id ASC",
                (rs, i) -> new AlerteDeStock("PEREMPTION", rs.getObject("article_id", UUID.class), rs.getString("libelle"),
                        rs.getObject("lot_id", UUID.class), rs.getString("numero_lot"),
                        rs.getObject("date_peremption", LocalDate.class), rs.getBigDecimal("quantite_restante"), null),
                centre.valeur(), auPlusTard);
    }

    @Override
    public List<AlerteDeStock> ruptures(CentreId centre) {
        return jdbc.query("SELECT id, libelle, COALESCE(stock_quantity, 0) AS qte, COALESCE(seuil_alerte, 0) AS seuil "
                        + "FROM articles WHERE center_id = ? AND active = TRUE "
                        + "AND COALESCE(stock_quantity, 0) <= COALESCE(seuil_alerte, 0) ORDER BY libelle, id",
                (rs, i) -> {
                    var quantite = rs.getBigDecimal("qte");
                    return new AlerteDeStock(quantite.signum() <= 0 ? "RUPTURE" : "SEUIL", rs.getObject("id", UUID.class),
                            rs.getString("libelle"), null, null, null, quantite, rs.getBigDecimal("seuil"));
                }, centre.valeur());
    }

    @Override
    public List<LigneDeTracabilite> tracabilite(CentreId centre, UUID lotId) {
        return jdbc.query("SELECT lot.id AS lot_id, lot.numero_lot, art.id AS article_id, art.libelle AS article_libelle, "
                        + "bs.seance_id, bs.patient_id, bs.date_sortie, bsl.quantite "
                        + "FROM bons_sortie_lignes bsl JOIN bons_sortie bs ON bs.id = bsl.bon_sortie_id "
                        + "JOIN lots lot ON lot.id = bsl.lot_id JOIN articles art ON art.id = bsl.article_id "
                        + "WHERE bsl.lot_id = ? AND bs.center_id = ? ORDER BY bs.date_sortie DESC, bs.reference DESC",
                (rs, i) -> new LigneDeTracabilite(rs.getObject("lot_id", UUID.class), rs.getString("numero_lot"),
                        rs.getObject("article_id", UUID.class), rs.getString("article_libelle"),
                        rs.getObject("seance_id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getObject("date_sortie", LocalDate.class), rs.getBigDecimal("quantite")),
                lotId, centre.valeur());
    }

    @Override
    public List<MouvementDeStock> mouvementsDeLArticle(CentreId centre, UUID articleId) {
        List<MouvementDeStock> mouvements = new ArrayList<>(jdbc.query(
                "SELECT id, article_id, lot_id, seance_id, mouvement_type, quantite, prix_unitaire, created_at, saisi_le "
                        + "FROM stock_movements WHERE center_id = ? AND article_id = ? AND inventaire_id IS NULL "
                        + "ORDER BY created_at, id",
                (rs, i) -> new MouvementDeStock(rs.getObject("id", UUID.class), rs.getObject("article_id", UUID.class),
                        rs.getObject("lot_id", UUID.class), rs.getObject("seance_id", UUID.class),
                        MouvementDeStock.Type.valueOf(rs.getString("mouvement_type")), rs.getBigDecimal("quantite"),
                        rs.getBigDecimal("prix_unitaire"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("saisi_le", OffsetDateTime.class)), centre.valeur(), articleId));
        mouvements.sort(MouvementDeStock.CHRONOLOGIE);
        return mouvements;
    }

    @Override
    public Optional<String> referenceDeReception(CentreId centre, UUID lotId) {
        return jdbc.queryForList("SELECT br.reference FROM lots l JOIN bons_reception br ON br.id = l.bon_reception_id "
                        + "AND br.center_id = l.center_id WHERE l.id = ? AND l.center_id = ?", String.class, lotId, centre.valeur())
                .stream().findFirst();
    }

    @Override
    public Optional<String> referenceDeSortie(CentreId centre, UUID seanceId, UUID lotId, UUID articleId) {
        return jdbc.queryForList("SELECT bs.reference FROM bons_sortie bs JOIN bons_sortie_lignes l ON l.bon_sortie_id = bs.id "
                        + "WHERE bs.center_id = ? AND bs.seance_id = ? AND l.lot_id = ? AND l.article_id = ? "
                        + "ORDER BY bs.created_at, bs.reference", String.class, centre.valeur(), seanceId, lotId, articleId)
                .stream().findFirst();
    }
}

