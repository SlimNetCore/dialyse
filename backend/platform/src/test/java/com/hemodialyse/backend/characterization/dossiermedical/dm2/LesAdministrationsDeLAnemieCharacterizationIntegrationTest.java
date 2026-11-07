package com.hemodialyse.backend.characterization.dossiermedical.dm2;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM2, lot 2 : {@code GET} et {@code POST /patients/{id}/administrations-anemie} (administrations réelles d'EPO et de fer injectable, par
 * l'infirmier ou le médecin). La sortie de stock liée à une séance est observée ailleurs (filet de la Séance et du Stock). Données fictives.
 */
class LesAdministrationsDeLAnemieCharacterizationIntegrationTest extends PrescriptionsCharacterizationSupport {

    @Override
    protected String dossier() {
        return "administrations";
    }

    private UUID patientA;
    private UUID patientB;

    private void decorAdministrations() {
        patientA = patientDm2(CENTRE_A, "PAT_A");
        patientB = patientDm2(CENTRE_B, "PAT_B");
    }

    private String api(UUID patient) {
        return PATIENTS_API + "/" + patient + "/administrations-anemie";
    }

    private String corps(UUID centre, String champs) {
        return "{" + (centre == null ? "" : "\"centerId\":\"" + centre + "\",") + champs + "}";
    }

    private static final String ADMINISTREE = "\"typeTraitement\":\"EPO\",\"molecule\":\"Epoetine fictive\",\"dose\":4000,\"uniteDose\":\"UI\","
            + "\"voie\":\"SC\",\"administree\":true";

    // ------------------------------------------------------------------ création

    @Test
    void une_administration_effective_est_enregistree_avec_sa_dose_et_previent_le_medecin() throws Exception {
        decorAdministrations();
        monterLAudit();
        Resp r = doPost(api(patientA), corps(CENTRE_A, ADMINISTREE + ",\"dateAdministration\":\"" + fixe(3) + "\",\"administrePar\":\"infirmier-fictif\""),
                as(CENTRE_A, "INFIRMIER"));
        ecriture("reponse", r);
        etatDesAdministrations("etat");
        audit("audit");
        notifications("notifications");
        diffusionsCapturees("diffusions");
    }

    @Test
    void une_administration_non_faite_exige_un_motif_et_perd_sa_dose() throws Exception {
        decorAdministrations();
        Resp r = doPost(api(patientA), corps(CENTRE_A, "\"typeTraitement\":\"FER_INJECTABLE\",\"molecule\":\"Fer fictif\",\"dose\":100,\"uniteDose\":\"mg\","
                + "\"administree\":false,\"motifNonAdministration\":\"Patient absent\",\"articleId\":\"" + idStable("article", "X")
                + "\",\"quantiteArticle\":1,\"dateAdministration\":\"" + fixe(3) + "\""), as(CENTRE_A, "MEDECIN"));
        ecriture("reponse", r);
        etatDesAdministrations("etat");
    }

    @Test
    void les_regles_de_saisie_de_l_administration() throws Exception {
        decorAdministrations();
        String date = ",\"dateAdministration\":\"" + fixe(3) + "\"";
        Map<String, String> cas = new LinkedHashMap<>();
        String[][] essais = {
                {"administree sans dose", "\"typeTraitement\":\"EPO\",\"administree\":true" + date},
                {"dose nulle", "\"typeTraitement\":\"EPO\",\"dose\":0,\"uniteDose\":\"UI\",\"administree\":true" + date},
                {"dose negative", "\"typeTraitement\":\"EPO\",\"dose\":-5,\"uniteDose\":\"UI\",\"administree\":true" + date},
                {"dose sans unite", "\"typeTraitement\":\"EPO\",\"dose\":4000,\"administree\":true" + date},
                {"dose avec unite blanche", "\"typeTraitement\":\"EPO\",\"dose\":4000,\"uniteDose\":\" \",\"administree\":true" + date},
                {"non administree sans motif", "\"typeTraitement\":\"EPO\",\"administree\":false" + date},
                {"non administree avec motif blanc", "\"typeTraitement\":\"EPO\",\"administree\":false,\"motifNonAdministration\":\"  \"" + date},
                {"non administree avec motif", "\"typeTraitement\":\"EPO\",\"administree\":false,\"motifNonAdministration\":\"Refus du patient\"" + date},
                {"date absente", "\"typeTraitement\":\"EPO\",\"dose\":4000,\"uniteDose\":\"UI\",\"administree\":true"},
                {"type absent", "\"dose\":4000,\"uniteDose\":\"UI\",\"administree\":true" + date},
                {"type inconnu", "\"typeTraitement\":\"VITAMINE\",\"dose\":4000,\"uniteDose\":\"UI\",\"administree\":true" + date},
                {"type en minuscules", "\"typeTraitement\":\"epo\",\"dose\":4000,\"uniteDose\":\"UI\",\"administree\":true" + date},
                {"champ administree absent (booleen primitif)", "\"typeTraitement\":\"EPO\",\"dose\":4000,\"uniteDose\":\"UI\"" + date},
                {"dose en unite de fer pour de l'EPO (acceptee : aucune coherence verifiee)", "\"typeTraitement\":\"EPO\",\"dose\":100,\"uniteDose\":\"mg\",\"administree\":true" + date},
                {"date future acceptee", "\"typeTraitement\":\"EPO\",\"dose\":4000,\"uniteDose\":\"UI\",\"administree\":true,\"dateAdministration\":\"2099-01-01\""},
                {"prescription et seance quelconques acceptees (sans article)", "\"typeTraitement\":\"EPO\",\"dose\":4000,\"uniteDose\":\"UI\",\"administree\":true"
                        + ",\"prescriptionMedicaleId\":\"" + idStable("prescription", "INCONNUE") + "\",\"seanceId\":\"" + idStable("seance", "INCONNUE") + "\"" + date},
                {"date illisible", "\"typeTraitement\":\"EPO\",\"dose\":4000,\"uniteDose\":\"UI\",\"administree\":true,\"dateAdministration\":\"demain\""},
                {"molecule trop longue (> 100)", "\"typeTraitement\":\"EPO\",\"molecule\":\"" + "M".repeat(101) + "\",\"dose\":4000,\"uniteDose\":\"UI\",\"administree\":true" + date},
        };
        for (String[] e : essais) {
            cas.put(e[0], statutEtCode(doPost(api(patientA), corps(CENTRE_A, e[1]), as(CENTRE_A, "INFIRMIER"))));
        }
        cas.put("corps vide", statutEtCode(doPost(api(patientA), "", as(CENTRE_A, "INFIRMIER"))));
        cas.put("identifiant de patient illisible", statutEtCode(doPost(PATIENTS_API + "/pas-un-uuid/administrations-anemie", "{}", as(CENTRE_A, "INFIRMIER"))));
        arbre("regles", cas);
        sql("enregistrees", "SELECT type_traitement, dose, unite_dose, administree, motif_non_administration, "
                + "(CASE WHEN date_administration = DATE '2099-01-01' THEN 'date future' ELSE 'date nommee' END) AS jour, "
                + "(CASE WHEN prescription_medicale_id IS NULL THEN 'non' ELSE 'oui' END) AS prescription, "
                + "(CASE WHEN seance_id IS NULL THEN 'non' ELSE 'oui' END) AS seance FROM administrations_anemie "
                + "WHERE center_id = '" + CENTRE_A + "' ORDER BY type_traitement, dose NULLS FIRST, unite_dose NULLS FIRST, administree, "
                + "motif_non_administration NULLS FIRST, jour, prescription");
    }

    @Test
    void la_quantite_d_article_est_deduite_de_la_fiche_et_jamais_prise_du_client() throws Exception {
        decorAdministrations();
        UUID epo = article(CENTRE_A, "EPO", "EPO-FICT", "EPO", "4000", "UI");
        UUID sansDosage = article(CENTRE_A, "SANS", "EPO-SANS", "EPO", null, null);
        UUID autreCentre = article(CENTRE_B, "EPO_B", "EPO-B", "EPO", "2000", "UI");
        String date = ",\"dateAdministration\":\"" + fixe(3) + "\"";
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("8000 UI sur article dose 4000 UI, quantite client 99 ignoree", statutEtCode(doPost(api(patientA), corps(CENTRE_A,
                "\"typeTraitement\":\"EPO\",\"dose\":8000,\"uniteDose\":\"UI\",\"administree\":true,\"articleId\":\"" + epo + "\",\"quantiteArticle\":99" + date),
                as(CENTRE_A, "INFIRMIER"))));
        cas.put("article sans dosage", statutEtCode(doPost(api(patientA), corps(CENTRE_A,
                "\"typeTraitement\":\"EPO\",\"dose\":8000,\"uniteDose\":\"UI\",\"administree\":true,\"articleId\":\"" + sansDosage + "\"" + date),
                as(CENTRE_A, "INFIRMIER"))));
        cas.put("unite de dose incompatible", statutEtCode(doPost(api(patientA), corps(CENTRE_A,
                "\"typeTraitement\":\"EPO\",\"dose\":8000,\"uniteDose\":\"mg\",\"administree\":true,\"articleId\":\"" + epo + "\"" + date),
                as(CENTRE_A, "INFIRMIER"))));
        cas.put("article d'un autre centre", statutEtCode(doPost(api(patientA), corps(CENTRE_A,
                "\"typeTraitement\":\"EPO\",\"dose\":4000,\"uniteDose\":\"UI\",\"administree\":true,\"articleId\":\"" + autreCentre + "\"" + date),
                as(CENTRE_A, "INFIRMIER"))));
        cas.put("article inconnu", statutEtCode(doPost(api(patientA), corps(CENTRE_A,
                "\"typeTraitement\":\"EPO\",\"dose\":4000,\"uniteDose\":\"UI\",\"administree\":true,\"articleId\":\"" + idStable("article", "INCONNU") + "\"" + date),
                as(CENTRE_A, "INFIRMIER"))));
        cas.put("quantite client conservee sans dose ? (administree exige une dose : refus)", statutEtCode(doPost(api(patientA), corps(CENTRE_A,
                "\"typeTraitement\":\"EPO\",\"administree\":true,\"articleId\":\"" + epo + "\",\"quantiteArticle\":3" + date), as(CENTRE_A, "INFIRMIER"))));
        arbre("conversion", cas);
        etatDesAdministrations("etat");
    }

    @Test
    void deux_administrations_identiques_sont_deux_lignes_sans_garde_contre_le_doublon() throws Exception {
        decorAdministrations();
        String corps = corps(CENTRE_A, ADMINISTREE + ",\"dateAdministration\":\"" + fixe(3) + "\"");
        List<Object> statuts = new ArrayList<>();
        statuts.add(doPost(api(patientA), corps, as(CENTRE_A, "INFIRMIER")).status());
        statuts.add(doPost(api(patientA), corps, as(CENTRE_A, "INFIRMIER")).status());
        arbre("rejeu", Map.of("statuts", statuts, "lignes", jdbc.queryForObject(
                "SELECT COUNT(*) FROM administrations_anemie WHERE center_id = ?", Integer.class, CENTRE_A)));
    }

    // ------------------------------------------------------------------ lecture

    @Test
    void la_liste_est_triee_par_date_decroissante_paginee_et_cloisonnee() throws Exception {
        decorAdministrations();
        for (int i = 1; i <= 5; i++) administration(CENTRE_A, patientA, "ADM" + i, i % 2 == 0 ? "EPO" : "FER_INJECTABLE", fixe(i * 2), i != 4, 1000 * i, "UI");
        administration(CENTRE_B, patientB, "ADM_B", "EPO", fixe(30), true, 4000, "UI");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("defauts", page(patientA, "", as(CENTRE_A, "INFIRMIER")));
        cas.put("page 1 taille 2", page(patientA, "page=1&size=2", as(CENTRE_A, "MEDECIN")));
        cas.put("page 9 hors limite", page(patientA, "page=9&size=2", as(CENTRE_A, "ADMIN")));
        cas.put("patient B par le centre A", page(patientB, "", as(CENTRE_A, "MEDECIN")));
        cas.put("centerId B (session A)", page(patientB, "centerId=" + CENTRE_B, as(CENTRE_A, "MEDECIN")));
        for (String p : new String[]{"size=0", "size=-1", "page=-1", "size=1000", "size=abc", "page=abc"}) {
            Map<String, Object> m = page(patientA, p, as(CENTRE_A, "MEDECIN"));
            m.remove("items");
            cas.put(p, m);
        }
        arbre("liste", cas);
    }

    private Map<String, Object> page(UUID patient, String parametres, RequestPostProcessor who) throws Exception {
        Resp r = doGet(api(patient) + (parametres.isEmpty() ? "" : "?" + parametres), who);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", statutEtCode(r));
        if (r.status() == 200) {
            JsonNode c = corps(r);
            m.put("total", c.path("total").asInt());
            m.put("page", c.path("page").asInt());
            m.put("size", c.path("size").asInt());
            List<String> items = new ArrayList<>();
            c.path("items").forEach(i -> items.add(i.path("dateAdministration").asString() + " " + i.path("typeTraitement").asString() + " "
                    + (i.path("administree").asBoolean() ? i.path("dose").asString() + i.path("uniteDose").asString() : "non administree")));
            m.put("items", items);
        }
        return m;
    }

    // ------------------------------------------------------------------ droits

    @Test
    void les_droits_par_profil_sur_la_lecture_et_la_saisie() throws Exception {
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm2().entrySet()) {
            nettoyerDm2();
            decorAdministrations();
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("liste", statutEtCode(doGet(api(patientA), p.getValue())));
            ligne.put("saisie", statutEtCode(doPost(api(patientA), corps(CENTRE_A, ADMINISTREE + ",\"dateAdministration\":\"" + fixe(3) + "\""), p.getValue())));
            profils.put(p.getKey(), ligne);
        }
        Map<String, Object> anonyme = new LinkedHashMap<>();
        anonyme.put("liste", statutEtCode(doGetAnonymous(api(idStable("patient", "ANON")))));
        anonyme.put("saisie", statutEtCode(appel("POST", api(idStable("patient", "ANON")), "{}", null)));
        profils.put("anonyme", anonyme);
        arbre("profils", profils);
    }

    @Test
    void le_cloisonnement_par_centre_repose_sur_le_centre_de_la_session() throws Exception {
        decorAdministrations();
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("saisie avec le centre B (session A)", statutEtCode(doPost(api(patientB), corps(CENTRE_B, ADMINISTREE + ",\"dateAdministration\":\"" + fixe(3) + "\""),
                as(CENTRE_A, "INFIRMIER"))));
        cas.put("saisie sans centre : centre de la session", statutEtCode(doPost(api(patientA), corps(null, ADMINISTREE + ",\"dateAdministration\":\"" + fixe(3) + "\""),
                as(CENTRE_A, "INFIRMIER"))));
        cas.put("saisie pour le patient du centre B, session A (aucun controle du patient)", statutEtCode(doPost(api(patientB),
                corps(CENTRE_A, ADMINISTREE + ",\"dateAdministration\":\"" + fixe(4) + "\""), as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER sans centre de session", statutEtCode(doGet(api(patientA), as(null, "INFIRMIER"))));
        arbre("cloisonnement", cas);
        etatDesAdministrations("etat");
    }
}
