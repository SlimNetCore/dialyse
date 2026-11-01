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
 * Slice DM8 : les tables du dossier médical appartiennent au contexte dossier-medical.
 * Plus aucun source hors de ce contexte (anciens modules, autres contextes) ne doit les
 * lire ou les écrire en SQL direct : les consommateurs passent par le langage publié
 * {@code dossiermedical.v1} ({@code ResultatExamenRecu}, {@code SerologiePositiveDetectee},
 * {@code BilanPreGreffeComplete}). Garde de non-régression (les entités JPA et les
 * migrations sont hors périmètre, ainsi que la plateforme legacy en cours de retrait).
 *
 * <p>La garde est elle-même testée : {@link #la_garde_detecte_une_lecture_directe_d_une_table_du_dossier_medical()}
 * prouve qu'une lecture directe est bien détectée, et
 * {@link #la_liste_des_tables_couvre_les_tables_lues_par_le_dossier_medical()} prouve que la
 * liste ne peut pas dériver des tables réellement manipulées par le contexte.
 */
class TableDuDossierMedicalTest {

    /**
     * Tables possédées par le dossier médical (schéma
     * {@code backend/contexts/dossier-medical/src/main/resources/db/schema.sql}).
     */
    private static final Set<String> TABLES_DU_DOSSIER_MEDICAL = Set.of(
            "demandes_examen", "lignes_demande_examen",
            "serologies",
            "antecedents", "allergies",
            "observations_biologiques",
            "bilans_pregreffe", "etapes_bilan_pregreffe");

    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private static List<String> sourcesFautives(List<Path> racines) {
        return racines.stream().filter(Files::isDirectory)
                .flatMap(TableDuDossierMedicalTest::sources)
                .filter(TableDuDossierMedicalTest::mentionneUneTableDuDossierMedical)
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

    private static boolean mentionneUneTableDuDossierMedical(Path source) {
        try {
            Matcher m = LECTURE_SQL.matcher(Files.readString(source));
            while (m.find()) {
                if (TABLES_DU_DOSSIER_MEDICAL.contains(m.group(2).toLowerCase())) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aucun_source_hors_du_contexte_ne_mentionne_les_tables_du_dossier_medical() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        List<Path> racinesPlateforme = List.of(
                backend.resolve("shared-kernel/src/main/java"));
        try (Stream<Path> modules = Files.list(backend)) {
            List<Path> anciens = modules.filter(p -> p.getFileName().toString().startsWith("bc-"))
                    .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            List<Path> contexts;
            try (Stream<Path> dossiers = Files.list(backend.resolve("contexts"))) {
                contexts = dossiers.filter(p -> !p.getFileName().toString().equals("dossier-medical"))
                        .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            }
            List<String> fautifs = sourcesFautives(
                    Stream.of(racinesPlateforme, anciens, contexts).flatMap(List::stream).toList());

            assertThat(fautifs)
                    .as("les tables du dossier médical ne doivent être lues que par le contexte dossier-medical")
                    .isEmpty();
        }
    }

    @Test
    void la_garde_detecte_une_lecture_directe_d_une_table_du_dossier_medical() throws IOException {
        Path bac = Files.createTempDirectory("garde-dossier-medical-dm8");
        Files.writeString(bac.resolve("LectureDirecte.java"), """
                class LectureDirecte {
                    String lecture = "SELECT a.id FROM serologies a WHERE a.center_id = ?";
                    String legitime = "SELECT s.id FROM seances s WHERE s.center_id = ?";
                }
                """);

        assertThat(sourcesFautives(List.of(bac)))
                .as("la garde doit détecter une lecture de serologies")
                .hasSize(1);

        Path bacPropre = Files.createTempDirectory("garde-dossier-medical-propre");
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
    void la_liste_des_tables_couvre_les_tables_lues_par_le_dossier_medical() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        Path contexte = backend.resolve("contexts/dossier-medical/src/main/java");

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
        effectivementLues.retainAll(TABLES_DU_DOSSIER_MEDICAL);

        assertThat(TABLES_DU_DOSSIER_MEDICAL)
                .as("toute table lue en SQL par le contexte dossier-medical doit être listée")
                .containsAll(effectivementLues);
    }
}
