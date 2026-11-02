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
 * {@code planned} : aucun consommateur ne passe encore par le langage publié, et des sources hors du contexte lisent ces tables en
 * SQL direct. Cette garde ne le cache pas :
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

    private static final String LECTURE_DIRECTE = "2026-11-02 : lecture SQL directe, à remplacer par organisation.v1 (O4 planned)";

    /** Lecteurs SQL connus hors du contexte Organisation (chemin depuis {@code backend/}) : 2026-11-02, revue O1-O5. */
    private static final Map<String, String> LECTEURS_CONNUS = lecteurs(
            "contexts/absences-patients/src/main/java/com/hemodialyse/backend/contexts/absencespatients/infrastructure/acl/legacyorganisation/CentresActifsJdbc.java",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacycentres/CentresActifsLegacy.java",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacycentres/HorlogeDesCentresLegacy.java",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacyorganisation/CreneauxDuCentreJdbc.java",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacyorganisation/LectureDesRessourcesLegacy.java",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacyorganisation/SallesDuCentreJdbc.java",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacyorganisation/SeriesDuCentreJdbc.java",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacyorganisation/VueDesSallesLegacy.java",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/persistence/VerrouDuReferentielDesInfirmiersJdbc.java",
            "contexts/stock/src/main/java/com/hemodialyse/backend/contexts/stock/infrastructure/acl/legacycentres/CentresActifsLegacy.java",
            "contexts/stock/src/main/java/com/hemodialyse/backend/contexts/stock/infrastructure/acl/legacycentres/HorlogeDesCentresLegacy.java",
            "contexts/stock/src/main/java/com/hemodialyse/backend/contexts/stock/infrastructure/acl/legacyseances/PerimetreDesSeancesLegacy.java",
            "contexts/stock/src/main/java/com/hemodialyse/backend/contexts/stock/infrastructure/persistence/InventairesJdbc.java",
            "contexts/stock/src/main/java/com/hemodialyse/backend/contexts/stock/infrastructure/persistence/OperationsDeStockJdbc.java",
            "platform/src/main/java/com/hemodialyse/backend/application/auth/AuthService.java",
            "platform/src/main/java/com/hemodialyse/backend/application/direction/DirectionAccountService.java",
            "platform/src/main/java/com/hemodialyse/backend/application/direction/DirectionDashboardQueryService.java",
            "platform/src/main/java/com/hemodialyse/backend/application/direction/DirectionRealtimeService.java",
            "platform/src/main/java/com/hemodialyse/backend/application/direction/DirectionSnapshotService.java",
            "platform/src/main/java/com/hemodialyse/backend/application/direction/SocieteAdminAccountService.java",
            "platform/src/main/java/com/hemodialyse/backend/application/query/PatientReferenceLookup.java",
            "platform/src/main/java/com/hemodialyse/backend/infrastructure/config/DevSuperAdminBootstrapInitializer.java",
            "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/adapter/MouvementPatientJdbcAdapter.java",
            // Admin générique (ADR 0070) : écrit `medecin` (désactivation, décision du 2026-11-03) ; salle et position_creneau par SQL dynamique.
            "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/adapter/referential/ReferentialAdminJdbcAdapter.java",
            "platform/src/main/java/com/hemodialyse/backend/infrastructure/reporting/DocumentIdentityProvider.java",
            "platform/src/main/java/com/hemodialyse/backend/infrastructure/reporting/ModeleDocumentProvisioner.java",
            "platform/src/main/java/com/hemodialyse/backend/infrastructure/scheduling/ObservancePrescriptionScheduler.java",
            "platform/src/main/java/com/hemodialyse/backend/infrastructure/scheduling/SeanceRegularisationScheduler.java",
            "platform/src/main/java/com/hemodialyse/backend/infrastructure/security/DirectionAccessGuard.java",
            "platform/src/main/java/com/hemodialyse/backend/infrastructure/web/rest/UserRestController.java");

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
                            .filter(p -> !chemin(p).contains("/entity/"))
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
