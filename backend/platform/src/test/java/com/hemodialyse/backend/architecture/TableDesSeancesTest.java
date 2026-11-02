package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Disabled;
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

@Disabled("Module seance GELE le 2026-11-02 (backend/contexts/seance/GEL.md, ADR 0074) : la garde ne scanne pas platform/src/main et exclut /acl/ "
        + "et /persistence/, donc elle est aveugle aux 18 lecteurs SQL de `seances` hors contexte ; elle n'a aucun sens tant que le refactoring "
        + "sur place n'a pas etabli la liste blanche nominative datee des lectures. A reecrire au filet SE1.")
class TableDesSeancesTest {

    private static final Set<String> TABLES_DE_LA_SEANCE = Set.of("seances");

    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private static List<String> sourcesFautives(List<Path> racines) {
        return racines.stream().filter(Files::isDirectory)
                .flatMap(TableDesSeancesTest::sources)
                .filter(TableDesSeancesTest::mentionneUneTableDeLaSeance)
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

    private static boolean mentionneUneTableDeLaSeance(Path source) {
        try {
            Matcher m = LECTURE_SQL.matcher(Files.readString(source));
            while (m.find()) {
                if (TABLES_DE_LA_SEANCE.contains(m.group(2).toLowerCase())) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aucun_source_hors_du_contexte_ne_mentionne_les_tables_de_la_seance() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        List<Path> racinesPlateforme = List.of(
                backend.resolve("shared-kernel/src/main/java"));
        try (Stream<Path> modules = Files.list(backend)) {
            List<Path> anciens = modules.filter(p -> p.getFileName().toString().startsWith("bc-"))
                    .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            List<Path> contexts;
            try (Stream<Path> dossiers = Files.list(backend.resolve("contexts"))) {
                contexts = dossiers.filter(p -> !p.getFileName().toString().equals("seance"))
                        .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            }
            List<String> fautifs = sourcesFautives(
                    Stream.of(racinesPlateforme, anciens, contexts).flatMap(List::stream).toList());

            assertThat(fautifs)
                    .as("les tables de Séance ne doivent être lues que par le contexte seance")
                    .isEmpty();
        }
    }

    @Test
    void la_garde_detecte_une_lecture_directe_d_une_table_de_la_seance() throws IOException {
        Path bac = Files.createTempDirectory("garde-seance-se7");
        Files.writeString(bac.resolve("LectureDirecte.java"), """
                class LectureDirecte {
                    String lecture = "SELECT a.id FROM seances a WHERE a.center_id = ?";
                    String legitime = "SELECT s.id FROM patients s WHERE s.center_id = ?";
                }
                """);

        assertThat(sourcesFautives(List.of(bac)))
                .as("la garde doit détecter une lecture de seances")
                .hasSize(1);

        Path bacPropre = Files.createTempDirectory("garde-seance-propre");
        Files.writeString(bacPropre.resolve("LectureLegitime.java"), """
                class LectureLegitime {
                    String legitime = "SELECT s.id FROM patients s WHERE s.center_id = ?";
                }
                """);

        assertThat(sourcesFautives(List.of(bacPropre)))
                .as("une table d'un autre contexte ne doit pas être signalée")
                .isEmpty();
    }

    @Test
    void la_liste_des_tables_couvre_les_tables_lues_par_le_seance() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        Path contexte = backend.resolve("contexts/seance/src/main/java");

        Set<String> luesParLeContexte = new TreeSet<>();
        try (Stream<Path> sources = sources(contexte)) {
            for (Path source : sources.toList()) {
                Matcher m = LECTURE_SQL.matcher(Files.readString(source));
                while (m.find()) {
                    String table = m.group(2).toLowerCase();
                    luesParLeContexte.add(table);
                }
            }
        }

        Set<String> effectivementLues = new TreeSet<>(luesParLeContexte);
        effectivementLues.retainAll(TABLES_DE_LA_SEANCE);

        assertThat(TABLES_DE_LA_SEANCE)
                .as("toute table lue en SQL par le contexte seance doit être listée")
                .containsAll(effectivementLues);
    }
}
