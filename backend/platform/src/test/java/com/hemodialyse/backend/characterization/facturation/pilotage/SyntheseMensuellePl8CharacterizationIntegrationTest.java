package com.hemodialyse.backend.characterization.facturation.pilotage;

import com.hemodialyse.backend.application.audit.AuditActionResolver;
import com.hemodialyse.backend.application.audit.AuditWriterService;
import com.hemodialyse.backend.infrastructure.security.AuditRequestFilter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * PL8 : complément du filet de la synthèse mensuelle de facturation avant le déplacement du rendu (gabarit Jasper dynamique et
 * lecture SQL de {@code factures} / {@code facture_lignes} dans le gabarit) vers le port {@code documents.v1}. Complète
 * {@link SyntheseMensuelleImprimeeCharacterizationIntegrationTest} (F5) sans la dupliquer : montants énormes et arrondis,
 * textes longs et unicode, chaînes de payeurs atypiques (casse, espaces, agence inconnue, autre centre), pagination du tableau,
 * mois sans séance, bornes sur la date de facturation et non sur la création, classeur Excel hors du cas nominal, centre inconnu,
 * périodes extrêmes, types de contenu, audit.
 */
class SyntheseMensuellePl8CharacterizationIntegrationTest extends PilotageFacturationSupport {

    @Autowired
    private AuditRequestFilter filtreDAudit;
    @Autowired
    private AuditWriterService ecrivainDAudit;

    @Override
    protected String theme() {
        return "synthese-pl8";
    }

    /** Caisse et agence nommées du centre ; retourne l'agence. */
    private UUID payeur(String cle, UUID centre, String codeCaisse, String libelleCaisse) {
        UUID c = UUID.nameUUIDFromBytes(("pl8:caisse:" + cle).getBytes(StandardCharsets.UTF_8));
        UUID a = UUID.nameUUIDFromBytes(("pl8:agence:" + cle).getBytes(StandardCharsets.UTF_8));
        nommer(c, "<CAISSE_" + cle + ">");
        nommer(a, "<AGENCE_" + cle + ">");
        caisse(c, centre, codeCaisse, libelleCaisse);
        agence(a, centre, c, "AG-" + cle, "Agence " + cle);
        return a;
    }

    private UUID factureAvecLigne(UUID agence, String numero, String jour, String ht, String tva, String ttc, String forfait, String ligneHt) {
        UUID f = facture(CENTRE_A, P_AMRANI, numero, jour, "0.00", ht, tva, ttc, agence, "PERMANENT");
        ligne(f, CENTRE_A, forfait, ligneHt, 1, ligneHt);
        return f;
    }

    private ObjectNode statutEtType(Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status());
        o.put("contentType", r.contentType());
        return o;
    }

    // ------------------------------------------------------------------ montants

    @Test
    void les_montants_enormes_les_totaux_nuls_et_les_arrondis_des_lignes_sont_rendus_avec_deux_decimales() throws Exception {
        UUID ag = AGENCE_1;
        // montant énorme (numeric(14,2)) : ligne et totaux enregistrés
        factureAvecLigne(ag, "FAC-GROS", "2026-04-02", "800000000000.00", "152000000000.00", "952000000000.00", "Forfait HD", "800000000000.00");
        // TTC proportionnel non terminant : 1/3 et 2/3 de 3,50
        UUID f2 = facture(CENTRE_A, P_BELKACEM, "FAC-TIERS", "2026-04-03", "0.00", "3.00", "0.50", "3.50", AGENCE_2, "PERMANENT");
        ligne(f2, CENTRE_A, "Forfait HD", "1.00", 1, "1.00");
        ligne(f2, CENTRE_A, "Forfait Transport", "2.00", 1, "2.00");
        // arrondi à la paire : 0,50 / 1,00 de 1,09 = 0,545
        UUID f3 = facture(CENTRE_A, P_CHIKHI, "FAC-DEMI", "2026-04-04", "0.00", "1.00", "0.09", "1.09", AGENCE_1, "PERMANENT");
        ligne(f3, CENTRE_A, "Forfait Arrondi", "0.50", 1, "0.50");
        ligne(f3, CENTRE_A, "Forfait Arrondi 2", "0.50", 1, "0.50");
        // total HT nul avec une ligne nulle : TTC de la ligne = 0
        UUID f4 = facture(CENTRE_A, P_AMRANI, "FAC-ZERO", "2026-04-05", "0.00", "0.00", "0.00", "5.00", AGENCE_1, "PERMANENT");
        ligne(f4, CENTRE_A, "Forfait Gratuit", "0.00", 1, "0.00");
        // ligne négative (avoir) et montant minimal
        UUID f5 = facture(CENTRE_A, P_AMRANI, "FAC-AVOIR", "2026-04-06", "0.00", "-40.00", "0.00", "-40.00", AGENCE_2, "PERMANENT");
        ligne(f5, CENTRE_A, "Forfait HD", "-40.00", 1, "-40.00");
        factureAvecLigne(AGENCE_2, "FAC-CENTIME", "2026-04-07", "0.01", "0.00", "0.01", "Forfait Centime", "0.01");

        goldenPdf("montants_enormes_et_arrondis", synthese(CENTRE_A, "2026-04-01", "2026-04-30", "PDF", adminA()));
        goldenNode("montants_enormes_et_arrondis_excel", contenuExcel(synthese(CENTRE_A, "2026-04-01", "2026-04-30", "EXCEL", adminA()).octets()));
    }

    // ------------------------------------------------------------------ textes

    @Test
    void les_textes_longs_et_unicode_des_forfaits_et_des_caisses_sont_rendus_sans_erreur() throws Exception {
        String longForfait = "Forfait d'hémodialyse très long ".repeat(8).substring(0, 255);
        String longCaisse = "Caisse nationale d'assurance sociale des travailleurs salariés ".repeat(4);
        UUID ag = payeur("LONGUE", CENTRE_A, "LONG", longCaisse);
        UUID ag2 = payeur("UNICODE", CENTRE_A, "ÉTÉ", "Caisse œuvre ü € ★ مرافقة 中文");
        factureAvecLigne(ag, "FAC-L1", "2026-05-02", "100.00", "0.00", "100.00", longForfait, "100.00");
        factureAvecLigne(ag2, "FAC-L2", "2026-05-03", "50.00", "0.00", "50.00", "Forfait œuvre ü € ★ مرافقة 中文", "50.00");
        factureAvecLigne(ag2, "FAC-L3", "2026-05-04", "10.00", "0.00", "10.00", "Forfait ]]> & <b> $P{CENTER_ID} $P!{X} '", "10.00");

        goldenPdf("textes_longs_et_unicode", synthese(CENTRE_A, "2026-05-01", "2026-05-31", "PDF", adminA()));
    }

    // ------------------------------------------------------------------ chaîne des payeurs

    @Test
    void les_chaines_de_payeurs_atypiques_casse_espaces_agence_inconnue_ou_d_un_autre_centre() throws Exception {
        UUID agMin = payeur("MIN", CENTRE_A, "cnas", "cnas en minuscules");
        UUID agEsp = payeur("ESP", CENTRE_A, "  CNAS  ", "CNAS avec espaces");
        UUID agVide = payeur("VIDE", CENTRE_A, "   ", "Caisse au code blanc");
        // agence du centre A dont la caisse est celle du centre B
        UUID caisseDeB = UUID.nameUUIDFromBytes("pl8:caisse:AUTRE_CENTRE".getBytes(StandardCharsets.UTF_8));
        UUID agCroisee = UUID.nameUUIDFromBytes("pl8:agence:CROISEE".getBytes(StandardCharsets.UTF_8));
        nommer(caisseDeB, "<CAISSE_DU_CENTRE_B>");
        nommer(agCroisee, "<AGENCE_CROISEE>");
        caisse(caisseDeB, CENTRE_B, "CNAS", "Caisse du centre B");
        agence(agCroisee, CENTRE_A, caisseDeB, "AG-X", "Agence croisée");
        // agence du centre B citée par une facture du centre A
        UUID inconnue = UUID.nameUUIDFromBytes("pl8:agence:INCONNUE".getBytes(StandardCharsets.UTF_8));
        nommer(inconnue, "<AGENCE_SANS_LIGNE_EN_BASE>");

        factureAvecLigne(AGENCE_1, "FAC-1", "2026-06-02", "100.00", "0.00", "100.00", "Forfait HD", "100.00");
        factureAvecLigne(agMin, "FAC-2", "2026-06-03", "200.00", "0.00", "200.00", "Forfait HD", "200.00");
        factureAvecLigne(agEsp, "FAC-3", "2026-06-04", "300.00", "0.00", "300.00", "Forfait HD", "300.00");
        factureAvecLigne(agVide, "FAC-4", "2026-06-05", "400.00", "0.00", "400.00", "Forfait HD", "400.00");
        factureAvecLigne(agCroisee, "FAC-5", "2026-06-06", "500.00", "0.00", "500.00", "Forfait HD", "500.00");
        factureAvecLigne(AGENCE_B, "FAC-6", "2026-06-07", "600.00", "0.00", "600.00", "Forfait HD", "600.00");
        factureAvecLigne(inconnue, "FAC-7", "2026-06-08", "700.00", "0.00", "700.00", "Forfait HD", "700.00");

        goldenPdf("chaines_de_payeurs_atypiques", synthese(CENTRE_A, "2026-06-01", "2026-06-30", "PDF", adminA()));
    }

    @Test
    void deux_agences_d_une_meme_caisse_et_une_caisse_sans_agence_ne_creent_qu_une_colonne_par_code() throws Exception {
        UUID agPremiere = payeur("A1", CENTRE_A, "MUT", "Mutuelle");
        UUID agSeconde = UUID.nameUUIDFromBytes("pl8:agence:A2".getBytes(StandardCharsets.UTF_8));
        nommer(agSeconde, "<AGENCE_A2>");
        agence(agSeconde, CENTRE_A, UUID.nameUUIDFromBytes("pl8:caisse:A1".getBytes(StandardCharsets.UTF_8)), "AG-A2", "Seconde agence");
        payeur("ORPHELINE", CENTRE_A, "ORPH", "Caisse sans facture");
        factureAvecLigne(agPremiere, "FAC-1", "2026-07-02", "10.00", "0.00", "10.00", "Forfait HD", "10.00");
        factureAvecLigne(agSeconde, "FAC-2", "2026-07-03", "20.00", "0.00", "20.00", "Forfait HD", "20.00");

        goldenPdf("agences_multiples_d_une_caisse", synthese(CENTRE_A, "2026-07-01", "2026-07-31", "PDF", adminA()));
    }

    // ------------------------------------------------------------------ cas limites

    @Test
    void les_factures_sans_seance_les_bornes_sur_la_date_de_facturation_et_l_instant_de_creation_sont_observes() throws Exception {
        UUID f1 = factureAvecLigne(AGENCE_1, "FAC-SANS-SEANCE", "2026-08-01", "100.00", "0.00", "100.00", "Forfait HD", "100.00");
        UUID f2 = factureAvecLigne(AGENCE_1, "FAC-FIN", "2026-08-31", "200.00", "0.00", "200.00", "Forfait HD", "200.00");
        UUID f3 = factureAvecLigne(AGENCE_1, "FAC-JUILLET", "2026-07-31", "999.00", "0.00", "999.00", "Forfait HD", "999.00");
        UUID f4 = factureAvecLigne(AGENCE_1, "FAC-SEPTEMBRE", "2026-09-01", "888.00", "0.00", "888.00", "Forfait HD", "888.00");
        // l'instant de création (UTC, proche de minuit) n'intervient pas : seule la date de facturation compte
        jdbc.update("UPDATE factures SET created_at = ? WHERE id = ?", Timestamp.valueOf("2026-07-31 23:59:59"), f1);
        jdbc.update("UPDATE factures SET created_at = ? WHERE id = ?", Timestamp.valueOf("2026-09-01 00:00:01"), f2);
        jdbc.update("UPDATE factures SET created_at = ? WHERE id = ?", Timestamp.valueOf("2026-08-15 12:00:00"), f3);
        jdbc.update("UPDATE factures SET created_at = ? WHERE id = ?", Timestamp.valueOf("2026-08-15 12:00:00"), f4);
        // séance d'un autre statut rattachée à une facture du mois, séance facturée sans facture
        seance(P_AMRANI, CENTRE_A, "2026-08-02", "PLANIFIEE", f2);
        seance(P_AMRANI, CENTRE_A, "2026-08-03", "FACTUREE", null);

        goldenPdf("sans_seance_bornes_et_creation", synthese(CENTRE_A, "2026-08-01", "2026-08-31", "PDF", adminA()));
    }

    @Test
    void un_tableau_de_soixante_forfaits_deborde_sur_plusieurs_pages_dans_l_ordre_alphabetique() throws Exception {
        for (int i = 60; i >= 1; i--) {
            String label = String.format("Forfait %02d", i);
            UUID f = facture(CENTRE_A, P_AMRANI, "FAC-F" + i, "2026-09-15", "0.00", "10.00", "0.00", "10.00",
                    i % 2 == 0 ? AGENCE_1 : AGENCE_2, "PERMANENT");
            ligne(f, CENTRE_A, label, "10.00", 1, "10.00");
        }
        // même libellé de forfait sur deux lignes : regroupées
        UUID f = facture(CENTRE_A, P_BELKACEM, "FAC-DOUBLON", "2026-09-16", "0.00", "20.00", "0.00", "20.00", AGENCE_1, "PERMANENT");
        ligne(f, CENTRE_A, "Forfait 01", "5.00", 1, "5.00");
        ligne(f, CENTRE_A, "Forfait 01", "15.00", 1, "15.00");
        // libellés de casse différente : deux lignes distinctes, tri sensible à la casse du SGBD
        UUID g = facture(CENTRE_A, P_BELKACEM, "FAC-CASSE", "2026-09-17", "0.00", "2.00", "0.00", "2.00", AGENCE_1, "PERMANENT");
        ligne(g, CENTRE_A, "forfait 01", "1.00", 1, "1.00");
        ligne(g, CENTRE_A, "FORFAIT 01", "1.00", 1, "1.00");

        goldenPdf("soixante_forfaits", synthese(CENTRE_A, "2026-09-01", "2026-09-30", "PDF", adminA()));
    }

    @Test
    void le_classeur_excel_hors_nominal_periode_vide_et_six_caisses() throws Exception {
        goldenNode("excel_periode_sans_facture", contenuExcel(synthese(CENTRE_A, "2026-05-01", "2026-05-31", "EXCEL", adminA()).octets()));

        for (int i = 1; i <= 6; i++) {
            UUID a = payeur("X" + i, CENTRE_A, "X" + i, "Caisse X" + i + " ]]> d'or");
            factureAvecLigne(a, "FAC-X" + i, "2026-10-0" + i, i + "00.00", i + ".00", i + "01.00", "Forfait HD", i + "00.00");
        }
        factureAvecLigne(null, "FAC-SANS-AGENCE", "2026-10-09", "5.00", "0.00", "5.00", "Forfait Transport", "5.00");
        goldenNode("excel_six_caisses", contenuExcel(synthese(CENTRE_A, "2026-10-01", "2026-10-31", "EXCEL", adminA()).octets()));
    }

    // ------------------------------------------------------------------ garde, paramètres

    @Test
    void le_centre_inconnu_ou_d_une_autre_societe_et_les_periodes_extremes_sont_observes() throws Exception {
        factureAvecLigne(AGENCE_1, "FAC-1", "2026-02-10", "100.00", "0.00", "100.00", "Forfait HD", "100.00");
        UUID jamaisVu = UUID.nameUUIDFromBytes("pl8:centre-inconnu".getBytes(StandardCharsets.UTF_8));
        nommer(jamaisVu, "<CENTRE_INCONNU>");

        golden("centre_inconnu_nouveau_code", synthese(jamaisVu, "2026-02-01", "2026-02-28", "PDF", adminA()));
        golden("centre_autre_societe_nouveau_code", synthese(CENTRE_C, "2026-02-01", "2026-02-28", "PDF", adminA()));
        golden("secretaire_autre_centre_nouveau_code", synthese(CENTRE_B, "2026-02-01", "2026-02-28", "PDF", as(CENTRE_A, "SECRETAIRE")));
        golden("admin_d_un_centre_vide_nouveau_code", synthese(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", as(CENTRE_VIDE, "ADMIN")));

        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("0001-01-01 au 9999-12-31", resumePdf(synthese(CENTRE_A, "0001-01-01", "9999-12-31", "PDF", adminA())));
        cas.put("annee 10000", resumePdf(synthese(CENTRE_A, "+10000-01-01", "+10000-12-31", "PDF", adminA())));
        cas.put("date-heure", resumePdf(synthese(CENTRE_A, "2026-02-01T00:00:00", "2026-02-28T23:59:59", "PDF", adminA())));
        cas.put("dates en entiers", resumePdf(doPost(FACTURATION + "/synthese/print",
                json("{'centerId':'" + CENTRE_A + "','periodStart':20260201,'periodEnd':20260228}"), adminA())));
        cas.put("format en nombre", resumePdf(doPost(FACTURATION + "/synthese/print",
                json("{'centerId':'" + CENTRE_A + "','periodStart':'2026-02-01','periodEnd':'2026-02-28','format':1}"), adminA())));
        cas.put("centre en majuscules", resumePdf(doPost(FACTURATION + "/synthese/print",
                json("{'centerId':'" + CENTRE_A.toString().toUpperCase() + "','periodStart':'2026-02-01','periodEnd':'2026-02-28'}"), adminA())));
        cas.put("centre sans tirets", resumePdf(doPost(FACTURATION + "/synthese/print",
                json("{'centerId':'" + CENTRE_A.toString().replace("-", "") + "','periodStart':'2026-02-01','periodEnd':'2026-02-28'}"), adminA())));
        cas.put("centre vide", resumePdf(doPost(FACTURATION + "/synthese/print",
                json("{'centerId':'','periodStart':'2026-02-01','periodEnd':'2026-02-28'}"), adminA())));
        cas.put("dates blanches", resumePdf(doPost(FACTURATION + "/synthese/print",
                json("{'centerId':'" + CENTRE_A + "','periodStart':' ','periodEnd':' '}"), adminA())));
        cas.put("tableau a la place de l'objet", resumePdf(doPost(FACTURATION + "/synthese/print", "[]", adminA())));
        goldenNode("periodes_extremes_et_formes_du_corps", mapper.valueToTree(cas));
    }

    @Test
    void les_types_de_contenu_les_methodes_et_les_acces_non_authentifies_de_la_route() throws Exception {
        String corps = json("{'centerId':'" + CENTRE_A + "','periodStart':'2026-02-01','periodEnd':'2026-02-28'}");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("text/plain", statutEtType(reponseBrute(post(java.net.URI.create(FACTURATION + "/synthese/print"))
                .contentType(MediaType.TEXT_PLAIN).content(corps).with(adminA()))));
        cas.put("xml", statutEtType(reponseBrute(post(java.net.URI.create(FACTURATION + "/synthese/print"))
                .contentType(MediaType.APPLICATION_XML).content("<a/>").with(adminA()))));
        cas.put("sans type de contenu", statutEtType(reponseBrute(post(java.net.URI.create(FACTURATION + "/synthese/print"))
                .content(corps).with(adminA()))));
        cas.put("Accept json sur un pdf", statutEtType(reponseBrute(post(java.net.URI.create(FACTURATION + "/synthese/print"))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(corps).with(adminA()))));
        cas.put("Accept pdf", statutEtType(reponseBrute(post(java.net.URI.create(FACTURATION + "/synthese/print"))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_PDF).content(corps).with(adminA()))));
        cas.put("PUT", statutEtType(reponseBrute(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put(java.net.URI.create(FACTURATION + "/synthese/print")).contentType(MediaType.APPLICATION_JSON).content(corps).with(adminA()))));
        cas.put("GET /synthese", statutEtType(doGet(FACTURATION + "/synthese", adminA())));
        cas.put("anonyme", statutEtType(doPostAnonyme(FACTURATION + "/synthese/print", corps)));
        goldenNode("types_de_contenu_et_methodes", mapper.valueToTree(cas));
    }

    // ------------------------------------------------------------------ audit

    @Test
    void l_impression_de_la_synthese_est_journalisee_comme_une_ecriture_y_compris_refusee() throws Exception {
        factureAvecLigne(AGENCE_1, "FAC-1", "2026-02-10", "100.00", "0.00", "100.00", "Forfait HD", "100.00");
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter, filtreDAudit).build();
        ecrivainDAudit.flush();
        jdbc.update("DELETE FROM audit_log WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);

        assertEquals(200, synthese(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", adminA()).status());
        assertEquals(400, synthese(CENTRE_A, "2026-03-01", "2026-02-01", "PDF", adminA()).status());
        assertEquals(403, synthese(CENTRE_B, "2026-02-01", "2026-02-28", "PDF", adminA()).status());
        assertEquals(403, synthese(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", as(CENTRE_A, "MEDECIN")).status());
        ecrivainDAudit.flush();

        ArrayNode lignes = mapper.createArrayNode();
        lignes.add("POST synthese/print suivi = " + AuditActionResolver.isTracked("POST", FACTURATION + "/synthese/print"));
        for (Map<String, Object> row : jdbc.queryForList("SELECT action_code, http_method, route_template, status_code, COUNT(*) AS n "
                + "FROM audit_log WHERE center_id IN (?, ?) GROUP BY action_code, http_method, route_template, status_code "
                + "ORDER BY route_template, http_method, status_code", CENTRE_A, CENTRE_B)) {
            lignes.add("journal : " + row);
        }
        ObjectNode racine = mapper.createObjectNode();
        racine.set("lignes", lignes);
        goldenNode("audit", racine);
        jdbc.update("DELETE FROM audit_log WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
    }

    // ------------------------------------------------------------------ outils

    private Resp reponseBrute(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requete) throws Exception {
        var r = mvc.perform(requete).andReturn().getResponse();
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(StandardCharsets.UTF_8), r.getContentAsByteArray(),
                r.getHeader("Content-Disposition"));
    }

    private ObjectNode resumePdf(Resp r) throws Exception {
        ObjectNode o = statutEtType(r);
        if (r.contentType() != null && r.contentType().contains("pdf")) {
            o.put("pages", pagesDuPdf(r.octets()));
            String texte = espacesSimples(texteDuPdf(r.octets()));
            int i = texte.indexOf("Periode:");
            o.put("periode", i < 0 ? "" : texte.substring(i, texte.indexOf('\n', i)).trim());
        } else {
            JsonNode n = null;
            try {
                n = mapper.readTree(r.raw());
            } catch (RuntimeException e) {
                o.put("corps", r.raw());
            }
            if (n != null) {
                o.put("code", n.path("code").asString(""));
            }
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
