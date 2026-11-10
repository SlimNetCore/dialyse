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
 * Les tables {@code caisse_assurance}, {@code agence} et {@code centre_payeur} appartiennent au contexte patient-couverture (slice RF3,
 * ADR 0126 : la chaîne des payeurs ; propriété décidée par l'ADR 0121, décision 2). Hors de ce contexte (et des entités JPA), aucun
 * nouveau code de production ne les nomme, ni en SQL, ni comme nom de table d'une description de table
 * ({@code new Table("agence", …)}). Les lecteurs d'aujourd'hui, hors de PC, forment la liste blanche NOMMÉE et DATÉE
 * ({@link #LECTEURS_CONNUS}) : ils migrent vers le langage publié {@code PayeursDuCentre} (RF3b : annuaire, RF3c : Comptabilité,
 * Facturation, lectures communes de l'Organisation). La gestion (CRUD, import) passe désormais par {@code GestionDesPayeurs}
 * (RF3a) : l'administration générique ne nomme plus aucune des trois tables.
 * <ul>
 *   <li>liste blanche NOMMÉE et DATÉE, effectif asserté : 11 à l'ouverture de la garde (RF3a, 2026-11-10) ;</li>
 *   <li>une entrée qui ne nomme plus aucune des trois tables est une entrée morte : le test échoue.</li>
 * </ul>
 */
class TableDesPayeursTest {

    private static final Set<String> TABLES = Set.of("caisse_assurance", "agence", "centre_payeur");

    private static final String ANNUAIRE = "2026-11-10 (RF3b) : lecture « annuaire » du libellé de la chaîne des payeurs par jointure SQL ; "
            + "à remplacer par le langage publié PayeursDuCentre.";
    private static final String JOINTURE = "2026-11-10 (RF3c) : jointure de facturation (agrégation GROUP BY sur la chaîne caisse -> agence -> "
            + "centre payeur) à ne pas découper ; à reprendre en jalons datés par la Facturation via PayeursDuCentre.";

    /** Lecteurs connus hors du contexte patient-couverture (chemin depuis {@code backend/}) : onze à l'ouverture (2026-11-10). */
    private static final Map<String, String> LECTEURS_CONNUS = new TreeMap<>();

    static {
        String ctx = "contexts/";
        LECTEURS_CONNUS.put(ctx + "comptabilite/src/main/java/com/hemodialyse/backend/contexts/comptabilite/infrastructure/persistence/PayeursDuCentreJdbc.java",
                "2026-11-10 (RF3c) : ACL de Comptabilité vers les tables des payeurs, nominative (ADR 0121, décision 3) ; remplacée par la "
                        + "lecture du langage publié PayeursDuCentre quand le lien comptabilite -> patient-couverture sera ajouté.");
        LECTEURS_CONNUS.put(ctx + "facturation/src/main/java/com/hemodialyse/backend/contexts/facturation/infrastructure/persistence/LectureDeLaSyntheseJdbc.java", JOINTURE);
        LECTEURS_CONNUS.put(ctx + "facturation/src/main/java/com/hemodialyse/backend/contexts/facturation/infrastructure/persistence/LectureDuTableauDeBordJdbc.java", JOINTURE);
        LECTEURS_CONNUS.put(ctx + "facturation/src/main/java/com/hemodialyse/backend/contexts/facturation/infrastructure/persistence/RepriseDesSoldesJdbc.java", JOINTURE);
        LECTEURS_CONNUS.put(ctx + "facturation/src/main/java/com/hemodialyse/backend/contexts/facturation/infrastructure/persistence/SituationsDesFacturesJdbc.java", JOINTURE);
        LECTEURS_CONNUS.put(ctx + "facturation/src/main/java/com/hemodialyse/backend/contexts/facturation/infrastructure/persistence/SyntheseFinanciereDesCentresJdbc.java", JOINTURE);
        LECTEURS_CONNUS.put(ctx + "organisation/src/main/java/com/hemodialyse/backend/contexts/organisation/infrastructure/persistence/ReferentielsCommunsJdbc.java",
                "2026-11-10 (RF3c) : lectures communes /referentials/{caisses,agences,centres-payeurs,centres-payeurs-details}, servies "
                        + "comme avant en RF3a (caches ref.* invalidés par PC) ; à déplacer vers PC avec retrait du code Organisation.");
        LECTEURS_CONNUS.put(ctx + "pilotage-direction/src/main/java/com/hemodialyse/backend/contexts/pilotagedirection/infrastructure/persistence/PatientsDesCentresJdbc.java", ANNUAIRE);
        LECTEURS_CONNUS.put(ctx + "seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/persistence/FacturationDesSeancesJdbc.java", ANNUAIRE);
        LECTEURS_CONNUS.put(ctx + "seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/persistence/SeancesRealiseesDesCentresJdbc.java", ANNUAIRE);
        LECTEURS_CONNUS.put("platform/src/main/java/com/hemodialyse/backend/infrastructure/reporting/FacturationSyntheseMensuelleReportService.java", ANNUAIRE);
    }

    private static final Pattern NOMMEE = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+([A-Za-z_][A-Za-z0-9_]*)|\\bTable\\(\"([a-z_]+)\"", Pattern.CASE_INSENSITIVE);

    private static Path backend() {
        return Path.of("..").toAbsolutePath().normalize();
    }

    private static String chemin(Path p) {
        return p.toString().replace('\\', '/');
    }

    private static String relatif(Path p) {
        return chemin(backend().relativize(p));
    }

    private static List<Path> sourcesDeProduction(Path racine) throws IOException {
        List<Path> trouves = new ArrayList<>();
        try (Stream<Path> modules = Stream.concat(Files.list(racine), Files.list(racine.resolve("contexts")))) {
            for (Path module : modules.toList()) {
                Path java = module.resolve("src/main/java");
                if (!Files.isDirectory(java) || chemin(module).endsWith("/contexts/patient-couverture")) continue;
                try (Stream<Path> fichiers = Files.walk(java)) {
                    fichiers.filter(p -> p.toString().endsWith(".java"))
                            .filter(p -> !chemin(p).contains("/entity/"))
                            .forEach(trouves::add);
                }
            }
        }
        return trouves;
    }

    private static boolean nommeUneTableDesPayeurs(Path source) {
        try {
            Matcher m = NOMMEE.matcher(Files.readString(source));
            while (m.find()) {
                String table = m.group(2) != null ? m.group(2) : m.group(3);
                if (table != null && TABLES.contains(table.toLowerCase(java.util.Locale.ROOT))) return true;
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aucun_nouveau_code_hors_de_patient_couverture_ne_nomme_les_tables_des_payeurs() throws IOException {
        Set<String> nouveaux = new TreeSet<>();
        for (Path source : sourcesDeProduction(backend())) {
            if (nommeUneTableDesPayeurs(source) && !LECTEURS_CONNUS.containsKey(relatif(source))) nouveaux.add(relatif(source));
        }
        assertThat(nouveaux)
                .as("lecteurs/écrivains des tables caisse_assurance, agence, centre_payeur hors patient-couverture : passer par patientcouverture.v1")
                .isEmpty();
    }

    @Test
    void aucune_entree_morte_dans_la_liste_blanche() throws IOException {
        Set<String> morts = new TreeSet<>();
        for (String chemin : LECTEURS_CONNUS.keySet()) {
            Path source = backend().resolve(chemin);
            if (!Files.isRegularFile(source) || !nommeUneTableDesPayeurs(source)) morts.add(chemin);
        }
        assertThat(morts).as("entrées mortes à retirer").isEmpty();
    }

    @Test
    void l_effectif_des_lecteurs_connus_est_asserte() {
        assertThat(LECTEURS_CONNUS).as("RF3a : 11 lecteurs hors patient-couverture à l'ouverture de la garde (2026-11-10)").hasSize(11);
    }

    @Test
    void chaque_lecteur_connu_porte_sa_raison_datee_et_son_butoir() {
        assertThat(LECTEURS_CONNUS.values()).allSatisfy(raison -> assertThat(raison).startsWith("2026-11-10 (RF3").contains(")"));
    }

    @Test
    void la_garde_detecte_une_table_nommee_en_sql_ou_en_litteral() throws IOException {
        Path bac = Files.createTempDirectory("garde-payeurs");
        Path sql = bac.resolve("Sql.java");
        Files.writeString(sql, "class Sql { String s = \"SELECT id FROM caisse_assurance WHERE center_id = ?\"; }");
        Path jointure = bac.resolve("Jointure.java");
        Files.writeString(jointure, "class Jointure { String s = \"SELECT 1 FROM patients p LEFT JOIN centre_payeur cp ON cp.id = p.x\"; }");
        Path litteral = bac.resolve("Litteral.java");
        Files.writeString(litteral, "class Litteral { Object t = new Table(\"agence\", List.of()); }");
        Path propre = bac.resolve("Propre.java");
        Files.writeString(propre, "class Propre { String s = \"SELECT id FROM patients WHERE centre_payeur_id = ?\"; "
                + "String alias = \"agence\"; String f = \"SELECT 1 FROM agence_snapshot WHERE x = ?\"; }");

        assertThat(nommeUneTableDesPayeurs(sql)).isTrue();
        assertThat(nommeUneTableDesPayeurs(jointure)).isTrue();
        assertThat(nommeUneTableDesPayeurs(litteral)).isTrue();
        assertThat(nommeUneTableDesPayeurs(propre)).isFalse();
    }

    @Test
    void la_garde_scanne_la_plateforme_et_les_autres_contextes_sans_patient_couverture() throws IOException {
        List<String> scannes = sourcesDeProduction(backend()).stream().map(TableDesPayeursTest::relatif).toList();
        assertThat(scannes).anyMatch(s -> s.startsWith("platform/src/main/java/"));
        assertThat(scannes).anyMatch(s -> s.startsWith("contexts/seance/"));
        assertThat(scannes).anyMatch(s -> s.startsWith("contexts/facturation/"));
        assertThat(scannes).noneMatch(s -> s.startsWith("contexts/patient-couverture/"));
    }
}
