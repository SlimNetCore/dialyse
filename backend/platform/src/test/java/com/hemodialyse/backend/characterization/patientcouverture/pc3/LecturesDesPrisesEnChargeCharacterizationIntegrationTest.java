package com.hemodialyse.backend.characterization.patientcouverture.pc3;

import com.hemodialyse.backend.characterization.patientcouverture.CouvertureCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PC3 : lectures des prises en charge : {@code GET /api/v1/pec} (centre), {@code GET /api/v1/pec/patient/{id}},
 * {@code POST /api/v1/pec/pec-center/search} (recherche paginée), cache des listes. Les attestations ({@code /pec/attestations*},
 * {@code attestations-center/search}) relèvent de PC4.
 */
class LecturesDesPrisesEnChargeCharacterizationIntegrationTest extends CouvertureCharacterizationSupport {

    @Override
    protected String racine() {
        return "../patientcouverture/pc3/";
    }

    @Override
    protected String dossier() {
        return "lectures";
    }

    private UUID dupont;
    private UUID martin;
    private UUID durand;

    /** Trois patients, cinq PEC aux statuts, périodes et fins de demande distincts (aucune égalité de tri). */
    private void jeuDePec() {
        UUID forfait = forfait(CENTRE_A, "HD3", "9500.00");
        dupont = patientCouvert(CENTRE_A, "DUPONT", true, null);
        martin = patientCouvert(CENTRE_A, "MARTIN", true, null);
        durand = patientCouvert(CENTRE_A, "DURAND", true, null);
        jdbc.update("UPDATE patients SET nom = 'Dupont', prenom = 'Jean', code_patient = 'PC-0001', numero_assurance = 'ASS-1001' WHERE id = ?", dupont);
        jdbc.update("UPDATE patients SET nom = 'Martin', prenom = 'Paul', code_patient = 'PC-0002', numero_assurance = 'ASS-2002' WHERE id = ?", martin);
        jdbc.update("UPDATE patients SET nom = 'Durand', prenom = 'Anne', code_patient = 'PC-0003', numero_assurance = 'ASS-3003' WHERE id = ?", durand);
        pecEnBase(CENTRE_A, "PEC_1", dupont, "VALIDEE", jour(-300), jour(-100), forfait, jour(-300), jour(-100), forfait, 1);
        pecEnBase(CENTRE_A, "PEC_2", dupont, "CREE", jour(-50), jour(50), forfait, null, null, null, 2);
        pecEnBase(CENTRE_A, "PEC_3", martin, "CLOTUREE", jour(-200), jour(-20), null, jour(-190), jour(-30), null, 3);
        pecEnBase(CENTRE_A, "PEC_4", durand, "VALIDEE", jour(-10), jour(300), forfait, jour(-5), jour(290), forfait, 4);
        pecEnBase(CENTRE_A, "PEC_5", martin, "CREE", jour(20), jour(400), null, null, null, null, 5);
    }

    private List<String> pecsTrouvees(String corpsJson) throws Exception {
        Resp r = doPost(PEC + "/pec-center/search", json(corpsJson), adminA());
        if (r.status() != 200) return List.of(refus(r));
        List<String> ids = new ArrayList<>();
        corps(r).path("items").forEach(i -> ids.add(aliasDe(i.path("id").asString())));
        return ids;
    }

    private String aliasDe(String id) {
        return aliasNommes.getOrDefault(id, id).replace("<", "").replace(">", "");
    }

    private String recherche(String complement) {
        return "{'centerId':'" + CENTRE_A + "','page':0,'size':50" + complement + "}";
    }

    @Test
    void les_listes_du_centre_et_du_patient_rendent_l_entite_de_la_pec_telle_quelle() throws Exception {
        jeuDePec();
        lecture("pec_du_centre_A", pecsDuCentre(CENTRE_A, adminA()));
        lecture("pec_de_Dupont", pecsDuPatient(dupont, CENTRE_A, adminA()));
        lecture("pec_d_un_patient_sans_pec", pecsDuPatient(patientCouvert(CENTRE_A, "SANS_PEC", true, null), CENTRE_A, adminA()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("patient inconnu", refus(pecsDuPatient(UUID.nameUUIDFromBytes("inconnu".getBytes()), CENTRE_A, adminA())) + " " + corps(pecsDuPatient(UUID.nameUUIDFromBytes("inconnu".getBytes()), CENTRE_A, adminA())).size());
        m.put("centre sans PEC", String.valueOf(corps(pecsDuCentre(CENTRE_B, adminB())).size()));
        m.put("centre absent", refus(doGet(PEC, adminA())));
        m.put("centre illisible", refus(doGet(PEC + "?centerId=zzz", adminA())));
        m.put("patient illisible", refus(doGet(PEC + "/patient/zzz?centerId=" + CENTRE_A, adminA())));
        arbre("cas_limites_nouveau_code", m);
    }

    @Test
    void les_listes_sont_gardees_en_cache_par_centre_et_par_patient_jusqu_a_une_ecriture_par_l_api() throws Exception {
        jeuDePec();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("liste du centre au départ", corps(pecsDuCentre(CENTRE_A, adminA())).size());
        m.put("liste de Dupont au départ", corps(pecsDuPatient(dupont, CENTRE_A, adminA())).size());
        pecEnBase(CENTRE_A, "PEC_6", dupont, "CREE", jour(60), jour(90), null, null, null, null, 6);
        m.put("après une écriture directe en base : liste du centre (périmée)", corps(pecsDuCentre(CENTRE_A, adminA())).size());
        m.put("après une écriture directe en base : liste de Dupont (périmée)", corps(pecsDuPatient(dupont, CENTRE_A, adminA())).size());
        creerLaPec("PEC_7", dupont, CENTRE_A, jour(100), jour(120), null, adminA());
        m.put("après une création par l'API : liste du centre", corps(pecsDuCentre(CENTRE_A, adminA())).size());
        m.put("après une création par l'API : liste de Dupont", corps(pecsDuPatient(dupont, CENTRE_A, adminA())).size());
        m.put("la recherche paginée n'est pas en cache", pecsTrouvees(recherche("")).size());
        arbre("cache_des_listes", m);
    }

    @Test
    void la_recherche_paginee_rend_une_ligne_a_plat_par_pec_triee_par_fin_de_demande_decroissante() throws Exception {
        jeuDePec();
        lecture("recherche_sans_filtre", doPost(PEC + "/pec-center/search", json(recherche("")), adminA()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("page 0 taille 2", pecsTrouvees("{'centerId':'" + CENTRE_A + "','page':0,'size':2}"));
        m.put("page 1 taille 2", pecsTrouvees("{'centerId':'" + CENTRE_A + "','page':1,'size':2}"));
        m.put("page 2 taille 2", pecsTrouvees("{'centerId':'" + CENTRE_A + "','page':2,'size':2}"));
        m.put("page 9 taille 2", pecsTrouvees("{'centerId':'" + CENTRE_A + "','page':9,'size':2}"));
        Resp page = doPost(PEC + "/pec-center/search", json("{'centerId':'" + CENTRE_A + "','page':1,'size':2}"), adminA());
        m.put("total, page et taille rendus pour la page 1", corps(page).path("total").asInt() + "/" + corps(page).path("page").asInt() + "/" + corps(page).path("size").asInt());
        arbre("pages", m);
    }

    @Test
    void la_recherche_refuse_les_bornes_de_page_et_les_corps_invalides() throws Exception {
        jeuDePec();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taille 200", pecsTrouvees("{'centerId':'" + CENTRE_A + "','page':0,'size':200}").size() + " résultat(s)");
        m.put("taille 201", pecsTrouvees("{'centerId':'" + CENTRE_A + "','page':0,'size':201}"));
        m.put("taille 0", pecsTrouvees("{'centerId':'" + CENTRE_A + "','page':0,'size':0}"));
        m.put("page négative", pecsTrouvees("{'centerId':'" + CENTRE_A + "','page':-1,'size':10}"));
        m.put("taille absente", pecsTrouvees("{'centerId':'" + CENTRE_A + "','page':0}"));
        m.put("page absente", pecsTrouvees("{'centerId':'" + CENTRE_A + "','size':10}"));
        m.put("centre absent", pecsTrouvees("{'page':0,'size':10}"));
        m.put("corps vide", pecsTrouvees("{}"));
        m.put("corps illisible", pecsTrouvees("{centerId"));
        m.put("taille texte", pecsTrouvees("{'centerId':'" + CENTRE_A + "','page':0,'size':'dix'}"));
        arbre("bornes_nouveau_code", m);
    }

    @Test
    void les_filtres_de_la_recherche_portent_sur_le_patient_le_statut_et_les_periodes_demandees() throws Exception {
        jeuDePec();
        Map<String, Object> m = new LinkedHashMap<>();
        String[][] filtres = {
                {"code PC-0001", "'code':'PC-0001'"}, {"code partiel et casse", "'code':'pc-000'"}, {"code inconnu", "'code':'zzz'"},
                {"nom Dupont", "'nom':'dupont'"}, {"nom et prénom", "'nom':'jean'"}, {"nom complet 'durand anne'", "'nom':'durand anne'"},
                {"nom complet inversé 'anne durand'", "'nom':'anne durand'"}, {"assurance 2002", "'assurance':'2002'"},
                {"statut CREE", "'statut':'CREE'"}, {"statut en minuscules", "'statut':'validee'"}, {"statut partiel 'ee'", "'statut':'ee'"},
                {"search 'martin'", "'search':'martin'"}, {"search 'creee'", "'search':'cree'"}, {"search code", "'search':'PC-0003'"},
                {"search assurance", "'search':'3003'"}, {"search espaces autour", "'search':'  martin  '"},
                {"filtres vides", "'code':'','nom':'  ','statut':''"}, {"deux filtres qui s'excluent", "'nom':'martin','statut':'VALIDEE'"},
                {"wildcard %", "'nom':'%'"}, {"wildcard _", "'code':'PC-000_'"},
                {"début exact", "'debut':'" + jour(-50) + "'"},
                {"début intervalle", "'debut':'" + jour(-300) + ".." + jour(-100) + "'"},
                {"début intervalle inversé", "'debut':'" + jour(-100) + ".." + jour(-300) + "'"},
                {"début borne basse ouverte", "'debut':'.." + jour(-100) + "'"},
                {"début borne haute ouverte", "'debut':'" + jour(-50) + "..'"},
                {"début en jj/mm/aaaa", "'debut':'" + jour(-50).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "'"},
                {"début avec heure", "'debut':'" + jour(-50) + "T10:00:00'"},
                {"début illisible (ignoré)", "'debut':'hier'"},
                {"début demi-illisible (ignoré)", "'debut':'" + jour(-50) + "..demain'"},
                {"fin exacte", "'fin':'" + jour(50) + "'"},
                {"fin intervalle", "'fin':'" + jour(-100) + ".." + jour(50) + "'"},
                {"début et fin combinés", "'debut':'" + jour(-300) + ".." + jour(-100) + "','fin':'" + jour(-100) + "'"},
        };
        for (String[] f : filtres) m.put(f[0], pecsTrouvees(recherche("," + f[1])));
        arbre("filtres", m);
    }

    @Test
    void la_recherche_ne_voit_que_le_centre_demande_et_ignore_les_dates_effectives_pour_filtrer() throws Exception {
        jeuDePec();
        UUID chezB = patientCouvert(CENTRE_B, "CHEZ_B", true, null);
        pecEnBase(CENTRE_B, "PEC_B", chezB, "VALIDEE", jour(-5), jour(5), null, null, null, null, 1);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centre A", pecsTrouvees("{'centerId':'" + CENTRE_A + "','page':0,'size':50}"));
        Resp b = doPost(PEC + "/pec-center/search", json("{'centerId':'" + CENTRE_B + "','page':0,'size':50}"), adminB());
        List<String> ids = new ArrayList<>();
        corps(b).path("items").forEach(i -> ids.add(aliasDe(i.path("id").asString())));
        m.put("centre B", ids);
        m.put("début filtré sur la période effective (PEC_4 : effectif J-5, demandé J-10)", pecsTrouvees(recherche(",'debut':'" + jour(-5) + "'")));
        arbre("isolement_et_dates", m);
    }
}
