package com.hemodialyse.backend.characterization.gmao.pilotage;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filet de caractérisation du suivi d'une intervention : indicateurs de réactivité (délai de prise en charge, durée,
 * indisponibilité pendant l'intervention et sur 12 mois, part plafonnée à 100 %) et ligne de temps (chronologie).
 * Historique de statuts et dates semés par SQL relativement à « maintenant » (intervalles clos et dans la fenêtre :
 * minutes exactes) ; les cas dépendant du temps réel du serveur (intervalle ouvert jusqu'à maintenant) sont vérifiés
 * par encadrement.
 */
class IndicateursChronologieCharacterizationIntegrationTest extends PilotageCharacterizationSupport {

    private String indicateurs(UUID i) {
        return GMAO + "/interventions/" + i + "/indicateurs";
    }

    private String chronologie(UUID i) {
        return GMAO + "/interventions/" + i + "/chronologie";
    }

    private OffsetDateTime il_y_a_jours(int jours) {
        return maintenant.minusDays(jours);
    }

    /** « délai/durée/pendant/12 mois/part » d'une réponse d'indicateurs, JSON compact du serveur. */
    private String ind(UUID i) throws Exception {
        Resp r = get(indicateurs(i), adminA());
        return r.status + " " + r.raw;
    }

    // ------------------------------------------------------------------ indicateurs : valeurs exactes

    @Test
    void les_indicateurs_cumulent_les_pannes_de_l_historique_et_calculent_la_part_de_l_intervention() throws Exception {
        UUID e = equipement("E_IND", CENTRE_A);
        historique(e, CENTRE_A, "EN_SERVICE", "EN_MAINTENANCE", il_y_a_jours(100));
        historique(e, CENTRE_A, "EN_MAINTENANCE", "EN_SERVICE", il_y_a_jours(100).plusMinutes(180));
        historique(e, CENTRE_A, "EN_SERVICE", "HORS_SERVICE", il_y_a_jours(50));
        historique(e, CENTRE_A, "HORS_SERVICE", "EN_SERVICE", il_y_a_jours(50).plusMinutes(90));

        Map<String, UUID> cas = new LinkedHashMap<>();
        OffsetDateTime d1 = il_y_a_jours(100).plusMinutes(30);
        cas.put("dans la première panne (120 min sur 270)", intervention("I_IND_1", e, CENTRE_A, x -> {
            x.debut = d1;
            x.fin = d1.plusMinutes(120);
            x.creation = d1.minusMinutes(90);
        }));
        OffsetDateTime d2 = il_y_a_jours(50);
        cas.put("dans la seconde panne (60 min sur 270)", intervention("I_IND_2", e, CENTRE_A, x -> {
            x.debut = d2.plusMinutes(10);
            x.fin = d2.plusMinutes(70);
            x.creation = d2.minusDays(1);
        }));
        cas.put("couvrant les deux pannes (270 sur 270 = 100)", intervention("I_IND_3", e, CENTRE_A, x -> {
            x.debut = il_y_a_jours(101);
            x.fin = il_y_a_jours(49);
            x.creation = il_y_a_jours(102);
        }));
        cas.put("hors de toute panne (0 sur 270)", intervention("I_IND_4", e, CENTRE_A, x -> {
            x.debut = il_y_a_jours(70);
            x.fin = il_y_a_jours(70).plusHours(4);
            x.creation = il_y_a_jours(71);
        }));
        cas.put("intervention commençant au début de la panne", intervention("I_IND_5", e, CENTRE_A, x -> {
            x.debut = il_y_a_jours(100);
            x.fin = il_y_a_jours(100).plusMinutes(60);
            x.creation = il_y_a_jours(100).minusHours(2);
        }));
        cas.put("intervention se terminant à la fin de la panne", intervention("I_IND_6", e, CENTRE_A, x -> {
            x.debut = il_y_a_jours(100).plusMinutes(120);
            x.fin = il_y_a_jours(100).plusMinutes(180);
            x.creation = il_y_a_jours(100);
        }));
        cas.put("créée après son début : délai 0", intervention("I_IND_7", e, CENTRE_A, x -> {
            x.debut = il_y_a_jours(100).plusMinutes(30);
            x.fin = il_y_a_jours(100).plusMinutes(90);
            x.creation = il_y_a_jours(100).plusMinutes(60);
        }));
        cas.put("créée au même instant que son début : délai 0", intervention("I_IND_8", e, CENTRE_A, x -> {
            x.debut = il_y_a_jours(100).plusMinutes(30);
            x.fin = il_y_a_jours(100).plusMinutes(90);
            x.creation = il_y_a_jours(100).plusMinutes(30);
        }));
        cas.put("troncature aux minutes entières (délai 90 min 30 s, durée 119 min 59 s)", intervention("I_IND_9", e, CENTRE_A, x -> {
            x.debut = il_y_a_jours(60);
            x.fin = il_y_a_jours(60).plusSeconds(119 * 60 + 59);
            x.creation = il_y_a_jours(60).minusSeconds(90 * 60 + 30);
        }));
        cas.put("fin antérieure au début (donnée incohérente) : durée 0", intervention("I_IND_10", e, CENTRE_A, x -> {
            x.debut = il_y_a_jours(100).plusMinutes(30);
            x.fin = il_y_a_jours(100);
            x.creation = il_y_a_jours(101);
        }));
        cas.put("fin = début : durée 0", intervention("I_IND_11", e, CENTRE_A, x -> {
            x.debut = il_y_a_jours(100).plusMinutes(30);
            x.fin = il_y_a_jours(100).plusMinutes(30);
            x.creation = il_y_a_jours(101);
        }));
        cas.put("planifiée dans le futur (délai de 3 jours, durée et panne 0)", intervention("I_IND_12", e, CENTRE_A, x -> {
            x.statut = "PLANIFIEE";
            x.debut = maintenant.plusDays(2);
            x.fin = null;
            x.creation = maintenant.minusDays(1);
        }));

        List<String> lignes = new ArrayList<>();
        for (var c : cas.entrySet()) lignes.add(c.getKey() + " -> " + ind(c.getValue()));
        goldenLignes("indicateurs_valeurs_exactes", lignes);
    }

    @Test
    void la_part_est_plafonnee_a_100_quand_l_intervention_deborde_de_la_fenetre_de_12_mois() throws Exception {
        UUID e = equipement("E_PLAFOND", CENTRE_A);
        historique(e, CENTRE_A, "EN_SERVICE", "HORS_SERVICE", il_y_a_jours(440));
        historique(e, CENTRE_A, "HORS_SERVICE", "EN_SERVICE", il_y_a_jours(400));
        historique(e, CENTRE_A, "EN_SERVICE", "EN_MAINTENANCE", il_y_a_jours(30));
        historique(e, CENTRE_A, "EN_MAINTENANCE", "EN_SERVICE", il_y_a_jours(30).plusMinutes(60));
        UUID i = intervention("I_PLAFOND", e, CENTRE_A, x -> {
            x.debut = il_y_a_jours(430);
            x.fin = il_y_a_jours(300);
            x.creation = il_y_a_jours(431);
        });

        Resp r = get(indicateurs(i), adminA());

        assertEquals(200, r.status);
        assertEquals(30 * 24 * 60, json(r).get("indisponibiliteInterventionMinutes").asInt(), "30 jours de panne pendant l'intervention");
        assertEquals(60, json(r).get("indisponibiliteEquipement12MoisMinutes").asInt(), "seule la panne de la fenêtre compte");
        assertEquals(0, new BigDecimal("100.0").compareTo(json(r).get("partIndisponibilite12MoisPct").decimalValue()), r.raw);
    }

    @Test
    void sans_aucune_panne_sur_12_mois_la_part_est_absente() throws Exception {
        UUID e = equipement("E_SANS_PANNE", CENTRE_A);
        UUID i = terminee("I_SANS_PANNE", e, CENTRE_A, il_y_a_jours(10), 120);

        Resp r = get(indicateurs(i), adminA());

        assertEquals(200, r.status);
        assertEquals(0, json(r).get("indisponibiliteEquipement12MoisMinutes").asInt());
        assertTrue(json(r).get("partIndisponibilite12MoisPct").isNull(), r.raw);
        golden("indicateurs_sans_panne", r);
    }

    @Test
    void une_intervention_en_cours_dure_jusqu_a_maintenant_et_une_panne_ouverte_court_jusqu_a_maintenant() throws Exception {
        UUID sain = equipement("E_EN_SERVICE", CENTRE_A);
        UUID enCours = intervention("I_EN_COURS", sain, CENTRE_A, x -> {
            x.statut = "EN_COURS";
            x.debut = maintenant.minusHours(2);
            x.fin = null;
            x.creation = maintenant.minusHours(3);
        });
        UUID enPanne = equipement("E_EN_MAINTENANCE", CENTRE_A, e -> e.statut = "EN_MAINTENANCE");
        UUID surPanne = intervention("I_SUR_PANNE_OUVERTE", enPanne, CENTRE_A, x -> {
            x.statut = "EN_COURS";
            x.debut = maintenant.minusHours(3);
            x.fin = null;
            x.creation = maintenant.minusHours(4);
        });

        var a = json(get(indicateurs(enCours), adminA()));
        assertEquals(60, a.get("delaiPriseEnChargeMinutes").asLong());
        long duree = a.get("dureeInterventionMinutes").asLong();
        assertTrue(duree >= 120 && duree <= 122, "durée jusqu'à maintenant : " + duree);
        assertEquals(0, a.get("indisponibiliteInterventionMinutes").asLong());
        assertEquals(0, a.get("indisponibiliteEquipement12MoisMinutes").asLong());
        assertTrue(a.get("partIndisponibilite12MoisPct").isNull());

        var b = json(get(indicateurs(surPanne), adminA()));
        long pendant = b.get("indisponibiliteInterventionMinutes").asLong();
        assertTrue(pendant >= 180 && pendant <= 182, "panne ouverte pendant l'intervention : " + pendant);
        long fenetre = Duration.between(maintenant.minusMonths(12), maintenant).toMinutes();
        long douzeMois = b.get("indisponibiliteEquipement12MoisMinutes").asLong();
        assertTrue(Math.abs(douzeMois - fenetre) <= 2, "sans historique, la panne courante couvre toute la fenêtre : " + douzeMois + " / " + fenetre);
        double part = b.get("partIndisponibilite12MoisPct").doubleValue();
        assertTrue(Math.abs(part - pendant * 100.0 / douzeMois) < 0.001, "part = pendant / 12 mois : " + part);
    }

    @Test
    void le_statut_precedent_de_la_premiere_entree_de_l_historique_vaut_pour_toute_la_periode_anterieure() throws Exception {
        UUID e = equipement("E_PREMIER_STATUT", CENTRE_A);
        historique(e, CENTRE_A, "HORS_SERVICE", "EN_SERVICE", il_y_a_jours(10));
        UUID i = terminee("I_PREMIER_STATUT", e, CENTRE_A, il_y_a_jours(5), 60);

        var r = json(get(indicateurs(i), adminA()));

        long attendu = Duration.between(maintenant.minusMonths(12), il_y_a_jours(10)).toMinutes();
        assertTrue(Math.abs(r.get("indisponibiliteEquipement12MoisMinutes").asLong() - attendu) <= 2,
                "de l'ouverture de la fenêtre à l'entrée : " + r + " / " + attendu);
        assertEquals(0, r.get("indisponibiliteInterventionMinutes").asLong());
        assertEquals(0.0, r.get("partIndisponibilite12MoisPct").doubleValue(), 0.0);
    }

    @Test
    void les_statuts_de_fin_de_vie_et_les_pannes_hors_fenetre_ne_comptent_pas() throws Exception {
        List<String> lignes = new ArrayList<>();
        for (String statut : List.of("REFORME", "DESACTIF", "A_REFORMER", "EN_SERVICE", "EN_MAINTENANCE", "EN_ATTENTE_PIECE", "HORS_SERVICE")) {
            UUID e = equipement("E_FIN_" + statut, CENTRE_A, x -> x.statut = statut);
            UUID i = terminee("I_FIN_" + statut, e, CENTRE_A, il_y_a_jours(2), 120);
            var r = json(get(indicateurs(i), adminA()));
            long fenetre = Duration.between(maintenant.minusMonths(12), maintenant).toMinutes();
            long douze = r.get("indisponibiliteEquipement12MoisMinutes").asLong();
            lignes.add("statut courant " + statut + " sans historique -> panne 12 mois " + (douze == 0 ? "0" : Math.abs(douze - fenetre) <= 2 ? "toute la fenêtre" : "autre : " + douze)
                    + ", pendant l'intervention " + r.get("indisponibiliteInterventionMinutes").asLong());
        }
        goldenLignes("indicateurs_statut_courant_sans_historique", lignes);
    }

    // ------------------------------------------------------------------ indicateurs : erreurs et autorisations

    @Test
    void les_indicateurs_refusent_les_ids_inconnus_les_autres_centres_les_donnees_incoherentes_et_l_intervention_supprimee_nouveau_code() throws Exception {
        UUID e = equipement("E_ERR", CENTRE_A);
        UUID i = terminee("I_ERR", e, CENTRE_A, il_y_a_jours(3), 60);
        UUID eb = equipement("E_ERR_B", CENTRE_B);
        UUID ib = terminee("I_ERR_B", eb, CENTRE_B, il_y_a_jours(3), 60);
        UUID incoherente = terminee("I_ERR_EQUIPEMENT_AUTRE_CENTRE", eb, CENTRE_A, il_y_a_jours(3), 60);
        UUID supprimee = intervention("I_ERR_SUPPRIMEE", e, CENTRE_A, x -> x.supprimeLe = il_y_a_jours(1));

        List<String> lignes = new ArrayList<>();
        lignes.add("intervention du centre -> " + get(indicateurs(i), adminA()).status);
        lignes.add("intervention inconnue -> " + resume(get(indicateurs(INCONNU), adminA())));
        lignes.add("intervention du centre B vue par A -> " + resume(get(indicateurs(ib), adminA())));
        lignes.add("intervention de A sur l'équipement du centre B -> " + resume(get(indicateurs(incoherente), adminA())));
        lignes.add("intervention supprimée logiquement -> " + get(indicateurs(supprimee), adminA()).status);
        lignes.add("id illisible -> " + resume(get(GMAO + "/interventions/pas-un-uuid/indicateurs", adminA())));
        lignes.add("méthode POST -> " + resume(call(org.springframework.http.HttpMethod.POST, indicateurs(i), null, adminA())));
        goldenLignes("indicateurs_erreurs_nouveau_code", lignes);
    }

    @Test
    void les_indicateurs_et_la_chronologie_sont_reserves_aux_administrateurs_du_centre() throws Exception {
        UUID e = equipement("E_AUTH", CENTRE_A);
        UUID i = terminee("I_AUTH", e, CENTRE_A, il_y_a_jours(3), 60);
        Map<String, org.springframework.test.web.servlet.request.RequestPostProcessor> qui = new LinkedHashMap<>();
        qui.put("ADMIN du centre", adminA());
        qui.put("ADMIN d'un autre centre", adminB());
        qui.put("TECHNICIEN", roleSeul("TECHNICIEN"));
        qui.put("MEDECIN", roleSeul("MEDECIN"));
        qui.put("SUPERADMIN", roleSeul("SUPERADMIN"));
        qui.put("DIRECTION", direction());
        qui.put("GMAO_RECTIFICATION sans ADMIN", roleSeul("GMAO_RECTIFICATION"));
        qui.put("anonyme", null);

        List<String> lignes = new ArrayList<>();
        for (var q : qui.entrySet()) {
            Resp ind = get(indicateurs(i), q.getValue());
            Resp chrono = get(chronologie(i), q.getValue());
            lignes.add(q.getKey() + " -> indicateurs " + (ind.status == 200 ? "200" : resume(ind))
                    + " ; chronologie " + (chrono.status == 200 ? "200" : resume(chrono)));
        }
        goldenLignes("suivi_autorisations", lignes);
    }

    // ------------------------------------------------------------------ chronologie

    @Test
    void la_chronologie_d_une_intervention_terminee_liste_creation_demarrage_cloture_avec_les_noms() throws Exception {
        UUID e = equipement("E_CHRONO", CENTRE_A);
        UUID i = intervention("I_CHRONO", e, CENTRE_A, x -> {
            x.demarrePar = TECH;
            x.demarreLe = T0.plusMinutes(10);
            x.cloturePar = TECH;
            x.clotureLe = T0.plusHours(2).plusMinutes(5);
        });

        golden("chronologie_terminee", get(chronologie(i), adminA()));
    }

    @Test
    void la_chronologie_d_une_intervention_planifiee_ou_annulee() throws Exception {
        UUID e = equipement("E_CHRONO_2", CENTRE_A);
        UUID planifiee = intervention("I_CHRONO_PLAN", e, CENTRE_A, x -> {
            x.statut = "PLANIFIEE";
            x.fin = null;
        });
        UUID annulee = intervention("I_CHRONO_ANN", e, CENTRE_A, x -> {
            x.statut = "ANNULEE";
            x.fin = null;
            x.annulePar = ADMIN_A;
            x.annuleLe = T0.plusHours(3);
            x.modification = T0.plusHours(3);
            x.modifiePar = ADMIN_A;
        });

        golden("chronologie_planifiee", get(chronologie(planifiee), adminA()));
        golden("chronologie_annulee", get(chronologie(annulee), adminA()));
    }

    @Test
    void la_chronologie_d_une_intervention_rectifiee_rejoue_la_cloture_anterieure_puis_la_rectification() throws Exception {
        UUID e = equipement("E_CHRONO_3", CENTRE_A);
        UUID i = intervention("I_CHRONO_RECT", e, CENTRE_A, x -> {
            x.statut = "EN_COURS";
            x.fin = null;
            x.demarrePar = TECH;
            x.demarreLe = T0;
            x.modification = T0.plusDays(2);
            x.modifiePar = ADMIN_A;
        });
        rectification(i, "Date de début erronée", ADMIN_A, T0.plusDays(1), T0.plusHours(2), TECH);
        rectification(i, "Seconde correction", TECH, T0.plusDays(1).plusHours(3), T0.plusDays(1).plusHours(1), ADMIN_A);

        golden("chronologie_rectifiee", get(chronologie(i), adminA()));
    }

    @Test
    void la_chronologie_trie_par_instant_conserve_l_ordre_des_ex_aequo_et_n_ajoute_la_modification_que_si_elle_est_la_plus_recente() throws Exception {
        UUID e = equipement("E_CHRONO_4", CENTRE_A);
        UUID sansNom = nom("SANS_NOM_COMPLET");
        utilisateur(sansNom, "zt-p6-sans-nom", null);
        Map<String, UUID> cas = new LinkedHashMap<>();
        cas.put("démarrage avant la création (donnée incohérente) : trié par instant", intervention("I_ORD_1", e, CENTRE_A, x -> {
            x.creation = T0;
            x.demarreLe = T0.minusHours(1);
            x.demarrePar = TECH;
        }));
        cas.put("création et démarrage au même instant : ordre d'insertion (CREEE puis DEMARREE)", intervention("I_ORD_2", e, CENTRE_A, x -> {
            x.creation = T0;
            x.demarreLe = T0;
            x.demarrePar = TECH;
        }));
        cas.put("modification égale au dernier événement : pas de MODIFIEE", intervention("I_ORD_3", e, CENTRE_A, x -> {
            x.demarreLe = T0.plusHours(1);
            x.demarrePar = TECH;
            x.modification = T0.plusHours(1);
            x.modifiePar = TECH;
        }));
        cas.put("modification antérieure au dernier événement : pas de MODIFIEE", intervention("I_ORD_4", e, CENTRE_A, x -> {
            x.demarreLe = T0.plusHours(1);
            x.demarrePar = TECH;
            x.modification = T0.plusMinutes(30);
            x.modifiePar = TECH;
        }));
        cas.put("modification postérieure : MODIFIEE en dernier, auteur = modifié par", intervention("I_ORD_5", e, CENTRE_A, x -> {
            x.demarreLe = T0.plusHours(1);
            x.demarrePar = TECH;
            x.modification = T0.plusHours(5);
            x.modifiePar = ADMIN_A;
        }));
        cas.put("modification sans modifié par : auteur absent", intervention("I_ORD_6", e, CENTRE_A, x -> x.modification = T0.plusHours(5)));
        cas.put("auteur sans nom complet : le nom d'utilisateur; auteur inconnu : sans nom; auteur absent", intervention("I_ORD_7", e, CENTRE_A, x -> {
            x.creePar = sansNom;
            x.demarreLe = T0.plusHours(1);
            x.demarrePar = INCONNU;
            x.statut = "TERMINEE";
            x.clotureLe = T0.plusHours(2);
            x.cloturePar = null;
        }));

        List<String> lignes = new ArrayList<>();
        for (var c : cas.entrySet()) {
            Resp r = get(chronologie(c.getValue()), adminA());
            List<String> evts = new ArrayList<>();
            json(r).forEach(n -> evts.add(n.get("type").asString() + "@" + n.get("at").asString() + " par=" + (n.get("parId").isNull() ? "-" : n.get("parId").asString())
                    + " nom=" + (n.get("parNom").isNull() ? "-" : n.get("parNom").asString())));
            lignes.add(c.getKey() + " -> " + r.status + " " + new Scrubber(r, false).scrubString(String.join(" ; ", evts)));
        }
        goldenLignes("chronologie_ordre_et_noms", lignes);
    }

    @Test
    void la_chronologie_ne_cite_ni_les_taches_ni_les_lignes_de_cout_et_suit_les_ecritures_du_nouveau_module() throws Exception {
        UUID e = equipement("E_CHRONO_5", CENTRE_A);
        OffsetDateTime debut = maintenant.minusDays(2).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        UUID i = planifierParHttp(e, "EN_SERVICE", null, debut);

        assertEquals("CREEE", types(get(chronologie(i), adminA())));
        demarrerParHttp(i);
        assertEquals("CREEE,DEMARREE", types(get(chronologie(i), adminA())));
        assertEquals(200, post(GMAO + "/interventions/" + i + "/taches", body("description", "Purger le circuit"), adminA()).status);
        assertEquals(200, post(GMAO + "/interventions/" + i + "/lignes-cout",
                body("type", "PIECE", "libelle", "Joint", "quantite", new BigDecimal("1"), "prixUnitaire", new BigDecimal("10.00")),
                adminA()).status);
        String apresTacheEtLigne = types(get(chronologie(i), adminA()));
        terminerParHttp(i, "EN_SERVICE", debut.plusHours(2));
        String apresCloture = types(get(chronologie(i), adminA()));
        var rectifieur = qui(CENTRE_A, ADMIN_A, "ADMIN", "GMAO_RECTIFICATION");
        assertEquals(200, post(GMAO + "/interventions/" + i + "/rectifier", body("motif", "Erreur"), rectifieur).status);
        String apresRectification = types(get(chronologie(i), adminA()));

        goldenLignes("chronologie_ecritures_du_nouveau_module", List.of(
                "après tâche et ligne de coût -> " + apresTacheEtLigne,
                "après clôture -> " + apresCloture,
                "après rectification -> " + apresRectification));
    }

    @Test
    void la_chronologie_refuse_les_ids_inconnus_les_autres_centres_et_l_intervention_supprimee_nouveau_code() throws Exception {
        UUID e = equipement("E_CHRONO_ERR", CENTRE_A);
        UUID i = terminee("I_CHRONO_ERR", e, CENTRE_A, il_y_a_jours(3), 60);
        UUID eb = equipement("E_CHRONO_ERR_B", CENTRE_B);
        UUID ib = terminee("I_CHRONO_ERR_B", eb, CENTRE_B, il_y_a_jours(3), 60);
        UUID supprimee = intervention("I_CHRONO_SUPPRIMEE", e, CENTRE_A, x -> x.supprimeLe = il_y_a_jours(1));
        UUID surEquipementAutreCentre = terminee("I_CHRONO_EQUIPEMENT_B", eb, CENTRE_A, il_y_a_jours(3), 60);

        List<String> lignes = new ArrayList<>();
        lignes.add("intervention du centre -> " + get(chronologie(i), adminA()).status);
        lignes.add("intervention inconnue -> " + resume(get(chronologie(INCONNU), adminA())));
        lignes.add("intervention du centre B vue par A -> " + resume(get(chronologie(ib), adminA())));
        lignes.add("intervention supprimée logiquement -> " + get(chronologie(supprimee), adminA()).status);
        lignes.add("intervention de A sur un équipement de B (la chronologie ne lit pas l'équipement) -> "
                + get(chronologie(surEquipementAutreCentre), adminA()).status);
        lignes.add("id illisible -> " + resume(get(GMAO + "/interventions/pas-un-uuid/chronologie", adminA())));
        goldenLignes("chronologie_erreurs_nouveau_code", lignes);
        assertNull(null);
    }

    private String types(Resp r) {
        assertEquals(200, r.status, r.raw);
        List<String> t = new ArrayList<>();
        json(r).forEach(n -> t.add(n.get("type").asString()));
        return String.join(",", t);
    }
}
