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
 * Les tables {@code caisse_assurance}, {@code agence} et {@code centre_payeur} appartiennent au contexte patient-couverture (slice RF3,
 * ADR 0126 : la chaîne des payeurs ; propriété décidée par l'ADR 0121, décision 2). La garde scanne le Java ET les gabarits {@code .jrxml}
 * (RF4) ; les butoirs sont des dates comparées à l'horloge (RF4, {@link ButoirsDesGardesDeTables}). Hors de ce contexte (les trois entités JPA ont été retirées en PL1), aucun
 * nouveau code de production ne les nomme, ni en SQL, ni comme nom de table d'une description de table
 * ({@code new Table("agence", …)}). Les lecteurs d'aujourd'hui, hors de PC, forment la liste blanche NOMMÉE et DATÉE
 * ({@link #LECTEURS_CONNUS}) : après RF3c, seules quatre jointures de la Facturation sur l'instantané figé de la facture restent (ADR 0128,
 * RF3c-J1), chacune avec sa raison et son butoir ; les autres lisent le langage publié {@code PayeursDuCentre} (RF3b : annuaire ;
 * RF3c : Comptabilité, reprise des soldes) ou sont servies par Patient et couverture (lectures communes). La gestion (CRUD, import)
 * passe par {@code GestionDesPayeurs} (RF3a) : l'administration générique ne nomme plus aucune des trois tables.
 * <ul>
 *   <li>liste blanche NOMMÉE et DATÉE, effectif asserté : 11 à l'ouverture de la garde (RF3a, 2026-11-10), 4 après RF3c ;</li>
 *   <li>une entrée qui ne nomme plus aucune des trois tables est une entrée morte : le test échoue.</li>
 * </ul>
 */
class TableDesPayeursTest {

    private static final Set<String> TABLES = Set.of("caisse_assurance", "agence", "centre_payeur");

    private static final String BUTOIR = " Butoir : revue de l'ADR 0128 au 2027-03-31 (sinon reconduction motivée).";

    /**
     * RF3c-J1 : l'agence et le centre payeur d'une facture sont des INSTANTANÉS figés à l'émission (agence_id_snapshot,
     * centre_payeur_id_snapshot), pas des lectures du référentiel ; la caisse n'est atteinte que par jointure sur l'agence figée.
     */
    private static final String INSTANTANE = "2026-11-10 (RF3c) : jointure sur l'instantané figé de la facture (agence_id_snapshot / "
            + "centre_payeur_id_snapshot, ADR 0128 RF3c-J1) avec agrégation GROUP BY / filtre SQL sur la chaîne ; le découper en lectures "
            + "publiées déplacerait tri, filtre et pagination dans Java sans preuve de parité hors H2." + BUTOIR;

    /** Lecteurs connus hors du contexte patient-couverture (chemin depuis {@code backend/}) : onze à l'ouverture, sept après RF3b, quatre après RF3c (2026-11-10). */
    private static final Map<String, String> LECTEURS_CONNUS = new TreeMap<>();

    static {
        String ctx = "contexts/";
        String facturation = ctx + "facturation/src/main/java/com/hemodialyse/backend/contexts/facturation/infrastructure/persistence/";
        LECTEURS_CONNUS.put(facturation + "LectureDeLaSyntheseJdbc.java", INSTANTANE
                .replace("sans preuve de parité hors H2", "(ORDER BY du code et du libellé de la caisse selon la collation de la base)"));
        LECTEURS_CONNUS.put(facturation + "LectureDuTableauDeBordJdbc.java", INSTANTANE
                .replace("sans preuve de parité hors H2", "(jointures NON bornées au centre, KB-FAC-65, que l'annuaire borne)"));
        LECTEURS_CONNUS.put(facturation + "SituationsDesFacturesJdbc.java", INSTANTANE
                .replace("sans preuve de parité hors H2", "(COALESCE instantané / payeur courant du patient, filtres par identité de caisse, agence "
                        + "et centre payeur, COUNT DISTINCT et LIMIT / OFFSET sur la chaîne)"));
        LECTEURS_CONNUS.put(facturation + "SyntheseFinanciereDesCentresJdbc.java", INSTANTANE
                .replace("sans preuve de parité hors H2", "(somme par caisse de plusieurs centres en une requête, MAX(nom), caisse non bornée)"));
        // RF4 (revue RF1-RF3d, constat 2) : le gabarit JRXML statique de l'impression de la synthèse lit encore les deux tables (RF3c-J1).
        LECTEURS_CONNUS.put("platform/src/main/resources/reports/synthese_mensuelle_facturation.jrxml", INSTANTANE
                .replace("avec agrégation GROUP BY / filtre SQL sur la chaîne", "avec agrégation GROUP BY par forfait et classement CNAS / CASNOS / "
                        + "CAMSSP / autres par préfixe du code de caisse (ModeleDocumentCatalog, gabarit statique, impression de la synthèse)")
                .replace("sans preuve de parité hors H2", "(le gabarit dynamique du même document lit déjà l'annuaire ; ce gabarit statique reste en "
                        + "SQL, KB-RF4-02)"));
    }

    /**
     * PL1 (a) : les trois entités JPA des payeurs sont retirées (les tables ont leur {@code CREATE TABLE} dans {@code db/schema.sql}) : plus
     * aucune exclusion, toute entité JPA est scannée (RF4, constat 3).
     */
    private static final Set<String> ENTITES_JPA_CONNUES = Set.of();

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
                            .filter(p -> !ButoirsDesGardesDeTables.estUneEntiteJpaConnue(relatif(p), ENTITES_JPA_CONNUES))
                            .forEach(trouves::add);
                }
                // RF4 : les gabarits JRXML portent du SQL (jointures de l'impression) : ils sont scannés comme le code Java
                Path ressources = module.resolve("src/main/resources");
                if (Files.isDirectory(ressources)) {
                    try (Stream<Path> gabarits = Files.walk(ressources)) {
                        gabarits.filter(p -> p.toString().endsWith(".jrxml")).forEach(trouves::add);
                    }
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
        assertThat(LECTEURS_CONNUS).as("RF3c : 4 lecteurs Java restent hors patient-couverture, les jointures sur l'instantané de la facture "
                + "(11 à l'ouverture de la garde en RF3a, 4 lecteurs « annuaire » migrés en RF3b, puis Comptabilité, reprise des soldes et "
                + "lectures communes de l'Organisation migrées ou déplacées en RF3c, le 2026-11-10) ; RF4 : + le gabarit JRXML statique de "
                + "l'impression de la synthèse (constat 2 de la revue), soit 5").hasSize(5);
    }

    @Test
    void chaque_lecteur_connu_porte_sa_raison_datee_et_son_butoir() {
        assertThat(LECTEURS_CONNUS.values()).allSatisfy(raison -> assertThat(raison).startsWith("2026-11-10 (RF3").contains(")"));
        assertThat(ButoirsDesGardesDeTables.butoirsAbsentsOuDepasses(LECTEURS_CONNUS, LocalDate.of(2026, 11, 10)))
                .as("chaque lecteur connu porte un butoir lisible (date ISO après « Butoir : »)").isEmpty();
    }

    @Test
    void aucun_butoir_n_est_depasse_a_la_date_du_jour() {
        assertThat(ButoirsDesGardesDeTables.butoirsAbsentsOuDepasses(LECTEURS_CONNUS, LocalDate.now()))
                .as("butoir (2027-03-31) dépassé : reconduire AVEC une raison motivée et une nouvelle date, ou migrer le lecteur "
                        + "(revue de l'ADR 0128)").isEmpty();
    }

    @Test
    void la_garde_des_butoirs_echoue_apres_la_date_et_pas_avant_et_exige_une_date_lisible() {
        Map<String, String> lecteurs = Map.of("a.java", INSTANTANE, "b.java", "2026-11-10 (RF3c) : sans butoir chiffré.");
        assertThat(ButoirsDesGardesDeTables.butoir(INSTANTANE)).contains(LocalDate.of(2027, 3, 31));
        assertThat(ButoirsDesGardesDeTables.butoirsAbsentsOuDepasses(lecteurs, LocalDate.of(2027, 3, 31))).containsExactly("b.java");
        assertThat(ButoirsDesGardesDeTables.butoirsAbsentsOuDepasses(lecteurs, LocalDate.of(2027, 4, 1))).containsExactly("a.java", "b.java");
    }

    @Test
    void aucune_exclusion_toutes_les_entites_jpa_sont_scannees() throws IOException {
        List<String> scannes = sourcesDeProduction(backend()).stream().map(TableDesPayeursTest::relatif).toList();
        assertThat(ENTITES_JPA_CONNUES).isEmpty();
        assertThat(scannes).as("une entité JPA est scannée").anyMatch(s -> s.contains("/entity/"));
        assertThat(scannes).as("les trois entités des payeurs n'existent plus (PL1 a)")
                .noneMatch(s -> s.endsWith("/AgenceJpaEntity.java") || s.endsWith("/CaisseAssuranceJpaEntity.java") || s.endsWith("/CentrePayeurJpaEntity.java"));
        assertThat(ButoirsDesGardesDeTables.estUneEntiteJpaConnue("platform/src/main/java/x/entity/Inconnue.java", ENTITES_JPA_CONNUES)).isFalse();
    }

    @Test
    void la_garde_detecte_une_table_nommee_dans_un_gabarit_jrxml() throws IOException {
        Path bac = Files.createTempDirectory("garde-payeurs-jrxml");
        Path gabarit = bac.resolve("g.jrxml");
        Files.writeString(gabarit, "<queryString><![CDATA[SELECT 1 FROM factures f LEFT JOIN agence a ON a.id = f.agence_id_snapshot]]></queryString>");
        assertThat(nommeUneTableDesPayeurs(gabarit)).isTrue();
        assertThat(sourcesDeProduction(backend()).stream().map(TableDesPayeursTest::relatif))
                .contains("platform/src/main/resources/reports/synthese_mensuelle_facturation.jrxml");
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
