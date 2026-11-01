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
 * Slice O4 : les tables de l'Organisation appartiennent au contexte Organisation. Plus aucun source hors de ce contexte
 * (plateforme, anciens modules, autres contextes) ne doit les lire ou les écrire en SQL direct : le pilotage passe par le
 * langage publié {@code organisation.v1} ({@code CentresActifs}, {@code SallesActives}, etc.). Garde de non-régression
 * (les entités JPA et les migrations Flyway sont hors périmètre).
 */
class TableDeLOrganisationTest {

    private static final Set<String> TABLES_DE_L_ORGANISATION = Set.of(
            "societes", "centers", "medecin", "machine", "protocole", "salle", "position_creneau");

    private static final Set<String> TABLES_LUES_AILLEURS = Set.of();

    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private static List<String> sourcesFautives(List<Path> racines) {
        return racines.stream().filter(Files::isDirectory)
                .flatMap(TableDeLOrganisationTest::sources)
                .filter(TableDeLOrganisationTest::mentionneUneTableDeLOrganisation)
                .map(Path::toString).toList();
    }

    private static Stream<Path> sources(Path racine) {
        try {
            return Files.walk(racine)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !chemin(p).contains("/entity/"))
                    .filter(p -> !chemin(p).contains("/migration/"))
                    .filter(p -> !chemin(p).contains("/acl/"))
                    .filter(p -> !chemin(p).contains("/persistence/"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String chemin(Path p) {
        return p.toString().replace('\\', '/');
    }

    private static boolean mentionneUneTableDeLOrganisation(Path source) {
        try {
            Matcher m = LECTURE_SQL.matcher(Files.readString(source));
            while (m.find()) {
                if (TABLES_DE_L_ORGANISATION.contains(m.group(2).toLowerCase())) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aucun_source_hors_du_contexte_ne_mentionne_les_tables_de_l_organisation() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        List<Path> racinesPlateforme = List.of(
                backend.resolve("shared-kernel/src/main/java"));
        try (Stream<Path> modules = Files.list(backend)) {
            List<Path> anciens = modules.filter(p -> p.getFileName().toString().startsWith("bc-"))
                    .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            List<Path> contexts;
            try (Stream<Path> dossiers = Files.list(backend.resolve("contexts"))) {
                contexts = dossiers.filter(p -> !p.getFileName().toString().equals("organisation"))
                        .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            }
            List<String> fautifs = sourcesFautives(
                    Stream.of(racinesPlateforme, anciens, contexts).flatMap(List::stream).toList());

            assertThat(fautifs)
                    .as("les tables de l'Organisation ne doivent être lues que par le contexte Organisation")
                    .isEmpty();
        }
    }

    @Test
    void la_garde_detecte_une_lecture_directe_d_une_table_de_l_organisation() throws IOException {
        Path bac = Files.createTempDirectory("garde-organisation-o4");
        Files.writeString(bac.resolve("LectureDirecte.java"), """
                class LectureDirecte {
                    String lecture = "SELECT a.id FROM salle a WHERE a.center_id = ?";
                    String ecriture = "UPDATE centers SET nom = ? WHERE id = ?";
                    String legitime = "SELECT s.id FROM seances s WHERE s.center_id = ?";
                }
                """);

        assertThat(sourcesFautives(List.of(bac)))
                .as("la garde doit détecter une lecture de salle et une écriture de centers")
                .hasSize(1);

        Path bacPropre = Files.createTempDirectory("garde-organisation-propre");
        Files.writeString(bacPropre.resolve("LectureLegitime.java"), """
                class LectureLegitime {
                    String legitime = "SELECT s.id FROM seances s WHERE s.center_id = ?";
                }
                """);

        assertThat(sourcesFautives(List.of(bacPropre)))
                .as("une table d'un autre contexte ne doit pas être signalée")
                .isEmpty();
    }

    @Test
    void la_liste_des_tables_couvre_les_tables_lues_par_l_organisation() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        Path organisation = backend.resolve("contexts/organisation/src/main/java");

        Set<String> luesParLOrganisation = new TreeSet<>();
        try (Stream<Path> sources = sources(organisation)) {
            for (Path source : sources.toList()) {
                Matcher m = LECTURE_SQL.matcher(Files.readString(source));
                while (m.find()) {
                    String table = m.group(2).toLowerCase();
                    if (!TABLES_LUES_AILLEURS.contains(table)) {
                        luesParLOrganisation.add(table);
                    }
                }
            }
        }

        assertThat(TABLES_DE_L_ORGANISATION)
                .as("toute table lue en SQL par le contexte Organisation doit être listée")
                .containsAll(luesParLOrganisation);
        Set<String> manquantes = new TreeSet<>(luesParLOrganisation);
        manquantes.removeAll(TABLES_DE_L_ORGANISATION);
        assertThat(manquantes).isEmpty();
    }
}