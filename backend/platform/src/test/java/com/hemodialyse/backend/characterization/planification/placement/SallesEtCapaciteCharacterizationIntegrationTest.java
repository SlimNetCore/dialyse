package com.hemodialyse.backend.characterization.planification.placement;

import com.hemodialyse.backend.characterization.planification.PlacementCharacterizationSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation P5 : {@code GET /api/v1/planning/salles} (vue d'ensemble des salles : générateurs affectés, capacité,
 * places restantes ; pagination). La règle de capacité à l'affectation d'un générateur ({@code SALLE_CAPACITE_DEPASSEE}) est portée
 * par la GMAO et verrouillée par son filet G3 ({@code EquipementHttpCharacterizationIntegrationTest}).
 */
class SallesEtCapaciteCharacterizationIntegrationTest extends PlacementCharacterizationSupport {

    @Override
    protected String theme() {
        return "p5-placement/salles";
    }

    private Resp salles(String requete) throws Exception {
        return doGet(SALLES + requete, adminA());
    }

    private List<String> lignes(Resp r) {
        List<String> l = new ArrayList<>();
        for (JsonNode s : corps(r).path("items")) {
            List<String> gens = new ArrayList<>();
            s.path("generateurs").forEach(g -> gens.add(g.path("code").asString() + ":" + g.path("statut").asString()));
            l.add(s.path("code").asString() + " isolement=" + s.path("isolement").asBoolean() + " capacite=" + s.path("capacite").asString("null")
                    + " nb=" + s.path("nbGenerateurs").asInt() + " restantes=" + s.path("placesRestantes").asString("null")
                    + " depassement=" + s.path("depassement").asBoolean() + " generateurs=" + gens);
        }
        return l;
    }

    @Test
    void reponse_complete_d_une_salle_avec_capacite() throws Exception {
        UUID s = salle(CENTRE_A, "SALLE_UNIQUE", "Salle unique", "NON");
        jdbc.update("UPDATE salle SET capacite = 3 WHERE id = ?", s);
        generateur(CENTRE_A, "GA", s, "EN_SERVICE");
        lecture("reponse_complete", salles(""));
    }

    @Test
    void les_salles_sont_triees_par_code_avec_capacite_places_restantes_et_depassement() throws Exception {
        decorDePlacement();
        jdbc.update("UPDATE salle SET capacite = 2 WHERE id = ?", salle1);
        jdbc.update("UPDATE salle SET capacite = 1 WHERE id = ?", salle2);
        generateur(CENTRE_A, "G2B", salle2, "EN_SERVICE");
        Resp r = salles("");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status());
        m.put("salles", lignes(r));
        m.put("total", corps(r).path("total").asInt());
        m.put("page", corps(r).path("page").asInt());
        m.put("size", corps(r).path("size").asInt());
        arbre("liste", m);
    }

    @Test
    void seuls_les_generateurs_de_dialyse_non_reformes_non_desactives_non_supprimes_sont_comptes() throws Exception {
        decorDePlacement();
        generateur(CENTRE_A, "G_HS", salle2, "HORS_SERVICE");
        generateur(CENTRE_A, "G_MAINT", salle2, "EN_MAINTENANCE");
        generateur(CENTRE_A, "G_REFORME", salle2, "REFORME");
        generateur(CENTRE_A, "G_DESACTIF", salle2, "DESACTIF");
        equipement(CENTRE_A, "E_EAU", "STATION_TRAITEMENT_EAU", salle2, "EN_SERVICE");
        UUID supprime = generateur(CENTRE_A, "G_SUPPRIME", salle2, "EN_SERVICE");
        jdbc.update("UPDATE gmao_equipements SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", supprime);
        generateur(CENTRE_B, "G_AUTRE_CENTRE", salle2, "EN_SERVICE");
        arbre("generateurs_comptes", lignes(salles("")));
    }

    @Test
    void la_pagination_decoupe_les_salles_et_refuse_ou_tolere_les_valeurs_limites() throws Exception {
        decorDePlacement();
        Map<String, Object> m = new LinkedHashMap<>();
        for (String q : new String[]{"?size=2", "?size=2&page=1", "?size=2&page=5", "?size=0", "?size=-1", "?page=-1", "?size=1000", "?size=abc", "?page=abc"}) {
            Resp r = salles(q);
            if (r.status() == 200) {
                JsonNode c = corps(r);
                m.put(q, "200 total=" + c.path("total").asInt() + " page=" + c.path("page").asInt() + " size=" + c.path("size").asInt()
                        + " items=" + c.path("items").size());
            } else {
                m.put(q, statutEtCode(r));
            }
        }
        arbre("pagination", m);
    }

    @Test
    void un_centre_ne_voit_que_ses_salles_et_la_garde_de_centre_s_applique() throws Exception {
        decorDePlacement();
        generateur(CENTRE_B, "GB1", salleB, "EN_SERVICE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centre B", lignes(doGet(SALLES, adminB())));
        m.put("centerId = centre de la session", statutEtCode(salles("?centerId=" + CENTRE_A)));
        m.put("centerId autre centre", statutEtCode(salles("?centerId=" + CENTRE_B)));
        m.put("centerId illisible", statutEtCode(salles("?centerId=abc")));
        arbre("isolation_de_centre", m);
    }

    @Test
    void un_centre_sans_salle_repond_une_liste_vide() throws Exception {
        arbre("centre_sans_salle", Map.of("statut", salles("").status(), "total", corps(salles("")).path("total").asInt()));
    }

    @Test
    void la_vue_reflete_le_statut_courant_des_generateurs_et_n_est_jamais_mise_en_cache() throws Exception {
        decorDePlacement();
        Resp avant = salles("");
        jdbc.update("UPDATE gmao_equipements SET statut = 'HORS_SERVICE' WHERE id = ?", g1a);
        Resp apres = salles("");
        arbre("statut_courant", Map.of("avant", lignes(avant), "apres", lignes(apres), "cacheControl", String.valueOf(apres.cacheControl())));
    }
}
