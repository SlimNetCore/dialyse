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
 * La table {@code forfait} appartient au contexte patient-couverture (slice RF2, ADR 0124 : catalogue de forfaits). Hors de ce
 * contexte (et des entités JPA), plus aucun code de production ne la nomme, ni en SQL, ni comme nom de table d'une description de
 * table ({@code new Table("forfait", …)}), à une exception nominative ({@link #LECTEURS_CONNUS}). Le descripteur
 * {@code DescripteursDeReferentiels.FORFAIT} (champs, alias d'en-têtes, modèle d'import), publié par Patient et couverture depuis PL11-1 : c'était le résiduel
 * nominatif RF2-R1 de la fiche. Les lecteurs passent par le langage publié {@code patientcouverture.v1} ({@code ForfaitsDuCentre}),
 * l'administration générique par {@code GestionDuCatalogueDeForfaits}.
 * <ul>
 *   <li>liste blanche NOMMÉE, sans échéance ({@link #LECTEURS_CONNUS}), effectif asserté : 1 à la livraison de RF2 ;</li>
 *   <li>une entrée qui ne nomme plus la table est une entrée morte : le test échoue.</li>
 * </ul>
 */
class TableDesForfaitsTest {

    private static final String TABLE_DES_FORFAITS = "forfait";

    /** Lecteurs connus hors du contexte patient-couverture (chemin depuis {@code backend/}) : un seul depuis RF2 (2026-11-10). */
    private static final Map<String, String> LECTEURS_CONNUS = new TreeMap<>();

    // ForfaitsDeSeanceJdbc : entrée SOLDÉE par LEC-PAT (ii) (2026-11-14, ADR 0177), le forfait de la prise en charge est lu par
    // CouvertureDesPatients.forfaitApplicable ; aucune échéance n'est reportée. Plus aucun lecteur connu hors patient-couverture.

    /** Aucune entité JPA connue ne porte la table {@code forfait} : l'exclusion nominative est vide (RF4, constat 3 de la revue). */
    private static final Set<String> ENTITES_JPA_CONNUES = Set.of();

    private static final Pattern NOMMEE = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+([A-Za-z_][A-Za-z0-9_]*)|\\bTable\\(\"(forfait)\"",
            Pattern.CASE_INSENSITIVE);

    /** Jointure par virgule : {@code FROM prise_en_charge p, forfait f} (la table n'est pas le premier nom après FROM). */
    private static final Pattern PAR_VIRGULE = Pattern.compile("\\bFROM\\s+[^\"();]*?,\\s*forfait\\b", Pattern.CASE_INSENSITIVE);

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
                            // RF4 : aucune entité JPA ne porte cette table ; plus d'exclusion du dossier /entity/ entier
                            .filter(p -> !ExclusionsDesGardesDeTables.estUneEntiteJpaConnue(relatif(p), ENTITES_JPA_CONNUES))
                            .forEach(trouves::add);
                }
            }
        }
        return trouves;
    }

    private static boolean nommeLaTableDesForfaits(Path source) {
        try {
            String texte = Files.readString(source);
            if (PAR_VIRGULE.matcher(texte).find()) return true;
            Matcher m = NOMMEE.matcher(texte);
            while (m.find()) {
                String table = m.group(2) != null ? m.group(2) : m.group(3);
                if (TABLE_DES_FORFAITS.equalsIgnoreCase(table)) return true;
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aucun_code_hors_de_patient_couverture_ne_nomme_la_table_des_forfaits() throws IOException {
        Set<String> nouveaux = new TreeSet<>();
        for (Path source : sourcesDeProduction(backend())) {
            if (nommeLaTableDesForfaits(source) && !LECTEURS_CONNUS.containsKey(relatif(source))) nouveaux.add(relatif(source));
        }
        assertThat(nouveaux)
                .as("lecteurs/écrivains de la table forfait hors patient-couverture : passer par patientcouverture.v1")
                .isEmpty();
    }

    @Test
    void aucune_entree_morte_dans_la_liste_blanche() throws IOException {
        Set<String> morts = new TreeSet<>();
        for (String chemin : LECTEURS_CONNUS.keySet()) {
            Path source = backend().resolve(chemin);
            if (!Files.isRegularFile(source) || !nommeLaTableDesForfaits(source)) morts.add(chemin);
        }
        assertThat(morts).as("entrées mortes à retirer").isEmpty();
    }

    @Test
    void l_effectif_des_lecteurs_connus_est_asserte() {
        assertThat(LECTEURS_CONNUS).as("LEC-PAT (ii) : le dernier lecteur (RF2-R2, ForfaitsDeSeanceJdbc) est soldé").isEmpty();
    }

    @Test
    void le_dernier_lecteur_est_solde_et_non_reporte() throws IOException {
        Path ancien = backend().resolve(
                "contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/persistence/ForfaitsDeSeanceJdbc.java");
        assertThat(LECTEURS_CONNUS).isEmpty();
        assertThat(nommeLaTableDesForfaits(ancien)).as("ForfaitsDeSeanceJdbc ne nomme plus la table forfait").isFalse();    }

    @Test
    void les_entites_jpa_ne_sont_plus_exclues_en_bloc() throws IOException {
        assertThat(sourcesDeProduction(backend()).stream().map(TableDesForfaitsTest::relatif)).anyMatch(s -> s.contains("/entity/"));
    }

    @Test
    void la_garde_detecte_une_table_nommee_en_sql_ou_en_litteral() throws IOException {
        Path bac = Files.createTempDirectory("garde-forfaits");
        Path sql = bac.resolve("Sql.java");
        Files.writeString(sql, "class Sql { String s = \"SELECT id FROM forfait WHERE center_id = ?\"; }");
        Path jointure = bac.resolve("Jointure.java");
        Files.writeString(jointure, "class Jointure { String s = \"SELECT 1 FROM prise_en_charge p INNER JOIN forfait f ON f.id = p.x\"; }");
        Path virgule = bac.resolve("Virgule.java");
        Files.writeString(virgule, "class Virgule { String s = \"SELECT 1 FROM prise_en_charge p, forfait f WHERE f.id = p.x\"; }");
        Path virguleLongue = bac.resolve("VirguleLongue.java");
        Files.writeString(virguleLongue, "class VirguleLongue { String s = \"SELECT 1 FROM a x,\\n b y ,  FORFAIT f WHERE 1 = 1\"; }");
        Path litteral = bac.resolve("Litteral.java");
        Files.writeString(litteral, "class Litteral { Object t = new Table(\"forfait\", List.of()); }");
        Path propre = bac.resolve("Propre.java");
        Files.writeString(propre, "class Propre { String s = \"SELECT id FROM seances WHERE forfait_override_id = ?\"; "
                + "String alias = \"forfait\"; String f = \"SELECT 1 FROM facture_lignes WHERE forfait_id = ?\"; }");

        assertThat(nommeLaTableDesForfaits(sql)).isTrue();
        assertThat(nommeLaTableDesForfaits(jointure)).isTrue();
        assertThat(nommeLaTableDesForfaits(virgule)).isTrue();
        assertThat(nommeLaTableDesForfaits(virguleLongue)).isTrue();
        assertThat(nommeLaTableDesForfaits(litteral)).isTrue();
        assertThat(nommeLaTableDesForfaits(propre)).isFalse();
    }

    @Test
    void la_garde_scanne_la_plateforme_et_les_autres_contextes_sans_patient_couverture() throws IOException {
        List<String> scannes = sourcesDeProduction(backend()).stream().map(TableDesForfaitsTest::relatif).toList();
        assertThat(scannes).anyMatch(s -> s.startsWith("platform/src/main/java/"));
        assertThat(scannes).anyMatch(s -> s.startsWith("contexts/seance/"));
        assertThat(scannes).anyMatch(s -> s.startsWith("contexts/facturation/"));
        assertThat(scannes).noneMatch(s -> s.startsWith("contexts/patient-couverture/"));
    }
}
