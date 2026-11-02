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

    /**
     * Codes des refus métier des contextes refaits sur place : sous-classes de {@code RegleMetierViolee}
     * ({@code super("CODE", …)}) et règles construites par code ({@code regle("CODE", …)}, {@code new ReglePiece("CODE", …)}).
     */
    private static final Pattern CONTEXT_RULE_CODE = Pattern.compile(
            "(?:super|regle|new ReglePiece|new RegleDePlanificationViolee)\\(\"([A-Z][A-Z0-9]*_[A-Z0-9_]*[A-Z0-9])\"");

    private static List<Path> mainSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        // modules legacy (backend/<module>) et contextes refaits sur place (backend/contexts/<contexte>)
        try (Stream<Path> mains = Files.walk(backend, 5)) {
            for (Path main : mains.filter(p -> p.endsWith(Path.of("src", "main", "java")) && Files.isDirectory(p)
                    && !p.toString().contains("node_modules") && !p.toString().contains("target")
                    // module GELE le 2026-11-02 (ADR 0072, GEL.md) : non branché, hors réacteur, ses routes ne sont pas servies
                    && !p.toString().replace('\\', '/').contains("/contexts/dossier-medical/")
                    // module GELE le 2026-11-02 (ADR 0073, GEL.md) : non branché, hors réacteur, ses routes ne sont pas servies
                    && !p.toString().replace('\\', '/').contains("/contexts/patient-couverture/")
                    // module GELE le 2026-11-02 (ADR 0074, GEL.md) : non branché, hors réacteur, ses routes ne sont pas servies
                    && !p.toString().replace('\\', '/').contains("/contexts/seance/")
                    // module GELE le 2026-11-02 (ADR 0075, GEL.md) : non branché, hors réacteur, ses routes ne sont pas servies
                    && !p.toString().replace('\\', '/').contains("/contexts/pilotage-direction/")).toList()) {
                try (Stream<Path> files = Files.walk(main)) {
                    files.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
                }
            }
        }
        return sources;
    }

    /**
     * Garde de {@code 03-organisation.md} : les codes de refus de l'Organisation (table de l'advice et codes propres, dont le 409
     * {@code ORGANISATION_ECRITURE_CONCURRENTE} et le 403 {@code ROLE_SCOPE}) et les règles de droits, de garde de centre, de 400 et de
     * suppression logique (RG-ORG-060 à 066) sont documentés.
     */
    @Test
    void organisationErrorCodesAndAccessRulesAreDocumented() throws IOException {
        Path advice = backend.resolve("contexts/organisation/src/main/java/com/hemodialyse/backend/contexts/organisation/"
                + "interfaces/http/ErreursDeLOrganisationAdvice.java");
        String source = Files.readString(advice, StandardCharsets.UTF_8);
        Set<String> codes = new TreeSet<>(Set.of("ORGANISATION_ECRITURE_CONCURRENTE", "ROLE_SCOPE", "REFERENTIAL_IN_USE"));
        Matcher entries = Pattern.compile("Map\\.entry\\(\"([A-Z][A-Z0-9_]*)\"").matcher(source);
        while (entries.find()) codes.add(entries.group(1));
        Set<String> missing = new TreeSet<>();
        for (String code : codes) if (!documentation.contains("`" + code + "`")) missing.add(code);
        assertThat(missing).as("codes de l'Organisation absents de 03-organisation.md").isEmpty();
        for (int n = 60; n <= 66; n++) {
            assertThat(documentation).as("RG-ORG-0" + n).contains("**RG-ORG-0" + n + "**");
        }
    }

    @Test
    void everyContextRuleCodeIsDocumented() throws IOException {
        Set<String> missing = new TreeSet<>();
        for (Path source : mainSources()) {
            if (!source.toString().replace('\\', '/').contains("/contexts/")) continue;
            String content = Files.readString(source, StandardCharsets.UTF_8);
            if (!content.contains("RegleMetierViolee") && !content.contains("ReglePiece")
                    && !content.contains("regle(") && !content.contains("RegleDePlanificationViolee")) continue;
            Matcher matcher = CONTEXT_RULE_CODE.matcher(content);
            while (matcher.find()) {
                if (!documentation.contains("`" + matcher.group(1) + "`")) missing.add(matcher.group(1));
            }
        }
        assertThat(missing).as("codes de refus des contextes absents de docs/reference/regles-de-gestion").isEmpty();
    }

    /**
     * L'annexe ne cite pas de contrôleur ou de tâche disparus du code (une entrée périmée trompe le lecteur).
     */
    @Test
    void theAnnexCitesNoRemovedControllerOrJob() throws IOException {
        Set<String> existing = new TreeSet<>();
        for (Path source : mainSources()) existing.add(source.getFileName().toString().replace(".java", ""));
        Matcher cited = Pattern.compile("^\\| `([A-Z][A-Za-z0-9]+)`", Pattern.MULTILINE).matcher(annexe);
        Set<String> removed = new TreeSet<>();
        while (cited.find()) {
            if (!existing.contains(cited.group(1))) removed.add(cited.group(1));
        }
        assertThat(removed).as("entrées de 16-annexe-tracabilite.md sans classe correspondante").isEmpty();
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
            boolean controller = content.matches("(?s).*@RestController(?!Advice).*");
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

    /**
     * Slice P9 (KB-PLN-50, KB-PLN-106, décision du 2026-11-02) : un seul calcul de présence ; un patient en sommeil n'est plus compté
     * nulle part (occupants, nombre de patients, effectif requis, alertes, « Mon planning »). La règle amendée (RG-PLN-046 et celle de
     * « Mon planning ») doit rester documentée tant que le golden {@code planning_patient_en_sommeil_nouveau_code} existe.
     */
    @Test
    void la_presence_unique_documente_que_le_sommeil_n_est_plus_compte_nulle_part() {
        assertThat(documentation)
                .contains("KB-PLN-106")
                .contains("n'apparaît plus dans « Mon planning »")
                .contains("dans l'effectif requis (décision du 2026-11-02, KB-PLN-50 et KB-PLN-106)")
                .contains("il est aussi celui de la présence des infirmiers");
    }
}
