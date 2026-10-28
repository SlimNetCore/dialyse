package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.reception.EcritureDeStockConcurrente;
import com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Écritures du Stock à la réception en SQL (tables {@code lots}, {@code stock_movements}, {@code articles}), dans la transaction
 * de l'appelant. Les articles sont verrouillés par un verrou de ligne ({@code SELECT … FOR UPDATE}) par identifiant croissant,
 * avec un délai de 5 s : au-delà, {@link EcritureDeStockConcurrente} (409). Les gardes d'inventaire reprennent celles du legacy
 * (RG-STK-040, 041). Le legacy restant (sorties, inventaire) pose le même verrou avant de rejouer un article.
 */
public class EcrituresDuStockJdbc implements EcrituresDuStock {

    private static final int DELAI_DU_VERROU_EN_SECONDES = 5;
    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcTemplate jdbc;
    private final JdbcTemplate verrous;

    public EcrituresDuStockJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.verrous = new JdbcTemplate(jdbc.getDataSource());
        this.verrous.setQueryTimeout(DELAI_DU_VERROU_EN_SECONDES);
    }

    private static OffsetDateTime minuitUtc(LocalDate jour) {
        return jour.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    @Override
    public void verrouiller(CentreId centre, Collection<UUID> articles) {
        try {
            for (UUID article : new TreeSet<>(articles)) {
                verrous.queryForList("SELECT id FROM articles WHERE id = ? AND center_id = ? FOR UPDATE", UUID.class, article,
                        centre.valeur());
            }
        } catch (PessimisticLockingFailureException | QueryTimeoutException e) {
            throw new EcritureDeStockConcurrente();
        }
    }

    @Override
    public void exigerAucunInventaireEnCours(CentreId centre) {
        List<String> enCours = jdbc.queryForList("SELECT reference FROM inventaires WHERE center_id = ? AND statut = 'EN_COURS'",
                String.class, centre.valeur());
        if (!enCours.isEmpty()) {
            throw new RefusDuReferentiel("STOCK_INVENTORY_IN_PROGRESS", "Inventaire " + enCours.get(0)
                    + " en cours : aucun mouvement de stock n'est permis jusqu'à sa clôture ou son annulation.");
        }
    }

    @Override
    public void exigerUneDateOuverte(CentreId centre, LocalDate jour) {
        LocalDate derniere = jdbc.queryForObject("SELECT MAX(date_inventaire) FROM inventaires WHERE center_id = ? "
                + "AND statut = 'CLOTURE'", LocalDate.class, centre.valeur());
        if (derniere != null && !jour.isAfter(derniere)) {
            throw new RefusDuReferentiel("STOCK_PERIOD_CLOSED", "Période clôturée : le stock a été inventorié le "
                    + derniere.format(FR) + ". Un mouvement doit être daté au plus tôt le " + derniere.plusDays(1).format(FR) + ".");
        }
    }

    @Override
    public List<Lot> lotsDuBon(CentreId centre, UUID bonId) {
        return jdbc.query("SELECT id, center_id, article_id, bon_reception_id, emplacement_id, numero_lot, date_peremption, "
                        + "quantite_initiale, quantite_restante, pmp FROM lots WHERE bon_reception_id = ? AND center_id = ? "
                        + "ORDER BY numero_lot, id",
                (rs, i) -> new Lot(rs.getObject("id", UUID.class), new CentreId(rs.getObject("center_id", UUID.class)),
                        rs.getObject("article_id", UUID.class), rs.getObject("bon_reception_id", UUID.class),
                        rs.getObject("emplacement_id", UUID.class), rs.getString("numero_lot"),
                        rs.getObject("date_peremption", LocalDate.class), rs.getBigDecimal("quantite_initiale"),
                        rs.getBigDecimal("quantite_restante"), rs.getBigDecimal("pmp")), bonId, centre.valeur());
    }

    @Override
    public void creerLot(Lot lot) {
        jdbc.update("INSERT INTO lots (id, center_id, article_id, bon_reception_id, emplacement_id, numero_lot, date_peremption, "
                        + "quantite_initiale, quantite_restante, pmp, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                lot.id(), lot.centre().valeur(), lot.articleId(), lot.bonReceptionId(), lot.emplacementId(), lot.numero(),
                lot.datePeremption(), lot.quantiteInitiale(), lot.quantiteRestante(), lot.pmp(), OffsetDateTime.now());
    }

    @Override
    public void modifierLot(Lot lot) {
        jdbc.update("UPDATE lots SET numero_lot = ?, date_peremption = ?, quantite_initiale = ?, quantite_restante = ?, pmp = ? "
                        + "WHERE id = ? AND center_id = ?", lot.numero(), lot.datePeremption(), lot.quantiteInitiale(),
                lot.quantiteRestante(), lot.pmp(), lot.id(), lot.centre().valeur());
    }

    @Override
    public void enregistrerEntree(CentreId centre, UUID articleId, UUID lotId, BigDecimal quantite, BigDecimal prix, String auteur,
                                  LocalDate jour, OffsetDateTime saisiLe) {
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, lot_id, mouvement_type, quantite, prix_unitaire, "
                        + "created_by, created_at, saisi_le) VALUES (?, ?, ?, ?, 'ENTREE', ?, ?, ?, ?, ?)", UUID.randomUUID(),
                centre.valeur(), articleId, lotId, quantite, prix, auteur, minuitUtc(jour), saisiLe);
    }

    @Override
    public Optional<EntreeDuLot> entreeDuLot(CentreId centre, UUID lotId) {
        return jdbc.query("SELECT id, inventaire_id FROM stock_movements WHERE center_id = ? AND lot_id = ? "
                        + "AND mouvement_type = 'ENTREE' ORDER BY created_at, id LIMIT 1",
                (rs, i) -> new EntreeDuLot(rs.getObject("id", UUID.class), rs.getObject("inventaire_id", UUID.class) != null),
                centre.valeur(), lotId).stream().findFirst();
    }

    @Override
    public void dater(UUID mouvementId, LocalDate jour) {
        jdbc.update("UPDATE stock_movements SET created_at = ? WHERE id = ?", minuitUtc(jour), mouvementId);
    }

    @Override
    public void corrigerEntree(UUID mouvementId, BigDecimal quantite, BigDecimal prix, LocalDate jour) {
        jdbc.update("UPDATE stock_movements SET quantite = ?, prix_unitaire = ?, created_at = ? WHERE id = ?", quantite, prix,
                minuitUtc(jour), mouvementId);
    }

    @Override
    public void appliquerLeRejeu(CentreId centre, UUID articleId, List<MiseAJourDeMouvement> mouvements, BigDecimal pmp,
                                 BigDecimal quantite) {
        jdbc.batchUpdate("UPDATE stock_movements SET pmp_apres = ? WHERE id = ?",
                mouvements.stream().map(m -> new Object[]{m.pmpApres(), m.mouvementId()}).toList());
        jdbc.batchUpdate("UPDATE stock_movements SET prix_unitaire = ? WHERE id = ?",
                mouvements.stream().filter(m -> m.valorisation() != null)
                        .map(m -> new Object[]{m.valorisation(), m.mouvementId()}).toList());
        jdbc.update("UPDATE articles SET pmp_courant = ?, stock_quantity = ? WHERE id = ? AND center_id = ?", pmp, quantite, articleId,
                centre.valeur());
    }
}



