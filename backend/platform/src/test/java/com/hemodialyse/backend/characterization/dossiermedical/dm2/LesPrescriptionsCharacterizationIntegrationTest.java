package com.hemodialyse.backend.characterization.dossiermedical.dm2;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM2, lot 1 (prescriptions) : {@code /api/v1/patients/{id}/prescriptions} (liste, en vigueur, recherche, création, modification,
 * suppression), cibles de dialyse et traitement de l'anémie (EPO, fer). Données fictives, dates fixes nommées {@code <D+n>}.
 */
class LesPrescriptionsCharacterizationIntegrationTest extends PrescriptionsCharacterizationSupport {

    @Override
    protected String dossier() {
        return "prescriptions";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm2(CENTRE_A, "PAT_A");
        patientB = patientDm2(CENTRE_B, "PAT_B");
    }

    private String corps(UUID centre, LocalDate date, String champs) {
        return "{" + (centre == null ? "" : "\"centerId\":\"" + centre + "\",") + (date == null ? "" : "\"datePrescription\":\"" + date + "\",")
                + "\"qbCible\":300" + (champs == null ? "" : "," + champs) + "}";
    }

    private static final String CIBLES = "\"qbCible\":320,\"qdCible\":500,\"ufMaxMl\":3000,\"dureeCibleMin\":240,\"poidsSecCibleKg\":72.456,"
            + "\"typeDialyseurPrescrit\":\"FX60\",\"anticoagTypePrescrit\":\"HEPARINE\"";

    private Map<String, Object> lisible(JsonNode p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.path("id").asString());
        m.put("patientId", p.path("patientId").asString());
        m.put("centerId", p.path("centerId").asString());
        m.put("datePrescription", p.path("datePrescription").asString());
        for (String cle : List.of("qbCible", "qdCible", "ufMaxMl", "dureeCibleMin", "poidsSecCibleKg", "typeDialyseurPrescrit", "anticoagTypePrescrit",
                "epoArticleCode", "epoArticleLibelle", "epoDoseUi", "epoVoie", "epoFrequenceValeur", "epoFrequenceUnite", "ferArticleCode",
                "ferArticleLibelle", "ferDoseMg", "ferVoie", "ferFrequenceValeur", "ferFrequenceUnite")) {
            if (!p.path(cle).isNull() && !p.path(cle).isMissingNode()) m.put(cle, p.path(cle).asString());
        }
        return m;
    }

    private Map<String, Object> page(UUID patient, String parametres, RequestPostProcessor who) throws Exception {
        Resp r = doGet(prescriptionsApi(patient) + (parametres.isEmpty() ? "" : "?" + parametres), who);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", statutEtCode(r));
        if (r.status() == 200) {
            JsonNode c = corps(r);
            m.put("total", c.path("total").asInt());
            m.put("page", c.path("page").asInt());
            m.put("size", c.path("size").asInt());
            List<Object> items = new ArrayList<>();
            c.path("items").forEach(i -> items.add(i.path("datePrescription").asString() + " " + i.path("id").asString()));
            m.put("items", items);
        }
        return m;
    }

    // ------------------------------------------------------------------ création

    @Test
    void une_prescription_enregistre_les_cibles_de_dialyse_arrondit_le_poids_sec_et_previent_le_centre() throws Exception {
        deuxCentres();
        monterLAudit();
        Resp r = doPost(prescriptionsApi(patientA), corps(CENTRE_A, fixe(5), CIBLES + ",\"medecinId\":\"" + idStable("medecin", "M1") + "\""),
                as(CENTRE_A, "MEDECIN"));
        aliasNommes.put(idStable("medecin", "M1").toString(), "<MEDECIN_FICTIF>");
        ecriture("reponse", r);
        etatDesPrescriptions("etat");
        audit("audit");
        notifications("notifications");
        diffusionsCapturees("diffusions");
    }

    @Test
    void sans_date_la_prescription_porte_le_jour_d_enregistrement_et_tout_est_facultatif() throws Exception {
        deuxCentres();
        Resp r = doPost(prescriptionsApi(patientA), "{\"centerId\":\"" + CENTRE_A + "\"}", as(CENTRE_A, "MEDECIN"));
        assertThat(r.status()).isEqualTo(200);
        String date = jdbc.queryForObject("SELECT CAST(date_prescription AS VARCHAR) FROM prescriptions_medicales WHERE center_id = ?",
                String.class, CENTRE_A);
        // Le jour par défaut est lu sur l'horloge de la JVM : jour plausible contrôlé, puis masqué.
        assertThat(new HashSet<>(List.of(LocalDate.now().toString(), LocalDate.now(ZoneId.of("Africa/Algiers")).toString(),
                LocalDate.now(ZoneId.of("UTC")).toString()))).contains(date);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status());
        m.put("corps", corps(r).properties().stream().map(Map.Entry::getKey).sorted().toList());
        m.put("date par defaut", "<DATE_DU_JOUR>");
        arbre("reponse", m);
    }

    @Test
    void le_poids_sec_est_borne_de_20_a_300_kg_et_arrondi_au_centieme() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        for (String poids : new String[]{"19.99", "20", "20.00", "72.455", "72.454", "299.999", "300", "300.01", "-5", "0"}) {
            Resp r = doPost(prescriptionsApi(patientA), corps(CENTRE_A, fixe(1), "\"poidsSecCibleKg\":" + poids), as(CENTRE_A, "MEDECIN"));
            String stocke = r.status() == 200 ? jdbc.queryForObject("SELECT CAST(poids_sec_cible_kg AS VARCHAR) FROM prescriptions_medicales WHERE id = ?",
                    String.class, UUID.fromString(corps(r).path("id").asString())) : "-";
            cas.put(poids, statutEtCode(r) + " -> " + stocke);
        }
        arbre("bornes", cas);
    }

    @Test
    void les_entrees_illisibles_ou_hors_bornes_sont_refusees() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        String[][] corps = {
                {"corps vide", ""},
                {"corps mal forme", "{pas du json"},
                {"date illisible", "{\"datePrescription\":\"demain\"}"},
                {"unite de frequence inconnue", "{\"epoFrequenceUnite\":\"ANNEES\"}"},
                {"medecinId illisible", "{\"medecinId\":\"dr-fictif\"}"},
                {"centerId illisible", "{\"centerId\":\"xx\"}"},
                {"doses et cibles negatives acceptees", "{\"qbCible\":-1,\"qdCible\":-5,\"ufMaxMl\":-100,\"dureeCibleMin\":-60,\"epoDoseUi\":-4000}"},
                {"date future acceptee", "{\"datePrescription\":\"2099-01-01\"}"},
                {"type de dialyseur long (> 100)", "{\"typeDialyseurPrescrit\":\"" + "X".repeat(101) + "\"}"},
                {"anticoagulant long (> 20)", "{\"anticoagTypePrescrit\":\"" + "X".repeat(21) + "\"}"},
        };
        for (String[] c : corps) {
            cas.put(c[0], statutEtCode(doPost(prescriptionsApi(patientA), c[1], as(CENTRE_A, "MEDECIN"))));
        }
        cas.put("identifiant de patient illisible", statutEtCode(doPost(PATIENTS_API + "/pas-un-uuid/prescriptions", "{}", as(CENTRE_A, "MEDECIN"))));
        cas.put("patient inconnu accepte", statutEtCode(doPost(prescriptionsApi(idStable("patient", "INCONNU")), "{}", as(CENTRE_A, "MEDECIN"))));
        arbre("erreurs", cas);
        sql("enregistrees", "SELECT (CASE WHEN date_prescription = DATE '2099-01-01' THEN 'date future' ELSE 'jour par defaut' END) AS jour, qb_cible, qd_cible, uf_max_ml, duree_cible_min, epo_dose_ui "
                + "FROM prescriptions_medicales WHERE center_id = '" + CENTRE_A + "' ORDER BY qb_cible NULLS FIRST, date_prescription, epo_dose_ui");
    }

    // ------------------------------------------------------------------ anémie et catalogue

    @Test
    void le_traitement_de_l_anemie_renvoie_le_code_et_le_libelle_des_articles_du_catalogue_du_centre() throws Exception {
        deuxCentres();
        UUID epo = article(CENTRE_A, "EPO", "EPO-FICT", "EPO", "4000", "UI");
        UUID fer = article(CENTRE_A, "FER", "FER-FICT", "FER_INJECTABLE", "100", "mg");
        UUID epoB = article(CENTRE_B, "EPO_B", "EPO-B", "EPO", "2000", "UI");
        String anemie = "\"epoArticleId\":\"" + epo + "\",\"epoDoseUi\":8000,\"epoVoie\":\"SC\",\"epoFrequenceValeur\":1,\"epoFrequenceUnite\":\"SEMAINE\","
                + "\"ferArticleId\":\"" + fer + "\",\"ferDoseMg\":100,\"ferVoie\":\"IV\",\"ferFrequenceValeur\":2,\"ferFrequenceUnite\":\"MOIS\"";
        Resp cree = doPost(prescriptionsApi(patientA), corps(CENTRE_A, fixe(2), anemie), as(CENTRE_A, "MEDECIN"));
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("creation", cree.status());
        cas.put("lecture", page(patientA, "", as(CENTRE_A, "MEDECIN")));
        Resp liste = doGet(prescriptionsApi(patientA), as(CENTRE_A, "MEDECIN"));
        cas.put("prescription lue", lisible(corps(liste).path("items").get(0)));
        // Article d'un autre centre, article inconnu : acceptes sans erreur, sans code ni libelle.
        Resp autre = doPost(prescriptionsApi(patientA), corps(CENTRE_A, fixe(3), "\"epoArticleId\":\"" + epoB + "\",\"epoDoseUi\":4000,\"epoFrequenceValeur\":1,\"epoFrequenceUnite\":\"SEMAINE\","
                + "\"ferArticleId\":\"" + idStable("article", "INCONNU") + "\",\"ferDoseMg\":50"), as(CENTRE_A, "MEDECIN"));
        cas.put("articles d'un autre centre et inconnu", autre.status());
        cas.put("lecture des articles etrangers", lisible(corps(doGet(prescriptionsApi(patientA) + "?from=" + fixe(3), as(CENTRE_A, "MEDECIN"))).path("items").get(0)));
        arbre("catalogue", cas);
        etatDesPrescriptions("etat");
    }

    @Test
    void la_dose_prescrite_doit_se_convertir_dans_l_unite_de_stock_de_l_article() throws Exception {
        deuxCentres();
        UUID epo = article(CENTRE_A, "EPO", "EPO-FICT", "EPO", "4000", "UI");
        UUID epoSansDosage = article(CENTRE_A, "EPO_SANS", "EPO-SANS", "EPO", null, null);
        UUID fer = article(CENTRE_A, "FER", "FER-FICT", "FER_INJECTABLE", "100", "mg");
        Map<String, String> cas = new LinkedHashMap<>();
        String[][] essais = {
                {"EPO 8000 UI sur article dose 4000 UI", "\"epoArticleId\":\"" + epo + "\",\"epoDoseUi\":8000"},
                {"EPO 0 UI (ignoree)", "\"epoArticleId\":\"" + epo + "\",\"epoDoseUi\":0"},
                {"EPO sans dose", "\"epoArticleId\":\"" + epo + "\""},
                {"EPO sur article sans dosage (1:1 impossible)", "\"epoArticleId\":\"" + epoSansDosage + "\",\"epoDoseUi\":8000"},
                {"fer 200 mg sur article dosage 100 mg", "\"ferArticleId\":\"" + fer + "\",\"ferDoseMg\":200"},
                {"article EPO (UI) prescrit comme fer (mg) : unite incompatible","\"ferArticleId\":\"" + epo + "\",\"ferDoseMg\":200"},
                {"dose sans article", "\"epoDoseUi\":8000,\"ferDoseMg\":100"},
        };
        for (String[] e : essais) {
            cas.put(e[0], statutEtCode(doPost(prescriptionsApi(patientA), corps(CENTRE_A, fixe(1), e[1]), as(CENTRE_A, "MEDECIN"))));
        }
        arbre("conversion", cas);
        sql("enregistrees", "SELECT epo_dose_ui, fer_dose_mg, (CASE WHEN epo_article_id IS NULL THEN 'non' ELSE 'oui' END) AS epo_article, "
                + "(CASE WHEN fer_article_id IS NULL THEN 'non' ELSE 'oui' END) AS fer_article FROM prescriptions_medicales "
                + "WHERE center_id = '" + CENTRE_A + "' ORDER BY epo_dose_ui NULLS FIRST, fer_dose_mg NULLS FIRST, epo_article, fer_article");
    }

    // ------------------------------------------------------------------ modification

    @Test
    void la_modification_remplace_tout_le_contenu_et_reecrit_la_date_de_creation() throws Exception {
        deuxCentres();
        UUID p = prescriptionAnemie(CENTRE_A, patientA, "PRESC", fixe(1), idStable("article", "X"), 8000, 1, "SEMAINE", idStable("article", "Y"), 100, 2, "MOIS");
        jdbc.update("UPDATE prescriptions_medicales SET qb_cible = 280, poids_sec_cible_kg = 70.50, type_dialyseur_prescrit = 'FX80' WHERE id = ?", p);
        Resp r = doPut(prescriptionsApi(patientA) + "/" + p, "{\"centerId\":\"" + CENTRE_A + "\",\"datePrescription\":\"" + fixe(4) + "\",\"qbCible\":350}",
                as(CENTRE_A, "MEDECIN"));
        ecriture("reponse", r);
        etatDesPrescriptions("etat");
    }

    @Test
    void modifier_un_identifiant_inconnu_cree_la_prescription_avec_cet_identifiant() throws Exception {
        deuxCentres();
        UUID voulu = idStable("prescription", "VOULU");
        aliasNommes.put(voulu.toString(), "<VOULU>");
        Resp r = doPut(prescriptionsApi(patientA) + "/" + voulu, corps(CENTRE_A, fixe(2), null), as(CENTRE_A, "MEDECIN"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status());
        m.put("id rendu", corps(r).path("id").asString());
        arbre("reponse", m);
        etatDesPrescriptions("etat");
    }

    @Test
    void la_modification_d_une_prescription_d_un_autre_centre_la_reaffecte_au_centre_de_la_session() throws Exception {
        deuxCentres();
        UUID chezB = prescription(CENTRE_B, patientB, "CHEZ_B", fixe(1), 300, "80.00");
        UUID autrePatient = patientDm2(CENTRE_A, "PAT_A2");
        UUID chezA = prescription(CENTRE_A, autrePatient, "CHEZ_A2", fixe(2), 250, "65.00");
        // Un MEDECIN du centre A, avec l'identifiant de la prescription du centre B (et le patient du centre B dans l'URL).
        Resp vol = doPut(prescriptionsApi(patientB) + "/" + chezB, corps(CENTRE_A, fixe(9), "\"qbCible\":111"), as(CENTRE_A, "MEDECIN"));
        // Un MEDECIN avec l'identifiant d'une prescription d'un autre patient de son centre, sous l'URL du patient A.
        Resp deplace = doPut(prescriptionsApi(patientA) + "/" + chezA, corps(CENTRE_A, fixe(8), "\"qbCible\":222"), as(CENTRE_A, "MEDECIN"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("prescription du centre B modifiee par le centre A", vol.status());
        m.put("prescription du patient A2 modifiee sous le patient A", deplace.status());
        arbre("reponses", m);
        etatDesPrescriptions("etat");
    }

    // ------------------------------------------------------------------ suppression

    @Test
    void la_suppression_est_portee_par_identifiant_patient_et_centre_et_se_rejoue_sans_erreur() throws Exception {
        deuxCentres();
        UUID p1 = prescription(CENTRE_A, patientA, "P1", fixe(1), 300, null);
        UUID p2 = prescription(CENTRE_A, patientA, "P2", fixe(2), 310, null);
        UUID pb = prescription(CENTRE_B, patientB, "PB", fixe(3), 320, null);
        UUID autrePatient = patientDm2(CENTRE_A, "PAT_A2");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("sous le mauvais patient", statutEtCode(doDelete(prescriptionsApi(autrePatient) + "/" + p1, as(CENTRE_A, "MEDECIN"))));
        cas.put("prescription du centre B depuis le centre A", statutEtCode(doDelete(prescriptionsApi(patientB) + "/" + pb, as(CENTRE_A, "MEDECIN"))));
        cas.put("avec centerId du centre B (session A)", statutEtCode(doDelete(prescriptionsApi(patientB) + "/" + pb + "?centerId=" + CENTRE_B, as(CENTRE_A, "MEDECIN"))));
        cas.put("lignes restantes avant suppression valide", jdbc.queryForObject("SELECT COUNT(*) FROM prescriptions_medicales WHERE center_id IN (?, ?)", Integer.class, CENTRE_A, CENTRE_B));
        monterLAudit();
        cas.put("suppression valide", statutEtCode(doDelete(prescriptionsApi(patientA) + "/" + p1, as(CENTRE_A, "MEDECIN"))));
        cas.put("meme suppression rejouee", statutEtCode(doDelete(prescriptionsApi(patientA) + "/" + p1, as(CENTRE_A, "MEDECIN"))));
        cas.put("identifiant inconnu", statutEtCode(doDelete(prescriptionsApi(patientA) + "/" + idStable("prescription", "INCONNUE"), as(CENTRE_A, "MEDECIN"))));
        cas.put("identifiant illisible", statutEtCode(doDelete(prescriptionsApi(patientA) + "/pas-un-uuid", as(CENTRE_A, "MEDECIN"))));
        arbre("suppressions", cas);
        etatDesPrescriptions("etat");
        // Chaque appel valide notifie le centre, y compris la suppression d'une prescription inexistante.
        notifications("notifications");
        audit("audit");
        assertThat(p2).isNotNull();
    }

    // ------------------------------------------------------------------ lectures

    @Test
    void la_liste_est_triee_du_plus_recent_au_plus_ancien_filtree_par_dates_et_paginee() throws Exception {
        deuxCentres();
        UUID autre = patientDm2(CENTRE_A, "PAT_A2");
        for (int i = 1; i <= 5; i++) prescription(CENTRE_A, patientA, "P" + i, fixe(i * 2), 300 + i, null);
        prescription(CENTRE_A, autre, "AUTRE", fixe(20), 400, null);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("toutes (defauts)", page(patientA, "", as(CENTRE_A, "MEDECIN")));
        cas.put("page 0 taille 2", page(patientA, "page=0&size=2", as(CENTRE_A, "MEDECIN")));
        cas.put("page 2 taille 2", page(patientA, "page=2&size=2", as(CENTRE_A, "MEDECIN")));
        cas.put("page 9 hors limite", page(patientA, "page=9&size=2", as(CENTRE_A, "MEDECIN")));
        cas.put("depuis D+5 (inclus)", page(patientA, "from=" + fixe(6), as(CENTRE_A, "MEDECIN")));
        cas.put("jusqu'a D+6 (inclus)", page(patientA, "to=" + fixe(6), as(CENTRE_A, "MEDECIN")));
        cas.put("de D+4 a D+8 (bornes incluses)", page(patientA, "from=" + fixe(4) + "&to=" + fixe(8), as(CENTRE_A, "MEDECIN")));
        cas.put("from apres to", page(patientA, "from=" + fixe(8) + "&to=" + fixe(4), as(CENTRE_A, "MEDECIN")));
        cas.put("from = to", page(patientA, "from=" + fixe(6) + "&to=" + fixe(6), as(CENTRE_A, "MEDECIN")));
        arbre("liste", cas);
        Map<String, Object> bornes = new LinkedHashMap<>();
        for (String parametres : new String[]{"size=0", "size=-1", "page=-1", "size=1000", "size=abc", "page=abc", "from=demain", "centerId=xx"}) {
            Map<String, Object> m = page(patientA, parametres, as(CENTRE_A, "MEDECIN"));
            m.remove("items");
            bornes.put(parametres, m);
        }
        arbre("bornes", bornes);
    }

    @Test
    void la_recherche_par_corps_exige_le_centre_et_le_patient_et_borne_la_page() throws Exception {
        deuxCentres();
        for (int i = 1; i <= 3; i++) prescription(CENTRE_A, patientA, "P" + i, fixe(i), 300 + i, null);
        prescription(CENTRE_B, patientB, "PB", fixe(1), 500, null);
        Map<String, Object> cas = new LinkedHashMap<>();
        String base = "\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patientA + "\",\"page\":0,\"size\":2";
        cas.put("page 0 taille 2", recherche("{" + base + "}", as(CENTRE_A, "MEDECIN")));
        cas.put("filtre de dates", recherche("{" + base + ",\"dateFrom\":\"" + fixe(2) + "\",\"dateTo\":\"" + fixe(2) + "\"}", as(CENTRE_A, "ADMIN")));
        cas.put("dates inversees", recherche("{" + base + ",\"dateFrom\":\"" + fixe(3) + "\",\"dateTo\":\"" + fixe(1) + "\"}", as(CENTRE_A, "MEDECIN")));
        // Jackson 3 refuse un entier primitif absent : page et size sont donc obligatoires dans le corps (500 sinon, pas 400).
        cas.put("page et taille absentes", recherche("{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patientA + "\"}", as(CENTRE_A, "MEDECIN")));
        cas.put("taille absente", recherche("{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patientA + "\",\"page\":0}", as(CENTRE_A, "MEDECIN")));
        cas.put("taille 0", recherche("{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patientA + "\",\"page\":0,\"size\":0}", as(CENTRE_A, "MEDECIN")));
        cas.put("taille 200", recherche("{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patientA + "\",\"page\":0,\"size\":200}", as(CENTRE_A, "MEDECIN")));
        cas.put("taille 201", recherche("{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patientA + "\",\"page\":0,\"size\":201}", as(CENTRE_A, "MEDECIN")));
        cas.put("page negative", recherche("{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patientA + "\",\"size\":2,\"page\":-1}", as(CENTRE_A, "MEDECIN")));
        cas.put("centre absent", recherche("{\"patientId\":\"" + patientA + "\",\"page\":0,\"size\":2}", as(CENTRE_A, "MEDECIN")));
        cas.put("patient absent", recherche("{\"centerId\":\"" + CENTRE_A + "\",\"page\":0,\"size\":2}", as(CENTRE_A, "MEDECIN")));
        cas.put("centre B (session A)", recherche("{\"centerId\":\"" + CENTRE_B + "\",\"patientId\":\"" + patientB + "\",\"page\":0,\"size\":2}", as(CENTRE_A, "MEDECIN")));
        cas.put("patient B, centre A", recherche("{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patientB + "\",\"page\":0,\"size\":2}", as(CENTRE_A, "MEDECIN")));
        cas.put("corps vide", statutEtCode(doPost("/api/v1/patients/prescriptions/search", "", as(CENTRE_A, "MEDECIN"))));
        arbre("recherche", cas);
    }

    private Object recherche(String corps, RequestPostProcessor who) throws Exception {
        Resp r = doPost("/api/v1/patients/prescriptions/search", corps, who);
        if (r.status() != 200) return statutEtCode(r);
        JsonNode c = corps(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", 200);
        m.put("total", c.path("total").asInt());
        m.put("size", c.path("size").asInt());
        List<String> items = new ArrayList<>();
        c.path("items").forEach(i -> items.add(i.path("datePrescription").asString() + " qb=" + i.path("qbCible").asString()));
        m.put("items", items);
        return m;
    }

    @Test
    void la_prescription_en_vigueur_est_la_plus_recente_dont_la_date_n_est_pas_posterieure() throws Exception {
        deuxCentres();
        prescription(CENTRE_A, patientA, "P1", fixe(2), 301, "70.00");
        prescription(CENTRE_A, patientA, "P2", fixe(10), 302, "71.00");
        prescription(CENTRE_A, patientA, "P3", fixe(20), 303, "72.00");
        prescription(CENTRE_B, patientB, "PB", fixe(1), 399, "90.00");
        Map<String, Object> cas = new LinkedHashMap<>();
        for (int n : new int[]{1, 2, 9, 10, 15, 20, 400}) {
            cas.put("a la date D+" + n, active(patientA, "date=" + fixe(n), as(CENTRE_A, "INFIRMIER")));
        }
        // Sans date : « aujourd'hui » (jour de la JVM) ; les prescriptions fixes sont toutes passees, la plus recente est donc celle de D+20.
        cas.put("sans date", active(patientA, "", as(CENTRE_A, "MEDECIN")));
        cas.put("date illisible", active(patientA, "date=demain", as(CENTRE_A, "MEDECIN")));
        cas.put("patient du centre B vu par le centre A", active(patientB, "date=" + fixe(400), as(CENTRE_A, "MEDECIN")));
        cas.put("centre B demande par la session A", active(patientB, "centerId=" + CENTRE_B + "&date=" + fixe(400), as(CENTRE_A, "MEDECIN")));
        arbre("en_vigueur", cas);
    }

    private Object active(UUID patient, String parametres, RequestPostProcessor who) throws Exception {
        Resp r = doGet(prescriptionsApi(patient) + "/active" + (parametres.isEmpty() ? "" : "?" + parametres), who);
        if (r.status() != 200) return statutEtCode(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", 200);
        m.put("prescription", lisible(corps(r)));
        return m;
    }

    // ------------------------------------------------------------------ droits

    @Test
    void les_droits_par_profil_sur_les_six_routes() throws Exception {
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm2().entrySet()) {
            nettoyerDm2();
            deuxCentres();
            UUID existante = prescription(CENTRE_A, patientA, "P1", fixe(1), 300, null);
            UUID aSupprimer = prescription(CENTRE_A, patientA, "P2", fixe(2), 310, null);
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("liste", statutEtCode(doGet(prescriptionsApi(patientA), p.getValue())));
            ligne.put("en vigueur", statutEtCode(doGet(prescriptionsApi(patientA) + "/active?date=" + fixe(5), p.getValue())));
            ligne.put("recherche", statutEtCode(doPost("/api/v1/patients/prescriptions/search",
                    "{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patientA + "\",\"page\":0,\"size\":2}", p.getValue())));
            // Corps sans page : la deserialisation echoue AVANT le controle des droits (500 pour tous les profils, y compris refuses).
            ligne.put("recherche (corps sans page)", statutEtCode(doPost("/api/v1/patients/prescriptions/search",
                    "{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patientA + "\",\"size\":2}", p.getValue())));
            ligne.put("creation", statutEtCode(doPost(prescriptionsApi(patientA), corps(CENTRE_A, fixe(3), null), p.getValue())));
            ligne.put("modification", statutEtCode(doPut(prescriptionsApi(patientA) + "/" + existante, corps(CENTRE_A, fixe(4), null), p.getValue())));
            ligne.put("suppression", statutEtCode(doDelete(prescriptionsApi(patientA) + "/" + aSupprimer, p.getValue())));
            profils.put(p.getKey(), ligne);
        }
        nettoyerDm2();
        UUID anonyme = patientDm2(CENTRE_A, "PAT_ANON");
        Map<String, Object> ligne = new LinkedHashMap<>();
        ligne.put("liste", statutEtCode(doGetAnonymous(prescriptionsApi(anonyme))));
        ligne.put("creation", statutEtCode(appel("POST", prescriptionsApi(anonyme), "{}", null)));
        profils.put("anonyme", ligne);
        arbre("profils", profils);
    }

    @Test
    void le_cloisonnement_par_centre_repose_sur_le_centre_de_la_session() throws Exception {
        deuxCentres();
        prescription(CENTRE_B, patientB, "PB", fixe(1), 399, null);
        prescription(CENTRE_A, patientA, "PA", fixe(2), 301, null);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("liste du patient B par le centre A", page(patientB, "", as(CENTRE_A, "MEDECIN")));
        cas.put("liste avec centerId B (session A)", page(patientB, "centerId=" + CENTRE_B, as(CENTRE_A, "MEDECIN")));
        cas.put("liste du patient A par le centre B", page(patientA, "", as(CENTRE_B, "MEDECIN")));
        cas.put("creation avec centerId B (session A)", statutEtCode(doPost(prescriptionsApi(patientB), corps(CENTRE_B, fixe(3), null), as(CENTRE_A, "MEDECIN"))));
        cas.put("creation sans centre : centre de la session", statutEtCode(doPost(prescriptionsApi(patientA), corps(null, fixe(3), null), as(CENTRE_A, "MEDECIN"))));
        cas.put("MEDECIN sans centre de session", statutEtCode(doGet(prescriptionsApi(patientA), as(null, "MEDECIN"))));
        cas.put("SUPERADMIN + MEDECIN", statutEtCode(doGet(prescriptionsApi(patientB) + "?centerId=" + CENTRE_B, as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        arbre("cloisonnement", cas);
        etatDesPrescriptions("etat");
    }
}
