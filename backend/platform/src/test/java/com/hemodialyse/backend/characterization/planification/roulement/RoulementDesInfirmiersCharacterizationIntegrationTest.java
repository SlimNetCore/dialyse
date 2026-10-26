package com.hemodialyse.backend.characterization.planification.roulement;

import com.hemodialyse.backend.characterization.planification.RoulementCharacterizationSupport;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filet de caractérisation P3 : roulement des infirmiers (trois routes d'affectations de {@code InfirmierRestController}).
 * Règle d'ensemble : jamais deux affectations d'un même infirmier sur le même créneau le même jour (même dans deux salles).
 */
class RoulementDesInfirmiersCharacterizationIntegrationTest extends RoulementCharacterizationSupport {

    private UUID amrani;
    private UUID benali;
    private UUID absentDuCentre;

    @Override
    protected String theme() {
        return "p3-roulement/roulement";
    }

    @BeforeEach
    void amorcer() {
        decor();
        amrani = inf(CENTRE_A, "INF_AMRANI", "Amrani", "Sara", true);
        benali = inf(CENTRE_A, "INF_BENALI", "Benali", "Karim", false);
        absentDuCentre = inf(CENTRE_B, "INF_CENTRE_B", "Zerrouki", "Rania", false);
    }

    private String route(UUID infirmier) {
        return INFIRMIERS + "/" + infirmier + "/affectations";
    }

    private String route(UUID infirmier, String affectation) {
        return route(infirmier) + "/" + affectation;
    }

    private String idDeLAffectation(Resp r) {
        return corps(r).path("id").asString();
    }

    @Test
    void une_affectation_valide_est_creee_avec_ses_jours_tries_et_aucun_avertissement_de_sureffectif_sans_effectif_requis_depasse() throws Exception {
        patientsPlaces(CENTRE_A, "PAT", 1, salle1, matin, JourSemaine.LUNDI, JourSemaine.MARDI);
        Resp r = doPost(route(amrani), corpsJson(salle1, matin, "MARDI", "LUNDI"), adminA());
        assertEquals(201, r.status());
        ecriture("creation_nominale", r);
        etatAffectations("creation_nominale_base");
    }

    private String corpsJson(UUID salle, UUID creneau, String... jours) {
        return json(corpsAffectation(salle, creneau, jours));
    }

    @Test
    void une_affectation_dans_une_salle_sans_patient_avertit_d_un_sureffectif_sur_chacun_de_ses_jours() throws Exception {
        Resp r = doPost(route(amrani), corpsJson(salle1, matin, "SAMEDI", "DIMANCHE", "MERCREDI"), adminA());
        assertEquals(201, r.status());
        ecriture("salle_sans_patient_avertit_du_sureffectif", r);
    }

    @Test
    void un_second_infirmier_sur_une_case_dont_l_effectif_requis_est_atteint_declenche_l_avertissement_pour_ses_jours_en_surnombre() throws Exception {
        patientsPlaces(CENTRE_A, "PAT", 4, salle1, matin, JourSemaine.LUNDI, JourSemaine.MARDI);
        Resp premier = doPost(route(amrani), corpsJson(salle1, matin, "LUNDI", "MARDI"), adminA());
        Resp second = doPost(route(benali), corpsJson(salle1, matin, "LUNDI", "MERCREDI"), adminA());
        assertEquals(201, premier.status());
        assertEquals(201, second.status());
        ecriture("premier_infirmier_sans_avertissement", premier);
        ecriture("second_infirmier_avertissement_lundi_surnombre_mercredi_sans_patient", second);
    }

    @Test
    void les_jours_en_double_dans_la_demande_sont_fusionnes_et_stockes_dans_l_ordre_de_la_semaine() throws Exception {
        Resp r = doPost(route(amrani), corpsJson(salle1, matin, "VENDREDI", "LUNDI", "LUNDI", "DIMANCHE"), adminA());
        assertEquals(201, r.status());
        ecriture("jours_en_double_fusionnes", r);
        etatAffectations("jours_en_double_fusionnes_base");
    }

    @Test
    void le_meme_creneau_un_meme_jour_est_refuse_meme_dans_une_autre_salle_et_il_suffit_d_un_seul_jour_commun() throws Exception {
        doPost(route(amrani), corpsJson(salle1, matin, "LUNDI", "MARDI"), adminA());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("autre salle, mêmes jours", statutEtCode(doPost(route(amrani), corpsJson(salle2, matin, "LUNDI", "MARDI"), adminA())));
        r.put("même salle, un seul jour commun", statutEtCode(doPost(route(amrani), corpsJson(salle1, matin, "MARDI", "JEUDI"), adminA())));
        r.put("même créneau, jours disjoints", statutEtCode(doPost(route(amrani), corpsJson(salle2, matin, "MERCREDI", "JEUDI"), adminA())));
        r.put("autre créneau, mêmes jours", statutEtCode(doPost(route(amrani), corpsJson(salle1, apresMidi, "LUNDI", "MARDI"), adminA())));
        r.put("un autre infirmier, même case, mêmes jours", statutEtCode(doPost(route(benali), corpsJson(salle1, matin, "LUNDI", "MARDI"), adminA())));
        arbre("chevauchement_regles", r);
        etatAffectations("chevauchement_base");
    }

    @Test
    void le_message_du_refus_de_chevauchement_est_celui_du_service() throws Exception {
        doPost(route(amrani), corpsJson(salle1, matin, "LUNDI"), adminA());
        Resp r = doPost(route(amrani), corpsJson(salle2, matin, "LUNDI"), adminA());
        assertEquals(422, r.status());
        ecriture("chevauchement_refus", r);
    }

    @Test
    void l_ordre_des_controles_est_infirmier_puis_salle_puis_creneau_puis_chevauchement() throws Exception {
        UUID inconnu = UUID.nameUUIDFromBytes("inconnu".getBytes());
        aliasNommes.put(inconnu.toString(), "<INCONNU>");
        doPost(route(amrani), corpsJson(salle1, matin, "LUNDI"), adminA());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("infirmier inconnu + salle inconnue", statutEtCode(doPost(route(inconnu), corpsJson(inconnu, matin, "LUNDI"), adminA())));
        r.put("infirmier d'un autre centre", statutEtCode(doPost(route(absentDuCentre), corpsJson(salle1, matin, "LUNDI"), adminA())));
        r.put("salle inconnue + créneau inconnu", statutEtCode(doPost(route(amrani), corpsJson(inconnu, inconnu, "LUNDI"), adminA())));
        r.put("salle d'un autre centre", statutEtCode(doPost(route(amrani), corpsJson(salleB, matin, "MARDI"), adminA())));
        r.put("créneau d'un autre centre", statutEtCode(doPost(route(amrani), corpsJson(salle1, creneauB, "MARDI"), adminA())));
        r.put("créneau inconnu + chevauchement", statutEtCode(doPost(route(amrani), corpsJson(salle1, inconnu, "LUNDI"), adminA())));
        r.put("chevauchement seul", statutEtCode(doPost(route(amrani), corpsJson(salle2, matin, "LUNDI"), adminA())));
        arbre("ordre_des_controles", r);
        etatAffectations("ordre_des_controles_base");
    }

    @Test
    void un_infirmier_desactive_est_refuse_en_422_et_son_roulement_deja_pose_n_apparait_plus_dans_la_presence_KB_PLN_35() throws Exception {
        UUID inactif = fiche(CENTRE_A, "INF_INACTIF", "INACTIF", "Dahmani", "Yanis", null, "INFIRMIER", false, true, null);
        semaine(1);
        assertEquals(201, doPost(route(inactif), corpsJson(salle1, matin, "LUNDI"), adminA()).status());
        jdbc.update("UPDATE infirmier SET actif = FALSE WHERE id = ?", inactif);
        Resp r = doPost(route(inactif), corpsJson(salle1, matin, "MARDI"), adminA());
        assertEquals(422, r.status());
        ecriture("infirmier_inactif_refuse", r);
        Map<String, Object> presence = casesDePresence(presenceDeLaSemaine(1, adminA()));
        arbre("infirmier_inactif_absent_de_la_presence", presence);
    }

    @Test
    void les_demandes_mal_formees_sont_refusees_en_400_avant_toute_ecriture() throws Exception {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("corps vide {}", statutEtCode(doPost(route(amrani), "{}", adminA())));
        r.put("jours vide", statutEtCode(doPost(route(amrani), json("{'salleId':'" + salle1 + "','creneauId':'" + matin + "','jours':[]}"), adminA())));
        r.put("jours absent", statutEtCode(doPost(route(amrani), json("{'salleId':'" + salle1 + "','creneauId':'" + matin + "'}"), adminA())));
        r.put("jours null", statutEtCode(doPost(route(amrani), json("{'salleId':'" + salle1 + "','creneauId':'" + matin + "','jours':null}"), adminA())));
        r.put("salleId absent", statutEtCode(doPost(route(amrani), json("{'creneauId':'" + matin + "','jours':['LUNDI']}"), adminA())));
        r.put("creneauId absent", statutEtCode(doPost(route(amrani), json("{'salleId':'" + salle1 + "','jours':['LUNDI']}"), adminA())));
        r.put("jour inconnu", statutEtCode(doPost(route(amrani), corpsJson(salle1, matin, "FUNDI"), adminA())));
        r.put("jour en minuscules", statutEtCode(doPost(route(amrani), corpsJson(salle1, matin, "lundi"), adminA())));
        r.put("jours contient null", statutEtCode(doPost(route(amrani), json("{'salleId':'" + salle1 + "','creneauId':'" + matin + "','jours':[null]}"), adminA())));
        r.put("salleId illisible", statutEtCode(doPost(route(amrani), json("{'salleId':'pas-un-uuid','creneauId':'" + matin + "','jours':['LUNDI']}"), adminA())));
        r.put("JSON illisible", statutEtCode(doPost(route(amrani), "{", adminA())));
        r.put("corps vide", statutEtCode(doPost(route(amrani), "", adminA())));
        r.put("identifiant de l'infirmier illisible", statutEtCode(doPost(INFIRMIERS + "/pas-un-uuid/affectations", corpsJson(salle1, matin, "LUNDI"), adminA())));
        r.put("champ inconnu ignoré", statutEtCode(doPost(route(benali), json("{'salleId':'" + salle1 + "','creneauId':'" + matin + "','jours':['LUNDI'],'id':'" + UUID.randomUUID() + "','x':1}"), adminA())));
        arbre("demandes_mal_formees", r);
        etatAffectations("demandes_mal_formees_base");
    }

    @Test
    void une_affectation_modifiee_remplace_salle_creneau_et_jours_et_peut_chevaucher_ses_propres_jours() throws Exception {
        String id = idDeLAffectation(doPost(route(amrani), corpsJson(salle1, matin, "LUNDI", "MARDI"), adminA()));
        Resp r = doPut(route(amrani, id), corpsJson(salle2, matin, "MARDI", "MERCREDI"), adminA());
        assertEquals(200, r.status());
        ecriture("modification_nominale", r);
        etatAffectations("modification_nominale_base");
    }

    @Test
    void une_modification_est_refusee_si_elle_chevauche_une_autre_affectation_du_meme_infirmier_et_la_base_est_inchangee() throws Exception {
        String premiere = idDeLAffectation(doPost(route(amrani), corpsJson(salle1, matin, "LUNDI"), adminA()));
        doPost(route(amrani), corpsJson(salle1, apresMidi, "MARDI"), adminA());
        Resp r = doPut(route(amrani, premiere), corpsJson(salle1, apresMidi, "LUNDI", "MARDI"), adminA());
        assertEquals(422, r.status());
        ecriture("modification_chevauchante_refusee", r);
        etatAffectations("modification_chevauchante_refusee_base");
    }

    @Test
    void l_ordre_des_controles_d_une_modification_est_affectation_puis_salle_puis_creneau_puis_chevauchement() throws Exception {
        UUID inconnu = UUID.nameUUIDFromBytes("inconnu".getBytes());
        aliasNommes.put(inconnu.toString(), "<INCONNU>");
        String id = idDeLAffectation(doPost(route(amrani), corpsJson(salle1, matin, "LUNDI"), adminA()));
        doPost(route(amrani), corpsJson(salle2, apresMidi, "LUNDI"), adminA());
        String deBenali = idDeLAffectation(doPost(route(benali), corpsJson(salle1, matin, "MARDI"), adminA()));
        String autreCentre = idDeLAffectation(doPost(INFIRMIERS + "/" + absentDuCentre + "/affectations?centerId=" + CENTRE_B,
                corpsJson(salleB, creneauB, "LUNDI"), adminB()));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("affectation inconnue", statutEtCode(doPut(route(amrani, inconnu.toString()), corpsJson(salle1, matin, "LUNDI"), adminA())));
        r.put("affectation d'un autre infirmier", statutEtCode(doPut(route(amrani, deBenali), corpsJson(salle1, matin, "LUNDI"), adminA())));
        r.put("affectation d'un autre centre", statutEtCode(doPut(route(absentDuCentre, autreCentre), corpsJson(salle1, matin, "LUNDI"), adminA())));
        r.put("infirmier inconnu dans l'URL", statutEtCode(doPut(route(inconnu, id), corpsJson(salle1, matin, "LUNDI"), adminA())));
        r.put("salle inconnue", statutEtCode(doPut(route(amrani, id), corpsJson(inconnu, matin, "LUNDI"), adminA())));
        r.put("salle d'un autre centre", statutEtCode(doPut(route(amrani, id), corpsJson(salleB, matin, "LUNDI"), adminA())));
        r.put("créneau inconnu + chevauchement", statutEtCode(doPut(route(amrani, id), corpsJson(salle1, inconnu, "LUNDI"), adminA())));
        r.put("chevauchement", statutEtCode(doPut(route(amrani, id), corpsJson(salle1, apresMidi, "LUNDI"), adminA())));
        r.put("jours vide", statutEtCode(doPut(route(amrani, id), json("{'salleId':'" + salle1 + "','creneauId':'" + matin + "','jours':[]}"), adminA())));
        r.put("identifiant d'affectation illisible", statutEtCode(doPut(route(amrani, "pas-un-uuid"), corpsJson(salle1, matin, "LUNDI"), adminA())));
        arbre("modification_ordre_des_controles", r);
        etatAffectations("modification_ordre_des_controles_base");
    }

    @Test
    void une_affectation_supprimee_disparait_et_ne_peut_l_etre_deux_fois_ni_par_un_autre_infirmier_ni_depuis_un_autre_centre() throws Exception {
        String id = idDeLAffectation(doPost(route(amrani), corpsJson(salle1, matin, "LUNDI"), adminA()));
        String autre = idDeLAffectation(doPost(route(benali), corpsJson(salle1, apresMidi, "LUNDI"), adminA()));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("par un autre infirmier", statutEtCode(doDelete(route(amrani, autre), adminA())));
        r.put("depuis l'administrateur d'un autre centre", statutEtCode(doDelete(route(amrani, id), adminB())));
        r.put("nominale", statutEtCode(doDelete(route(amrani, id), adminA())));
        r.put("deuxième suppression", statutEtCode(doDelete(route(amrani, id), adminA())));
        r.put("identifiant illisible", statutEtCode(doDelete(route(amrani, "pas-un-uuid"), adminA())));
        arbre("suppression_regles", r);
        etatAffectations("suppression_base");
    }

    @Test
    void le_corps_d_une_suppression_n_est_pas_lu_et_la_reponse_est_vide() throws Exception {
        String id = idDeLAffectation(doPost(route(amrani), corpsJson(salle1, matin, "LUNDI"), adminA()));
        Resp r = doDelete(route(amrani, id), adminA());
        assertEquals(204, r.status());
        ecriture("suppression_reponse", r);
    }

    @Test
    void le_roulement_ecrit_apparait_dans_la_presence_de_la_semaine_et_la_suppression_l_en_retire() throws Exception {
        semaine(1);
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, JourSemaine.LUNDI, JourSemaine.MERCREDI);
        doPost(route(amrani), corpsJson(salle1, matin, "LUNDI", "MERCREDI", "VENDREDI"), adminA());
        String benaliId = idDeLAffectation(doPost(route(benali), corpsJson(salle1, matin, "LUNDI"), adminA()));
        arbre("presence_apres_ecriture", casesDePresence(presenceDeLaSemaine(1, adminA())));
        doDelete(route(benali, benaliId), adminA());
        arbre("presence_apres_suppression", casesDePresence(presenceDeLaSemaine(1, adminA())));
    }

    @Test
    void un_meme_infirmier_sur_deux_salles_a_la_meme_case_n_est_possible_que_par_des_donnees_heritees_et_la_presence_signale_le_conflit() throws Exception {
        semaine(1);
        affectation(CENTRE_A, "HERITEE_1", amrani, salle1, matin, "LUNDI");
        affectation(CENTRE_A, "HERITEE_2", amrani, salle2, matin, "LUNDI");
        arbre("donnees_heritees_conflit_de_presence", casesDePresence(presenceDeLaSemaine(1, adminA())));
        // l'écriture d'une troisième est refusée, la modification de l'une d'elles aussi (elle chevauche l'autre)
        Resp r = doPut(route(amrani, UUID.nameUUIDFromBytes("planification-p2-affectation-HERITEE_1".getBytes()).toString()),
                corpsJson(salle1, matin, "LUNDI"), adminA());
        ecriture("donnees_heritees_modification_refusee", r);
    }

    @Test
    void les_roles_hors_administrateur_ne_modifient_rien_et_les_droits_passent_avant_le_corps() throws Exception {
        String id = idDeLAffectation(doPost(route(amrani), corpsJson(salle1, matin, "LUNDI"), adminA()));
        Map<String, Object> r = new LinkedHashMap<>();
        for (Map.Entry<String, org.springframework.test.web.servlet.request.RequestPostProcessor> p : profils().entrySet()) {
            if (p.getKey().equals("ADMIN") || p.getKey().equals("SUPERADMIN + ADMIN")) continue;
            r.put("POST corps valide [" + p.getKey() + "]", statutEtCode(doPost(route(benali), corpsJson(salle1, apresMidi, "MARDI"), p.getValue())));
            r.put("POST corps vide [" + p.getKey() + "]", statutEtCode(doPost(route(benali), "{}", p.getValue())));
            r.put("DELETE [" + p.getKey() + "]", statutEtCode(doDelete(route(amrani, id), p.getValue())));
        }
        arbre("roles_refuses_et_ordre_corps_autorisation", r);
        assertEquals(1, nombreDe("infirmier_affectation"));
    }

    @Test
    void deux_ajouts_simultanes_qui_se_chevauchent_ne_sont_serialises_par_aucun_verrou_issue_non_deterministe_KB_PLN_25() throws Exception {
        int deuxLignes = 0;
        int uneLigne = 0;
        for (int tour = 0; tour < 6; tour++) {
            jdbc.update("DELETE FROM infirmier_affectation WHERE center_id = ?", CENTRE_A);
            List<Resp> reponses = enParallele(
                    () -> doPost(route(amrani), corpsJson(salle1, matin, "LUNDI", "MARDI"), adminA()),
                    () -> doPost(route(amrani), corpsJson(salle2, matin, "MARDI", "MERCREDI"), adminA()));
            List<Integer> statuts = new ArrayList<>(reponses.stream().map(Resp::status).toList());
            statuts.sort(Integer::compare);
            int lignes = nombreDe("infirmier_affectation");
            // invariants seuls : jamais un 409 ni une erreur serveur ; autant de lignes que de 201
            assertTrue(statuts.equals(List.of(201, 201)) || statuts.equals(List.of(201, 422)), statuts.toString());
            assertEquals(statuts.stream().filter(s -> s == 201).count(), lignes);
            if (lignes == 2) deuxLignes++;
            else uneLigne++;
        }
        System.out.println("[KB-PLN-25] deux ajouts simultanes chevauchants : 2 lignes " + deuxLignes + " fois, 1 ligne " + uneLigne + " fois sur 6");
    }
}
