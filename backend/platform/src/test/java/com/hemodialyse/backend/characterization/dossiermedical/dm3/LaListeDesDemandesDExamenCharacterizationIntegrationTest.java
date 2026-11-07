package com.hemodialyse.backend.characterization.dossiermedical.dm3;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM3, lot 3 (lecture) : {@code GET /api/v1/patients/{id}/demandes-examen} (pagination, tri, contenu).
 */
class LaListeDesDemandesDExamenCharacterizationIntegrationTest extends ExamensCharacterizationSupport {

    @Override
    protected String dossier() {
        return "liste";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm3(CENTRE_A, "PAT_A");
        patientB = patientDm3(CENTRE_B, "PAT_B");
    }

    private Map<String, Object> page(UUID patient, String parametres, RequestPostProcessor who) throws Exception {
        Resp r = doGet(demandesApi(patient) + (parametres.isEmpty() ? "" : "?" + parametres), who);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", statutEtCode(r));
        if (r.status() == 200) {
            JsonNode c = corps(r);
            m.put("total", c.path("total").asInt());
            m.put("page", c.path("page").asInt());
            m.put("size", c.path("size").asInt());
            List<Object> items = new ArrayList<>();
            c.path("items").forEach(i -> items.add(i.path("dateDemande").asString() + " " + i.path("motif").asString() + " "
                    + i.path("statut").asString()));
            m.put("items", items);
        }
        return m;
    }

    @Test
    void la_liste_est_triee_de_la_demande_la_plus_recente_a_la_plus_ancienne_et_paginee() throws Exception {
        deuxCentres();
        UUID autre = patientDm3(CENTRE_A, "PAT_A2");
        demande(CENTRE_A, patientA, "DEM_1", fixe(1), "BIOLOGIE", "DEMANDE", false, "motif 1", null);
        demande(CENTRE_A, patientA, "DEM_2", fixe(4), "IMAGERIE", "PRELEVE", true, "motif 2", null);
        demande(CENTRE_A, patientA, "DEM_3", fixe(2), "FONCTIONNEL", "RESULTAT_DISPONIBLE", false, "motif 3", null);
        demande(CENTRE_A, patientA, "DEM_4", fixe(5), "ANAPATH", "ANNULE", false, "motif 4", null);
        demande(CENTRE_A, patientA, "DEM_5", fixe(3), "BIOLOGIE", "VALIDE", false, "motif 5", "conclusion fictive");
        demande(CENTRE_A, autre, "DEM_AUTRE", fixe(6), "BIOLOGIE", "DEMANDE", false, "motif autre patient", null);
        Map<String, Object> pages = new LinkedHashMap<>();
        for (int page = 0; page <= 3; page++) {
            pages.put("page " + page, page(patientA, "page=" + page + "&size=2", as(CENTRE_A, "MEDECIN")));
        }
        arbre("pages", pages);
    }

    @Test
    void les_bornes_de_pagination_sont_ramenees_a_des_valeurs_sures_ou_refusees_en_500() throws Exception {
        deuxCentres();
        for (int i = 1; i <= 3; i++) {
            demande(CENTRE_A, patientA, "DEM_" + i, fixe(i), "BIOLOGIE", "DEMANDE", false, "motif " + i, null);
        }
        Map<String, Object> bornes = new LinkedHashMap<>();
        for (String parametres : new String[]{"", "size=0", "size=-1", "page=-1", "page=-5&size=2", "size=1", "size=200", "size=1000",
                "page=1000", "size=abc", "page=abc", "centerId=pas-un-uuid", "size=2147483647", "size=99999999999"}) {
            bornes.put(parametres.isEmpty() ? "(defauts)" : parametres, resume(patientA, parametres));
        }
        arbre("bornes", bornes);
    }

    private Map<String, Object> resume(UUID patient, String parametres) throws Exception {
        Resp r = doGet(demandesApi(patient) + (parametres.isEmpty() ? "" : "?" + parametres), as(CENTRE_A, "MEDECIN"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", statutEtCode(r));
        if (r.status() == 200) {
            JsonNode c = corps(r);
            m.put("items", c.path("items").size());
            m.put("total", c.path("total").asInt());
            m.put("page", c.path("page").asInt());
            m.put("size", c.path("size").asInt());
        }
        return m;
    }

    @Test
    void la_liste_plafonne_la_taille_de_page_a_200() throws Exception {
        deuxCentres();
        jdbc.update("INSERT INTO demandes_examen (id, patient_id, center_id, prescripteur_id, date_demande, categorie, urgent, motif, statut, "
                        + "conclusion, created_at, updated_at) SELECT RANDOM_UUID(), ?, ?, 'medecin-fictif', DATEADD('DAY', X, DATE '2026-01-01'), "
                        + "'BIOLOGIE', FALSE, 'serie', 'DEMANDE', NULL, ?, ? FROM SYSTEM_RANGE(1, 205)",
                patientA, CENTRE_A, CREATION_FIXE, CREATION_FIXE);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("size=1000", resume(patientA, "size=1000"));
        m.put("size=200&page=1", resume(patientA, "size=200&page=1"));
        m.put("defauts", resume(patientA, ""));
        arbre("plafond", m);
    }

    @Test
    void la_liste_rend_toutes_les_demandes_quel_que_soit_leur_statut_avec_leurs_lignes_et_leur_conclusion() throws Exception {
        deuxCentres();
        UUID d = demande(CENTRE_A, patientA, "DEM_VALIDE", fixe(2), "BIOLOGIE", "VALIDE", true, "Suivi fictif", "Conclusion fictive");
        ligne(d, "L1", "LOINC", "718-7", "Hemoglobine fictive", "Hemoglobine", "A jeun");
        ligne(d, "L2", null, null, null, "Examen sans code", null);
        ligne(d, "L3", "LOCAL", "CR-1", null, null, "code local seul");
        demande(CENTRE_A, patientA, "DEM_ANNULEE", fixe(1), "IMAGERIE", "ANNULE", false, null, null);
        Resp liste = doGet(demandesApi(patientA) + "?centerId=" + CENTRE_A, as(CENTRE_A, "ADMIN"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", liste.status());
        m.put("cacheControl", liste.cacheControl());
        m.put("total", corps(liste).path("total").asInt());
        List<Object> items = new ArrayList<>();
        corps(liste).path("items").forEach(i -> items.add(demandeLisible(i)));
        m.put("demandes", items);
        arbre("liste", m);
    }

    @Test
    void la_forme_complete_de_la_reponse_d_une_demande_est_verrouillee() throws Exception {
        deuxCentres();
        UUID d = demande(CENTRE_A, patientA, "DEM", fixe(2), "ANAPATH", "PRELEVE", true, "Biopsie fictive", null);
        ligne(d, "L1", "LOINC", "718-7", "Hemoglobine fictive", "Hemoglobine", "A jeun");
        lecture("liste", doGet(demandesApi(patientA), as(CENTRE_A, "MEDECIN")));
    }

    @Test
    void un_patient_sans_demande_inconnu_ou_d_un_autre_centre_donne_une_liste_vide_en_200() throws Exception {
        deuxCentres();
        demande(CENTRE_B, patientB, "DEM_B", fixe(1), "BIOLOGIE", "DEMANDE", false, "motif B", null);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("patient sans demande", page(patientDm3(CENTRE_A, "PAT_VIDE"), "", as(CENTRE_A, "MEDECIN")));
        cas.put("patient inconnu", page(idStable("patient", "INCONNU"), "", as(CENTRE_A, "MEDECIN")));
        cas.put("patient du centre B, session A", page(patientB, "", as(CENTRE_A, "MEDECIN")));
        cas.put("temoin : patient du centre B, session B", page(patientB, "", as(CENTRE_B, "MEDECIN")));
        cas.put("identifiant de patient illisible", statutEtCode(doGet(PATIENTS_API + "/pas-un-uuid/demandes-examen", as(CENTRE_A, "MEDECIN"))));
        arbre("listes_vides", cas);
    }

    @Test
    void les_demandes_de_deux_patients_d_un_meme_centre_ne_se_melangent_pas_et_les_lignes_suivent_leur_demande() throws Exception {
        deuxCentres();
        UUID autre = patientDm3(CENTRE_A, "PAT_A2");
        UUID d1 = demande(CENTRE_A, patientA, "DEM_A", fixe(1), "BIOLOGIE", "DEMANDE", false, "motif A", null);
        UUID d2 = demande(CENTRE_A, autre, "DEM_A2", fixe(1), "BIOLOGIE", "DEMANDE", false, "motif A2", null);
        ligne(d1, "LA", "LOINC", "718-7", null, "Ligne de A", null);
        ligne(d2, "LA2", "LOINC", "2160-0", null, "Ligne de A2", null);
        Map<String, Object> cas = new LinkedHashMap<>();
        for (UUID p : List.of(patientA, autre)) {
            Resp r = doGet(demandesApi(p), as(CENTRE_A, "MEDECIN"));
            List<Object> items = new ArrayList<>();
            corps(r).path("items").forEach(i -> items.add(demandeLisible(i)));
            cas.put(p.equals(patientA) ? "PAT_A" : "PAT_A2", items);
        }
        arbre("patients", cas);
    }
}
