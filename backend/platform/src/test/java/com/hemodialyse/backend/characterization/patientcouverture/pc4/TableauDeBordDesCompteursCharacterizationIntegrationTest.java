package com.hemodialyse.backend.characterization.patientcouverture.pc4;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PL3 : filet complémentaire des deux routes du tableau de bord ({@code GET /api/v1/dashboard/stats}, {@code POST /stats/search}) :
 * cas vide, cas riche (PEC, attestations, effectif, mois), isolement par centre, droits (onze profils, anonyme, centre de session),
 * refus de forme. Le jour du centre est déjà verrouillé par {@code JourDuCentreCharacterizationIntegrationTest}, les attestations
 * à échéance par {@code EffetsEtLecteursDesAttestations...}.
 */
class TableauDeBordDesCompteursCharacterizationIntegrationTest extends AttestationCharacterizationSupport {

    private static final String STATS = "/api/v1/dashboard/stats";

    @Override
    protected String dossier() {
        return "tableau_de_bord";
    }

    private String compteurs(Resp r) {
        return r.status() == 200 ? corps(r).toString() : refus(r);
    }

    @Test
    void un_centre_sans_donnee_rend_des_compteurs_a_zero_par_les_deux_routes() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("GET", compteurs(doGet(STATS + "?centerId=" + CENTRE_A, adminA())));
        m.put("GET avec mois", compteurs(doGet(STATS + "?centerId=" + CENTRE_A + "&month=2026-01", adminA())));
        m.put("POST", compteurs(doPost(STATS + "/search", json("{'centerId':'" + CENTRE_A + "'}"), adminA())));
        arbre("centre_vide", m);
    }

    @Test
    void les_compteurs_de_prises_en_charge_d_attestations_et_de_patients_distinguent_statut_mois_et_centre() throws Exception {
        supposerLeJourDeLaJvmEgalAuJourDuCentre();
        UUID forfait = forfait(CENTRE_A, "HD3", "9500.00");
        UUID p1 = patientCouvert(CENTRE_A, "RICHE_1", false, "ENFANT");
        UUID p2 = patientCouvert(CENTRE_A, "RICHE_2", false, "ENFANT");
        pecEnBase(CENTRE_A, "R_CREE", p1, "CREE", jour(-10), jour(10), forfait, null, null, null, 1);
        pecEnBase(CENTRE_A, "R_VAL_1", p1, "VALIDEE", jour(-10), jour(10), forfait, jour(-10), jour(10), forfait, 2);
        pecEnBase(CENTRE_A, "R_VAL_2", p2, "VALIDEE", jour(-100), jour(-50), forfait, jour(-100), jour(-50), forfait, 3);
        pecEnBase(CENTRE_A, "R_VAL_3", p2, "VALIDEE", jour(-10), jour(200), forfait, jour(-10), jour(200), forfait, 4);
        pecEnBase(CENTRE_A, "R_CLOT", p2, "CLOTUREE", jour(-300), jour(-200), forfait, jour(-300), jour(-200), forfait, 5);
        attestationEnBase(CENTRE_A, "R_ATT_1", p1, jour(-5), jour(10));
        attestationEnBase(CENTRE_A, "R_ATT_2", p2, jour(-5), jour(100));
        UUID forfaitB = forfait(CENTRE_B, "HD3", "9500.00");
        UUID pb = patientCouvert(CENTRE_B, "RICHE_B", false, "ENFANT");
        pecEnBase(CENTRE_B, "RB_VAL", pb, "VALIDEE", jour(-10), jour(10), forfaitB, jour(-10), jour(10), forfaitB, 6);
        attestationEnBase(CENTRE_B, "RB_ATT", pb, jour(-5), jour(10));
        viderLesCaches();

        Map<String, Object> m = new LinkedHashMap<>();
        for (String requete : new String[]{"", "&expirationDays=0", "&expirationDays=15", "&expirationDays=365", "&month=2026-01",
                "&month=2026-02", "&month=2026-1", "&month=n-importe-quoi", "&month=&expirationDays=15"}) {
            m.put("A GET" + requete, compteurs(doGet(STATS + "?centerId=" + CENTRE_A + requete, adminA())));
        }
        m.put("B GET", compteurs(doGet(STATS + "?centerId=" + CENTRE_B, adminB())));
        m.put("A POST expirationDays=15 mois=2026-01", compteurs(doPost(STATS + "/search",
                json("{'centerId':'" + CENTRE_A + "','expirationDays':15,'month':'2026-01'}"), adminA())));
        m.put("A POST sans expirationDays (défaut 30)", compteurs(doPost(STATS + "/search", json("{'centerId':'" + CENTRE_A + "'}"), adminA())));
        arbre("compteurs_riches_nouveau_code", m);
    }

    @Test
    void les_droits_du_tableau_de_bord_par_profil_anonyme_et_centre_de_session() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profils().entrySet()) {
            m.put("GET " + p.getKey(), statutEtCode(doGet(STATS + "?centerId=" + CENTRE_A, p.getValue())));
        }
        for (String role : new String[]{"PHARMACIEN", "DIRECTEUR_ADMINISTRATIF", "CHEF_DE_SERVICE"}) {
            m.put("GET " + role, statutEtCode(doGet(STATS + "?centerId=" + CENTRE_A, as(CENTRE_A, role))));
        }
        for (Map.Entry<String, RequestPostProcessor> p : profils().entrySet()) {
            m.put("POST " + p.getKey(), statutEtCode(doPost(STATS + "/search", json("{'centerId':'" + CENTRE_A + "'}"), p.getValue())));
        }
        m.put("GET anonyme", statutEtCode(anonyme("GET", STATS + "?centerId=" + CENTRE_A, null)));
        m.put("POST anonyme", statutEtCode(anonyme("POST", STATS + "/search", "{\"centerId\":\"" + CENTRE_A + "\"}")));
        arbre("droits_par_profil_nouveau_code", m);
    }

    @Test
    void le_centre_de_la_requete_est_compare_a_celui_de_la_session() throws Exception {
        supposerLeJourDeLaJvmEgalAuJourDuCentre();
        UUID p = patientCouvert(CENTRE_A, "GARDE_A", false, "ENFANT");
        attestationEnBase(CENTRE_A, "G_ATT", p, jour(-5), jour(10));
        pecEnBase(CENTRE_A, "G_CREE", p, "CREE", jour(-10), jour(10), forfait(CENTRE_A, "HD3", "9500.00"), null, null, null, 1);
        viderLesCaches();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("GET centre A par l'ADMIN du centre B", compteurs(doGet(STATS + "?centerId=" + CENTRE_A, adminB())));
        m.put("POST centre A par l'ADMIN du centre B", compteurs(doPost(STATS + "/search", json("{'centerId':'" + CENTRE_A + "'}"), adminB())));
        m.put("GET centre A par un ADMIN sans centre de session", compteurs(doGet(STATS + "?centerId=" + CENTRE_A, as(null, "ADMIN"))));
        m.put("POST centre A par un ADMIN sans centre de session", compteurs(doPost(STATS + "/search", json("{'centerId':'" + CENTRE_A + "'}"), as(null, "ADMIN"))));
        m.put("GET centre inconnu par l'ADMIN du centre A", compteurs(doGet(STATS + "?centerId=" + CENTRE_INCONNU, adminA())));
        arbre("centre_de_session_nouveau_code", m);
    }

    @Test
    void les_requetes_mal_formees_sont_refusees_ou_degradees() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("GET sans centerId", compteurs(doGet(STATS, adminA())));
        m.put("GET centerId illisible", compteurs(doGet(STATS + "?centerId=abc", adminA())));
        m.put("GET expirationDays illisible", compteurs(doGet(STATS + "?centerId=" + CENTRE_A + "&expirationDays=abc", adminA())));
        m.put("GET expirationDays négatif", compteurs(doGet(STATS + "?centerId=" + CENTRE_A + "&expirationDays=-5", adminA())));
        m.put("POST corps absent", compteurs(doPost(STATS + "/search", "", adminA())));
        m.put("POST JSON invalide", compteurs(doPost(STATS + "/search", "{centerId", adminA())));
        m.put("POST objet vide", compteurs(doPost(STATS + "/search", "{}", adminA())));
        m.put("POST centerId nul", compteurs(doPost(STATS + "/search", json("{'centerId':null}"), adminA())));
        m.put("POST centerId illisible", compteurs(doPost(STATS + "/search", json("{'centerId':'abc'}"), adminA())));
        m.put("POST expirationDays illisible", compteurs(doPost(STATS + "/search", json("{'centerId':'" + CENTRE_A + "','expirationDays':'x'}"), adminA())));
        m.put("POST mois illisible", compteurs(doPost(STATS + "/search", json("{'centerId':'" + CENTRE_A + "','month':'2026-13'}"), adminA())));
        arbre("requetes_mal_formees_nouveau_code", m);
    }
}
