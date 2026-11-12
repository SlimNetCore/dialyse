package com.hemodialyse.backend.characterization.documents.doc1;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Harnais du filet DOC1 (contexte Documents, ADR 0140) : rendu et papier en-tete. Point d'observation : HTTP
 * ({@code POST /api/v1/documents/print[/{modeleId}]}, MockMvc, securite reelle, JDBC reel sur H2 {@code test}) et services
 * ({@code DocumentIdentityProvider}, {@code ModeleDocumentPrinter}). Aucun mock du code caracterise.
 * <p>
 * PDF : on compare le TEXTE extrait (PDFBox) normalise, l'en-tete {@code %PDF}, le nombre de pages et le nombre d'images, jamais
 * les octets ; metadonnees volatiles (dates de creation, producteur, identifiant du fichier) ignorees. Excel : le contenu des
 * cellules (formate comme affiche). HTML : le gabarit enveloppe + le PDF integre decode.
 * <p>
 * Volatiles masques : date et heure du jour, "Imprime le ..." . Donnees fictives uniquement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class Doc1Support {

    static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "documents", "doc1");

    static final UUID SOCIETE = UUID.fromString("99998150-0000-0000-0000-000000000001");
    static final UUID SOCIETE_NUE = UUID.fromString("99998150-0000-0000-0000-000000000002");
    /** Centre complet : societe, logo, adresse, patients, attestation. */
    static final UUID CENTRE_D = UUID.fromString("99998150-0000-0000-0000-00000000000d");
    /** Centre sans societe, sans patient, sans logo. */
    static final UUID CENTRE_E = UUID.fromString("99998150-0000-0000-0000-00000000000e");
    /** Centre vide (aucun modele, aucune donnee) : ne sert qu'aux refus. */
    static final UUID CENTRE_F = UUID.fromString("99998150-0000-0000-0000-00000000000f");
    static final UUID PATIENT_1 = UUID.fromString("99998150-0000-0000-0000-000000000101");
    static final UUID PATIENT_2 = UUID.fromString("99998150-0000-0000-0000-000000000102");
    static final UUID PATIENT_E = UUID.fromString("99998150-0000-0000-0000-000000000103");
    static final UUID ATTESTATION_1 = UUID.fromString("99998150-0000-0000-0000-000000000201");
    static final UUID USER_ADMIN = UUID.fromString("99998150-0000-0000-0000-0000000000f1");
    static final UUID USER_MED = UUID.fromString("99998150-0000-0000-0000-0000000000f2");

    /** PNG 1x1 valide, fige (aucun encodeur dans le golden). */
    static final byte[] LOGO = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Autowired
    WebApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;

    final ObjectMapper mapper = JsonMapper.builder().build();
    MockMvc mvc;
    private String scenario = "?";

    @BeforeEach
    void setUpDoc1(TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        nettoyer();
        semerLeDecor();
    }

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM modele_document_version WHERE modele_id IN (SELECT id FROM modele_document WHERE center_id IN (?, ?, ?))",
                CENTRE_D, CENTRE_E, CENTRE_F);
        jdbc.update("DELETE FROM modele_document WHERE center_id IN (?, ?, ?)", CENTRE_D, CENTRE_E, CENTRE_F);
        jdbc.update("DELETE FROM attestation_droit WHERE center_id IN (?, ?, ?)", CENTRE_D, CENTRE_E, CENTRE_F);
        jdbc.update("DELETE FROM patients WHERE center_id IN (?, ?, ?)", CENTRE_D, CENTRE_E, CENTRE_F);
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-D1%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-D1%'");
    }

    // ------------------------------------------------------------------ decor

    private void semerLeDecor() {
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, nif, nis, rc, adresse, ville, wilaya, telephone, "
                        + "email, site_web, pied_page, logo, logo_content_type, actif, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOCIETE, "ZT-D1", "Societe Papier", "NIF-1", "NIS-2", "RC-3", "1 rue Alpha", "Alger", "Alger",
                "021 00 00 00", "soc@x.dz", "https://soc.x.dz", "Mention legale de test", LOGO, "image/png");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, adresse, ville, wilaya, telephone, email, site_web, actif) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,TRUE)",
                CENTRE_D, "ZT-D1-D", "Centre Papier", SOCIETE, "2 rue Beta", "Blida", "Tipaza", "025 11 11 11",
                "c@x.dz", "https://c.x.dz");
        // societe_id est obligatoire : le centre E a une societe sans aucune coordonnee ni logo
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOCIETE_NUE, "ZT-D1-N", "Societe Nue");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)",
                CENTRE_E, "ZT-D1-E", "Centre Nu", SOCIETE_NUE);
        patient(PATIENT_1, CENTRE_D, "ZT-P1", "DURAND", "Amel", "F", "1980-01-01", "ASS-ZT-1");
        patient(PATIENT_2, CENTRE_D, "ZT-P2", "BENALI", "Karim", "M", "1975-06-15", "ASS-ZT-2");
        patient(PATIENT_E, CENTRE_E, "ZT-PE", "MARTIN", "Zoe", "F", "1990-03-03", "ASS-ZT-E");
        jdbc.update("INSERT INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) VALUES (?,?,?,?,?)",
                ATTESTATION_1, PATIENT_1, CENTRE_D, java.sql.Date.valueOf("2026-01-01"), java.sql.Date.valueOf("2027-01-01"));
    }

    private void patient(UUID id, UUID centre, String code, String nom, String prenom, String sexe, String naissance, String assurance) {
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, "
                        + "numero_assurance, date_admission, type_patient, etat_patient, qualite_assure, sous_kt, "
                        + "epo_enabled, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,FALSE,FALSE,?)",
                id, centre, code, nom, prenom, sexe, java.sql.Date.valueOf(naissance), assurance,
                java.sql.Date.valueOf("2026-01-02"), "PERMANENT", "ACTIF", "ASSURE",
                OffsetDateTime.of(2026, 1, 2, 8, 0, 0, 0, ZoneOffset.UTC));
    }

    /** Modele actif d'un type pour un centre (identifiant stable derive du couple). */
    UUID modele(UUID centre, String type, String chemin, String format, boolean actif) {
        UUID id = UUID.nameUUIDFromBytes(("doc1-" + centre + "-" + type + "-" + chemin).getBytes(StandardCharsets.UTF_8));
        jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, "
                        + "description, active, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)", id, centre, type + (chemin.hashCode() % 7),
                "Modele " + type, type, chemin, format, "fictif", actif, OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        return id;
    }

    UUID modele(UUID centre, String type, String chemin) {
        return modele(centre, type, chemin, "PDF", true);
    }

    // ------------------------------------------------------------------ identites

    static RequestPostProcessor qui(UUID centre, UUID userId, String... roles) {
        return user(UserPrincipal.create(userId.toString(), centre == null ? null : centre.toString(), "u-" + userId, "",
                List.of(roles), true));
    }

    static RequestPostProcessor adminD() {
        return qui(CENTRE_D, USER_ADMIN, "ADMIN");
    }

    // ------------------------------------------------------------------ HTTP

    static final class Resp {
        final int status;
        final byte[] bytes;
        final MockHttpServletResponse http;

        Resp(MockHttpServletResponse r) {
            this.http = r;
            this.status = r.getStatus();
            this.bytes = r.getContentAsByteArray();
        }

        String type() {
            return http.getContentType() == null ? null : http.getContentType().split(";")[0];
        }

        String texte() {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    String corps(UUID centre, String type, String format, Map<String, String> params) {
        StringBuilder sb = new StringBuilder("{");
        if (centre != null) sb.append("\"centerId\":\"").append(centre).append("\",");
        if (type != null) sb.append("\"typeDocument\":\"").append(type).append("\",");
        if (format != null) sb.append("\"formatOverride\":\"").append(format).append("\",");
        sb.append("\"params\":{");
        if (params != null) {
            boolean premier = true;
            for (var e : params.entrySet()) {
                sb.append(premier ? "" : ",").append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');
                premier = false;
            }
        }
        return sb.append("}}").toString();
    }

    Resp imprimer(String json, RequestPostProcessor who) throws Exception {
        return envoyer("/api/v1/documents/print", json, who);
    }

    Resp imprimerParModele(UUID modeleId, String json, RequestPostProcessor who) throws Exception {
        return envoyer("/api/v1/documents/print/" + modeleId, json, who);
    }

    Resp envoyer(String chemin, String json, RequestPostProcessor who) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.post(chemin);
        if (json != null) b.contentType(MediaType.APPLICATION_JSON).content(json);
        if (who != null) b.with(who);
        return new Resp(mvc.perform(b).andReturn().getResponse());
    }

    // ------------------------------------------------------------------ observation des documents

    private static final Pattern IMPRIME_LE = Pattern.compile("Imprimé le \\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}(:\\d{2})?");

    String normaliser(String texte) {
        String t = texte.replaceAll("\\s+", " ").trim();
        // seul l'horodatage d'impression du pied de page est volatil ; les dates des patients sont figees (et gardees)
        t = IMPRIME_LE.matcher(t).replaceAll("Imprimé le <HORODATAGE>");
        return masquerIds(t);
    }

    String masquerIds(String t) {
        Matcher m = UUID_RE.matcher(t);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(alias(m.group())));
        m.appendTail(sb);
        return sb.toString();
    }

    static String alias(String uuid) {
        if (uuid.equals(CENTRE_D.toString())) return "<CENTRE_D>";
        if (uuid.equals(CENTRE_E.toString())) return "<CENTRE_E>";
        if (uuid.equals(CENTRE_F.toString())) return "<CENTRE_F>";
        if (uuid.equals(PATIENT_1.toString())) return "<PATIENT_1>";
        if (uuid.equals(PATIENT_2.toString())) return "<PATIENT_2>";
        if (uuid.equals(PATIENT_E.toString())) return "<PATIENT_E>";
        if (uuid.equals(ATTESTATION_1.toString())) return "<ATTESTATION_1>";
        return "<UUID>";
    }

    /** Resume stable d'un PDF : entete, pages, images, texte par page. */
    Map<String, Object> pdf(byte[] octets) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entete", new String(octets, 0, Math.min(5, octets.length), StandardCharsets.US_ASCII));
        try (PDDocument doc = PDDocument.load(octets)) {
            m.put("pages", doc.getNumberOfPages());
            int images = 0;
            for (PDPage page : doc.getPages()) {
                PDResources res = page.getResources();
                if (res == null) continue;
                for (COSName n : res.getXObjectNames()) if (res.isImageXObject(n)) images++;
            }
            m.put("images", images);
            List<String> textes = new ArrayList<>();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                PDFTextStripper s = new PDFTextStripper();
                s.setStartPage(p);
                s.setEndPage(p);
                textes.add(normaliser(s.getText(doc)));
            }
            m.put("texte par page", textes);
        }
        return m;
    }

    /** Contenu des cellules non vides de chaque feuille, tel qu'affiche. */
    Map<String, Object> xlsx(byte[] octets) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entete", new String(octets, 0, Math.min(2, octets.length), StandardCharsets.US_ASCII));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(octets))) {
            DataFormatter fmt = new DataFormatter();
            m.put("feuilles", wb.getNumberOfSheets());
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                List<String> lignes = new ArrayList<>();
                for (Row row : sheet) {
                    List<String> cellules = new ArrayList<>();
                    for (Cell c : row) {
                        String v = fmt.formatCellValue(c).trim();
                        if (!v.isEmpty()) cellules.add(c.getColumnIndex() + "=" + normaliser(v));
                    }
                    if (!cellules.isEmpty()) lignes.add((row.getRowNum()) + ": " + String.join(" | ", cellules));
                }
                m.put("feuille " + (i + 1), lignes);
            }
        }
        return m;
    }

    /** Resume d'une reponse de rendu, selon son type de contenu. */
    Map<String, Object> vue(Resp r) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status);
        m.put("contentType", r.type());
        String disposition = r.http.getHeader("Content-Disposition");
        if (disposition != null) m.put("contentDisposition", disposition);
        String t = r.type();
        if (t == null || r.bytes.length == 0) {
            m.put("octets", r.bytes.length);
        } else if (t.equals("application/pdf")) {
            m.putAll(pdf(r.bytes));
        } else if (t.contains("spreadsheetml")) {
            m.putAll(xlsx(r.bytes));
        } else if (t.equals("text/html")) {
            String html = r.texte();
            Matcher mm = Pattern.compile("data:application/pdf;base64,([A-Za-z0-9+/=]+)'").matcher(html);
            m.put("gabarit html", mm.find() ? html.replace(mm.group(1), "<PDF_BASE64>") : html);
            if (mm.find(0)) m.put("pdf integre", pdf(Base64.getDecoder().decode(mm.group(1))));
        } else if (t.startsWith("text/")) {
            m.put("texte", masquerIds(r.texte()));
        } else if (t.contains("json")) {
            m.put("json", masquerIds(r.texte()));
        } else {
            m.put("octets", r.bytes.length);
        }
        return m;
    }

    /** Statut, type et premiere ligne du corps d'erreur (texte brut ou JSON), sans les messages techniques instables. */
    Map<String, Object> refus(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status);
        m.put("contentType", r.type());
        if (r.bytes.length > 0) m.put("corps", masquerIds(r.texte()));
        return m;
    }

    void golden(String nom, Object valeur) {
        GoldenMaster.verify(GOLDENS.resolve(scenario), nom, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(valeur));
    }
}
