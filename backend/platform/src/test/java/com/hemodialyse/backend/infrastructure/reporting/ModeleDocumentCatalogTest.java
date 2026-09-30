package com.hemodialyse.backend.infrastructure.reporting;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Règle : tout rapport livré est un modèle de document. Chaque {@code reports/*.jrxml} doit être déclaré dans
 * {@link ModeleDocumentCatalog} (sinon il ne serait ni provisionné pour les centres ni personnalisable).
 */
class ModeleDocumentCatalogTest {

    @Test
    void everyShippedReportTemplateIsADocumentModel() throws Exception {
        Set<String> declared = ModeleDocumentCatalog.entries().stream()
                .map(ModeleDocumentCatalog.Entry::cheminJrxml).collect(Collectors.toSet());
        List<String> shipped;
        try (var files = Files.list(Path.of("src/main/resources/reports"))) {
            shipped = files.map(p -> "reports/" + p.getFileName()).filter(n -> n.endsWith(".jrxml")).sorted().toList();
        }
        assertThat(declared).as("modèles Jasper livrés mais absents du catalogue des modèles de documents")
                .containsAll(shipped);
        for (String path : declared) {
            assertThat(Files.exists(Path.of("src/main/resources").resolve(path))).as(path).isTrue();
        }
    }

    @Test
    void typesAreUniqueAndResolvable() {
        assertThat(ModeleDocumentCatalog.entries().stream().map(ModeleDocumentCatalog.Entry::type).distinct().count())
                .isEqualTo(ModeleDocumentCatalog.entries().size());
        assertThat(ModeleDocumentCatalog.find(" inventaire_stock ")).isPresent();
        assertThat(ModeleDocumentCatalog.find("INCONNU")).isEmpty();
    }
}

