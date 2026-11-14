package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaires;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.PeriodeDeStock;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.StatutDInventaire;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.VerrouDuCentreFourni;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Tables {@code inventaires} et {@code inventaire_lignes} (migration V4). La référence {@code INV-nnnnn} vient du compteur
 * {@code INV} dans la transaction de l'inventaire ; une modification verrouille la ligne de l'inventaire ({@code FOR UPDATE}) ; les
 * opérations « centre entier » verrouillent la ligne du centre par le port {@link VerrouDuCentreFourni} (O4 lot B, ADR 0108).
 */
public class InventairesJdbc implements Inventaires {

    private static final String ENTETE = "id, center_id, reference, date_inventaire, statut, commentaire, created_by, created_at, "
            + "closed_by, closed_at";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final com.hemodialyse.backend.contexts.stock.publishedlanguage.NumerotationDesPiecesFournie sequence;

    private final VerrouDuCentreFourni verrouDuCentre;

    public InventairesJdbc(JdbcTemplate jdbc, TransactionTemplate transaction, VerrouDuCentreFourni verrouDuCentre,
                           com.hemodialyse.backend.contexts.stock.publishedlanguage.NumerotationDesPiecesFournie numerotation) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.verrouDuCentre = verrouDuCentre;
        this.sequence = numerotation;
    }

    /**
     * « 3.000 » en base se lit 3 (les quantités s'affichent sans zéros inutiles, comme dans le legacy).
     */
    private static java.math.BigDecimal sansZerosInutiles(java.math.BigDecimal valeur) {
        if (valeur == null) {
            return null;
        }
        java.math.BigDecimal reduite = valeur.stripTrailingZeros();
        return reduite.scale() < 0 ? reduite.setScale(0) : reduite;
    }

    private List<LigneDInventaire> lignesDe(UUID inventaire, UUID centre) {
        return jdbc.query("SELECT id, article_id, article_code, article_libelle, unite, lot_id, numero_lot, date_peremption, "
                        + "quantite_theorique, quantite_comptee, pmp, motif_ecart, compte_par, compte_le, ajoutee FROM inventaire_lignes "
                        + "WHERE inventaire_id = ? AND center_id = ? ORDER BY position_ligne",
                (rs, i) -> new LigneDInventaire(rs.getObject("id", UUID.class), rs.getObject("article_id", UUID.class),
                        rs.getString("article_code"), rs.getString("article_libelle"), rs.getString("unite"),
                        rs.getObject("lot_id", UUID.class), rs.getString("numero_lot"), rs.getObject("date_peremption", LocalDate.class),
                        sansZerosInutiles(rs.getBigDecimal("quantite_theorique")), sansZerosInutiles(rs.getBigDecimal("quantite_comptee")),
                        rs.getBigDecimal("pmp"),
                        rs.getString("motif_ecart"), rs.getString("compte_par"), rs.getObject("compte_le", OffsetDateTime.class),
                        rs.getBoolean("ajoutee")), inventaire, centre);
    }

    private Optional<Inventaire> lire(String sql, Object... parametres) {
        return jdbc.query(sql, (rs, i) -> Inventaire.reconstituer(rs.getObject("id", UUID.class),
                        new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("reference"),
                        rs.getObject("date_inventaire", LocalDate.class), StatutDInventaire.valueOf(rs.getString("statut")),
                        rs.getString("commentaire"), rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getString("closed_by"), rs.getObject("closed_at", OffsetDateTime.class), List.of()), parametres).stream().findFirst()
                .map(i -> Inventaire.reconstituer(i.id(), i.centre(), i.reference(), i.dateInventaire(), i.statut(), i.commentaire(),
                        i.creePar(), i.creeLe(), i.clotureePar(), i.clotureeLe(), lignesDe(i.id(), i.centre().valeur())));
    }

    private void remplacerLesLignes(Inventaire inventaire) {
        jdbc.update("DELETE FROM inventaire_lignes WHERE inventaire_id = ? AND center_id = ?", inventaire.id(),
                inventaire.centre().valeur());
        int[] position = {0};
        jdbc.batchUpdate("INSERT INTO inventaire_lignes (id, inventaire_id, center_id, position_ligne, article_id, article_code, "
                        + "article_libelle, unite, lot_id, numero_lot, date_peremption, quantite_theorique, quantite_comptee, pmp, "
                        + "motif_ecart, compte_par, compte_le, ajoutee) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                inventaire.lignes().stream().map(l -> new Object[]{l.id(), inventaire.id(), inventaire.centre().valeur(), position[0]++,
                        l.articleId(), l.articleCode(), l.articleLibelle(), l.unite(), l.lotId(), l.numeroLot(),
                        l.datePeremption() == null ? null : Date.valueOf(l.datePeremption()), l.quantiteTheorique(),
                        l.quantiteComptee(), l.pmp(), l.motifEcart(), l.comptePar(), l.compteLe(), l.ajoutee()}).toList());
    }

    @Override
    public Inventaire creer(CentreId centre, Function<String, Inventaire> fabrique) {
        return transaction.execute(statut -> {
            Inventaire inventaire = fabrique.apply(sequence.suivante(centre.valeur(), "INV", "INV-"));
            jdbc.update("INSERT INTO inventaires (" + ENTETE + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", inventaire.id(),
                    centre.valeur(), inventaire.reference(), Date.valueOf(inventaire.dateInventaire()), inventaire.statut().name(),
                    inventaire.commentaire(), inventaire.creePar(), inventaire.creeLe(), inventaire.clotureePar(),
                    inventaire.clotureeLe());
            remplacerLesLignes(inventaire);
            return inventaire;
        });
    }

    @Override
    public Optional<Inventaire> parId(CentreId centre, UUID id) {
        return lire("SELECT " + ENTETE + " FROM inventaires WHERE id = ? AND center_id = ?", id, centre.valeur());
    }

    @Override
    public Optional<Inventaire> enCours(CentreId centre) {
        return lire("SELECT " + ENTETE + " FROM inventaires WHERE center_id = ? AND statut = 'EN_COURS'", centre.valeur());
    }

    @Override
    public Optional<Inventaire> verrouiller(CentreId centre, UUID id) {
        jdbc.queryForList("SELECT id FROM inventaires WHERE id = ? AND center_id = ? FOR UPDATE", UUID.class, id, centre.valeur());
        return parId(centre, id);
    }

    @Override
    public void enregistrer(Inventaire inventaire) {
        jdbc.update("UPDATE inventaires SET statut = ?, commentaire = ?, closed_by = ?, closed_at = ? WHERE id = ? AND center_id = ?",
                inventaire.statut().name(), inventaire.commentaire(), inventaire.clotureePar(), inventaire.clotureeLe(), inventaire.id(),
                inventaire.centre().valeur());
        remplacerLesLignes(inventaire);
    }

    @Override
    public PeriodeDeStock periode(CentreId centre) {
        LocalDate derniere = jdbc.queryForObject("SELECT MAX(date_inventaire) FROM inventaires WHERE center_id = ? AND statut = 'CLOTURE'",
                LocalDate.class, centre.valeur());
        String enCours = jdbc.queryForList("SELECT reference FROM inventaires WHERE center_id = ? AND statut = 'EN_COURS'",
                String.class, centre.valeur()).stream().findFirst().orElse(null);
        return new PeriodeDeStock(centre, derniere, enCours);
    }

    @Override
    public PageDInventaires lister(CentreId centre, int page, int taille) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM inventaires WHERE center_id = ?", Long.class, centre.valeur());
        List<Resume> elements = jdbc.query("SELECT i.id, i.reference, i.date_inventaire, i.statut, i.created_by, i.created_at, "
                        + "i.closed_by, i.closed_at, "
                        + "(SELECT COUNT(*) FROM inventaire_lignes l WHERE l.inventaire_id = i.id) AS nb_lignes, "
                        + "(SELECT COUNT(*) FROM inventaire_lignes l WHERE l.inventaire_id = i.id AND l.quantite_comptee IS NOT NULL) "
                        + "AS nb_comptees, "
                        + "(SELECT COUNT(*) FROM inventaire_lignes l WHERE l.inventaire_id = i.id AND l.quantite_comptee IS NOT NULL "
                        + " AND l.quantite_comptee <> l.quantite_theorique) AS nb_ecarts, "
                        + "(SELECT COALESCE(SUM((l.quantite_comptee - l.quantite_theorique) * COALESCE(l.pmp, 0)), 0) "
                        + " FROM inventaire_lignes l WHERE l.inventaire_id = i.id AND l.quantite_comptee IS NOT NULL) AS valeur_ecarts "
                        + "FROM inventaires i WHERE i.center_id = ? ORDER BY i.date_inventaire DESC, i.created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> new Resume(rs.getObject("id", UUID.class), rs.getString("reference"),
                        rs.getObject("date_inventaire", LocalDate.class), StatutDInventaire.valueOf(rs.getString("statut")),
                        rs.getInt("nb_lignes"), rs.getInt("nb_comptees"), rs.getInt("nb_ecarts"), rs.getBigDecimal("valeur_ecarts").setScale(2, java.math.RoundingMode.HALF_UP),
                        rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class), rs.getString("closed_by"),
                        rs.getObject("closed_at", OffsetDateTime.class)), centre.valeur(), taille, (long) page * taille);
        return new PageDInventaires(elements, total == null ? 0 : total, page, taille);
    }

    @Override
    public void verrouillerLaPeriode(CentreId centre) {
        verrouDuCentre.verrouiller(centre.valeur());
    }
}

