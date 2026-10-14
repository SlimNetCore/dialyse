package com.hemodialyse.backend.characterization.gmao.interventions;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tâches d'intervention (R21, KB-GMAO-55) : FONCTIONNALITÉ NOUVELLE créée à la demande de l'utilisateur le 2026-10-12, sans
 * équivalent legacy. Ces goldens {@code *_nouveau_code} décrivent le comportement voulu, pas un comportement hérité ; ils
 * remplacent {@code taches_aucune_route}, caduc. Contrat : {@code POST /{id}/taches}, {@code POST /{id}/taches/{tacheId}/demarrer},
 * {@code POST /{id}/taches/{tacheId}/achever}, réponse = l'intervention entière, rôle ADMIN, centre de la session. Les
 * statuts, la limite de 30 et les habilitations sont À VALIDER PAR LE MÉTIER.
 */
class InterventionTachesNouveauCodeIntegrationTest extends InterventionCharacterizationSupport {

    private UUID ajouter(UUID intervention, String description) throws Exception {
        Resp r = post("/" + intervention + "/taches", body("description", description), adminA());
        assertEquals(200, r.status, r.raw);
        JsonNode taches = json(r).get("taches");
        return UUID.fromString(taches.get(taches.size() - 1).get("id").asString());
    }

    private int nombreDeTaches(UUID intervention) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM gmao_taches_intervention WHERE intervention_id = ?", Integer.class,
                intervention);
    }

    private String statutDeLaTache(UUID tache) {
        return jdbc.queryForObject("SELECT statut FROM gmao_taches_intervention WHERE id = ?", String.class, tache);
    }

    @Test
    void ajouter_une_tache_renvoie_l_intervention_avec_la_tache_a_faire_et_la_persiste() throws Exception {
        UUID i = demarree(null);
        Resp r = post("/" + i + "/taches", body("description", "  Changer le joint  "), adminA());
        golden("tache_ajouter_nouveau_code", r);
        assertEquals(200, r.status);
        assertEquals(1, nombreDeTaches(i));
        assertEquals(CENTRE_A, jdbc.queryForObject("SELECT centre_id FROM gmao_taches_intervention WHERE intervention_id = ?",
                UUID.class, i));
        // la lecture de l'intervention expose la liste
        Resp lue = get("/" + i, adminA());
        assertEquals("Changer le joint", json(lue).get("taches").get(0).get("description").asString());
        assertEquals("A_FAIRE", json(lue).get("taches").get(0).get("statut").asString());
    }

    @Test
    void une_intervention_sans_tache_n_expose_pas_le_champ_taches_donc_les_contrats_existants_restent_identiques() throws Exception {
        UUID i = planifier(null);
        assertFalse(json(get("/" + i, adminA())).has("taches"));
        assertFalse(json(post("/" + i + "/demarrer", null, adminA())).has("taches"));
        assertFalse(json(get("?size=50", adminA())).get("items").get(0).has("taches"));
    }

    @Test
    void une_tache_va_de_a_faire_a_en_cours_puis_completee_avec_ses_jalons() throws Exception {
        UUID i = demarree(null);
        UUID tache = ajouter(i, "Changer le joint");
        ajouter(i, "Tester l'étanchéité");

        Resp demarree = post("/" + i + "/taches/" + tache + "/demarrer", null, adminA());
        golden("tache_demarrer_nouveau_code", demarree);
        assertEquals("EN_COURS", statutDeLaTache(tache));

        Resp achevee = post("/" + i + "/taches/" + tache + "/achever", null, adminA());
        golden("tache_achever_nouveau_code", achevee);
        assertEquals("COMPLETEE", statutDeLaTache(tache));
        assertEquals(2, nombreDeTaches(i));
        assertEquals("EN_COURS", statutIntervention(i));
        JsonNode taches = json(get("/" + i, adminA())).get("taches");
        assertEquals("COMPLETEE", taches.get(0).get("statut").asString());
        assertEquals("A_FAIRE", taches.get(1).get("statut").asString());
    }

    @Test
    void les_refus_de_regle_metier_sont_en_400_ou_422_sans_rien_modifier() throws Exception {
        UUID planifiee = planifier(null);
        UUID enCours = demarree(null);
        UUID tache = ajouter(enCours, "Changer le joint");
        UUID terminee = terminee(null, 60);
        UUID annulee = dansLeStatut("ANNULEE");
        UUID inconnue = INCONNU;
        ObjectNode m = mapper.createObjectNode();

        m.put("description_vide", resume(post("/" + enCours + "/taches", body("description", "   "), adminA())));
        m.put("description_absente", resume(post("/" + enCours + "/taches", body(), adminA())));
        m.put("achever_une_tache_a_faire", resume(post("/" + enCours + "/taches/" + tache + "/achever", null, adminA())));
        post("/" + enCours + "/taches/" + tache + "/demarrer", null, adminA());
        m.put("demarrer_une_tache_en_cours", resume(post("/" + enCours + "/taches/" + tache + "/demarrer", null, adminA())));
        post("/" + enCours + "/taches/" + tache + "/achever", null, adminA());
        m.put("demarrer_une_tache_completee", resume(post("/" + enCours + "/taches/" + tache + "/demarrer", null, adminA())));
        m.put("achever_une_tache_completee", resume(post("/" + enCours + "/taches/" + tache + "/achever", null, adminA())));
        m.put("tache_inconnue", resume(post("/" + enCours + "/taches/" + inconnue + "/demarrer", null, adminA())));
        m.put("ajouter_sur_intervention_inconnue", resume(post("/" + inconnue + "/taches", body("description", "x"), adminA())));
        m.put("ajouter_sur_intervention_terminee", resume(post("/" + terminee + "/taches", body("description", "x"), adminA())));
        m.put("ajouter_sur_intervention_annulee", resume(post("/" + annulee + "/taches", body("description", "x"), adminA())));
        UUID surPlanifiee = ajouter(planifiee, "A préparer");
        m.put("ajouter_sur_intervention_planifiee", "200 (acceptée)");
        m.put("demarrer_sur_intervention_planifiee",
                resume(post("/" + planifiee + "/taches/" + surPlanifiee + "/demarrer", null, adminA())));
        m.put("tache_id_illisible", resume(post("/" + enCours + "/taches/pas-un-uuid/demarrer", null, adminA())));
        m.put("intervention_id_illisible", resume(post("/pas-un-uuid/taches", body("description", "x"), adminA())));
        goldenNode("taches_refus_nouveau_code", m);

        assertEquals("COMPLETEE", statutDeLaTache(tache));
        assertEquals(0, nombreDeTaches(terminee));
        assertEquals(0, nombreDeTaches(annulee));
        assertEquals("A_FAIRE", statutDeLaTache(surPlanifiee));
    }

    @Test
    void la_limite_est_de_trente_taches_par_intervention() throws Exception {
        UUID i = demarree(null);
        for (int n = 1; n <= 30; n++) {
            ajouter(i, "Tâche " + n);
        }
        Resp refusee = post("/" + i + "/taches", body("description", "De trop"), adminA());
        golden("tache_limite_nouveau_code", refusee);
        assertEquals(422, refusee.status);
        assertEquals(30, nombreDeTaches(i));
        assertEquals(30, json(get("/" + i, adminA())).get("taches").size());
    }

    @Test
    void l_isolation_de_centre_et_les_habilitations_sont_appliquees_des_le_depart() throws Exception {
        UUID i = demarree(null);
        UUID tache = ajouter(i, "Changer le joint");
        ObjectNode m = mapper.createObjectNode();

        m.put("ajouter_autre_centre", resume(post("/" + i + "/taches", body("description", "x"), adminB())));
        m.put("demarrer_autre_centre", resume(post("/" + i + "/taches/" + tache + "/demarrer", null, adminB())));
        m.put("achever_autre_centre", resume(post("/" + i + "/taches/" + tache + "/achever", null, adminB())));
        m.put("ajouter_non_administrateur", resume(post("/" + i + "/taches", body("description", "x"), nonAdmin())));
        m.put("demarrer_non_administrateur", resume(post("/" + i + "/taches/" + tache + "/demarrer", null, nonAdmin())));
        m.put("achever_non_administrateur", resume(post("/" + i + "/taches/" + tache + "/achever", null, nonAdmin())));
        m.put("ajouter_anonyme", resume(post("/" + i + "/taches", body("description", "x"), null)));
        m.put("demarrer_anonyme", resume(post("/" + i + "/taches/" + tache + "/demarrer", null, null)));
        m.put("lire_autre_centre", resume(get("/" + i, adminB())));
        goldenNode("taches_isolation_et_autorisations_nouveau_code", m);

        assertEquals("A_FAIRE", statutDeLaTache(tache));
        assertEquals(1, nombreDeTaches(i));
        assertTrue(json(get("/" + i, adminA())).has("taches"));
    }
}
