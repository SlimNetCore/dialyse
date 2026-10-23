package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.DemiJournee;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.LigneDePiece;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.PerimetreDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiePourSeances;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiesPourSeances;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Tables {@code sorties_pour_seances}, {@code sorties_pour_seances_lignes} et {@code consommations_de_seance(_imputations)}
 * (migration V45, slice S6b). La référence {@code SPS-nnnnn} vient du compteur {@code SEQ_SPS} dans la transaction de la pièce ; une
 * modification verrouille la ligne de la pièce ({@code FOR UPDATE}).
 */
public class SortiesPourSeancesJdbc implements SortiesPourSeances {

    private static final String COLONNES = "id, center_id, reference, jour, salle, demi_journee, statut, enregistree_le, enregistree_par, "
            + "validee_le, validee_par";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final SequenceDesPiecesJdbc sequence;

    public SortiesPourSeancesJdbc(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.sequence = new SequenceDesPiecesJdbc(jdbc);
    }

    private void inserer(SortiePourSeances p) {
        jdbc.update("INSERT INTO sorties_pour_seances (" + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", p.id(),
                p.centre().valeur(), p.reference(), p.jour(), p.perimetre().salle(),
                p.perimetre().demiJournee() == null ? null : p.perimetre().demiJournee().name(), p.statut().name(), p.enregistreeLe(),
                p.enregistreePar(), p.valideeLe(), p.valideePar());
    }

    private void remplacerLesLignes(SortiePourSeances p) {
        jdbc.update("DELETE FROM sorties_pour_seances_lignes WHERE sortie_id = ?", p.id());
        jdbc.batchUpdate("INSERT INTO sorties_pour_seances_lignes (id, sortie_id, article_id, lot_id, quantite_sortie, valeur_unitaire, "
                        + "quantite_consommee, ecart, motif) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                p.lignes().stream().map(l -> new Object[]{l.id(), p.id(), l.articleId(), l.lotId(), l.quantiteSortie(),
                        l.valeurUnitaire(), l.quantiteConsommee(), l.ecart(), l.motif()}).toList());
    }

    private List<LigneDePiece> lignesDe(UUID id) {
        return jdbc.query("SELECT id, article_id, lot_id, quantite_sortie, valeur_unitaire, quantite_consommee, ecart, motif "
                        + "FROM sorties_pour_seances_lignes WHERE sortie_id = ?",
                (rs, i) -> new LigneDePiece(rs.getObject("id", UUID.class), rs.getObject("article_id", UUID.class),
                        rs.getObject("lot_id", UUID.class), rs.getBigDecimal("quantite_sortie"), rs.getBigDecimal("valeur_unitaire"),
                        rs.getBigDecimal("quantite_consommee"), rs.getBigDecimal("ecart"), rs.getString("motif")), id);
    }

    private List<SortiePourSeances> lire(String sql, Object... parametres) {
        return jdbc.query(sql, (rs, i) -> {
            String demiJournee = rs.getString("demi_journee");
            return SortiePourSeances.reconstituer(rs.getObject("id", UUID.class), new CentreId(rs.getObject("center_id", UUID.class)),
                    rs.getString("reference"), rs.getObject("jour", LocalDate.class),
                    new PerimetreDeSortie(rs.getString("salle"), demiJournee == null ? null : DemiJournee.valueOf(demiJournee)),
                    SortiePourSeances.Statut.valueOf(rs.getString("statut")), rs.getObject("enregistree_le", OffsetDateTime.class),
                    rs.getString("enregistree_par"), rs.getObject("validee_le", OffsetDateTime.class), rs.getString("validee_par"),
                    List.of());
        }, parametres).stream().map(p -> SortiePourSeances.reconstituer(p.id(), p.centre(), p.reference(), p.jour(), p.perimetre(),
                p.statut(), p.enregistreeLe(), p.enregistreePar(), p.valideeLe(), p.valideePar(), lignesDe(p.id()))).toList();
    }

    @Override
    public SortiePourSeances creer(CentreId centre, Function<String, SortiePourSeances> fabrique) {
        return transaction.execute(statut -> {
            SortiePourSeances piece = fabrique.apply(sequence.suivante(centre.valeur(), "SEQ_SPS", "SPS-"));
            inserer(piece);
            remplacerLesLignes(piece);
            return lire("SELECT " + COLONNES + " FROM sorties_pour_seances WHERE id = ? AND center_id = ?", piece.id(), centre.valeur())
                    .get(0);
        });
    }

    @Override
    public Optional<SortiePourSeances> parId(CentreId centre, UUID id) {
        return lire("SELECT " + COLONNES + " FROM sorties_pour_seances WHERE id = ? AND center_id = ?", id, centre.valeur()).stream()
                .findFirst();
    }

    @Override
    public List<SortiePourSeances> toutes(CentreId centre) {
        return lire("SELECT " + COLONNES + " FROM sorties_pour_seances WHERE center_id = ? ORDER BY enregistree_le DESC, reference DESC",
                centre.valeur());
    }

    @Override
    public Optional<SortiePourSeances> dernieresDuPerimetre(CentreId centre, PerimetreDeSortie perimetre) {
        String demiJournee = perimetre.demiJournee() == null ? null : perimetre.demiJournee().name();
        List<Object> parametres = new ArrayList<>(List.of(centre.valeur()));
        StringBuilder sql = new StringBuilder("SELECT " + COLONNES + " FROM sorties_pour_seances WHERE center_id = ?");
        if (perimetre.salle() == null) {
            sql.append(" AND salle IS NULL");
        } else {
            sql.append(" AND salle = ?");
            parametres.add(perimetre.salle());
        }
        if (demiJournee == null) {
            sql.append(" AND demi_journee IS NULL");
        } else {
            sql.append(" AND demi_journee = ?");
            parametres.add(demiJournee);
        }
        sql.append(" ORDER BY enregistree_le DESC, reference DESC LIMIT 1");
        return lire(sql.toString(), parametres.toArray()).stream().findFirst();
    }

    @Override
    public List<SortiePourSeances> duJour(CentreId centre, LocalDate jour) {
        return lire("SELECT " + COLONNES + " FROM sorties_pour_seances WHERE center_id = ? AND jour = ? "
                + "ORDER BY enregistree_le DESC, reference DESC", centre.valeur(), jour);
    }

    @Override
    public Optional<SortiePourSeances> verrouiller(CentreId centre, UUID id) {
        jdbc.queryForList("SELECT id FROM sorties_pour_seances WHERE id = ? AND center_id = ? FOR UPDATE", UUID.class, id,
                centre.valeur());
        return parId(centre, id);
    }

    @Override
    public Optional<SortiePourSeances> modifier(CentreId centre, UUID id, UnaryOperator<SortiePourSeances> modification) {
        return transaction.execute(statut -> {
            List<UUID> verrou = jdbc.queryForList("SELECT id FROM sorties_pour_seances WHERE id = ? AND center_id = ? FOR UPDATE",
                    UUID.class, id, centre.valeur());
            if (verrou.isEmpty()) {
                return Optional.empty();
            }
            SortiePourSeances apres = modification.apply(parId(centre, id).orElseThrow());
            jdbc.update("UPDATE sorties_pour_seances SET statut = ?, validee_le = ?, validee_par = ? WHERE id = ? AND center_id = ?",
                    apres.statut().name(), apres.valideeLe(), apres.valideePar(), id, centre.valeur());
            remplacerLesLignes(apres);
            return parId(centre, id);
        });
    }

    @Override
    public Consommations consommations(CentreId centre, UUID sortieId) {
        Map<UUID, BigDecimal> parLot = new LinkedHashMap<>();
        Map<UUID, BigDecimal> horsLots = new LinkedHashMap<>();
        List<Object[]> imputations = new ArrayList<>(jdbc.query("SELECT i.article_id, i.lot_id, i.quantite FROM consommations_de_seance_imputations i "
                        + "JOIN consommations_de_seance c ON c.seance_id = i.seance_id WHERE c.sortie_id = ? AND c.center_id = ?",
                (rs, n) -> new Object[]{rs.getObject("article_id", UUID.class), rs.getObject("lot_id", UUID.class),
                        rs.getBigDecimal("quantite")}, sortieId, centre.valeur()));
        for (Object[] imputation : imputations) {
            UUID lot = (UUID) imputation[1];
            if (lot == null) {
                horsLots.merge((UUID) imputation[0], (BigDecimal) imputation[2], BigDecimal::add);
            } else {
                parLot.merge(lot, (BigDecimal) imputation[2], BigDecimal::add);
            }
        }
        return new Consommations(parLot, horsLots);
    }
}



