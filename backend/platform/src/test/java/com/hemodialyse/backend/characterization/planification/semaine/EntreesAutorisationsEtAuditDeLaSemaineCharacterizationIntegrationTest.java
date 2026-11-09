package com.hemodialyse.backend.characterization.planification.semaine;

import com.hemodialyse.backend.characterization.planification.SemaineCharacterizationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.hemodialyse.backend.characterization.planification.JourSemaine.LUNDI;
import static com.hemodialyse.backend.characterization.planification.JourSemaine.MERCREDI;

/**
 * Filet de caractérisation P6 : entrées (date et centre illisibles, méthodes HTTP), garde de centre, matrice des onze profils et de
 * l'anonyme sur {@code GET /api/v1/planning/semaine}, audit et diffusions d'une lecture.
 */
class EntreesAutorisationsEtAuditDeLaSemaineCharacterizationIntegrationTest extends SemaineCharacterizationSupport {

    @Override
    protected String theme() {
        return "p6-semaine/entrees";
    }

    @Test
    void une_date_illisible_est_refusee_et_les_formes_voisines_sont_acceptees_ou_refusees_telles_quelles() throws Exception {
        petitDecor();
        Map<String, Object> m = new LinkedHashMap<>();
        String[][] formes = {{"texte", "abc"}, {"mois 13", "2026-13-01"}, {"30 fevrier", "2026-02-30"}, {"sans tirets", "20260101"},
                {"sans zeros", "2026-1-1"}, {"vide", ""}, {"avec heure", "2026-01-01T00:00:00"}, {"jj/mm/aaaa", "01/01/2026"},
                {"annee signee", "+2026-01-01"}, {"annee negative", "-0001-01-01"}};
        for (String[] forme : formes) {
            m.put("date : " + forme[0], statutEtCode(doGet(SEMAINE + "?date=" + forme[1], adminA())));
        }
        arbre("date_illisible", m);
    }

    @Test
    void un_centre_illisible_ou_d_un_autre_centre_est_refuse_et_le_centre_de_la_session_est_accepte() throws Exception {
        petitDecor();
        nommerLaSemaine(1);
        String date = "date=" + semaine(1, MERCREDI);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centre de la session", statutEtCode(doGet(SEMAINE + "?centerId=" + CENTRE_A + "&" + date, adminA())));
        m.put("autre centre", statutEtCode(doGet(SEMAINE + "?centerId=" + CENTRE_B + "&" + date, adminA())));
        m.put("centre illisible", statutEtCode(doGet(SEMAINE + "?centerId=abc&" + date, adminA())));
        m.put("centre vide", statutEtCode(doGet(SEMAINE + "?centerId=&" + date, adminA())));
        m.put("centre inconnu", statutEtCode(doGet(SEMAINE + "?centerId=" + java.util.UUID.nameUUIDFromBytes("x".getBytes()) + "&" + date, adminA())));
        m.put("session sans centre", statutEtCode(doGet(SEMAINE + "?" + date, as(null, "ADMIN"))));
        m.put("session sans centre avec centre demande", statutEtCode(doGet(SEMAINE + "?centerId=" + CENTRE_A + "&" + date, as(null, "ADMIN"))));
        m.put("SUPERADMIN sur un autre centre", statutEtCode(doGet(SEMAINE + "?centerId=" + CENTRE_B + "&" + date, as(CENTRE_A, "SUPERADMIN"))));
        m.put("SUPERADMIN sans centre demande", statutEtCode(doGet(SEMAINE + "?" + date, as(CENTRE_A, "SUPERADMIN"))));
        m.put("SUPERADMIN sans centre de session ni demande", statutEtCode(doGet(SEMAINE + "?" + date, as(null, "SUPERADMIN"))));
        arbre("garde_de_centre", m);
    }

    @Test
    void un_centre_ne_voit_que_ses_patients_et_ses_salles() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_A", salle1, matin, g1a, LUNDI);
        patientPlace(CENTRE_B, "PAT_B", salleB, creneauB, LUNDI);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centre A", projectionDeLaSemaine(lireLaSemaine(1, adminA())).get("cases"));
        m.put("centre B", projectionDeLaSemaine(lireLaSemaine(1, adminB())));
        arbre("isolation_de_centre", m);
    }

    @Test
    void les_autres_methodes_et_les_parametres_inconnus() throws Exception {
        petitDecor();
        nommerLaSemaine(1);
        String date = "?date=" + semaine(1, MERCREDI);
        Map<String, Object> m = new LinkedHashMap<>();
        for (String methode : new String[]{"POST", "PUT", "DELETE", "PATCH"}) {
            m.put(methode, statutEtCode(appel(methode, SEMAINE + date, "{}", adminA())));
        }
        m.put("parametre inconnu ignore", statutEtCode(doGet(SEMAINE + date + "&inconnu=1", adminA())));
        m.put("date repetee", statutEtCode(doGet(SEMAINE + date + "&date=" + semaine(1, LUNDI), adminA())));
        m.put("sous-chemin", statutEtCode(doGet(SEMAINE + "/autre" + date, adminA())));
        m.put("slash final", statutEtCode(doGet(SEMAINE + "/" + date, adminA())));
        arbre("methodes_et_parametres", m);
    }

    @Test
    void la_semaine_selon_les_onze_profils_et_l_anonyme() throws Exception {
        petitDecor();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        nommerLaSemaine(1);
        Map<String, Object> matrice = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> profil : profils().entrySet()) {
            Resp r = doGet(SEMAINE + "?date=" + semaine(1, MERCREDI), profil.getValue());
            matrice.put(profil.getKey(), statutEtCode(r) + (r.status() == 200 ? " cellules=" + corps(r).path("cellules").size() : ""));
        }
        matrice.put("anonyme", statutEtCode(doGetAnonymous(SEMAINE + "?date=" + semaine(1, MERCREDI))));
        arbre("matrice_profils", matrice);
    }

    @Test
    void les_profils_a_centre_unique_lisent_leur_centre_et_le_refus_ne_depend_pas_du_centre_demande() throws Exception {
        petitDecor();
        nommerLaSemaine(1);
        Map<String, Object> matrice = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> profil : profils().entrySet()) {
            matrice.put(profil.getKey() + " / centre B demande",
                    statutEtCode(doGet(SEMAINE + "?centerId=" + CENTRE_B + "&date=" + semaine(1, MERCREDI), profil.getValue())));
        }
        arbre("matrice_profils_autre_centre", matrice);
    }

    @Test
    void une_lecture_n_est_pas_auditee_ou_l_est_telle_quelle_et_n_emet_aucune_diffusion() throws Exception {
        petitDecor();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        monterLAudit();
        lireLaSemaine(1);
        audit("audit_de_la_lecture");
        diffusionsCapturees("diffusions_de_la_lecture");
    }
}
