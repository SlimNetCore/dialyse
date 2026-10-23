package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice A4 : la table {@code absence_patient} appartient au contexte Absences de patients. Plus aucun source de la plateforme (ni
 * des anciens modules, ni des autres contextes) ne la lit ou ne l'écrit : le pilotage passe par le langage publié {@code AbsencesDuPilotage}, la Facturation
 * par {@code DeclarationDesAbsences}. Garde de non-régression de l'étranglement (les migrations Flyway sont hors périmètre).
 */
class TableDesAbsencesDePatientsTest {

    @Test
    void aucun_source_hors_du_contexte_ne_mentionne_la_table_des_absences_de_patients() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        List<Path> racines = List.of(backend.resolve("platform/src/main/java"), backend.resolve("shared-kernel/src/main/java"));
        try (Stream<Path> modules = Files.list(backend)) {
            List<Path> anciens = modules.filter(p -> p.getFileName().toString().startsWith("bc-"))
                    .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            List<Path> contexts;
            try (Stream<Path> dossiers = Files.list(backend.resolve("contexts"))) {
                contexts = dossiers.filter(p -> !p.getFileName().toString().equals("absences-patients"))
                        .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            }
            List<String> fautifs = Stream.of(racines, anciens, contexts).flatMap(List::stream).filter(Files::isDirectory)
                    .flatMap(TableDesAbsencesDePatientsTest::sources)
                    .filter(TableDesAbsencesDePatientsTest::mentionLaTable).map(Path::toString).toList();

            assertThat(fautifs).isEmpty();
        }
    }

    private static Stream<Path> sources(Path racine) {
        try {
            return Files.walk(racine).filter(p -> p.toString().endsWith(".java"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean mentionLaTable(Path source) {
        try {
            return Files.readString(source).contains("absence_patient");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
