package com.hemodialyse.backend.characterization.planification.roulement;

import com.hemodialyse.backend.characterization.planification.RoulementCharacterizationSupport;
import com.hemodialyse.backend.characterization.planification.JourSemaine;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation P3 : profils autorisés sur les douze routes du roulement, des absences, de « Mon planning » et des
 * remplacements (lecture ADMIN, SECRETAIRE, MEDECIN, INFIRMIER ; roulement ADMIN ; absences et remplacements ADMIN ou SECRETAIRE ;
 * « Mon planning » : les quatre rôles de lecture). Chaque cellule repart d'une base réinitialisée ; le résultat est « statut HTTP
 * (et code) / la requête a-t-elle écrit ? ». Les routes de « Mon planning » sont appelées par un compte sans fiche
 * (422 {@code INFIRMIER_NON_LIE} lorsque l'accès est accordé) : l'accès avec une fiche liée est observé dans le filet « Mon planning ».
 */
class AutorisationsDuRoulementEtDesAbsencesCharacterizationIntegrationTest extends RoulementCharacterizationSupport {

    private interface Appel {
        Resp executer(RequestPostProcessor who) throws Exception;
    }

    private UUID amrani;
    private UUID benali;
    private UUID affectationId;
    private UUID remplacementId;
    private UUID absenceDeBenali;
    private LocalDate lundi;

    @Override
    protected String theme() {
        return "p3-roulement/autorisations";
    }

    private void amorcer() {
        nettoyerInfirmiers();
        nettoyerRoulement();
        cleanupPlanification();
        aliasNommes.clear();
        datesNommees.clear();
        decor();
        lundi = semaine(1, JourSemaine.LUNDI);
        semaine(1);
        amrani = inf(CENTRE_A, "INF_AMRANI", "Amrani", "Sara", true);
        benali = inf(CENTRE_A, "INF_BENALI", "Benali", "Karim", false);
        affectation(CENTRE_A, "AFFECTATION_AMRANI", amrani, salle1, matin, "LUNDI,MARDI");
        affectationId = UUID.nameUUIDFromBytes("planification-p2-affectation-AFFECTATION_AMRANI".getBytes());
        absence(CENTRE_A, "ABS_BENALI", benali, lundi, lundi, "CONGE", null);
        absenceDeBenali = absenceId("ABS_BENALI");
        remplacementId = remplacement(CENTRE_A, "REMPL_BENALI", semaine(1, JourSemaine.MARDI), salle1, matin, benali, null);
    }

    @Test
    void les_douze_routes_selon_les_onze_profils_et_l_anonyme() throws Exception {
        amorcer();
        Map<String, Appel> routes = new LinkedHashMap<>();
        routes.put("POST /infirmiers/{id}/affectations", who -> appel("POST", INFIRMIERS + "/" + benali + "/affectations",
                corpsAffectation(salle2, apresMidi, "MERCREDI"), who));
        routes.put("PUT /infirmiers/{id}/affectations/{aid}", who -> appel("PUT", INFIRMIERS + "/" + amrani + "/affectations/" + affectationId,
                corpsAffectation(salle2, matin, "LUNDI"), who));
        routes.put("DELETE /infirmiers/{id}/affectations/{aid}", who -> appel("DELETE", INFIRMIERS + "/" + amrani + "/affectations/" + affectationId, null, who));
        routes.put("GET /infirmiers/absences", who -> appel("GET", INFIRMIERS + "/absences", null, who));
        routes.put("POST /infirmiers/absences", who -> appel("POST", INFIRMIERS + "/absences",
                corpsAbsence(amrani, semaine(1, JourSemaine.MERCREDI), semaine(1, JourSemaine.JEUDI), "CONGE", null), who));
        routes.put("DELETE /infirmiers/absences/{id}", who -> appel("DELETE", INFIRMIERS + "/absences/" + absenceDeBenali, null, who));
        routes.put("GET /infirmiers/moi/planning", who -> appel("GET", INFIRMIERS + "/moi/planning?date=" + lundi, null, who));
        routes.put("GET /infirmiers/moi/absences", who -> appel("GET", INFIRMIERS + "/moi/absences", null, who));
        routes.put("POST /infirmiers/moi/absences", who -> appel("POST", INFIRMIERS + "/moi/absences",
                corpsMonAbsence(semaine(1, JourSemaine.MERCREDI), semaine(1, JourSemaine.JEUDI), "CONGE", null), who));
        routes.put("DELETE /infirmiers/moi/absences/{id}", who -> appel("DELETE", INFIRMIERS + "/moi/absences/" + absenceDeBenali, null, who));
        routes.put("POST /infirmiers/presence/remplacements", who -> appel("POST", INFIRMIERS + "/presence/remplacements",
                corpsRemplacement(semaine(1, JourSemaine.MERCREDI), salle1, matin, amrani, null), who));
        routes.put("DELETE /infirmiers/presence/remplacements/{id}", who -> appel("DELETE", INFIRMIERS + "/presence/remplacements/" + remplacementId, null, who));

        Map<String, Object> matrice = new LinkedHashMap<>();
        for (Map.Entry<String, Appel> route : routes.entrySet()) {
            Map<String, RequestPostProcessor> profils = new LinkedHashMap<>(profils());
            profils.put("anonyme", null);
            for (Map.Entry<String, RequestPostProcessor> profil : profils.entrySet()) {
                amorcer();
                String avant = empreinteP3();
                Resp r = route.getValue().executer(profil.getValue());
                boolean ecrit = !avant.equals(empreinteP3());
                matrice.put(route.getKey() + " [" + profil.getKey() + "]",
                        statutEtCode(r) + (route.getKey().startsWith("GET") ? "" : " / " + (ecrit ? "ecrit" : "rien ecrit")));
            }
        }
        arbre("matrice_routes_x_profils", matrice);
    }

    @Test
    void les_formes_de_refus_filtre_de_perimetre_autorisation_de_methode_et_anonyme_et_leur_corps() throws Exception {
        amorcer();
        ecriture("refus_medecin_seul_lecture_absences_filtre_de_perimetre", doGet(INFIRMIERS + "/absences", as(CENTRE_A, "MEDECIN")));
        ecriture("refus_secretaire_roulement_autorisation_de_methode", doPost(INFIRMIERS + "/" + benali + "/affectations",
                json(corpsAffectation(salle2, apresMidi, "MERCREDI")), secretaire()));
        ecriture("refus_medecin_et_infirmier_absences_ecriture", doPost(INFIRMIERS + "/absences",
                json(corpsAbsence(amrani, semaine(1, JourSemaine.MERCREDI), semaine(1, JourSemaine.MERCREDI), "CONGE", null)),
                as(CENTRE_A, "MEDECIN", "INFIRMIER")));
        ecriture("refus_infirmier_seul_mon_planning_filtre_de_perimetre_ou_fiche", doGet(INFIRMIERS + "/moi/planning", as(CENTRE_A, "INFIRMIER")));
        ecriture("refus_anonyme_absences", doGetAnonymous(INFIRMIERS + "/absences"));
        ecriture("refus_direction_remplacements", doPost(INFIRMIERS + "/presence/remplacements", json(corpsRemplacement(lundi, salle1, matin, amrani, null)), direction()));
        ecriture("refus_admin_sans_centre_absences", doGet(INFIRMIERS + "/absences", as(null, "ADMIN")));
    }

    @Test
    void les_droits_sont_verifies_avant_le_corps_donc_un_role_refuse_recoit_403_meme_avec_un_corps_invalide_KB_PLN_30() throws Exception {
        amorcer();
        Map<String, Object> r = new LinkedHashMap<>();
        RequestPostProcessor medecinInfirmier = as(CENTRE_A, "MEDECIN", "INFIRMIER");
        RequestPostProcessor comptable = as(CENTRE_A, "COMPTABLE");
        r.put("POST absences corps vide [MEDECIN + INFIRMIER]", statutEtCode(doPost(INFIRMIERS + "/absences", "{}", medecinInfirmier)));
        r.put("POST absences corps valide [MEDECIN + INFIRMIER]", statutEtCode(doPost(INFIRMIERS + "/absences",
                json(corpsAbsence(amrani, lundi, lundi, "CONGE", null)), medecinInfirmier)));
        r.put("POST absences JSON illisible [COMPTABLE]", statutEtCode(doPost(INFIRMIERS + "/absences", "{", comptable)));
        r.put("POST remplacements corps vide [MEDECIN + INFIRMIER]", statutEtCode(doPost(INFIRMIERS + "/presence/remplacements", "{}", medecinInfirmier)));
        r.put("POST remplacements corps valide [MEDECIN + INFIRMIER]", statutEtCode(doPost(INFIRMIERS + "/presence/remplacements",
                json(corpsRemplacement(lundi, salle1, matin, amrani, null)), medecinInfirmier)));
        r.put("POST affectations corps vide [SECRETAIRE]", statutEtCode(doPost(INFIRMIERS + "/" + amrani + "/affectations", "{}", secretaire())));
        r.put("POST moi/absences corps vide [COMPTABLE]", statutEtCode(doPost(INFIRMIERS + "/moi/absences", "{}", comptable)));
        r.put("POST moi/absences type inconnu [COMPTABLE]", statutEtCode(doPost(INFIRMIERS + "/moi/absences", json(corpsMonAbsence(lundi, lundi, "VACANCES", null)), comptable)));
        arbre("corps_avant_autorisation", r);
    }

    @Test
    void la_garde_de_centre_borne_chaque_famille_de_routes_au_centre_de_la_session() throws Exception {
        amorcer();
        Map<String, Object> r = new LinkedHashMap<>();
        String[] centres = {"", "?centerId=" + CENTRE_A, "?centerId=" + CENTRE_B, "?centerId=abc"};
        for (String q : centres) {
            amorcer();
            String etiquette = q.isEmpty() ? "sans centerId" : q.contains(CENTRE_A.toString()) ? "centerId = centre de la session" : q.contains(CENTRE_B.toString()) ? "centerId d'un autre centre" : "centerId illisible";
            r.put("GET absences [" + etiquette + "]", statutEtCode(doGet(INFIRMIERS + "/absences" + q, adminA())));
            r.put("POST absences [" + etiquette + "]", statutEtCode(doPost(INFIRMIERS + "/absences" + q, json(corpsAbsence(amrani, lundi, lundi, "CONGE", null)), adminA())));
            r.put("POST affectations [" + etiquette + "]", statutEtCode(doPost(INFIRMIERS + "/" + benali + "/affectations" + q, json(corpsAffectation(salle2, apresMidi, "MERCREDI")), adminA())));
            r.put("POST remplacements [" + etiquette + "]", statutEtCode(doPost(INFIRMIERS + "/presence/remplacements" + q, json(corpsRemplacement(semaine(1, JourSemaine.MERCREDI), salle1, matin, amrani, null)), adminA())));
            r.put("GET moi/absences [" + etiquette + "]", statutEtCode(doGet(INFIRMIERS + "/moi/absences" + q, comme(UUID.nameUUIDFromBytes("x".getBytes()), CENTRE_A, "INFIRMIER"))));
        }
        arbre("garde_de_centre", r);
    }
}
