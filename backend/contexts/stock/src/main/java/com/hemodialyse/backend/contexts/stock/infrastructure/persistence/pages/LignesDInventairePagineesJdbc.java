package com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.inventaire.PagesDesLignesDInventaire;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Feuille de comptage paginée en SQL (table {@code inventaire_lignes} de l'inventaire du centre) : {@code COUNT(*)} puis
 * {@code ORDER BY … LIMIT ? OFFSET ?} avec les mêmes {@code WHERE}. Lignes lues comme la consultation de l'inventaire (quantités
 * sans zéros inutiles) ; écart et valeur d'écart triés avec les lignes non comptées en dernier.
 */
public class LignesDInventairePagineesJdbc implements PagesDesLignesDInventaire {

    private static final String NON_COMPTEE_EN_DERNIER = "CASE WHEN quantite_comptee IS NULL THEN 1 ELSE 0 END, ";
    private static final String ECART = "(quantite_comptee - quantite_theorique)";
    private static final Map<String, String> COLONNES = Map.of(
            "position", "position_ligne",
            "article", "LOWER(article_code)",
            "lot", "LOWER(COALESCE(numero_lot, ''))",
            "peremption", "CASE WHEN date_peremption IS NULL THEN 1 ELSE 0 END, date_peremption",
            "theorique", "quantite_theorique",
            "comptee", NON_COMPTEE_EN_DERNIER + "quantite_comptee",
            "ecart", NON_COMPTEE_EN_DERNIER + ECART,
            "valeurEcart", NON_COMPTEE_EN_DERNIER + ECART + " * COALESCE(pmp, 0)");

    private final JdbcTemplate jdbc;

    public LignesDInventairePagineesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** « 3.000 » en base se lit 3, comme la consultation de l'inventaire. */
    private static BigDecimal sansZerosInutiles(BigDecimal valeur) {
        if (valeur == null) {
            return null;
        }
        BigDecimal reduite = valeur.stripTrailingZeros();
        return reduite.scale() < 0 ? reduite.setScale(0) : reduite;
    }

    @Override
    public boolean existe(CentreId centre, UUID inventaire) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM inventaires WHERE id = ? AND center_id = ?", Long.class, inventaire,
                centre.valeur());
        return n != null && n > 0;
    }

    @Override
    public PageDeResultats<LigneDInventaire> lignes(CentreId centre, UUID inventaire, Filtres filtres, DemandeDePage demande) {
        StringBuilder where = new StringBuilder(" FROM inventaire_lignes WHERE inventaire_id = ? AND center_id = ?");
        List<Object> parametres = new ArrayList<>(List.of(inventaire, centre.valeur()));
        switch (filtres.etat()) {
            case A_COMPTER -> where.append(" AND quantite_comptee IS NULL");
            case COMPTEES -> where.append(" AND quantite_comptee IS NOT NULL");
            case ECARTS -> where.append(" AND quantite_comptee IS NOT NULL AND quantite_comptee <> quantite_theorique");
            case TOUTES -> { }
        }
        filtres.recherche().ifPresent(t -> {
            where.append(" AND (LOWER(article_code) LIKE ? ESCAPE '\\' OR LOWER(article_libelle) LIKE ? ESCAPE '\\'"
                    + " OR LOWER(COALESCE(numero_lot, '')) LIKE ? ESCAPE '\\')");
            String motif = SqlDePage.contient(t);
            parametres.add(motif);
            parametres.add(motif);
            parametres.add(motif);
        });
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, parametres.toArray());
        List<Object> pagines = new ArrayList<>(parametres);
        pagines.add(demande.taille());
        pagines.add(demande.decalage());
        List<LigneDInventaire> items = jdbc.query("SELECT id, article_id, article_code, article_libelle, unite, lot_id, numero_lot, "
                        + "date_peremption, quantite_theorique, quantite_comptee, pmp, motif_ecart, compte_par, compte_le, ajoutee" + where
                        + SqlDePage.ordre(demande, COLONNES, "position_ligne, id") + SqlDePage.limite(),
                (rs, i) -> new LigneDInventaire(rs.getObject("id", UUID.class), rs.getObject("article_id", UUID.class),
                        rs.getString("article_code"), rs.getString("article_libelle"), rs.getString("unite"),
                        rs.getObject("lot_id", UUID.class), rs.getString("numero_lot"), rs.getObject("date_peremption", LocalDate.class),
                        sansZerosInutiles(rs.getBigDecimal("quantite_theorique")), sansZerosInutiles(rs.getBigDecimal("quantite_comptee")),
                        rs.getBigDecimal("pmp"), rs.getString("motif_ecart"), rs.getString("compte_par"),
                        rs.getObject("compte_le", OffsetDateTime.class), rs.getBoolean("ajoutee")),
                pagines.toArray());
        return new PageDeResultats<>(items, total == null ? 0 : total, demande.page(), demande.taille());
    }
}
