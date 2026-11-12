package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les tables {@code caisse_assurance}, {@code agence} et {@code centre_payeur} appartiennent au contexte patient-couverture (slice RF3,
 * ADR 0126 : la chaîne des payeurs ; propriété décidée par l'ADR 0121, décision 2). Règle PERMANENTE : hors de ce contexte, aucun code
 * de production (Java ou gabarit {@code .jrxml}, RF4) ne nomme ces tables, ni en SQL (FROM, JOIN, INTO, UPDATE, TABLE), ni comme nom de
 * table d'une description de table ({@code new Table("agence", …)}) : on lit le langage publié {@code PayeursDuCentre}.
 * <p>
 * Historique : onze lecteurs connus à l'ouverture de la garde (RF3a, 2026-11-10), sept après RF3b, quatre après RF3c, trois après PL8
 * (ADR 0147), AUCUN après PL8b (ADR 0149 : tableau de bord, situations des factures et synthèse financière lisent la chaîne des payeurs
 * publiée). Le tableau d'exceptions, ses raisons datées et ses butoirs sont supprimés : toute nouvelle jointure échoue ici.
 */
class TableDesPayeursTest {

    private static final Set<String> TABLES = Set.of("caisse_assurance", "agence", "centre_payeur");

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
        List<Path> trouvees = new ArrayList<>();
        try (Stream<Path> modules = Stream.concat(Files.list(racine), Files.list(racine.resolve("contexts")))) {
            for (Path module : modules.toList()) {
                Path java = module.resolve("src/main/java");
                if (!Files.isDirectory(java) || chemin(module).endsWith("/contexts/patient-couverture")) continue;
                try (Stream<Path> fichiers = Files.walk(java)) {
                    fichiers.filter(p -> p.toString().endsWith(".java")).forEach(trouvees::add);
                }
                // RF4 : les gabarits JRXML portent du SQL (jointures de l'impression) : ils sont scannés comme le code Java
                Path ressources = module.resolve("src/main/resources");
                if (Files.isDirectory(ressources)) {
                    try (Stream<Path> gabarits = Files.walk(ressources)) {
                        gabarits.filter(p -> p.toString().endsWith(".jrxml")).forEach(trouvees::add);
                    }
                }
            }
        }
        return trouvees;
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
    void aucun_code_hors_de_patient_couverture_ne_nomme_les_tables_des_payeurs() throws IOException {
        Set<String> fautifs = new TreeSet<>();
        for (Path source : sourcesDeProduction(backend())) {
            if (nommeUneTableDesPayeurs(source)) fautifs.add(relatif(source));
        }
        assertThat(fautifs)
                .as("lecteurs/écrivains des tables caisse_assurance, agence, centre_payeur hors patient-couverture : passer par patientcouverture.v1 "
                        + "(PayeursDuCentre), sans exception ni butoir (ADR 0149)")
                .isEmpty();
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
        assertThat(scannes).as("les entités JPA sont scannées").anyMatch(s -> s.contains("/entity/"));
    }
}
