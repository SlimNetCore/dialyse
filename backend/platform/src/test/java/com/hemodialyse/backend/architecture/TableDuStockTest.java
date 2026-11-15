package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice S9 : les tables du Stock appartiennent au contexte Stock. Plus aucun source hors de ce contexte (plateforme, anciens
 * modules, autres contextes) ne doit les lire ou les écrire en SQL direct : le pilotage passe par le langage publié
 * {@code CatalogueDArticles}, {@code ConsommationsDeSeance}, {@code IndicateursDuStock}, {@code OperationsDeStock} et
 * {@code GroupesDArticlesDesCentres}. Garde de non-régression (les entités JPA et les migrations Flyway sont hors périmètre).
 *
 * <p>La garde est elle-même testée : {@link #la_garde_detecte_une_lecture_directe_d_une_table_du_stock()} prouve qu'une lecture
 * directe est bien détectée, et {@link #la_liste_des_tables_couvre_les_tables_lues_par_le_stock()} prouve que la liste ne peut pas
 * dériver des tables réellement manipulées par le contexte Stock.
 */
class TableDuStockTest {

    /**
     * Tables possédées par le Stock (schéma {@code contexts/stock/.../db/h2/stock-schema.sql} depuis S-DDL3, ADR 0186, + tables des adapters du contexte).
     */
    private static final Set<String> TABLES_DU_STOCK = Set.of(
            "articles", "lots", "fournisseurs", "emplacements",
            "groupes_articles", "groupes_articles_items",
            "bons_commande", "bons_commande_lignes", "bons_reception", "bons_reception_lignes",
            "bons_sortie", "bons_sortie_lignes",
            "stock_movements", "stock_parametres",
            "inventaires", "inventaire_lignes",
            "consommations_de_seance", "consommations_de_seance_imputations",
            "sorties_pour_seances", "sorties_pour_seances_lignes");

    /**
     * Tables lues par le contexte Stock chez d'autres contextes (dette d'étranglement connue, hors périmètre de cette garde) :
     * centres et horloge, séances et salles, réglages applicatifs.
     */
    private static final Set<String> TABLES_LUES_AILLEURS = Set.of(
            // S-DDL2 (2026-11-14, ADR 0185) : « app_settings » SOLDÉ (retiré, jamais reporté) : les compteurs sont à la Comptabilité.
            "centers", "position_creneau", "salle", "seances");

    /**
     * Mots-clés SQL en majuscules : le code de production écrit ses requêtes en majuscules, alors que la prose française contient
     * des « table des séances » ou « from a » qui ne sont pas du SQL. Le nom de table, lui, est comparé sans tenir compte de la casse.
     */
    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private static List<String> sourcesFautives(List<Path> racines) {
        return racines.stream().filter(Files::isDirectory)
                .flatMap(TableDuStockTest::sources)
                .filter(TableDuStockTest::mentionneUneTableDuStock)
                .map(Path::toString).toList();
    }

    private static Stream<Path> sources(Path racine) {
        try {
            return Files.walk(racine)
                    .filter(p -> p.toString().endsWith(".java"))
                    // RP3b (2026-11-13, ADR 0172) : l'exclusion des répertoires « /migration/ » est SOLDÉE (supprimée, jamais reportée) ;
                    // l'annulation d'un lot de reprise lit les bons de sortie par BonsDeSortieDeLaReprise (stock.v1).
                    .filter(p -> !chemin(p).contains("/entity/"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String chemin(Path p) {
        return p.toString().replace('\\', '/');
    }

    private static boolean mentionneUneTableDuStock(Path source) {
        try {
            Matcher m = LECTURE_SQL.matcher(Files.readString(source));
            while (m.find()) {
                if (TABLES_DU_STOCK.contains(m.group(2).toLowerCase())) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aucun_source_hors_du_contexte_ne_mentionne_les_tables_du_stock() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        List<Path> racinesPlateforme = List.of(
                backend.resolve("platform/src/main/java"), backend.resolve("shared-kernel/src/main/java"));
        try (Stream<Path> modules = Files.list(backend)) {
            List<Path> anciens = modules.filter(p -> p.getFileName().toString().startsWith("bc-"))
                    .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            List<Path> contexts;
            try (Stream<Path> dossiers = Files.list(backend.resolve("contexts"))) {
                contexts = dossiers.filter(p -> !p.getFileName().toString().equals("stock"))
                        .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            }
            List<String> fautifs = sourcesFautives(
                    Stream.of(racinesPlateforme, anciens, contexts).flatMap(List::stream).toList());

            assertThat(fautifs)
                    .as("les tables du Stock ne doivent être lues que par le contexte Stock")
                    .isEmpty();
        }
    }

    /**
     * RP3b (2026-11-13, ADR 0172) : verrou du jalon soldé. L'adapter d'annulation d'un lot de reprise ne contrôle plus {@code bons_sortie} en
     * SQL (nom de table calculé compris) ; l'exclusion des répertoires « migration » de la garde est supprimée.
     */
    @Test
    void l_annulation_d_un_lot_de_reprise_ne_touche_plus_les_tables_du_stock() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        Path adapter = backend.resolve("platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/adapter/migration/MigrationRollbackJdbcAdapter.java");
        String texte = Files.readString(adapter);

        assertThat(sourcesFautives(List.of(backend.resolve("platform/src/main/java")))).as("aucun source de la plateforme, répertoires migration compris")
                .isEmpty();
        assertThat(TABLES_DU_STOCK.stream().filter(t -> texte.contains("\"" + t + "\""))).as("aucune table du Stock nommée par l'adapter").isEmpty();
        assertThat(texte).doesNotContain("JdbcTemplate");
    }

    /**
     * DOC5j (ADR 0202) : le procès-verbal d'inventaire livré n'embarque plus de requête ; le Stock fournit les lignes de l'inventaire
     * ({@code LignesDuProcesVerbal}). Le gabarit ne nomme aucune table du Stock.
     */
    @Test
    void le_proces_verbal_d_inventaire_n_embarque_plus_de_requete() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        String xml = Files.readString(backend.resolve("contexts/documents/src/main/resources/reports/inventaire_stock.jrxml"));

        assertThat(xml).doesNotContain("queryString");
        assertThat(LECTURE_SQL.matcher(xml).results().map(m -> m.group(2).toLowerCase())).noneMatch(TABLES_DU_STOCK::contains);
    }

    /**
     * Preuve de non-régression de la garde : un source qui lit directement une table du Stock doit être signalé.
     */
    @Test
    void la_garde_detecte_une_lecture_directe_d_une_table_du_stock() throws IOException {
        Path bac = Files.createTempDirectory("garde-stock-s9");
        Files.writeString(bac.resolve("LectureDirecte.java"), """
                class LectureDirecte {
                    String lecture = "SELECT a.id FROM articles a WHERE a.center_id = ?";
                    String ecriture = "UPDATE stock_movements SET quantite = ? WHERE id = ?";
                    String legitime = "SELECT s.id FROM seances s WHERE s.center_id = ?";
                }
                """);

        assertThat(sourcesFautives(List.of(bac)))
                .as("la garde doit détecter une lecture de articles et une écriture de stock_movements")
                .hasSize(1);

        Path bacPropre = Files.createTempDirectory("garde-stock-propre");
        Files.writeString(bacPropre.resolve("LectureLegitime.java"), """
                class LectureLegitime {
                    String legitime = "SELECT s.id FROM seances s WHERE s.center_id = ?";
                }
                """);

        assertThat(sourcesFautives(List.of(bacPropre)))
                .as("une table d'un autre contexte ne doit pas être signalée")
                .isEmpty();
    }

    /**
     * Preuve de non-dérive : toute table lue en SQL par le contexte Stock doit figurer dans la liste gardée, sauf dette
     * explicitement listée dans {@link #TABLES_LUES_AILLEURS}.
     */
    @Test
    void la_liste_des_tables_couvre_les_tables_lues_par_le_stock() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        Path stock = backend.resolve("contexts/stock/src/main/java");

        Set<String> luesParLeStock = new TreeSet<>();
        try (Stream<Path> sources = sources(stock)) {
            for (Path source : sources.toList()) {
                Matcher m = LECTURE_SQL.matcher(Files.readString(source));
                while (m.find()) {
                    String table = m.group(2).toLowerCase();
                    if (!TABLES_LUES_AILLEURS.contains(table)) {
                        luesParLeStock.add(table);
                    }
                }
            }
        }

        assertThat(TABLES_DU_STOCK)
                .as("toute table lue en SQL par le contexte Stock doit être listée dans TABLES_DU_STOCK ou dans TABLES_LUES_AILLEURS")
                .containsAll(luesParLeStock);
        Set<String> manquantes = new TreeSet<>(luesParLeStock);
        manquantes.removeAll(TABLES_DU_STOCK);
        assertThat(manquantes).isEmpty();
    }
}
