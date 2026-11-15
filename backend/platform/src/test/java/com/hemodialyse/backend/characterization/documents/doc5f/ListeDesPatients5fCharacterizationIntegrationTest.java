package com.hemodialyse.backend.characterization.documents.doc5f;

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
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * DOC5f : filet de rendu de la liste des patients, TOUT le texte du PDF (et le contenu des cellules du tableur), avant la conversion du
 * gabarit {@code liste_patients.jrxml} de son SQL embarqué (une seule table, {@code patients} de Patient et couverture, triée par nom puis
 * prénom) vers un jeu de données fourni par Patient et couverture. Observé par {@code POST /api/v1/documents/print} (par type) et
 * {@code /print/{modeleId}} (par modèle) : une liste riche (plusieurs pages, noms accentués, en minuscules ou en majuscules, deux patients
 * de même nom, champs absents ou vides, un patient d'un autre centre) et un centre sans patient. Le texte doit rester identique après la
 * conversion. Date d'impression masquée. DONNÉES FICTIVES.
 */
class ListeDesPatients5fCharacterizationIntegrationTest extends PrescriptionsCharacterizationSupport {

    private static final UUID MODELE_A = UUID.fromString("99997800-0000-0000-0000-0000000f5a01");
    private static final UUID MODELE_B = UUID.fromString("99997800-0000-0000-0000-0000000f5b01");

    @Override
    protected String dossier() {
        return "doc5f";
    }

    @BeforeEach
    void modeles() {
        retirerLesModeles();
        for (UUID[] m : new UUID[][]{{MODELE_A, CENTRE_A}, {MODELE_B, CENTRE_B}}) {
            jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, "
                            + "description, active, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)", m[0], m[1], "LISTE_PATIENTS_5F", "Liste des patients",
                    "LISTE_PATIENTS", "reports/liste_patients.jrxml", "PDF", "Liste fictive 5f", true,
                    OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        }
    }

    @AfterEach
    void retirerLesModeles() {
        jdbc.update("DELETE FROM modele_document WHERE id IN (?, ?)", MODELE_A, MODELE_B);
    }

    private void patient(UUID centre, String alias, String code, String nom, String prenom, String sexe, String groupe, Integer naissance,
                         int admission, String assurance, String mobile, String etat, String adresse) {
        UUID id = idStable("patient5f", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, groupe_sanguin, date_naissance, date_admission, "
                        + "numero_assurance, tel_mobile, etat_patient, adresse, type_patient, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, centre, code, nom, prenom, sexe, groupe, naissance == null ? null : java.sql.Date.valueOf(fixe(naissance)),
                java.sql.Date.valueOf(fixe(admission)), assurance, mobile, etat, adresse, "NON_VACANCIER", CREATION_FIXE);
    }

    private static String corps(UUID centre, String format) {
        return "{\"centerId\":\"" + centre + "\",\"typeDocument\":\"LISTE_PATIENTS\","
                + (format == null ? "" : "\"formatOverride\":\"" + format + "\",") + "\"params\":{}}";
    }

    private MockHttpServletResponse parType(UUID centre, String format) throws Exception {
        return mvc.perform(post("/api/v1/documents/print").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(corps(centre, format)).with(as(centre, "MEDECIN"))).andReturn().getResponse();
    }

    private MockHttpServletResponse parModele(UUID modele, UUID centre) throws Exception {
        return mvc.perform(post("/api/v1/documents/print/" + modele).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"centerId\":\"" + centre + "\",\"params\":{}}").with(as(centre, "MEDECIN"))).andReturn().getResponse();
    }

    private Map<String, Object> resume(MockHttpServletResponse r) throws Exception {
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

    @Test
    void la_liste_riche_imprime_tout_le_texte_dans_l_ordre_nom_prenom_sur_plusieurs_pages() throws Exception {
        patient(CENTRE_A, "AMRANI_SARA", "P-001", "Amrani", "Sara", "F", "A+", 1, 10, "ASS-001", "0555 00 00 01", "ACTIF", "1 rue Alpha");
        patient(CENTRE_A, "AMRANI_ALI", "P-002", "Amrani", "Ali", "M", "O-", 2, 11, "ASS-002", "0555 00 00 02", "ACTIF", null);
        patient(CENTRE_A, "AMRANI_ALI_BIS", "P-003", "Amrani", "ali", "M", "B+", 3, 12, "ASS-003", null, "ACTIF", "");
        patient(CENTRE_A, "MINUSCULE", "P-004", "benali", "Karim", "M", null, null, 13, "ASS-004", "", "SUSPENDU", "2 rue Beta");
        patient(CENTRE_A, "ELOI", "P-005", "Éloi", "Émile", "M", "AB+", 4, 14, "ASS-005", "0555 00 00 05", "ACTIF", null);
        patient(CENTRE_A, "ZAHI", "P-006", "Zahi", "Zoé", "F", "A-", 5, 15, "ASS-006", "0555 00 00 06", null, null);
        patient(CENTRE_A, "FONTAINE", "P-007", "de la Fontaine", "Jean", "M", "O+", 6, 16, "ASS-007", "0555 00 00 07", "TRANSFERE", null);
        patient(CENTRE_A, "SANS_CODE", "", "Zéro", "Zélie", "F", "B-", 7, 17, "", "0555 00 00 08", "ACTIF", null);
        patient(CENTRE_A, "NOM_LONG", "P-009", "Nom très long qui dépasse largement la colonne prévue pour le nom du patient",
                "Prénom également très long pour vérifier la troncature ou le retour à la ligne", "F", "AB-", 8, 18, "ASS-009", "0555 00 00 09", "ACTIF", null);
        for (int i = 10; i < 52; i++) {
            patient(CENTRE_A, "GEN_" + i, "P-0" + i, "Patient " + (i % 7 == 0 ? "ZZ" : "A") + i, "Test", i % 2 == 0 ? "M" : "F", "A+", 9, 19,
                    "ASS-0" + i, "0555 00 01 " + i, "ACTIF", null);
        }
        // un patient d'un autre centre : ne doit pas apparaître
        patient(CENTRE_B, "ETRANGER", "P-900", "Etranger", "Autre", "M", "A+", 1, 10, "ASS-900", "0000", "ACTIF", null);

        Map<String, Object> cas = new LinkedHashMap<>();
        MockHttpServletResponse reponse = parType(CENTRE_A, null);
        assertEquals(200, reponse.getStatus());
        cas.put("par type (PDF)", resume(reponse));
        cas.put("par modele (PDF)", resume(parModele(MODELE_A, CENTRE_A)));
        cas.put("par type (tableur)", resume(parType(CENTRE_A, "EXCEL")));
        arbre("liste_patients_doc5f_riche_texte", cas);
    }

    @Test
    void un_centre_sans_patient_imprime_le_document_comme_avant() throws Exception {
        patient(CENTRE_B, "ETRANGER", "P-900", "Etranger", "Autre", "M", "A+", 1, 10, "ASS-900", "0000", "ACTIF", null);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("par type (PDF)", resume(parType(CENTRE_A, null)));
        cas.put("par type (tableur)", resume(parType(CENTRE_A, "EXCEL")));
        arbre("liste_patients_doc5f_vide_texte", cas);
    }
}
