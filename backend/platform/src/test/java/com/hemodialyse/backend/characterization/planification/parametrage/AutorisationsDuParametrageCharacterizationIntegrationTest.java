package com.hemodialyse.backend.characterization.planification.parametrage;

import com.hemodialyse.backend.characterization.planification.PlanificationCharacterizationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation P1 : profils autorisés sur les neuf routes du paramétrage (RG-PLN-005, RG-PLN-092/097/098 :
 * {@code @PreAuthorize} de méthode et de classe, {@code RoleScopeFilter}, anonyme). Chaque cellule repart d'une base
 * réinitialisée ; le résultat est « statut HTTP (et code) / la requête a-t-elle écrit ? ».
 */
class AutorisationsDuParametrageCharacterizationIntegrationTest extends PlanificationCharacterizationSupport {

    @Override
    protected String theme() {
        return "p1-parametrage/autorisations";
    }

    private interface Appel {
        Resp executer(RequestPostProcessor who) throws Exception;
    }

    private UUID patient;
    private UUID infirmier;
    private UUID creneau;

    private void amorcer() {
        cleanupPlanification();
        aliasNommes.clear();
        creneau = creneau(CENTRE_A, "CRENEAU_1", "Matin");
        patient = patient(CENTRE_A, "PATIENT_1", "Benali", "Karim", null, null);
        infirmier = infirmier(CENTRE_A, "INFIRMIER_1", "Amrani", "Samia", "INFIRMIER", true);
    }

    private int ecritures() {
        int n = 0;
        for (String t : new String[]{"planning_parametres", "planification_reglages", "planning_preference_patient",
                "infirmier_profil_planning"}) {
            n += jdbc.queryForObject("SELECT COUNT(*) FROM " + t + " WHERE center_id = ?", Integer.class, CENTRE_A);
        }
        return n;
    }

    @Test
    void les_neuf_routes_selon_les_onze_profils_et_l_anonyme() throws Exception {
        amorcer();
        Map<String, Appel> routes = new LinkedHashMap<>();
        routes.put("GET /parametres", who -> who == null ? doGetAnonymous(PLANNING + "/parametres") : doGet(PLANNING + "/parametres", who));
        routes.put("PUT /parametres", who -> corpsPut(PLANNING + "/parametres", "{'joursOuverts':['LUNDI']}", who));
        routes.put("GET /preferences/reglages", who -> who == null ? doGetAnonymous(PLANNING + "/preferences/reglages")
                : doGet(PLANNING + "/preferences/reglages", who));
        routes.put("PUT /preferences/reglages", who -> corpsPut(PLANNING + "/preferences/reglages",
                "{'replanificationAuto':true,'heuresParVacation':5,'heuresHebdoTempsPlein':40,'reposHebdoMin':1}", who));
        routes.put("GET /preferences/patients", who -> who == null ? doGetAnonymous(PLANNING + "/preferences/patients")
                : doGet(PLANNING + "/preferences/patients", who));
        routes.put("PUT /preferences/patients/{id}", who -> corpsPut(PLANNING + "/preferences/patients/" + patient,
                "{'seancesParSemaine':3,'joursAChoisir':true}", who));
        routes.put("GET /preferences/infirmiers", who -> who == null ? doGetAnonymous(PLANNING + "/preferences/infirmiers")
                : doGet(PLANNING + "/preferences/infirmiers", who));
        routes.put("PUT /preferences/infirmiers/{id}", who -> corpsPut(PLANNING + "/preferences/infirmiers/" + infirmier,
                "{'tauxActivite':80,'competences':['CATHETER']}", who));

        Map<String, Object> matrice = new LinkedHashMap<>();
        for (Map.Entry<String, Appel> route : routes.entrySet()) {
            Map<String, RequestPostProcessor> profils = new LinkedHashMap<>(profils());
            profils.put("anonyme", null);
            for (Map.Entry<String, RequestPostProcessor> profil : profils.entrySet()) {
                amorcer();
                Resp r = route.getValue().executer(profil.getValue());
                boolean ecrit = ecritures() > 0;
                matrice.put(route.getKey() + " [" + profil.getKey() + "]",
                        statutEtCode(r) + (route.getKey().startsWith("PUT") ? " / " + (ecrit ? "ecrit" : "rien ecrit") : ""));
            }
        }
        goldenNode("matrice_routes_x_profils", mapper.valueToTree(matrice));
    }

    private Resp corpsPut(String path, String corps, RequestPostProcessor who) throws Exception {
        return who == null ? doPutAnonymous(path, json(corps)) : doPut(path, json(corps), who);
    }

    @Test
    void les_deux_formes_de_refus_filtre_de_perimetre_et_autorisation_de_methode_et_leur_corps() throws Exception {
        amorcer();
        goldenSimple("refus_medecin_seul_lecture_filtre_de_perimetre", doGet(PLANNING + "/parametres", as(CENTRE_A, "MEDECIN")));
        goldenSimple("refus_infirmier_seul_lecture_filtre_de_perimetre", doGet(PLANNING + "/parametres", as(CENTRE_A, "INFIRMIER")));
        goldenSimple("refus_secretaire_ecriture_autorisation_de_methode",
                doPut(PLANNING + "/parametres", json("{'joursOuverts':['LUNDI']}"), as(CENTRE_A, "SECRETAIRE")));
        goldenSimple("refus_secretaire_ecriture_reglages_autorisation_de_methode", doPut(PLANNING + "/preferences/reglages",
                json("{'replanificationAuto':true,'heuresParVacation':5,'heuresHebdoTempsPlein':40,'reposHebdoMin':1}"), as(CENTRE_A, "SECRETAIRE")));
        goldenSimple("refus_role_inconnu_lecture", doGet(PLANNING + "/preferences/patients", as(CENTRE_A, "COMPTABLE")));
        goldenSimple("refus_anonyme_lecture", doGetAnonymous(PLANNING + "/parametres"));
        goldenSimple("refus_direction_lecture", doGet(PLANNING + "/parametres", direction()));
    }
}
