package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le module {@code demo-data} (SEED2, ADR 0189) est le SEUL endroit autorisé à lire plusieurs contextes pour fabriquer des données de
 * démonstration. Ce n'est pas un contexte : aucune règle datée, la règle est structurelle.
 * <ul>
 *   <li>il ne contient que des scripts SQL de démonstration sous {@code src/main/resources/db/demo/} (aucun Java) ;</li>
 *   <li>aucun contexte, ni le noyau partagé, ne dépend de lui : seule la plateforme (composition) le déclare, en scope runtime ;</li>
 *   <li>le script d'amorçage de la plateforme ({@code db/seed.sql}) ne contient que l'identité : il ne lit aucune table d'un contexte.</li>
 * </ul>
 */
class DonneesDeDemonstrationTest {

    private static Path backend() {
        return Path.of("..").toAbsolutePath().normalize();
    }

    @Test
    void le_module_de_demonstration_ne_contient_que_des_scripts_sql_de_demonstration() throws IOException {
        Path module = backend().resolve("demo-data");
        assertThat(module.resolve("src/main/java")).as("aucun code Java : le module ne fait que composer des données").doesNotExist();
        Path racine = module.resolve("src/main/resources");
        Set<String> hors = new TreeSet<>();
        try (Stream<Path> fichiers = Files.walk(racine)) {
            fichiers.filter(Files::isRegularFile).map(p -> racine.relativize(p).toString().replace('\\', '/'))
                    .filter(p -> !p.startsWith("db/demo/")).forEach(hors::add);
        }
        assertThat(hors).as("tout fichier du module vit sous db/demo/").isEmpty();
        assertThat(module.resolve("src/main/resources/db/demo/jeu-derive-seed.sql")).isRegularFile();
        assertThat(module.resolve("src/main/resources/db/demo/renadial-seed.sql")).isRegularFile();
    }

    @Test
    void aucun_contexte_ne_depend_du_module_de_demonstration() throws IOException {
        List<Path> poms;
        try (Stream<Path> contextes = Files.list(backend().resolve("contexts"))) {
            poms = contextes.map(c -> c.resolve("pom.xml")).filter(Files::isRegularFile).toList();
        }
        assertThat(poms).isNotEmpty();
        Set<String> fautifs = new TreeSet<>();
        for (Path pom : poms) {
            if (Files.readString(pom).contains("<artifactId>demo-data</artifactId>")) fautifs.add(pom.toString());
        }
        assertThat(fautifs).as("le module de démonstration compose les contextes, jamais l'inverse").isEmpty();
    }

    @Test
    void seule_la_plateforme_depend_du_module_de_demonstration_en_scope_runtime() throws IOException {
        String pom = Files.readString(backend().resolve("platform/pom.xml"));
        assertThat(pom).containsPattern(Pattern.compile(
                "<artifactId>demo-data</artifactId>\\s*<version>[^<]+</version>\\s*<scope>runtime</scope>"));
    }

    @Test
    void l_amorcage_de_la_plateforme_ne_garde_que_l_identite() throws IOException {
        String seed = Files.readString(backend().resolve("platform/src/main/resources/db/seed.sql"));
        Set<String> tables = new TreeSet<>();
        var m = Pattern.compile("\\b(?:FROM|JOIN|INTO|UPDATE)\\s+(?:public\\.)?([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE).matcher(seed);
        while (m.find()) tables.add(m.group(1).toLowerCase());
        assertThat(tables).as("db/seed.sql : identité seulement (rôles, utilisateurs, rattachements)")
                .isSubsetOf("app_role", "app_user", "app_user_role", "app_user_center", "centers", "dual");
    }
}
