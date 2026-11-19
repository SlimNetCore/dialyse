package com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.seances.PagesDesSortiesPourSeances;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.DemiJournee;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.LigneDePiece;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiePourSeances;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sorties pour séances paginées en SQL (tables {@code sorties_pour_seances}, {@code sorties_pour_seances_lignes}, jointure
 * {@code articles} du même contexte pour le libellé) : {@code COUNT(*)} puis {@code ORDER BY … LIMIT ? OFFSET ?} avec les mêmes
 * {@code WHERE}. Les lignes non rapprochées (consommé, écart nuls) se trient en dernier.
 */
public class SortiesPourSeancesPagineesJdbc implements PagesDesSortiesPourSeances {

    private static final String NOMBRE_DE_LIGNES = "(SELECT COUNT(*) FROM sorties_pour_seances_lignes l WHERE l.sortie_id = p.id)";
    private static final Map<String, String> TRIS_DES_PIECES = Map.of("reference", "p.reference", "jour", "p.jour", "statut",
            "p.statut", "lignes", NOMBRE_DE_LIGNES, "enregistreeLe", "p.enregistree_le");
    private static final Map<String, String> TRIS_DES_LIGNES = Map.of("article", "LOWER(a.libelle)", "sortie", "l.quantite_sortie",
            "consomme", "CASE WHEN l.quantite_consommee IS NULL THEN 1 ELSE 0 END, l.quantite_consommee",
            "ecart", "CASE WHEN l.ecart IS NULL THEN 1 ELSE 0 END, l.ecart");

    private final JdbcTemplate jdbc;

    public SortiesPourSeancesPagineesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static List<Object> pagines(List<Object> parametres, DemandeDePage demande) {
        List<Object> tous = new ArrayList<>(parametres);
        tous.add(demande.taille());
        tous.add(demande.decalage());
        return tous;
    }

    @Override
    public PageDeResultats<ResumeDePiece> pieces(CentreId centre, FiltresDesPieces filtres, DemandeDePage demande) {
        StringBuilder where = new StringBuilder(" FROM sorties_pour_seances p WHERE p.center_id = ?");
        List<Object> parametres = new ArrayList<>(List.of(centre.valeur()));
        filtres.reference().ifPresent(t -> {
            where.append(" AND LOWER(p.reference) LIKE ? ESCAPE '\\'");
            parametres.add(SqlDePage.contient(t));
        });
        if (!filtres.statuts().isEmpty()) {
            where.append(" AND p.statut IN (").append(String.join(", ", filtres.statuts().stream().map(s -> "?").toList())).append(')');
            filtres.statuts().forEach(s -> parametres.add(s.name()));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, parametres.toArray());
        List<ResumeDePiece> items = jdbc.query("SELECT p.id, p.center_id, p.reference, p.jour, p.salle, p.demi_journee, p.statut, "
                        + "p.enregistree_le, p.enregistree_par, p.validee_le, p.validee_par, " + NOMBRE_DE_LIGNES + " AS nombre" + where
                        + SqlDePage.ordre(demande, TRIS_DES_PIECES, "p.reference DESC, p.id") + SqlDePage.limite(),
                (rs, i) -> {
                    String demiJournee = rs.getString("demi_journee");
                    return new ResumeDePiece(rs.getObject("id", UUID.class), new CentreId(rs.getObject("center_id", UUID.class)),
                            rs.getString("reference"), rs.getObject("jour", LocalDate.class), rs.getString("salle"),
                            demiJournee == null ? null : DemiJournee.valueOf(demiJournee),
                            SortiePourSeances.Statut.valueOf(rs.getString("statut")), rs.getObject("enregistree_le", OffsetDateTime.class),
                            rs.getString("enregistree_par"), rs.getObject("validee_le", OffsetDateTime.class), rs.getString("validee_par"),
                            rs.getInt("nombre"));
                }, pagines(parametres, demande).toArray());
        return new PageDeResultats<>(items, total == null ? 0 : total, demande.page(), demande.taille());
    }

    @Override
    public boolean existe(CentreId centre, UUID piece) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM sorties_pour_seances WHERE id = ? AND center_id = ?", Long.class, piece,
                centre.valeur());
        return n != null && n > 0;
    }

    @Override
    public PageDeResultats<LigneDetaillee> lignes(CentreId centre, UUID piece, Optional<String> article, DemandeDePage demande) {
        StringBuilder where = new StringBuilder(" FROM sorties_pour_seances_lignes l "
                + "JOIN sorties_pour_seances p ON p.id = l.sortie_id "
                + "LEFT JOIN articles a ON a.id = l.article_id AND a.center_id = p.center_id "
                + "WHERE l.sortie_id = ? AND p.center_id = ?");
        List<Object> parametres = new ArrayList<>(List.of(piece, centre.valeur()));
        article.ifPresent(t -> {
            where.append(" AND (LOWER(a.code) LIKE ? ESCAPE '\\' OR LOWER(a.libelle) LIKE ? ESCAPE '\\')");
            parametres.add(SqlDePage.contient(t));
            parametres.add(SqlDePage.contient(t));
        });
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, parametres.toArray());
        List<LigneDetaillee> items = jdbc.query("SELECT l.id, l.article_id, l.lot_id, l.quantite_sortie, l.valeur_unitaire, "
                        + "l.quantite_consommee, l.ecart, l.motif, a.code AS article_code, a.libelle AS article_libelle" + where
                        + SqlDePage.ordre(demande, TRIS_DES_LIGNES, "a.code, l.id") + SqlDePage.limite(),
                (rs, i) -> new LigneDetaillee(new LigneDePiece(rs.getObject("id", UUID.class), rs.getObject("article_id", UUID.class),
                        rs.getObject("lot_id", UUID.class), rs.getBigDecimal("quantite_sortie"), rs.getBigDecimal("valeur_unitaire"),
                        rs.getBigDecimal("quantite_consommee"), rs.getBigDecimal("ecart"), rs.getString("motif")),
                        rs.getString("article_code"), rs.getString("article_libelle")),
                pagines(parametres, demande).toArray());
        return new PageDeResultats<>(items, total == null ? 0 : total, demande.page(), demande.taille());
    }
}
