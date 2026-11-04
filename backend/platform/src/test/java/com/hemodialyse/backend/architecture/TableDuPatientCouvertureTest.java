package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice PC5 : les tables de Patient &amp; couverture ({@code patients}, {@code mouvement_patient}, {@code assure},
 * {@code assure_patient}, {@code prise_en_charge}, {@code attestation_droit}) appartiennent au contexte patient-couverture. Plus aucun
 * source hors de ce contexte ne doit les lire ou les écrire en SQL direct : les autres contextes passent par le langage publié
 * {@code patientcouverture.v1} ({@code FichesDesPatients}, {@code CouvertureDesPatients}, {@code CompteursDeCouverture}).
 * <p>
 * Garde de NON-RÉGRESSION sur le modèle de {@code TableDeLOrganisationTest} : les lecteurs et écrivains restants sont une LISTE
 * BLANCHE NOMMÉE, DATÉE, avec la raison et l'échéance de chaque jalon (acceptés le 2026-11-04). Un nouveau lecteur hors liste fait
 * échouer le test ; une entrée morte (fichier disparu ou migré) aussi : la liste doit être réduite.
 */
class TableDuPatientCouvertureTest {

    private static final Set<String> TABLES_DU_PATIENT_COUVERTURE = Set.of(
            "patients", "mouvement_patient", "assure", "assure_patient", "prise_en_charge", "attestation_droit");

    // Multiligne : « FROM » en fin de ligne et le nom de la table sur la suivante sont aussi détectés.
    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);

    private static final String PLACE = "2026-11-04 : colonnes de PLACE de patients, propriété de la Planification (ADR 0052) ; "
            + "échéance : inversion de la propriété de la place, hors périmètre (aucune échéance)";
    private static final String CYCLE = "2026-11-04 : dépendance Maven CIRCULAIRE (contexts-patient-couverture dépend de "
            + "contexts-planification) ; échéance : extraction des interfaces publiées dans un module d'API partagé (le port est prêt)";
    private static final String JOINTURE = "2026-11-04 : jointure avec les tables du contexte consommateur dans un GROUP BY / un tri "
            + "(la découper changerait le résultat) ; échéance : reprise du contexte consommateur";

    /** Lecteurs et écrivains restants hors du contexte (chemin depuis {@code backend/}) : jalons acceptés le 2026-11-04. */
    private static final Map<String, String> JALONS = jalons(
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/PlacesDuCentreJdbc.java", PLACE + " ; ÉCRIT la place",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/PlacesTenuesJdbc.java", PLACE + " ; ÉCRIT la place",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/MouvementsDesPatientsJdbc.java", PLACE + " ; ÉCRIT le mouvement PLACE_LIBEREE",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/PlanningDesPlacesDuCentreJdbc.java", PLACE,
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/SejoursDesPatientsJdbc.java", PLACE,
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacyseance/SeancesRealiseesJdbc.java", PLACE + " (jointure séances-patients)",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/persistence/LectureDesPreferencesJdbc.java", PLACE,
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/ContraintesDesPatientsJdbc.java",
            "2026-11-04 : lecture croisée Planification / dossier médical (transporteurs, date de naissance, abords vasculaires), pas une lecture de fiche publiée ; échéance : reprise du dossier médical",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/NomsDesPatientsJdbc.java", CYCLE + " (port FichesDesPatients.noms)",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/PatientsDuCentreJdbc.java", CYCLE + " (port FichesDesPatients.situation)",
            "contexts/facturation/src/main/java/com/hemodialyse/backend/contexts/facturation/infrastructure/acl/legacyseance/SeancesFacturablesJdbc.java", JOINTURE + " : séances x patients, tri nom/prénom, payeur (la partie PEC passe par le langage publié)",
            "contexts/facturation/src/main/java/com/hemodialyse/backend/contexts/facturation/infrastructure/persistence/SituationsDesFacturesJdbc.java", JOINTURE + " : six tables de Facturation, KB-FAC-49",
            "contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/persistence/PecValidationJdbc.java",
            "2026-11-04 : code MORT, contexts/seance est gelé et HORS réacteur (ADR 0074) ; échéance : reprise ou suppression du module Séance (le port CouvertureDesPatients.pecValidee est prêt)",
            "platform/src/main/java/com/hemodialyse/backend/application/direction/DirectionBreakdownQueryService.java", JOINTURE + " : agrégats de la Direction avec le prédicat d'effectif",
            "platform/src/main/java/com/hemodialyse/backend/application/direction/DirectionDashboardQueryService.java", JOINTURE + " : agrégats de la Direction avec le prédicat d'effectif",
            "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/adapter/SeancePlanningJdbcAdapter.java", PLACE + " (jours de dialyse)",
            "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/adapter/migration/MigrationRollbackJdbcAdapter.java",
            "2026-11-04 : outil de migration, écrit/supprime en bloc (patients, assure, assure_patient) ; à lancer hors activité du centre (ADR 0077 point 12, ADR 0078 point 10) ; échéance : reprise de la migration par le module",
            "platform/src/main/java/com/hemodialyse/backend/infrastructure/web/rest/SeanceRestController.java",
            "2026-11-04 : contrôleur de 1300 lignes de la Séance (tableau de bord, journal), hors ports ; échéance : reprise du contexte Séance");

    private static Map<String, String> jalons(String... couples) {
        Map<String, String> m = new TreeMap<>();
        for (int i = 0; i < couples.length; i += 2) m.put(couples[i], couples[i + 1]);
        return m;
    }

    private static Path backend() {
        return Path.of("..").toAbsolutePath().normalize();
    }

    private static String chemin(Path p) {
        return p.toString().replace('\\', '/');
    }

    private static String relatif(Path backend, Path p) {
        return chemin(backend.relativize(p));
    }

    private static Stream<Path> sources(Path racine) {
        try {
            return Files.walk(racine).filter(p -> p.toString().endsWith(".java"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Racines {@code src/main/java} de tout le backend hors du contexte patient-couverture. */
    private static List<Path> racinesHorsDuContexte(Path backend) throws IOException {
        try (Stream<Path> dossiers = Files.list(backend)) {
            List<Path> modules = dossiers.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals("contexts")).toList();
            List<Path> racines = new java.util.ArrayList<>(modules.stream().map(p -> p.resolve("src/main/java"))
                    .filter(Files::isDirectory).toList());
            try (Stream<Path> contextes = Files.list(backend.resolve("contexts"))) {
                racines.addAll(contextes.filter(p -> !p.getFileName().toString().equals("patient-couverture"))
                        .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList());
            }
            return racines;
        }
    }

    private static boolean mentionneUneTable(Path source) {
        try {
            Matcher m = LECTURE_SQL.matcher(Files.readString(source));
            while (m.find()) {
                if (TABLES_DU_PATIENT_COUVERTURE.contains(m.group(2).toLowerCase())) return true;
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> sourcesFautives(Path backend, List<Path> racines) {
        Set<String> fautives = new TreeSet<>();
        racines.stream().filter(Files::isDirectory).flatMap(TableDuPatientCouvertureTest::sources)
                .filter(TableDuPatientCouvertureTest::mentionneUneTable).forEach(p -> fautives.add(relatif(backend, p)));
        return fautives;
    }

    @Test
    void aucun_nouveau_lecteur_sql_des_tables_du_patient_couverture_hors_de_la_liste_blanche() throws IOException {
        Path backend = backend();
        Set<String> nouveaux = new TreeSet<>(sourcesFautives(backend, racinesHorsDuContexte(backend)));
        nouveaux.removeAll(JALONS.keySet());

        assertThat(nouveaux)
                .as("nouveaux lecteurs SQL des tables de Patient & couverture : passer par patientcouverture.v1 (FichesDesPatients, "
                        + "CouvertureDesPatients, CompteursDeCouverture) ou les inscrire (nommés, datés, avec raison et échéance)")
                .isEmpty();
    }

    @Test
    void aucune_entree_morte_dans_la_liste_blanche() throws IOException {
        Path backend = backend();
        Set<String> lecteurs = sourcesFautives(backend, racinesHorsDuContexte(backend));
        Set<String> mortes = new TreeSet<>(JALONS.keySet());
        mortes.removeAll(lecteurs);

        assertThat(mortes)
                .as("entrées mortes de la liste blanche (fichier disparu ou migré vers le langage publié) : réduire la liste")
                .isEmpty();
    }

    @Test
    void chaque_jalon_porte_une_date_et_une_echeance() {
        assertThat(JALONS.values()).allSatisfy(raison -> {
            assertThat(raison).startsWith("2026-11-04");
            assertThat(raison).contains("échéance");
        });
    }

    @Test
    void la_garde_detecte_une_lecture_directe_meme_sur_deux_lignes_et_ignore_les_autres_tables() throws IOException {
        Path bac = Files.createTempDirectory("garde-patient-couverture-pc5");
        Files.writeString(bac.resolve("LectureDirecte.java"), """
                class LectureDirecte {
                    String lecture = "SELECT a.id FROM patients a WHERE a.center_id = ?";
                    String ecriture = "UPDATE prise_en_charge SET statut = ? WHERE id = ?";
                    String legitime = "SELECT s.id FROM seances s WHERE s.center_id = ?";
                }
                """);
        Files.writeString(bac.resolve("LectureSurDeuxLignes.java"), """
                class LectureSurDeuxLignes {
                    String lecture = "SELECT COUNT(1) FROM "
                            + "attestation_droit WHERE center_id = ?";
                    String jointure = \"""
                            SELECT s.id
                            FROM seances s
                            JOIN assure_patient ap ON ap.patient_id = s.patient_id
                            \""";
                }
                """);

        assertThat(sourcesFautives(bac, List.of(bac))).containsExactlyInAnyOrder("LectureDirecte.java", "LectureSurDeuxLignes.java");

        Path bacPropre = Files.createTempDirectory("garde-patient-couverture-propre");
        Files.writeString(bacPropre.resolve("LectureLegitime.java"), """
                class LectureLegitime {
                    String legitime = "SELECT s.id FROM seances s WHERE s.center_id = ?";
                }
                """);

        assertThat(sourcesFautives(bacPropre, List.of(bacPropre))).as("une table d'un autre contexte n'est pas signalée").isEmpty();
    }

    @Test
    void la_liste_des_tables_couvre_les_tables_lues_par_le_patient_couverture() throws IOException {
        Path contexte = backend().resolve("contexts/patient-couverture/src/main/java");

        Set<String> luesParLeContexte = new TreeSet<>();
        try (Stream<Path> sources = sources(contexte)) {
            for (Path source : sources.toList()) {
                Matcher m = LECTURE_SQL.matcher(Files.readString(source));
                while (m.find()) luesParLeContexte.add(m.group(2).toLowerCase());
            }
        }
        Set<String> effectivementLues = new TreeSet<>(luesParLeContexte);
        effectivementLues.retainAll(TABLES_DU_PATIENT_COUVERTURE);

        assertThat(TABLES_DU_PATIENT_COUVERTURE)
                .as("toute table lue en SQL par le contexte patient-couverture doit être listée")
                .containsAll(effectivementLues);
    }
}
