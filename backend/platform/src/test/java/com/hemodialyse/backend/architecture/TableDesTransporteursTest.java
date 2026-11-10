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
 * Les tables {@code transporteur} et {@code categorie_transport} appartiennent au contexte patient-couverture (slice RF1, ADR 0122).
 * Hors de ce contexte (et des entités JPA), plus aucun code de production ne les nomme : ni en SQL, ni comme nom de table d'une
 * description de table ({@code new Table("transporteur", …)}). Le descripteur {@code ReferentialKind.TRANSPORTEUR} (champs, alias
 * d'en-têtes, modèle d'import) reste à l'administration générique : c'est le résiduel nominatif RF1-R1 de la fiche.
 * Les lecteurs passent par le langage publié {@code patientcouverture.v1} ({@code Transporteurs}, {@code CategoriesDeTransport}),
 * l'administration générique par {@code GestionDesTransporteurs}.
 * <ul>
 *   <li>liste blanche NOMMÉE et DATÉE ({@link #LECTEURS_CONNUS}) : vide à la livraison de RF1 ;</li>
 *   <li>une entrée qui ne nomme plus aucune des deux tables est une entrée morte : le test échoue.</li>
 * </ul>
 */
class TableDesTransporteursTest {

    private static final Set<String> TABLES_DES_TRANSPORTEURS = Set.of("transporteur", "categorie_transport");

    /** Lecteurs connus hors du contexte patient-couverture (chemin depuis {@code backend/}) : aucun depuis RF1 (2026-11-10). */
    private static final Map<String, String> LECTEURS_CONNUS = new TreeMap<>();

    /** Aucune entité JPA connue ne porte ces tables : l'exclusion nominative est vide (RF4, constat 3 de la revue). */
    private static final Set<String> ENTITES_JPA_CONNUES = Set.of();

    private static final Pattern NOMMEE = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+([A-Za-z_][A-Za-z0-9_]*)|\\bTable\\(\"(transporteur|categorie_transport)\"",
            Pattern.CASE_INSENSITIVE);

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
                            // RF4 : aucune entité JPA ne porte ces tables ; plus d'exclusion du dossier /entity/ entier
                            .filter(p -> !ButoirsDesGardesDeTables.estUneEntiteJpaConnue(relatif(p), ENTITES_JPA_CONNUES))
                            .forEach(trouves::add);
                }
            }
        }
        return trouves;
    }

    private static boolean nommeUneTableDesTransporteurs(Path source) {
        try {
            Matcher m = NOMMEE.matcher(Files.readString(source));
            while (m.find()) {
                String table = m.group(2) != null ? m.group(2) : m.group(3);
                if (TABLES_DES_TRANSPORTEURS.contains(table.toLowerCase())) return true;
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aucun_code_hors_de_patient_couverture_ne_nomme_les_tables_des_transporteurs() throws IOException {
        Set<String> nouveaux = new TreeSet<>();
        for (Path source : sourcesDeProduction(backend())) {
            if (nommeUneTableDesTransporteurs(source) && !LECTEURS_CONNUS.containsKey(relatif(source))) nouveaux.add(relatif(source));
        }
        assertThat(nouveaux)
                .as("lecteurs/écrivains des tables des transporteurs hors patient-couverture : passer par patientcouverture.v1")
                .isEmpty();
    }

    @Test
    void aucune_entree_morte_dans_la_liste_blanche() throws IOException {
        Set<String> morts = new TreeSet<>();
        for (String chemin : LECTEURS_CONNUS.keySet()) {
            Path source = backend().resolve(chemin);
            if (!Files.isRegularFile(source) || !nommeUneTableDesTransporteurs(source)) morts.add(chemin);
        }
        assertThat(morts).as("entrées mortes à retirer").isEmpty();
    }

    @Test
    void l_effectif_des_lecteurs_connus_est_asserte() {
        assertThat(LECTEURS_CONNUS).as("RF1 : 0 lecteur hors patient-couverture").hasSize(0);
    }

    @Test
    void aucun_butoir_n_est_a_comparer_et_les_entites_jpa_ne_sont_plus_exclues_en_bloc() throws IOException {
        // Liste blanche vide : rien à reconduire. Si une entrée apparaît, elle devra porter un butoir lisible et non dépassé.
        assertThat(ButoirsDesGardesDeTables.butoirsAbsentsOuDepasses(LECTEURS_CONNUS, java.time.LocalDate.now())).isEmpty();
        assertThat(sourcesDeProduction(backend()).stream().map(TableDesTransporteursTest::relatif)).anyMatch(s -> s.contains("/entity/"));
    }

    @Test
    void la_garde_detecte_une_table_nommee_en_sql_ou_en_litteral() throws IOException {
        Path bac = Files.createTempDirectory("garde-transporteurs");
        Path sql = bac.resolve("Sql.java");
        Files.writeString(sql, "class Sql { String s = \"SELECT id FROM transporteur WHERE center_id = ?\"; }");
        Path litteral = bac.resolve("Litteral.java");
        Files.writeString(litteral, "class Litteral { Object t = new Table(\"categorie_transport\", List.of()); }");
        Path propre = bac.resolve("Propre.java");
        Files.writeString(propre, "class Propre { String s = \"SELECT id FROM patients WHERE transporteur_aller_id = ?\"; "
                + "String alias = \"transporteur\"; }");

        assertThat(nommeUneTableDesTransporteurs(sql)).isTrue();
        assertThat(nommeUneTableDesTransporteurs(litteral)).isTrue();
        assertThat(nommeUneTableDesTransporteurs(propre)).isFalse();
    }

    @Test
    void la_garde_scanne_la_plateforme_et_les_autres_contextes_sans_patient_couverture() throws IOException {
        List<String> scannes = sourcesDeProduction(backend()).stream().map(TableDesTransporteursTest::relatif).toList();
        assertThat(scannes).anyMatch(s -> s.startsWith("platform/src/main/java/"));
        assertThat(scannes).anyMatch(s -> s.startsWith("contexts/organisation/"));
        assertThat(scannes).noneMatch(s -> s.startsWith("contexts/patient-couverture/"));
    }
}
