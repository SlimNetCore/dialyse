package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Les tables {@code seances}, {@code volet_paramedical} et {@code volet_medical} appartiennent au contexte Séance (ADR 0100, décision 1 :
 * les volets suivent le cycle de vie de la séance). Garde réécrite par SE7c1 (ADR 0096), étendue par SE7f aux volets et aux entités JPA
 * (KB-SEA-98 : un {@code @Table(name = "seances")} est un lecteur ; le dossier {@code /entity/} n'est plus exclu). Elle scanne TOUT le code
 * de production (plateforme, anciens modules, autres contextes, ACL et adapters de persistance compris), sauf le contexte Séance. Les lecteurs
 * actuels sont une liste blanche NOMMÉE, où chaque entrée porte sa nature et sa condition de sortie (un événement, jamais une date) :
 * <ul>
 *   <li>un nouveau lecteur, ou un lecteur qui n'est pas listé, fait échouer le test ;</li>
 *   <li>une entrée qui ne lit plus {@code seances} (fichier disparu ou migré) est une entrée morte : le test échoue, la liste doit
 *   être réduite (c'est ainsi que l'avancement de SE7c se mesure) ;</li>
 *   <li>le contexte Stock n'a plus aucune entrée (SE7c1) et ne peut plus en avoir.</li>
 * </ul>
 */
class TableDesSeancesTest {

    private record Lecteur(String nature, String conditionDeSortie) {
    }

    /** Lecteurs SQL connus hors du contexte Séance (chemin depuis {@code backend/}) : SE7c1 ; réduite par SE7e (ADR 0099). */
    private static final Map<String, Lecteur> LECTEURS_CONNUS = new TreeMap<>();

    /**
     * FAUX POSITIFS de la détection des noms calculés ({@link NomsDeTableCalcules}) : un littéral {@code "seances"} qui n'atteint AUCUNE
     * requête, dans un fichier nommé par un constructeur de SQL. Nommés, avec la raison vérifiée dans le code (2026-11-08, SE7e) ; une
     * entrée qui n'est plus détectée est une entrée morte.
     */
    private static final Map<String, String> MENTIONS_NON_SQL = new TreeMap<>();

    private static void lecteur(String chemin, String nature, String conditionDeSortie) {
        LECTEURS_CONNUS.put(chemin, new Lecteur(nature, conditionDeSortie));
    }

    static {
        // Facturation : plus aucune entrée depuis SE7c2 (ADR 0097, 2026-11-08) ; les 5 classes SQL ont disparu ou ne touchent plus seances.
        // Absences de patients, Planification, Dossier médical : plus aucune entrée depuis SE7d (ADR 0098, 2026-11-08) ; les 5 lecteurs passent
        // par seance.v1 (SeancesRealiseesPubliees, LiensDExclusionPublies, SeancesRealiseesAvecLeurPlacePubliees, ConstantesDesSeancesPubliees).
        // Plateforme : plus aucun lecteur du pilotage, des statistiques, du cahier (service), de la reprise ni du référentiel depuis SE7e
        // (ADR 0099, 2026-11-08) : ils passent par SeancesRealiseesDesCentresPubliees, StatistiquesDesSeancesPubliees, SeancesDuCahierPubliees,
        // SeancesDeLaReprisePubliees, ForfaitsImposesAuxSeancesPublies. Plus aucun fournisseur de seance.v1 ni aucun adaptateur de bc-seance
        // depuis SE7f (ADR 0100, 2026-11-08) : HistoriqueFourniLegacy et ForfaitsFournisLegacy sont repris dans contexts/seance ; les entités
        // JPA SeanceJpaEntity, VoletParamedicalJpaEntity, VoletMedicalJpaEntity sont retirées. Restent l'état Jasper (justifié), les scripts de
        // données, le schéma H2 et les deux lecteurs des volets de la reprise (résidu E-R6).
        String plateforme = "platform/src/main/java/com/hemodialyse/backend/";
        // MigrationRollbackJdbcAdapter : entrée SOLDÉE par RP2 (2026-11-13, ADR 0170) ; le contrôle « complétée » lit les volets par
        // SeancesDeLaReprisePubliees.laSeanceEstCompletee.
        // Ressources SQL de production hors Flyway, révélées par le durcissement de la garde (revue SE7c1, 2026-11-08).
        String ressources = "platform/src/main/resources/";
        // db/schema.sql : entrée SOLDÉE par S-DDL3b (2026-11-14, ADR 0187) ; le DDL H2 des séances et des volets est dans le script du contexte
        // Séance (contexts/seance/.../db/h2/seance-schema.sql, hors du périmètre de la garde).
        // contexts/documents/.../reports/cahier_dialyse.jrxml : entrée SOLDÉE par DOC5a (2026-11-15, ADR 0193). Le gabarit n'embarque plus de
        // requête : la Séance compose le jeu de données du cahier (SeancesImprimablesDuCahierJdbc, dans son contexte) et le fournit au moteur.
        // db/seed.sql, db/demo/renadial-seed.sql, db/demo/renadial-purge.sql : entrées SOLDÉES par SEED2 (2026-11-15, ADR 0188 et 0189). Le
        // jeu dérivé et le jeu RENADIAL vivent dans le module demo-data (hors du périmètre de la garde, voir DonneesDeDemonstrationTest) ;
        // db/seed.sql ne garde que l'identité.

        // MigrationEntity.java (faux positif nommé de la détection des noms calculés : SEANCES("seances", …), segment d'URL /import/seances) :
        // exemption SOLDÉE par RP3b (2026-11-13, ADR 0172) ; plus aucun constructeur de SQL de la reprise ne la nomme (aucun report).
    }

    /** Les trois tables de la Séance (ADR 0100 : les volets suivent la séance ; {@code resultats_analyses} est au Dossier médical). */
    private static final Set<String> TABLES_DE_LA_SEANCE = Set.of("seances", "volet_paramedical", "volet_medical");

    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:public\\.)?([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);

    /** Jointure à l'ancienne dans une même chaîne SQL : {@code FROM patients p, seances s} (la table n'est pas juste après FROM). */
    private static final Pattern LISTE_DE_TABLES = Pattern.compile(
            "\\bFROM\\s+[^;()\"]{0,160}?,\\s*(?:public\\.)?(?:seances|volet_paramedical|volet_medical)\\b", Pattern.CASE_INSENSITIVE);

    /** Entité JPA déclarée sur une table de la Séance (KB-SEA-98 : la garde ne voyait ni les requêtes dérivées ni les entités). */
    private static final Pattern ENTITE_JPA = Pattern.compile(
            "@Table\\s*\\(\\s*name\\s*=\\s*\"(?:public\\.)?(?:seances|volet_paramedical|volet_medical)\"", Pattern.CASE_INSENSITIVE);

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

    /** Tous les sources de production de {@code racine} (le contexte Séance excepté), entités JPA COMPRISES (SE7f, KB-SEA-98). */
    private static List<Path> sourcesDeProduction(Path racine) throws IOException {
        List<Path> trouves = new ArrayList<>();
        try (Stream<Path> modules = Stream.concat(Files.list(racine), Files.list(racine.resolve("contexts")))) {
            for (Path module : modules.toList()) {
                Path java = module.resolve("src/main/java");
                if (!Files.isDirectory(java) || chemin(module).endsWith("/contexts/seance")) continue;
                try (Stream<Path> fichiers = Files.walk(java)) {
                    fichiers.filter(p -> p.toString().endsWith(".java")).forEach(trouves::add);
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

    /**
     * Lecture littérale dans un fichier : {@code FROM seances}, {@code FROM a, seances}, OU littéral {@code "seances"} dans un fichier qui
     * construit lui-même une requête (nom de table calculé dans le même fichier, KB-SEA-92).
     */
    private static boolean mentionneLaTableDeLaSeance(Path source) {
        try {
            String texte = Files.readString(source);
            Matcher m = LECTURE_SQL.matcher(texte);
            while (m.find()) {
                if (TABLES_DE_LA_SEANCE.contains(m.group(2).toLowerCase())) return true;
            }
            if (LISTE_DE_TABLES.matcher(texte).find()) return true;
            if (ENTITE_JPA.matcher(texte).find()) return true;
            return NomsDeTableCalcules.contientLeLitteral(texte, TABLES_DE_LA_SEANCE) && NomsDeTableCalcules.construitUneRequete(texte);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Tous les lecteurs de {@code seances} des {@code sources} (chemins relatifs) : lecture littérale, ou nom de table calculé ou INDIRECT
     * (littéral {@code "seances"} rattaché à une requête construite d'un autre fichier : KB-SEA-92, durcissement SE7e).
     */
    private static Set<String> lecteurs(List<Path> sources) {
        Set<String> lecteurs = new TreeSet<>();
        for (Path source : sources) {
            if (mentionneLaTableDeLaSeance(source)) lecteurs.add(relatif(source));
        }
        NomsDeTableCalcules.lecteursIndirects(sources, TABLES_DE_LA_SEANCE).forEach(p -> lecteurs.add(relatif(p)));
        return lecteurs;
    }

    @Test
    void aucun_nouveau_lecteur_sql_de_la_table_des_seances_hors_de_la_liste_blanche() throws IOException {
        Set<String> nouveaux = new TreeSet<>(lecteurs(sourcesDeProduction(backend())));
        nouveaux.removeAll(LECTEURS_CONNUS.keySet());
        nouveaux.removeAll(MENTIONS_NON_SQL.keySet());
        assertThat(nouveaux)
                .as("nouveaux lecteurs SQL de seances (littéraux, noms de table calculés ou indirects) : passer par seance.v1 "
                        + "(publishedlanguage) ou les inscrire (nommés, avec nature et condition de sortie)")
                .isEmpty();
    }

    @Test
    void aucune_entree_morte_dans_la_liste_blanche() throws IOException {
        Set<String> lecteurs = lecteurs(sourcesDeProduction(backend()));
        Set<String> morts = new TreeSet<>();
        for (String chemin : LECTEURS_CONNUS.keySet()) {
            if (!Files.isRegularFile(backend().resolve(chemin)) || !lecteurs.contains(chemin)) morts.add(chemin);
        }
        for (String chemin : MENTIONS_NON_SQL.keySet()) {
            if (!Files.isRegularFile(backend().resolve(chemin)) || !lecteurs.contains(chemin)) morts.add(chemin);
        }
        assertThat(morts)
                .as("entrées qui ne lisent plus seances (ou faux positifs qui ne sont plus détectés) : les retirer")
                .isEmpty();
    }

    @Test
    void les_lecteurs_se7e_de_la_plateforme_ne_lisent_ni_n_ecrivent_plus_la_table_des_seances() throws IOException {
        String plateforme = "platform/src/main/java/com/hemodialyse/backend/";
        // PD1 (ADR 0102) : DirectionDashboardQueryService et DirectionBreakdownQueryService ont disparu de la plateforme ; leur lecture des
        // séances est le port SeancesRealiseesDesCentres du Pilotage direction (SeancesRealiseesParLaSeance, langage publié de la Séance).
        // PL10b2 (ADR 0152) : DirectionAbsencesQueryService est devenu ConsulterLesAbsencesDesCentres, dans le Pilotage (mêmes ports de la Séance).
        List<String> se7e = List.of(plateforme + "infrastructure/persistence/adapter/migration/HistoricalRecordJdbcAdapter.java",
                plateforme + "infrastructure/persistence/adapter/migration/MigrationRollbackJdbcAdapter.java");
        assertThat(se7e).as("les 2 fichiers de SE7e restés en plateforme existent (PatientStatsQueryService est parti au Dossier médical, PL4 ; CahierDialyseReportService à la Séance, PL5)").allMatch(c -> Files.isRegularFile(backend().resolve(c)));
        // Sur la seule table seances (les volets : voir l_annulation_d_un_lot_de_reprise_ne_lit_plus_les_volets_de_la_seance, RP2).
        Set<String> lecteursDeSeances = new TreeSet<>();
        for (Path source : sourcesDeProduction(backend())) {
            if (mentionne(source, Set.of("seances"))) lecteursDeSeances.add(relatif(source));
        }
        assertThat(se7e.stream().filter(lecteursDeSeances::contains).toList())
                .as("ces fichiers passent par les ports publiés de la Séance (ADR 0099, SE7e), sans SQL ni nom de table calculé sur seances")
                .isEmpty();
    }

    /**
     * RP2 (2026-11-13, ADR 0170) : l'annulation d'un lot de reprise lit « volet complété » par {@code SeancesDeLaReprisePubliees} ; son
     * entrée est supprimée. Verrou : elle ne figure plus dans la liste blanche ET ne
     * lit plus les trois tables de la Séance (volets compris).
     */
    @Test
    void l_annulation_d_un_lot_de_reprise_ne_lit_plus_les_volets_de_la_seance() throws IOException {
        String adapter = "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/adapter/migration/MigrationRollbackJdbcAdapter.java";
        assertThat(LECTEURS_CONNUS).doesNotContainKey(adapter);
        assertThat(lecteurs(sourcesDeProduction(backend()))).as("ni SQL ni nom de table calculé sur seances, volet_medical, volet_paramedical")
                .doesNotContain(adapter);
    }

    /** Lecture littérale d'une des {@code tables} dans un fichier (même détection que {@link #mentionneLaTableDeLaSeance}, tables choisies). */
    private static boolean mentionne(Path source, Set<String> tables) {
        try {
            String texte = Files.readString(source);
            Matcher m = LECTURE_SQL.matcher(texte);
            while (m.find()) {
                if (tables.contains(m.group(2).toLowerCase())) return true;
            }
            return NomsDeTableCalcules.contientLeLitteral(texte, tables) && NomsDeTableCalcules.construitUneRequete(texte);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void la_garde_detecte_un_nom_de_table_calcule_ou_indirect() throws IOException {
        Path bac = Files.createTempDirectory("garde-seance-se7e");
        Path descripteurs = bac.resolve("Descripteurs.java");
        Files.writeString(descripteurs, """
                class Descripteurs {
                    static final Usage U = new Usage("seances", "forfait_override_id");
                }
                """);
        Path compteur = bac.resolve("Compteur.java");
        Files.writeString(compteur, """
                class Compteur {
                    long n(Usage u) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + Descripteurs.U.table(), Long.class); }
                }
                """);
        Path liste = bac.resolve("ListeDeTables.java");
        Files.writeString(liste, """
                class ListeDeTables {
                    static final java.util.List<String> T = java.util.List.of("prise_en_charge", "public.SEANCES");
                    void supprimer(String t) { jdbc.update("DELETE FROM " + t + " WHERE id = ?", 1); }
                }
                """);
        Path constructeur = bac.resolve("Constructeur.java");
        Files.writeString(constructeur, """
                class Constructeur {
                    static final String TABLE = "seances";
                    String sql() { return new StringBuilder("SELECT id FROM ").append(TABLE).toString(); }
                }
                """);
        Path url = bac.resolve("CodeDUrl.java");
        Files.writeString(url, """
                class CodeDUrl {
                    static final String CODE = "seances";
                }
                """);
        Path jsonSansSql = bac.resolve("CleJson.java");
        Files.writeString(jsonSansSql, """
                class CleJson {
                    String n(Node t) { return t.path("seances").asText() + " FROM table"; }
                }
                """);
        Path format = bac.resolve("Formateur.java");
        Files.writeString(format, """
                class Formateur {
                    static final String TABLE = "seances";
                    long n() { return jdbc.queryForObject(String.format("SELECT COUNT(*) FROM %s WHERE id = ?", TABLE), Long.class, 1); }
                }
                """);
        Path formatted = bac.resolve("Formate.java");
        Files.writeString(formatted, """
                class Formate {
                    static final String TABLE = "seances";
                    String sql() { return "SELECT id FROM %s".formatted(TABLE); }
                }
                """);
        Path blocDeTexte = bac.resolve("BlocDeTexte.java");
        Files.writeString(blocDeTexte, """
                class BlocDeTexte {
                    static final String TABLE = "seances";
                    String sql() {
                        return \"""
                                SELECT id
                                  FROM
                                \""" + TABLE + " WHERE id = ?";
                    }
                }
                """);
        List<Path> sources = List.of(descripteurs, compteur, liste, constructeur, url, jsonSansSql, format, formatted, blocDeTexte);

        assertThat(NomsDeTableCalcules.lecteursIndirects(sources, TABLES_DE_LA_SEANCE))
                .as("descripteur nommé par un constructeur de SQL, liste et constructeur dans le même fichier, String.format, formatted et "
                        + "text block (revue SE7e n4) ; ni un code d'URL non nommé, ni une clé JSON sans requête construite")
                .containsExactlyInAnyOrder(descripteurs, liste, constructeur, format, formatted, blocDeTexte);
        assertThat(mentionneLaTableDeLaSeance(liste)).as("littéral et requête construite dans le même fichier").isTrue();
        assertThat(mentionneLaTableDeLaSeance(descripteurs)).as("seul : la détection inter-fichiers est nécessaire").isFalse();
    }

    @Test
    void chaque_entree_porte_une_nature_et_une_condition_de_sortie() {
        // DOC5a (ADR 0193) : la liste blanche est vide ; si une entrée revient, elle porte sa nature et sa condition de sortie
        LECTEURS_CONNUS.forEach((chemin, lecteur) -> {
            assertThat(lecteur.nature()).as("nature de " + chemin).isNotBlank();
            assertThat(lecteur.conditionDeSortie()).as("condition de sortie de " + chemin).isNotBlank();
        });
    }

    @Test
    void la_liste_blanche_est_vide_depuis_que_le_cahier_ne_porte_plus_de_requete() {
        assertThat(LECTEURS_CONNUS)
                .as("la liste blanche des lecteurs des tables de la Séance est vide (0 Facturation depuis SE7c2, 0 autre contexte depuis "
                        + "SE7d, 0 lecteur Java du pilotage, des statistiques, du cahier, de la reprise ni du référentiel sur seances depuis SE7e, "
                        + "0 fournisseur de seance.v1 ni entité JPA depuis SE7f, 0 lecteur Java des volets depuis RP2, 0 schéma H2 depuis S-DDL3b "
                        + "(ADR 0187), 0 script de données depuis SEED2 (ADR 0189), 0 état Jasper depuis DOC5a (ADR 0193 : la Séance compose le jeu "
                        + "de données du cahier)) : toute entrée ajoutée doit être décidée et reportée dans la fiche de la slice et l'annexe 16")
                .isEmpty();
        assertThat(LECTEURS_CONNUS.keySet().stream().filter(c -> c.startsWith("contexts/")).count())
                .as("dont 0 d'un autre contexte (SE7d), ni module Documents (DOC5a)").isZero();
        assertThat(LECTEURS_CONNUS.keySet().stream().filter(c -> c.startsWith("platform/src/main/java/")))
                .as("dont 0 source Java : l'annulation d'un lot de reprise lit les volets par la Séance depuis RP2 (ADR 0170)").isEmpty();
        assertThat(LECTEURS_CONNUS.keySet().stream().filter(c -> c.startsWith("platform/src/main/resources/")).count())
                .as("dont 0 ressource SQL de la plateforme (scripts de données soldés par SEED2, ADR 0189 ; l'état Jasper du cahier est "
                        + "au module Documents depuis DOC2, ADR 0162)").isZero();
        assertThat(MENTIONS_NON_SQL).as("plus aucun faux positif nommé de la détection des noms calculés (RP3b, ADR 0172)").isEmpty();
        assertThat(LECTEURS_CONNUS.keySet().stream().filter(c -> c.startsWith("contexts/facturation/")).count())
                .as("dont 0 de la Facturation (SE7c2)").isZero();
    }

    /** SE7f (ADR 0100) : la plateforme n'a plus ni entité JPA ni adaptateur de bc-seance sur les tables de la Séance ; aucun fournisseur de seance.v1 n'y touche. */
    @Test
    void la_plateforme_n_a_plus_d_entite_jpa_ni_de_fournisseur_sur_les_tables_de_la_seance() throws IOException {
        String plateforme = "platform/src/main/java/com/hemodialyse/backend/";
        List<String> retires = List.of(plateforme + "infrastructure/persistence/entity/SeanceJpaEntity.java",
                plateforme + "infrastructure/persistence/entity/VoletParamedicalJpaEntity.java",
                plateforme + "infrastructure/persistence/entity/VoletMedicalJpaEntity.java",
                plateforme + "infrastructure/persistence/repository/SeanceJpaRepository.java",
                plateforme + "infrastructure/persistence/adapter/SeanceRepositoryAdapter.java",
                plateforme + "infrastructure/acl/seance/HistoriqueFourniLegacy.java",
                plateforme + "infrastructure/acl/seance/ForfaitsFournisLegacy.java",
                plateforme + "infrastructure/acl/seance/VoletsFournisLegacy.java");
        assertThat(retires).as("retirés par SE7f").noneMatch(c -> Files.isRegularFile(backend().resolve(c)));
        Set<String> lecteurs = lecteurs(sourcesDeProduction(backend()));
        assertThat(lecteurs.stream().filter(c -> c.startsWith(plateforme + "infrastructure/acl/seance/")).toList())
                .as("les fournisseurs restants de seance.v1 (fiches, diffusions, rappels, administrations) n'ont aucun SQL sur les tables de la Séance")
                .isEmpty();
        assertThat(lecteurs.stream().filter(c -> c.contains("/entity/") || c.contains("/repository/")).toList())
                .as("plus aucune entité ni dépôt JPA sur seances, volet_paramedical, volet_medical").isEmpty();
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
        Path acl = backend().resolve("contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/acl/facturation");
        try (Stream<Path> fichiers = Files.list(acl)) {
            List<Path> traducteurs = fichiers.filter(p -> p.toString().endsWith(".java")).toList();
            assertThat(traducteurs).as("les 4 traducteurs de SE7c2").hasSize(4);
            assertThat(traducteurs.stream().filter(TableDesSeancesTest::mentionneLaTableDeLaSeance).toList()).isEmpty();
        }
    }

    @Test
    void les_absences_la_planification_et_le_dossier_medical_ne_lisent_plus_la_table_des_seances() throws IOException {
        List<String> consommateurs = new ArrayList<>();
        for (Path source : sourcesDeProduction(backend())) {
            String chemin = relatif(source);
            boolean consommateur = chemin.startsWith("contexts/absences-patients/") || chemin.startsWith("contexts/planification/")
                    || chemin.startsWith("contexts/dossier-medical/");
            if (consommateur && mentionneLaTableDeLaSeance(source)) consommateurs.add(chemin);
        }
        assertThat(consommateurs).as("ces contextes passent par les ports publiés de la Séance (ADR 0098, SE7d)").isEmpty();
    }

    @Test
    void les_traducteurs_des_absences_et_de_la_planification_dans_la_plateforme_n_ont_pas_de_sql_sur_les_seances() {
        String acl = "contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/acl/";
        List<Path> traducteurs = List.of(backend().resolve(acl + "absencespatients/SeancesRealiseesParLaSeance.java"),
                backend().resolve(acl + "absencespatients/LiensDExclusionParLaSeance.java"),
                backend().resolve("contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/acl/planification/"
                        + "SeancesRealiseesAvecLeurPlaceParLaSeance.java"));
        assertThat(traducteurs).as("les 3 traducteurs de SE7d").allMatch(Files::isRegularFile);
        assertThat(traducteurs.stream().filter(TableDesSeancesTest::mentionneLaTableDeLaSeance).toList()).isEmpty();
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
        Path entite = bac.resolve("VoletJpaEntity.java");
        Files.writeString(entite, """
                @Entity
                @Table(name = "volet_paramedical")
                class VoletJpaEntity { }
                """);
        Path volet = bac.resolve("LectureDuVolet.java");
        Files.writeString(volet, """
                class LectureDuVolet {
                    String lecture = "SELECT v.id FROM volet_medical v WHERE v.seance_id = ?";
                }
                """);
        Path creation = bac.resolve("schema.sql");
        Files.writeString(creation, "CREATE TABLE IF NOT EXISTS seances (id UUID PRIMARY KEY);");

        assertThat(mentionneLaTableDeLaSeance(entite)).as("entité JPA sur une table de la Séance (SE7f, KB-SEA-98)").isTrue();
        assertThat(mentionneLaTableDeLaSeance(volet)).as("lecture d'un volet (SE7f)").isTrue();
        assertThat(mentionneLaTableDeLaSeance(creation)).as("CREATE TABLE IF NOT EXISTS (schéma H2)").isTrue();
        assertThat(mentionneLaTableDeLaSeance(liste)).as("FROM a, seances").isTrue();
        assertThat(mentionneLaTableDeLaSeance(qualifiee)).as("public.seances").isTrue();
        assertThat(mentionneLaTableDeLaSeance(ressource)).as("ressource SQL").isTrue();
        assertThat(mentionneLaTableDeLaSeance(variable)).as("une variable nommée seances n'est pas du SQL").isFalse();
        assertThat(mentionneLaTableDeLaSeance(fautif)).as("lecture de seances").isTrue();
        assertThat(mentionneLaTableDeLaSeance(ecriture)).as("écriture de seances").isTrue();
        assertThat(mentionneLaTableDeLaSeance(propre)).as("table d'un autre contexte").isFalse();
    }
}
