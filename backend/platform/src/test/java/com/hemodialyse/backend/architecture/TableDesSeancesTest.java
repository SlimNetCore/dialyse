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

    private static final LocalDate LECTURES_PUBLIEES = LocalDate.of(2027, 3, 31);
    private static final LocalDate RETRAIT_DU_PILOTAGE_LEGACY = LocalDate.of(2027, 6, 30);

    /** Lecteurs SQL connus hors du contexte Séance (chemin depuis {@code backend/}) : 2026-11-08, SE7c1. */
    private static final Map<String, Lecteur> LECTEURS_CONNUS = new TreeMap<>();

    private static void lecteur(String chemin, String nature, LocalDate butoir) {
        LECTEURS_CONNUS.put(chemin, new Lecteur(nature, butoir));
    }

    static {
        // Facturation : plus aucune entrée depuis SE7c2 (ADR 0097, 2026-11-08) ; les 5 classes SQL ont disparu ou ne touchent plus seances.
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
        // Ressources SQL de production hors Flyway, révélées par le durcissement de la garde (revue SE7c1, 2026-11-08).
        String ressources = "platform/src/main/resources/";
        lecteur(ressources + "reports/cahier_dialyse.jrxml", "état Jasper du cahier de dialyse (lecture, jointure séances ; KB-SEA-47 lit le code du forfait)",
                RETRAIT_DU_PILOTAGE_LEGACY);
        lecteur(ressources + "db/seed.sql", "jeu d'amorçage de développement (écrit des séances)", RETRAIT_DU_PILOTAGE_LEGACY);
        lecteur(ressources + "db/demo/renadial-seed.sql", "jeu de démonstration (écrit des séances)", RETRAIT_DU_PILOTAGE_LEGACY);
        lecteur(ressources + "db/demo/renadial-purge.sql", "purge de la démonstration (SUPPRIME des séances ; jamais sur une base partagée)",
                RETRAIT_DU_PILOTAGE_LEGACY);
    }

    private static final Set<String> TABLES_DE_LA_SEANCE = Set.of("seances");

    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+(?:public\\.)?([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);

    /** Jointure à l'ancienne dans une même chaîne SQL : {@code FROM patients p, seances s} (la table n'est pas juste après FROM). */
    private static final Pattern LISTE_DE_TABLES = Pattern.compile(
            "\\bFROM\\s+[^;()\"]{0,160}?,\\s*(?:public\\.)?seances\\b", Pattern.CASE_INSENSITIVE);

    /** Ressources SQL de production hors Flyway (états Jasper, requêtes en fichier) : lues comme les sources Java. */
    private static final Set<String> EXTENSIONS_DE_RESSOURCES = Set.of(".sql", ".jrxml", ".xml");

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
                Path ressources = module.resolve("src/main/resources");
                if (Files.isDirectory(ressources)) {
                    try (Stream<Path> fichiers = Files.walk(ressources)) {
                        fichiers.filter(Files::isRegularFile).filter(p -> !chemin(p).contains("/db/migration/"))
                                .filter(p -> EXTENSIONS_DE_RESSOURCES.stream().anyMatch(e -> p.toString().endsWith(e)))
                                .forEach(trouves::add);
                    }
                }
            }
        }
        return trouves;
    }

    private static boolean mentionneLaTableDeLaSeance(Path source) {
        try {
            String texte = Files.readString(source);
            Matcher m = LECTURE_SQL.matcher(texte);
            while (m.find()) {
                if (TABLES_DE_LA_SEANCE.contains(m.group(2).toLowerCase())) return true;
            }
            return LISTE_DE_TABLES.matcher(texte).find();
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
    void la_liste_blanche_compte_17_lecteurs_dont_12_de_la_plateforme() {
        assertThat(LECTEURS_CONNUS)
                .as("la liste blanche des lecteurs de seances compte 17 entrées (0 Facturation depuis SE7c2, 5 autres contextes, 12 plateforme "
                        + "dont 8 sources Java et 4 ressources SQL) : toute entrée ajoutée ou retirée doit être décidée, datée et reportée "
                        + "dans la fiche de SE7c et l'annexe 16")
                .hasSize(17);
        assertThat(LECTEURS_CONNUS.keySet().stream().filter(c -> c.startsWith("platform/")).count())
                .as("dont 12 de la plateforme").isEqualTo(12);
        assertThat(LECTEURS_CONNUS.keySet().stream().filter(c -> c.startsWith("platform/src/main/resources/")).count())
                .as("dont 4 ressources SQL").isEqualTo(4);
        assertThat(LECTEURS_CONNUS.keySet().stream().filter(c -> c.startsWith("contexts/facturation/")).count())
                .as("dont 0 de la Facturation (SE7c2)").isZero();
    }

    @Test
    void la_facturation_ne_lit_ni_n_ecrit_plus_la_table_des_seances() throws IOException {
        List<String> facturation = new ArrayList<>();
        for (Path source : sourcesDeProduction(backend())) {
            if (relatif(source).startsWith("contexts/facturation/") && mentionneLaTableDeLaSeance(source)) facturation.add(relatif(source));
        }
        assertThat(facturation).as("la Facturation passe par les ports publiés de la Séance, via l'ACL de la plateforme (ADR 0097)").isEmpty();
        assertThat(LECTEURS_CONNUS.keySet()).noneMatch(c -> c.startsWith("contexts/facturation/"));
    }

    @Test
    void les_traducteurs_de_la_facturation_dans_la_plateforme_n_ont_pas_de_sql_sur_les_seances() throws IOException {
        Path acl = backend().resolve("platform/src/main/java/com/hemodialyse/backend/infrastructure/acl/facturation");
        try (Stream<Path> fichiers = Files.list(acl)) {
            List<Path> traducteurs = fichiers.filter(p -> p.toString().endsWith(".java")).toList();
            assertThat(traducteurs).as("les 4 traducteurs de SE7c2").hasSize(4);
            assertThat(traducteurs.stream().filter(TableDesSeancesTest::mentionneLaTableDeLaSeance).toList()).isEmpty();
        }
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
        assertThat(scannes).as("ressources SQL de production hors Flyway").anyMatch(s -> s.contains("/src/main/resources/"));
        assertThat(scannes).noneMatch(s -> s.contains("/db/migration/"));
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

        Path liste = bac.resolve("JointureAncienne.java");
        Files.writeString(liste, """
                class JointureAncienne {
                    String lecture = "SELECT p.nom FROM patients p, seances s WHERE s.patient_id = p.id";
                }
                """);
        Path qualifiee = bac.resolve("TableQualifiee.java");
        Files.writeString(qualifiee, """
                class TableQualifiee {
                    String lecture = "SELECT s.id FROM public.seances s";
                }
                """);
        Path ressource = bac.resolve("etat.jrxml");
        Files.writeString(ressource, "<queryString><![CDATA[SELECT s.id FROM seances s WHERE s.center_id = $P{centre}]]></queryString>");
        Path variable = bac.resolve("VariableNommeeSeances.java");
        Files.writeString(variable, """
                class VariableNommeeSeances {
                    void appel(java.util.List<String> patients, java.util.List<String> seances) { }
                }
                """);

        assertThat(mentionneLaTableDeLaSeance(liste)).as("FROM a, seances").isTrue();
        assertThat(mentionneLaTableDeLaSeance(qualifiee)).as("public.seances").isTrue();
        assertThat(mentionneLaTableDeLaSeance(ressource)).as("ressource SQL").isTrue();
        assertThat(mentionneLaTableDeLaSeance(variable)).as("une variable nommée seances n'est pas du SQL").isFalse();
        assertThat(mentionneLaTableDeLaSeance(fautif)).as("lecture de seances").isTrue();
        assertThat(mentionneLaTableDeLaSeance(ecriture)).as("écriture de seances").isTrue();
        assertThat(mentionneLaTableDeLaSeance(propre)).as("table d'un autre contexte").isFalse();
    }
}
