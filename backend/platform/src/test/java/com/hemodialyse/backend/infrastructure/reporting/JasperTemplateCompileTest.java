package com.hemodialyse.backend.infrastructure.reporting;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JRException;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class JasperTemplateCompileTest {

    @Test
    void fichePatientTemplateCompiles() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("reports/fiche_patient.jrxml")) {
            assertNotNull(is, "Template introuvable: reports/fiche_patient.jrxml");
            try {
                JasperReport report = JasperCompileManager.compileReport(is);
                assertNotNull(report);
            } catch (JRException e) {
                StringBuilder chain = new StringBuilder("JRException causes:\n");
                Throwable c = e;
                int i = 0;
                while (c != null && i < 12) {
                    chain.append("[").append(i).append("] ")
                        .append(c.getClass().getName())
                        .append(" -> ")
                        .append(c.getMessage())
                        .append("\n");
                    c = c.getCause();
                    i++;
                }
                throw new RuntimeException(chain.toString(), e);
            }
        }
    }

    @Test
    void allReportTemplatesCompile() throws Exception {
        Path reportsDir = Path.of("src/main/resources/reports");
        List<Path> files = Files.list(reportsDir)
                .filter(p -> p.getFileName().toString().endsWith(".jrxml"))
                .sorted()
                .collect(Collectors.toList());

        List<String> failures = new ArrayList<>();
        for (Path p : files) {
            try (InputStream is = Files.newInputStream(p)) {
                JasperReport report = JasperCompileManager.compileReport(is);
                assertNotNull(report);
            } catch (Exception e) {
                failures.add(p.getFileName() + " -> " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail("Templates Jasper invalides:\n" + String.join("\n", failures));
        }
    }
}


