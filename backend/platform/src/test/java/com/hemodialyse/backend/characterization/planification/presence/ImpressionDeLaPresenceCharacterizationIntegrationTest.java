package com.hemodialyse.backend.characterization.planification.presence;

import com.hemodialyse.backend.characterization.planification.PresenceCharacterizationSupport;
import com.hemodialyse.backend.characterization.planification.JourSemaine;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Filet de caractérisation P4 : {@code GET /api/v1/infirmiers/presence/imprimer} (planning de présence imprimable de la semaine
 * par le modèle de document {@code PLANNING_PRESENCE_INFIRMIERS} du centre). On observe le statut, le type de contenu, le nom de
 * fichier, et le TEXTE du document (cellules lues dans le HTML ou le classeur XLSX produits), jamais le binaire PDF (seule sa
 * signature est vérifiée). Le format dépend du modèle du centre ({@code modele_document.format_impression}).
 */
class ImpressionDeLaPresenceCharacterizationIntegrationTest extends PresenceCharacterizationSupport {

    private static final Pattern TEXTE_XLSX = Pattern.compile("<t[^>]*>([^<]*)</t>");

    @Override
    protected String theme() {
        return "p4-presence/impression";
    }

    private void amorcer() throws Exception {
        decor();
        semaine(0);
        semaine(1);
        UUID amrani = infirmierAffecte(CENTRE_A, "INF_AMRANI", "Amrani", "Sara", true, salle1, matin, "LUNDI,MERCREDI");
        UUID benali = infirmierAffecte(CENTRE_A, "INF_BENALI", "Benali", null, false, salle1, matin, "LUNDI");
        UUID cherif = inf(CENTRE_A, "INF_CHERIF", "Cherif", "Lina", false);
        absence(CENTRE_A, "ABS_BENALI", benali, semaine(1, JourSemaine.LUNDI), semaine(1, JourSemaine.LUNDI), "CONGE", null);
        remplacement(CENTRE_A, "REMPL_CHERIF", semaine(1, JourSemaine.LUNDI), salle1, matin, cherif, benali);
        infirmierAffecte(CENTRE_B, "INF_ETRANGER", "Etranger", "Autre", false, salleB, creneauB, "LUNDI");
        // l'impression ne crée plus le modèle (KB-PLN-51) : le modèle du centre est celui que le provisionnement du démarrage a posé
        jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, "
                        + "description, active) VALUES (?, ?, 'PLANNING_PRESENCE_INFIRMIERS', 'Planning de présence des infirmiers', "
                        + "'PLANNING_PRESENCE_INFIRMIERS', 'reports/planning_presence_infirmiers.jrxml', 'PDF', 'Présence', TRUE)",
                UUID.randomUUID(), CENTRE_A);
    }

    private void formatDuModele(UUID centre, String format) {
        jdbc.update("UPDATE modele_document SET format_impression = ? WHERE center_id = ? AND UPPER(type_document) = 'PLANNING_PRESENCE_INFIRMIERS'",
                format, centre);
    }

    private Map<String, Object> enveloppe(MockHttpServletResponse r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.getStatus());
        m.put("contentType", r.getContentType());
        m.put("contentDisposition", r.getHeader("Content-Disposition") == null ? null : dates(r.getHeader("Content-Disposition")));
        m.put("cacheControl", r.getHeader("Cache-Control"));
        m.put("tailleNonNulle", r.getContentAsByteArray().length > 0);
        return m;
    }

    /**
     * Le « HTML » du modèle est une page qui embarque le PDF en base64 (iframe) : on décode puis on lit le texte du PDF.
     */
    private List<String> textesHtml(byte[] octets) throws IOException {
        Matcher m = Pattern.compile("data:application/pdf;base64,([A-Za-z0-9+/=]+)").matcher(new String(octets, StandardCharsets.UTF_8));
        if (!m.find()) return List.of("<pas de PDF embarque>");
        return textesPdf(java.util.Base64.getDecoder().decode(m.group(1)));
    }

    /** Texte du PDF (OpenPDF), une ligne par ligne de texte, dates françaises nommées. */
    private List<String> textesPdf(byte[] octets) throws IOException {
        com.lowagie.text.pdf.PdfReader lecteur = new com.lowagie.text.pdf.PdfReader(octets);
        List<String> lignes = new ArrayList<>();
        com.lowagie.text.pdf.parser.PdfTextExtractor extracteur = new com.lowagie.text.pdf.parser.PdfTextExtractor(lecteur);
        for (int page = 1; page <= lecteur.getNumberOfPages(); page++) {
            for (String l : extracteur.getTextFromPage(page).split("\\R")) {
                String t = l.trim();
                if (!t.isEmpty()) lignes.add(datesFr(t));
            }
        }
        lecteur.close();
        return lignes;
    }

    private List<String> textesXlsx(byte[] octets) throws IOException {
        List<String> textes = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(octets))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (!e.getName().equals("xl/sharedStrings.xml") && !e.getName().startsWith("xl/worksheets/sheet")) continue;
                Matcher m = TEXTE_XLSX.matcher(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                while (m.find()) textes.add(datesFr(m.group(1)));
            }
        }
        return textes;
    }

    // ------------------------------------------------------------------ PDF (format par défaut du modèle)

    @Test
    void le_planning_est_un_pdf_en_ligne_nomme_d_apres_le_dimanche_de_la_semaine() throws Exception {
        amorcer();
        MockHttpServletResponse r = brute(PRESENCE + "/imprimer?date=" + semaine(1, JourSemaine.MERCREDI), adminA());
        Map<String, Object> m = enveloppe(r);
        m.put("signaturePdf", new String(r.getContentAsByteArray(), 0, 4, StandardCharsets.ISO_8859_1));
        m.put("texteDuPdf", textesPdf(r.getContentAsByteArray()));
        arbre("pdf_par_defaut", m);
        // n'importe quel jour de la semaine donne le même nom de fichier
        Map<String, Object> noms = new LinkedHashMap<>();
        for (JourSemaine j : JourSemaine.values()) {
            noms.put(j.name(), dates(brute(PRESENCE + "/imprimer?date=" + semaine(1, j), adminA()).getHeader("Content-Disposition")));
        }
        arbre("nom_de_fichier_selon_le_jour_de_la_semaine", noms);
    }

    @Test
    void sans_date_la_semaine_courante_est_imprimee_et_une_date_illisible_est_refusee() throws Exception {
        amorcer();
        MockHttpServletResponse courante = brute(PRESENCE + "/imprimer", adminA());
        arbre("sans_date_semaine_courante", enveloppe(courante));
        lecture("date_illisible", doGet(PRESENCE + "/imprimer?date=abc", adminA()));
    }

    // ------------------------------------------------------------------ texte du document

    @Test
    void le_texte_du_planning_en_html_liste_les_prevus_les_absents_et_les_remplacants_du_centre_seulement() throws Exception {
        amorcer();
        formatDuModele(CENTRE_A, "HTML");
        MockHttpServletResponse r = brute(PRESENCE + "/imprimer?date=" + semaine(1, JourSemaine.MERCREDI), adminA());
        Map<String, Object> m = enveloppe(r);
        m.put("textes", textesHtml(r.getContentAsByteArray()));
        arbre("html_textes_du_centre_A", m);
        jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, "
                        + "description, active) VALUES (?, ?, 'PLANNING_PRESENCE_INFIRMIERS', 'Planning de présence des infirmiers', "
                        + "'PLANNING_PRESENCE_INFIRMIERS', 'reports/planning_presence_infirmiers.jrxml', 'PDF', 'Présence', TRUE)",
                UUID.randomUUID(), CENTRE_B);
        formatDuModele(CENTRE_B, "HTML");
        MockHttpServletResponse b = brute(PRESENCE + "/imprimer?centerId=" + CENTRE_B + "&date=" + semaine(1, JourSemaine.MERCREDI),
                as(CENTRE_B, "ADMIN"));
        Map<String, Object> mb = enveloppe(b);
        mb.put("textes", textesHtml(b.getContentAsByteArray()));
        arbre("html_textes_du_centre_B", mb);
    }

    @Test
    void le_texte_de_la_semaine_suivante_ne_montre_ni_absent_ni_remplacant() throws Exception {
        amorcer();
        formatDuModele(CENTRE_A, "HTML");
        MockHttpServletResponse r = brute(PRESENCE + "/imprimer?date=" + semaine(2, JourSemaine.MERCREDI), adminA());
        semaine(2);
        arbre("html_textes_semaine_suivante", textesHtml(r.getContentAsByteArray()));
    }

    @Test
    void les_formats_excel_xls_xlsx_donnent_un_classeur_en_piece_jointe_dont_on_lit_les_cellules() throws Exception {
        amorcer();
        Map<String, Object> r = new LinkedHashMap<>();
        for (String format : new String[]{"EXCEL", "XLS", "XLSX", "excel"}) {
            formatDuModele(CENTRE_A, format);
            MockHttpServletResponse resp = brute(PRESENCE + "/imprimer?date=" + semaine(1, JourSemaine.MERCREDI), adminA());
            Map<String, Object> m = enveloppe(resp);
            m.put("signatureZip", new String(resp.getContentAsByteArray(), 0, 2, StandardCharsets.ISO_8859_1));
            if (format.equals("EXCEL")) m.put("cellules", textesXlsx(resp.getContentAsByteArray()));
            r.put("format " + format, m);
        }
        arbre("formats_excel", r);
    }

    @Test
    void les_autres_formats_csv_rtf_inconnu_sont_servis_comme_un_pdf_en_ligne() throws Exception {
        amorcer();
        Map<String, Object> r = new LinkedHashMap<>();
        for (String format : new String[]{"PDF", "pdf", "CSV", "RTF", "INCONNU"}) {
            formatDuModele(CENTRE_A, format);
            MockHttpServletResponse resp = brute(PRESENCE + "/imprimer?date=" + semaine(1, JourSemaine.MERCREDI), adminA());
            Map<String, Object> m = enveloppe(resp);
            byte[] contenu = resp.getContentAsByteArray();
            m.put("debutDuContenu", contenu.length >= 4 ? new String(contenu, 0, 4, StandardCharsets.ISO_8859_1).replaceAll("[^\\x20-\\x7e]", "?") : "");
            r.put("format " + format, m);
        }
        arbre("formats_pdf_csv_et_inconnus", r);
    }

    // ------------------------------------------------------------------ modèle du centre

    @Test
    void un_modele_desactive_refuse_l_impression_avec_un_message_lisible() throws Exception {
        amorcer();
        jdbc.update("UPDATE modele_document SET active = FALSE WHERE center_id = ? AND UPPER(type_document) = 'PLANNING_PRESENCE_INFIRMIERS'",
                CENTRE_A);
        lecture("modele_desactive", doGet(PRESENCE + "/imprimer?date=" + semaine(1, JourSemaine.MERCREDI), adminA()));
    }

    @Test
    void l_impression_ne_cree_pas_le_modele_du_centre_et_ne_touche_pas_aux_tables_de_la_presence() throws Exception {
        decor();
        semaine(1);
        int avant = jdbc.queryForObject("SELECT COUNT(*) FROM modele_document WHERE center_id = ?", Integer.class, CENTRE_A);
        String empreinte = empreinteP3();
        brute(PRESENCE + "/imprimer?date=" + semaine(1, JourSemaine.MERCREDI), adminA());
        int apres = jdbc.queryForObject("SELECT COUNT(*) FROM modele_document WHERE center_id = ?", Integer.class, CENTRE_A);
        arbre("provisionnement_du_modele", Map.of("modelesAvant", avant, "modelesApres", apres, "tablesP3Inchangees", empreinte.equals(empreinteP3()),
                "modelePresenceCree", jdbc.queryForObject("SELECT COUNT(*) FROM modele_document WHERE center_id = ? AND UPPER(type_document) = "
                        + "'PLANNING_PRESENCE_INFIRMIERS'", Integer.class, CENTRE_A)));
    }
}
