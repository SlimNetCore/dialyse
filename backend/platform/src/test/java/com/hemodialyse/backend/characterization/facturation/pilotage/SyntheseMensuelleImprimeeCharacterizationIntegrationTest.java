package com.hemodialyse.backend.characterization.facturation.pilotage;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F5 : {@code POST /api/v1/facturation/synthese/print} (synthèse mensuelle de facturation : tableau forfait x caisse HT/TTC,
 * totaux, indicateurs, répartitions). Rendu Jasper dynamique ; PDF observé par son texte extrait (PDFBox), son type, ses
 * en-têtes et son nombre de pages (le PDF ne porte aucun horodatage d'édition).
 */
class SyntheseMensuelleImprimeeCharacterizationIntegrationTest extends PilotageFacturationSupport {

    @Override
    protected String theme() {
        return "synthese-imprimee";
    }

    /**
     * Février 2026 du centre A : trois caisses (CASNOS, CNAS, sans caisse), trois forfaits, TVA 19 %, 9 %, 1,25 % et 0 %,
     * une facture sans ligne, une facture hors période et une facture du centre B.
     */
    private void jeuDeFevrier() {
        UUID f1 = facture(CENTRE_A, P_AMRANI, "FAC-0001", "2026-02-01", "19.00", "1000.00", "190.00", "1190.00", AGENCE_1, "PERMANENT");
        ligne(f1, CENTRE_A, "Forfait HD", "100.05", 9, "900.45");
        ligne(f1, CENTRE_A, "Forfait Transport", "9.99", 10, "99.90");
        UUID f2 = facture(CENTRE_A, P_BELKACEM, "FAC-0002", "2026-02-15", "9.00", "500.00", "45.00", "545.00", AGENCE_2, "VACANCIER");
        ligne(f2, CENTRE_A, "Forfait HD", "0.50", 1, "0.50");
        ligne(f2, CENTRE_A, "Forfait Dialyse Nuit", "499.50", 1, "499.50");
        UUID f3 = facture(CENTRE_A, P_CHIKHI, "FAC-0003", "2026-02-28", "1.25", "10.00", "0.13", "10.13", null, null);
        ligne(f3, CENTRE_A, "Forfait HD", "10.00", 1, "10.00");
        UUID f4 = facture(CENTRE_A, P_AMRANI, "FAC-0004", "2026-02-10", "0.00", "250.00", "0.00", "250.00", AGENCE_1, "PERMANENT");
        ligne(f4, CENTRE_A, "Forfait HD", "250.00", 1, "250.00");
        // facture sans ligne : comptée dans les indicateurs et les répartitions, absente du tableau
        UUID sansLigne = facture(CENTRE_A, P_CHIKHI, "FAC-0005", "2026-02-20", "0.00", "77.00", "0.00", "77.00", AGENCE_2, "PERMANENT");
        // hors période (31 janvier, 1er mars) et centre B : exclus
        UUID janvier = facture(CENTRE_A, P_AMRANI, "FAC-0000", "2026-01-31", "0.00", "5000.00", "0.00", "5000.00", AGENCE_1, "PERMANENT");
        ligne(janvier, CENTRE_A, "Forfait HD", "5000.00", 1, "5000.00");
        UUID mars = facture(CENTRE_A, P_AMRANI, "FAC-0006", "2026-03-01", "0.00", "6000.00", "0.00", "6000.00", AGENCE_1, "PERMANENT");
        ligne(mars, CENTRE_A, "Forfait HD", "6000.00", 1, "6000.00");
        UUID b = facture(CENTRE_B, P_CENTRE_B, "FAC-B-1", "2026-02-03", "0.00", "9999.00", "0.00", "9999.00", AGENCE_B, "PERMANENT");
        ligne(b, CENTRE_B, "Forfait B", "9999.00", 1, "9999.00");
        seance(P_AMRANI, CENTRE_A, "2026-02-01", "FACTUREE", f1);
        seance(P_AMRANI, CENTRE_A, "2026-02-02", "FACTUREE", f1);
        seance(P_AMRANI, CENTRE_A, "2026-02-03", "FACTUREE", f1);
        seance(P_BELKACEM, CENTRE_A, "2026-02-15", "FACTUREE", f2);
        seance(P_CHIKHI, CENTRE_A, "2026-02-27", "FACTUREE", f3);
        seance(P_AMRANI, CENTRE_A, "2026-02-10", "FACTUREE", f4);
        seance(P_CHIKHI, CENTRE_A, "2026-02-19", "FACTUREE", sansLigne);
        seance(P_CHIKHI, CENTRE_A, "2026-02-20", "FACTUREE", sansLigne);
        seance(P_CHIKHI, CENTRE_A, "2026-02-21", "FACTUREE", sansLigne);
        seance(P_CHIKHI, CENTRE_A, "2026-02-22", "FACTUREE", sansLigne);
    }

    private long fichiersTemporaires() throws IOException {
        long n = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Path.of(System.getProperty("java.io.tmpdir")), "synthese-facturation-*")) {
            for (Path ignored : ds) n++;
        }
        return n;
    }

    @Test
    void la_synthese_de_fevrier_presente_le_tableau_forfait_par_caisse_les_totaux_et_les_repartitions() throws Exception {
        jeuDeFevrier();
        long avant = fichiersTemporaires();

        Resp r = synthese(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", adminA());

        goldenPdf("fevrier_2026_pdf_nouveau_code", r); // Nouveau code F5 : totaux enregistrés (ADR 0034), montant de TVA
        assertEquals(avant, fichiersTemporaires(), "le gabarit temporaire est supprimé après impression");
    }

    @Test
    void les_bornes_de_la_periode_sont_inclusives_et_une_periode_d_un_jour_est_acceptee() throws Exception {
        jeuDeFevrier();

        goldenPdf("jour_unique_2026_02_28_nouveau_code", synthese(CENTRE_A, "2026-02-28", "2026-02-28", "PDF", adminA()));
        goldenPdf("periode_31_janvier_au_1er_mars_nouveau_code", synthese(CENTRE_A, "2026-01-31", "2026-03-01", "PDF", adminA()));
    }

    @Test
    void une_periode_de_plusieurs_annees_est_acceptee_sans_plafond() throws Exception {
        jeuDeFevrier();

        goldenPdf("deux_annees_nouveau_code", synthese(CENTRE_A, "2025-01-01", "2026-12-31", "PDF", adminA()));
    }

    @Test
    void une_periode_sans_facture_donne_une_colonne_inconnu_et_un_message_vide() throws Exception {
        jeuDeFevrier();

        goldenPdf("periode_sans_facture", synthese(CENTRE_A, "2026-05-01", "2026-05-31", "PDF", adminA()));
        // Nouveau code F5 : un centre qui n'est pas celui de la session donne 403 (KB-FAC-52 corrigé)
        golden("centre_sans_facture_nouveau_code", synthese(CENTRE_VIDE, "2026-02-01", "2026-02-28", "PDF", adminA()));
    }

    @Test
    void la_synthese_ne_filtre_pas_par_le_centre_de_l_utilisateur() throws Exception {
        jeuDeFevrier();

        // Nouveau code F5 : 403 si le centre du corps n'est pas celui de la session (KB-FAC-52 corrigé)
        golden("centre_b_vu_par_admin_a_nouveau_code", synthese(CENTRE_B, "2026-02-01", "2026-02-28", "PDF", adminA()));
        goldenPdf("centre_b_par_son_administrateur_nouveau_code", synthese(CENTRE_B, "2026-02-01", "2026-02-28", "PDF", adminB()));
    }

    @Test
    void les_barres_de_repartition_arrondissent_au_plus_proche_sur_dix_positions() throws Exception {
        // patients : caisse 1 = 4 (max), caisse 2 = 1 (1/4 de 10 = 2,5 -> 3), sans caisse = 3 (7,5 -> 8)
        UUID p4 = UUID.nameUUIDFromBytes("f5:p4".getBytes());
        UUID p5 = UUID.nameUUIDFromBytes("f5:p5".getBytes());
        UUID p6 = UUID.nameUUIDFromBytes("f5:p6".getBytes());
        UUID p7 = UUID.nameUUIDFromBytes("f5:p7".getBytes());
        patient(p4, CENTRE_A, "PIL-004", "Dahmani", "Samir");
        patient(p5, CENTRE_A, "PIL-005", "Ferhat", "Yasmine");
        patient(p6, CENTRE_A, "PIL-006", "Gacem", "Karim");
        patient(p7, CENTRE_A, "PIL-007", "Haddad", "Meriem");
        UUID[] caisse1 = {P_AMRANI, p4, p5, p6};
        int i = 0;
        for (UUID p : caisse1) {
            UUID f = facture(CENTRE_A, p, "FAC-1" + i, "2026-07-0" + (++i), "0.00", "100.00", "0.00", "100.00", AGENCE_1, "PERMANENT");
            ligne(f, CENTRE_A, "Forfait HD", "100.00", 1, "100.00");
            seance(p, CENTRE_A, "2026-07-1" + i, "FACTUREE", f);
        }
        UUID f5 = facture(CENTRE_A, P_BELKACEM, "FAC-2", "2026-07-05", "0.00", "100.00", "0.00", "100.00", AGENCE_2, "VACANCIER");
        ligne(f5, CENTRE_A, "Forfait HD", "100.00", 1, "100.00");
        seance(P_BELKACEM, CENTRE_A, "2026-07-20", "FACTUREE", f5);
        for (UUID p : new UUID[]{P_CHIKHI, p7, UUID.nameUUIDFromBytes("f5:p8".getBytes())}) {
            if (p.equals(UUID.nameUUIDFromBytes("f5:p8".getBytes()))) {
                patient(p, CENTRE_A, "PIL-008", "Ibrahim", "Lamia");
            }
            UUID f = facture(CENTRE_A, p, "FAC-3" + p.toString().substring(0, 4), "2026-07-06", "0.00", "100.00", "0.00", "100.00", null, null);
            ligne(f, CENTRE_A, "Forfait HD", "100.00", 1, "100.00");
            seance(p, CENTRE_A, "2026-07-21", "FACTUREE", f);
        }

        goldenPdf("barres_arrondies_nouveau_code", synthese(CENTRE_A, "2026-07-01", "2026-07-31", "PDF", adminA()));
    }

    @Test
    void les_libelles_de_caisse_avec_apostrophe_ou_fin_de_cdata_sont_neutralises() throws Exception {
        UUID caisseSpeciale = UUID.nameUUIDFromBytes("f5:caisse-speciale".getBytes());
        UUID agenceSpeciale = UUID.nameUUIDFromBytes("f5:agence-speciale".getBytes());
        nommer(caisseSpeciale, "<CAISSE_SPECIALE>");
        nommer(agenceSpeciale, "<AGENCE_SPECIALE>");
        caisse(caisseSpeciale, CENTRE_A, "C'OR", "Caisse d'Or ]]> & <b>");
        agence(agenceSpeciale, CENTRE_A, caisseSpeciale, "AG-S", "Agence speciale");
        UUID f = facture(CENTRE_A, P_AMRANI, "FAC-0001", "2026-08-10", "19.00", "100.00", "19.00", "119.00", agenceSpeciale, "PERMANENT");
        ligne(f, CENTRE_A, "Forfait d'hémodialyse", "100.00", 1, "100.00");
        seance(P_AMRANI, CENTRE_A, "2026-08-09", "FACTUREE", f);

        goldenPdf("libelles_speciaux_nouveau_code", synthese(CENTRE_A, "2026-08-01", "2026-08-31", "PDF", adminA()));
    }

    @Test
    void deux_caisses_de_meme_code_partagent_une_seule_colonne_nommee_d_apres_la_premiere() throws Exception {
        UUID doublon = UUID.nameUUIDFromBytes("f5:caisse-doublon".getBytes());
        UUID agenceDoublon = UUID.nameUUIDFromBytes("f5:agence-doublon".getBytes());
        nommer(doublon, "<CAISSE_DOUBLON>");
        nommer(agenceDoublon, "<AGENCE_DOUBLON>");
        caisse(doublon, CENTRE_A, "CNAS", "CNAS bis");
        agence(agenceDoublon, CENTRE_A, doublon, "AG-D", "Agence doublon");
        UUID f1 = facture(CENTRE_A, P_AMRANI, "FAC-0001", "2026-10-05", "0.00", "100.00", "0.00", "100.00", AGENCE_1, "PERMANENT");
        ligne(f1, CENTRE_A, "Forfait HD", "100.00", 1, "100.00");
        UUID f2 = facture(CENTRE_A, P_BELKACEM, "FAC-0002", "2026-10-06", "0.00", "50.00", "0.00", "50.00", agenceDoublon, "PERMANENT");
        ligne(f2, CENTRE_A, "Forfait HD", "50.00", 1, "50.00");

        goldenPdf("caisses_de_meme_code_nouveau_code", synthese(CENTRE_A, "2026-10-01", "2026-10-31", "PDF", adminA()));
    }

    @Test
    void six_caisses_elargissent_la_page_du_tableau() throws Exception {
        for (int i = 1; i <= 6; i++) {
            UUID c = UUID.nameUUIDFromBytes(("f5:large-caisse" + i).getBytes());
            UUID a = UUID.nameUUIDFromBytes(("f5:large-agence" + i).getBytes());
            nommer(c, "<CAISSE_LARGE_" + i + ">");
            nommer(a, "<AGENCE_LARGE_" + i + ">");
            caisse(c, CENTRE_A, "L" + i, "Caisse large " + i);
            agence(a, CENTRE_A, c, "AL" + i, "Agence large " + i);
            UUID f = facture(CENTRE_A, P_AMRANI, "FAC-L" + i, "2026-09-0" + i, "0.00", i + "00.00", "0.00", i + "00.00", a, "PERMANENT");
            ligne(f, CENTRE_A, "Forfait HD", i + "00.00", 1, i + "00.00");
        }

        goldenPdf("six_caisses_nouveau_code", synthese(CENTRE_A, "2026-09-01", "2026-09-30", "PDF", adminA()));
    }

    @Test
    void les_formats_pdf_excel_html_donnent_leur_type_et_leur_nom_de_fichier() throws Exception {
        jeuDeFevrier();
        Map<String, Object> cas = new LinkedHashMap<>();
        String[] formats = {"PDF", "pdf", " Pdf ", "", "EXCEL", "XLS", "XLSX", "xlsx", "HTML", "html", "CSV", "WORD", "docx"};
        for (String format : formats) {
            Resp r = synthese(CENTRE_A, "2026-02-01", "2026-02-28", format, adminA());
            ObjectNode o = mapper.createObjectNode();
            o.put("status", r.status());
            o.put("contentType", r.contentType());
            o.put("disposition", r.disposition());
            if (r.status() != 200) {
                o.put("corps", r.raw());
            } else if (r.contentType() != null && r.contentType().contains("html")) {
                o.put("debutHtml", r.raw().substring(0, Math.min(40, r.raw().length())).replaceAll("\\s+", " "));
                o.put("pdfEmbarque", r.raw().contains("data:application/pdf;base64,"));
            } else if (r.contentType() != null && r.contentType().contains("pdf")) {
                o.put("pages", pagesDuPdf(r.octets()));
            } else {
                o.put("signatureZip", r.octets().length > 2 && r.octets()[0] == 'P' && r.octets()[1] == 'K');
            }
            cas.put("[" + format + "]", o);
        }
        goldenNode("formats", mapper.valueToTree(cas));
    }

    @Test
    void le_format_absent_est_un_pdf() throws Exception {
        jeuDeFevrier();

        Resp r = doPost(FACTURATION + "/synthese/print", json("{'centerId':'" + CENTRE_A + "','periodStart':'2026-02-01','periodEnd':'2026-02-28'}"), adminA());

        assertEquals(200, r.status());
        assertEquals("application/pdf", r.contentType());
        assertEquals("inline; filename=synthese-facturation-mensuelle.pdf", r.disposition());
        assertTrue(texteDuPdf(r.octets()).contains("SYNTHESE MENSUELLE FACTURATION"));
    }

    @Test
    void le_classeur_excel_contient_le_tableau_les_totaux_et_les_indicateurs() throws Exception {
        jeuDeFevrier();

        Resp r = synthese(CENTRE_A, "2026-02-01", "2026-02-28", "EXCEL", adminA());

        assertEquals(200, r.status());
        goldenNode("excel_fevrier_2026_nouveau_code", contenuExcel(r.octets()));
    }

    @Test
    void les_periodes_et_centres_invalides_sont_refuses_en_texte_brut_400() throws Exception {
        jeuDeFevrier();
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("debut apres fin", resume(synthese(CENTRE_A, "2026-03-01", "2026-02-01", "PDF", adminA())));
        cas.put("debut absent", resume(synthese(CENTRE_A, null, "2026-02-28", "PDF", adminA())));
        cas.put("fin absente", resume(synthese(CENTRE_A, "2026-02-01", null, "PDF", adminA())));
        cas.put("centre absent", resume(synthese(null, "2026-02-01", "2026-02-28", "PDF", adminA())));
        cas.put("format inconnu", resume(synthese(CENTRE_A, "2026-02-01", "2026-02-28", "CSV", adminA())));
        cas.put("date impossible", resume(doPost(FACTURATION + "/synthese/print",
                json("{'centerId':'" + CENTRE_A + "','periodStart':'2026-02-30','periodEnd':'2026-03-01'}"), adminA())));
        cas.put("date au format francais", resume(doPost(FACTURATION + "/synthese/print",
                json("{'centerId':'" + CENTRE_A + "','periodStart':'01/02/2026','periodEnd':'28/02/2026'}"), adminA())));
        cas.put("centre illisible", resume(doPost(FACTURATION + "/synthese/print",
                json("{'centerId':'abc','periodStart':'2026-02-01','periodEnd':'2026-02-28'}"), adminA())));
        cas.put("json invalide", resume(doPost(FACTURATION + "/synthese/print", "{", adminA())));
        cas.put("corps vide", resume(doPost(FACTURATION + "/synthese/print", "", adminA())));
        cas.put("objet vide", resume(doPost(FACTURATION + "/synthese/print", "{}", adminA())));
        cas.put("champs inconnus ignores", resume(doPost(FACTURATION + "/synthese/print",
                json("{'centerId':'" + CENTRE_A + "','periodStart':'2026-02-01','periodEnd':'2026-02-28','x':1}"), adminA())));
        cas.put("GET non supporte", resume(doGet(FACTURATION + "/synthese/print", adminA())));
        goldenNode("refus_corps_et_periodes", mapper.valueToTree(cas));
    }

    @Test
    void la_synthese_repond_selon_le_profil() throws Exception {
        jeuDeFevrier();
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("ADMIN", synthese(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", as(CENTRE_A, "ADMIN")).status());
        cas.put("SECRETAIRE", synthese(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", as(CENTRE_A, "SECRETAIRE")).status());
        cas.put("MEDECIN seul", synthese(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", as(CENTRE_A, "MEDECIN")).status());
        cas.put("MEDECIN + SECRETAIRE", synthese(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", as(CENTRE_A, "MEDECIN", "SECRETAIRE")).status());
        cas.put("INFIRMIER seul", synthese(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", as(CENTRE_A, "INFIRMIER")).status());
        cas.put("COMPTABLE", synthese(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", as(CENTRE_A, "COMPTABLE")).status());
        cas.put("DIRECTION", synthese(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", direction()).status());
        cas.put("SUPERADMIN", synthese(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", as(CENTRE_A, "SUPERADMIN")).status());
        cas.put("aucun role", synthese(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", as(CENTRE_A)).status());
        cas.put("anonyme", doPostAnonyme(FACTURATION + "/synthese/print", corpsSynthese(CENTRE_A, "2026-02-01", "2026-02-28", "PDF")).status());
        goldenNode("profils", mapper.valueToTree(cas));
        golden("refus_medecin_seul", synthese(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", as(CENTRE_A, "MEDECIN")));
    }

    private ObjectNode resume(Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status());
        o.put("contentType", r.contentType());
        String type = r.contentType() == null ? "" : r.contentType();
        if (type.contains("problem+json")) {
            // code d'erreur ; les messages techniques du JDK, de Jackson et de Spring ne sont pas verrouillés
            JsonNode n = mapper.readTree(r.raw());
            String code = n.path("code").asString("");
            o.put("code", code);
            if ("VALIDATION_ERROR".equals(code)) o.put("titre", n.path("title").asString());
        } else if (type.contains("pdf")) {
            o.put("corps", "(pdf, " + (r.octets().length > 0 ? "non vide" : "vide") + ")");
        } else {
            o.put("corps", r.raw() == null ? "" : r.raw());
        }
        return o;
    }

    private JsonNode contenuExcel(byte[] octets) throws Exception {
        ObjectNode racine = mapper.createObjectNode();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(octets))) {
            ArrayNode feuilles = racine.putArray("feuilles");
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                ObjectNode f = feuilles.addObject();
                f.put("nom", sheet.getSheetName());
                ArrayNode lignes = f.putArray("lignes");
                for (Row row : sheet) {
                    ArrayNode cellules = lignes.addArray();
                    for (Cell c : row) {
                        cellules.add(c.getCellType() == CellType.NUMERIC ? "N:" + c.getNumericCellValue() : "S:" + c.getStringCellValue());
                    }
                }
            }
        }
        return racine;
    }
}
