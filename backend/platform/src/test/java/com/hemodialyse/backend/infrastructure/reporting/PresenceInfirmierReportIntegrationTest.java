package com.hemodialyse.backend.infrastructure.reporting;

import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Planning de présence imprimable sur base réelle : la requête du modèle lit le roulement, les absences et les
 * remplacements du centre (jamais ceux d'un autre centre) et le document se génère via le modèle de document du centre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PresenceInfirmierReportIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99998100-0000-0000-0000-00000000000a");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99998100-0000-0000-0000-00000000000b");
    private static final UUID SALLE = UUID.fromString("99998100-0000-0000-0000-0000000000a1");
    private static final UUID SALLE_AUTRE = UUID.fromString("99998100-0000-0000-0000-0000000000b1");
    private static final UUID MATIN = UUID.fromString("99998100-0000-0000-0000-0000000000c1");
    private static final UUID MATIN_AUTRE = UUID.fromString("99998100-0000-0000-0000-0000000000c2");
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 9, 27);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PresenceInfirmierReportService report;

    @BeforeEach
    void seed() {
        cleanup();
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'PR-S1', 'Salle test')", SALLE, CENTRE);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'PR-S2', 'Salle autre')", SALLE_AUTRE, AUTRE_CENTRE);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'PR1', 'Matin')", MATIN, CENTRE);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'PR2', 'Matin autre')", MATIN_AUTRE, AUTRE_CENTRE);

        UUID amrani = infirmier(CENTRE, "PR1", "Amrani", "Sara");
        UUID benali = infirmier(CENTRE, "PR2", "Benali", null);
        UUID renfort = infirmier(CENTRE, "PR3", "Cherif", null);
        UUID etranger = infirmier(AUTRE_CENTRE, "PR1", "Etranger", null);
        affectation(CENTRE, amrani, SALLE, MATIN, "LUNDI,MERCREDI");
        affectation(CENTRE, benali, SALLE, MATIN, "LUNDI");
        affectation(AUTRE_CENTRE, etranger, SALLE_AUTRE, MATIN_AUTRE, "LUNDI");
        jdbc.update("INSERT INTO infirmier_absence (id, center_id, infirmier_id, date_debut, date_fin, type) "
                        + "VALUES (?, ?, ?, ?, ?, 'CONGE')", UUID.randomUUID(), CENTRE, benali, Date.valueOf(DIMANCHE.plusDays(1)),
                Date.valueOf(DIMANCHE.plusDays(1)));
        jdbc.update("INSERT INTO infirmier_remplacement (id, center_id, date_jour, salle_id, creneau_id, infirmier_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), CENTRE, Date.valueOf(DIMANCHE.plusDays(1)), SALLE, MATIN, renfort);
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("infirmier_remplacement", "infirmier_absence", "infirmier_affectation", "infirmier",
                "position_creneau", "salle")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        }
    }

    private UUID infirmier(UUID centre, String matricule, String nom, String prenom) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO infirmier (id, center_id, matricule, nom, prenom, qualification, habilite_isolement, actif) "
                + "VALUES (?, ?, ?, ?, ?, 'INFIRMIER', FALSE, TRUE)", id, centre, matricule, nom, prenom);
        return id;
    }

    private void affectation(UUID centre, UUID infirmier, UUID salle, UUID creneau, String jours) {
        jdbc.update("INSERT INTO infirmier_affectation (id, center_id, infirmier_id, salle_id, creneau_id, jours) "
                + "VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), centre, infirmier, salle, creneau, jours);
    }

    private List<String> lignes(UUID centre) throws Exception {
        JasperReport compiled;
        try (InputStream is = Files.newInputStream(Path.of("src/main/resources/reports/planning_presence_infirmiers.jrxml"))) {
            compiled = JasperCompileManager.compileReport(is);
        }
        Map<String, Object> params = new HashMap<>(PresenceInfirmierReportService.params(DIMANCHE));
        params.put("CENTER_ID", centre.toString());
        try (Connection connection = dataSource.getConnection()) {
            JasperPrint print = JasperFillManager.fillReport(compiled, params, connection);
            return print.getPages().stream().map(JRPrintPage::getElements).flatMap(List::stream)
                    .filter(e -> e instanceof JRPrintText).map(e -> ((JRPrintText) e).getFullText())
                    .collect(Collectors.toList());
        }
    }

    @Test
    void the_report_query_lists_planned_absent_and_replacement_nurses_of_the_center_only() throws Exception {
        List<String> textes = lignes(CENTRE);

        assertThat(textes).contains("Sara Amrani", "Benali", "Cherif", "Prévu", "Absent", "Remplaçant");
        assertThat(textes).doesNotContain("Etranger");
        assertThat(textes.stream().filter("Sara Amrani"::equals).count())
                .as("Amrani travaille le lundi et le mercredi").isEqualTo(2);
        assertThat(textes).contains("Semaine du 27/09/2026 au 03/10/2026");
    }

    @Test
    void another_center_only_sees_its_own_nurses() throws Exception {
        List<String> textes = lignes(AUTRE_CENTRE);

        assertThat(textes).contains("Etranger").doesNotContain("Sara Amrani", "Benali", "Cherif");
    }

    @Test
    void the_document_is_generated_through_the_center_document_model() {
        ModeleDocumentPrinter.Document doc = report.imprimer(CENTRE, DIMANCHE.plusDays(3));

        assertThat(doc.format()).isNotBlank();
        assertThat(new String(doc.content(), 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    @Test
    void the_parameters_cover_the_seven_days_of_the_week() {
        Map<String, Object> p = PresenceInfirmierReportService.params(DIMANCHE);

        assertThat(p.get("DATE_0")).isEqualTo(Date.valueOf(DIMANCHE));
        assertThat(p.get("DATE_6")).isEqualTo(Date.valueOf(DIMANCHE.plusDays(6)));
        assertThat(p.get("SEMAINE_LABEL")).isEqualTo("Semaine du 27/09/2026 au 03/10/2026");
    }
}
