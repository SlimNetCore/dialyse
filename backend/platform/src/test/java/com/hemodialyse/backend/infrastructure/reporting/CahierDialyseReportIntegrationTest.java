package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cahier de dialyse imprimable sur base réelle : une page par séance réalisée (la plus récente d'abord), avec toutes
 * les informations du cahier — paramédical, consommables valorisés, forfait, dossier médical, prescription en vigueur
 * à la date de la séance, volet médical — sans jamais lire les données d'un autre centre ni d'une séance non réalisée.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class CahierDialyseReportIntegrationTest {

    private static final UUID SOC = UUID.fromString("99997500-0000-0000-0000-000000000001");
    private static final UUID C1 = UUID.fromString("99997500-0000-0000-0000-0000000000c1");
    private static final UUID C2 = UUID.fromString("99997500-0000-0000-0000-0000000000c2");
    private static final LocalDate J1 = LocalDate.of(2026, 9, 10);
    private static final LocalDate J2 = LocalDate.of(2026, 9, 12);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private CahierDialyseReportService report;

    private UUID patient;
    private UUID autrePatient;
    private UUID seance1;
    private UUID seance2;

    @BeforeEach
    void seed() {
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC, "ZT-CD1", "Société CD1");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", C1, "ZT-CD1-A", "C1", SOC);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", C2, "ZT-CD1-B", "C2", SOC);
        patient = patient(C1, "Benali", "Karim");
        autrePatient = patient(C2, "Etranger", "Paul");

        UUID forfait = UUID.randomUUID();
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?,?,?,?,?)", forfait, C1, "FH1",
                "Forfait hémodialyse", new BigDecimal("12000.00"));
        jdbc.update("INSERT INTO prise_en_charge (id, patient_id, center_id, date_debut_effectif, forfait_effectif_id, "
                        + "statut, created_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)", UUID.randomUUID(), patient, C1,
                Date.valueOf("2026-01-01"), forfait, "VALIDEE");

        seance1 = seance(C1, patient, J1, "VALIDEE");
        seance2 = seance(C1, patient, J2, "FACTUREE");
        seance(C1, patient, J2.plusDays(2), "CREE");            // non réalisée : jamais imprimée
        seance(C2, autrePatient, J1, "VALIDEE");                  // autre centre

        jdbc.update("INSERT INTO volet_paramedical (id, seance_id, center_id, poids_avant_kg, poids_apres_kg, ta_avant, "
                        + "ta_apres, duree_minutes, debit_sang_ml_min, ultrafiltration_ml, anticoagulant, type_dialysat, incidents) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), seance2, C1, new BigDecimal("72.5"),
                new BigDecimal("70.0"), "13/8", "12/7", 240, 300, new BigDecimal("2500"), "Héparine", "Bicarbonate",
                "Crampes en fin de séance");
        jdbc.update("INSERT INTO volet_medical (id, seance_id, center_id, prescription, tolerance_seance, examen_clinique, "
                        + "resultats_biologiques, ajustements_therapeutiques, conclusion_medicale) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), seance2, C1, "Hémodialyse 4h", "Bonne tolérance", "RAS", "Hb 10,5 g/dL",
                "Augmenter l'EPO", "Séance satisfaisante");

        UUID epo = article(C1, "EPO01", "Époétine alfa");
        UUID filtre = article(C1, "FIL01", "Dialyseur FX80");
        mouvement(C1, filtre, seance2, new BigDecimal("1"), new BigDecimal("1500.00"));
        mouvement(C1, epo, seance2, new BigDecimal("2"), new BigDecimal("250.50"));

        jdbc.update("INSERT INTO dossier_medical_patient (id, patient_id, center_id, nephropathie_initiale, "
                        + "date_mise_en_dialyse, hepatite_b_statut, hepatite_c_statut, observation_globale, conclusion_medicale) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), patient, C1, "Néphropathie diabétique",
                Date.valueOf("2024-03-05"), "VACCINE", "NEGATIF", "Patient stable", "Poursuivre le traitement");
        // deux prescriptions : seule la plus récente antérieure ou égale à la séance est en vigueur
        prescription(patient, LocalDate.of(2026, 1, 1), new BigDecimal("68.00"), epo, 4000);
        prescription(patient, LocalDate.of(2026, 9, 11), new BigDecimal("69.50"), epo, 6000);
        prescription(patient, LocalDate.of(2026, 10, 1), new BigDecimal("71.00"), epo, 8000);
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("stock_movements", "volet_medical", "volet_paramedical", "prescriptions_medicales",
                "dossier_medical_patient", "seances", "prise_en_charge", "forfait", "articles", "patients")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", C1, C2);
        }
        jdbc.update("DELETE FROM modele_document WHERE center_id IN (?, ?)", C1, C2);
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-CD1%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-CD1%'");
    }

    private UUID patient(UUID centre, String nom, String prenom) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, numero_assurance, "
                        + "date_admission, type_patient, etat_patient, qualite_assure, sous_kt, epo_enabled, groupe_sanguin, "
                        + "created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,?,CURRENT_TIMESTAMP)",
                id, centre, "PAT-CD" + id.toString().substring(0, 4), nom, prenom, "M", Date.valueOf("1970-05-05"),
                "ASS-" + id.toString().substring(0, 8), Date.valueOf("2026-01-02"), "NON_VACANCIER", "PERMANENT",
                "ASSURE", false, "A+");
        return id;
    }

    private UUID seance(UUID centre, UUID patient, LocalDate date, String statut) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) "
                + "VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)", id, patient, centre, Date.valueOf(date), statut);
        return id;
    }

    private UUID article(UUID centre, String code, String libelle) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, active, gere_par_lot, seuil_alerte, "
                + "stock_quantity) VALUES (?,?,?,?,?,TRUE,FALSE,0,0)", id, centre, code, libelle, "boîte");
        return id;
    }

    private void mouvement(UUID centre, UUID article, UUID seance, BigDecimal quantite, BigDecimal prix) {
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, seance_id, mouvement_type, quantite, "
                        + "prix_unitaire, created_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", UUID.randomUUID(), centre,
                article, seance, "SORTIE", quantite, prix);
    }

    private void prescription(UUID patient, LocalDate date, BigDecimal poidsSec, UUID epo, int doseUi) {
        jdbc.update("INSERT INTO prescriptions_medicales (id, patient_id, center_id, date_prescription, poids_sec_cible_kg, "
                        + "epo_article_id, epo_dose_ui, epo_voie, epo_frequence_valeur, epo_frequence_unite, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", UUID.randomUUID(), patient, C1, Date.valueOf(date),
                poidsSec, epo, doseUi, "SC", 1, "SEMAINE");
    }

    private List<String> textes(UUID centre, UUID patientId) throws Exception {
        JasperReport compiled;
        try (InputStream is = Files.newInputStream(Path.of("src/main/resources/reports/cahier_dialyse.jrxml"))) {
            compiled = JasperCompileManager.compileReport(is);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("CENTER_ID", centre.toString());
        params.put("PATIENT_ID", patientId.toString());
        params.put("DATE_DEBUT", Date.valueOf(CahierDialyseReportService.DEBUT_PAR_DEFAUT));
        params.put("DATE_FIN", Date.valueOf(CahierDialyseReportService.FIN_PAR_DEFAUT));
        try (Connection connection = dataSource.getConnection()) {
            JasperPrint print = JasperFillManager.fillReport(compiled, params, connection);
            return print.getPages().stream().map(JRPrintPage::getElements).flatMap(List::stream)
                    .filter((JRPrintElement e) -> e instanceof JRPrintText).map(e -> ((JRPrintText) e).getFullText())
                    .collect(Collectors.toList());
        }
    }

    private List<Integer> pagesParSeance(UUID centre, UUID patientId) throws Exception {
        JasperReport compiled;
        try (InputStream is = Files.newInputStream(Path.of("src/main/resources/reports/cahier_dialyse.jrxml"))) {
            compiled = JasperCompileManager.compileReport(is);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("CENTER_ID", centre.toString());
        params.put("PATIENT_ID", patientId.toString());
        params.put("DATE_DEBUT", Date.valueOf(CahierDialyseReportService.DEBUT_PAR_DEFAUT));
        params.put("DATE_FIN", Date.valueOf(CahierDialyseReportService.FIN_PAR_DEFAUT));
        try (Connection connection = dataSource.getConnection()) {
            JasperPrint print = JasperFillManager.fillReport(compiled, params, connection);
            return List.of(print.getPages().size());
        }
    }

    @Test
    void prints_one_page_per_performed_session_most_recent_first_and_never_a_pending_one() throws Exception {
        List<String> textes = textes(C1, patient);

        assertThat(pagesParSeance(C1, patient)).containsExactly(2);
        assertThat(textes.stream().filter(t -> t.contains("Séance d'hémodialyse du")).toList()).containsExactly(
                "  Page 1 du cahier — Séance d'hémodialyse du 12/09/2026",
                "  Page 2 du cahier — Séance d'hémodialyse du 10/09/2026");
        assertThat(textes).contains("Statut : Facturée  ", "Statut : Validée  ");
        assertThat(textes.stream().anyMatch(t -> t.contains("14/09/2026"))).as("séance CREE exclue").isFalse();
    }

    @Test
    void prints_every_information_of_the_notebook_page() throws Exception {
        List<String> textes = textes(C1, patient);

        // forfait
        assertThat(textes).contains("Forfait : FH1 — Forfait hémodialyse");
        // volet paramédical (10 informations)
        assertThat(textes).contains("Poids avant (kg) : 72.5", "TA avant : 13/8", "TA après : 12/7", "Durée (min) : 240",
                "Débit sang (mL/min) : 300", "Type de dialysat : Bicarbonate", "Anticoagulant : Héparine",
                "Incidents / remarques : Crampes en fin de séance");
        assertThat(textes.stream().anyMatch(t -> t.equals("Poids après (kg) : 70"))).isTrue();
        assertThat(textes.stream().anyMatch(t -> t.equals("Ultrafiltration (mL) : 2500"))).isTrue();
        // séance sans volet paramédical : tirets, jamais un champ manquant
        assertThat(textes).contains("Poids avant (kg) : -", "Incidents / remarques : -");
        // consommables valorisés
        assertThat(textes).contains("  FIL01 — Dialyseur FX80", "  EPO01 — Époétine alfa", "Aucun article sorti pour cette séance.");
        // montants (le séparateur de milliers dépend de la locale : on compare les chiffres)
        List<String> chiffres = textes.stream().map(t -> t.replaceAll("[^0-9]", "")).toList();
        assertThat(chiffres).contains("150000", "50100", "200100");
        // dossier médical
        assertThat(textes).contains("Date de mise en dialyse : 05/03/2024", "Statut hépatite B : Vacciné",
                "Statut hépatite C : Négatif", "Néphropathie initiale : Néphropathie diabétique",
                "Observation globale : Patient stable", "Conclusion du médecin : Poursuivre le traitement");
        // volet médical de la séance
        assertThat(textes).contains("Prescription : Hémodialyse 4h", "Tolérance de séance : Bonne tolérance",
                "Examen clinique : RAS", "Résultats biologiques : Hb 10,5 g/dL", "Ajustements thérapeutiques : Augmenter l'EPO",
                "Conclusion médicale : Séance satisfaisante");
    }

    @Test
    void uses_the_prescription_in_force_at_each_session_date() throws Exception {
        List<String> textes = textes(C1, patient);

        // séance du 10/09 : prescription du 01/01 ; séance du 12/09 : celle du 11/09 (pas celle du 01/10, postérieure)
        assertThat(textes).contains("Poids sec : 69.5 kg", "Poids sec : 68 kg");
        assertThat(textes).contains("EPO : Époétine alfa — 6000 UI SC 1/semaine", "EPO : Époétine alfa — 4000 UI SC 1/semaine");
        assertThat(textes).doesNotContain("Poids sec : 71 kg");
    }

    @Test
    void never_reads_another_center() throws Exception {
        assertThat(textes(C1, autrePatient)).isEmpty();
        assertThat(textes(C2, autrePatient)).isNotEmpty();
        assertThat(textes(C2, autrePatient)).noneMatch(t -> t.contains("FH1") || t.contains("EPO01"));
    }

    @Test
    void the_document_is_generated_through_the_center_document_model() {
        ModeleDocumentPrinter.Document doc = report.imprimer(C1, patient, null, null, ZoneOffset.UTC, "admin");

        assertThat(doc.format()).isNotBlank();
        assertThat(new String(doc.content(), 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    @Test
    void the_period_limits_the_printed_sessions_and_empty_or_foreign_requests_are_refused() {
        ModeleDocumentPrinter.Document seule = report.imprimer(C1, patient, J2, J2, ZoneOffset.UTC, "admin");
        assertThat(new String(seule.content(), 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");

        assertEquals("CAHIER_VIDE", assertThrows(BusinessException.class,
                () -> report.imprimer(C1, patient, J2.plusDays(30), null, ZoneOffset.UTC, "admin")).getCode());
        assertEquals("CAHIER_PERIODE_INVALIDE", assertThrows(BusinessException.class,
                () -> report.imprimer(C1, patient, J2, J1, ZoneOffset.UTC, "admin")).getCode());
        assertEquals("PATIENT_INTROUVABLE", assertThrows(BusinessException.class,
                () -> report.imprimer(C1, autrePatient, null, null, ZoneOffset.UTC, "admin")).getCode());
    }
}
