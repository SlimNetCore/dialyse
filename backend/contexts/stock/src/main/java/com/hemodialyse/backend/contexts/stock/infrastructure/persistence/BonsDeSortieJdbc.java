package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.sortie.BonDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortie.BonsDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortie.LigneDeSortie;
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
 * Tables {@code bons_sortie} et {@code bons_sortie_lignes} (même schéma que le legacy, dont les sorties de séance se servent
 * encore, slice S6b). La référence {@code BS-nnnnn} vient du compteur {@code SEQ_BS} dans la transaction du bon ; une
 * modification verrouille la ligne du bon ({@code FOR UPDATE}).
 */
public class BonsDeSortieJdbc implements BonsDeSortie {

    private static final String COLONNES = "id, center_id, reference, seance_id, patient_id, poste, date_sortie, created_by, created_at";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final SequenceDesPiecesJdbc sequence;

    public BonsDeSortieJdbc(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.sequence = new SequenceDesPiecesJdbc(jdbc);
    }

    private void inserer(BonDeSortie bon) {
        jdbc.update("INSERT INTO bons_sortie (" + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", bon.id(), bon.centre().valeur(),
                bon.reference(), bon.seanceId(), bon.patientId(), bon.poste(), bon.dateSortie(), bon.creePar(), bon.creeLe());
    }

    private void remplacerLesLignes(BonDeSortie bon) {
        jdbc.update("DELETE FROM bons_sortie_lignes WHERE bon_sortie_id = ?", bon.id());
        jdbc.batchUpdate("INSERT INTO bons_sortie_lignes (id, bon_sortie_id, article_id, lot_id, quantite, pmp_applique) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                bon.lignes().stream().map(l -> new Object[]{l.id(), bon.id(), l.articleId(), l.lotId(), l.quantite(),
                        l.pmpApplique()}).toList());
    }

    private List<LigneDeSortie> lignesDe(UUID bonId) {
        return jdbc.query("SELECT id, article_id, lot_id, quantite, pmp_applique FROM bons_sortie_lignes WHERE bon_sortie_id = ?",
                (rs, i) -> new LigneDeSortie(rs.getObject("id", UUID.class), rs.getObject("article_id", UUID.class),
                        rs.getObject("lot_id", UUID.class), rs.getBigDecimal("quantite"), rs.getBigDecimal("pmp_applique")), bonId);
    }

    private List<BonDeSortie> lire(String sql, Object... parametres) {
        return jdbc.query(sql, (rs, i) -> BonDeSortie.reconstituer(rs.getObject("id", UUID.class),
                        new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("reference"),
                        rs.getObject("seance_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("poste"),
                        rs.getObject("date_sortie", LocalDate.class), rs.getString("created_by"),
                        rs.getObject("created_at", OffsetDateTime.class), List.of()), parametres)
                .stream().map(b -> BonDeSortie.reconstituer(b.id(), b.centre(), b.reference(), b.seanceId(), b.patientId(),
                        b.poste(), b.dateSortie(), b.creePar(), b.creeLe(), lignesDe(b.id()))).toList();
    }

    @Override
    public BonDeSortie creer(CentreId centre, Function<String, BonDeSortie> fabrique) {
        return transaction.execute(statut -> {
            BonDeSortie bon = fabrique.apply(sequence.suivante(centre.valeur(), "SEQ_BS", "BS-"));
            inserer(bon);
            remplacerLesLignes(bon);
            return lire("SELECT " + COLONNES + " FROM bons_sortie WHERE id = ? AND center_id = ?", bon.id(), centre.valeur()).get(0);
        });
    }

    @Override
    public Optional<BonDeSortie> parId(CentreId centre, UUID id) {
        return lire("SELECT " + COLONNES + " FROM bons_sortie WHERE id = ? AND center_id = ?", id, centre.valeur()).stream()
                .findFirst();
    }

    @Override
    public List<BonDeSortie> tous(CentreId centre) {
        return lire("SELECT " + COLONNES + " FROM bons_sortie WHERE center_id = ? ORDER BY created_at DESC", centre.valeur());
    }

    @Override
    public Optional<BonDeSortie> modifier(CentreId centre, UUID id, UnaryOperator<BonDeSortie> modification) {
        return transaction.execute(statut -> {
            List<UUID> verrou = jdbc.queryForList("SELECT id FROM bons_sortie WHERE id = ? AND center_id = ? FOR UPDATE", UUID.class,
                    id, centre.valeur());
            if (verrou.isEmpty()) {
                return Optional.empty();
            }
            BonDeSortie apres = modification.apply(parId(centre, id).orElseThrow());
            jdbc.update("UPDATE bons_sortie SET seance_id = ?, patient_id = ?, poste = ?, date_sortie = ?, created_by = ? "
                            + "WHERE id = ? AND center_id = ?", apres.seanceId(), apres.patientId(), apres.poste(), apres.dateSortie(),
                    apres.creePar(), id, centre.valeur());
            remplacerLesLignes(apres);
            return parId(centre, id);
        });
    }
}

