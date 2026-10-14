package com.hemodialyse.backend.characterization.gmao.interventions;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Slice gmao-g4-interventions, thème « cycle de vie » : planifier, lire, lister, démarrer, terminer, annuler, transitions
 * interdites, effets sur l'équipement (statut, journal, alerte, cache) et parcours complets.
 */
class InterventionCycleDeVieCharacterizationIntegrationTest extends InterventionCharacterizationSupport {

    private static final String[] STATUTS_EQUIPEMENT = {"EN_SERVICE", "EN_MAINTENANCE", "EN_ATTENTE_PIECE", "HORS_SERVICE",
            "DESACTIF", "A_REFORMER", "REFORME"};

    private void amorcerCache() {
        caches.getCache("ref.generateurs").put(CENTRE_A.toString(), List.of());
        assertNotNull(caches.getCache("ref.generateurs").get(CENTRE_A.toString()));
    }

    private boolean cacheAmorce() {
        return caches.getCache("ref.generateurs").get(CENTRE_A.toString()) != null;
    }

    private void cas(ObjectNode m, String etiquette, String corps) throws Exception {
        m.put(etiquette, resume(post("", corps, adminA())));
    }

    // ------------------------------------------------------------------ planifier

    @Test
    void planifier_une_intervention_complete_cree_une_intervention_planifiee_sans_toucher_l_equipement() throws Exception {
        String corps = body("equipementId", GEN, "type", "PREVENTIVE", "dateDebut", DEBUT, "description", "Revision annuelle",
                "intervenantId", I_INTERNE, "etatEquipementAvant", "EN_MAINTENANCE", "symptome", "  Bruit anormal  ",
                "priorite", "HAUTE", "echeance", OffsetDateTime.of(2099, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        Resp r = post("", corps, adminA());
        golden("planifier_intervention_complete_201", r);
        UUID i = UUID.fromString(json(r).get("id").asString());
        assertEquals("PLANIFIEE", statutIntervention(i));
        assertEquals("EN_SERVICE", statutEquipement(GEN));
        assertEquals(List.of(), historique(GEN));
        assertEquals(List.of(), alertes());
        golden("lire_intervention_planifiee", get("/" + i, adminA()));
    }

    @Test
    void planifier_avec_les_seuls_champs_obligatoires_applique_la_priorite_normale() throws Exception {
        Resp r = post("", creation(GEN, "EN_SERVICE", null), adminA());
        golden("planifier_intervention_minimale_201", r);
        assertEquals("NORMALE", json(r).get("priorite").asString());
        assertTrue(json(r).get("enRetard").asBoolean(), "début dans le passé : en retard");
    }

    @Test
    void planifier_une_intervention_dans_le_futur_n_est_pas_en_retard() throws Exception {
        String corps = modifie(creationAvec("echeance", null), "dateDebut", "2099-01-01T08:00:00Z");
        Resp r = post("", corps, adminA());
        assertEquals(201, r.status, r.raw);
        assertFalse(json(r).get("enRetard").asBoolean());
        assertFalse(json(r).get("echeanceDepassee").asBoolean());
    }

    @Test
    void planifier_avec_une_echeance_depassee_signale_l_echeance_depassee() throws Exception {
        Resp r = post("", creationAvec("echeance", "2026-03-11T08:00:00Z"), adminA());
        assertEquals(201, r.status, r.raw);
        assertTrue(json(r).get("echeanceDepassee").asBoolean());
    }

    @Test
    void planifier_ignore_les_champs_de_pilotage_fournis_par_le_client() throws Exception {
        String corps = modifie(modifie(modifie(creation(GEN, "EN_SERVICE", null), "statut", "TERMINEE"), "id", INCONNU),
                "centreId", CENTRE_B);
        Resp r = post("", corps, adminA());
        golden("planifier_ignore_statut_id_centre_du_corps", r);
        assertEquals(CENTRE_A.toString(), json(r).get("centreId").asString());
    }

    @Test
    void planifier_restitue_la_date_avec_son_decalage_a_la_creation_mais_en_utc_a_la_lecture() throws Exception {
        Resp r = post("", creationAvec("dateDebut", "2026-03-10T09:00:00+01:00"), adminA());
        assertEquals(201, r.status, r.raw);
        golden("planifier_date_avec_decalage_reponse", r);
        UUID i = UUID.fromString(json(r).get("id").asString());
        golden("planifier_date_avec_decalage_relecture", get("/" + i, adminA()));
    }

    @Test
    void planifier_refuse_ou_accepte_selon_le_corps_matrice_de_validation() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        cas(m, "sans_type", creationAvec("type", null));
        cas(m, "type_inconnu", creationAvec("type", "BIZARRE"));
        cas(m, "type_minuscules", creationAvec("type", "curative"));
        for (String t : new String[]{"PREVENTIVE", "CURATIVE", "URGENTE", "CONTROLE", "INSTALLATION", "DEINSTALLATION",
                "REMPLACEMENT_PIECE", "REVISION_COMPLETE"}) {
            cas(m, "type_" + t, creationAvec("type", t));
        }
        cas(m, "sans_description", creationAvec("description", null));
        cas(m, "description_blanche", creationAvec("description", "   "));
        cas(m, "sans_date_debut", creationAvec("dateDebut", null));
        cas(m, "date_debut_illisible", creationAvec("dateDebut", "demain"));
        cas(m, "sans_etat_avant", creationAvec("etatEquipementAvant", null));
        cas(m, "etat_avant_inconnu", creationAvec("etatEquipementAvant", "CASSE"));
        cas(m, "etat_avant_minuscules", creationAvec("etatEquipementAvant", "en_service"));
        for (String s : STATUTS_EQUIPEMENT) {
            cas(m, "etat_avant_" + s, creationAvec("etatEquipementAvant", s));
        }
        cas(m, "priorite_inconnue", creationAvec("priorite", "CRITIQUE"));
        cas(m, "priorite_vide", creationAvec("priorite", ""));
        cas(m, "priorite_blanche", creationAvec("priorite", "  "));
        cas(m, "priorite_minuscules", creationAvec("priorite", "haute"));
        cas(m, "priorite_urgente", creationAvec("priorite", "URGENTE"));
        cas(m, "echeance_avant_le_debut", creationAvec("echeance", "2026-03-10T07:59:59Z"));
        cas(m, "echeance_egale_au_debut", creationAvec("echeance", "2026-03-10T08:00:00Z"));
        cas(m, "sans_equipement", creationAvec("equipementId", null));
        cas(m, "equipement_inconnu", creationAvec("equipementId", INCONNU));
        cas(m, "equipement_d_un_autre_centre", creationAvec("equipementId", AUTRE_CENTRE));
        cas(m, "equipement_reforme", creationAvec("equipementId", REFORME));
        cas(m, "equipement_supprime_logiquement", creationAvec("equipementId", SUPPRIME));
        cas(m, "equipement_identifiant_invalide", creationAvec("equipementId", "pas-un-uuid"));
        cas(m, "intervenant_inconnu", creationAvec("intervenantId", I_INCONNU));
        cas(m, "intervenant_d_un_autre_centre", creationAvec("intervenantId", I_AUTRE_CENTRE));
        cas(m, "intervenant_desactive", creationAvec("intervenantId", I_DESACTIVE));
        cas(m, "symptome_blanc", creationAvec("symptome", "   "));
        cas(m, "corps_objet_vide", "{}");
        cas(m, "corps_json_illisible", "{pas du json");
        cas(m, "corps_absent", null);
        goldenNode("planifier_matrice_de_validation", m);
        // seules les créations acceptées ont laissé des lignes ; aucune ne touche l'équipement
        assertEquals("EN_SERVICE", statutEquipement(GEN));
        assertEquals(List.of(), historique(GEN));
    }

    @Test
    void planifier_un_symptome_blanc_est_enregistre_vide_et_un_symptome_est_rogne() throws Exception {
        Resp blanc = post("", creationAvec("symptome", "   "), adminA());
        assertEquals(201, blanc.status);
        assertTrue(json(blanc).get("symptome").isNull());
        Resp rogne = post("", creationAvec("symptome", "  fuite  "), adminA());
        assertEquals("fuite", json(rogne).get("symptome").asString());
    }

    @Test
    void planifier_ne_rogne_pas_la_description_et_accepte_les_textes_longs() throws Exception {
        Resp r = post("", creationAvec("description", "  Panne  " + "x".repeat(600)), adminA());
        assertEquals(201, r.status, r.raw);
        assertTrue(json(r).get("description").asString().startsWith("  Panne  x"));
        assertEquals(609, json(r).get("description").asString().length());
    }

    // ------------------------------------------------------------------ lecture

    @Test
    void lire_une_intervention_inconnue_ou_d_un_autre_centre_repond_404_sans_corps() throws Exception {
        UUID autre = jdbcIntervention(CENTRE_B, AUTRE_CENTRE, "PLANIFIEE");
        golden("lire_intervention_inconnue_404", get("/" + INCONNU, adminA()));
        golden("lire_intervention_d_un_autre_centre_404", get("/" + autre, adminA()));
        assertEquals(200, get("/" + autre, adminB()).status);
    }

    @Test
    void lire_avec_un_identifiant_illisible_repond_une_erreur() throws Exception {
        golden("lire_identifiant_invalide", get("/pas-un-uuid", adminA()));
    }

    @Test
    void lire_une_intervention_supprimee_logiquement_la_renvoie_et_une_commande_la_retablit() throws Exception {
        UUID i = planifier(null);
        jdbc.update("UPDATE gmao_interventions SET deleted_at = ? WHERE id = ?", OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC), i);
        assertEquals(200, get("/" + i, adminA()).status);
        assertEquals(0, json(get("?size=50", adminA())).get("total").asInt());
        assertEquals(200, post("/" + i + "/demarrer", null, adminA()).status);
        assertNull(row(i).get("DELETED_AT"), "toEntity ne reporte pas deleted_at : la commande rétablit l'intervention");
        assertEquals(1, json(get("?size=50", adminA())).get("total").asInt());
    }

    private UUID jdbcIntervention(UUID centre, UUID equipement, String statut) {
        UUID i = UUID.randomUUID();
        jdbc.update("INSERT INTO gmao_interventions (id, equipement_id, centre_id, type, statut, date_debut, description, "
                        + "date_creation, cree_par, priorite) VALUES (?, ?, ?, 'CURATIVE', ?, ?, 'Semee', ?, ?, 'NORMALE')",
                i, equipement, centre, statut, DEBUT, DEBUT, ADMIN_A);
        return i;
    }

    // ------------------------------------------------------------------ liste

    private void semer(UUID intervention, int jour) {
        jdbc.update("UPDATE gmao_interventions SET date_creation = ? WHERE id = ?",
                OffsetDateTime.of(2026, 4, jour, 9, 0, 0, 0, ZoneOffset.UTC), intervention);
    }

    private List<UUID> jeuDeListe() throws Exception {
        UUID planifiee = planifier(GEN, "EN_SERVICE", null);
        UUID enCours = demarree(null);
        UUID terminee = terminee(I_INTERNE, 90);
        UUID annulee = dansLeStatut("ANNULEE");
        UUID surEau = planifier(EAU, "EN_SERVICE", null);
        UUID autreCentre = jdbcIntervention(CENTRE_B, AUTRE_CENTRE, "PLANIFIEE");
        UUID supprimee = planifier(null);
        jdbc.update("UPDATE gmao_interventions SET deleted_at = ? WHERE id = ?", OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC), supprimee);
        List<UUID> ordre = List.of(planifiee, enCours, terminee, annulee, surEau, autreCentre, supprimee);
        for (int k = 0; k < ordre.size(); k++) {
            semer(ordre.get(k), k + 1);
        }
        return ordre;
    }

    @Test
    void lister_renvoie_les_interventions_du_centre_les_plus_recentes_d_abord_sans_les_supprimees() throws Exception {
        jeuDeListe();
        Resp r = get("?size=50", adminA());
        golden("lister_toutes_les_interventions_du_centre", r);
        assertEquals(5, json(r).get("total").asInt());
        assertEquals(1, json(get("?size=50", adminB())).get("total").asInt());
    }

    @Test
    void lister_filtre_par_statut_ou_par_equipement_et_pagine() throws Exception {
        jeuDeListe();
        ObjectNode m = mapper.createObjectNode();
        String[] requetes = {"?statut=EN_COURS", "?statut=TERMINEE", "?statut=en_cours", "?statut=INCONNU", "?statut=",
                "?statut=EN_ATTENTE_VALIDATION", "?equipementId=" + EAU, "?equipementId=" + GEN, "?equipementId=" + GEN + "&statut=TERMINEE",
                "?equipementId=" + AUTRE_CENTRE, "?equipementId=" + INCONNU, "?equipementId=pas-un-uuid",
                "?size=2&page=0", "?size=2&page=1", "?size=2&page=2", "?size=2&page=99", "?size=0", "?page=-1", "?size=abc",
                "?size=100000", ""};
        for (String q : requetes) {
            Resp r = get(q, adminA());
            ObjectNode e = mapper.createObjectNode();
            e.put("resume", resume(r));
            if (r.status == 200) {
                e.put("total", json(r).path("total").asInt(-1));
                e.put("page", json(r).path("page").asInt(-1));
                e.put("size", json(r).path("size").asInt(-1));
                e.put("items", json(r).path("items").size());
                List<String> statuts = new ArrayList<>();
                json(r).path("items").forEach(x -> statuts.add(x.path("statut").asString()));
                e.put("statuts", String.join(",", statuts));
            }
            m.set("GET " + (q.isEmpty() ? "(sans paramètre)" : q).replace(EAU.toString(), "<STATION_EAU>")
                    .replace(GEN.toString(), "<GENERATEUR_G01>").replace(AUTRE_CENTRE.toString(), "<GENERATEUR_AUTRE_CENTRE>")
                    .replace(INCONNU.toString(), "<INCONNU>"), e);
        }
        goldenNode("lister_filtres_et_pagination", m);
    }

    // ------------------------------------------------------------------ démarrer

    @Test
    void demarrer_met_l_intervention_en_cours_et_l_equipement_dans_l_etat_avant_avec_journal_alerte_et_cache() throws Exception {
        UUID i = planifier(GEN, "EN_MAINTENANCE", I_INTERNE);
        amorcerCache();
        Resp r = post("/" + i + "/demarrer", null, adminA());
        golden("demarrer_nominal_200", r);
        assertEquals("EN_COURS", statutIntervention(i));
        assertEquals(ADMIN_A, UUID.fromString(String.valueOf(row(i).get("DEMARRE_PAR"))));
        assertNotNull(row(i).get("DEMARRE_LE"));
        assertEquals("EN_MAINTENANCE", statutEquipement(GEN));
        assertEquals(List.of("EN_SERVICE>EN_MAINTENANCE|Intervention démarrée"), historique(GEN));
        assertEquals(List.of("G-01/EN_MAINTENANCE/0//roles,ADMIN,SECRETAIRE,"), alertes());
        assertFalse(cacheAmorce(), "le démarrage évince ref.generateurs");
        assertEquals(List.of(), lignes(i), "le démarrage ne valorise rien");
    }

    @Test
    void demarrer_avec_l_etat_deja_en_vigueur_ne_laisse_ni_journal_ni_alerte() throws Exception {
        UUID i = planifier(GEN, "EN_SERVICE", null);
        assertEquals(200, post("/" + i + "/demarrer", null, adminA()).status);
        assertEquals("EN_SERVICE", statutEquipement(GEN));
        assertEquals(List.of(), historique(GEN));
        assertEquals(List.of(), alertes());
    }

    @Test
    void demarrer_sur_une_station_d_eau_change_le_statut_sans_alerte_de_generateur() throws Exception {
        UUID i = planifier(EAU, "HORS_SERVICE", null);
        assertEquals(200, post("/" + i + "/demarrer", null, adminA()).status);
        assertEquals("HORS_SERVICE", statutEquipement(EAU));
        assertEquals(List.of("EN_SERVICE>HORS_SERVICE|Intervention démarrée"), historique(EAU));
        assertEquals(List.of(), alertes());
    }

    @Test
    void demarrer_selon_le_statut_de_l_equipement_matrice() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        for (String depart : STATUTS_EQUIPEMENT) {
            cleanup();
            setUp();
            jdbc.update("UPDATE gmao_equipements SET statut = ? WHERE id = ?", depart, GEN);
            UUID i = planifier(GEN, "EN_MAINTENANCE", null);
            Resp r = post("/" + i + "/demarrer", null, adminA());
            ObjectNode e = mapper.createObjectNode();
            e.put("reponse", resume(r));
            e.put("statutIntervention", statutIntervention(i));
            e.put("statutEquipement", statutEquipement(GEN));
            e.put("journal", String.join(" ; ", historique(GEN)));
            e.put("alertes", String.join(" ; ", alertes()));
            m.set("equipement_" + depart, e);
        }
        goldenNode("demarrer_matrice_selon_le_statut_de_l_equipement", m);
    }

    @Test
    void demarrer_sur_un_equipement_reforme_est_refuse_et_l_intervention_reste_planifiee() throws Exception {
        UUID i = planifier(REFORME, "EN_MAINTENANCE", I_INTERNE);
        Resp r = post("/" + i + "/demarrer", null, adminA());
        golden("demarrer_sur_equipement_reforme_422", r);
        assertEquals("PLANIFIEE", statutIntervention(i));
        assertNull(row(i).get("DEMARRE_LE"));
        assertEquals("REFORME", statutEquipement(REFORME));
        assertEquals(List.of(), historique(REFORME));
    }

    @Test
    void demarrer_deux_fois_ou_une_intervention_inconnue_ou_d_un_autre_centre_est_refuse() throws Exception {
        UUID i = demarree(null);
        golden("demarrer_deux_fois_422", post("/" + i + "/demarrer", null, adminA()));
        golden("demarrer_inconnue_400", post("/" + INCONNU + "/demarrer", null, adminA()));
        UUID autre = jdbcIntervention(CENTRE_B, AUTRE_CENTRE, "PLANIFIEE");
        golden("demarrer_d_un_autre_centre_400", post("/" + autre + "/demarrer", null, adminA()));
        assertEquals("PLANIFIEE", statutIntervention(autre));
        golden("demarrer_identifiant_invalide", post("/pas-un-uuid/demarrer", null, adminA()));
    }

    // ------------------------------------------------------------------ terminer

    @Test
    void terminer_clot_l_intervention_et_remet_l_equipement_dans_l_etat_apres_avec_journal() throws Exception {
        UUID i = planifier(GEN, "EN_MAINTENANCE", null);
        assertEquals(200, post("/" + i + "/demarrer", null, adminA()).status);
        amorcerCache();
        Resp r = post("/" + i + "/terminer", body("actions", "Remplacement pompe", "etatEquipementApres", "EN_SERVICE",
                "dateFin", DEBUT.plusMinutes(95), "cause", "  Joint usé  "), adminA());
        golden("terminer_nominal_200", r);
        assertEquals("TERMINEE", statutIntervention(i));
        assertEquals("Joint usé", row(i).get("CAUSE"));
        assertNotNull(row(i).get("CLOTURE_LE"));
        assertEquals(ADMIN_A, UUID.fromString(String.valueOf(row(i).get("CLOTURE_PAR"))));
        assertEquals("EN_SERVICE", statutEquipement(GEN));
        assertEquals(List.of("EN_SERVICE>EN_MAINTENANCE|Intervention démarrée", "EN_MAINTENANCE>EN_SERVICE|Intervention terminée"),
                historique(GEN));
        assertEquals(1, alertes().size(), "le retour en service n'alerte pas");
        assertFalse(cacheAmorce(), "la clôture évince ref.generateurs");
    }

    @Test
    void terminer_valide_le_corps_matrice() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        String[][] cas = {
                {"sans_actions", "actions", null}, {"actions_blanches", "actions", "   "},
                {"sans_etat_apres", "etatEquipementApres", null}, {"etat_apres_inconnu", "etatEquipementApres", "CASSE"},
                {"etat_apres_minuscules", "etatEquipementApres", "en_service"},
                {"sans_date_fin", "dateFin", null}, {"date_fin_illisible", "dateFin", "bientot"},
                {"date_fin_avant_le_debut", "dateFin", "2026-03-10T07:59:00Z"},
                {"date_fin_egale_au_debut", "dateFin", "2026-03-10T08:00:00Z"},
                {"cause_blanche", "cause", "   "}};
        for (String[] c : cas) {
            cleanup();
            setUp();
            UUID i = demarree(null);
            m.put(c[0], resume(post("/" + i + "/terminer", clotureAvec(c[1], c[2]), adminA())) + " | " + statutIntervention(i));
        }
        for (String etat : STATUTS_EQUIPEMENT) {
            cleanup();
            setUp();
            UUID i = demarree(null);
            m.put("etat_apres_" + etat, resume(post("/" + i + "/terminer", clotureAvec("etatEquipementApres", etat), adminA()))
                    + " | " + statutIntervention(i) + " | equipement " + statutEquipement(GEN));
        }
        cleanup();
        setUp();
        UUID i = demarree(null);
        m.put("corps_objet_vide", resume(post("/" + i + "/terminer", "{}", adminA())));
        m.put("corps_illisible", resume(post("/" + i + "/terminer", "{pas du json", adminA())));
        m.put("corps_absent", resume(post("/" + i + "/terminer", null, adminA())));
        goldenNode("terminer_matrice_de_validation", m);
    }

    @Test
    void terminer_une_intervention_de_duree_nulle_est_acceptee() throws Exception {
        UUID i = demarree(I_INTERNE);
        Resp r = post("/" + i + "/terminer", cloture("EN_SERVICE", DEBUT), adminA());
        golden("terminer_duree_nulle_200", r);
        assertEquals(List.of(), lignes(i));
    }

    @Test
    void terminer_une_intervention_non_en_cours_est_refuse() throws Exception {
        UUID planifiee = planifier(null);
        golden("terminer_planifiee_422", post("/" + planifiee + "/terminer", cloture(60), adminA()));
        UUID terminee = terminee(null, 60);
        golden("terminer_deja_terminee_422", post("/" + terminee + "/terminer", cloture(60), adminA()));
    }

    @Test
    void terminer_selon_l_etat_avant_et_l_etat_apres_matrice_des_effets_sur_le_generateur() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        for (String avant : new String[]{"EN_SERVICE", "EN_MAINTENANCE", "EN_ATTENTE_PIECE", "HORS_SERVICE"}) {
            for (String apres : new String[]{"EN_SERVICE", "EN_ATTENTE_PIECE", "HORS_SERVICE", "A_REFORMER"}) {
                cleanup();
                setUp();
                UUID i = planifier(GEN, avant, null);
                assertEquals(200, post("/" + i + "/demarrer", null, adminA()).status);
                Resp r = post("/" + i + "/terminer", cloture(apres, DEBUT.plusMinutes(30)), adminA());
                ObjectNode e = mapper.createObjectNode();
                e.put("reponse", resume(r));
                e.put("statutEquipement", statutEquipement(GEN));
                e.put("journal", String.join(" ; ", historique(GEN)));
                e.put("alertes", String.join(" ; ", alertes()));
                m.set(avant + "_vers_" + apres, e);
            }
        }
        goldenNode("terminer_matrice_etat_avant_etat_apres", m);
    }

    @Test
    void terminer_en_proposant_la_reforme_pose_a_reformer_sans_reformer_et_previent_les_habilitations_nouveau_code() throws Exception {
        UUID i = demarree(null);
        assertEquals(200, post("/" + i + "/terminer", cloture("A_REFORMER", DEBUT.plusMinutes(30)), adminA()).status);
        assertEquals("A_REFORMER", statutEquipement(GEN));
        assertEquals(List.of("EN_SERVICE>A_REFORMER|Intervention terminée"), historique(GEN));
        assertEquals(List.of("G-01/A_REFORMER/0//roles,ADMIN,SECRETAIRE,"), alertes());
        // ÉCART DÉCIDÉ le 2026-10-12 (KB-GMAO-57, P2 créée à la demande de l'utilisateur) : le legacy ne créait aucune
        // notification de réforme ; le nouveau code ajoute « REFORME_PROPOSEE » à l'alerte d'indisponibilité (voir
        // InterventionReformeProposeeNouveauCodeIntegrationTest pour le détail)
        assertEquals(List.of("GENERATEUR_INDISPONIBLE", "REFORME_PROPOSEE"), typesDeNotifications().stream().sorted().toList());
    }

    @Test
    void terminer_est_refuse_si_l_equipement_a_ete_reforme_entre_temps_et_rien_n_est_enregistre() throws Exception {
        UUID i = demarree(I_INTERNE);
        jdbc.update("UPDATE gmao_equipements SET statut = 'REFORME' WHERE id = ?", GEN);
        Resp r = post("/" + i + "/terminer", cloture(120), adminA());
        golden("terminer_apres_reforme_de_l_equipement_422", r);
        assertEquals("EN_COURS", statutIntervention(i));
        assertEquals(List.of(), lignes(i), "la valorisation n'est pas enregistrée quand la clôture échoue");
        assertNull(row(i).get("CLOTURE_LE"));
        assertEquals("REFORME", statutEquipement(GEN));
    }

    // ------------------------------------------------------------------ annuler

    @Test
    void annuler_une_planifiee_la_marque_annulee_avec_la_raison_dans_les_observations() throws Exception {
        UUID i = planifier(null);
        Resp r = post("/" + i + "/annuler", body("raison", "Doublon"), adminA());
        golden("annuler_planifiee_200", r);
        assertEquals("ANNULEE", statutIntervention(i));
        assertEquals(ADMIN_A, UUID.fromString(String.valueOf(row(i).get("ANNULE_PAR"))));
        assertNotNull(row(i).get("ANNULE_LE"));
        assertEquals("EN_SERVICE", statutEquipement(GEN));
    }

    @Test
    void annuler_une_intervention_en_cours_laisse_l_equipement_dans_l_etat_pose_au_demarrage() throws Exception {
        UUID i = planifier(GEN, "EN_MAINTENANCE", null);
        assertEquals(200, post("/" + i + "/demarrer", null, adminA()).status);
        Resp r = post("/" + i + "/annuler", body("raison", "Pièce non livrée"), adminA());
        golden("annuler_en_cours_200", r);
        assertEquals("ANNULEE", statutIntervention(i));
        assertEquals("EN_MAINTENANCE", statutEquipement(GEN), "l'annulation ne remet pas l'équipement en service");
        assertEquals(List.of("EN_SERVICE>EN_MAINTENANCE|Intervention démarrée"), historique(GEN));
    }

    @Test
    void annuler_une_intervention_deja_annulee_est_accepte_et_cumule_les_raisons() throws Exception {
        UUID i = planifier(null);
        assertEquals(200, post("/" + i + "/annuler", body("raison", "Première"), adminA()).status);
        Resp r = post("/" + i + "/annuler", body("raison", "Seconde"), adminA());
        golden("annuler_deja_annulee_200_raisons_cumulees", r);
        assertEquals("ANNULEE", statutIntervention(i));
    }

    @Test
    void annuler_une_intervention_terminee_ou_avec_une_raison_vide_est_refuse() throws Exception {
        UUID terminee = terminee(null, 60);
        golden("annuler_terminee_422", post("/" + terminee + "/annuler", body("raison", "Erreur"), adminA()));
        assertEquals("TERMINEE", statutIntervention(terminee));
        UUID i = planifier(null);
        golden("annuler_raison_vide_400", post("/" + i + "/annuler", body("raison", "  "), adminA()));
        golden("annuler_sans_raison_400", post("/" + i + "/annuler", "{}", adminA()));
        golden("annuler_sans_corps", post("/" + i + "/annuler", null, adminA()));
        golden("annuler_inconnue_400", post("/" + INCONNU + "/annuler", body("raison", "x"), adminA()));
        assertEquals("PLANIFIEE", statutIntervention(i));
    }

    @Test
    void annuler_une_intervention_en_attente_de_validation_est_accepte() throws Exception {
        UUID i = dansLeStatut("EN_ATTENTE_VALIDATION");
        Resp r = post("/" + i + "/annuler", body("raison", "Abandon"), adminA());
        assertEquals(200, r.status, r.raw);
        assertEquals("ANNULEE", statutIntervention(i));
    }

    // ------------------------------------------------------------------ transitions

    @Test
    void les_commandes_selon_le_statut_de_l_intervention_matrice_des_transitions() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        for (String statut : new String[]{"PLANIFIEE", "EN_COURS", "TERMINEE", "ANNULEE", "EN_ATTENTE_VALIDATION"}) {
            ObjectNode e = mapper.createObjectNode();
            String[] commandes = {"demarrer", "terminer", "annuler", "ajouter_ligne", "supprimer_ligne_inconnue", "rectifier"};
            for (String commande : commandes) {
                cleanup();
                setUp();
                UUID i = dansLeStatut(statut);
                Resp r = switch (commande) {
                    case "demarrer" -> post("/" + i + "/demarrer", null, adminA());
                    case "terminer" -> post("/" + i + "/terminer", cloture(60), adminA());
                    case "annuler" -> post("/" + i + "/annuler", body("raison", "test"), adminA());
                    case "ajouter_ligne" -> post("/" + i + "/lignes-cout", ligne("PIECE", "Joint", 1, bd("10.00")), adminA());
                    case "supprimer_ligne_inconnue" -> delete("/" + i + "/lignes-cout/" + INCONNU, adminA());
                    default -> post("/" + i + "/rectifier", body("motif", "Erreur de saisie"), rectifieur());
                };
                e.put(commande, resume(r) + " | statut final " + statutIntervention(i));
            }
            m.set(statut, e);
        }
        goldenNode("transitions_matrice_statut_x_commande", m);
    }

    // ------------------------------------------------------------------ retard, échéance, chronologie

    @Test
    void l_echeance_et_le_retard_ne_sont_signales_que_pour_une_intervention_ouverte_matrice() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        String[][] echeances = {{"echeance_passee", "2026-03-11T08:00:00Z"}, {"echeance_future", "2099-01-01T00:00:00Z"},
                {"sans_echeance", null}};
        for (String statut : new String[]{"PLANIFIEE", "EN_COURS", "TERMINEE", "ANNULEE", "EN_ATTENTE_VALIDATION"}) {
            ObjectNode e = mapper.createObjectNode();
            for (String[] ech : echeances) {
                cleanup();
                setUp();
                UUID i = dansLeStatut(statut);
                jdbc.update("UPDATE gmao_interventions SET echeance = ? WHERE id = ?",
                        ech[1] == null ? null : OffsetDateTime.parse(ech[1]), i);
                var r = json(get("/" + i, adminA()));
                e.put(ech[0], "enRetard=" + r.get("enRetard").asBoolean() + " echeanceDepassee=" + r.get("echeanceDepassee").asBoolean());
            }
            m.set(statut, e);
        }
        goldenNode("retard_et_echeance_selon_le_statut", m);
    }

    @Test
    void la_chronologie_d_une_intervention_annulee_en_cours_trace_creation_demarrage_et_annulation() throws Exception {
        UUID i = demarree(null);
        assertEquals(200, post("/" + i + "/annuler", body("raison", "Pièce non livrée"), adminA()).status);
        Resp chrono = get("/" + i + "/chronologie", adminA());
        tools.jackson.databind.node.ArrayNode sans = mapper.createArrayNode();
        json(chrono).forEach(e -> {
            if (!"MODIFIEE".equals(e.path("type").asString())) sans.add(e);
        });
        golden("chronologie_intervention_annulee_en_cours", new Resp(chrono.status, mapper.writeValueAsString(sans)));
    }

    // ------------------------------------------------------------------ parcours

    @Test
    void parcours_complet_planifier_demarrer_terminer_valorise_le_temps_et_laisse_la_trace_sur_l_equipement() throws Exception {
        UUID i = planifier(GEN, "EN_MAINTENANCE", I_INTERNE);
        assertEquals(200, post("/" + i + "/demarrer", null, adminA()).status);
        Resp fin = post("/" + i + "/terminer", cloture("EN_SERVICE", DEBUT.plusMinutes(90)), adminA());
        golden("parcours_cloture_valorisee", fin);
        golden("parcours_relecture_apres_cloture", get("/" + i, adminA()));
        assertEquals(List.of("MAIN_OEUVRE|Main d'œuvre|1.500|35.00|true"), lignes(i));
    }

    @Test
    void parcours_planifier_puis_annuler_ne_touche_pas_a_l_equipement() throws Exception {
        UUID i = planifier(GEN, "HORS_SERVICE", I_INTERNE);
        assertEquals(200, post("/" + i + "/annuler", body("raison", "Remplacé par un contrat"), adminA()).status);
        golden("parcours_annulee_relecture", get("/" + i, adminA()));
        assertEquals("EN_SERVICE", statutEquipement(GEN));
        assertEquals(List.of(), historique(GEN));
        assertEquals(List.of(), alertes());
    }

    @Test
    void les_statistiques_gmao_comptent_les_interventions_par_statut_et_les_retards() throws Exception {
        jeuDeListe();
        Resp r = send(request(GET, "/api/v1/gmao/stats").with(adminA()));
        golden("statistiques_gmao_apres_jeu_de_donnees", r);
    }
}
