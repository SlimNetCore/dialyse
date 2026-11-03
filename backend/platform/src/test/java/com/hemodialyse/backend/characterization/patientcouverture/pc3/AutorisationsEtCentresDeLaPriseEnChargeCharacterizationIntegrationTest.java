package com.hemodialyse.backend.characterization.patientcouverture.pc3;

import com.hemodialyse.backend.characterization.patientcouverture.CouvertureCharacterizationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * PC3 : droits d'accès des huit routes de prise en charge (11 profils, rôles réels hors des onze, rôle personnalisé, anonyme) et
 * garde de centre (le centre du corps ou de la requête est-il comparé à celui de la session ?).
 */
class AutorisationsEtCentresDeLaPriseEnChargeCharacterizationIntegrationTest extends CouvertureCharacterizationSupport {

    @Override
    protected String racine() {
        return "../patientcouverture/pc3/";
    }

    @Override
    protected String dossier() {
        return "autorisations";
    }

    private UUID patient;
    private UUID pec;

    private void decor(String statut) {
        jdbc.update("DELETE FROM prise_en_charge WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        caches.getCacheNames().forEach(n -> java.util.Optional.ofNullable(caches.getCache(n)).ifPresent(org.springframework.cache.Cache::clear));
        if (patient == null) patient = patientCouvert(CENTRE_A, "VACANCIER", true, null);
        pec = pecEnBase(CENTRE_A, "PEC_1", patient, statut, jour(-5), jour(25), null, null, null, null, 1);
    }

    private void matrice(String name, String statutDeDepart, Function<RequestPostProcessor, Resp> appel) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profils().entrySet()) {
            decor(statutDeDepart);
            m.put(p.getKey(), statutEtCode(appel.apply(p.getValue())));
        }
        for (String role : new String[]{"PHARMACIEN", "DIRECTEUR_ADMINISTRATIF", "CHEF_DE_SERVICE"}) {
            decor(statutDeDepart);
            m.put(role + (role.equals("CHEF_DE_SERVICE") ? " (rôle personnalisé)" : ""), statutEtCode(appel.apply(as(CENTRE_A, role))));
        }
        arbre(name, m);
    }

    private Resp sans(java.util.concurrent.Callable<Resp> c) {
        try {
            return c.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void les_lectures_de_pec_sont_ouvertes_a_tout_compte_de_centre_et_fermees_a_la_direction_et_au_superadmin() throws Exception {
        matrice("matrice_liste_du_centre", "VALIDEE", p -> sans(() -> pecsDuCentre(CENTRE_A, p)));
        matrice("matrice_liste_du_patient", "VALIDEE", p -> sans(() -> pecsDuPatient(patient, CENTRE_A, p)));
        matrice("matrice_session_autorisee", "VALIDEE", p -> sans(() -> sessionAllowed(pec, CENTRE_A, p)));
        matrice("matrice_recherche", "VALIDEE", p -> sans(() -> doPost(PEC + "/pec-center/search", json("{'centerId':'" + CENTRE_A + "','page':0,'size':10}"), p)));
        decor("VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("liste du centre", statutEtCode(anonyme("GET", PEC + "?centerId=" + CENTRE_A, null)));
        m.put("liste du patient", statutEtCode(anonyme("GET", PEC + "/patient/" + patient + "?centerId=" + CENTRE_A, null)));
        m.put("session autorisée", statutEtCode(anonyme("GET", PEC + "/" + pec + "/session-allowed?centerId=" + CENTRE_A + "&userId=u", null)));
        m.put("recherche", statutEtCode(anonyme("POST", PEC + "/pec-center/search", "{\"centerId\":\"" + CENTRE_A + "\",\"page\":0,\"size\":10}")));
        arbre("anonyme_lectures", m);
    }

    @Test
    void la_creation_et_la_cloture_sont_ouvertes_a_tout_compte_de_centre_la_validation_est_reservee_a_l_admin_et_la_suppression_ouverte() throws Exception {
        matrice("matrice_creation", "VALIDEE", p -> sans(() -> creerLaPec("PEC_X", patient, CENTRE_A, jour(-5), jour(25), null, p)));
        matrice("matrice_validation", "CREE", p -> sans(() -> valider(pec, CENTRE_A, jour(0), jour(20), null, p)));
        matrice("matrice_cloture", "VALIDEE", p -> sans(() -> cloturer(pec, CENTRE_A, p)));
        matrice("matrice_suppression", "CREE", p -> sans(() -> supprimer(pec, CENTRE_A, patient, p)));
        decor("CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("création", statutEtCode(anonyme("POST", PEC, "{\"patientId\":\"" + patient + "\",\"centerId\":\"" + CENTRE_A + "\"}")));
        m.put("validation", statutEtCode(anonyme("POST", PEC + "/" + pec + "/validate", "{\"centerId\":\"" + CENTRE_A + "\"}")));
        m.put("clôture", statutEtCode(anonyme("POST", PEC + "/" + pec + "/close", "{\"centerId\":\"" + CENTRE_A + "\"}")));
        m.put("suppression", statutEtCode(anonyme("DELETE", PEC + "/" + pec + "?centerId=" + CENTRE_A, null)));
        arbre("anonyme_ecritures", m);
        etatDesPec("etat_apres_les_refus_anonymes");
    }

    @Test
    void le_centre_de_la_requete_n_est_jamais_compare_a_celui_de_la_session_toutes_les_routes_de_pec_servent_un_autre_centre() throws Exception {
        patient = patientCouvert(CENTRE_A, "VACANCIER", true, null);
        UUID forfait = forfait(CENTRE_A, "HD3", "9500.00");
        UUID existante = pecEnBase(CENTRE_A, "PEC_A", patient, "CREE", jour(-5), jour(25), forfait, null, null, null, 1);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("liste des PEC du centre A par l'ADMIN du centre B", refus(pecsDuCentre(CENTRE_A, adminB())) + " " + corps(pecsDuCentre(CENTRE_A, adminB())).size() + " PEC");
        m.put("liste des PEC du patient A par l'ADMIN du centre B", String.valueOf(corps(pecsDuPatient(patient, CENTRE_A, adminB())).size()));
        m.put("recherche du centre A par l'ADMIN du centre B", String.valueOf(corps(doPost(PEC + "/pec-center/search", json("{'centerId':'" + CENTRE_A + "','page':0,'size':10}"), adminB())).path("total")));
        m.put("liste du centre A sans centre de session", refus(pecsDuCentre(CENTRE_A, as(null, "ADMIN"))));
        m.put("session autorisée par l'ADMIN du centre B", refus(sessionAllowed(existante, CENTRE_A, adminB())));
        m.put("validation par l'ADMIN du centre B", refus(valider(existante, CENTRE_A, jour(0), jour(20), null, adminB())));
        m.put("clôture par l'ADMIN du centre B", refus(cloturer(existante, CENTRE_A, adminB())));
        Resp creee = creerLaPec("PEC_B", patient, CENTRE_A, jour(-5), jour(25), null, adminB());
        m.put("création dans le centre A par l'ADMIN du centre B", refus(creee));
        m.put("suppression par l'ADMIN du centre B", refus(supprimer(existante, CENTRE_A, patient, adminB())));
        m.put("création par un ADMIN sans centre de session", refus(creerLaPec("PEC_C", patient, CENTRE_A, jour(-5), jour(25), null, as(null, "ADMIN"))));
        m.put("validation par un ADMIN sans centre de session", refus(valider(UUID.fromString(corps(creee).path("id").asString()),
                CENTRE_A, jour(0), jour(20), null, as(null, "ADMIN"))));
        arbre("sans_garde_de_centre", m);
        etatDesPec("etat_final");
    }
}
