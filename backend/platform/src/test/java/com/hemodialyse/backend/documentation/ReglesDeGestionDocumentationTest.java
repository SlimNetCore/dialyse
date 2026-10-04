package com.hemodialyse.backend.documentation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde anti-oubli du référentiel des règles de gestion ({@code docs/reference/regles-de-gestion}).
 * <p>
 * Le document de référence doit rester complet : toute règle métier ajoutée au code (code d'erreur métier,
 * contrôleur REST, tâche planifiée) doit être décrite dans ce référentiel dans la même modification. Ce test lit
 * les sources du dépôt et échoue en nommant ce qui manque.
 */
class ReglesDeGestionDocumentationTest {

    /**
     * Codes littéraux uniquement : un code construit dynamiquement (préfixe se terminant par « _ », comme
     * {@code PLACEMENT_<règle>}) est documenté à la main dans son chapitre.
     */
    private static final Pattern BUSINESS_CODE = Pattern.compile("new BusinessException\\(\"([A-Z][A-Z0-9_]*[A-Z0-9])\"");
    private static final Pattern RULE_REFERENCE = Pattern.compile("RG-[A-Z]{3}-\\d{3}[a-z]?");
    private static final Pattern RULE_DEFINITION = Pattern.compile("^- \\*\\*[^*]*?(RG-[A-Z]{3}-\\d{3}[a-z]?)");
    private static final Pattern PLACEHOLDER = Pattern.compile("RG-[A-Z]{3}-…");

    private static Path backend;
    private static String documentation;
    private static String annexe;
    private static List<String> definitionLines;

    @BeforeAll
    static void load() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("docs/reference/regles-de-gestion"))) {
            dir = dir.getParent();
        }
        assertThat(dir).as("racine du dépôt contenant docs/reference/regles-de-gestion").isNotNull();
        backend = dir.resolve("backend");
        Path docs = dir.resolve("docs/reference/regles-de-gestion");
        StringBuilder all = new StringBuilder();
        definitionLines = new ArrayList<>();
        try (Stream<Path> files = Files.list(docs)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                all.append(content).append('\n');
                content.lines().filter(l -> l.startsWith("- **")).forEach(definitionLines::add);
            }
        }
        documentation = all.toString();
        annexe = Files.readString(docs.resolve("16-annexe-tracabilite.md"), StandardCharsets.UTF_8);
    }

    private static List<Path> mainSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> modules = Files.list(backend)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path main = module.resolve("src/main/java");
                if (!Files.isDirectory(main)) continue;
                try (Stream<Path> files = Files.walk(main)) {
                    files.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
                }
            }
        }
        return sources;
    }

    @Test
    void everyBusinessErrorCodeIsDocumented() throws IOException {
        Set<String> missing = new TreeSet<>();
        for (Path source : mainSources()) {
            Matcher matcher = BUSINESS_CODE.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (matcher.find()) {
                String code = matcher.group(1);
                if (!documentation.contains("`" + code + "`")) missing.add(code);
            }
        }
        assertThat(missing).as("codes d'erreur métier absents de docs/reference/regles-de-gestion").isEmpty();
    }

    @Test
    void everyRestControllerAndScheduledJobIsTraced() throws IOException {
        Set<String> missing = new TreeSet<>();
        for (Path source : mainSources()) {
            String content = Files.readString(source, StandardCharsets.UTF_8);
            String name = source.getFileName().toString().replace(".java", "");
            boolean controller = content.contains("@RestController");
            boolean scheduled = content.contains("@Scheduled");
            if ((controller || scheduled) && !annexe.contains("`" + name + "`")) missing.add(name);
        }
        assertThat(missing)
                .as("contrôleurs REST ou tâches planifiées absents de 16-annexe-tracabilite.md")
                .isEmpty();
    }

    @Test
    void ruleIdentifiersAreUniqueResolvableAndComplete() {
        Map<String, Integer> definitions = new HashMap<>();
        for (String line : definitionLines) {
            Matcher matcher = RULE_DEFINITION.matcher(line);
            if (matcher.find()) definitions.merge(matcher.group(1), 1, Integer::sum);
        }
        assertThat(definitions).as("aucune règle définie").isNotEmpty();

        Set<String> duplicates = new TreeSet<>();
        definitions.forEach((id, count) -> {
            if (count > 1) duplicates.add(id);
        });
        assertThat(duplicates).as("identifiants de règles définis plusieurs fois").isEmpty();

        Set<String> unresolved = new TreeSet<>();
        Matcher references = RULE_REFERENCE.matcher(documentation);
        while (references.find()) {
            if (!definitions.containsKey(references.group())) unresolved.add(references.group());
        }
        assertThat(unresolved).as("références à des règles inexistantes").isEmpty();

        assertThat(PLACEHOLDER.matcher(documentation).find())
                .as("identifiant de règle laissé en suspens (RG-XXX-…)").isFalse();
    }
}
