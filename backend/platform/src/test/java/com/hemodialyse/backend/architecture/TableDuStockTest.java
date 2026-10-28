package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice S9 : les tables du Stock appartiennent au contexte Stock. Plus aucun source hors de ce contexte (plateforme, anciens
 * modules, autres contextes) ne doit les lire ou les écrire en SQL direct : le pilotage passe par le langage publié
 * {@code CatalogueDArticles}, {@code ConsommationsDeSeance}, {@code IndicateursDuStock}, {@code OperationsDeStock} et
 * {@code GroupesDArticlesDesCentres}. Garde de non-régression (les migrations Flyway sont hors périmètre).
 */
class TableDuStockTest {

    private static final Set<String> TABLES_STOCK = Set.of(
            "stock_movements", "consommations_de_seance", "sorties_pour_seances", "bons_sortie",
            "inventaires", "inventaire_lignes");

    private static Stream<Path> sources(Path racine) {
        try {
            return Files.walk(racine).filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/entity/"))
                    .filter(p -> !p.toString().contains("/migration/"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean mentionUneTableDuStock(Path source) {
        try {
            String contenu = Files.readString(source);
            return TABLES_STOCK.stream().anyMatch(table -> {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                        "(?i)(FROM|JOIN|INTO|UPDATE|TABLE)\\\\s+" + table + "\\\\b");
                return p.matcher(contenu).find();
            });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aucun_source_hors_du_contexte_ne_mentionne_les_tables_du_stock() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        List<Path> racines = List.of(backend.resolve("platform/src/main/java"), backend.resolve("shared-kernel/src/main/java"));
        try (Stream<Path> modules = Files.list(backend)) {
            List<Path> anciens = modules.filter(p -> p.getFileName().toString().startsWith("bc-"))
                    .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            List<Path> contexts;
            try (Stream<Path> dossiers = Files.list(backend.resolve("contexts"))) {
                contexts = dossiers.filter(p -> !p.getFileName().toString().equals("stock"))
                        .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            }
            List<String> fautifs = Stream.of(racines, anciens, contexts).flatMap(List::stream).filter(Files::isDirectory)
                    .flatMap(TableDuStockTest::sources)
                    .filter(TableDuStockTest::mentionUneTableDuStock).map(Path::toString).toList();

            assertThat(fautifs).isEmpty();
        }
    }
}
