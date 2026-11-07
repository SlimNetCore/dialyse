package com.hemodialyse.backend.characterization.dossiermedical.dm8;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM8, lot 3 : statistiques médicales du patient ({@code GET stats/medical}, {@code POST stats/medical/search}) et exports
 * ({@code GET stats/export}, {@code POST stats/export/search}, CSV et PDF) : agrégats et tendances lus dans {@code resultats_analyses} et
 * {@code prescriptions_medicales}, filtres de période, patient sans donnée, droits par profil, garde de centre, audit, aucune écriture.
 */
class LesStatistiquesMedicalesEtLesExportsDuPatientCharacterizationIntegrationTest extends Dm8CharacterizationSupport {

    @Override
    protected String dossier() {
        return "stats";
    }

    private UUID patientA;
    private UUID patientB;

    /** Trois bilans et trois prescriptions pour le patient A du centre A ; un bilan d'un autre patient ; un patient du centre B. */
    private void jeuDeDonnees() {
        patientA = patientDm8(CENTRE_A, "PAT_A");
        UUID autre = patientDm8(CENTRE_A, "PAT_AUTRE");
        patientB = patientDm8(CENTRE_B, "PAT_B");
        analyseEnBase(CENTRE_A, patientA, "AN_1", fixe(10), "hb_g_dl", "10", "kt_v_mensuel", "1.0", "ferritine_ng_ml", "300", "phosphore_mg_dl", "5.5",
                "calcium_mg_dl", "9");
        analyseEnBase(CENTRE_A, patientA, "AN_2", fixe(40), "hb_g_dl", "12", "ferritine_ng_ml", "500");
        analyseEnBase(CENTRE_A, patientA, "AN_3", fixe(70), "kt_v_mensuel", "1.5", "phosphore_mg_dl", "6");
        analyseEnBase(CENTRE_A, autre, "AN_AUTRE_PATIENT", fixe(50), "hb_g_dl", "99", "kt_v_mensuel", "9", "ferritine_ng_ml", "9999");
        analyseEnBase(CENTRE_B, patientB, "AN_B", fixe(20), "hb_g_dl", "8", "kt_v_mensuel", "0.8", "ferritine_ng_ml", "100");
        prescriptionEnBase(CENTRE_A, patientA, "PRESC_1", fixe(5), "4000", "SC", 1, "SEMAINE");
        prescriptionEnBase(CENTRE_A, patientA, "PRESC_2", fixe(35), "6000", "IV", 3, "SEMAINE");
        prescriptionEnBase(CENTRE_A, patientA, "PRESC_3", fixe(65), null, null, null, null);
        prescriptionEnBase(CENTRE_B, patientB, "PRESC_B", fixe(20), "2000", "SC", 1, "SEMAINE");
        jdbc.update("UPDATE prescriptions_medicales SET poids_sec_cible_kg = 68.0 WHERE id = ?", idStable("prescription", "PRESC_1-" + CENTRE_A));
    }

    private String medical(UUID patient, UUID centre, String suite) {
        return statsApi(patient, "medical") + "?centerId=" + centre + suite;
    }

    private String exportApi(UUID patient, UUID centre, String format, String suite) {
        return statsApi(patient, "export") + "?centerId=" + centre + (format == null ? "" : "&format=" + format) + suite;
    }

    private String recherche(UUID centre, UUID patient, LocalDate du, LocalDate au, String format) {
        return corps("centerId", centre, "patientId", patient, "dateFrom", du, "dateTo", au, "format", format);
    }

    /**
     * Les dates des tendances sont des {@code java.sql.Date} sérialisées en horodatage ISO (UTC) : on les ramène au jour du fuseau de la JVM,
     * qui est celui des fixtures, pour pouvoir les nommer.
     */
    private String jour(JsonNode n) {
        if (n.isMissingNode() || n.isNull()) return "-";
        if (n.isNumber()) return java.time.Instant.ofEpochMilli(n.asLong()).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
        try {
            return java.time.OffsetDateTime.parse(n.asString()).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate().toString();
        } catch (RuntimeException e) {
            return n.asString();
        }
    }

    /** Moyennes et dates des tendances : forme lisible d'une réponse de statistiques. */
    private String synthese(Resp r) {
        if (r.status() != 200) return refus(r);
        JsonNode c = corps(r);
        List<String> hb = new ArrayList<>();
        c.path("hbTrend").forEach(n -> hb.add(jour(n.path("DATE_PRELEVEMENT")) + ":" + n.path("HB_G_DL").asString() + "/" + n.path("KT_V_MENSUEL").asString()
                + "/" + n.path("FERRITINE_NG_ML").asString()));
        List<String> epo = new ArrayList<>();
        c.path("epoTrend").forEach(n -> epo.add(jour(n.path("DATE_PRESCRIPTION")) + ":" + n.path("EPO_DOSE_UI").asString() + " " + n.path("EPO_VOIE").asString()
                + " " + n.path("EPO_FREQUENCE_VALEUR").asString() + "/" + n.path("EPO_FREQUENCE_UNITE").asString()));
        return "avgHb=" + c.path("avgHbGDl").asString() + " avgKtV=" + c.path("avgKtV").asString() + " avgFerritine=" + c.path("avgFerritineNgMl").asString()
                + " ; hbTrend=" + hb + " ; epoTrend=" + epo;
    }

    private Map<String, Object> telechargement(MockHttpServletResponse r) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.getStatus());
        m.put("type", r.getContentType());
        m.put("disposition", r.getHeader("Content-Disposition"));
        String texte = r.getContentAsString(StandardCharsets.UTF_8);
        if (r.getStatus() == 200 && (r.getContentType() == null || !r.getContentType().contains("pdf"))) {
            m.put("lignes", List.of(texte.split("\n", -1)));
        } else if (r.getStatus() != 200) {
            m.put("corps", texte.isBlank() ? "" : refusBrut(r.getStatus(), texte));
        }
        return m;
    }

    private List<String> texte(byte[] pdf) throws Exception {
        List<String> lignes = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(pdf)) {
            for (String ligne : new PDFTextStripper().getText(doc).split("\\R")) {
                if (!ligne.isBlank()) lignes.add(ligne.strip().replaceAll("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}", "<HORODATAGE_MINUTE>"));
            }
        }
        return lignes;
    }

    @Test
    void les_moyennes_ignorent_les_valeurs_absentes_et_les_tendances_sont_triees_par_date_croissante() throws Exception {
        jeuDeDonnees();
        String avant = empreinteLecteurs();
        Resp r = doGet(medical(patientA, CENTRE_A, ""), medecinA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 reponse complete", reponse(r));
        m.put("02 en-tete Cache-Control", r.cacheControl());
        m.put("03 lecture synthetique", synthese(r));
        m.put("04 aucune ecriture", empreinteLecteurs().equals(avant));
        arbre("agregats", m);
    }

    @Test
    void les_bornes_de_periode_sont_incluses_et_filtrent_les_analyses_et_les_prescriptions() throws Exception {
        jeuDeDonnees();
        Map<String, Object> m = new LinkedHashMap<>();
        String[][] cas = {
                {"sans periode", ""},
                {"depuis le jour du premier bilan (borne incluse)", "&from=" + fixe(10)},
                {"depuis le lendemain du premier bilan", "&from=" + fixe(11)},
                {"jusqu'au jour du second bilan (borne incluse)", "&to=" + fixe(40)},
                {"jusqu'a la veille du second bilan", "&to=" + fixe(39)},
                {"du 11 au 39 (aucun bilan, une prescription)", "&from=" + fixe(11) + "&to=" + fixe(39)},
                {"un seul jour avec un bilan", "&from=" + fixe(10) + "&to=" + fixe(10)},
                {"periode apres toutes les donnees", "&from=" + fixe(200)},
                {"from posterieur a to", "&from=" + fixe(40) + "&to=" + fixe(10)},
                {"date illisible", "&from=pas-une-date"},
        };
        int i = 0;
        for (String[] c : cas) {
            m.put(String.format("%02d %s", ++i, c[0]), synthese(doGet(medical(patientA, CENTRE_A, c[1]), medecinA())));
        }
        arbre("periodes", m);
    }

    @Test
    void sans_donnee_les_moyennes_valent_zero_et_les_tendances_sont_vides_meme_pour_un_patient_inconnu_ou_d_un_autre_centre() throws Exception {
        jeuDeDonnees();
        UUID sans = patientDm8(CENTRE_A, "PAT_SANS");
        UUID valeursNulles = patientDm8(CENTRE_A, "PAT_NULLES");
        analyseEnBase(CENTRE_A, valeursNulles, "AN_NULLE", fixe(10), "phosphore_mg_dl", "5");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 patient sans donnee", reponse(doGet(medical(sans, CENTRE_A, ""), medecinA())));
        m.put("02 patient inconnu", synthese(doGet(medical(idStable("patient", "inconnu"), CENTRE_A, ""), medecinA())));
        m.put("03 patient du centre B lu par la session A (centre A demande)", synthese(doGet(medical(patientB, CENTRE_A, ""), medecinA())));
        m.put("04 patient du centre A lu par la session B (centre B demande)", synthese(doGet(medical(patientA, CENTRE_B, ""), medecinB())));
        m.put("05 bilan sans aucune des trois valeurs moyennees", synthese(doGet(medical(valeursNulles, CENTRE_A, ""), medecinA())));
        m.put("06 centerId absent", refus(doGet(statsApi(patientA, "medical"), medecinA())));
        m.put("07 centerId illisible", refus(doGet(statsApi(patientA, "medical") + "?centerId=abc", medecinA())));
        arbre("sans_donnees", m);
    }

    @Test
    void la_recherche_par_corps_rend_la_meme_chose_que_la_lecture_et_valide_son_corps() throws Exception {
        jeuDeDonnees();
        Map<String, Object> m = new LinkedHashMap<>();
        Resp get = doGet(medical(patientA, CENTRE_A, "&from=" + fixe(10) + "&to=" + fixe(40)), medecinA());
        Resp post = doPost(PATIENTS_API + "/stats/medical/search", recherche(CENTRE_A, patientA, fixe(10), fixe(40), null), medecinA());
        m.put("01 recherche", synthese(post));
        m.put("02 meme corps que la lecture", get.raw().equals(post.raw()));
        m.put("03 type et format du corps de recherche ignores", synthese(doPost(PATIENTS_API + "/stats/medical/search",
                corps("centerId", CENTRE_A, "patientId", patientA, "type", "N_IMPORTE_QUOI", "format", "pdf"), medecinA())));
        m.put("04 centerId absent", refus(doPost(PATIENTS_API + "/stats/medical/search", corps("patientId", patientA), medecinA())));
        m.put("05 patientId absent", refus(doPost(PATIENTS_API + "/stats/medical/search", corps("centerId", CENTRE_A), medecinA())));
        m.put("06 dateFrom posterieure a dateTo", refus(doPost(PATIENTS_API + "/stats/medical/search", recherche(CENTRE_A, patientA, fixe(40), fixe(10), null), medecinA())));
        m.put("07 centre d'une autre session", refus(doPost(PATIENTS_API + "/stats/medical/search", recherche(CENTRE_B, patientB, null, null, null), medecinA())));
        m.put("08 corps illisible", refus(doPost(PATIENTS_API + "/stats/medical/search", "{pas du json", medecinA())));
        m.put("09 corps absent", refus(doPost(PATIENTS_API + "/stats/medical/search", "", medecinA())));
        arbre("recherche", m);
    }

    @Test
    void l_export_csv_reprend_six_colonnes_entre_guillemets_trie_par_date_et_ne_rend_qu_un_en_tete_sans_donnee() throws Exception {
        jeuDeDonnees();
        UUID sans = patientDm8(CENTRE_A, "PAT_SANS");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 export complet (ADMIN)", telechargement(binaire(exportApi(patientA, CENTRE_A, "csv", ""), adminA2())));
        m.put("02 sur une periode", telechargement(binaire(exportApi(patientA, CENTRE_A, "csv", "&from=" + fixe(11) + "&to=" + fixe(60)), adminA2())));
        m.put("03 patient sans donnee (en-tete seul)", telechargement(binaire(exportApi(sans, CENTRE_A, "csv", ""), adminA2())));
        m.put("04 format en majuscules", telechargement(binaire(exportApi(patientA, CENTRE_A, "CSV", "&from=" + fixe(60)), adminA2())).get("lignes"));
        m.put("05 format entoure d'espaces", telechargement(binaire(exportApi(patientA, CENTRE_A, "%20csv%20", "&from=" + fixe(60)), adminA2())).get("lignes"));
        m.put("06 format inconnu", telechargement(binaire(exportApi(patientA, CENTRE_A, "xml", ""), adminA2())));
        m.put("07 format absent", telechargement(binaire(exportApi(patientA, CENTRE_A, null, ""), adminA2())));
        m.put("08 format vide", telechargement(binaire(exportApi(patientA, CENTRE_A, "", ""), adminA2())));
        m.put("09 from posterieur a to", telechargement(binaire(exportApi(patientA, CENTRE_A, "csv", "&from=" + fixe(40) + "&to=" + fixe(10)), adminA2())));
        arbre("csv", m);
    }

    @Test
    void l_export_pdf_reprend_les_statistiques_paramedicales_et_medicales_en_deux_tableaux() throws Exception {
        jeuDeDonnees();
        seanceAvecVolet(CENTRE_A, patientA, fixe(20), "70.0", "68.5", "130/80", null);
        seanceAvecVolet(CENTRE_A, patientA, fixe(30), "71.0", "69.0", "bizarre", "1800");
        UUID sans = patientDm8(CENTRE_A, "PAT_SANS");
        // Le nom du centre figure en en-tete et pied de page : on le fixe (la ligne existe peut-etre deja, posee par d'autres filets).
        var existants = jdbc.queryForList("SELECT name FROM centers WHERE id = ?", CENTRE_A);
        if (existants.isEmpty()) {
            jdbc.update("INSERT INTO centers (id, code, name, actif) VALUES (?,?,?,TRUE)", CENTRE_A, "ZT-DM8-A", "Centre DM8");
        } else {
            jdbc.update("UPDATE centers SET name = ? WHERE id = ?", "Centre DM8", CENTRE_A);
        }
        try {
            lirePdf(sans);
        } finally {
            if (existants.isEmpty()) {
                jdbc.update("DELETE FROM centers WHERE id = ?", CENTRE_A);
            } else {
                jdbc.update("UPDATE centers SET name = ? WHERE id = ?", existants.get(0).get("NAME"), CENTRE_A);
            }
        }
    }

    private void lirePdf(UUID sans) throws Exception {
        String avant = empreinteLecteurs();
        MockHttpServletResponse r = binaire(exportApi(patientA, CENTRE_A, "pdf", ""), adminA2());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 statut", r.getStatus());
        m.put("02 type", r.getContentType());
        m.put("03 en-tete de telechargement", r.getHeader("Content-Disposition"));
        m.put("04 signature du fichier", new String(r.getContentAsByteArray(), 0, 5, StandardCharsets.ISO_8859_1));
        m.put("05 texte", texte(r.getContentAsByteArray()));
        MockHttpServletResponse periode = binaire(exportApi(patientA, CENTRE_A, "pdf", "&from=" + fixe(25) + "&to=" + fixe(45)), adminA2());
        m.put("06 texte sur une periode", texte(periode.getContentAsByteArray()));
        m.put("07 patient sans donnee", texte(binaire(exportApi(sans, CENTRE_A, "pdf", ""), adminA2()).getContentAsByteArray()));
        m.put("08 aucune ecriture", empreinteLecteurs().equals(avant));
        arbre("pdf", m);
    }

    @Test
    void la_recherche_d_export_par_corps_suit_les_memes_regles_que_l_export_par_lecture() throws Exception {
        jeuDeDonnees();
        Map<String, Object> m = new LinkedHashMap<>();
        String api = PATIENTS_API + "/stats/export/search";
        MockHttpServletResponse csv = binairePost(api, recherche(CENTRE_A, patientA, fixe(11), fixe(60), "csv"), adminA2());
        m.put("01 csv", telechargement(csv));
        m.put("02 meme contenu que l'export par lecture", csv.getContentAsString(StandardCharsets.UTF_8).equals(binaire(exportApi(patientA, CENTRE_A, "csv",
                "&from=" + fixe(11) + "&to=" + fixe(60)), adminA2()).getContentAsString(StandardCharsets.UTF_8)));
        MockHttpServletResponse pdf = binairePost(api, recherche(CENTRE_A, patientA, null, null, " PDF "), adminA2());
        m.put("03 pdf (format en majuscules et espaces)", List.of(pdf.getStatus(), String.valueOf(pdf.getContentType())));
        m.put("04 format absent", telechargement(binairePost(api, recherche(CENTRE_A, patientA, null, null, null), adminA2())));
        m.put("05 format inconnu", telechargement(binairePost(api, recherche(CENTRE_A, patientA, null, null, "xml"), adminA2())));
        m.put("06 dateFrom posterieure a dateTo", telechargement(binairePost(api, recherche(CENTRE_A, patientA, fixe(40), fixe(10), "csv"), adminA2())));
        m.put("07 centre d'une autre session", telechargement(binairePost(api, recherche(CENTRE_B, patientB, null, null, "csv"), adminA2())));
        m.put("08 patientId absent", telechargement(binairePost(api, corps("centerId", CENTRE_A, "format", "csv"), adminA2())));
        arbre("recherche_export", m);
    }

    @Test
    void les_statistiques_sont_ouvertes_a_quatre_roles_et_les_exports_reserves_a_l_administrateur() throws Exception {
        jeuDeDonnees();
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm8().entrySet()) {
            Map<String, String> routes = new LinkedHashMap<>();
            routes.put("01 GET stats/medical", statutEtCode(doGet(medical(patientA, CENTRE_A, ""), p.getValue())));
            routes.put("02 POST stats/medical/search", statutEtCode(doPost(PATIENTS_API + "/stats/medical/search", recherche(CENTRE_A, patientA, null, null, null),
                    p.getValue())));
            routes.put("03 GET stats/export csv", statutEtCode(doGet(exportApi(patientA, CENTRE_A, "csv", ""), p.getValue())));
            routes.put("04 POST stats/export/search csv", statutEtCode(doPost(PATIENTS_API + "/stats/export/search", recherche(CENTRE_A, patientA, null, null, "csv"),
                    p.getValue())));
            routes.put("05 GET stats/export format inconnu", statutEtCode(doGet(exportApi(patientA, CENTRE_A, "xml", ""), p.getValue())));
            profils.put(p.getKey(), regrouper(routes));
        }
        Map<String, String> anonyme = new LinkedHashMap<>();
        anonyme.put("lecture", statutEtCode(appel("GET", medical(patientA, CENTRE_A, ""), null, null)));
        anonyme.put("recherche", statutEtCode(appel("POST", PATIENTS_API + "/stats/medical/search", recherche(CENTRE_A, patientA, null, null, null), null)));
        anonyme.put("export", statutEtCode(appel("GET", exportApi(patientA, CENTRE_A, "csv", ""), null, null)));
        profils.put("anonyme", anonyme);
        arbre("profils", profils);
    }

    @Test
    void le_centre_demande_doit_etre_celui_de_la_session_et_un_patient_d_un_autre_centre_ne_rend_rien() throws Exception {
        jeuDeDonnees();
        String avant = empreinteLecteurs();
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> a = new LinkedHashMap<>();
        a.put("01 GET stats/medical centerId=B", refus(doGet(medical(patientB, CENTRE_B, ""), medecinA())));
        a.put("02 POST stats/medical/search centre B", refus(doPost(PATIENTS_API + "/stats/medical/search", recherche(CENTRE_B, patientB, null, null, null), medecinA())));
        a.put("03 GET stats/export centerId=B (ADMIN)", refus(doGet(exportApi(patientB, CENTRE_B, "csv", ""), adminA2())));
        a.put("04 POST stats/export/search centre B (ADMIN)", refus(doPost(PATIENTS_API + "/stats/export/search", recherche(CENTRE_B, patientB, null, null, "csv"), adminA2())));
        a.put("05 centerId inconnu", refus(doGet(medical(patientA, idStable("centre", "inconnu"), ""), medecinA())));
        m.put("01 session A vise le centre B", a);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("01 MEDECIN sans centre de session", refus(doGet(medical(patientA, CENTRE_A, ""), as(null, "MEDECIN"))));
        b.put("02 ADMIN sans centre de session : export", refus(doGet(exportApi(patientA, CENTRE_A, "csv", ""), as(null, "ADMIN"))));
        b.put("03 SUPERADMIN + ADMIN (session A) : export du centre B", refus(doGet(exportApi(patientB, CENTRE_B, "csv", ""), as(CENTRE_A, "SUPERADMIN", "ADMIN"))));
        b.put("04 session B lit le patient du centre A avec son propre centre : aucune donnee", synthese(doGet(medical(patientA, CENTRE_B, ""), medecinB())));
        b.put("05 session B exporte le patient du centre A avec son propre centre (ADMIN B) : en-tete seul",
                telechargement(binaire(exportApi(patientA, CENTRE_B, "csv", ""), as(CENTRE_B, "ADMIN"))).get("lignes"));
        m.put("02 sessions particulieres", b);
        m.put("03 aucune ecriture", empreinteLecteurs().equals(avant));
        arbre("cloisonnement", m);
    }

    @Test
    void les_lectures_ne_sont_pas_journalisees_mais_les_recherches_par_corps_le_sont_comme_des_creations() throws Exception {
        jeuDeDonnees();
        monterLAudit();
        doGet(medical(patientA, CENTRE_A, ""), medecinA());
        doGet(exportApi(patientA, CENTRE_A, "csv", ""), adminA2());
        doGet(exportApi(patientA, CENTRE_A, "pdf", ""), adminA2());
        doGet(exportApi(patientA, CENTRE_A, "csv", ""), medecinA());
        doPost(PATIENTS_API + "/stats/medical/search", recherche(CENTRE_A, patientA, null, null, null), medecinA());
        doPost(PATIENTS_API + "/stats/medical/search", recherche(CENTRE_A, patientA, null, null, null), as(CENTRE_A, "SECRETAIRE"));
        doPost(PATIENTS_API + "/stats/export/search", recherche(CENTRE_A, patientA, null, null, "csv"), adminA2());
        doPost(PATIENTS_API + "/stats/export/search", recherche(CENTRE_A, patientA, null, null, "csv"), medecinA());
        doPost(PATIENTS_API + "/stats/medical/search", recherche(CENTRE_B, patientB, null, null, null), medecinA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }
}
