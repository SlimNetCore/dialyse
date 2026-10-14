package com.hemodialyse.backend.characterization.gmao.interventions;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Slice gmao-g4-interventions, thème « argent » : valorisation automatique du temps à la clôture (tarif et nature lus à
 * la clôture, KB-GMAO-10), lignes de coût manuelles, pas de double comptage, Σ des coûts lue par la fiche équipement.
 * La durée vient de {@code dateDebut}/{@code dateFin} fournies par le client : aucune horloge dans le calcul, donc
 * aucun seam n'est nécessaire pour verrouiller exactement les heures, l'arrondi et les montants.
 */
class InterventionArgentCharacterizationIntegrationTest extends InterventionCharacterizationSupport {

    /** Ne conserve que la partie « argent » de la réponse (lignes de coût, coût total). */
    private Resp cout(Resp reponse) {
        assertEquals(200, reponse.status, reponse.raw);
        ObjectNode o = mapper.createObjectNode();
        o.set("lignesCout", json(reponse).get("lignesCout"));
        o.set("coutTotal", json(reponse).get("coutTotal"));
        return new Resp(200, mapper.writeValueAsString(o));
    }

    private String resumeCout(Resp reponse) {
        JsonNode lignes = json(reponse).get("lignesCout");
        List<String> l = new java.util.ArrayList<>();
        lignes.forEach(x -> l.add(x.get("type").asString() + " " + x.get("quantite") + "x" + x.get("prixUnitaire") + "="
                + x.get("montant") + "; "));
        java.util.Collections.sort(l); // l'ordre de lecture des lignes n'est pas garanti
        return String.join("", l) + "total=" + json(reponse).get("coutTotal");
    }

    private Resp cloturerEn(UUID intervenant, OffsetDateTime fin) throws Exception {
        UUID i = demarree(intervenant);
        return post("/" + i + "/terminer", cloture("EN_SERVICE", fin), adminA());
    }

    // ------------------------------------------------------------------ valorisation automatique

    @Test
    void la_cloture_valorise_le_temps_d_un_intervenant_interne_en_main_d_oeuvre_au_tarif_horaire() throws Exception {
        Resp r = cout(cloturer(I_INTERNE, 90));
        golden("valorisation_interne_90_minutes_main_d_oeuvre", r);
        assertTrue(r.raw.contains("\"MAIN_OEUVRE\""), r.raw);
    }

    @Test
    void la_cloture_valorise_le_temps_d_un_prestataire_externe_en_honoraires() throws Exception {
        Resp r = cout(cloturer(I_EXTERNE, 150));
        golden("valorisation_externe_150_minutes_honoraires", r);
        assertTrue(r.raw.contains("\"INTERVENANT\""), r.raw);
    }

    @Test
    void la_cloture_arrondit_les_heures_a_deux_decimales_demi_superieur_matrice_des_durees() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        for (int minutes : new int[]{1, 2, 3, 5, 7, 10, 15, 20, 29, 30, 31, 45, 50, 55, 59, 60, 61, 89, 90, 100, 119, 120, 1440, 6000}) {
            Resp r = cloturer(I_INTERNE, minutes);
            assertEquals(200, r.status, r.raw);
            m.put(minutes + "_minutes", resumeCout(r));
        }
        goldenNode("valorisation_arrondi_des_heures_par_duree", m);
    }

    @Test
    void la_cloture_tronque_les_secondes_avant_de_convertir_en_heures() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        for (int secondes : new int[]{0, 1, 29, 59, 60, 61, 89, 90, 119, 120, 3599, 3600}) {
            Resp r = cloturerEn(I_INTERNE, DEBUT.plusSeconds(secondes));
            assertEquals(200, r.status, r.raw);
            m.put(secondes + "_secondes", resumeCout(r));
        }
        goldenNode("valorisation_troncature_des_secondes", m);
    }

    @Test
    void la_cloture_ne_valorise_rien_sans_tarif_ou_avec_un_tarif_nul_negatif_ou_sans_intervenant_valide() throws Exception {
        jdbc.update("INSERT INTO gmao_intervenants (id, centre_id, nom, type, tarif_horaire_defaut, actif) "
                + "VALUES (?, ?, 'Eve Tarif Negatif', 'INTERNE', -5.00, TRUE)", id(17), CENTRE_A);
        ObjectNode m = mapper.createObjectNode();
        m.put("sans_intervenant", resumeCout(cloturer(null, 60)));
        m.put("intervenant_sans_tarif", resumeCout(cloturer(I_SANS_TARIF, 60)));
        m.put("intervenant_tarif_zero", resumeCout(cloturer(I_TARIF_ZERO, 60)));
        m.put("intervenant_tarif_negatif_en_base", resumeCout(cloturer(id(17), 60)));
        m.put("intervenant_d_un_autre_centre", resumeCout(cloturer(I_AUTRE_CENTRE, 60)));
        m.put("intervenant_desactive_valorise", resumeCout(cloturer(I_DESACTIVE, 60)));
        m.put("intervenant_supprime_de_la_base", resumeCout(cloturerApresSuppression(60)));
        goldenNode("valorisation_cas_sans_ligne_et_intervenant_desactive", m);
    }

    private Resp cloturerApresSuppression(long minutes) throws Exception {
        UUID i = demarree(I_INTERNE);
        jdbc.update("DELETE FROM gmao_intervenants WHERE id = ?", I_INTERNE);
        return post("/" + i + "/terminer", cloture(minutes), adminA());
    }

    @Test
    void la_cloture_utilise_le_tarif_et_la_nature_courants_de_l_intervenant_sans_snapshot_kb_gmao_10() throws Exception {
        // tarif révisé entre la planification et la clôture : c'est le tarif de la clôture qui compte
        UUID i = demarree(I_INTERNE);
        jdbc.update("UPDATE gmao_intervenants SET tarif_horaire_defaut = 40.10 WHERE id = ?", I_INTERNE);
        Resp r = post("/" + i + "/terminer", cloture(60), adminA());
        golden("valorisation_tarif_revise_avant_la_cloture", cout(r));
        // nature changée entre la planification et la clôture : honoraires au lieu de main d'oeuvre
        UUID j = demarree(I_INTERNE);
        jdbc.update("UPDATE gmao_intervenants SET type = 'EXTERNE' WHERE id = ?", I_INTERNE);
        golden("valorisation_nature_changee_avant_la_cloture", cout(post("/" + j + "/terminer", cloture(60), adminA())));
    }

    @Test
    void la_cloture_ne_double_compte_pas_une_ligne_de_meme_nature_matrice() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        String[][] cas = {
                {"interne_avec_main_d_oeuvre_manuelle", "INTERNE", "MAIN_OEUVRE"},
                {"interne_avec_ligne_intervenant_manuelle", "INTERNE", "INTERVENANT"},
                {"interne_avec_piece_manuelle", "INTERNE", "PIECE"},
                {"interne_avec_autre_manuelle", "INTERNE", "AUTRE"},
                {"externe_avec_ligne_intervenant_manuelle", "EXTERNE", "INTERVENANT"},
                {"externe_avec_piece_manuelle", "EXTERNE", "PIECE"},
                {"externe_avec_autre_manuelle", "EXTERNE", "AUTRE"}};
        for (String[] c : cas) {
            cleanup();
            setUp();
            UUID intervenant = c[1].equals("INTERNE") ? I_INTERNE : I_EXTERNE;
            UUID i = demarree(intervenant);
            Resp ajout = post("/" + i + "/lignes-cout", ligne(c[2], "Saisie manuelle", 1, bd("10.00")), adminA());
            assertEquals(200, ajout.status, ajout.raw);
            Resp fin = post("/" + i + "/terminer", cloture(60), adminA());
            m.put(c[0], String.join(" ; ", lignes(i)) + " || " + resumeCout(fin));
        }
        goldenNode("valorisation_pas_de_double_comptage", m);
    }

    // ------------------------------------------------------------------ lignes de coût manuelles

    @Test
    void ajouter_une_ligne_de_cout_cumule_les_montants_et_renvoie_le_total() throws Exception {
        UUID i = planifier(null);
        Resp a = post("/" + i + "/lignes-cout", body("type", "PIECE", "libelle", "Joint torique", "quantite", bd("2"),
                "prixUnitaire", bd("12.50"), "articleStockId", id(70)), adminA());
        golden("ajouter_ligne_piece_avec_article_stock", a);
        Resp b = post("/" + i + "/lignes-cout", ligne("AUTRE", "Transport", bd("1.5"), bd("0.01")), adminA());
        golden("ajouter_deuxieme_ligne_cumul", cout(b));
        assertEquals(2, lignes(i).size());
        golden("lire_apres_deux_lignes", get("/" + i, adminA()));
    }

    @Test
    void ajouter_une_ligne_valide_le_corps_matrice() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        UUID i = demarree(null);
        String l = "x".repeat(255);
        Object[][] cas = {
                {"type_absent", body("libelle", "L", "quantite", 1, "prixUnitaire", bd("1"))},
                {"type_inconnu", ligne("DIVERS", "L", 1, bd("1"))},
                {"type_minuscules", ligne("piece", "L", 1, bd("1"))},
                {"type_PIECE", ligne("PIECE", "L", 1, bd("1"))},
                {"type_MAIN_OEUVRE", ligne("MAIN_OEUVRE", "L", 1, bd("1"))},
                {"type_INTERVENANT", ligne("INTERVENANT", "L", 1, bd("1"))},
                {"type_AUTRE", ligne("AUTRE", "L", 1, bd("1"))},
                {"libelle_absent", body("type", "PIECE", "quantite", 1, "prixUnitaire", bd("1"))},
                {"libelle_blanc", ligne("PIECE", "   ", 1, bd("1"))},
                {"libelle_255", ligne("PIECE", l, 1, bd("1"))},
                {"libelle_256", ligne("PIECE", l + "y", 1, bd("1"))},
                {"quantite_absente", body("type", "PIECE", "libelle", "L", "prixUnitaire", bd("1"))},
                {"quantite_zero", ligne("PIECE", "L", 0, bd("1"))},
                {"quantite_negative", ligne("PIECE", "L", -1, bd("1"))},
                {"quantite_0_001", ligne("PIECE", "L", bd("0.001"), bd("1"))},
                {"quantite_0_0005", ligne("PIECE", "L", bd("0.0005"), bd("1"))},
                {"quantite_1_2345", ligne("PIECE", "L", bd("1.2345"), bd("1"))},
                {"quantite_999999999", ligne("PIECE", "L", bd("999999999"), bd("1"))},
                {"quantite_9999999999", ligne("PIECE", "L", bd("9999999999"), bd("1"))},
                {"prix_absent", body("type", "PIECE", "libelle", "L", "quantite", 1)},
                {"prix_negatif", ligne("PIECE", "L", 1, bd("-0.01"))},
                {"prix_zero", ligne("PIECE", "L", 1, bd("0"))},
                {"prix_12_345", ligne("PIECE", "L", 1, bd("12.345"))},
                {"prix_12_344", ligne("PIECE", "L", 1, bd("12.344"))},
                {"prix_9999999999_99", ligne("PIECE", "L", 1, bd("9999999999.99"))},
                {"prix_100000000000", ligne("PIECE", "L", 1, bd("100000000000"))},
                {"article_stock_non_uuid", modifie(ligne("PIECE", "L", 1, bd("1")), "articleStockId", "pas-un-uuid")},
                {"article_stock_inexistant", modifie(ligne("PIECE", "L", 1, bd("1")), "articleStockId", id(71))},
                {"corps_objet_vide", "{}"},
                {"corps_illisible", "{pas du json"},
                {"corps_absent", null}};
        for (Object[] c : cas) {
            m.put((String) c[0], resume(post("/" + i + "/lignes-cout", (String) c[1], adminA())));
        }
        goldenNode("ajouter_ligne_matrice_de_validation", m);
        goldenNode("ajouter_ligne_matrice_lignes_en_base", mapper.valueToTree(lignes(i)));
    }

    @Test
    void ajouter_une_ligne_decimale_puis_relire_montre_l_ecart_entre_la_reponse_et_la_base() throws Exception {
        UUID i = planifier(null);
        Resp r = post("/" + i + "/lignes-cout", ligne("PIECE", "Capteur", bd("3.333"), bd("10.105")), adminA());
        golden("ajouter_ligne_decimale_reponse", r);
        golden("ajouter_ligne_decimale_relecture", get("/" + i, adminA()));
        assertEquals(1, lignes(i).size());
    }

    @Test
    void la_main_d_oeuvre_manuelle_est_refusee_pour_un_intervenant_externe_du_centre_seulement() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        for (UUID intervenant : new UUID[]{I_EXTERNE, I_INTERNE, I_DESACTIVE, I_AUTRE_CENTRE, I_INCONNU, null}) {
            cleanup();
            setUp();
            UUID i = planifier(GEN, "EN_SERVICE", intervenant == I_INCONNU ? null : intervenant);
            if (intervenant == I_INCONNU) {
                jdbc.update("UPDATE gmao_interventions SET intervenant_id = ? WHERE id = ?", I_INCONNU, i);
            }
            Resp r = post("/" + i + "/lignes-cout", ligne("MAIN_OEUVRE", "Main d'œuvre", 2, bd("30.00")), adminA());
            m.put(intervenant == null ? "sans_intervenant" : new Scrubber(r).scrubString(intervenant.toString()), resume(r));
        }
        goldenNode("main_d_oeuvre_manuelle_selon_l_intervenant", m);
    }

    @Test
    void ajouter_une_ligne_est_possible_sur_une_intervention_terminee_sans_nouvelle_valorisation() throws Exception {
        UUID i = terminee(I_INTERNE, 60);
        Resp r = post("/" + i + "/lignes-cout", ligne("PIECE", "Oubli de saisie", 1, bd("20.00")), adminA());
        golden("ajouter_ligne_apres_cloture", cout(r));
        assertEquals(2, lignes(i).size());
    }

    @Test
    void supprimer_une_ligne_de_cout_selon_l_etat_de_l_intervention_et_de_la_ligne() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        UUID i = demarree(I_INTERNE);
        Resp ajout = post("/" + i + "/lignes-cout", ligne("PIECE", "Joint", 2, bd("5.00")), adminA());
        UUID ligneId = UUID.fromString(json(ajout).get("lignesCout").get(0).get("id").asString());
        UUID autre = planifier(null);
        Resp ligneAutre = post("/" + autre + "/lignes-cout", ligne("PIECE", "Autre", 1, bd("1.00")), adminA());
        UUID idAutre = UUID.fromString(json(ligneAutre).get("lignesCout").get(0).get("id").asString());

        m.put("ligne_d_une_autre_intervention", resume(delete("/" + i + "/lignes-cout/" + idAutre, adminA())));
        m.put("ligne_inconnue", resume(delete("/" + i + "/lignes-cout/" + INCONNU, adminA())));
        m.put("identifiant_de_ligne_invalide", resume(delete("/" + i + "/lignes-cout/pas-un-uuid", adminA())));
        m.put("intervention_inconnue", resume(delete("/" + INCONNU + "/lignes-cout/" + ligneId, adminA())));
        Resp ok = delete("/" + i + "/lignes-cout/" + ligneId, adminA());
        m.put("ligne_supprimee_en_cours", resume(ok) + " | " + resumeCout(ok));
        m.put("lignes_restantes_en_base", String.join(" ; ", lignes(i)));
        m.put("seconde_suppression_de_la_meme_ligne", resume(delete("/" + i + "/lignes-cout/" + ligneId, adminA())));
        goldenNode("supprimer_ligne_matrice", m);
    }

    @Test
    void supprimer_une_ligne_est_refuse_sur_une_intervention_terminee_ou_annulee_et_permis_sur_la_ligne_automatique_d_une_rouverte() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        UUID terminee = terminee(I_INTERNE, 60);
        UUID auto = UUID.fromString(json(get("/" + terminee, adminA())).get("lignesCout").get(0).get("id").asString());
        m.put("ligne_automatique_d_une_terminee", resume(delete("/" + terminee + "/lignes-cout/" + auto, adminA())));
        UUID annulee = planifier(null);
        Resp ajout = post("/" + annulee + "/lignes-cout", ligne("PIECE", "Joint", 1, bd("5.00")), adminA());
        UUID idAnnulee = UUID.fromString(json(ajout).get("lignesCout").get(0).get("id").asString());
        assertEquals(200, post("/" + annulee + "/annuler", body("raison", "x"), adminA()).status);
        m.put("ligne_d_une_annulee", resume(delete("/" + annulee + "/lignes-cout/" + idAnnulee, adminA())));
        UUID rouverte = terminee(I_INTERNE, 60);
        assertEquals(200, post("/" + rouverte + "/rectifier", body("motif", "Erreur"), rectifieur()).status);
        m.put("lignes_automatiques_apres_rectification", String.join(" ; ", lignes(rouverte)));
        goldenNode("supprimer_ligne_selon_l_etat", m);
    }

    // ------------------------------------------------------------------ rectification

    @Test
    void rectifier_rouvre_la_cloture_retire_les_lignes_automatiques_garde_les_manuelles_et_trace_la_cloture_annulee() throws Exception {
        UUID i = demarree(I_INTERNE);
        assertEquals(200, post("/" + i + "/lignes-cout", ligne("PIECE", "Joint", 2, bd("5.00")), adminA()).status);
        assertEquals(200, post("/" + i + "/terminer", cloture(90), adminA()).status);
        assertEquals(2, lignes(i).size());

        Resp r = post("/" + i + "/rectifier", body("motif", "  Heure de fin erronée  "), rectifieur());
        golden("rectifier_nominal_200", r);
        assertEquals("EN_COURS", statutIntervention(i));
        assertEquals(List.of("PIECE|Joint|2.000|5.00|false"), lignes(i));
        assertNull(row(i).get("DATE_FIN"));
        assertNull(row(i).get("CLOTURE_LE"));
        assertNull(row(i).get("CLOTURE_PAR"));
        assertNull(row(i).get("ETAT_EQUIPEMENT_APRES"));
        List<java.util.Map<String, Object>> trace = jdbc.queryForList(
                "SELECT motif, par, cloture_anterieure_le, cloture_anterieure_par FROM gmao_rectifications_intervention "
                        + "WHERE intervention_id = ?", i);
        assertEquals(1, trace.size());
        assertEquals("Heure de fin erronée", trace.get(0).get("MOTIF"));
        assertEquals(RECTIF, UUID.fromString(String.valueOf(trace.get(0).get("PAR"))));
        assertEquals(ADMIN_A, UUID.fromString(String.valueOf(trace.get(0).get("CLOTURE_ANTERIEURE_PAR"))));
        assertTrue(trace.get(0).get("CLOTURE_ANTERIEURE_LE") != null);
        // l'équipement garde l'état posé par la première clôture : la rectification ne le touche pas
        assertEquals("EN_SERVICE", statutEquipement(GEN));
    }

    @Test
    void rectifier_puis_terminer_de_nouveau_recalcule_la_valorisation_sur_les_nouvelles_dates_et_au_tarif_courant() throws Exception {
        UUID i = terminee(I_INTERNE, 90);
        jdbc.update("UPDATE gmao_intervenants SET tarif_horaire_defaut = 50.00 WHERE id = ?", I_INTERNE);
        OffsetDateTime nouveauDebut = DEBUT.plusHours(1);
        Resp r = post("/" + i + "/rectifier", body("motif", "Début mal saisi", "dateDebut", nouveauDebut), rectifieur());
        assertEquals(200, r.status, r.raw);
        Resp fin = post("/" + i + "/terminer", cloture("EN_SERVICE", nouveauDebut.plusMinutes(20)), adminA());
        golden("rectifier_puis_terminer_valorisation_recalculee", cout(fin));
        golden("rectifier_puis_terminer_relecture", get("/" + i, adminA()));
    }

    @Test
    void rectifier_avec_une_nouvelle_date_de_debut_posterieure_a_l_ancienne_fin_empeche_de_terminer_a_l_ancienne_fin() throws Exception {
        UUID i = terminee(I_INTERNE, 30);
        assertEquals(200, post("/" + i + "/rectifier", body("motif", "Décalage", "dateDebut", DEBUT.plusHours(5)), rectifieur()).status);
        golden("terminer_apres_rectification_date_fin_avant_le_nouveau_debut", post("/" + i + "/terminer", cloture(30), adminA()));
        assertEquals("EN_COURS", statutIntervention(i));
    }

    @Test
    void rectifier_deux_fois_cumule_les_traces_et_la_chronologie_donne_le_numero_de_cloture() throws Exception {
        UUID i = terminee(I_INTERNE, 60);
        assertEquals(200, post("/" + i + "/rectifier", body("motif", "Première correction"), rectifieur()).status);
        assertEquals(200, post("/" + i + "/terminer", cloture(75), adminA()).status);
        Resp seconde = post("/" + i + "/rectifier", body("motif", "Seconde correction"), rectifieur());
        golden("rectifier_deuxieme_fois_200", seconde);
        assertEquals(2, json(seconde).get("nbRectifications").asInt());
        assertEquals(200, post("/" + i + "/terminer", cloture(100), adminA()).status);
        Resp relecture = get("/" + i, adminA());
        golden("rectifier_deux_fois_relecture_finale", relecture);
        assertEquals(2, json(relecture).get("nbRectifications").asInt());
        assertEquals(List.of("MAIN_OEUVRE|Main d'œuvre|1.670|35.00|true"), lignes(i));
        // lecture indispensable : la chronologie porte la trace (numéro de clôture = rectifications + 1)
        // L'évènement « MODIFIEE » final (la valorisation automatique rejoue now() après la clôture) dépend de la
        // résolution de l'horloge : il est écarté du golden (voir KB-GMAO-50).
        Resp chrono = get("/" + i + "/chronologie", adminA());
        tools.jackson.databind.node.ArrayNode sans = mapper.createArrayNode();
        json(chrono).forEach(e -> {
            if (!"MODIFIEE".equals(e.path("type").asString())) sans.add(e);
        });
        golden("rectifier_deux_fois_chronologie", new Resp(chrono.status, mapper.writeValueAsString(sans)));
    }

    @Test
    void rectifier_avec_une_main_d_oeuvre_manuelle_ajoutee_apres_la_rectification_n_ajoute_pas_de_ligne_automatique() throws Exception {
        UUID i = terminee(I_INTERNE, 60);
        assertEquals(200, post("/" + i + "/rectifier", body("motif", "Erreur"), rectifieur()).status);
        assertEquals(200, post("/" + i + "/lignes-cout", ligne("MAIN_OEUVRE", "Main d'œuvre saisie", 3, bd("30.00")), adminA()).status);
        assertEquals(200, post("/" + i + "/terminer", cloture(60), adminA()).status);
        assertEquals(List.of("MAIN_OEUVRE|Main d'œuvre saisie|3.000|30.00|false"), lignes(i));
    }

    @Test
    void rectifier_puis_terminer_avec_un_autre_etat_apres_met_a_jour_l_equipement_et_le_journal() throws Exception {
        UUID i = terminee(null, 60);
        assertEquals(200, post("/" + i + "/rectifier", body("motif", "Mauvais état constaté"), rectifieur()).status);
        assertEquals(200, post("/" + i + "/terminer", cloture("HORS_SERVICE", DEBUT.plusMinutes(60)), adminA()).status);
        assertEquals("HORS_SERVICE", statutEquipement(GEN));
        assertEquals(List.of("EN_SERVICE>HORS_SERVICE|Intervention terminée"), historique(GEN));
        assertEquals(List.of("G-01/HORS_SERVICE/0//roles,ADMIN,SECRETAIRE,"), alertes());
    }

    @Test
    void rectifier_valide_le_corps_et_le_statut() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        UUID i = terminee(null, 60);
        m.put("motif_absent", resume(post("/" + i + "/rectifier", "{}", rectifieur())));
        m.put("motif_blanc", resume(post("/" + i + "/rectifier", body("motif", "   "), rectifieur())));
        m.put("date_debut_illisible", resume(post("/" + i + "/rectifier", body("motif", "x", "dateDebut", "hier"), rectifieur())));
        m.put("corps_absent", resume(post("/" + i + "/rectifier", null, rectifieur())));
        m.put("statut_apres_refus", statutIntervention(i));
        UUID enCours = demarree(null);
        m.put("intervention_en_cours", resume(post("/" + enCours + "/rectifier", body("motif", "x"), rectifieur())));
        UUID planifiee = planifier(null);
        m.put("intervention_planifiee", resume(post("/" + planifiee + "/rectifier", body("motif", "x"), rectifieur())));
        m.put("intervention_inconnue", resume(post("/" + INCONNU + "/rectifier", body("motif", "x"), rectifieur())));
        goldenNode("rectifier_matrice_de_validation", m);
    }

    @Test
    void annuler_une_intervention_rectifiee_est_permis_et_conserve_les_lignes_manuelles() throws Exception {
        UUID i = terminee(I_INTERNE, 60);
        assertEquals(200, post("/" + i + "/lignes-cout", ligne("PIECE", "Joint", 1, bd("5.00")), adminA()).status);
        assertEquals(200, post("/" + i + "/rectifier", body("motif", "Erreur"), rectifieur()).status);
        Resp r = post("/" + i + "/annuler", body("raison", "Intervention fictive"), adminA());
        golden("annuler_apres_rectification", r);
        assertEquals(List.of("PIECE|Joint|1.000|5.00|false"), lignes(i));
    }

    // ------------------------------------------------------------------ lecture des coûts après clôture (consommateur)

    @Test
    void la_fiche_de_l_equipement_somme_les_couts_des_interventions_en_cours_et_terminees_seulement() throws Exception {
        OffsetDateTime recent = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30).truncatedTo(ChronoUnit.SECONDS);
        String corps = modifie(creation(GEN, "EN_SERVICE", null), "dateDebut", recent.toString());
        UUID planifiee = creerAvec(corps);
        UUID enCours = creerAvec(corps);
        UUID terminee = creerAvec(corps);
        UUID annulee = creerAvec(corps);
        for (UUID i : new UUID[]{planifiee, enCours, terminee, annulee}) {
            assertEquals(200, post("/" + i + "/lignes-cout", ligne("PIECE", "Pièce " + i, 1, bd(i == planifiee ? "10.00"
                    : i == enCours ? "20.00" : i == terminee ? "30.00" : "40.00")), adminA()).status);
        }
        assertEquals(200, post("/" + enCours + "/demarrer", null, adminA()).status);
        assertEquals(200, post("/" + terminee + "/demarrer", null, adminA()).status);
        assertEquals(200, post("/" + terminee + "/terminer", body("actions", "ok", "etatEquipementApres", "EN_SERVICE",
                "dateFin", recent.plusMinutes(10)), adminA()).status);
        assertEquals(200, post("/" + annulee + "/annuler", body("raison", "x"), adminA()).status);

        Resp fiche = send(request(org.springframework.http.HttpMethod.GET, "/api/v1/gmao/equipements/" + GEN + "/fiche").with(adminA()));
        assertEquals(200, fiche.status, fiche.raw);
        ObjectNode obs = mapper.createObjectNode();
        obs.put("nbInterventions", json(fiche).get("nbInterventions").asInt());
        obs.set("coutMaintenancePeriode", json(fiche).get("coutMaintenancePeriode"));
        obs.set("coutMaintenanceCumule", json(fiche).get("coutMaintenanceCumule"));
        goldenNode("fiche_equipement_couts_apres_cloture", obs);
        assertEquals(0, new BigDecimal("50").compareTo(json(fiche).get("coutMaintenancePeriode").decimalValue()),
                "période : en cours (20) + terminée (30) ; planifiée et annulée exclues");
    }

    private UUID creerAvec(String corps) throws Exception {
        Resp r = post("", corps, adminA());
        assertEquals(201, r.status, r.raw);
        return UUID.fromString(json(r).get("id").asString());
    }
}
