package com.hemodialyse.backend.characterization.organisation;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation O2/O3 : administration des référentiels du centre ({@code /api/v1/admin/referentials}, salles, créneaux,
 * médecins), qui reste servie par la plateforme (admin générique relocalisé en O5). Droits (11 profils + anonyme), garde de centre,
 * création et validations. Sert de repère de parité : ces routes ne doivent pas bouger pendant la refonte d'Organisation.
 */
class AdministrationDesReferentielsCharacterizationIntegrationTest extends OrganisationCharacterizationSupport {

    private static final String A = "/api/v1/admin/referentials";

    @Override
    protected String theme() {
        return "o2-administration";
    }

    private interface Appel {
        Resp executer(RequestPostProcessor who) throws Exception;
    }

    private UUID salleA;
    private UUID creneauA;
    private UUID medecinA;

    private void amorcer() {
        reinitialiser();
        salleA = salle(CENTRE_A, "SALLE_A1", "S1", "Salle une");
        creneauA = creneau(CENTRE_A, "CRENEAU_A1", "P1", "Matin");
        medecinA = medecin(CENTRE_A, "MEDECIN_A1", "Benali", "Karim", "Néphrologue");
    }

    @Test
    void les_routes_selon_les_onze_profils_et_l_anonyme() throws Exception {
        Map<String, Appel> routes = new LinkedHashMap<>();
        routes.put("GET /admin/referentials", w -> doGet(A, w));
        routes.put("GET /admin/referentials/salle", w -> doGet(A + "/salles?centerId=" + CENTRE_A, w));
        routes.put("GET /admin/referentials/creneau", w -> doGet(A + "/creneaux?centerId=" + CENTRE_A, w));
        routes.put("GET /admin/referentials/medecin", w -> doGet(A + "/medecins?centerId=" + CENTRE_A, w));
        routes.put("POST /admin/referentials/salle", w -> doPost(A + "/salles", "{'centerId':'" + CENTRE_A + "','values':{'code':'S9','nom':'Neuf','isolement':'NON'}}", w));
        routes.put("PUT /admin/referentials/salle/{id}", w -> doPut(A + "/salles/" + salleA, "{'centerId':'" + CENTRE_A + "','values':{'code':'S1','nom':'Renommée','isolement':'NON'}}", w));
        routes.put("DELETE /admin/referentials/salle/{id}", w -> doDelete(A + "/salles/" + salleA + "?centerId=" + CENTRE_A, w));
        routes.put("POST /admin/referentials/creneau", w -> doPost(A + "/creneaux", "{'centerId':'" + CENTRE_A + "','values':{'code':'P9','libelle':'Nuit'}}", w));
        routes.put("DELETE /admin/referentials/creneau/{id}", w -> doDelete(A + "/creneaux/" + creneauA + "?centerId=" + CENTRE_A, w));
        routes.put("POST /admin/referentials/medecin", w -> doPost(A + "/medecins", "{'centerId':'" + CENTRE_A + "','values':{'nom':'Nouveau','prenom':'Doc','specialite':'X'}}", w));
        routes.put("DELETE /admin/referentials/medecin/{id}", w -> doDelete(A + "/medecins/" + medecinA + "?centerId=" + CENTRE_A, w));
        routes.put("GET /admin/referentials/salle/template", w -> doGet(A + "/salles/template?format=csv", w));

        ObjectNode matrice = mapper.createObjectNode();
        for (var route : routes.entrySet()) {
            ObjectNode ligne = mapper.createObjectNode();
            for (var profil : profils().entrySet()) {
                amorcer();
                ligne.put(profil.getKey(), statutEtCode(route.getValue().executer(profil.getValue())));
            }
            amorcer();
            ligne.put("anonyme", statutEtCode(route.getValue().executer(null)));
            matrice.set(route.getKey(), ligne);
        }
        goldenNode("matrice-des-droits", matrice);
    }

    @Test
    void garde_de_centre_et_contenu_des_listes() throws Exception {
        amorcer();
        golden("liste-des-salles", doGet(A + "/salles?centerId=" + CENTRE_A, as(CENTRE_A, "ADMIN")));
        golden("liste-des-creneaux", doGet(A + "/creneaux?centerId=" + CENTRE_A, as(CENTRE_A, "ADMIN")));
        golden("liste-des-medecins", doGet(A + "/medecins?centerId=" + CENTRE_A, as(CENTRE_A, "ADMIN")));
        golden("liste-des-salles-d-un-autre-centre", doGet(A + "/salles?centerId=" + CENTRE_B, as(CENTRE_A, "ADMIN")));
        golden("liste-sans-centre-demande", doGet(A + "/salles", as(CENTRE_A, "ADMIN")));
        golden("referentiel-inconnu", doGet(A + "/inconnu?centerId=" + CENTRE_A, as(CENTRE_A, "ADMIN")));
    }

    @Test
    void creation_modification_et_suppression_d_une_salle() throws Exception {
        amorcer();
        golden("creation-salle", doPost(A + "/salles", "{'centerId':'" + CENTRE_A + "','values':{'code':'S9','nom':'Neuf','isolement':'NON'}}", as(CENTRE_A, "ADMIN")));
        golden("creation-salle-sans-nom", doPost(A + "/salles", "{'centerId':'" + CENTRE_A + "','values':{'code':'S8'}}", as(CENTRE_A, "ADMIN")));
        golden("creation-salle-dans-un-autre-centre", doPost(A + "/salles", "{'centerId':'" + CENTRE_B + "','values':{'code':'S7','nom':'x','isolement':'NON'}}", as(CENTRE_A, "ADMIN")));
        golden("modification-salle", doPut(A + "/salles/" + salleA, "{'centerId':'" + CENTRE_A + "','values':{'code':'S1','nom':'Renommée','isolement':'OUI'}}", as(CENTRE_A, "ADMIN")));
        golden("suppression-salle", doDelete(A + "/salles/" + salleA + "?centerId=" + CENTRE_A, as(CENTRE_A, "ADMIN")));
        golden("suppression-salle-inconnue", doDelete(A + "/salles/" + UUID.fromString("99998200-0000-0000-0000-0000000000e9") + "?centerId=" + CENTRE_A, as(CENTRE_A, "ADMIN")));
        goldenSql("salles-en-base", "SELECT center_id, code, nom FROM salle WHERE center_id IN (?, ?) ORDER BY code", CENTRE_A, CENTRE_B);
    }
}
