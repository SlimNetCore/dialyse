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
 * Les tables de l'Organisation appartiennent au contexte Organisation. La slice O4 (lectures publiées {@code organisation.v1}) est
 * construite (lots A à E, ADR 0107 à 0111) : les consommateurs passent par le langage publié, et seuls les résiduels nominatifs
 * O4-R9 à O4-R13 (identité et accès, amorçage de développement, admin générique) lisent encore ces tables en SQL direct.
 * Cette garde ne le cache pas :
 * <ul>
 *   <li>elle scanne TOUT le code de production (plateforme, anciens modules, autres contextes, ACL et adapters de persistance
 *   compris), sauf le contexte Organisation et les entités JPA ;</li>
 *   <li>les lecteurs actuels sont une liste blanche NOMMÉE et DATÉE ({@link #LECTEURS_CONNUS}) : un nouveau lecteur, ou un lecteur
 *   qui change de table sans être listé, fait échouer le test ;</li>
 *   <li>une entrée de la liste qui ne lit plus aucune table de l'Organisation (fichier disparu ou migré) est une entrée morte :
 *   le test échoue, la liste doit être réduite (c'est ainsi que l'avancement de O4 se mesure).</li>
 * </ul>
 */
class TableDeLOrganisationTest {

    private static final Set<String> TABLES_DE_L_ORGANISATION = Set.of(
            "societes", "centers", "medecin", "machine", "protocole", "salle", "position_creneau");

    private static final String LECTURE_DIRECTE = "2026-11-02 : lecture SQL directe, à remplacer par organisation.v1 (O4 planned)."
            + " Butoir : retrait à l'échéance de organisation.v1 (jalon O4) au 2027-03-31 (sinon reconduction motivée).";

    /** Lecteurs SQL connus hors du contexte Organisation (chemin depuis {@code backend/}) : 2026-11-02, revue O1-O5. */
    private static final Map<String, String> LECTEURS_CONNUS = lecteurs(
            // 2026-11-09, O4 lot A (ADR 0107) : 10 lecteurs retirés (centres actifs, tous les centres, fuseau du centre, société d'un
            // centre, sociétés et centres du Pilotage) : 32 -> 22. Les besoins restants sont dans la fiche O4 (lots B à E).
            // 2026-11-09, O4 lot B (ADR 0108) : 4 lecteurs du Stock retirés (CentresActifsLegacy, HorlogeDesCentresLegacy, InventairesJdbc,
            // OperationsDeStockJdbc) : ports fournis au Stock, traducteurs de la plateforme appelant organisation.v1 : 22 -> 18.
            // 2026-11-09, O4 lot C (ADR 0109) : 3 lecteurs retirés (LecturesPublieesDesSeancesJdbc, LectureDesMouvementsJdbc,
            // LectureDesPatientsJdbc) : libellés de salle, de créneau et de médecin lus par LibellesDeLOrganisation : 18 -> 15.
            // 2026-11-09, O4 lot D (ADR 0110) : 6 lecteurs de la Planification retirés (CreneauxDuCentreJdbc, LectureDesRessourcesLegacy,
            // SallesDuCentreJdbc, SeriesDuCentreJdbc, VueDesSallesLegacy, VerrouDuReferentielDesInfirmiersJdbc) : salles et créneaux par
            // SallesEtCreneauxDuCentre, isolement par la commande DefinirLesSallesDIsolement, verrou par VerrouDuCentre : 15 -> 9.
            // 2026-11-09, O4 lot E (ADR 0111) : 2 lecteurs retirés (DocumentIdentityProvider : identité imprimée par IdentiteImprimee ;
            // ModeleDocumentProvisioner : tous les centres par AnnuaireDesCentres.tous) : 9 -> 7. Les 7 restants sont des résiduels nominatifs
            // (O4-R9 à O4-R13 de la fiche O4 : authentification, comptes, garde d'accès, amorçage dev, admin générique).
            // 2026-11-14, IDN-lecture (ADR 0174) : les 6 derniers lecteurs soldés (AuthService, DirectionAccountService,
            // SocieteAdminAccountService, DevSuperAdminBootstrapInitializer, SessionLegacy du Pilotage, UserRestController) : 6 -> 0.
            // La liste reste vide et la garde permanente : tout nouveau lecteur SQL d'une table de l'Organisation hors du contexte échoue.
            );

    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);

    private static Map<String, String> lecteurs(String... chemins) {
        Map<String, String> m = new TreeMap<>();
        for (String chemin : chemins) m.put(chemin, LECTURE_DIRECTE);
        return m;
    }

    private static Path backend() {
        return Path.of("..").toAbsolutePath().normalize();
    }

    /** Tous les sources de production de {@code racine} (le contexte Organisation et les entités JPA exceptés). */
    private static List<Path> sourcesDeProduction(Path racine) throws IOException {
        List<Path> trouves = new java.util.ArrayList<>();
        try (Stream<Path> modules = Stream.concat(Files.list(racine), Files.list(racine.resolve("contexts")))) {
            for (Path module : modules.toList()) {
                Path java = module.resolve("src/main/java");
                if (!Files.isDirectory(java) || chemin(module).endsWith("/contexts/organisation")) continue;
                try (Stream<Path> fichiers = Files.walk(java)) {
                    fichiers.filter(p -> p.toString().endsWith(".java"))
                            .filter(p -> !ButoirsDesGardesDeTables.estUneEntiteJpaConnue(relatif(p), ENTITES_JPA_CONNUES))
                            .forEach(trouves::add);
                }
            }
        }
        return trouves;
    }

    private static String chemin(Path p) {
        return p.toString().replace('\\', '/');
    }

    private static String relatif(Path p) {
        return chemin(backend().relativize(p));
    }

    private static boolean mentionneUneTableDeLOrganisation(Path source) {
        try {
            Matcher m = LECTURE_SQL.matcher(Files.readString(source));
            while (m.find()) {
                if (TABLES_DE_L_ORGANISATION.contains(m.group(2).toLowerCase())) return true;
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aucun_nouveau_lecteur_sql_des_tables_de_l_organisation_hors_de_la_liste_blanche() throws IOException {
        Set<String> nouveaux = new TreeSet<>();
        for (Path source : sourcesDeProduction(backend())) {
            if (mentionneUneTableDeLOrganisation(source) && !LECTEURS_CONNUS.containsKey(relatif(source))) {
                nouveaux.add(relatif(source));
            }
        }
        assertThat(nouveaux)
                .as("nouveaux lecteurs SQL des tables de l'Organisation : passer par organisation.v1 ou les inscrire (nommés, datés)")
                .isEmpty();
    }

    @Test
    void aucune_entree_morte_dans_la_liste_blanche() throws IOException {
        Set<String> morts = new TreeSet<>();
        for (String chemin : LECTEURS_CONNUS.keySet()) {
            Path source = backend().resolve(chemin);
            if (!Files.isRegularFile(source) || !mentionneUneTableDeLOrganisation(source)) morts.add(chemin);
        }
        assertThat(morts)
                .as("entrées de la liste blanche qui ne lisent plus les tables de l'Organisation : les retirer (avancement de O4)")
                .isEmpty();
    }

    /** Critère d'avancement de O4 : 32 lecteurs au 2026-11-02 et au lancement, 22 après le lot A, 18 après le lot B, 15 après le lot C, 9 après le lot D, 7 après le lot E (2026-11-09). Le baisser avec chaque lot. */
    @Test
    void le_nombre_de_lecteurs_connus_est_le_critere_d_avancement_de_o4() {
        assertThat(LECTEURS_CONNUS).as("lecteurs SQL directs restants (lot E de O4 : 9 -> 7, puis +1 avec la garde du rapport PL9, -1 DirectionAccessGuard en PL10b2 : 7, -1 ReferentialAdminJdbcAdapter en PL11-3 : 6, IDN-lecture ADR 0174 : 0)").isEmpty();
    }

    /**
     * Les entités JPA des tables de l'Organisation (chemins depuis {@code backend/}) : seules exclues de la garde (revue globale
     * 2026-11-10, constat mineur : l'exclusion du dossier {@code /entity/} entier cachait toute autre entité).
     */
    private static final Set<String> ENTITES_JPA_CONNUES = Set.of(); // IDN-licence (ADR 0175) : les deux entités sont supprimées, aucun report

    @Test
    void chaque_lecteur_connu_porte_un_butoir_lisible_et_aucun_n_est_depasse_a_la_date_du_jour() {
        assertThat(ButoirsDesGardesDeTables.butoirsAbsentsOuDepasses(LECTEURS_CONNUS, java.time.LocalDate.of(2026, 11, 10)))
                .as("chaque lecteur connu porte un butoir lisible (date ISO après « Butoir : »)").isEmpty();
        assertThat(ButoirsDesGardesDeTables.butoirsAbsentsOuDepasses(LECTEURS_CONNUS, java.time.LocalDate.now()))
                .as("butoir (2027-03-31) dépassé : migrer le lecteur vers organisation.v1, ou reconduire AVEC une raison motivée "
                        + "et une nouvelle date").isEmpty();
        assertThat(LECTEURS_CONNUS.values()).allSatisfy(raison ->
                assertThat(ButoirsDesGardesDeTables.butoir(raison)).contains(java.time.LocalDate.of(2027, 3, 31)));
    }

    @Test
    void l_exclusion_est_nominative_les_autres_entites_jpa_sont_scannees() throws IOException {
        List<String> scannes = sourcesDeProduction(backend()).stream().map(TableDeLOrganisationTest::relatif).toList();
        assertThat(ENTITES_JPA_CONNUES).as("plus aucune entité JPA d'Organisation dans platform (ADR 0175)").isEmpty();
        assertThat(Files.exists(backend().resolve(
                "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/entity/CenterJpaEntity.java"))).isFalse();
        assertThat(Files.exists(backend().resolve(
                "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/entity/SocieteJpaEntity.java"))).isFalse();
        assertThat(scannes).as("une entité JPA non nommée est scannée").anyMatch(s -> s.contains("/entity/"));
        assertThat(ButoirsDesGardesDeTables.estUneEntiteJpaConnue("platform/src/main/java/x/entity/Inconnue.java", ENTITES_JPA_CONNUES)).isFalse();
    }

    @Test
    void la_garde_detecte_une_lecture_directe_d_une_table_de_l_organisation() throws IOException {
        Path bac = Files.createTempDirectory("garde-organisation");
        Path fautif = bac.resolve("LectureDirecte.java");
        Files.writeString(fautif, """
                class LectureDirecte {
                    String lecture = "SELECT a.id FROM salle a WHERE a.center_id = ?";
                    String ecriture = "UPDATE centers SET nom = ? WHERE id = ?";
                }
                """);
        Path propre = bac.resolve("LectureLegitime.java");
        Files.writeString(propre, """
                class LectureLegitime {
                    String legitime = "SELECT s.id FROM seances s WHERE s.center_id = ?";
                }
                """);

        assertThat(mentionneUneTableDeLOrganisation(fautif)).as("lecture de salle et écriture de centers").isTrue();
        assertThat(mentionneUneTableDeLOrganisation(propre)).as("table d'un autre contexte").isFalse();
    }

    @Test
    void la_garde_scanne_la_plateforme_les_acl_et_les_adapters_de_persistance() throws IOException {
        List<String> scannes = sourcesDeProduction(backend()).stream().map(TableDeLOrganisationTest::relatif).toList();
        assertThat(scannes).anyMatch(s -> s.startsWith("platform/src/main/java/"));
        assertThat(scannes).anyMatch(s -> s.contains("/infrastructure/acl/"));
        assertThat(scannes).anyMatch(s -> s.contains("/infrastructure/persistence/"));
        assertThat(scannes).noneMatch(s -> s.startsWith("contexts/organisation/"));
    }
}
