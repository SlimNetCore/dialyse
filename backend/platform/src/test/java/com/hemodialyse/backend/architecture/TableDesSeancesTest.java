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
    /** Résidu E-R3 (ADR 0099) : requête embarquée du modèle de document PERSONNALISABLE du cahier de dialyse. */
    private static final LocalDate CAHIER_PERSONNALISABLE = LocalDate.of(2027, 6, 30);

    /** Lecteurs SQL connus hors du contexte Séance (chemin depuis {@code backend/}) : 2026-11-08, SE7c1 ; réduite par SE7e (ADR 0099). */
    private static final Map<String, Lecteur> LECTEURS_CONNUS = new TreeMap<>();

    /**
     * FAUX POSITIFS de la détection des noms calculés ({@link NomsDeTableCalcules}) : un littéral {@code "seances"} qui n'atteint AUCUNE
     * requête, dans un fichier nommé par un constructeur de SQL. Nommés, avec la raison vérifiée dans le code (2026-11-08, SE7e) ; une
     * entrée qui n'est plus détectée est une entrée morte.
     */
    private static final Map<String, String> MENTIONS_NON_SQL = new TreeMap<>();

    private static void lecteur(String chemin, String nature, LocalDate butoir) {
        LECTEURS_CONNUS.put(chemin, new Lecteur(nature, butoir));
    }

    static {
        // Facturation : plus aucune entrée depuis SE7c2 (ADR 0097, 2026-11-08) ; les 5 classes SQL ont disparu ou ne touchent plus seances.
        // Absences de patients, Planification, Dossier médical : plus aucune entrée depuis SE7d (ADR 0098, 2026-11-08) ; les 5 lecteurs passent
        // par seance.v1 (SeancesRealiseesPubliees, LiensDExclusionPublies, SeancesRealiseesAvecLeurPlacePubliees, ConstantesDesSeancesPubliees).
        // Plateforme : plus aucun lecteur du pilotage, des statistiques, du cahier (service), de la reprise ni du référentiel depuis SE7e
        // (ADR 0099, 2026-11-08) : ils passent par SeancesRealiseesDesCentresPubliees, StatistiquesDesSeancesPubliees, SeancesDuCahierPubliees,
        // SeancesDeLaReprisePubliees, ForfaitsImposesAuxSeancesPublies. Restent les fournisseurs de seance.v1 (SE7f), l'état Jasper (justifié)
        // et les scripts de données (SE7f).
        String plateforme = "platform/src/main/java/com/hemodialyse/backend/";
        lecteur(plateforme + "infrastructure/acl/seance/HistoriqueFourniLegacy.java", "historique des séances fourni à la Séance (lecture ; SE7f)", LECTURES_PUBLIEES);
        lecteur(plateforme + "infrastructure/acl/seance/ForfaitsFournisLegacy.java", "forfaits fournis à la Séance (lecture ; SE7f)", LECTURES_PUBLIEES);
        // Ressources SQL de production hors Flyway, révélées par le durcissement de la garde (revue SE7c1, 2026-11-08).
        String ressources = "platform/src/main/resources/";
        lecteur(ressources + "reports/cahier_dialyse.jrxml", "état Jasper du cahier de dialyse (lecture, jointure séances ; KB-SEA-47, 88) : modèle "
                        + "de document PERSONNALISABLE par centre, dont la requête unique est embarquée (ADR 0099, décision 3, résidu E-R3)",
                CAHIER_PERSONNALISABLE);
        lecteur(ressources + "db/seed.sql", "jeu d'amorçage (écrit des séances ; rejoué à chaque démarrage sans SEED_DATA_LOCATIONS ; SE7f)",
                RETRAIT_DU_PILOTAGE_LEGACY);
        lecteur(ressources + "db/demo/renadial-seed.sql", "jeu de démonstration (écrit des séances ; SE7f)", RETRAIT_DU_PILOTAGE_LEGACY);
        lecteur(ressources + "db/demo/renadial-purge.sql", "purge de la démonstration (SUPPRIME des séances ; jamais sur une base partagée ; SE7f)",
                RETRAIT_DU_PILOTAGE_LEGACY);

        MENTIONS_NON_SQL.put(plateforme + "infrastructure/migration/model/MigrationEntity.java",
                "code du type de donnée importée (SEANCES(\"seances\", …) : segment d'URL /import/seances et colonne « seances » de "
                        + "joursDialyse) ; nommé par HistoricalRecordJdbcAdapter qui construit ses requêtes depuis TABLES, où les séances ne "
                        + "figurent plus (SE7e) : la chaîne n'atteint aucune requête");
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
                        + "(publishedlanguage) ou les inscrire (nommés, datés, butoir)")
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
        List<String> se7e = List.of(plateforme + "application/direction/DirectionDashboardQueryService.java",
                plateforme + "application/direction/DirectionBreakdownQueryService.java",
                plateforme + "application/direction/DirectionAbsencesQueryService.java",
                plateforme + "application/query/PatientStatsQueryService.java",
                plateforme + "infrastructure/reporting/CahierDialyseReportService.java",
                plateforme + "infrastructure/persistence/adapter/migration/HistoricalRecordJdbcAdapter.java",
                plateforme + "infrastructure/persistence/adapter/migration/MigrationRollbackJdbcAdapter.java",
                plateforme + "infrastructure/persistence/adapter/referential/ReferentialAdminJdbcAdapter.java",
                plateforme + "infrastructure/persistence/adapter/referential/ReferentialAdminTables.java");
        assertThat(se7e).as("les 9 fichiers de SE7e existent").allMatch(c -> Files.isRegularFile(backend().resolve(c)));
        Set<String> lecteurs = lecteurs(sourcesDeProduction(backend()));
        assertThat(se7e.stream().filter(lecteurs::contains).toList())
                .as("ces fichiers passent par les ports publiés de la Séance (ADR 0099, SE7e), sans SQL ni nom de table calculé sur seances")
                .isEmpty();
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
        List<Path> sources = List.of(descripteurs, compteur, liste, constructeur, url, jsonSansSql);

        assertThat(NomsDeTableCalcules.lecteursIndirects(sources, TABLES_DE_LA_SEANCE))
                .as("descripteur nommé par un constructeur de SQL, liste et constructeur dans le même fichier ; ni un code d'URL non nommé, "
                        + "ni une clé JSON sans requête construite")
                .containsExactlyInAnyOrder(descripteurs, liste, constructeur);
        assertThat(mentionneLaTableDeLaSeance(liste)).as("littéral et requête construite dans le même fichier").isTrue();
        assertThat(mentionneLaTableDeLaSeance(descripteurs)).as("seul : la détection inter-fichiers est nécessaire").isFalse();
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
    void la_liste_blanche_compte_6_lecteurs_ceux_de_se7f_et_l_etat_jasper_justifie() {
        assertThat(LECTEURS_CONNUS)
                .as("la liste blanche des lecteurs de seances compte 6 entrées (0 Facturation depuis SE7c2, 0 autre contexte depuis SE7d, "
                        + "0 lecteur Java du pilotage, des statistiques, du cahier, de la reprise ni du référentiel depuis SE7e ; restent 2 "
                        + "fournisseurs de seance.v1 et 3 scripts de données, tous de SE7f, et l'état Jasper du cahier, justifié) : toute entrée "
                        + "ajoutée ou retirée doit être décidée, datée et reportée dans la fiche de la slice et l'annexe 16")
                .hasSize(6);
        assertThat(LECTEURS_CONNUS.keySet().stream().filter(c -> c.startsWith("contexts/")).count())
                .as("dont 0 d'un autre contexte (SE7d)").isZero();
        assertThat(LECTEURS_CONNUS.keySet().stream().filter(c -> c.startsWith("platform/")).count())
                .as("dont 6 de la plateforme").isEqualTo(6);
        assertThat(LECTEURS_CONNUS.keySet().stream().filter(c -> c.startsWith("platform/src/main/java/")))
                .as("dont 2 sources Java, les fournisseurs de seance.v1 (SE7f)")
                .containsExactlyInAnyOrder("platform/src/main/java/com/hemodialyse/backend/infrastructure/acl/seance/HistoriqueFourniLegacy.java",
                        "platform/src/main/java/com/hemodialyse/backend/infrastructure/acl/seance/ForfaitsFournisLegacy.java");
        assertThat(LECTEURS_CONNUS.keySet().stream().filter(c -> c.startsWith("platform/src/main/resources/")).count())
                .as("dont 4 ressources SQL (l'état Jasper et 3 scripts de données)").isEqualTo(4);
        assertThat(MENTIONS_NON_SQL).as("1 faux positif nommé de la détection des noms calculés").hasSize(1);
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
        String acl = "platform/src/main/java/com/hemodialyse/backend/infrastructure/acl/";
        List<Path> traducteurs = List.of(backend().resolve(acl + "absences/SeancesRealiseesParLaSeance.java"),
                backend().resolve(acl + "absences/LiensDExclusionParLaSeance.java"),
                backend().resolve(acl + "planification/SeancesRealiseesAvecLeurPlaceParLaSeance.java"));
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

        assertThat(mentionneLaTableDeLaSeance(liste)).as("FROM a, seances").isTrue();
        assertThat(mentionneLaTableDeLaSeance(qualifiee)).as("public.seances").isTrue();
        assertThat(mentionneLaTableDeLaSeance(ressource)).as("ressource SQL").isTrue();
        assertThat(mentionneLaTableDeLaSeance(variable)).as("une variable nommée seances n'est pas du SQL").isFalse();
        assertThat(mentionneLaTableDeLaSeance(fautif)).as("lecture de seances").isTrue();
        assertThat(mentionneLaTableDeLaSeance(ecriture)).as("écriture de seances").isTrue();
        assertThat(mentionneLaTableDeLaSeance(propre)).as("table d'un autre contexte").isFalse();
    }
}
