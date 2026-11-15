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
 * La table {@code resultats_analyses} appartient au contexte Dossier médical (ADR 0100, décision 2 : écrite par {@code AnalysesJdbc} et
 * {@code BilansBiologiquesJdbc}, lue pour la dernière créatinine par {@code DerniereCreatinineJdbc}). Garde écrite par SE7f (KB-SEA-98) sur
 * le modèle de {@link TableDesSeancesTest} : elle scanne TOUT le code de production (plateforme, anciens modules, autres contextes, entités
 * JPA comprises : un {@code @Table(name = "resultats_analyses")} est un lecteur), sauf le contexte Dossier médical. Les lecteurs actuels sont
 * une liste blanche NOMMÉE, où chaque entrée porte sa nature et sa condition de sortie (un événement, jamais une date) : un lecteur hors
 * liste ou une entrée morte fait échouer le test.
 */
class TableDesAnalysesTest {

    private record Lecteur(String nature, String conditionDeSortie) {
    }

    /** Lecteurs SQL connus hors du contexte Dossier médical (chemin depuis {@code backend/}) : SE7f (ADR 0100). */
    private static final Map<String, Lecteur> LECTEURS_CONNUS = new TreeMap<>();

    private static void lecteur(String chemin, String nature, String conditionDeSortie) {
        LECTEURS_CONNUS.put(chemin, new Lecteur(nature, conditionDeSortie));
    }

    static {
        String plateforme = "platform/src/main/java/com/hemodialyse/backend/";
        // DM9 (ADR 0101) : DirectionIndicatorsQueryService et PatientStatsQueryService lisent par IndicateursCliniquesPubliees et
        // StatistiquesMedicalesPubliees ; retirés de la liste (8 → 6 entrées).
        // HistoricalRecordJdbcAdapter : entrée SOLDÉE par RP3b (2026-11-13, ADR 0172) ; l'import écrit les analyses par
        // RepriseDeLHistoriqueMedical (dossier-medical.v1).
        // MigrationRollbackJdbcAdapter : entrée SOLDÉE par RP2 (2026-11-13, ADR 0170) ; l'annulation d'un lot lit et supprime les analyses du
        // lot par DonneesMedicalesDeLaReprise (dossier-medical.v1).        String ressources = "platform/src/main/resources/";
        // db/seed.sql et db/demo/renadial-*.sql : entrées SOLDÉES par SEED2 (ADR 0188 et 0189) ; jeux de démonstration dans le module
        // demo-data (voir DonneesDeDemonstrationTest).
        // db/schema.sql : entrée SOLDÉE par S-DDL3b (2026-11-14, ADR 0187) ; le DDL H2 de resultats_analyses est dans le script du contexte
        // Dossier médical (contexts/dossier-medical/.../db/h2/dossier-medical-schema.sql).
    }

    private static final Set<String> TABLES = Set.of("resultats_analyses");

    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:public\\.)?([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LISTE_DE_TABLES = Pattern.compile(
            "\\bFROM\\s+[^;()\"]{0,160}?,\\s*(?:public\\.)?resultats_analyses\\b", Pattern.CASE_INSENSITIVE);
    /** Entité JPA déclarée sur la table (KB-SEA-98 : la garde des séances ne voyait pas les accès JPA). */
    private static final Pattern ENTITE_JPA = Pattern.compile("@Table\\s*\\(\\s*name\\s*=\\s*\"(?:public\\.)?resultats_analyses\"", Pattern.CASE_INSENSITIVE);
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

    /** Tous les sources de production (le contexte Dossier médical excepté), entités JPA COMPRISES. */
    static List<Path> sourcesDeProduction(Path racine) throws IOException {
        List<Path> trouves = new ArrayList<>();
        try (Stream<Path> modules = Stream.concat(Files.list(racine), Files.list(racine.resolve("contexts")))) {
            for (Path module : modules.toList()) {
                Path java = module.resolve("src/main/java");
                if (!Files.isDirectory(java) || chemin(module).endsWith("/contexts/dossier-medical")) continue;
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

    static boolean mentionneLaTable(Path source) {
        try {
            String texte = Files.readString(source);
            Matcher m = LECTURE_SQL.matcher(texte);
            while (m.find()) {
                if (TABLES.contains(m.group(2).toLowerCase())) return true;
            }
            if (LISTE_DE_TABLES.matcher(texte).find()) return true;
            if (ENTITE_JPA.matcher(texte).find()) return true;
            return NomsDeTableCalcules.contientLeLitteral(texte, TABLES) && NomsDeTableCalcules.construitUneRequete(texte);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> lecteurs(List<Path> sources) {
        Set<String> lecteurs = new TreeSet<>();
        for (Path source : sources) {
            if (mentionneLaTable(source)) lecteurs.add(relatif(source));
        }
        NomsDeTableCalcules.lecteursIndirects(sources, TABLES).forEach(p -> lecteurs.add(relatif(p)));
        return lecteurs;
    }

    @Test
    void aucun_nouveau_lecteur_sql_de_la_table_des_analyses_hors_de_la_liste_blanche() throws IOException {
        Set<String> nouveaux = new TreeSet<>(lecteurs(sourcesDeProduction(backend())));
        nouveaux.removeAll(LECTEURS_CONNUS.keySet());
        assertThat(nouveaux)
                .as("nouveaux lecteurs de resultats_analyses (SQL, nom de table calculé ou entité JPA) : passer par dossier-medical.v1 ou les inscrire (nommés, avec nature et condition de sortie)")
                .isEmpty();
    }

    @Test
    void aucune_entree_morte_dans_la_liste_blanche() throws IOException {
        Set<String> lecteurs = lecteurs(sourcesDeProduction(backend()));
        Set<String> morts = new TreeSet<>();
        for (String chemin : LECTEURS_CONNUS.keySet()) {
            if (!Files.isRegularFile(backend().resolve(chemin)) || !lecteurs.contains(chemin)) morts.add(chemin);
        }
        assertThat(morts).as("entrées qui ne lisent plus resultats_analyses : les retirer").isEmpty();
    }

    @Test
    void la_plateforme_n_a_plus_d_entite_jpa_ni_d_adaptateur_bc_seance_sur_les_analyses() throws IOException {
        List<String> disparus = List.of("platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/entity/ResultatAnalyseJpaEntity.java",
                "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/repository/ResultatAnalyseJpaRepository.java",
                "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/adapter/ResultatAnalyseRepositoryAdapter.java",
                "platform/src/main/java/com/hemodialyse/backend/application/query/KdigoGreffeQueryService.java");
        assertThat(disparus.subList(0, 3)).as("retirés par SE7f").noneMatch(c -> Files.isRegularFile(backend().resolve(c)));
        assertThat(disparus.get(3)).as("retiré par DM9 (ADR 0101) : l'évaluation KDIGO vit dans contexts/dossier-medical")
                .matches(c -> !Files.isRegularFile(backend().resolve(c)));
        // DM9 : les deux derniers lecteurs SQL de la plateforme passent par les langages publiés du Dossier médical.
        assertThat(lecteurs(sourcesDeProduction(backend()))).doesNotContain(
                "platform/src/main/java/com/hemodialyse/backend/application/direction/DirectionIndicatorsQueryService.java",
                "platform/src/main/java/com/hemodialyse/backend/application/query/PatientStatsQueryService.java");
    }

    @Test
    void chaque_entree_porte_une_nature_et_une_condition_de_sortie() {
        // Liste blanche VIDE depuis SEED2 (ADR 0189) : plus aucun lecteur hors du Dossier médical ; toute entrée ajoutée doit porter nature et condition de sortie.
        assertThat(LECTEURS_CONNUS).isEmpty();
        LECTEURS_CONNUS.forEach((chemin, lecteur) -> {
            assertThat(lecteur.nature()).as("nature de " + chemin).isNotBlank();
            assertThat(lecteur.conditionDeSortie()).as("condition de sortie de " + chemin).isNotBlank();
        });
        assertThat(LECTEURS_CONNUS.keySet()).noneMatch(c -> c.startsWith("contexts/"));
    }

    /**
     * RP2 (ADR 0170) : l'annulation d'un lot de reprise passe par {@code DonneesMedicalesDeLaReprise} ; son entrée est supprimée. Verrou : elle ne figure plus dans la liste blanche ET ne lit plus la table.
     */
    @Test
    void l_annulation_d_un_lot_de_reprise_ne_touche_plus_la_table_des_analyses() throws IOException {
        String adapter = "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/adapter/migration/MigrationRollbackJdbcAdapter.java";
        assertThat(LECTEURS_CONNUS).doesNotContainKey(adapter);
        assertThat(lecteurs(sourcesDeProduction(backend()))).doesNotContain(adapter);
    }

    /**
     * RP3b (2026-11-13, ADR 0172) : l'import de l'historique d'un lot de reprise écrit les analyses par {@code RepriseDeLHistoriqueMedical} ;
     * son entrée est supprimée. Verrou : elle ne figure plus dans la liste blanche ET ne
     * lit ni n'écrit plus la table.
     */
    @Test
    void l_import_de_l_historique_d_un_lot_de_reprise_n_ecrit_plus_la_table_des_analyses() throws IOException {
        String adapter = "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/adapter/migration/HistoricalRecordJdbcAdapter.java";
        assertThat(LECTEURS_CONNUS).doesNotContainKey(adapter);
        assertThat(lecteurs(sourcesDeProduction(backend()))).doesNotContain(adapter);
    }

    @Test
    void la_garde_detecte_une_entite_jpa_et_une_lecture_directe() throws IOException {
        Path bac = Files.createTempDirectory("garde-analyses-se7f");
        Path entite = bac.resolve("Entite.java");
        Files.writeString(entite, "@Entity\n@Table(name = \"resultats_analyses\", indexes = @Index(name = \"i\", columnList = \"patient_id\"))\nclass Entite { }");
        Path lecture = bac.resolve("Lecture.java");
        Files.writeString(lecture, "class Lecture { String s = \"SELECT creatinine_mg_dl FROM resultats_analyses WHERE patient_id = ?\"; }");
        Path creation = bac.resolve("schema.sql");
        Files.writeString(creation, "CREATE TABLE IF NOT EXISTS resultats_analyses (id UUID PRIMARY KEY);");
        Path propre = bac.resolve("Propre.java");
        Files.writeString(propre, "class Propre { String s = \"SELECT id FROM analyses_externes\"; }");

        assertThat(mentionneLaTable(entite)).as("entité JPA").isTrue();
        assertThat(mentionneLaTable(lecture)).as("lecture SQL").isTrue();
        assertThat(mentionneLaTable(creation)).as("CREATE TABLE IF NOT EXISTS").isTrue();
        assertThat(mentionneLaTable(propre)).as("autre table").isFalse();
    }
}
