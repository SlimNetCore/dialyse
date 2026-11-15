package com.hemodialyse.backend.characterization.documents.doc5g;

import com.hemodialyse.backend.characterization.dossiermedical.dm2.PrescriptionsCharacterizationSupport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * DOC5g : socle commun des filets de rendu de la liste des ouvertures de droit et de la liste des prises en charge (TOUT le texte du PDF,
 * cellules du tableur), observés par {@code POST /api/v1/documents/print} (par type) et {@code /print/{modeleId}} (par modèle). DONNÉES
 * FICTIVES ; date d'impression masquée.
 */
abstract class ListesDeCouverture5gSupport extends PrescriptionsCharacterizationSupport {

    protected static final UUID MODELE_A = UUID.fromString("99997800-0000-0000-0000-0000000f5a71");
    protected static final UUID MODELE_B = UUID.fromString("99997800-0000-0000-0000-0000000f5b71");

    @Override
    protected String dossier() {
        return "doc5g";
    }

    /** Le type de document imprimé (par exemple {@code LISTE_PEC}). */
    protected abstract String type();

    /** Le gabarit livré (par exemple {@code reports/liste_pec.jrxml}). */
    protected abstract String gabarit();

    @BeforeEach
    void modeles() {
        retirerLesModeles();
        for (UUID[] m : new UUID[][]{{MODELE_A, CENTRE_A}, {MODELE_B, CENTRE_B}}) {
            jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, "
                            + "description, active, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)", m[0], m[1], type() + "_5G", "Liste 5g",
                    type(), gabarit(), "PDF", "Liste fictive 5g", true, OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        }
    }

    @BeforeEach
    void viderLaCouverture() {
        retirerLaCouverture();
    }

    @AfterEach
    void retirerLesModeles() {
        jdbc.update("DELETE FROM modele_document WHERE id IN (?, ?)", MODELE_A, MODELE_B);
    }

    @AfterEach
    void retirerLaCouverture() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM attestation_droit WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", centre);
        }
    }

    protected UUID patient(UUID centre, String alias, String code, String nom, String prenom, String assurance) {
        UUID id = idStable("patient5g", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id, centre, code, nom, prenom, "M", java.sql.Date.valueOf(fixe(10)), assurance, "NON_VACANCIER", "ACTIF", CREATION_FIXE);
        return id;
    }

    protected String corps(UUID centre, String format) {
        return "{\"centerId\":\"" + centre + "\",\"typeDocument\":\"" + type() + "\","
                + (format == null ? "" : "\"formatOverride\":\"" + format + "\",") + "\"params\":{}}";
    }

    protected MockHttpServletResponse parType(UUID centre, String format) throws Exception {
        return mvc.perform(post("/api/v1/documents/print").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(corps(centre, format)).with(as(centre, "MEDECIN"))).andReturn().getResponse();
    }

    protected MockHttpServletResponse parModele(UUID modele, UUID centre) throws Exception {
        return mvc.perform(post("/api/v1/documents/print/" + modele).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"centerId\":\"" + centre + "\",\"params\":{}}").with(as(centre, "MEDECIN"))).andReturn().getResponse();
    }

    protected Map<String, Object> resume(MockHttpServletResponse r) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.getStatus());
        String type = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        m.put("contentType", type);
        byte[] octets = r.getContentAsByteArray();
        if ("application/pdf".equals(type)) {
            try (PDDocument doc = PDDocument.load(octets)) {
                m.put("pages", doc.getNumberOfPages());
                List<String> lignes = new ArrayList<>(new PDFTextStripper().getText(doc).replace("\r\n", "\n").lines().toList());
                lignes.replaceAll(l -> joursEnFrancais(l.replaceAll("Imprimé le \\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}", "Imprimé le <HORODATAGE_IMPRESSION>")));
                m.put("texte", lignes);
            }
        } else if (type != null && type.contains("spreadsheetml")) {
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
                            if (!v.isEmpty()) cellules.add(c.getColumnIndex() + "=" + joursEnFrancais(v.replaceAll(
                                    "Imprimé le \\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}(:\\d{2})?", "Imprimé le <HORODATAGE_IMPRESSION>")));
                        }
                        if (!cellules.isEmpty()) lignes.add(row.getRowNum() + ": " + String.join(" | ", cellules));
                    }
                    m.put("feuille " + (i + 1), lignes);
                }
            }
        } else if (type != null && type.startsWith("text/")) {
            m.put("texte", new String(octets, StandardCharsets.UTF_8));
        } else {
            m.put("octets", octets.length > 0);
        }
        return m;
    }
}
