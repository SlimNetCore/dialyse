package com.hemodialyse.backend.characterization.patientcouverture.pc4;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PC4 : {@code POST /api/v1/pec/attestations-center/search} (liste paginée des attestations d'un centre, jointe aux patients) :
 * forme, tri, pagination, bornes, filtres (code, nom, assurance, recherche globale, dates), isolement.
 */
class RechercheDesAttestationsCharacterizationIntegrationTest extends AttestationCharacterizationSupport {

    @Override
    protected String dossier() {
        return "recherche";
    }

    /** Cinq patients du centre A et leurs attestations, fin décroissante : ALPHA (J+50), BRAVO (J+40), CHARLIE (J+30), DELTA (J+20), ECHO (J+10). */
    private void decorDeRecherche() {
        String[] noms = {"ALPHA", "BRAVO", "CHARLIE", "DELTA", "ECHO"};
        for (int i = 0; i < noms.length; i++) {
            UUID p = patientCouvert(CENTRE_A, noms[i], false, "ENFANT");
            attestationEnBase(CENTRE_A, "ATT_" + noms[i], p, jour(-100 + 10 * i), jour(50 - 10 * i));
        }
    }

    private String corps(String centre, String reste) {
        return "{'centerId':'" + centre + "'" + (reste.isEmpty() ? "" : "," + reste) + "}";
    }

    private String lignes(Resp r) {
        if (r.status() != 200) return refus(r);
        JsonNode c = corps(r);
        List<String> l = new ArrayList<>();
        for (JsonNode i : c.path("items")) {
            String code = i.path("code_patient").isNull() ? "<sans patient>" : i.path("code_patient").asString();
            l.add(code);
        }
        return "total " + c.path("total").asString() + " : " + l;
    }

    private Resp chercher(String reste) throws Exception {
        return rechercher(corps(CENTRE_A.toString(), "'page':0,'size':50" + (reste.isEmpty() ? "" : "," + reste)), adminA());
    }

    @Test
    void la_recherche_rend_les_attestations_du_centre_jointes_au_patient_triees_par_fin_decroissante() throws Exception {
        decorDeRecherche();
        ecriture("page_0_taille_2", rechercher(corps(CENTRE_A.toString(), "'page':0,'size':2"), adminA()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("page 0 taille 2", lignes(rechercher(corps(CENTRE_A.toString(), "'page':0,'size':2"), adminA())));
        m.put("page 1 taille 2", lignes(rechercher(corps(CENTRE_A.toString(), "'page':1,'size':2"), adminA())));
        m.put("page 2 taille 2", lignes(rechercher(corps(CENTRE_A.toString(), "'page':2,'size':2"), adminA())));
        m.put("page 3 taille 2 (au-delà)", lignes(rechercher(corps(CENTRE_A.toString(), "'page':3,'size':2"), adminA())));
        m.put("taille 200 (maximum)", lignes(rechercher(corps(CENTRE_A.toString(), "'page':0,'size':200"), adminA())));
        m.put("taille 1", lignes(rechercher(corps(CENTRE_A.toString(), "'page':4,'size':1"), adminA())));
        m.put("centre B (vide)", lignes(rechercher(corps(CENTRE_B.toString(), "'page':0,'size':10"), adminB())));
        arbre("pages", m);
        Resp r = rechercher(corps(CENTRE_A.toString(), "'page':0,'size':2"), adminA());
        Map<String, Object> champs = new LinkedHashMap<>();
        champs.put("clés de la réponse", new java.util.TreeSet<>(corps(r).propertyNames()));
        champs.put("clés d'une ligne", new java.util.ArrayList<>(corps(r).path("items").get(0).propertyNames()));
        arbre("formes", champs);
    }

    @Test
    void une_attestation_sans_patient_du_centre_est_rendue_avec_des_champs_patient_nuls_et_un_patient_d_un_autre_centre_n_est_pas_joint() throws Exception {
        UUID patientA = patientCouvert(CENTRE_A, "ALPHA", false, "ENFANT");
        UUID patientB = patientCouvert(CENTRE_B, "BETA_B", false, "ENFANT");
        UUID inconnu = UUID.nameUUIDFromBytes("pc4-recherche-inconnu".getBytes());
        aliasNommes.put(inconnu.toString(), "<PATIENT_INCONNU>");
        attestationEnBase(CENTRE_A, "A1", patientA, jour(-5), jour(50));
        attestationEnBase(CENTRE_A, "A2", patientB, jour(-5), jour(40));
        attestationEnBase(CENTRE_A, "A3", inconnu, jour(-5), jour(30));
        lecture("sans_filtre_lignes", chercherLaPage());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sans filtre", lignes(chercher("")));
        m.put("nom = Patient (présent chez le patient du centre A seulement)", lignes(chercher("'nom':'Patient'")));
        m.put("assurance vide", lignes(chercher("'assurance':''")));
        m.put("search = test", lignes(chercher("'search':'test'")));
        arbre("jointure", m);
    }

    private Resp chercherLaPage() throws Exception {
        return chercher("");
    }

    @Test
    void les_bornes_de_la_pagination_et_les_refus_de_forme() throws Exception {
        decorDeRecherche();
        String a = CENTRE_A.toString();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("corps vide {}", lignes(rechercher("{}", adminA())));
        m.put("centerId seul (page 0, taille 0)", lignes(rechercher("{'centerId':'" + a + "'}", adminA())));
        m.put("taille absente", lignes(rechercher(corps(a, "'page':0"), adminA())));
        m.put("page absente (0 par défaut)", lignes(rechercher(corps(a, "'size':10"), adminA())));
        m.put("taille 0", lignes(rechercher(corps(a, "'page':0,'size':0"), adminA())));
        m.put("taille 201", lignes(rechercher(corps(a, "'page':0,'size':201"), adminA())));
        m.put("taille négative", lignes(rechercher(corps(a, "'page':0,'size':-1"), adminA())));
        m.put("page négative", lignes(rechercher(corps(a, "'page':-1,'size':10"), adminA())));
        m.put("page textuelle", lignes(rechercher(corps(a, "'page':'x','size':10"), adminA())));
        m.put("page très grande", lignes(rechercher(corps(a, "'page':100000,'size':10"), adminA())));
        m.put("centerId absent", lignes(rechercher("{'page':0,'size':10}", adminA())));
        m.put("centerId n'est pas un UUID", lignes(rechercher("{'centerId':'abc','page':0,'size':10}", adminA())));
        m.put("JSON invalide", lignes(rechercher("{", adminA())));
        m.put("corps absent", lignes(rechercher("", adminA())));
        m.put("champ inconnu ignoré", lignes(rechercher(corps(a, "'page':0,'size':10,'inconnu':1"), adminA())));
        arbre("bornes", m);
    }

    @Test
    void les_filtres_de_code_de_nom_d_assurance_et_de_recherche_globale_sont_des_sous_chaines_sans_casse() throws Exception {
        decorDeRecherche();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code = ALPHA", lignes(chercher("'code':'ALPHA'")));
        m.put("code = pc- (tous)", lignes(chercher("'code':'pc-'")));
        m.put("code = ch (sous-chaîne)", lignes(chercher("'code':'ch'")));
        m.put("code avec espaces autour", lignes(chercher("'code':'  bravo  '")));
        m.put("code inconnu", lignes(chercher("'code':'ZZZ'")));
        m.put("code blanc (ignoré)", lignes(chercher("'code':'   '")));
        m.put("nom = patient delta (nom, prénom ou « nom prénom »)", lignes(chercher("'nom':'patient delta'")));
        m.put("nom = test (prénom de tous)", lignes(chercher("'nom':'test'")));
        m.put("nom = delta test (nom et prénom accolés)", lignes(chercher("'nom':'delta test'")));
        m.put("nom = TEST DELTA (sens inverse)", lignes(chercher("'nom':'test delta'")));
        m.put("assurance = ASS-ECHO", lignes(chercher("'assurance':'ASS-ECHO'")));
        m.put("assurance = ass- (tous)", lignes(chercher("'assurance':'ass-'")));
        m.put("assurance inconnue", lignes(chercher("'assurance':'999'")));
        m.put("search = charlie (code, nom, prénom ou assurance)", lignes(chercher("'search':'charlie'")));
        m.put("search = ass-bravo", lignes(chercher("'search':'ass-bravo'")));
        m.put("search = test", lignes(chercher("'search':'test'")));
        m.put("search blanc (ignoré)", lignes(chercher("'search':' '")));
        m.put("search + code incompatibles", lignes(chercher("'search':'alpha','code':'echo'")));
        m.put("search + code compatibles", lignes(chercher("'search':'pha','code':'alpha'")));
        m.put("joker % non échappé", lignes(chercher("'code':'%'")));
        m.put("joker _ non échappé", lignes(chercher("'code':'_C_'")));
        m.put("filtres null", lignes(chercher("'code':null,'nom':null,'assurance':null,'search':null,'debut':null,'fin':null")));
        arbre("filtres", m);
    }

    @Test
    void les_filtres_de_dates_acceptent_une_date_exacte_un_intervalle_ouvert_ou_inverse_et_ignorent_ce_qu_ils_ne_lisent_pas() throws Exception {
        decorDeRecherche();
        String fmt = "dd/MM/yyyy";
        Map<String, Object> m = new LinkedHashMap<>();
        // début : ALPHA J-100, BRAVO J-90, CHARLIE J-80, DELTA J-70, ECHO J-60 ; fin : J+50, J+40, J+30, J+20, J+10
        m.put("debut exact J-90", lignes(chercher("'debut':'" + jour(-90) + "'")));
        m.put("debut exact sans attestation (J-95)", lignes(chercher("'debut':'" + jour(-95) + "'")));
        m.put("debut intervalle J-90..J-70", lignes(chercher("'debut':'" + jour(-90) + ".." + jour(-70) + "'")));
        m.put("debut intervalle ouvert à droite J-80..", lignes(chercher("'debut':'" + jour(-80) + "..'")));
        m.put("debut intervalle ouvert à gauche ..J-90", lignes(chercher("'debut':'.." + jour(-90) + "'")));
        m.put("debut intervalle inversé J-70..J-90", lignes(chercher("'debut':'" + jour(-70) + ".." + jour(-90) + "'")));
        m.put("debut intervalle vide ..", lignes(chercher("'debut':'..'")));
        m.put("debut intervalle dont une borne est illisible", lignes(chercher("'debut':'abc.." + jour(-70) + "'")));
        m.put("debut illisible", lignes(chercher("'debut':'abc'")));
        m.put("debut au format jj/mm/aaaa", lignes(chercher("'debut':'" + jour(-90).format(DateTimeFormatter.ofPattern(fmt)) + "'")));
        m.put("debut avec heure (T)", lignes(chercher("'debut':'" + jour(-90) + "T10:00:00'")));
        m.put("debut avec heure (espace)", lignes(chercher("'debut':'" + jour(-90) + " 10:00:00'")));
        m.put("debut blanc", lignes(chercher("'debut':'  '")));
        m.put("fin exacte J+30", lignes(chercher("'fin':'" + jour(30) + "'")));
        m.put("fin intervalle J+20..J+40", lignes(chercher("'fin':'" + jour(20) + ".." + jour(40) + "'")));
        m.put("fin intervalle ..J+20", lignes(chercher("'fin':'.." + jour(20) + "'")));
        m.put("fin intervalle J+40..", lignes(chercher("'fin':'" + jour(40) + "..'")));
        m.put("fin illisible", lignes(chercher("'fin':'31/31/2030'")));
        m.put("debut et fin combinés", lignes(chercher("'debut':'" + jour(-100) + ".." + jour(-80) + "','fin':'" + jour(30) + "..'")));
        m.put("debut et fin sans résultat commun", lignes(chercher("'debut':'" + jour(-100) + "','fin':'" + jour(10) + "'")));
        m.put("filtres de dates + nom", lignes(chercher("'debut':'.." + jour(-70) + "','nom':'charlie'")));
        arbre("dates", m);
    }

    @Test
    void les_attestations_d_un_autre_centre_ne_sont_jamais_rendues_mais_le_centre_de_la_requete_n_est_pas_compare_a_celui_de_la_session() throws Exception {
        decorDeRecherche();
        UUID pb = patientCouvert(CENTRE_B, "ZULU_B", false, "ENFANT");
        attestationEnBase(CENTRE_B, "ATT_ZULU", pb, jour(-5), jour(500));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centre A", lignes(chercher("")));
        m.put("centre A, filtre sur le code d'un patient du centre B", lignes(chercher("'code':'zulu'")));
        m.put("centre B par son administrateur", lignes(rechercher(corps(CENTRE_B.toString(), "'page':0,'size':50"), adminB())));
        m.put("centre A par l'administrateur du centre B", lignes(rechercher(corps(CENTRE_A.toString(), "'page':0,'size':50"), adminB())));
        m.put("centre inconnu", lignes(rechercher(corps(CENTRE_INCONNU.toString(), "'page':0,'size':50"), adminA())));
        arbre("isolement", m);
    }
}
