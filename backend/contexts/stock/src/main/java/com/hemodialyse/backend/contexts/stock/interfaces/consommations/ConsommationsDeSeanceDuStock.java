package com.hemodialyse.backend.contexts.stock.interfaces.consommations;

import com.hemodialyse.backend.contexts.stock.application.consommations.GestionDesConsommations;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter entrant du langage publié {@link ConsommationsDeSeance} : la consommation de séance imputée sur la sortie pour séances (sans
 * sortie de stock). Appelé par la Séance, par l'aiguillage de la plateforme quand le centre a activé la sortie pour séances.
 * Lectures historiques (S9) via JDBC sur les tables du contexte.
 */
public class ConsommationsDeSeanceDuStock implements ConsommationsDeSeance {
    private final GestionDesConsommations consommations;
    private final JdbcTemplate jdbc;

    public ConsommationsDeSeanceDuStock(GestionDesConsommations consommations, JdbcTemplate jdbc) {
        this.consommations = consommations;
        this.jdbc = jdbc;
    }

    public ConsommationsDeSeanceDuStock(GestionDesConsommations consommations) {
        this(consommations, null);
    }

    @Override
    public void ajouter(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, UUID article, BigDecimal quantite, String auteur) {
        consommations.ajouter(new CentreId(centre), seance, patient, dateSeance, article, quantite);
    }

    @Override
    public void fixer(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, UUID article, BigDecimal quantite, String auteur) {
        consommations.fixer(new CentreId(centre), seance, patient, dateSeance, article, quantite);
    }

    @Override
    public void annuler(UUID centre, UUID seance, String auteur) {
        consommations.annuler(new CentreId(centre), seance);
    }

    @Override
    public void administrer(UUID centre, UUID seance, UUID patient, LocalDate jour, UUID article, BigDecimal quantite, String auteur) {
        consommations.ajouter(new CentreId(centre), seance, patient, jour, article, quantite);
    }

    @Override
    public void enregistrerALaValidation(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, List<Ligne> lignes,
                                         String auteur) {
        for (Ligne ligne : lignes) {
            consommations.ajouter(new CentreId(centre), seance, patient, dateSeance, ligne.article(), ligne.quantite());
        }
    }

    @Override
    public List<LigneHistorique> historique(UUID centre, UUID seance) {
        if (jdbc == null) return List.of();
        List<LigneHistorique> result = new ArrayList<>();
        result.addAll(jdbc.query("""
                        SELECT sm.article_id AS article_id, a.code AS article_code, a.libelle AS article_libelle,
                               a.unite AS article_unite, sm.prix_unitaire AS valeur_unitaire,
                               SUM(sm.quantite) AS quantite,
                               SUM(COALESCE(sm.quantite, 0) * COALESCE(sm.prix_unitaire, 0)) AS total_valorise
                        FROM stock_movements sm
                        INNER JOIN articles a ON a.id = sm.article_id AND a.center_id = sm.center_id
                        WHERE sm.center_id = ? AND sm.seance_id = ? AND sm.mouvement_type = 'SORTIE'
                        GROUP BY sm.article_id, a.code, a.libelle, a.unite, sm.prix_unitaire
                        ORDER BY a.code
                        """,
                (rs, rowNum) -> new LigneHistorique(
                        rs.getObject("article_id", UUID.class), rs.getString("article_code"),
                        rs.getString("article_libelle"), rs.getString("article_unite"),
                        rs.getBigDecimal("quantite"), rs.getBigDecimal("valeur_unitaire"),
                        rs.getBigDecimal("total_valorise")),
                centre, seance));
        result.addAll(jdbc.query("""
                        SELECT i.article_id AS article_id, a.code AS article_code, a.libelle AS article_libelle,
                               a.unite AS article_unite, l.valeur_unitaire AS valeur_unitaire,
                               SUM(i.quantite) AS quantite,
                               SUM(i.quantite * COALESCE(l.valeur_unitaire, 0)) AS total_valorise
                        FROM consommations_de_seance c
                        INNER JOIN consommations_de_seance_imputations i ON i.seance_id = c.seance_id
                        INNER JOIN articles a ON a.id = i.article_id AND a.center_id = c.center_id
                        LEFT JOIN sorties_pour_seances_lignes l ON l.sortie_id = c.sortie_id
                                                              AND l.article_id = i.article_id AND l.lot_id = i.lot_id
                        WHERE c.center_id = ? AND c.seance_id = ?
                        GROUP BY i.article_id, a.code, a.libelle, a.unite, l.valeur_unitaire
                        ORDER BY a.code
                        """,
                (rs, rowNum) -> new LigneHistorique(
                        rs.getObject("article_id", UUID.class), rs.getString("article_code"),
                        rs.getString("article_libelle"), rs.getString("article_unite"),
                        rs.getBigDecimal("quantite"), rs.getBigDecimal("valeur_unitaire"),
                        rs.getBigDecimal("total_valorise")),
                centre, seance));
        return result;
    }

    @Override
    public List<LigneHistorique> sortiesHistoriques(UUID centre, UUID seance) {
        if (jdbc == null) return List.of();
        return jdbc.query("""
                        SELECT bsl.article_id AS article_id, a.code AS article_code, a.libelle AS article_libelle,
                               a.unite AS article_unite, bsl.quantite AS quantite, bsl.prix_unitaire AS valeur_unitaire,
                               (bsl.quantite * COALESCE(bsl.prix_unitaire, 0)) AS total_valorise
                        FROM bons_sortie_lignes bsl
                        INNER JOIN bons_sortie bs ON bs.id = bsl.bon_sortie_id
                        INNER JOIN articles a ON a.id = bsl.article_id AND a.center_id = bs.center_id
                        WHERE bs.center_id = ? AND bs.seance_id = ?
                        ORDER BY a.code
                        """,
                (rs, rowNum) -> new LigneHistorique(
                        rs.getObject("article_id", UUID.class), rs.getString("article_code"),
                        rs.getString("article_libelle"), rs.getString("article_unite"),
                        rs.getBigDecimal("quantite"), rs.getBigDecimal("valeur_unitaire"),
                        rs.getBigDecimal("total_valorise")),
                centre, seance);
    }

    @Override
    public List<SortieParDate> sortiesParDate(UUID centre, LocalDate date) {
        if (jdbc == null) return List.of();
        return jdbc.query("""
                        SELECT bsl.article_id AS article_id, a.code AS article_code, a.libelle AS article_libelle,
                               SUM(bsl.quantite) AS quantite_totale
                        FROM bons_sortie_lignes bsl
                        INNER JOIN bons_sortie bs ON bs.id = bsl.bon_sortie_id
                        INNER JOIN articles a ON a.id = bsl.article_id AND a.center_id = bs.center_id
                        WHERE bs.center_id = ? AND bs.date_sortie = ?
                        GROUP BY bsl.article_id, a.code, a.libelle
                        ORDER BY a.code ASC
                        """,
                (rs, rowNum) -> new SortieParDate(
                        rs.getObject("article_id", UUID.class), rs.getString("article_code"),
                        rs.getString("article_libelle"), rs.getBigDecimal("quantite_totale")),
                centre, java.sql.Date.valueOf(date));
    }
}