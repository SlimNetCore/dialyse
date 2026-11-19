package com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.bons.PagesDesBons;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommande;
import com.hemodialyse.backend.contexts.stock.domain.commandes.LigneDeBon;
import com.hemodialyse.backend.contexts.stock.domain.commandes.StatutDuBon;
import com.hemodialyse.backend.contexts.stock.domain.reception.BonDeReception;
import com.hemodialyse.backend.contexts.stock.domain.reception.LigneDeReception;
import com.hemodialyse.backend.contexts.stock.domain.sortie.BonDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortie.LigneDeSortie;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.BonsDeReceptionJdbc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pages des bons du centre en SQL (tables {@code bons_commande}, {@code bons_reception}, {@code bons_sortie} et leurs lignes) :
 * {@code COUNT(*)} puis {@code ORDER BY … LIMIT ? OFFSET ?} avec le même {@code WHERE} (centre, filtres). Le champ de tri est
 * traduit par une table fixe (jamais le texte reçu) ; départage {@code reference DESC, id}. Les lignes des bons de la page sont
 * lues en une requête {@code IN}. Bons de réception : le statut historique {@code RECU} se lit, se filtre et se trie comme
 * {@code VALIDE} (KB-STK-58).
 */
public class BonsParPageJdbc implements PagesDesBons {

    private static final String DEPARTAGE = "b.reference DESC, b.id";
    private static final Map<String, String> TRIS_COMMANDE = Map.of(
            "reference", "b.reference",
            "statut", "CASE b.statut WHEN 'BROUILLON' THEN 0 WHEN 'VALIDE' THEN 1 ELSE 2 END",
            "lignes", "(SELECT COUNT(*) FROM bons_commande_lignes l WHERE l.bon_commande_id = b.id)",
            "createdAt", "b.created_at");
    private static final String STATUT_RECEPTION = "CASE WHEN b.statut = 'RECU' THEN 'VALIDE' ELSE b.statut END";
    private static final Map<String, String> TRIS_RECEPTION = Map.of(
            "reference", "b.reference",
            "dateReception", "b.date_reception",
            "statut", "CASE " + STATUT_RECEPTION + " WHEN 'BROUILLON' THEN 0 ELSE 1 END",
            "lignes", "(SELECT COUNT(*) FROM bons_reception_lignes l WHERE l.bon_reception_id = b.id)",
            "createdAt", "b.created_at");
    private static final Map<String, String> TRIS_SORTIE = Map.of(
            "reference", "b.reference",
            "dateSortie", "b.date_sortie",
            "lignes", "(SELECT COUNT(*) FROM bons_sortie_lignes l WHERE l.bon_sortie_id = b.id)",
            "createdAt", "b.created_at");

    private final JdbcTemplate jdbc;

    public BonsParPageJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code WHERE} construit à partir de fragments fixes ; seules les valeurs passent en paramètres. */
    private static final class Condition {
        private final StringBuilder sql = new StringBuilder(" WHERE b.center_id = ?");
        private final List<Object> parametres = new ArrayList<>();

        Condition(CentreId centre) {
            parametres.add(centre.valeur());
        }

        void reference(String terme) {
            if (terme == null) return;
            sql.append(" AND LOWER(b.reference) LIKE ? ESCAPE '\\'");
            parametres.add(SqlDePage.contient(terme));
        }

        void parmi(String expression, Collection<String> valeurs) {
            if (valeurs.isEmpty()) return;
            sql.append(" AND ").append(expression).append(" IN (")
                    .append(valeurs.stream().map(v -> "?").collect(Collectors.joining(", "))).append(')');
            parametres.addAll(valeurs);
        }
    }

    private <T> PageDeResultats<T> page(String table, String colonnes, Condition condition, DemandeDePage demande,
                                        Map<String, String> tris, RowMapper<T> lecture) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " b" + condition.sql, Long.class,
                condition.parametres.toArray());
        List<Object> parametres = new ArrayList<>(condition.parametres);
        parametres.add(demande.taille());
        parametres.add(demande.decalage());
        List<T> items = jdbc.query("SELECT " + colonnes + " FROM " + table + " b" + condition.sql
                + SqlDePage.ordre(demande, tris, DEPARTAGE) + SqlDePage.limite(), lecture, parametres.toArray());
        return new PageDeResultats<>(items, total == null ? 0 : total, demande.page(), demande.taille());
    }

    /** Lignes des bons {@code ids} (une requête), groupées par bon, dans l'ordre de lecture. */
    private <L> Map<UUID, List<L>> lignes(String sql, List<UUID> ids, RowMapper<L> lecture, Function<L, UUID> bonDe) {
        if (ids.isEmpty()) return Map.of();
        String marques = ids.stream().map(i -> "?").collect(Collectors.joining(", "));
        return jdbc.query(sql.replace(":ids", marques), lecture, ids.toArray()).stream()
                .collect(Collectors.groupingBy(bonDe, LinkedHashMap::new, Collectors.toList()));
    }

    private record Avec<L>(UUID bonId, L ligne) {
    }

    @Override
    public PageDeResultats<BonDeCommande> bonsDeCommande(CentreId centre, FiltreDesBonsDeCommande filtre, DemandeDePage demande) {
        Condition condition = new Condition(centre);
        condition.reference(filtre.reference());
        condition.parmi("b.statut", filtre.statuts().stream().map(StatutDuBon::name).sorted().toList());
        PageDeResultats<BonDeCommande> entetes = page("bons_commande",
                "b.id, b.center_id, b.reference, b.fournisseur_id, b.statut, b.created_by, b.created_at", condition, demande,
                TRIS_COMMANDE, (rs, i) -> BonDeCommande.reconstituer(rs.getObject("id", UUID.class),
                        new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("reference"),
                        rs.getObject("fournisseur_id", UUID.class), StatutDuBon.valueOf(rs.getString("statut")),
                        rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class), List.of()));
        Map<UUID, List<Avec<LigneDeBon>>> lignes = lignes("SELECT id, bon_commande_id, article_id, quantite, prix_unitaire "
                        + "FROM bons_commande_lignes WHERE bon_commande_id IN (:ids)",
                entetes.items().stream().map(BonDeCommande::id).toList(),
                (rs, i) -> new Avec<>(rs.getObject("bon_commande_id", UUID.class), new LigneDeBon(rs.getObject("id", UUID.class),
                        rs.getObject("article_id", UUID.class), rs.getBigDecimal("quantite"), rs.getBigDecimal("prix_unitaire"))),
                Avec::bonId);
        return entetes.map(b -> BonDeCommande.reconstituer(b.id(), b.centre(), b.reference(), b.fournisseurId(), b.statut(),
                b.creePar(), b.creeLe(), lignes.getOrDefault(b.id(), List.of()).stream().map(Avec::ligne).toList()));
    }

    @Override
    public PageDeResultats<BonDeReception> bonsDeReception(CentreId centre, FiltreDesBonsDeReception filtre,
                                                           DemandeDePage demande) {
        Condition condition = new Condition(centre);
        condition.reference(filtre.reference());
        condition.parmi(STATUT_RECEPTION, filtre.statuts().stream().map(BonDeReception.Statut::name).sorted().toList());
        PageDeResultats<BonDeReception> entetes = page("bons_reception",
                "b.id, b.center_id, b.reference, b.bon_commande_id, b.fournisseur_id, b.date_reception, b.statut, b.created_by, "
                        + "b.created_at", condition, demande, TRIS_RECEPTION,
                (rs, i) -> BonDeReception.reconstituer(rs.getObject("id", UUID.class),
                        new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("reference"),
                        rs.getObject("bon_commande_id", UUID.class), rs.getObject("fournisseur_id", UUID.class),
                        rs.getObject("date_reception", LocalDate.class),
                        BonsDeReceptionJdbc.statutLu(rs.getString("statut"), rs.getObject("id", UUID.class)),
                        rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class), List.of()));
        Map<UUID, List<Avec<LigneDeReception>>> lignes = lignes("SELECT id, bon_reception_id, article_id, quantite, "
                        + "prix_unitaire, numero_lot, date_peremption, emplacement_id, lot_id FROM bons_reception_lignes "
                        + "WHERE bon_reception_id IN (:ids)",
                entetes.items().stream().map(BonDeReception::id).toList(),
                (rs, i) -> new Avec<>(rs.getObject("bon_reception_id", UUID.class), new LigneDeReception(
                        rs.getObject("id", UUID.class), rs.getObject("article_id", UUID.class), rs.getBigDecimal("quantite"),
                        rs.getBigDecimal("prix_unitaire"), rs.getString("numero_lot"),
                        rs.getObject("date_peremption", LocalDate.class), rs.getObject("emplacement_id", UUID.class),
                        rs.getObject("lot_id", UUID.class))),
                Avec::bonId);
        return entetes.map(b -> BonDeReception.reconstituer(b.id(), b.centre(), b.reference(), b.bonCommandeId(),
                b.fournisseurId(), b.dateReception(), b.statut(), b.creePar(), b.creeLe(),
                lignes.getOrDefault(b.id(), List.of()).stream().map(Avec::ligne).toList()));
    }

    @Override
    public PageDeResultats<BonDeSortie> bonsDeSortie(CentreId centre, FiltreDesBonsDeSortie filtre, DemandeDePage demande) {
        Condition condition = new Condition(centre);
        condition.reference(filtre.reference());
        PageDeResultats<BonDeSortie> entetes = page("bons_sortie",
                "b.id, b.center_id, b.reference, b.seance_id, b.patient_id, b.poste, b.date_sortie, b.created_by, b.created_at",
                condition, demande, TRIS_SORTIE,
                (rs, i) -> BonDeSortie.reconstituer(rs.getObject("id", UUID.class),
                        new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("reference"),
                        rs.getObject("seance_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("poste"),
                        rs.getObject("date_sortie", LocalDate.class), rs.getString("created_by"),
                        rs.getObject("created_at", OffsetDateTime.class), List.of()));
        Map<UUID, List<Avec<LigneDeSortie>>> lignes = lignes("SELECT id, bon_sortie_id, article_id, lot_id, quantite, "
                        + "pmp_applique FROM bons_sortie_lignes WHERE bon_sortie_id IN (:ids)",
                entetes.items().stream().map(BonDeSortie::id).toList(),
                (rs, i) -> new Avec<>(rs.getObject("bon_sortie_id", UUID.class), new LigneDeSortie(rs.getObject("id", UUID.class),
                        rs.getObject("article_id", UUID.class), rs.getObject("lot_id", UUID.class), rs.getBigDecimal("quantite"),
                        rs.getBigDecimal("pmp_applique"))),
                Avec::bonId);
        return entetes.map(b -> BonDeSortie.reconstituer(b.id(), b.centre(), b.reference(), b.seanceId(), b.patientId(),
                b.poste(), b.dateSortie(), b.creePar(), b.creeLe(),
                lignes.getOrDefault(b.id(), List.of()).stream().map(Avec::ligne).toList()));
    }
}
