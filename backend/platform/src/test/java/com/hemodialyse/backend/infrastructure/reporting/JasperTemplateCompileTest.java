package com.hemodialyse.backend.infrastructure.reporting;

import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.util.JRLoader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class JasperTemplateCompileTest {

    @Test
    void fichePatientTemplateLoads() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("reports/fiche_patient.jasper")) {
            assertNotNull(is, "Template compilé introuvable: reports/fiche_patient.jasper");
            JasperReport report = (JasperReport) JRLoader.loadObject(is);
            assertNotNull(report);
        }
    }

    @Test
    void allReportTemplatesLoad() throws Exception {
        Path reportsDir = Path.of("src/main/resources/reports");
        assertTrue(Files.isDirectory(reportsDir), "Répertoire canonique introuvable: src/main/resources/reports");

        List<Path> files = Files.list(reportsDir)
                .filter(p -> p.getFileName().toString().endsWith(".jasper"))
                .sorted()
                .collect(Collectors.toList());

        List<String> failures = new ArrayList<>();
        for (Path p : files) {
            try (InputStream is = Files.newInputStream(p)) {
                JasperReport report = (JasperReport) JRLoader.loadObject(is);
                assertNotNull(report);
            } catch (Exception e) {
                failures.add(p.getFileName() + " -> " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail("Templates Jasper invalides:\n" + String.join("\n", failures));
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


