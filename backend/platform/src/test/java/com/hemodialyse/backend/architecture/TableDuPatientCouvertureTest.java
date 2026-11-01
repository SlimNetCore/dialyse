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
 * Slice PC5 : les tables de Patient &amp; couverture appartiennent au contexte patient-couverture. Plus aucun source hors de ce
 * contexte (plateforme, anciens modules, autres contextes) ne doit les lire ou les écrire en SQL direct : le pilotage passe
 * par le langage publié {@code patientcouverture.v1} et les ports publiés. Garde de non-régression (les entités JPA et les
 * migrations Flyway sont hors périmètre).
 */
class TableDuPatientCouvertureTest {

    private static final Set<String> TABLES_DU_PATIENT_COUVERTURE = Set.of(
            "patients", "mouvement_patient", "assure", "assure_patient", "prise_en_charge", "attestation_droit");

    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private static List<String> sourcesFautives(List<Path> racines) {
        return racines.stream().filter(Files::isDirectory)
                .flatMap(TableDuPatientCouvertureTest::sources)
                .filter(TableDuPatientCouvertureTest::mentionneUneTableDuPatientCouverture)
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

    private static boolean mentionneUneTableDuPatientCouverture(Path source) {
        try {
            Matcher m = LECTURE_SQL.matcher(Files.readString(source));
            while (m.find()) {
                if (TABLES_DU_PATIENT_COUVERTURE.contains(m.group(2).toLowerCase())) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aucun_source_hors_du_contexte_ne_mentionne_les_tables_du_patient_couverture() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        List<Path> racinesPlateforme = List.of(
                backend.resolve("shared-kernel/src/main/java"));
        try (Stream<Path> modules = Files.list(backend)) {
            List<Path> anciens = modules.filter(p -> p.getFileName().toString().startsWith("bc-"))
                    .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            List<Path> contexts;
            try (Stream<Path> dossiers = Files.list(backend.resolve("contexts"))) {
                contexts = dossiers.filter(p -> !p.getFileName().toString().equals("patient-couverture"))
                        .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            }
            List<String> fautifs = sourcesFautives(
                    Stream.of(racinesPlateforme, anciens, contexts).flatMap(List::stream).toList());

            assertThat(fautifs)
                    .as("les tables de Patient & couverture ne doivent être lues que par le contexte patient-couverture")
                    .isEmpty();
        }
    }

    @Test
    void la_garde_detecte_une_lecture_directe_d_une_table_du_patient_couverture() throws IOException {
        Path bac = Files.createTempDirectory("garde-patient-couverture-pc5");
        Files.writeString(bac.resolve("LectureDirecte.java"), """
                class LectureDirecte {
                    String lecture = "SELECT a.id FROM patients a WHERE a.center_id = ?";
                    String ecriture = "UPDATE prise_en_charge SET statut = ? WHERE id = ?";
                    String legitime = "SELECT s.id FROM seances s WHERE s.center_id = ?";
                }
                """);

        assertThat(sourcesFautives(List.of(bac)))
                .as("la garde doit détecter une lecture de patients et une écriture de prise_en_charge")
                .hasSize(1);

        Path bacPropre = Files.createTempDirectory("garde-patient-couverture-propre");
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
    void la_liste_des_tables_couvre_les_tables_lues_par_le_patient_couverture() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        Path contexte = backend.resolve("contexts/patient-couverture/src/main/java");

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

        // On ne conserve que les tables du périmètre déclaré.
        Set<String> effectivementLues = new TreeSet<>(luesParLeContexte);
        effectivementLues.retainAll(TABLES_DU_PATIENT_COUVERTURE);

        assertThat(TABLES_DU_PATIENT_COUVERTURE)
                .as("toute table lue en SQL par le contexte patient-couverture doit être listée")
                .containsAll(effectivementLues);
    }
}