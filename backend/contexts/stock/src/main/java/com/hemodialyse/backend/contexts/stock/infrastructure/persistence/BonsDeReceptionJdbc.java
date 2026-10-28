package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.reception.BonDeReception;
import com.hemodialyse.backend.contexts.stock.domain.reception.BonsDeReception;
import com.hemodialyse.backend.contexts.stock.domain.reception.LigneDeReception;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Tables {@code bons_reception} et {@code bons_reception_lignes} (même schéma que le legacy, dont le jeu de démonstration se
 * sert encore). La référence {@code BR-nnnnn} vient du compteur {@code SEQ_BR} dans la transaction du bon ; une modification
 * verrouille la ligne du bon ({@code FOR UPDATE}) ; les lignes validées portent leur {@code lot_id} (KB-STK-37).
 */
public class BonsDeReceptionJdbc implements BonsDeReception {

    private static final String COLONNES = "id, center_id, reference, bon_commande_id, fournisseur_id, date_reception, statut, "
            + "created_by, created_at";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final SequenceDesPiecesJdbc sequence;

    public BonsDeReceptionJdbc(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.sequence = new SequenceDesPiecesJdbc(jdbc);
    }

    private void inserer(BonDeReception bon) {
        jdbc.update("INSERT INTO bons_reception (" + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", bon.id(),
                bon.centre().valeur(), bon.reference(), bon.bonCommandeId(), bon.fournisseurId(), bon.dateReception(),
                bon.statut().name(), bon.creePar(), bon.creeLe());
    }

    private void remplacerLesLignes(BonDeReception bon) {
        jdbc.update("DELETE FROM bons_reception_lignes WHERE bon_reception_id = ?", bon.id());
        jdbc.batchUpdate("INSERT INTO bons_reception_lignes (id, bon_reception_id, article_id, quantite, prix_unitaire, numero_lot, "
                        + "date_peremption, emplacement_id, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                bon.lignes().stream().map(l -> new Object[]{l.id(), bon.id(), l.articleId(), l.quantite(), l.prixUnitaire(),
                        l.numeroLot(), l.datePeremption(), l.emplacementId(), l.lotId()}).toList());
    }

    private List<LigneDeReception> lignesDe(UUID bonId) {
        return jdbc.query("SELECT id, article_id, quantite, prix_unitaire, numero_lot, date_peremption, emplacement_id, lot_id "
                        + "FROM bons_reception_lignes WHERE bon_reception_id = ?",
                (rs, i) -> new LigneDeReception(rs.getObject("id", UUID.class), rs.getObject("article_id", UUID.class),
                        rs.getBigDecimal("quantite"), rs.getBigDecimal("prix_unitaire"), rs.getString("numero_lot"),
                        rs.getObject("date_peremption", LocalDate.class), rs.getObject("emplacement_id", UUID.class),
                        rs.getObject("lot_id", UUID.class)), bonId);
    }

    private List<BonDeReception> lire(String sql, Object... parametres) {
        return jdbc.query(sql, (rs, i) -> BonDeReception.reconstituer(rs.getObject("id", UUID.class),
                        new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("reference"),
                        rs.getObject("bon_commande_id", UUID.class), rs.getObject("fournisseur_id", UUID.class),
                        rs.getObject("date_reception", LocalDate.class), BonDeReception.Statut.valueOf(rs.getString("statut")),
                        rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class), List.of()), parametres)
                .stream().map(b -> BonDeReception.reconstituer(b.id(), b.centre(), b.reference(), b.bonCommandeId(),
                        b.fournisseurId(), b.dateReception(), b.statut(), b.creePar(), b.creeLe(), lignesDe(b.id()))).toList();
    }

    @Override
    public BonDeReception creer(CentreId centre, Function<String, BonDeReception> fabrique) {
        return transaction.execute(statut -> {
            BonDeReception bon = fabrique.apply(sequence.suivante(centre.valeur(), "SEQ_BR", "BR-"));
            inserer(bon);
            remplacerLesLignes(bon);
            return lire("SELECT " + COLONNES + " FROM bons_reception WHERE id = ? AND center_id = ?", bon.id(), centre.valeur())
                    .get(0);
        });
    }

    @Override
    public Optional<BonDeReception> parId(CentreId centre, UUID id) {
        return lire("SELECT " + COLONNES + " FROM bons_reception WHERE id = ? AND center_id = ?", id, centre.valeur())
                .stream().findFirst();
    }

    @Override
    public List<BonDeReception> tous(CentreId centre) {
        return lire("SELECT " + COLONNES + " FROM bons_reception WHERE center_id = ? ORDER BY created_at DESC, reference DESC",
                centre.valeur());
    }

    @Override
    public Optional<BonDeReception> modifier(CentreId centre, UUID id, UnaryOperator<BonDeReception> modification) {
        return transaction.execute(statut -> {
            List<UUID> verrou = jdbc.queryForList("SELECT id FROM bons_reception WHERE id = ? AND center_id = ? FOR UPDATE",
                    UUID.class, id, centre.valeur());
            if (verrou.isEmpty()) {
                return Optional.<BonDeReception>empty();
            }
            BonDeReception apres = modification.apply(parId(centre, id).orElseThrow());
            jdbc.update("UPDATE bons_reception SET fournisseur_id = ?, date_reception = ?, statut = ? WHERE id = ? AND center_id = ?",
                    apres.fournisseurId(), apres.dateReception(), apres.statut().name(), id, centre.valeur());
            remplacerLesLignes(apres);
            return parId(centre, id);
        });
    }
}

