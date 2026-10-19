package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.stock.OperationsDeStock;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lit les opérations de stock d'un centre déjà valorisées (tables du module Stock, aucune écriture, aucune migration).
 * Requêtes simples compatibles H2 et PostgreSQL ; regroupements par jour (UTC) et par comptes de l'article en mémoire,
 * sur la seule période demandée. Une ligne de valeur nulle (sans prix ni PMP) est signalée par le code de son article.
 */
public final class OperationsDeStockJdbc implements OperationsDeStock {

    private static final String RECEPTIONS = """
            SELECT br.id, br.reference, COALESCE(br.date_reception, CAST(br.created_at AS DATE)) AS jour,
                   a.compte_stock, a.compte_charge, COALESCE(a.code, CAST(l.article_id AS VARCHAR(64))) AS article,
                   l.quantite * COALESCE(l.prix_unitaire, 0) AS montant
            FROM bons_reception br
            JOIN bons_reception_lignes l ON l.bon_reception_id = br.id
            LEFT JOIN articles a ON a.id = l.article_id AND a.center_id = br.center_id
            WHERE br.center_id = ? AND br.statut = 'VALIDE'
              AND COALESCE(br.date_reception, CAST(br.created_at AS DATE)) BETWEEN ? AND ?
            ORDER BY jour, br.reference, br.id
            """;

    private static final String SORTIES = """
            SELECT m.created_at, a.compte_stock, a.compte_charge,
                   COALESCE(a.code, CAST(m.article_id AS VARCHAR(64))) AS article,
                   ABS(m.quantite) * COALESCE(m.prix_unitaire, m.pmp_apres, a.pmp_courant, 0) AS montant
            FROM stock_movements m
            LEFT JOIN articles a ON a.id = m.article_id AND a.center_id = m.center_id
            WHERE m.center_id = ? AND m.mouvement_type = 'SORTIE' AND m.created_at >= ? AND m.created_at < ?
            ORDER BY m.created_at
            """;

    private static final String INVENTAIRES = """
            SELECT i.id, i.reference, i.date_inventaire, a.compte_stock,
                   (l.quantite_comptee - l.quantite_theorique) * COALESCE(l.pmp, 0) AS ecart
            FROM inventaires i
            JOIN inventaire_lignes l ON l.inventaire_id = i.id AND l.center_id = i.center_id
            LEFT JOIN articles a ON a.id = l.article_id AND a.center_id = i.center_id
            WHERE i.center_id = ? AND i.statut = 'CLOTURE' AND i.date_inventaire BETWEEN ? AND ?
              AND l.quantite_comptee IS NOT NULL
            ORDER BY i.date_inventaire, i.reference, i.id
            """;

    private final JdbcTemplate jdbc;

    public OperationsDeStockJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Timestamp debutDuJour(LocalDate jour) {
        return Timestamp.from(jour.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private static String vide(String compte) {
        return compte == null || compte.isBlank() ? "" : compte.trim();
    }

    private static String ouNull(String compte) {
        return compte.isEmpty() ? null : compte;
    }

    private static BigDecimal montant(ResultSet rs, String colonne) throws SQLException {
        BigDecimal valeur = rs.getBigDecimal(colonne);
        return valeur == null ? BigDecimal.ZERO : valeur;
    }

    @Override
    public List<CentreId> centres() {
        return jdbc.query("SELECT id FROM centers", (rs, i) -> new CentreId(rs.getObject(1, UUID.class)));
    }

    @Override
    public List<Reception> receptions(CentreId centre, LocalDate du, LocalDate au) {
        Map<UUID, Piece> pieces = new LinkedHashMap<>();
        jdbc.query(RECEPTIONS, rs -> {
            String reference = rs.getString("reference");
            LocalDate jour = rs.getObject("jour", LocalDate.class);
            pieces.computeIfAbsent(rs.getObject("id", UUID.class), id -> new Piece(id, reference, jour))
                    .ajouter(rs.getString("compte_stock"), rs.getString("compte_charge"), rs.getString("article"),
                            montant(rs, "montant"));
        }, centre.valeur(), du, au);
        return pieces.values().stream()
                .map(p -> new Reception(p.id, p.reference, p.date, p.montants(), List.copyOf(p.sansValeur))).toList();
    }

    @Override
    public List<SortiesDuJour> sorties(CentreId centre, LocalDate du, LocalDate au) {
        Map<LocalDate, Piece> jours = new LinkedHashMap<>();
        jdbc.query(SORTIES, rs -> {
            LocalDate jour = rs.getObject("created_at", OffsetDateTime.class)
                    .withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
            jours.computeIfAbsent(jour, j -> new Piece(null, null, j))
                    .ajouter(rs.getString("compte_stock"), rs.getString("compte_charge"), rs.getString("article"),
                            montant(rs, "montant"));
        }, centre.valeur(), debutDuJour(du), debutDuJour(au.plusDays(1)));
        return jours.values().stream()
                .map(p -> new SortiesDuJour(p.date, p.montants(), List.copyOf(p.sansValeur))).toList();
    }

    @Override
    public List<Inventaire> inventaires(CentreId centre, LocalDate du, LocalDate au) {
        Map<UUID, Piece> pieces = new LinkedHashMap<>();
        jdbc.query(INVENTAIRES, rs -> {
            String reference = rs.getString("reference");
            LocalDate jour = rs.getObject("date_inventaire", LocalDate.class);
            pieces.computeIfAbsent(rs.getObject("id", UUID.class), id -> new Piece(id, reference, jour))
                    .ajouterEcart(rs.getString("compte_stock"), montant(rs, "ecart"));
        }, centre.valeur(), du, au);
        return pieces.values().stream().map(p -> new Inventaire(p.id, p.reference, p.date, p.ecarts())).toList();
    }

    /**
     * Pièce en cours de lecture : montants cumulés par couple de comptes de l'article.
     */
    private static final class Piece {
        private final UUID id;
        private final String reference;
        private final LocalDate date;
        /** clé = compte de stock + séparateur + compte de charge (vides si l'article n'en précise pas) */
        private final Map<String, BigDecimal> montants = new LinkedHashMap<>();
        private final Map<String, BigDecimal> boni = new LinkedHashMap<>();
        private final Map<String, BigDecimal> mali = new LinkedHashMap<>();
        private final Set<String> sansValeur = new LinkedHashSet<>();

        private Piece(UUID id, String reference, LocalDate date) {
            this.id = id;
            this.reference = reference;
            this.date = date;
        }

        private void ajouter(String compteStock, String compteCharge, String article, BigDecimal montant) {
            montants.merge(vide(compteStock) + "|" + vide(compteCharge), montant, BigDecimal::add);
            if (montant.signum() == 0) {
                sansValeur.add(article);
            }
        }

        private void ajouterEcart(String compteStock, BigDecimal ecart) {
            String compte = vide(compteStock);
            boni.putIfAbsent(compte, BigDecimal.ZERO);
            mali.putIfAbsent(compte, BigDecimal.ZERO);
            if (ecart.signum() > 0) {
                boni.merge(compte, ecart, BigDecimal::add);
            } else {
                mali.merge(compte, ecart.negate(), BigDecimal::add);
            }
        }

        private List<Montant> montants() {
            List<Montant> liste = new ArrayList<>();
            montants.forEach((cle, montant) -> {
                int separateur = cle.indexOf('|');
                liste.add(new Montant(ouNull(cle.substring(0, separateur)), ouNull(cle.substring(separateur + 1)),
                        montant));
            });
            return liste;
        }

        private List<Ecart> ecarts() {
            List<Ecart> liste = new ArrayList<>();
            boni.forEach((compte, valeur) -> liste.add(new Ecart(ouNull(compte), valeur, mali.get(compte))));
            return liste;
        }
    }
}

