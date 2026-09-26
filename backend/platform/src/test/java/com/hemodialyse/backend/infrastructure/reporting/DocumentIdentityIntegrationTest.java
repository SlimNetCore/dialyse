package com.hemodialyse.backend.infrastructure.reporting;

import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperPrintManager;
import net.sf.jasperreports.engine.JasperReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L'identité société + centre est calculée côté serveur et imprimée dans l'en-tête et le pied de page des
 * documents ; le client ne peut pas la falsifier.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class DocumentIdentityIntegrationTest {

    private static final UUID SOCIETE = UUID.fromString("99995000-0000-0000-0000-000000000001");
    private static final UUID CENTRE = UUID.fromString("99995000-0000-0000-0000-000000000002");
    private static final UUID PATIENT = UUID.fromString("99995000-0000-0000-0000-000000000003");
    private static final UUID ATTESTATION = UUID.fromString("99995000-0000-0000-0000-000000000004");

    @Autowired
    private DocumentIdentityProvider identity;
    @Autowired
    private JasperReportService jasper;
    @Autowired
    private JdbcTemplate jdbc;

    private static byte[] logoPng() throws Exception {
        BufferedImage img = new BufferedImage(120, 90, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(20, 118, 110));
        g.fillRect(0, 0, 120, 90);
        g.setColor(Color.WHITE);
        g.fillOval(30, 20, 60, 50);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @BeforeEach
    void seed() throws Exception {
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, nif, nis, rc, adresse, ville, wilaya, telephone, "
                        + "email, site_web, pied_page, logo, logo_content_type, actif, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOCIETE, "ZT-ID", "Société d'identité", "NIF-1", "NIS-2", "RC-3", "1 rue A", "Alger", "Alger",
                "021 00 00 00", "soc@x.dz", "https://soc.x.dz", "Mention légale de test", logoPng(), "image/png");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, adresse, ville, wilaya, telephone, email, site_web, actif) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,TRUE)",
                CENTRE, "ZT-ID-C1", "Centre d'identité", SOCIETE, "2 rue B", "Blida", "Blida", "025 11 11 11",
                "c@x.dz", "https://c.x.dz");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, "
                        + "numero_assurance, date_admission, type_patient, etat_patient, qualite_assure, sous_kt, "
                        + "epo_enabled, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,FALSE,FALSE,CURRENT_TIMESTAMP)",
                PATIENT, CENTRE, "ZT-P1", "DURAND", "Amel", "F", java.sql.Date.valueOf("1980-01-01"), "ASS-ZT-1",
                java.sql.Date.valueOf("2026-01-02"), "PERMANENT", "ACTIF", "ASSURE");
        jdbc.update("INSERT INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) VALUES (?,?,?,?,?)",
                ATTESTATION, PATIENT, CENTRE, java.sql.Date.valueOf("2026-01-01"), java.sql.Date.valueOf("2027-01-01"));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM attestation_droit WHERE id = ?", ATTESTATION);
        jdbc.update("DELETE FROM patients WHERE id = ?", PATIENT);
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-ID%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-ID%'");
    }

    @Test
    void the_identity_of_the_centre_and_its_societe_is_resolved_from_the_database() {
        Map<String, Object> p = identity.paramsFor(CENTRE);
        assertEquals("Société d'identité", p.get("SOCIETE_NOM"));
        assertEquals("NIF : NIF-1 · NIS : NIS-2 · RC : RC-3", p.get("SOCIETE_LEGAL"));
        assertEquals("Mention légale de test", p.get("SOCIETE_PIED_PAGE"));
        assertEquals("Centre d'identité", p.get("CENTRE_NOM"));
        assertEquals("2 rue B, Blida", p.get("CENTRE_ADRESSE"));
        assertEquals("Tél : 025 11 11 11 · Email : c@x.dz · Web : https://c.x.dz", p.get("CENTRE_CONTACT"));
        assertTrue(((String) p.get("CENTRE_LIGNE")).startsWith("Centre d'identité · 2 rue B, Blida"));
        assertInstanceOf(InputStream.class, p.get("SOCIETE_LOGO"), "le logo est fourni sous forme de flux");
    }

    @Test
    void an_unknown_centre_yields_empty_strings_and_no_logo() {
        Map<String, Object> p = identity.paramsFor(UUID.randomUUID());
        assertEquals("", p.get("SOCIETE_NOM"));
        assertEquals("", p.get("CENTRE_LIGNE"));
        assertNull(p.get("SOCIETE_LOGO"));
        assertFalse(identity.paramsFor(null).isEmpty());
    }

    @Test
    void html_documents_get_an_escaped_identity_header_and_footer() {
        jdbc.update("UPDATE societes SET raison_sociale = ? WHERE id = ?", "<script>alert(1)</script> & Cie", SOCIETE);
        String html = identity.decorateHtml("<html><head></head><body><h1>Doc</h1></body></html>", CENTRE);
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; Cie"), "le nom est échappé : " + html);
        assertFalse(html.contains("<script>"), "aucun HTML injecté");
        assertTrue(html.contains("data:image/png;base64,"), "logo intégré en data URI");
        assertTrue(html.indexOf("Centre d&#39;identité") < html.indexOf("<h1>Doc</h1>"), "en-tête avant le contenu");
        assertTrue(html.lastIndexOf("Mention légale de test") > html.indexOf("<h1>Doc</h1>"), "pied de page après le contenu");
    }

    @Test
    void reserved_identity_parameters_sent_by_the_client_are_ignored() {
        Map<String, String> safe = DocumentIdentityProvider.stripReservedParams(Map.of(
                "patientId", "p1", "SOCIETE_NOM", "Usurpation", "centre_nom", "Faux", "SOCIETE_LOGO", "x"));
        assertEquals(Map.of("patientId", "p1"), safe);
    }

    @Test
    void the_bundled_documents_print_the_identity_in_the_header_and_footer() throws Exception {
        // Un document réel (liste des attestations du centre) rendu avec l'identité du centre.
        {
            Map<String, Object> params = new HashMap<>();
            params.put("CENTER_ID", CENTRE.toString());
            params.putAll(identity.paramsFor(CENTRE));

            JasperReport report = jasper.compileReport("reports/liste_attestations.jrxml");
            JasperPrint print = jasper.fillReport(report, params);
            assertFalse(print.getPages().isEmpty(), "l'attestation du test doit produire une page");

            List<String> texts = print.getPages().get(0).getElements().stream()
                    .filter(e -> e instanceof JRPrintText)
                    .map(e -> ((JRPrintText) e).getFullText()).collect(Collectors.toList());
            assertTrue(texts.contains("Société d'identité"), "raison sociale en en-tête : " + texts);
            assertTrue(texts.stream().anyMatch(t -> t.contains("Mention légale de test")), "pied de page société");
            assertTrue(texts.stream().anyMatch(t -> t.contains("Centre d'identité")), "nom du centre");
            boolean hasImage = print.getPages().get(0).getElements().stream()
                    .anyMatch(e -> e.getClass().getSimpleName().contains("Image"));
            assertTrue(hasImage, "le logo est imprimé");

            // Aperçu visuel (contrôle manuel) : target/identity-preview.png
            BufferedImage page = (BufferedImage) JasperPrintManager.printPageToImage(print, 0, 1.5f);
            assertNotNull(page);
            ImageIO.write(page, "png", new File("target/identity-preview.png"));
        }
    }

    @Test
    void portrait_documents_keep_their_content_below_the_identity_header() throws Exception {
        {
            String patientId = PATIENT.toString();
            for (String template : List.of("attestation", "fiche_patient")) {
                Map<String, Object> params = new HashMap<>();
                params.put("CENTER_ID", CENTRE.toString());
                params.put("patientId", patientId);
                params.put("attestationId", "");
                params.putAll(identity.paramsFor(CENTRE));

                JasperPrint print = jasper.fillReport(jasper.compileReport("reports/" + template + ".jrxml"), params);
                assertFalse(print.getPages().isEmpty(), template + " : aucune page générée");
                List<String> texts = print.getPages().get(0).getElements().stream()
                        .filter(e -> e instanceof JRPrintText).map(e -> ((JRPrintText) e).getFullText())
                        .collect(Collectors.toList());
                assertTrue(texts.contains("Société d'identité"), template + " : en-tête société");
                assertTrue(texts.stream().anyMatch(t -> t.contains("Mention légale de test")), template + " : pied de page");
                ImageIO.write((BufferedImage) JasperPrintManager.printPageToImage(print, 0, 1.2f), "png",
                        new File("target/identity-preview-" + template + ".png"));
            }
        }
    }
}
