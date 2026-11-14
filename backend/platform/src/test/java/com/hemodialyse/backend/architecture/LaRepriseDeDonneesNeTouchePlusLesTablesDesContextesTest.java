package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RP3c (2026-11-14, ADR 0173) : verrou de fin de la reprise de données (RP1 à RP3c). L'outil de reprise de la plateforme (packages
 * {@code infrastructure/migration}, {@code persistence/adapter/migration}, {@code MigrationRestController}, {@code MigrationDtos},
 * {@code MigrationApplicationService}) n'écrit ni ne lit plus AUCUNE table d'un autre contexte : les seules tables nommées par une requête
 * sont les siennes ({@code migration_batch}, {@code migration_entity_run}, {@code migration_id_map}, {@code migration_value_map}).
 * Les destinataires sont atteints par leurs langages publiés (séances SE7e, soldes F6, couverture RP1/RP3a, médical RP2/RP3b, stock RP3b).
 * Aucune exception, aucun butoir : une entrée ne peut pas être ajoutée ici.
 */
class LaRepriseDeDonneesNeTouchePlusLesTablesDesContextesTest {

    private static final Set<String> TABLES_DE_LA_REPRISE = Set.of("migration_batch", "migration_entity_run", "migration_id_map", "migration_value_map");

    private static final Pattern TABLE_DE_REQUETE = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE)\\s+(?:public\\.)?([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);

    /** Seuls trois adapters de la reprise tiennent des requêtes : sur leurs propres tables. */
    private static final Set<String> ADAPTERS_DES_TABLES_DE_LA_REPRISE = Set.of("MigrationBatchJdbcAdapter.java", "IdMappingJdbcAdapter.java",
            "ValueMappingJdbcAdapter.java");

    private static Path plateforme() {
        return Path.of("..").toAbsolutePath().normalize().resolve("platform/src/main/java/com/hemodialyse/backend");
    }

    private static List<Path> sourcesDeLaReprise() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (String dossier : List.of("infrastructure/migration", "infrastructure/persistence/adapter/migration")) {
            try (Stream<Path> fichiers = Files.walk(plateforme().resolve(dossier))) {
                fichiers.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
            }
        }
        for (String fichier : List.of("infrastructure/web/rest/MigrationRestController.java", "infrastructure/web/dto/migration/MigrationDtos.java",
                "application/migration/MigrationApplicationService.java")) {
            sources.add(plateforme().resolve(fichier));
        }
        return sources;
    }

    @Test
    void les_seules_tables_nommees_par_une_requete_de_la_reprise_sont_les_siennes() throws IOException {
        List<String> intruses = new ArrayList<>();
        for (Path source : sourcesDeLaReprise()) {
            String texte = Files.readString(source);
            Matcher m = TABLE_DE_REQUETE.matcher(texte);
            while (m.find()) {
                String ligne = ligneDe(texte, m.start()).trim();
                // les commentaires ne sont pas du SQL ; une requête est un littéral de chaîne (ou une bloc de texte) sur sa ligne
                if (ligne.startsWith("*") || ligne.startsWith("//") || ligne.startsWith("/*")) continue;
                String table = m.group(2).toLowerCase();
                if (!TABLES_DE_LA_REPRISE.contains(table)) intruses.add(source.getFileName() + " : " + table);
            }
        }
        assertThat(intruses).as("tables d'un autre contexte nommées par une requête de la reprise : passer par le langage publié du propriétaire")
                .isEmpty();
    }

    @Test
    void seuls_les_adapters_des_tables_de_la_reprise_possedent_un_jdbc_template() throws IOException {
        Set<String> porteurs = new TreeSet<>();
        for (Path source : sourcesDeLaReprise()) {
            String texte = Files.readString(source);
            if (texte.contains("JdbcTemplate") || texte.contains("NamedParameterJdbcTemplate") || texte.contains("EntityManager")) {
                porteurs.add(source.getFileName().toString());
            }
        }
        assertThat(ADAPTERS_DES_TABLES_DE_LA_REPRISE).containsAll(porteurs);
        assertThat(porteurs).doesNotContain("HistoricalRecordJdbcAdapter.java", "MigrationRollbackJdbcAdapter.java", "OpeningBalanceJdbcAdapter.java");
    }

    private static String ligneDe(String texte, int position) {
        int debut = texte.lastIndexOf('\n', position) + 1;
        int fin = texte.indexOf('\n', position);
        return texte.substring(debut, fin < 0 ? texte.length() : fin);
    }
}
