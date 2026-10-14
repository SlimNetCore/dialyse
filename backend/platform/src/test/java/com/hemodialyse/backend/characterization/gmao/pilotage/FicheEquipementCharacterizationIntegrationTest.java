package com.hemodialyse.backend.characterization.gmao.pilotage;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filet de caractérisation de la fiche équipement (GET /api/v1/gmao/equipements/{id}/fiche) et de la disponibilité
 * avant affectation d'un patient (GET .../disponibilite-patient) : coûts exacts, ratio de maintenance à 4 décimales,
 * coût par heure d'indisponibilité, seuil de réforme 0,60 (RG-GMA-061/062/063), arrondis HALF_UP.
 * Dates relatives à « maintenant » (fenêtre glissante de 12 mois) : goldens à instants masqués.
 */
class FicheEquipementCharacterizationIntegrationTest extends PilotageCharacterizationSupport {

    private String fiche(UUID e) {
        return GMAO + "/equipements/" + e + "/fiche";
    }

    private String disponibilite(UUID e) {
        return GMAO + "/equipements/" + e + "/disponibilite-patient";
    }

    private OffsetDateTime il_y_a_jours(int jours) {
        return maintenant.minusDays(jours);
    }

    private JsonNode ficheJson(UUID e) throws Exception {
        Resp r = get(fiche(e), adminA());
        assertEquals(200, r.status, r.raw);
        return json(r);
    }

    /** Intervention démarrée à {@code debut} dans le statut donné, avec une ligne de coût d'un montant. */
    private UUID coute(String label, UUID e, String statut, OffsetDateTime debut, String quantite, String prix) {
        UUID i = intervention(label, e, CENTRE_A, x -> {
            x.statut = statut;
            x.debut = debut;
            x.fin = "TERMINEE".equals(statut) ? debut.plusHours(1) : null;
            x.creation = debut.minusHours(1);
        });
        ligne(i, "PIECE", "Pièce " + label, quantite, prix);
        return i;
    }

    // ------------------------------------------------------------------ cas nominal

    @Test
    void la_fiche_regroupe_couts_indisponibilite_derniere_intervention_et_prochain_plan() throws Exception {
        UUID e = equipement("E_FICHE", CENTRE_A, x -> {
            x.designation = "Générateur de la salle 1";
            x.fabricant = "Fresenius";
            x.modele = "5008S";
            x.serie = "SN-5008";
            x.prix = new BigDecimal("100000.00");
        });
        historique(e, CENTRE_A, "EN_SERVICE", "EN_MAINTENANCE", il_y_a_jours(100));
        historique(e, CENTRE_A, "EN_MAINTENANCE", "EN_SERVICE", il_y_a_jours(100).plusMinutes(150));
        coute("I_FICHE_TERMINEE", e, "TERMINEE", il_y_a_jours(100), "2", "12500.00");              // période + cumul : 25 000,00
        coute("I_FICHE_EN_COURS", e, "EN_COURS", il_y_a_jours(30), "1", "5000.50");                 // période + cumul : 5 000,50
        coute("I_FICHE_PLANIFIEE", e, "PLANIFIEE", il_y_a_jours(20), "1", "99999.00");              // exclue
        coute("I_FICHE_ANNULEE", e, "ANNULEE", il_y_a_jours(20), "1", "77777.00");                  // exclue
        coute("I_FICHE_ANCIENNE", e, "TERMINEE", il_y_a_jours(500), "1", "10000.00");               // cumul seulement
        coute("I_FICHE_ATTENTE", e, "EN_ATTENTE_VALIDATION", il_y_a_jours(5), "1", "4444.00");      // exclue des coûts, mais « dernière »
        UUID supprimee = coute("I_FICHE_SUPPRIMEE", e, "TERMINEE", il_y_a_jours(10), "1", "55555.00");
        jdbc.update("UPDATE gmao_interventions SET deleted_at = ? WHERE id = ?", il_y_a_jours(9), supprimee);
        plan("P_FICHE_LOINTAIN", e, CENTRE_A, "ACTIF", maintenant.plusDays(10), null);
        plan("P_FICHE_PROCHE", e, CENTRE_A, "ACTIF", maintenant.plusDays(3), null);
        plan("P_FICHE_INACTIF", e, CENTRE_A, "INACTIF", maintenant.plusDays(1), null);
        plan("P_FICHE_SUPPRIME", e, CENTRE_A, "ACTIF", maintenant.plusDays(2), maintenant.minusDays(1));

        Resp r = get(fiche(e), adminA());

        assertEquals(200, r.status);
        JsonNode f = json(r);
        assertEquals(6, f.get("nbInterventions").asInt(), "toutes les interventions non supprimées, quel que soit leur statut");
        assertEquals(2.5, f.get("indisponibiliteHeures").asDouble(), 0.0);
        assertEquals(0, new BigDecimal("30000.50").compareTo(f.get("coutMaintenancePeriode").decimalValue()));
        assertEquals(0, new BigDecimal("40000.50").compareTo(f.get("coutMaintenanceCumule").decimalValue()));
        assertEquals(0, new BigDecimal("140000.50").compareTo(f.get("coutPossession").decimalValue()));
        assertEquals("0.4000", f.get("ratioMaintenance").decimalValue().toPlainString());
        assertEquals("12000.20", f.get("coutParHeureIndisponibilite").decimalValue().toPlainString());
        assertEquals(false, f.get("reformeRecommandee").asBoolean());
        assertEquals("0.60", f.get("seuilReforme").decimalValue().toPlainString());
        assertEquals(nom("I_FICHE_ATTENTE").toString(), f.get("derniereIntervention").get("id").asString(),
                "dernière = date de début la plus récente, tous statuts confondus");
        assertEquals(nom("P_FICHE_PROCHE").toString(), f.get("prochainePlanMaintenance").get("id").asString(),
                "plan actif non supprimé à la date prévue la plus proche");
        goldenRelatif("fiche_nominale", r);
    }

    @Test
    void le_prochain_plan_est_le_plus_ancien_meme_en_retard_et_les_plans_inactifs_ou_supprimes_sont_ignores() throws Exception {
        UUID e = equipement("E_FICHE_PLANS", CENTRE_A);
        plan("P_RETARD", e, CENTRE_A, "ACTIF", maintenant.minusDays(5), null);
        plan("P_FUTUR", e, CENTRE_A, "ACTIF", maintenant.plusDays(5), null);
        plan("P_ARCHIVE", e, CENTRE_A, "ARCHIVE", maintenant.minusDays(50), null);

        assertEquals(nom("P_RETARD").toString(), ficheJson(e).get("prochainePlanMaintenance").get("id").asString());

        UUID sans = equipement("E_FICHE_SANS_PLAN_ACTIF", CENTRE_A);
        plan("P_INACTIF_SEUL", sans, CENTRE_A, "INACTIF", maintenant.plusDays(5), null);
        assertTrue(ficheJson(sans).get("prochainePlanMaintenance").isNull());
    }

    @Test
    void la_fiche_d_un_equipement_sans_historique_ni_intervention_ni_plan() throws Exception {
        UUID e = equipement("E_FICHE_VIDE", CENTRE_A);

        Resp r = get(fiche(e), adminA());

        assertEquals(200, r.status);
        goldenRelatif("fiche_equipement_vierge", r);
    }

    // ------------------------------------------------------------------ ratio, seuil de réforme, coût par heure

    @Test
    void le_seuil_de_0_60_est_compare_au_ratio_exact_l_arrondi_a_4_decimales_n_est_que_l_affichage_nouveau_code() throws Exception {
        String[][] cas = {
                // libellé, prix, coût cumulé, statut
                {"cumul 599,94 sur 1000 (0,59994 -> 0,5999)", "1000.00", "599.94", "EN_SERVICE"},
                {"cumul 599,95 sur 1000 (0,59995 -> 0,6000 HALF_UP)", "1000.00", "599.95", "EN_SERVICE"},
                {"cumul 599,96 sur 1000 (0,59996 -> 0,6000 affiché : non recommandée car 0,59996 < 0,60)", "1000.00", "599.96", "EN_SERVICE"},
                {"cumul 600,00 sur 1000 (= seuil)", "1000.00", "600.00", "EN_SERVICE"},
                {"cumul 600,04 sur 1000", "1000.00", "600.04", "EN_SERVICE"},
                {"cumul 1,00 sur 3 (0,3333)", "3.00", "1.00", "EN_SERVICE"},
                {"cumul 2,00 sur 3 (0,6667)", "3.00", "2.00", "EN_SERVICE"},
                {"cumul 0,01 sur 100000 (0,0000)", "100000.00", "0.01", "EN_SERVICE"},
                {"cumul 125 sur 100 (1,2500)", "100.00", "125.00", "EN_SERVICE"},
                {"prix 0 : ratio inconnu", "0.00", "999.00", "EN_SERVICE"},
                {"prix absent : ratio inconnu", null, "999.00", "EN_SERVICE"},
                {"prix négatif : ratio inconnu", "-10.00", "999.00", "EN_SERVICE"},
                {"À réformer, prix absent : recommandée d'office", null, "0.00", "A_REFORMER"},
                {"À réformer, ratio 0,10 : recommandée d'office", "1000.00", "100.00", "A_REFORMER"},
                {"Réformé, ratio 5 : jamais recommandée", "100.00", "500.00", "REFORME"},
                {"Désactivé, ratio 5 : jamais recommandée", "100.00", "500.00", "DESACTIF"},
                {"En maintenance, ratio 0,70 : recommandée", "1000.00", "700.00", "EN_MAINTENANCE"},
                {"En attente de pièce, ratio 0,70 : recommandée", "1000.00", "700.00", "EN_ATTENTE_PIECE"},
                {"Hors service, ratio 0,70 : recommandée", "1000.00", "700.00", "HORS_SERVICE"},
        };
        List<String> lignes = new ArrayList<>();
        int n = 0;
        for (String[] c : cas) {
            int k = n++;
            UUID e = equipement("E_SEUIL_" + k, CENTRE_A, x -> {
                x.statut = c[3];
                x.prix = c[1] == null ? null : new BigDecimal(c[1]);
            });
            if (new BigDecimal(c[2]).signum() > 0) coute("I_SEUIL_" + k, e, "TERMINEE", il_y_a_jours(40), "1", c[2]);
            JsonNode f = ficheJson(e);
            lignes.add(c[0] + " -> ratio=" + f.get("ratioMaintenance") + " possession=" + f.get("coutPossession")
                    + " recommandee=" + f.get("reformeRecommandee") + " seuil=" + f.get("seuilReforme"));
        }
        goldenLignes("fiche_ratio_et_seuil_de_reforme_nouveau_code", lignes);
    }

    @Test
    void le_cout_par_heure_d_indisponibilite_est_arrondi_a_2_decimales_HALF_UP_et_absent_sans_indisponibilite() throws Exception {
        int[] minutes = {0, 1, 45, 100, 180, 7, 59, 61};
        String[] periodes = {"100.00", "1.00", "100.00", "100.00", "100.00", "10.00", "100.00", "100.00"};
        List<String> lignes = new ArrayList<>();
        for (int k = 0; k < minutes.length; k++) {
            int m = minutes[k];
            UUID e = equipement("E_HEURE_" + k, CENTRE_A, x -> x.prix = new BigDecimal("1000.00"));
            if (m > 0) {
                historique(e, CENTRE_A, "EN_SERVICE", "HORS_SERVICE", il_y_a_jours(60));
                historique(e, CENTRE_A, "HORS_SERVICE", "EN_SERVICE", il_y_a_jours(60).plusMinutes(m));
            }
            coute("I_HEURE_" + k, e, "TERMINEE", il_y_a_jours(40), "1", periodes[k]);
            JsonNode f = ficheJson(e);
            lignes.add(m + " min d'indisponibilité, coût de période " + periodes[k] + " -> heures=" + f.get("indisponibiliteHeures")
                    + " coutParHeure=" + f.get("coutParHeureIndisponibilite"));
        }
        goldenLignes("fiche_cout_par_heure_d_indisponibilite", lignes);
    }

    @Test
    void la_fenetre_de_12_mois_exclut_les_couts_anciens_mais_le_cumul_les_garde() throws Exception {
        UUID e = equipement("E_FENETRE", CENTRE_A, x -> x.prix = new BigDecimal("1000.00"));
        coute("I_FEN_LIMITE_DEDANS", e, "TERMINEE", maintenant.minusMonths(12).plusDays(1), "1", "100.00");
        coute("I_FEN_LIMITE_DEHORS", e, "TERMINEE", maintenant.minusMonths(12).minusDays(1), "1", "10.00");
        coute("I_FEN_FUTUR", e, "EN_COURS", maintenant.plusDays(1), "1", "1000.00");

        JsonNode f = ficheJson(e);

        assertEquals(0, new BigDecimal("100.00").compareTo(f.get("coutMaintenancePeriode").decimalValue()),
                "période = [maintenant - 12 mois, maintenant[ sur la date de début : le futur n'y est pas");
        assertEquals(0, new BigDecimal("1110.00").compareTo(f.get("coutMaintenanceCumule").decimalValue()),
                "le cumul compte aussi l'intervention en cours dont le début est futur");
    }

    @Test
    void l_indisponibilite_de_la_fiche_est_limitee_a_la_fenetre_et_une_panne_en_cours_court_jusqu_a_maintenant() throws Exception {
        UUID chevauche = equipement("E_PANNE_CHEVAUCHE", CENTRE_A);
        historique(chevauche, CENTRE_A, "EN_SERVICE", "HORS_SERVICE", il_y_a_jours(400));
        historique(chevauche, CENTRE_A, "HORS_SERVICE", "EN_SERVICE", il_y_a_jours(300));
        double attendu = Duration.between(maintenant.minusMonths(12), il_y_a_jours(300)).toMinutes() / 60.0;
        assertEquals(attendu, ficheJson(chevauche).get("indisponibiliteHeures").asDouble(), 0.05,
                "de l'ouverture de la fenêtre à la fin de la panne");

        UUID ouverte = equipement("E_PANNE_OUVERTE", CENTRE_A, x -> x.statut = "EN_MAINTENANCE");
        historique(ouverte, CENTRE_A, "EN_SERVICE", "EN_MAINTENANCE", il_y_a_jours(2));
        assertEquals(48.0, ficheJson(ouverte).get("indisponibiliteHeures").asDouble(), 0.05, "panne ouverte depuis 2 jours");

        UUID reforme = equipement("E_PANNE_REFORME", CENTRE_A, x -> x.statut = "REFORME");
        historique(reforme, CENTRE_A, "EN_MAINTENANCE", "REFORME", il_y_a_jours(2));
        UUID desactive = equipement("E_PANNE_DESACTIF", CENTRE_A, x -> x.statut = "DESACTIF");
        historique(desactive, CENTRE_A, "EN_MAINTENANCE", "DESACTIF", il_y_a_jours(2));
        double fenetre = Duration.between(maintenant.minusMonths(12), il_y_a_jours(2)).toMinutes() / 60.0;
        assertEquals(fenetre, ficheJson(reforme).get("indisponibiliteHeures").asDouble(), 0.05,
                "la panne précédant la réforme compte jusqu'au passage à Réformé, pas au-delà");
        assertEquals(fenetre, ficheJson(desactive).get("indisponibiliteHeures").asDouble(), 0.05);
    }

    // ------------------------------------------------------------------ disponibilité avant affectation d'un patient

    @Test
    void la_disponibilite_patient_ne_depend_que_du_statut_un_equipement_desactive_n_est_pas_disponible_nouveau_code() throws Exception {
        List<String> lignes = new ArrayList<>();
        for (String statut : List.of("EN_SERVICE", "EN_MAINTENANCE", "EN_ATTENTE_PIECE", "HORS_SERVICE", "DESACTIF", "A_REFORMER", "REFORME")) {
            UUID e = equipement("E_DISPO_" + statut, CENTRE_A, x -> x.statut = statut);
            // une intervention en cours ne change rien : seul le statut compte
            intervention("I_DISPO_" + statut, e, CENTRE_A, x -> {
                x.statut = "EN_COURS";
                x.fin = null;
            });
            Resp r = get(disponibilite(e), adminA());
            lignes.add(statut + " -> " + r.status + " " + r.raw);
        }
        goldenLignes("disponibilite_patient_par_statut_nouveau_code", lignes);
    }

    // ------------------------------------------------------------------ erreurs, isolation, autorisations

    @Test
    void la_fiche_et_la_disponibilite_refusent_les_ids_inconnus_et_les_autres_centres_mais_servent_un_equipement_supprime() throws Exception {
        UUID e = equipement("E_ERR", CENTRE_A);
        UUID b = equipement("E_ERR_B", CENTRE_B);
        UUID supprime = equipement("E_ERR_SUPPRIME", CENTRE_A, x -> x.supprimeLe = il_y_a_jours(3));

        List<String> lignes = new ArrayList<>();
        for (String route : List.of("fiche", "disponibilite-patient")) {
            String p = GMAO + "/equipements/";
            lignes.add(route + " : équipement du centre -> " + get(p + e + "/" + route, adminA()).status);
            lignes.add(route + " : équipement inconnu -> " + resume(get(p + INCONNU + "/" + route, adminA())));
            lignes.add(route + " : équipement du centre B vu par A -> " + resume(get(p + b + "/" + route, adminA())));
            lignes.add(route + " : équipement supprimé logiquement -> " + get(p + supprime + "/" + route, adminA()).status);
            lignes.add(route + " : id illisible -> " + resume(get(p + "pas-un-uuid/" + route, adminA())));
            lignes.add(route + " : méthode POST -> " + resume(call(org.springframework.http.HttpMethod.POST, p + e + "/" + route, null, adminA())));
        }
        goldenLignes("fiche_erreurs", lignes);
    }

    @Test
    void la_fiche_ne_compte_que_les_interventions_et_plans_de_l_equipement_sans_regarder_leur_centre() throws Exception {
        UUID e = equipement("E_CENTRE", CENTRE_A);
        coute("I_CENTRE_A", e, "TERMINEE", il_y_a_jours(30), "1", "100.00");
        UUID autre = intervention("I_CENTRE_B_SUR_E", e, CENTRE_B, x -> {
            x.debut = il_y_a_jours(20);
            x.fin = il_y_a_jours(20).plusHours(1);
        });
        ligne(autre, "PIECE", "Pièce du centre B", "1", "7.00");

        JsonNode f = ficheJson(e);

        assertEquals(2, f.get("nbInterventions").asInt(), "KB-GMAO-71 : les requêtes de la fiche filtrent par équipement, pas par centre");
        assertEquals(0, new BigDecimal("107.00").compareTo(f.get("coutMaintenanceCumule").decimalValue()));
    }

    @Test
    void la_fiche_et_la_disponibilite_sont_reservees_aux_administrateurs_du_centre() throws Exception {
        UUID e = equipement("E_AUTH", CENTRE_A);
        Map<String, RequestPostProcessor> qui = new LinkedHashMap<>();
        qui.put("ADMIN du centre", adminA());
        qui.put("ADMIN d'un autre centre", adminB());
        qui.put("TECHNICIEN", roleSeul("TECHNICIEN"));
        qui.put("MEDECIN", roleSeul("MEDECIN"));
        qui.put("SECRETAIRE", roleSeul("SECRETAIRE"));
        qui.put("SUPERADMIN", roleSeul("SUPERADMIN"));
        qui.put("DIRECTION", direction());
        qui.put("GMAO_REFORME sans ADMIN", roleSeul("GMAO_REFORME"));
        qui.put("anonyme", null);

        List<String> lignes = new ArrayList<>();
        for (var q : qui.entrySet()) {
            Resp f = get(fiche(e), q.getValue());
            Resp d = get(disponibilite(e), q.getValue());
            lignes.add(q.getKey() + " -> fiche " + (f.status == 200 ? "200" : resume(f)) + " ; disponibilité "
                    + (d.status == 200 ? "200" : resume(d)));
        }
        goldenLignes("fiche_autorisations", lignes);
    }

    // ------------------------------------------------------------------ cohérence avec les écritures du nouveau module

    @Test
    void la_fiche_suit_la_cloture_d_une_intervention_ecrite_par_le_nouveau_module() throws Exception {
        UUID e = equipement("E_COHERENCE", CENTRE_A, x -> x.prix = new BigDecimal("10000.00"));
        OffsetDateTime debut = maintenant.minusDays(4).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        UUID i = planifierParHttp(e, "EN_SERVICE", INTERVENANT_INTERNE, debut);
        demarrerParHttp(i);
        assertEquals(200, post(GMAO + "/interventions/" + i + "/lignes-cout",
                body("type", "PIECE", "libelle", "Pompe", "quantite", new BigDecimal("2"), "prixUnitaire", new BigDecimal("1500.00")),
                adminA()).status);

        JsonNode enCours = ficheJson(e);
        assertEquals(1, enCours.get("nbInterventions").asInt());
        assertEquals(0, new BigDecimal("3000.00").compareTo(enCours.get("coutMaintenancePeriode").decimalValue()));

        terminerParHttp(i, "HORS_SERVICE", debut.plusHours(3));
        JsonNode terminee = ficheJson(e);
        // la clôture ajoute la main d'œuvre de l'intervenant (35,00 x 3 h) et fixe l'état après intervention
        assertEquals(0, new BigDecimal("3105.00").compareTo(terminee.get("coutMaintenancePeriode").decimalValue()), terminee.toString());
        assertEquals("HORS_SERVICE", terminee.get("equipement").get("statut").asString());
        assertEquals("0.3105", terminee.get("ratioMaintenance").decimalValue().toPlainString());
        assertEquals(nom("E_COHERENCE").toString(), terminee.get("equipement").get("id").asString());
        assertEquals(i.toString(), terminee.get("derniereIntervention").get("id").asString());
        assertEquals("TERMINEE", terminee.get("derniereIntervention").get("statut").asString());
        double heures = terminee.get("indisponibiliteHeures").asDouble();
        assertTrue(heures >= 0.0 && heures < 0.2, "panne ouverte depuis la clôture : " + heures);
        assertEquals(false, get(disponibilite(e), adminA()).raw.contains("\"disponible\":true"));

        var rectifieur = qui(CENTRE_A, ADMIN_A, "ADMIN", "GMAO_RECTIFICATION");
        assertEquals(200, post(GMAO + "/interventions/" + i + "/rectifier", body("motif", "Erreur"), rectifieur).status);
        JsonNode rectifiee = ficheJson(e);
        assertEquals(0, new BigDecimal("3000.00").compareTo(rectifiee.get("coutMaintenancePeriode").decimalValue()),
                "la rectification retire la ligne automatique : coût ramené à la pièce");
        assertEquals("EN_COURS", rectifiee.get("derniereIntervention").get("statut").asString());
    }

    @Test
    void la_fiche_suit_l_execution_d_un_plan_et_la_reforme_ecrites_par_le_nouveau_module() throws Exception {
        UUID e = equipement("E_COHERENCE_PLAN", CENTRE_A, x -> x.prix = new BigDecimal("1000.00"));
        Resp creation = post(GMAO + "/plans-maintenance", body("equipementId", e, "designation", "Contrôle mensuel",
                "frequence", "MENSUELLE", "prochaineDatePrevue", maintenant.plusDays(2)), adminA());
        assertEquals(201, creation.status, creation.raw);
        UUID plan = UUID.fromString(json(creation).get("id").asString());
        JsonNode avant = ficheJson(e).get("prochainePlanMaintenance");
        assertEquals(plan.toString(), avant.get("id").asString());
        assertEquals(0, avant.get("nombreExecutions").asInt());

        assertEquals(200, post(GMAO + "/plans-maintenance/" + plan + "/executer", null, adminA()).status);

        JsonNode apres = ficheJson(e).get("prochainePlanMaintenance");
        assertEquals(1, apres.get("nombreExecutions").asInt());
        assertTrue(apres.get("derniereDateExecution").isString());
        assertTrue(OffsetDateTime.parse(apres.get("prochaineDatePrevue").asString()).isAfter(maintenant.plusDays(2)),
                "la prochaine date prévue a été avancée par l'exécution");

        Resp reforme = post(GMAO + "/equipements/" + e + "/reformer", body("motif", "Obsolète"), reformeur());
        assertEquals(200, reforme.status, reforme.raw);
        JsonNode reformee = ficheJson(e);
        assertEquals("REFORME", reformee.get("equipement").get("statut").asString());
        assertEquals(false, reformee.get("reformeRecommandee").asBoolean());
        assertEquals(false, get(disponibilite(e), adminA()).raw.contains("\"disponible\":true"));
    }
}
