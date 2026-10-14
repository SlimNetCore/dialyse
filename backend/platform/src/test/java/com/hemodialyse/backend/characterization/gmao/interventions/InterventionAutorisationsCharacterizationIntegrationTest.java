package com.hemodialyse.backend.characterization.gmao.interventions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Slice gmao-g4-interventions, thème « autorisations, isolation de centre, identifiants et corps illisibles ». Le golden
 * {@code taches_aucune_route} (KB-GMAO-55) est caduc depuis la création des tâches (G4 temps 2) : voir
 * {@code InterventionTachesNouveauCodeIntegrationTest}.
 */
class InterventionAutorisationsCharacterizationIntegrationTest extends InterventionCharacterizationSupport {

    /** Les neuf routes de {@code InterventionRestController} sur une intervention donnée (méthode, chemin, corps). */
    private Map<String, Object[]> routes(UUID i, UUID ligne) {
        Map<String, Object[]> r = new LinkedHashMap<>();
        r.put("POST_creer", new Object[]{HttpMethod.POST, "", creation(GEN, "EN_SERVICE", null)});
        r.put("GET_lister", new Object[]{HttpMethod.GET, "?size=50", null});
        r.put("GET_lire", new Object[]{HttpMethod.GET, "/" + i, null});
        r.put("POST_demarrer", new Object[]{HttpMethod.POST, "/" + i + "/demarrer", null});
        r.put("POST_terminer", new Object[]{HttpMethod.POST, "/" + i + "/terminer", cloture(60)});
        r.put("POST_ajouter_ligne", new Object[]{HttpMethod.POST, "/" + i + "/lignes-cout", ligne("PIECE", "L", 1, bd("1.00"))});
        r.put("DELETE_supprimer_ligne", new Object[]{HttpMethod.DELETE, "/" + i + "/lignes-cout/" + ligne, null});
        r.put("POST_rectifier", new Object[]{HttpMethod.POST, "/" + i + "/rectifier", body("motif", "x")});
        r.put("POST_annuler", new Object[]{HttpMethod.POST, "/" + i + "/annuler", body("raison", "x")});
        return r;
    }

    private ObjectNode jouer(Map<String, Object[]> routes, RequestPostProcessor who) throws Exception {
        ObjectNode m = mapper.createObjectNode();
        for (var e : routes.entrySet()) {
            Object[] v = e.getValue();
            m.put(e.getKey(), resume(call((HttpMethod) v[0], (String) v[1], (String) v[2], who)));
        }
        return m;
    }

    @Test
    void un_utilisateur_non_administrateur_est_refuse_en_403_sur_les_neuf_routes_sans_toucher_aux_donnees() throws Exception {
        UUID i = planifier(null);
        goldenNode("autorisation_non_administrateur", jouer(routes(i, INCONNU), nonAdmin()));
        assertEquals("PLANIFIEE", statutIntervention(i));
        assertEquals(1, nombreDInterventions(CENTRE_A));
        assertEquals("EN_SERVICE", statutEquipement(GEN));
    }

    @Test
    void une_requete_anonyme_est_refusee_en_403_sans_corps_sur_les_neuf_routes() throws Exception {
        UUID i = planifier(null);
        goldenNode("autorisation_anonyme", jouer(routes(i, INCONNU), null));
        assertEquals("PLANIFIEE", statutIntervention(i));
        assertEquals(1, nombreDInterventions(CENTRE_A));
    }

    @Test
    void rectifier_exige_le_role_admin_et_l_habilitation_gmao_rectification_kb_gmao_52() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        String[] profils = {"admin_sans_habilitation", "admin_avec_habilitation", "technicien", "anonyme"};
        RequestPostProcessor[] qui = {adminA(), rectifieur(), nonAdmin(), null};
        for (int k = 0; k < profils.length; k++) {
            cleanup();
            setUp();
            UUID i = terminee(null, 60);
            Resp r = post("/" + i + "/rectifier", body("motif", "Erreur"), qui[k]);
            m.put(profils[k], resume(r) + " | statut final " + statutIntervention(i));
        }
        goldenNode("autorisation_rectifier_selon_le_profil", m);

        // l'habilitation seule, sans le rôle ADMIN : le legacy rectifiait (200, KB-GMAO-52 : le @PreAuthorize de méthode
        // remplaçait celui de la classe). ÉCART DÉCIDÉ le 2026-10-12 : le nouveau code exige ADMIN et GMAO_RECTIFICATION (403).
        cleanup();
        setUp();
        UUID terminee = terminee(null, 60);
        Resp seule = post("/" + terminee + "/rectifier", body("motif", "Erreur"), rectifieurSansAdmin());
        assertEquals(403, seule.status);
        goldenNode("rectifier_habilitation_seule_sans_admin_nouveau_code", mapper.createObjectNode()
                .put("habilitation_sans_admin", resume(seule) + " | statut final " + statutIntervention(terminee)));
        assertEquals("TERMINEE", statutIntervention(terminee));

        // les autres commandes restent réservées à ADMIN : l'habilitation seule n'ouvre rien d'autre
        UUID i = planifier(null);
        assertEquals(403, post("/" + i + "/annuler", body("raison", "x"), rectifieurSansAdmin()).status);
        assertEquals(403, get("/" + i, rectifieurSansAdmin()).status);
    }

    @Test
    void chaque_centre_ne_voit_et_ne_pilote_que_ses_interventions() throws Exception {
        UUID i = terminee(I_INTERNE, 60);
        UUID planifiee = planifier(null);
        ObjectNode m = mapper.createObjectNode();
        m.put("GET_lire", resume(get("/" + i, adminB())));
        m.put("GET_lister_centre_B", resume(get("?size=50", adminB())) + " total=" + json(get("?size=50", adminB())).get("total"));
        m.put("POST_demarrer", resume(post("/" + planifiee + "/demarrer", null, adminB())));
        m.put("POST_terminer", resume(post("/" + planifiee + "/terminer", cloture(60), adminB())));
        m.put("POST_ajouter_ligne", resume(post("/" + planifiee + "/lignes-cout", ligne("PIECE", "L", 1, bd("1.00")), adminB())));
        m.put("DELETE_supprimer_ligne", resume(delete("/" + i + "/lignes-cout/" + INCONNU, adminB())));
        m.put("POST_annuler", resume(post("/" + planifiee + "/annuler", body("raison", "x"), adminB())));
        m.put("POST_rectifier", resume(post("/" + i + "/rectifier", body("motif", "x"),
                qui(CENTRE_B, ADMIN_B, "ADMIN", "GMAO_RECTIFICATION"))));
        m.put("POST_creer_sur_equipement_du_centre_A", resume(post("", creation(GEN, "EN_SERVICE", null), adminB())));
        m.put("GET_lister_equipement_du_centre_A", resume(get("?equipementId=" + GEN, adminB())));
        goldenNode("isolation_de_centre_commandes_du_centre_B_sur_le_centre_A", m);
        assertEquals("PLANIFIEE", statutIntervention(planifiee));
        assertEquals("TERMINEE", statutIntervention(i));
        assertEquals(0, nombreDInterventions(CENTRE_B));
        assertEquals(List.of("MAIN_OEUVRE|Main d'œuvre|1.000|35.00|true"), lignes(i));
    }

    @Test
    void les_identifiants_inconnus_ou_illisibles_sont_refuses_sur_toutes_les_routes_de_commande() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        for (String ident : new String[]{INCONNU.toString(), "pas-un-uuid", "12345"}) {
            ObjectNode e = mapper.createObjectNode();
            for (var r : routes(UUID.fromString("00000000-0000-0000-0000-000000000000"), INCONNU).entrySet()) {
                String cle = r.getKey();
                if (cle.equals("POST_creer") || cle.equals("GET_lister")) continue;
                Object[] v = r.getValue();
                String chemin = ((String) v[1]).replace("00000000-0000-0000-0000-000000000000", ident);
                e.put(cle, resume(call((HttpMethod) v[0], chemin, (String) v[2], rectifieur())));
            }
            m.set(ident.equals(INCONNU.toString()) ? "uuid_inconnu" : ident, e);
        }
        goldenNode("identifiants_inconnus_ou_illisibles", m);
    }

    @Test
    void les_corps_non_json_ou_absents_sont_refuses_en_500_sur_les_routes_a_corps_kb_gmao_18() throws Exception {
        UUID i = demarree(null);
        ObjectNode m = mapper.createObjectNode();
        String[][] routes = {{"POST_creer", ""}, {"POST_terminer", "/" + i + "/terminer"},
                {"POST_ajouter_ligne", "/" + i + "/lignes-cout"}, {"POST_rectifier", "/" + i + "/rectifier"},
                {"POST_annuler", "/" + i + "/annuler"}};
        for (String[] r : routes) {
            m.put(r[0] + "_text_plain", resume(send(request(HttpMethod.POST, BASE + r[1]).with(rectifieur())
                    .contentType(MediaType.TEXT_PLAIN).content("bonjour"))));
            m.put(r[0] + "_xml", resume(send(request(HttpMethod.POST, BASE + r[1]).with(rectifieur())
                    .contentType(MediaType.APPLICATION_XML).content("<a/>"))));
            m.put(r[0] + "_json_tableau", resume(post(r[1], "[]", rectifieur())));
            m.put(r[0] + "_json_null", resume(post(r[1], "null", rectifieur())));
        }
        goldenNode("corps_non_json_ou_illisibles", m);
        assertEquals("EN_COURS", statutIntervention(i));
        assertEquals(1, nombreDInterventions(CENTRE_A));
    }
}
