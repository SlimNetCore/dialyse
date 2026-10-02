package com.hemodialyse.backend.infrastructure.reporting;

import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Les modèles sont livrés sous forme de sources {@code .jrxml} uniquement : ils sont compilés (et mis en cache) à
 * l'exécution, ce qui évite qu'un binaire {@code .jasper} périmé ne masque une modification du source.
 */
class JasperTemplateCompileTest {

    static final Set<String> IDENTITY_PARAMETERS = Set.of(
            "SOCIETE_NOM", "SOCIETE_ADRESSE", "SOCIETE_TELEPHONE", "SOCIETE_EMAIL", "SOCIETE_SITE_WEB",
            "SOCIETE_CONTACT", "SOCIETE_LEGAL", "SOCIETE_PIED_PAGE", "SOCIETE_LOGO",
            "CENTRE_NOM", "CENTRE_ADRESSE", "CENTRE_TELEPHONE", "CENTRE_EMAIL", "CENTRE_SITE_WEB",
            "CENTRE_CONTACT", "CENTRE_LIGNE");
    private static final Path REPORTS_DIR = Path.of("src/main/resources/reports");
    /**
     * Modèles imprimés pour un centre : ils portent l'en-tête et le pied de page société + centre.
     */
    private static final List<String> DOCUMENT_TEMPLATES = List.of(
            "attestation", "fiche_patient", "inventaire_stock", "liste_attestations", "liste_patients", "liste_pec",
            "ordonnance", "prise_en_charge", "bon_intervention", "planning_presence_infirmiers");

    private static JasperReport compile(Path source) throws Exception {
        try (InputStream is = Files.newInputStream(source)) {
            return JasperCompileManager.compileReport(is);
        }
    }

    @Test
    void allReportTemplatesCompile() throws Exception {
        assertTrue(Files.isDirectory(REPORTS_DIR), "Répertoire canonique introuvable: " + REPORTS_DIR);

        List<Path> files;
        try (var paths = Files.list(REPORTS_DIR)) {
            files = paths.filter(p -> p.getFileName().toString().endsWith(".jrxml")).sorted().collect(Collectors.toList());
        }
        assertTrue(files.size() >= DOCUMENT_TEMPLATES.size(), "Modèles manquants dans " + REPORTS_DIR);

        List<String> failures = new ArrayList<>();
        for (Path p : files) {
            try {
                compile(p);
            } catch (Exception e) {
                failures.add(p.getFileName() + " -> " + e.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            fail("Templates Jasper invalides:\n" + String.join("\n", failures));
        }
    }

    @Test
    void documentTemplatesDeclareTheIdentityParametersWithTheExpectedTypes() throws Exception {
        for (String name : DOCUMENT_TEMPLATES) {
            JasperReport report = compile(REPORTS_DIR.resolve(name + ".jrxml"));
            for (String expected : IDENTITY_PARAMETERS) {
                JRParameter parameter = java.util.Arrays.stream(report.getParameters())
                        .filter(x -> expected.equals(x.getName())).findFirst().orElse(null);
                assertNotNull(parameter, name + " doit déclarer le paramètre " + expected);
                Class<?> type = "SOCIETE_LOGO".equals(expected) ? InputStream.class : String.class;
                assertEquals(type, parameter.getValueClass(), name + " : type de " + expected);
            }
        }
    }

    @Test
    void legacyBackendReportsDirectoryMustStayEmpty() throws Exception {
        Path legacyDir = Path.of("..", "reports");
        if (!Files.isDirectory(legacyDir)) {
            return;
        }

        try (var paths = Files.list(legacyDir)) {
            List<Path> legacyReports = paths
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".jrxml") || name.endsWith(".jasper");
                    })
                    .collect(Collectors.toList());

            assertTrue(legacyReports.isEmpty(),
                    "Le répertoire legacy backend/reports ne doit plus contenir de rapports Jasper: " + legacyReports);
        }
    }
}
