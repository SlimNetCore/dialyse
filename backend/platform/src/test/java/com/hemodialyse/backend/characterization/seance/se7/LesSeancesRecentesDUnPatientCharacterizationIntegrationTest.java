package com.hemodialyse.backend.characterization.seance.se7;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SE7a : les dernières séances d'un patient avec leurs constantes ({@code GET /api/v1/seances/patient/{id}/recentes}, servi par
 * {@code SeanceStationRestController}, ADMIN, INFIRMIER et MEDECIN) : tri, borne {@code before}, {@code limit}, séances sans volet,
 * cloisonnement, droits. Les constantes d'un patient sont des données de santé CONFIDENTIELLES ; le cloisonnement par centre est donc
 * observé. Décrit le comportement ACTUEL.
 */
class LesSeancesRecentesDUnPatientCharacterizationIntegrationTest extends Se7CharacterizationSupport {

    @Override
    protected String dossier() {
        return "recentes";
    }

    private UUID cinqSeances(UUID centre, UUID patient) {
        UUID derniere = null;
        for (int i = 1; i <= 5; i++) {
            UUID s = seanceValidee(centre, patient, "S_" + i + (centre.equals(CENTRE_A) ? "_A" : "_B"), jour(-(i + 2)), i == 5 ? "CREE" : "SIGNEE");
            if (i != 3) voletParamedicalComplet(centre, s, "70." + i, "68." + i, "13" + i + "/80", "12" + i + "/70", 200 + i, "100" + i);
            if (i == 1) derniere = s;
        }
        return derniere;
    }

    @Test
    void les_seances_recentes_sont_triees_de_la_plus_recente_a_la_plus_ancienne_trois_par_defaut_sans_la_borne_haute() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        cinqSeances(CENTRE_A, patient);
        // Le jour par defaut de « before » est celui de la JVM, a un jour pres du jour du centre : aucune seance entre J-2 et J+9.
        seanceValidee(CENTRE_A, patient, "S_FUTUR", jour(10), "VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 defaut (before = jour du centre, limit = 3)", reponse(recentes(patient, null, infirmierA())));
        m.put("02 before = J-5 (la seance de J-5 incluse ou non)", reponse(recentes(patient, "before=" + jour(-5), infirmierA())));
        m.put("03 limit = 1", reponse(recentes(patient, "limit=1", infirmierA())));
        m.put("04 limit = 10 (toutes)", reponse(recentes(patient, "limit=10", infirmierA())));
        m.put("05 limit = 0", reponse(recentes(patient, "limit=0", infirmierA())));
        m.put("06 limit negatif", reponse(recentes(patient, "limit=-1", infirmierA())));
        m.put("07 limit texte", refus(recentes(patient, "limit=trois", infirmierA())));
        m.put("08 before illisible", refus(recentes(patient, "before=demain", infirmierA())));
        m.put("09 before tres ancien", reponse(recentes(patient, "before=1900-01-01", infirmierA())));
        m.put("10 centre explicite = centre de la session", reponse(recentes(patient, "centerId=" + CENTRE_A, infirmierA())));
        arbre("tri_et_bornes", m);
    }

    @Test
    void les_seances_recentes_d_un_patient_inconnu_ou_d_un_autre_centre_sont_vides_ou_refusees() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        cinqSeances(CENTRE_A, patient);
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        cinqSeances(CENTRE_B, patientB);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 patient inconnu", reponse(recentes(UUID.randomUUID(), null, infirmierA())));
        m.put("02 patient du centre B lu par la session A", reponse(recentes(patientB, null, infirmierA())));
        m.put("03 patient du centre B avec centerId = B demande par la session A", refus(recentes(patientB, "centerId=" + CENTRE_B, infirmierA())));
        m.put("04 patient du centre A avec centerId = B demande par la session A", refus(recentes(patient, "centerId=" + CENTRE_B, infirmierA())));
        m.put("05 identifiant de patient illisible", refus(doGet(SEANCES_API + "/patient/xx/recentes", infirmierA())));
        m.put("06 patient du centre B lu par la session B", reponse(recentes(patientB, null, infirmierB())));
        arbre("cloisonnement", m);
    }

    @Test
    void les_droits_des_seances_recentes_par_profil() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        cinqSeances(CENTRE_A, patient);
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            m.put(p.getKey(), refus(recentes(patient, "limit=1", p.getValue())));
        }
        m.put("anonyme", refus(doGetAnonyme(SEANCES_API + "/patient/" + patient + "/recentes?limit=1")));
        arbre("droits", m);
    }
}
