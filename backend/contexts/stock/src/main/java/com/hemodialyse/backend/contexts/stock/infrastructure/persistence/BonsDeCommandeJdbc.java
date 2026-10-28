package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommande;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonsDeCommande;
import com.hemodialyse.backend.contexts.stock.domain.commandes.LigneDeBon;
import com.hemodialyse.backend.contexts.stock.domain.commandes.StatutDuBon;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Tables {@code bons_commande} et {@code bons_commande_lignes} (même schéma que le legacy, que la réception legacy lit et
 * marque {@code RECU} jusqu'à la slice S5). La référence vient du compteur {@code SEQ_BC} d'{@code app_settings} (préfixe
 * {@code BC-}, cinq chiffres), incrémenté dans la transaction du bon : un refus ne consomme rien. Une modification verrouille
 * la ligne du bon ({@code FOR UPDATE}).
 */
public class BonsDeCommandeJdbc implements BonsDeCommande {

    private static final String CLE_SEQUENCE = "SEQ_BC";
    private static final String PREFIXE_PAR_DEFAUT = "BC-";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public BonsDeCommandeJdbc(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    private String referenceSuivante(CentreId centre) {
        int modifies = incrementer(centre);
        if (modifies == 0) {
            try {
                jdbc.update("INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur) VALUES (?, ?, ?, 1)",
                        centre.valeur(), CLE_SEQUENCE, PREFIXE_PAR_DEFAUT);
            } catch (DuplicateKeyException concurrent) {
                incrementer(centre);
            }
        }
        return jdbc.queryForObject("SELECT prefixe, dernier_compteur FROM app_settings WHERE center_id = ? AND cle = ?",
                (rs, i) -> (rs.getString("prefixe") == null ? PREFIXE_PAR_DEFAUT : rs.getString("prefixe"))
                        + String.format("%05d", rs.getLong("dernier_compteur")), centre.valeur(), CLE_SEQUENCE);
    }

    private int incrementer(CentreId centre) {
        return jdbc.update("UPDATE app_settings SET dernier_compteur = dernier_compteur + 1 WHERE center_id = ? AND cle = ?",
                centre.valeur(), CLE_SEQUENCE);
    }

    private void insererLeBon(BonDeCommande bon) {
        jdbc.update("INSERT INTO bons_commande (id, center_id, reference, fournisseur_id, statut, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", bon.id(), bon.centre().valeur(), bon.reference(), bon.fournisseurId(),
                bon.statut().name(), bon.creePar(), bon.creeLe());
    }

    private void remplacerLesLignes(BonDeCommande bon) {
        jdbc.update("DELETE FROM bons_commande_lignes WHERE bon_commande_id = ?", bon.id());
        jdbc.batchUpdate("INSERT INTO bons_commande_lignes (id, bon_commande_id, article_id, quantite, prix_unitaire) "
                        + "VALUES (?, ?, ?, ?, ?)",
                bon.lignes().stream().map(l -> new Object[]{l.id(), bon.id(), l.articleId(), l.quantite(), l.prixUnitaire()})
                        .toList());
    }

    private List<LigneDeBon> lignesDe(UUID bonId) {
        return jdbc.query("SELECT id, article_id, quantite, prix_unitaire FROM bons_commande_lignes WHERE bon_commande_id = ?",
                (rs, i) -> new LigneDeBon(rs.getObject("id", UUID.class), rs.getObject("article_id", UUID.class),
                        rs.getBigDecimal("quantite"), rs.getBigDecimal("prix_unitaire")), bonId);
    }

    private List<BonDeCommande> lire(String sql, Object... parametres) {
        return jdbc.query(sql, (rs, i) -> BonDeCommande.reconstituer(rs.getObject("id", UUID.class),
                        new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("reference"),
                        rs.getObject("fournisseur_id", UUID.class), StatutDuBon.valueOf(rs.getString("statut")),
                        rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class), List.of()), parametres).stream()
                .map(b -> BonDeCommande.reconstituer(b.id(), b.centre(), b.reference(), b.fournisseurId(), b.statut(),
                        b.creePar(), b.creeLe(), lignesDe(b.id()))).toList();
    }

    @Override
    public BonDeCommande creer(CentreId centre, Function<String, BonDeCommande> fabrique) {
        return transaction.execute(statut -> {
            BonDeCommande bon = fabrique.apply(referenceSuivante(centre));
            insererLeBon(bon);
            remplacerLesLignes(bon);
            return parIdDansLaTransaction(centre, bon.id()).orElseThrow();
        });
    }

    private Optional<BonDeCommande> parIdDansLaTransaction(CentreId centre, UUID id) {
        return lire("SELECT id, center_id, reference, fournisseur_id, statut, created_by, created_at FROM bons_commande "
                + "WHERE id = ? AND center_id = ?", id, centre.valeur()).stream().findFirst();
    }

    @Override
    public Optional<BonDeCommande> parId(CentreId centre, UUID id) {
        return parIdDansLaTransaction(centre, id);
    }

    @Override
    public List<BonDeCommande> tous(CentreId centre) {
        return lire("SELECT id, center_id, reference, fournisseur_id, statut, created_by, created_at FROM bons_commande "
                + "WHERE center_id = ? ORDER BY created_at DESC, reference DESC", centre.valeur());
    }

    @Override
    public Optional<BonDeCommande> modifier(CentreId centre, UUID id, UnaryOperator<BonDeCommande> modification) {
        return transaction.execute(statut -> {
            List<UUID> verrou = jdbc.queryForList("SELECT id FROM bons_commande WHERE id = ? AND center_id = ? FOR UPDATE",
                    UUID.class, id, centre.valeur());
            if (verrou.isEmpty()) {
                return Optional.empty();
            }
            BonDeCommande avant = parIdDansLaTransaction(centre, id).orElseThrow();
            BonDeCommande apres = modification.apply(avant);
            jdbc.update("UPDATE bons_commande SET statut = ? WHERE id = ? AND center_id = ?", apres.statut().name(), id,
                    centre.valeur());
            remplacerLesLignes(apres);
            return parIdDansLaTransaction(centre, id);
        });
    }
}

