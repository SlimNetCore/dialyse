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
 * Slice P9 : les tables de la Planification appartiennent au contexte Planification. Aucun source hors de ce contexte (plateforme,
 * anciens modules, autres contextes) ne doit les lire ou les écrire en SQL direct, sauf la liste blanche documentée ci-dessous : le
 * pilotage passe par le langage publié ({@code PlacesDesPatients}, {@code CapaciteDesCentres}, {@code SemaineDeLInfirmier}...).
 * Modèle : {@link TableDuStockTest}. Les colonnes de place de {@code patients} et les tables {@code salle} / {@code position_creneau}
 * appartiennent à Patient et couverture et à Organisation : hors de cette garde.
 *
 * <p>La garde est elle-même testée : une lecture directe est détectée, une table d'un autre contexte ne l'est pas, et la liste ne peut
 * pas dériver des tables réellement manipulées par le contexte.
 */
class TableDeLaPlanificationTest {

    private static final Set<String> TABLES_DE_LA_PLANIFICATION = Set.of(
            "infirmier", "infirmier_absence", "infirmier_affectation", "infirmier_remplacement", "infirmier_profil_planning",
            "planning_parametres", "planning_preference_patient", "deplacement_temporaire",
            "planification_optimisation", "planification_calendrier_case", "planification_reglages");

    /**
     * Tables lues par la Planification chez d'autres contextes (comptes, centres, patients, Organisation, séances, calendrier clinique).
     */
    private static final Set<String> TABLES_LUES_AILLEURS = Set.of(
            "patients", "salle", "position_creneau", "app_user", "app_user_role", "app_user_center", "app_role", "centers",
            "serologies_patient", "mouvement_patient", "seances", "center_holiday", "center_closure_day", "abords_vasculaires");

    /**
     * Liste blanche : sources hors contexte qui lisent encore une table de la Planification, avec la raison et le jalon (dette
     * documentée dans {@code .ddd/migration/roadmap.md}, « Dette de P9 »). Chaque ligne doit disparaître avec son jalon.
     */
    private static final Set<String> LISTE_BLANCHE = Set.of(
            // « Planning paramétré » lu par le tableau de bord de la Direction (capacité) : contexte Pilotage de la direction
            "DirectionCapaciteQueryService.java",
            // contrôle de présence du patient, règles recopiées dans le domaine Séance : contexte Séance
            "SeancePlanningJdbcAdapter.java",
            // « le créneau / la salle est-il utilisé par un patient ? » et usages des salles : contexte Organisation
            "ReferentialRepositoryAdapter.java", "ReferentialAdminTables.java");

    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private static List<String> sourcesFautives(List<Path> racines) {
        return racines.stream().filter(Files::isDirectory)
                .flatMap(TableDeLaPlanificationTest::sources)
                .filter(TableDeLaPlanificationTest::mentionneUneTableDeLaPlanification)
                .map(p -> p.getFileName().toString()).toList();
    }

    private static Stream<Path> sources(Path racine) {
        try {
            return Files.walk(racine)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !chemin(p).contains("/entity/"))
                    .filter(p -> !chemin(p).contains("/migration/"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String chemin(Path p) {
        return p.toString().replace('\\', '/');
    }

    private static boolean mentionneUneTableDeLaPlanification(Path source) {
        try {
            Matcher m = LECTURE_SQL.matcher(Files.readString(source));
            while (m.find()) {
                if (TABLES_DE_LA_PLANIFICATION.contains(m.group(2).toLowerCase())) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aucun_source_hors_du_contexte_ne_mentionne_les_tables_de_la_planification_hors_liste_blanche() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        List<Path> racinesPlateforme = List.of(
                backend.resolve("platform/src/main/java"), backend.resolve("shared-kernel/src/main/java"));
        try (Stream<Path> modules = Files.list(backend)) {
            List<Path> anciens = modules.filter(p -> p.getFileName().toString().startsWith("bc-"))
                    .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            List<Path> contexts;
            try (Stream<Path> dossiers = Files.list(backend.resolve("contexts"))) {
                contexts = dossiers.filter(p -> !p.getFileName().toString().equals("planification"))
                        .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList();
            }
            List<String> fautifs = sourcesFautives(
                    Stream.of(racinesPlateforme, anciens, contexts).flatMap(List::stream).toList()).stream()
                    .filter(nom -> !LISTE_BLANCHE.contains(nom)).toList();

            assertThat(fautifs)
                    .as("les tables de la Planification ne doivent être lues que par le contexte Planification (liste blanche exceptée)")
                    .isEmpty();
        }
    }

    @Test
    void la_garde_detecte_une_lecture_directe_d_une_table_de_la_planification() throws IOException {
        Path bac = Files.createTempDirectory("garde-planification-p9");
        Files.writeString(bac.resolve("LectureDirecte.java"), """
                class LectureDirecte {
                    String lecture = "SELECT a.id FROM infirmier_absence a WHERE a.center_id = ?";
                    String ecriture = "UPDATE deplacement_temporaire SET motif = ? WHERE id = ?";
                    String legitime = "SELECT s.id FROM seances s WHERE s.center_id = ?";
                }
                """);
        assertThat(sourcesFautives(List.of(bac))).as("une lecture de infirmier_absence est détectée").hasSize(1);

        Path propre = Files.createTempDirectory("garde-planification-propre");
        Files.writeString(propre.resolve("LectureLegitime.java"), """
                class LectureLegitime {
                    String legitime = "SELECT s.id FROM seances s WHERE s.center_id = ?";
                }
                """);
        assertThat(sourcesFautives(List.of(propre))).as("une table d'un autre contexte n'est pas signalée").isEmpty();
    }

    @Test
    void la_liste_des_tables_couvre_les_tables_lues_par_la_planification() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        Set<String> luesParLaPlanification = new TreeSet<>();
        try (Stream<Path> sources = sources(backend.resolve("contexts/planification/src/main/java"))) {
            for (Path source : sources.toList()) {
                Matcher m = LECTURE_SQL.matcher(Files.readString(source));
                while (m.find()) {
                    String table = m.group(2).toLowerCase();
                    if (!TABLES_LUES_AILLEURS.contains(table)) {
                        luesParLaPlanification.add(table);
                    }
                }
            }
        }
        assertThat(TABLES_DE_LA_PLANIFICATION)
                .as("toute table lue par la Planification est listée dans TABLES_DE_LA_PLANIFICATION ou TABLES_LUES_AILLEURS")
                .containsAll(luesParLaPlanification);
    }
}
