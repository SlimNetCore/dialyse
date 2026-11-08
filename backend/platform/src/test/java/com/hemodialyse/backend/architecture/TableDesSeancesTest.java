package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * La table {@code seances} appartient au contexte Séance. Garde réécrite par SE7c1 (ADR 0096) : elle remplace la garde désactivée depuis
 * SE1. Elle scanne TOUT le code de production (plateforme, anciens modules, autres contextes, ACL et adapters de persistance compris),
 * sauf le contexte Séance et les entités JPA. Les lecteurs actuels sont une liste blanche NOMMÉE, DATÉE, avec une revue au plus tard le
 * {@link #REVUE} et un BUTOIR PROPRE à chaque entrée :
 * <ul>
 *   <li>un nouveau lecteur, ou un lecteur qui n'est pas listé, fait échouer le test ;</li>
 *   <li>une entrée qui ne lit plus {@code seances} (fichier disparu ou migré) est une entrée morte : le test échoue, la liste doit
 *   être réduite (c'est ainsi que l'avancement de SE7c se mesure) ;</li>
 *   <li>le contexte Stock n'a plus aucune entrée (SE7c1) et ne peut plus en avoir.</li>
 * </ul>
 */
class TableDesSeancesTest {

    /** Revue de toute la liste au plus tard à cette date ; chaque entrée porte en plus son butoir. */
    private static final LocalDate REVUE = LocalDate.of(2026, 12, 15);

    private record Lecteur(String nature, LocalDate butoir) {
    }

    private static final LocalDate FIN_DE_SE7C2 = LocalDate.of(2027, 1, 31);
    private static final LocalDate LECTURES_PUBLIEES = LocalDate.of(2027, 3, 31);
    private static final LocalDate RETRAIT_DU_PILOTAGE_LEGACY = LocalDate.of(2027, 6, 30);

    /** Lecteurs SQL connus hors du contexte Séance (chemin depuis {@code backend/}) : 2026-11-08, SE7c1. */
    private static final Map<String, Lecteur> LECTEURS_CONNUS = new TreeMap<>();

    private static void lecteur(String chemin, String nature, LocalDate butoir) {
        LECTEURS_CONNUS.put(chemin, new Lecteur(nature, butoir));
    }

    static {
        // Facturation : sous-slice c2 (ARGENT : 5 UPDATE), hors du périmètre de c1.
        String fact = "contexts/facturation/src/main/java/com/hemodialyse/backend/contexts/facturation/infrastructure/";
        lecteur(fact + "acl/legacyseance/SeancesDeFacturationJdbc.java", "lecture + 4 UPDATE (statut ABSENT, exclusion, statut, forfait imposé), c2", FIN_DE_SE7C2);
        lecteur(fact + "acl/legacyseance/LiaisonDesSeancesJdbc.java", "UPDATE facture_id / statut FACTUREE, c2", FIN_DE_SE7C2);
        lecteur(fact + "acl/legacyseance/SeancesFacturablesJdbc.java", "lecture des séances facturables, c2", FIN_DE_SE7C2);
        lecteur(fact + "persistence/LectureDeLaSyntheseJdbc.java", "lecture de la synthèse mensuelle, c2", FIN_DE_SE7C2);
        lecteur(fact + "persistence/LectureDuTableauDeBordJdbc.java", "lecture du pilotage de la Facturation, c2", FIN_DE_SE7C2);
        // Autres contextes refaits : lecture seule, à remplacer par seance.v1 (lectures publiées à décider avec chacun).
        lecteur("contexts/absences-patients/src/main/java/com/hemodialyse/backend/contexts/absencespatients/infrastructure/acl/legacyseance/SeancesRealiseesJdbc.java",
                "séances réalisées d'un patient (lecture)", LECTURES_PUBLIEES);
        lecteur("contexts/absences-patients/src/main/java/com/hemodialyse/backend/contexts/absencespatients/infrastructure/acl/legacyseance/ExclusionsDeSeancesJdbc.java",
                "exclusion de séances pour absence (lecture)", LECTURES_PUBLIEES);
        lecteur("contexts/absences-patients/src/main/java/com/hemodialyse/backend/contexts/absencespatients/infrastructure/acl/legacyplanning/PatientsAttendusJdbc.java",
                "patients attendus (jointure séances, lecture)", LECTURES_PUBLIEES);
        lecteur("contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacyseance/SeancesRealiseesJdbc.java",
                "séances réalisées pour la grille de la semaine (lecture)", LECTURES_PUBLIEES);
        lecteur("contexts/dossier-medical/src/main/java/com/hemodialyse/backend/contexts/dossiermedical/infrastructure/persistence/HistoriqueDesConstantesJdbc.java",
                "historique des constantes (jointure séances, lecture)", LECTURES_PUBLIEES);
        // Plateforme (legacy) : fournisseurs du langage publié de la Séance, pilotage de la Direction, rapports, migration.
        String plateforme = "platform/src/main/java/com/hemodialyse/backend/";
        lecteur(plateforme + "infrastructure/acl/seance/HistoriqueFourniLegacy.java", "historique des séances fourni à la Séance (lecture)", LECTURES_PUBLIEES);
        lecteur(plateforme + "infrastructure/acl/seance/ForfaitsFournisLegacy.java", "forfaits fournis à la Séance (lecture)", LECTURES_PUBLIEES);
        lecteur(plateforme + "application/direction/DirectionDashboardQueryService.java", "tableau de bord de la Direction (lecture)", RETRAIT_DU_PILOTAGE_LEGACY);
        lecteur(plateforme + "application/direction/DirectionBreakdownQueryService.java", "répartition de la Direction (lecture)", RETRAIT_DU_PILOTAGE_LEGACY);
        lecteur(plateforme + "application/direction/DirectionAbsencesQueryService.java", "absences de la Direction (lecture)", RETRAIT_DU_PILOTAGE_LEGACY);
        lecteur(plateforme + "application/query/PatientStatsQueryService.java", "statistiques du patient (lecture)", RETRAIT_DU_PILOTAGE_LEGACY);
        lecteur(plateforme + "infrastructure/reporting/CahierDialyseReportService.java", "cahier de dialyse imprimé (lecture)", RETRAIT_DU_PILOTAGE_LEGACY);
        lecteur(plateforme + "infrastructure/persistence/adapter/migration/MigrationRollbackJdbcAdapter.java",
                "annulation d'un lot de migration (SUPPRESSION de séances importées)", RETRAIT_DU_PILOTAGE_LEGACY);
    }

    private static final Set<String> TABLES_DE_LA_SEANCE = Set.of("seances");

    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);

    private static Path backend() {
        return Path.of("..").toAbsolutePath().normalize();
    }

    private static String chemin(Path p) {
        return p.toString().replace('\\', '/');
    }

    private static String relatif(Path p) {
        return chemin(backend().relativize(p));
    }

    /** Tous les sources de production de {@code racine} (le contexte Séance et les entités JPA exceptés). */
    private static List<Path> sourcesDeProduction(Path racine) throws IOException {
        List<Path> trouves = new ArrayList<>();
        try (Stream<Path> modules = Stream.concat(Files.list(racine), Files.list(racine.resolve("contexts")))) {
            for (Path module : modules.toList()) {
                Path java = module.resolve("src/main/java");
                if (!Files.isDirectory(java) || chemin(module).endsWith("/contexts/seance")) continue;
                try (Stream<Path> fichiers = Files.walk(java)) {
                    fichiers.filter(p -> p.toString().endsWith(".java")).filter(p -> !chemin(p).contains("/entity/")).forEach(trouves::add);
                }
            }
        }
        return trouves;
    }

    private static boolean mentionneLaTableDeLaSeance(Path source) {
        try {
            Matcher m = LECTURE_SQL.matcher(Files.readString(source));
            while (m.find()) {
                if (TABLES_DE_LA_SEANCE.contains(m.group(2).toLowerCase())) return true;
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aucun_nouveau_lecteur_sql_de_la_table_des_seances_hors_de_la_liste_blanche() throws IOException {
        Set<String> nouveaux = new TreeSet<>();
        for (Path source : sourcesDeProduction(backend())) {
            if (mentionneLaTableDeLaSeance(source) && !LECTEURS_CONNUS.containsKey(relatif(source))) nouveaux.add(relatif(source));
        }
        assertThat(nouveaux)
                .as("nouveaux lecteurs SQL de seances : passer par seance.v1 (publishedlanguage) ou les inscrire (nommés, datés, butoir)")
                .isEmpty();
    }

    @Test
    void aucune_entree_morte_dans_la_liste_blanche() throws IOException {
        Set<String> morts = new TreeSet<>();
        for (String chemin : LECTEURS_CONNUS.keySet()) {
            Path source = backend().resolve(chemin);
            if (!Files.isRegularFile(source) || !mentionneLaTableDeLaSeance(source)) morts.add(chemin);
        }
        assertThat(morts)
                .as("entrées qui ne lisent plus seances : les retirer (avancement de SE7c)")
                .isEmpty();
    }

    @Test
    void chaque_entree_a_un_butoir_propre_et_la_liste_est_a_revoir_au_plus_tard_le_2026_12_15() {
        assertThat(LECTEURS_CONNUS).isNotEmpty();
        LECTEURS_CONNUS.forEach((chemin, lecteur) -> {
            assertThat(lecteur.nature()).as("nature de " + chemin).isNotBlank();
            assertThat(lecteur.butoir()).as("butoir de " + chemin).isAfter(REVUE);
        });
    }

    @Test
    void le_contexte_stock_ne_lit_plus_la_table_des_seances() throws IOException {
        List<String> stock = new ArrayList<>();
        for (Path source : sourcesDeProduction(backend())) {
            if (relatif(source).startsWith("contexts/stock/") && mentionneLaTableDeLaSeance(source)) stock.add(relatif(source));
        }
        assertThat(stock).as("le Stock passe par FacturationDesSeancesPubliee et PlacesDesSeancesPubliees (ADR 0096)").isEmpty();
        assertThat(LECTEURS_CONNUS.keySet()).noneMatch(c -> c.startsWith("contexts/stock/"));
    }

    @Test
    void la_garde_scanne_la_plateforme_les_acl_et_les_adapters_de_persistance() throws IOException {
        List<String> scannes = sourcesDeProduction(backend()).stream().map(TableDesSeancesTest::relatif).toList();
        assertThat(scannes).anyMatch(s -> s.startsWith("platform/src/main/java/"));
        assertThat(scannes).anyMatch(s -> s.contains("/infrastructure/acl/"));
        assertThat(scannes).anyMatch(s -> s.contains("/infrastructure/persistence/"));
        assertThat(scannes).noneMatch(s -> s.startsWith("contexts/seance/"));
    }

    @Test
    void la_garde_detecte_une_lecture_directe_d_une_table_de_la_seance() throws IOException {
        Path bac = Files.createTempDirectory("garde-seance-se7c");
        Path fautif = bac.resolve("LectureDirecte.java");
        Files.writeString(fautif, """
                class LectureDirecte {
                    String lecture = "SELECT a.id FROM seances a WHERE a.center_id = ?";
                }
                """);
        Path ecriture = bac.resolve("EcritureDirecte.java");
        Files.writeString(ecriture, """
                class EcritureDirecte {
                    String ecriture = "UPDATE seances SET statut = ? WHERE id = ?";
                }
                """);
        Path propre = bac.resolve("LectureLegitime.java");
        Files.writeString(propre, """
                class LectureLegitime {
                    String legitime = "SELECT s.id FROM patients s WHERE s.center_id = ?";
                }
                """);

        assertThat(mentionneLaTableDeLaSeance(fautif)).as("lecture de seances").isTrue();
        assertThat(mentionneLaTableDeLaSeance(ecriture)).as("écriture de seances").isTrue();
        assertThat(mentionneLaTableDeLaSeance(propre)).as("table d'un autre contexte").isFalse();
    }
}
