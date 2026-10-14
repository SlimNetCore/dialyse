package com.hemodialyse.backend.characterization.gmao.pilotage;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation des statistiques du tableau de bord GMAO (GET /api/v1/gmao/stats) : comptages par statut,
 * plans actifs et en retard, interventions « à relancer », isolation de centre, jeu de données vide, cohérence avec les
 * écritures du nouveau module.
 */
class StatsGmaoCharacterizationIntegrationTest extends PilotageCharacterizationSupport {

    private static final String STATS = GMAO + "/stats";

    private OffsetDateTime il_y_a_jours(int jours) {
        return maintenant.minusDays(jours);
    }

    private OffsetDateTime dans_jours(int jours) {
        return maintenant.plusDays(jours);
    }

    private void semerLeParc(UUID centre, String prefixe) {
        List<String> statuts = List.of("EN_SERVICE", "EN_SERVICE", "EN_SERVICE", "EN_MAINTENANCE", "EN_MAINTENANCE", "HORS_SERVICE",
                "A_REFORMER", "REFORME", "DESACTIF", "EN_ATTENTE_PIECE");
        for (int k = 0; k < statuts.size(); k++) {
            String statut = statuts.get(k);
            equipement(prefixe + "_" + k + "_" + statut, centre, e -> e.statut = statut);
        }
        equipement(prefixe + "_SUPPRIME", centre, e -> e.supprimeLe = il_y_a_jours(3));
    }

    @Test
    void un_centre_sans_aucune_donnee_a_des_statistiques_a_zero() throws Exception {
        Resp r = get(STATS, adminB());

        assertEquals(200, r.status);
        golden("stats_centre_vide", r);
    }

    @Test
    void les_statistiques_comptent_le_parc_les_interventions_et_les_plans_du_centre_sans_les_supprimes() throws Exception {
        semerLeParc(CENTRE_A, "A");
        UUID e = equipement("E_STATS", CENTRE_A);
        // interventions : statut, début, échéance, suppression
        intervention("I_ST_PLAN_PASSEE", e, CENTRE_A, x -> {          // à relancer : planifiée dont le début est passé
            x.statut = "PLANIFIEE";
            x.debut = il_y_a_jours(2);
            x.fin = null;
        });
        intervention("I_ST_PLAN_FUTURE", e, CENTRE_A, x -> {          // non
            x.statut = "PLANIFIEE";
            x.debut = dans_jours(2);
            x.fin = null;
        });
        intervention("I_ST_PLAN_FUTURE_ECHEANCE_PASSEE", e, CENTRE_A, x -> {   // à relancer : échéance dépassée
            x.statut = "PLANIFIEE";
            x.debut = dans_jours(2);
            x.fin = null;
            x.echeance = il_y_a_jours(1);
        });
        intervention("I_ST_COURS_ECHEANCE_PASSEE", e, CENTRE_A, x -> { // à relancer
            x.statut = "EN_COURS";
            x.debut = il_y_a_jours(2);
            x.fin = null;
            x.echeance = il_y_a_jours(1);
        });
        intervention("I_ST_COURS_ECHEANCE_FUTURE", e, CENTRE_A, x -> { // non
            x.statut = "EN_COURS";
            x.debut = il_y_a_jours(2);
            x.fin = null;
            x.echeance = dans_jours(1);
        });
        intervention("I_ST_COURS_SANS_ECHEANCE", e, CENTRE_A, x -> {   // non (en cours depuis hier, pas de début compté)
            x.statut = "EN_COURS";
            x.debut = il_y_a_jours(5);
            x.fin = null;
        });
        intervention("I_ST_TERMINEE_ECHEANCE_PASSEE", e, CENTRE_A, x -> x.echeance = il_y_a_jours(30));   // non
        intervention("I_ST_TERMINEE_2", e, CENTRE_A, x -> {
        });
        intervention("I_ST_ANNULEE_ECHEANCE_PASSEE", e, CENTRE_A, x -> {  // non
            x.statut = "ANNULEE";
            x.fin = null;
            x.echeance = il_y_a_jours(30);
        });
        intervention("I_ST_ATTENTE", e, CENTRE_A, x -> x.statut = "EN_ATTENTE_VALIDATION");      // total seulement
        intervention("I_ST_SUPPRIMEE_PASSEE", e, CENTRE_A, x -> {     // supprimée : nulle part
            x.statut = "PLANIFIEE";
            x.debut = il_y_a_jours(2);
            x.fin = null;
            x.supprimeLe = il_y_a_jours(1);
        });
        // plans
        plan("P_ST_ACTIF_FUTUR", e, CENTRE_A, "ACTIF", dans_jours(5), null);
        plan("P_ST_ACTIF_RETARD", e, CENTRE_A, "ACTIF", il_y_a_jours(5), null);
        plan("P_ST_ACTIF_RETARD_2", e, CENTRE_A, "ACTIF", il_y_a_jours(400), null);
        plan("P_ST_INACTIF_RETARD", e, CENTRE_A, "INACTIF", il_y_a_jours(5), null);
        plan("P_ST_ARCHIVE_RETARD", e, CENTRE_A, "ARCHIVE", il_y_a_jours(5), null);
        plan("P_ST_SUPPRIME_RETARD", e, CENTRE_A, "ACTIF", il_y_a_jours(5), il_y_a_jours(1));

        golden("stats_nominales", get(STATS, adminA()));
    }

    @Test
    void les_statistiques_sont_bornees_au_centre_de_l_appelant() throws Exception {
        semerLeParc(CENTRE_A, "A");
        equipement("E_ST_B", CENTRE_B);
        UUID eb = nom("E_ST_B");
        intervention("I_ST_B", eb, CENTRE_B, x -> {
            x.statut = "PLANIFIEE";
            x.debut = il_y_a_jours(2);
            x.fin = null;
        });
        plan("P_ST_B", eb, CENTRE_B, "ACTIF", il_y_a_jours(2), null);

        List<String> lignes = new ArrayList<>();
        lignes.add("centre A -> " + get(STATS, adminA()).raw);
        lignes.add("centre B -> " + get(STATS, adminB()).raw);
        goldenLignes("stats_isolation_de_centre", lignes);
    }

    @Test
    void les_statistiques_sont_reservees_aux_administrateurs() throws Exception {
        Map<String, RequestPostProcessor> qui = new LinkedHashMap<>();
        qui.put("ADMIN", adminA());
        qui.put("TECHNICIEN", roleSeul("TECHNICIEN"));
        qui.put("MEDECIN", roleSeul("MEDECIN"));
        qui.put("SECRETAIRE", roleSeul("SECRETAIRE"));
        qui.put("INFIRMIER", roleSeul("INFIRMIER"));
        qui.put("SUPERADMIN", roleSeul("SUPERADMIN"));
        qui.put("DIRECTION", direction());
        qui.put("GMAO_RECTIFICATION sans ADMIN", roleSeul("GMAO_RECTIFICATION"));
        qui.put("anonyme", null);

        List<String> lignes = new ArrayList<>();
        for (var q : qui.entrySet()) {
            Resp r = get(STATS, q.getValue());
            lignes.add(q.getKey() + " -> " + (r.status == 200 ? "200" : resume(r)));
        }
        lignes.add("POST -> " + resume(call(org.springframework.http.HttpMethod.POST, STATS, null, adminA())));
        lignes.add("sous-chemin inconnu -> " + resume(get(STATS + "/autre", adminA())));
        goldenLignes("stats_autorisations", lignes);
    }

    @Test
    void les_statistiques_suivent_les_ecritures_du_nouveau_module() throws Exception {
        List<String> lignes = new ArrayList<>();
        lignes.add("départ -> " + get(STATS, adminA()).raw);

        Resp creation = post(GMAO + "/equipements", body("code", "P6-NEUF", "designation", "Générateur neuf",
                "type", "GENERATEUR_DIALYSE", "dateInstallation", INSTALLATION, "prixAcquisition", new BigDecimal("1000.00")), adminA());
        assertEquals(201, creation.status, creation.raw);
        UUID e = UUID.fromString(json(creation).get("id").asString());
        lignes.add("équipement enregistré -> " + get(STATS, adminA()).raw);

        UUID i = planifierParHttp(e, "EN_SERVICE", null, il_y_a_jours(1));
        lignes.add("intervention planifiée (début passé : à relancer) -> " + get(STATS, adminA()).raw);

        demarrerParHttp(i);
        lignes.add("intervention démarrée -> " + get(STATS, adminA()).raw);

        terminerParHttp(i, "EN_SERVICE", il_y_a_jours(1).plusHours(2));
        lignes.add("intervention terminée, équipement remis en service -> " + get(STATS, adminA()).raw);

        assertEquals(200, post(GMAO + "/equipements/" + e + "/hors-service", body("raison", "Fuite"), adminA()).status);
        lignes.add("équipement mis hors service -> " + get(STATS, adminA()).raw);

        Resp plan = post(GMAO + "/plans-maintenance", body("equipementId", e, "designation", "Contrôle", "frequence", "MENSUELLE",
                "prochaineDatePrevue", il_y_a_jours(3)), adminA());
        assertEquals(201, plan.status, plan.raw);
        UUID p = UUID.fromString(json(plan).get("id").asString());
        lignes.add("plan créé (prochaine date passée) -> " + get(STATS, adminA()).raw);

        assertEquals(200, post(GMAO + "/plans-maintenance/" + p + "/executer", null, adminA()).status);
        lignes.add("plan exécuté -> " + get(STATS, adminA()).raw);

        assertEquals(200, post(GMAO + "/plans-maintenance/" + p + "/desactiver", null, adminA()).status);
        lignes.add("plan désactivé -> " + get(STATS, adminA()).raw);

        assertEquals(200, post(GMAO + "/equipements/" + e + "/reformer", body("motif", "Obsolète"), reformeur()).status);
        lignes.add("équipement réformé -> " + get(STATS, adminA()).raw);

        goldenLignes("stats_ecritures_du_nouveau_module", lignes);
    }
}
