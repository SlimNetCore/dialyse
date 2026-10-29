package com.hemodialyse.backend.characterization;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde des goldens {@code *_nouveau_code} (revue P8, M1) : un golden approuvé n'enregistre jamais une exécution cassée. Deux signes
 * d'une approbation en lot sans relecture : un nom doublement suffixé ({@code _nouveau_code_nouveau_code}) et un golden dont TOUTES
 * les réponses (au moins trois) sont des 500.
 */
class GoldensNouveauCodeGardeTest {

    private static final Path RACINE = Path.of("src", "test", "resources", "characterization");
    /**
     * Une réponse HTTP notée dans un golden : statut seul, ou statut suivi d'un code ou d'un message.
     */
    private static final Pattern REPONSE = Pattern.compile("\"(200|201|202|204|400|401|403|404|405|409|415|422|500|502|503)(?:[ \"])");

    private static List<Path> goldensDuNouveauCode() throws IOException {
        try (Stream<Path> fichiers = Files.walk(RACINE)) {
            return fichiers.filter(p -> p.getFileName().toString().contains("_nouveau_code")).toList();
        }
    }

    @Test
    void should_have_no_golden_with_a_doubled_new_code_suffix() throws IOException {
        List<String> doubles = goldensDuNouveauCode().stream().map(p -> p.getFileName().toString())
                .filter(n -> n.contains("_nouveau_code_nouveau_code")).toList();
        assertThat(doubles).as("goldens approuvés en lot sous un nom doublement suffixé").isEmpty();
    }

    @Test
    void should_not_approve_a_golden_that_records_a_broken_run_made_only_of_500() throws IOException {
        List<String> casses = new ArrayList<>();
        for (Path golden : goldensDuNouveauCode()) {
            String contenu = Files.readString(golden, StandardCharsets.UTF_8);
            Matcher m = REPONSE.matcher(contenu);
            int total = 0;
            int erreursServeur = 0;
            while (m.find()) {
                total++;
                if (m.group(1).startsWith("50")) erreursServeur++;
            }
            if (total >= 3 && erreursServeur == total)
                casses.add(golden.getFileName() + " (" + total + " réponses 5xx)");
        }
        assertThat(casses).as("goldens qui enregistrent une exécution cassée").isEmpty();
    }
}
