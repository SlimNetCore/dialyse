package com.hemodialyse.backend.characterization.planification.placement;

import com.hemodialyse.backend.characterization.planification.PlacementCharacterizationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;

/**
 * Filet de caractérisation P5 : profils autorisés sur les routes du placement (aide au placement, vue des salles, enregistrement
 * d'une fiche patient avec son placement, enregistrement d'une sérologie) selon les onze profils d'accès et l'anonyme. Les droits
 * viennent des annotations, du filtre de périmètre ({@code RoleScopeFilter}) et de la garde de centre.
 */
class AutorisationsDuPlacementCharacterizationIntegrationTest extends PlacementCharacterizationSupport {

    private interface Appel {
        Resp executer(RequestPostProcessor who) throws Exception;
    }

    @Override
    protected String theme() {
        return "p5-placement/autorisations";
    }

    private Map<String, Appel> routes(UUID patient) {
        Map<String, Appel> routes = new LinkedHashMap<>();
        routes.put("GET /planning/affectations", who -> doGet(AFFECTATIONS + "?jours=LUNDI&limite=1", who));
        routes.put("GET /planning/salles", who -> doGet(SALLES, who));
        routes.put("PUT /patients/{id} (placement conforme)", who -> doPut(PATIENTS + "/" + patient,
                ficheDe("PAT_1", CENTRE_A, salle1, matin, g1a, "LUNDI"), who));
        routes.put("POST /patients/{id}/serologies", who -> doPost(PATIENTS + "/" + patient + "/serologies",
                corpsSerologie(CENTRE_A, "AC_HBC", "NEGATIF"), who));
        return routes;
    }

    @Test
    void les_routes_du_placement_selon_les_onze_profils_et_l_anonyme() throws Exception {
        decorDePlacement();
        UUID patient = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        Map<String, Object> matrice = new LinkedHashMap<>();
        for (Map.Entry<String, Appel> route : routes(patient).entrySet()) {
            Map<String, RequestPostProcessor> profils = new LinkedHashMap<>(profils());
            for (Map.Entry<String, RequestPostProcessor> profil : profils.entrySet()) {
                matrice.put(route.getKey() + " [" + profil.getKey() + "]", statutEtCode(route.getValue().executer(profil.getValue())));
            }
            String methode = route.getKey().split(" ")[0];
            String chemin = route.getKey().split(" ")[1];
            String url = chemin.startsWith("/planning") ? PLANNING + chemin.substring("/planning".length())
                    : PATIENTS + "/" + patient + (chemin.endsWith("serologies") ? "/serologies" : "");
            matrice.put(route.getKey() + " [anonyme]", statutEtCode(appel(methode, url, methode.equals("GET") ? null : "{}", null)));
        }
        arbre("matrice_routes_x_profils", matrice);
    }
}
