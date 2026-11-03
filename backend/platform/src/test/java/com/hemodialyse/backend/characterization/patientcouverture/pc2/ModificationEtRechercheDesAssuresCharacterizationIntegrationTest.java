package com.hemodialyse.backend.characterization.patientcouverture.pc2;

import com.hemodialyse.backend.characterization.patientcouverture.CouvertureCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PC2 : {@code PUT /api/v1/patients/assures/{numero}} (modification d'un assuré) et {@code GET /api/v1/patients/assures}
 * (recherche et liste). Les droits de rôles et la garde de centre sont verrouillés par PC1 ({@code autorisations}).
 */
class ModificationEtRechercheDesAssuresCharacterizationIntegrationTest extends CouvertureCharacterizationSupport {

    @Override
    protected String racine() {
        return "../patientcouverture/pc2/";
    }

    @Override
    protected String dossier() {
        return "assures";
    }

    private String corpsComplet() {
        return json("{'nom':'Nouveau','prenom':'Prenom2','sexe':'F','dateNaissance':'" + jour(-9000) + "','telPersonnel':'0550123456',"
                + "'telMobile':'0661234567','telBureau':'021234567','adresse':'12 rue des Lilas','groupeSanguin':'O+'}");
    }

    private Resp modifierAssure(String numero, String corps) throws Exception {
        return doPut(ASSURES + "/" + numero + "?centerId=" + CENTRE_A, corps, adminA());
    }

    @Test
    void modifier_un_assure_remplace_toutes_ses_coordonnees_sans_toucher_a_son_numero_ni_a_son_centre_et_la_fiche_du_patient_les_relit() throws Exception {
        UUID patient = patientAyantDroit("ENFANT_1");
        assure(CENTRE_A, "PC2-AS-1", "Dupont", "Jean");
        affecter(patient, "PC2-AS-1", adminA());
        ecriture("reponse_de_la_modification", modifierAssure("PC2-AS-1", corpsComplet()));
        etatDesAssures("assure_ecrit");
        Resp fiche = lireLaFiche(patient);
        Map<String, Object> m = new LinkedHashMap<>();
        corps(fiche).properties().forEach(e -> {
            if (e.getKey().toLowerCase().startsWith("assure")) {
                m.put(e.getKey(), e.getValue().isNull() ? null : e.getValue().isValueNode() ? e.getValue().asString() : e.getValue().toString());
            }
        });
        arbre("ce_que_la_fiche_du_patient_relit_de_l_assure", new java.util.TreeMap<>(m));
        lecture("historique_avec_la_nouvelle_identite", historique(patient, adminA()));
    }

    private Resp lireLaFiche(UUID patient) throws Exception {
        return doGet(PATIENTS + "/" + patient + "?centerId=" + CENTRE_A, adminA());
    }

    @Test
    void un_corps_sans_champs_efface_toutes_les_coordonnees_de_l_assure() throws Exception {
        assure(CENTRE_A, "PC2-AS-1", "Dupont", "Jean");
        jdbc.update("UPDATE assure SET adresse = 'ancienne', tel_personnel = '0550000000', groupe_sanguin = 'A+', date_naissance = ? WHERE numero_assurance = 'PC2-AS-1'",
                sqlDate(jour(-9000)));
        ecriture("reponse_corps_vide", modifierAssure("PC2-AS-1", json("{}")));
        etatDesAssures("assure_efface");
    }

    @Test
    void la_date_de_naissance_de_l_assure_est_lue_sur_ses_dix_premiers_caracteres_ou_refusee_si_illisible() throws Exception {
        assure(CENTRE_A, "PC2-AS-1", "Dupont", "Jean");
        Map<String, String> entrees = new LinkedHashMap<>();
        entrees.put("date ISO", jour(-9000).toString());
        entrees.put("date et heure", jour(-9001) + "T10:00:00Z");
        entrees.put("date suivie d'un texte", jour(-9002) + " texte");
        entrees.put("jour/mois/année", jour(-9003).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        entrees.put("mois 13", "2000-13-01");
        entrees.put("trop courte", "2000");
        entrees.put("vide", "");
        entrees.put("espaces", "   ");
        entrees.put("texte libre", "inconnue");
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : entrees.entrySet()) {
            Resp r = modifierAssure("PC2-AS-1", corps(champs("nom", "N", "dateNaissance", e.getValue())));
            String stockee = jdbc.queryForObject("SELECT CAST(date_naissance AS VARCHAR) FROM assure WHERE numero_assurance = 'PC2-AS-1'", String.class);
            m.put(e.getKey(), refus(r) + " | stockée=" + dates(stockee == null ? "null" : stockee));
        }
        arbre("naissance_lue_ou_refusee", m);
    }

    @Test
    void les_telephones_et_champs_libres_de_l_assure_sont_enregistres_tels_quels_sans_validation() throws Exception {
        assure(CENTRE_A, "PC2-AS-1", "Dupont", "Jean");
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Map<String, Object>> cas = new LinkedHashMap<>();
        cas.put("téléphone personnel avec des lettres", champs("nom", "N", "telPersonnel", "abc"));
        cas.put("téléphone mobile de 2 chiffres", champs("nom", "N", "telMobile", "12"));
        cas.put("téléphone bureau de 22 chiffres", champs("nom", "N", "telBureau", "00 00 00 00 00 00 00 00 00 00 00"));
        cas.put("sexe hors liste", champs("nom", "N", "sexe", "Z"));
        cas.put("groupe sanguin hors liste", champs("nom", "N", "groupeSanguin", "ZZ"));
        cas.put("adresse de 255 caractères", champs("nom", "N", "adresse", "x".repeat(255)));
        cas.put("adresse de 256 caractères", champs("nom", "N", "adresse", "x".repeat(256)));
        cas.put("nom de 256 caractères", champs("nom", "n".repeat(256)));
        for (Map.Entry<String, Map<String, Object>> e : cas.entrySet()) {
            Resp r = modifierAssure("PC2-AS-1", corps(e.getValue()));
            m.put(e.getKey(), refus(r));
        }
        arbre("statuts", m);
        sql("assure_ecrit", "SELECT nom IS NOT NULL AND LENGTH(nom) < 256 AS nom_court, sexe, tel_personnel, tel_mobile, tel_bureau, "
                + "LENGTH(adresse) AS longueur_adresse, groupe_sanguin FROM assure WHERE numero_assurance = 'PC2-AS-1'");
    }

    @Test
    void la_modification_d_un_assure_refuse_l_inconnu_et_les_corps_illisibles_sans_rien_ecrire() throws Exception {
        assure(CENTRE_A, "PC2-AS-1", "Dupont", "Jean");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assuré inconnu", refus(modifierAssure("PC2-INCONNU", corpsComplet())));
        m.put("numéro en minuscules", refus(modifierAssure("pc2-as-1", corpsComplet())));
        m.put("corps absent", refus(modifierAssure("PC2-AS-1", "")));
        m.put("corps JSON invalide", refus(modifierAssure("PC2-AS-1", "{nom")));
        m.put("corps tableau", refus(modifierAssure("PC2-AS-1", "[]")));
        m.put("corps null", refus(modifierAssure("PC2-AS-1", "null")));
        m.put("champ inconnu", refus(modifierAssure("PC2-AS-1", json("{'nom':'N','inconnu':1}"))));
        m.put("nom numérique", refus(modifierAssure("PC2-AS-1", json("{'nom':12}"))));
        m.put("centre absent", refus(doPut(ASSURES + "/PC2-AS-1", corpsComplet(), adminA())));
        m.put("centre illisible", refus(doPut(ASSURES + "/PC2-AS-1?centerId=zzz", corpsComplet(), adminA())));
        m.put("type de contenu texte", refus(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put(java.net.URI.create(ASSURES + "/PC2-AS-1?centerId=" + CENTRE_A)).contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                .content(corpsComplet()).with(adminA()))));
        arbre("refus", m);
        etatDesAssures("assure_apres_les_refus_et_les_corps_acceptes");
    }

    @Test
    void la_recherche_des_assures_filtre_par_numero_nom_ou_prenom_sans_casse_et_trie_par_nom_puis_prenom() throws Exception {
        assure(CENTRE_A, "PC2-AS-1", "Dupont", "Jean");
        assure(CENTRE_A, "PC2-AS-2", "Dupond", "Alice");
        assure(CENTRE_A, "PC2-AS-3", "Dupont", "Anne");
        assure(CENTRE_A, "PC2-AS-4", null, null);
        assure(CENTRE_A, "PC2-AS-5", "Zola", "Émile");
        assure(CENTRE_A, "PC2-AS-6", "100%", "a_b");
        assure(CENTRE_B, "PC2-AS-B", "Dupont", "AutreCentre");
        Map<String, Object> m = new LinkedHashMap<>();
        for (String q : new String[]{null, "", " ", "dup", "DUPONT", "jean", "pc2-as-2", "PC2-AS", " dup ", "%", "_", "100%", "a_b", "é", "É", "zzz", "Autre"}) {
            String url = ASSURES + "?centerId=" + CENTRE_A + (q == null ? "" : "&q=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8));
            Resp r = doGet(url, adminA());
            List<String> numeros = new ArrayList<>();
            if (r.status() == 200) corps(r).forEach(x -> numeros.add(x.path("numeroAssurance").asString()));
            m.put("q=" + (q == null ? "<absent>" : "'" + q + "'"), r.status() + " " + numeros);
        }
        m.put("centre B", corps(doGet(ASSURES + "?centerId=" + CENTRE_B, adminB())).size() + " assuré(s)");
        m.put("centre absent", refus(doGet(ASSURES, adminA())));
        arbre("recherche", m);
        lecture("liste_complete_du_centre_A", doGet(ASSURES + "?centerId=" + CENTRE_A, adminA()));
    }
}
