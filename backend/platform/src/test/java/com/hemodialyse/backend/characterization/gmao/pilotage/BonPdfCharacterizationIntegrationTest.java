package com.hemodialyse.backend.characterization.gmao.pilotage;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filet de caractérisation du bon d'intervention (GET /api/v1/gmao/interventions/{id}/bon) : PDF du modèle de
 * document du centre. Le contenu est observé par le texte extrait du PDF (stable : seul l'horodatage d'édition est
 * masqué), les en-têtes HTTP et le nombre de pages.
 */
class BonPdfCharacterizationIntegrationTest extends PilotageCharacterizationSupport {

    private static final List<String> LIGNES_CLES = List.of("N° BI", "Début", "Durée", "Intervenant :", "État avant",
            "Panne constatée", "Cause trouvée", "Travaux réalisés", "Pièce remplacée", "COÛT TOTAL", "Créée", "Démarrée",
            "Terminée", "Annulée", "Rectifiée", "Dernière modification", "fuseau", "Équipement :", "N° de série",
            "Établi par", "Clôturé par");

    private String bon(UUID intervention) {
        return GMAO + "/interventions/" + intervention + "/bon";
    }

    private UUID generateur(String label) {
        return equipement(label, CENTRE_A, e -> {
            e.designation = "Générateur " + label;
            e.fabricant = "Fresenius";
            e.modele = "5008S";
            e.serie = "SN-123";
        });
    }

    /** Intervention terminée complète (4 types de ligne de coût) : le cas nominal du bon. */
    private UUID interventionComplete(String label, UUID equipement) {
        UUID i = intervention(label, equipement, CENTRE_A, x -> {
            x.intervenant = INTERVENANT_INTERNE;
            x.symptome = "Alarme conductivité";
            x.cause = "Cellule usée";
            x.actions = "Cellule remplacée";
            x.pieceRemplacee = "Cellule de conductivité";
            x.priorite = "URGENTE";
            x.echeance = T0.plusHours(6);
            x.etatAvant = "HORS_SERVICE";
            x.etatApres = "EN_SERVICE";
            x.demarrePar = TECH;
            x.demarreLe = T0.plusMinutes(10);
            x.cloturePar = TECH;
            x.clotureLe = T0.plusHours(2).plusMinutes(5);
        });
        ligne(i, "PIECE", "Cellule de conductivité", "2", "12500.00");
        ligne(i, "MAIN_OEUVRE", "Main d'oeuvre", "1.5", "3000.00");
        ligne(i, "INTERVENANT", "Temps intervenant", "2.25", "2000.00");
        ligne(i, "AUTRE", "Transport", "1", "500.50");
        return i;
    }

    private List<String> lignesCles(String texte) {
        List<String> out = new ArrayList<>();
        for (String l : texte.lines().toList()) {
            if (LIGNES_CLES.stream().anyMatch(l::contains)) out.add(l);
        }
        return out;
    }

    @Test
    void le_bon_d_une_intervention_terminee_est_un_pdf_en_ligne_nomme_par_son_numero() throws Exception {
        UUID i = interventionComplete("I_BON", generateur("G01"));

        Resp r = get(bon(i), adminA());

        assertEquals(200, r.status);
        assertEquals("application/pdf", r.header("Content-Type"));
        String id8 = i.toString().substring(0, 8);
        assertEquals("inline; filename=\"bon-intervention-" + id8 + ".pdf\"; filename*=UTF-8''bon-intervention-" + id8 + ".pdf",
                r.header("Content-Disposition"));
        assertEquals("%PDF-", new String(r.bytes, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1));
        assertEquals(2, pagesDuPdf(r.bytes), "KB-GMAO-69 : le bon tient sur 2 pages, la seconde ne porte que l'en-tête et le pied");
        assertTrue(r.bytes.length > 2000);
        goldenTexte("bon_nominal_texte", textePdf(r));
    }

    @Test
    void le_bon_ne_compte_que_les_lignes_de_cout_de_l_intervention_et_arrondit_les_montants_au_centime_HALF_UP() throws Exception {
        UUID g = generateur("G01");
        UUID autre = intervention("I_AUTRE", g, CENTRE_A, x -> x.actions = "Autre");
        ligne(autre, "AUTRE", "Hors bon", "1", "99999.00");
        List<String> lignes = new ArrayList<>();
        String[][] cas = {
                {"une ligne de 1,005 (0,335 x 3,00)", "0.335", "3.00", null, null},
                {"une ligne de 0,001", "0.001", "1.00", null, null},
                {"deux lignes de 1,004 (somme 2,008)", "0.502", "2.00", "0.502", "2.00"},
                {"deux lignes de 0,005 (somme 0,010)", "0.005", "1.00", "0.005", "1.00"},
                {"une ligne de 12 345 678,90 (grand montant)", "1", "9999999.99", null, null},
        };
        int n = 0;
        for (String[] c : cas) {
            UUID i = intervention("I_ARRONDI_" + (n++), g, CENTRE_A, x -> x.actions = "Remplacement");
            ligne(i, "PIECE", "Ligne 1", c[1], c[2]);
            if (c[3] != null) ligne(i, "PIECE", "Ligne 2", c[3], c[4]);
            String texte = espacesSimples(textePdf(get(bon(i), adminA())));
            assertFalse(texte.contains("99 999,00"), "les lignes d'une autre intervention ne comptent pas");
            lignes.add(c[0] + " -> " + texte.lines().filter(l -> l.startsWith("Pièces de rechange") || l.startsWith("COÛT TOTAL"))
                    .toList());
        }
        goldenLignes("bon_arrondis_des_couts", lignes);
    }

    @Test
    void le_bon_sans_ligne_de_cout_affiche_des_totaux_a_zero() throws Exception {
        UUID i = intervention("I_SANS_COUT", generateur("G01"), CENTRE_A, x -> x.actions = "Visite");

        String texte = espacesSimples(textePdf(get(bon(i), adminA())));

        assertTrue(texte.contains("COÛT TOTAL : 0,00 DA"), texte);
        goldenTexte("bon_sans_ligne_de_cout_texte", textePdf(get(bon(i), adminA())));
    }

    @Test
    void le_fuseau_demande_decale_les_dates_et_un_fuseau_invalide_ou_absent_retombe_sur_UTC() throws Exception {
        UUID i = interventionComplete("I_FUSEAU", generateur("G01"));
        Map<String, String[]> variantes = new LinkedHashMap<>();
        variantes.put("(absent)", null);
        variantes.put("(vide)", new String[]{""});
        variantes.put("(espaces)", new String[]{"  "});
        variantes.put("Africa/Algiers", new String[]{"Africa/Algiers"});
        variantes.put("(Africa/Algiers entouré d'espaces)", new String[]{" Africa/Algiers "});
        variantes.put("Europe/Paris", new String[]{"Europe/Paris"});
        variantes.put("UTC", new String[]{"UTC"});
        variantes.put("Z", new String[]{"Z"});
        variantes.put("+01:00", new String[]{"+01:00"});
        variantes.put("America/New_York", new String[]{"America/New_York"});
        variantes.put("Mars/Olympus (inconnu)", new String[]{"Mars/Olympus"});
        variantes.put("africa/algiers (casse)", new String[]{"africa/algiers"});
        variantes.put("(deux paramètres tz)", new String[]{"Africa/Algiers", "Europe/Paris"});

        List<String> lignes = new ArrayList<>();
        for (var e : variantes.entrySet()) {
            Resp r = e.getValue() == null ? get(bon(i), adminA()) : getParam(bon(i), adminA(), "tz", e.getValue());
            if (r.status != 200) {
                lignes.add(e.getKey() + " -> " + resume(r));
                continue;
            }
            List<String> cles = lignesCles(textePdf(r)).stream()
                    .filter(l -> l.startsWith("Début") || l.contains("Démarrée") || l.contains("fuseau")).toList();
            lignes.add(e.getKey() + " -> " + String.join(" | ", cles));
        }
        goldenLignes("bon_fuseaux_horaires", lignes);
    }

    @Test
    void le_bon_selon_le_statut_de_l_intervention() throws Exception {
        UUID g = generateur("G01");
        Map<String, UUID> cas = new LinkedHashMap<>();
        cas.put("PLANIFIEE (sans fin)", intervention("I_PLAN", g, CENTRE_A, x -> {
            x.statut = "PLANIFIEE";
            x.fin = null;
        }));
        cas.put("EN_COURS", intervention("I_COURS", g, CENTRE_A, x -> {
            x.statut = "EN_COURS";
            x.fin = null;
            x.demarrePar = TECH;
            x.demarreLe = T0;
        }));
        cas.put("ANNULEE", intervention("I_ANN", g, CENTRE_A, x -> {
            x.statut = "ANNULEE";
            x.fin = null;
            x.annulePar = ADMIN_A;
            x.annuleLe = T0.plusHours(3);
            x.modification = T0.plusHours(3);
            x.modifiePar = ADMIN_A;
        }));
        cas.put("EN_ATTENTE_VALIDATION", intervention("I_ATT", g, CENTRE_A, x -> x.statut = "EN_ATTENTE_VALIDATION"));
        cas.put("TERMINEE (préventive, priorité haute)", intervention("I_PREV", g, CENTRE_A, x -> {
            x.type = "PREVENTIVE";
            x.priorite = "HAUTE";
        }));
        cas.put("TERMINEE (priorité absente)", intervention("I_SANS_PRIO", g, CENTRE_A, x -> x.priorite = null));

        List<String> lignes = new ArrayList<>();
        for (var e : cas.entrySet()) {
            Resp r = get(bon(e.getValue()), adminA());
            lignes.add(e.getKey() + " -> " + (r.status == 200 ? String.join(" | ", lignesCles(textePdf(r))) : resume(r)));
        }
        goldenLignes("bon_selon_le_statut", lignes);
    }

    @Test
    void le_bon_tolere_les_donnees_manquantes_et_ignore_un_intervenant_d_un_autre_centre() throws Exception {
        UUID nu = equipement("G_NU", CENTRE_A, e -> e.designation = "Equipement nu");
        UUID i1 = intervention("I_NU", nu, CENTRE_A, x -> x.intervenant = INTERVENANT_AUTRE_CENTRE);
        UUID i2 = intervention("I_INTERVENANT_INCONNU", nu, CENTRE_A, x -> x.intervenant = INCONNU);

        goldenTexte("bon_donnees_manquantes_texte", textePdf(get(bon(i1), adminA())));
        assertEquals(
                lignesCles(textePdf(get(bon(i1), adminA()))).stream().filter(l -> l.startsWith("Intervenant")).toList(),
                lignesCles(textePdf(get(bon(i2), adminA()))).stream().filter(l -> l.startsWith("Intervenant")).toList(),
                "un intervenant d'un autre centre ou inconnu est omis sans erreur");
    }

    @Test
    void le_bon_formate_la_duree_le_type_la_marque_de_l_equipement_et_ignore_les_champs_blancs() throws Exception {
        List<String> lignes = new ArrayList<>();
        UUID g = generateur("G01");
        int n = 0;
        for (long minutes : new long[]{0, 1, 45, 59, 60, 65, 600, 1440, 1500}) {
            long m = minutes;
            UUID i = intervention("I_DUREE_" + (n++), g, CENTRE_A, x -> x.fin = x.debut.plusMinutes(m));
            String ligne = textePdf(get(bon(i), adminA())).lines().filter(l -> l.startsWith("Durée")).findFirst().orElse("(aucune ligne)");
            lignes.add("durée de " + minutes + " min -> " + ligne);
        }
        n = 0;
        for (String type : List.of("PREVENTIVE", "CURATIVE", "URGENTE", "CONTROLE", "INSTALLATION", "DEINSTALLATION", "REMPLACEMENT_PIECE", "REVISION_COMPLETE")) {
            UUID i = intervention("I_TYPE_" + (n++), g, CENTRE_A, x -> x.type = type);
            lignes.add("type " + type + " -> " + textePdf(get(bon(i), adminA())).lines().filter(l -> l.startsWith("N° BI")).findFirst().orElse("?").replaceAll("BI-[0-9A-F]{8}", "BI-<ID8>"));
        }
        String[][] marques = {{"Fresenius", "5008S"}, {"Fresenius", null}, {null, "5008S"}, {"  ", "  "}, {null, null}};
        n = 0;
        for (String[] mq : marques) {
            UUID e = equipement("E_MARQUE_" + n, CENTRE_A, x -> {
                x.designation = "Désignation";
                x.fabricant = mq[0];
                x.modele = mq[1];
                x.serie = mq[0] == null ? "   " : "SN";
            });
            UUID i = intervention("I_MARQUE_" + (n++), e, CENTRE_A, x -> {
            });
            String texte = textePdf(get(bon(i), adminA()));
            lignes.add("fabricant=" + mq[0] + " modèle=" + mq[1] + " -> " + texte.lines().filter(l -> l.startsWith("Équipement :") || l.startsWith("N° de série")).toList());
        }
        UUID externe = nom("INTERVENANT_EXTERNE");
        intervenant(externe, CENTRE_A, "Acme Dialyse Services", "EXTERNE", null);
        UUID iExterne = intervention("I_EXTERNE", g, CENTRE_A, x -> x.intervenant = externe);
        lignes.add("intervenant externe -> " + lignesCles(textePdf(get(bon(iExterne), adminA()))).stream()
                .filter(l -> l.startsWith("Intervenant")).toList());
        UUID blanc = intervention("I_CHAMPS_BLANCS", g, CENTRE_A, x -> {
            x.symptome = "   ";
            x.cause = "";
            x.actions = "  ";
            x.pieceRemplacee = " ";
        });
        lignes.add("champs blancs -> " + lignesCles(textePdf(get(bon(blanc), adminA()))).stream()
                .filter(l -> l.startsWith("Panne") || l.startsWith("Cause") || l.startsWith("Travaux") || l.startsWith("Pièce remplacée")).toList());
        goldenLignes("bon_formats_de_duree_type_marque_et_champs_blancs", lignes);
    }

    @Test
    void la_chronologie_du_bon_porte_les_rectifications_et_la_derniere_modification() throws Exception {
        UUID g = generateur("G01");
        UUID i = intervention("I_RECT", g, CENTRE_A, x -> {
            x.statut = "EN_COURS";
            x.fin = null;
            x.demarrePar = TECH;
            x.demarreLe = T0;
            x.modification = T0.plusDays(2);
            x.modifiePar = ADMIN_A;
        });
        rectification(i, "Date de début erronée", ADMIN_A, T0.plusDays(1), T0.plusHours(2), TECH);

        goldenTexte("bon_chronologie_rectifiee_texte", textePdf(get(bon(i), adminA())));
    }

    @Test
    void le_bon_est_refuse_pour_une_intervention_supprimee_mais_delivre_pour_un_equipement_supprime_nouveau_code() throws Exception {
        UUID g = generateur("G01");
        UUID supprimee = intervention("I_SUPPRIMEE", g, CENTRE_A, x -> x.supprimeLe = T0.plusDays(1));
        UUID gs = equipement("G_SUPPR", CENTRE_A, e -> e.supprimeLe = T0.plusDays(1));
        UUID surSupprime = intervention("I_SUR_EQUIPEMENT_SUPPRIME", gs, CENTRE_A, x -> {
        });

        assertEquals(400, get(bon(supprimee), adminA()).status, "KB-GMAO-69 corrigé : une intervention supprimée logiquement n est plus lisible par id");
        assertEquals(200, get(bon(surSupprime), adminA()).status);
    }

    @Test
    void le_bon_refuse_les_ids_inconnus_les_autres_centres_et_les_donnees_incoherentes() throws Exception {
        UUID g = generateur("G01");
        UUID i = intervention("I_OK", g, CENTRE_A, x -> {
        });
        UUID gb = equipement("G_B", CENTRE_B);
        UUID iB = intervention("I_B", gb, CENTRE_B, x -> {
        });
        UUID incoherente = intervention("I_EQUIPEMENT_AUTRE_CENTRE", gb, CENTRE_A, x -> {
        });
        UUID sansEquipement = intervention("I_SANS_EQUIPEMENT", INCONNU, CENTRE_A, x -> {
        });

        List<String> lignes = new ArrayList<>();
        lignes.add("intervention du centre A -> " + (get(bon(i), adminA()).status));
        lignes.add("intervention inconnue -> " + resume(get(bon(INCONNU), adminA())));
        lignes.add("intervention du centre B vue par A -> " + resume(get(bon(iB), adminA())));
        lignes.add("intervention de A sur l'équipement du centre B -> " + resume(get(bon(incoherente), adminA())));
        lignes.add("intervention dont l'équipement n'existe pas -> " + resume(get(bon(sansEquipement), adminA())));
        lignes.add("id illisible -> " + resume(get(GMAO + "/interventions/pas-un-uuid/bon", adminA())));
        lignes.add("méthode POST -> " + resume(call(org.springframework.http.HttpMethod.POST, bon(i), null, adminA())));
        goldenLignes("bon_erreurs", lignes);
    }

    @Test
    void le_bon_est_reserve_aux_administrateurs_du_centre() throws Exception {
        UUID i = intervention("I_AUTH", generateur("G01"), CENTRE_A, x -> {
        });
        Map<String, org.springframework.test.web.servlet.request.RequestPostProcessor> qui = new LinkedHashMap<>();
        qui.put("ADMIN du centre", adminA());
        qui.put("ADMIN d'un autre centre", adminB());
        qui.put("TECHNICIEN", roleSeul("TECHNICIEN"));
        qui.put("MEDECIN", roleSeul("MEDECIN"));
        qui.put("SECRETAIRE", roleSeul("SECRETAIRE"));
        qui.put("INFIRMIER", roleSeul("INFIRMIER"));
        qui.put("SUPERADMIN", roleSeul("SUPERADMIN"));
        qui.put("DIRECTION", direction());
        qui.put("GMAO_RECTIFICATION sans ADMIN", roleSeul("GMAO_RECTIFICATION"));
        qui.put("GMAO_REFORME sans ADMIN", roleSeul("GMAO_REFORME"));
        qui.put("anonyme", null);

        List<String> lignes = new ArrayList<>();
        for (var e : qui.entrySet()) {
            Resp r = get(bon(i), e.getValue());
            lignes.add(e.getKey() + " -> " + (r.status == 200 ? "200 PDF" : resume(r)));
        }
        goldenLignes("bon_autorisations", lignes);
    }

    @Test
    void le_format_du_modele_du_centre_commande_le_type_de_contenu_et_l_en_tete_de_telechargement() throws Exception {
        UUID i = interventionComplete("I_FORMATS", generateur("G01"));
        String id8 = i.toString().substring(0, 8);
        List<String> lignes = new ArrayList<>();
        for (String format : List.of("PDF", "EXCEL", "XLS", "XLSX", "HTML", "html", "inconnu")) {
            jdbc.update("DELETE FROM modele_document WHERE center_id = ?", CENTRE_A);
            jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, "
                            + "format_impression, description, active) VALUES (?, ?, 'BON_INTERVENTION', 'Bon', "
                            + "'BON_INTERVENTION', 'reports/bon_intervention.jrxml', ?, 'x', TRUE)",
                    UUID.randomUUID(), CENTRE_A, format);
            Resp r = get(bon(i), adminA());
            String debut = r.bytes.length >= 4 ? new String(r.bytes, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1) : "";
            lignes.add(format + " -> " + r.status + " type=" + r.header("Content-Type") + " disposition="
                    + String.valueOf(r.header("Content-Disposition")).replace(id8, "<ID8>") + " debut="
                    + debut.replaceAll("[^\\x20-\\x7e]", "?"));
        }
        goldenLignes("bon_formats_du_modele", lignes);
    }

    @Test
    void un_modele_de_document_desactive_rend_le_bon_impossible() throws Exception {
        UUID i = intervention("I_INACTIF", generateur("G01"), CENTRE_A, x -> {
        });
        jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, "
                + "format_impression, description, active) VALUES (?, ?, 'BON_INTERVENTION', 'Bon', 'BON_INTERVENTION', "
                + "'reports/bon_intervention.jrxml', 'PDF', 'x', FALSE)", UUID.randomUUID(), CENTRE_A);

        golden("bon_modele_desactive", get(bon(i), adminA()));
    }

    @Test
    void le_bon_lit_le_nom_de_l_utilisateur_connecte_comme_editeur_et_le_modele_se_cree_a_la_premiere_impression() throws Exception {
        UUID i = intervention("I_EDITEUR", generateur("G01"), CENTRE_A, x -> {
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM modele_document WHERE center_id = ?", Integer.class, CENTRE_A));

        String texte = textePdf(get(bon(i), adminA()));

        assertTrue(texte.contains("Édité le <DATE> par u-"), "l'éditeur est le nom d'utilisateur du jeton : " + texte);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM modele_document WHERE center_id = ? "
                + "AND type_document = 'BON_INTERVENTION' AND active = TRUE", Integer.class, CENTRE_A),
                "KB-GMAO-70 : une lecture crée le modèle de document du centre (écriture dans un GET)");
    }

    // ------------------------------------------------------------------ cohérence avec les écritures du nouveau module

    @Test
    void le_bon_reflete_la_cloture_les_lignes_de_cout_et_la_rectification_ecrites_par_le_nouveau_module() throws Exception {
        UUID g = generateur("G01");
        OffsetDateTime debut = maintenant.minusDays(3).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        UUID i = planifierParHttp(g, "EN_SERVICE", INTERVENANT_INTERNE, debut);
        demarrerParHttp(i);
        assertEquals(200, post(GMAO + "/interventions/" + i + "/lignes-cout",
                body("type", "PIECE", "libelle", "Pompe", "quantite", new BigDecimal("2"), "prixUnitaire", new BigDecimal("1500.00")),
                adminA()).status);
        terminerParHttp(i, "EN_SERVICE", debut.plusHours(3));

        String apresCloture = espacesSimples(textePdf(get(bon(i), adminA())));
        assertTrue(apresCloture.contains("Terminée"), apresCloture);
        assertTrue(apresCloture.contains("Pièces de rechange : 3 000,00 DA"), apresCloture);
        // la clôture ajoute une ligne de MAIN D'ŒUVRE (pas « intervenant ») au tarif de l'intervenant : 35,00 DA/h x 3 h
        assertTrue(apresCloture.contains("Main d'œuvre : 105,00 DA"), apresCloture);
        assertTrue(apresCloture.contains("Honoraires de l'intervenant : 0,00 DA"), apresCloture);
        assertTrue(apresCloture.contains("COÛT TOTAL : 3 105,00 DA"), apresCloture);
        assertTrue(apresCloture.contains("Durée : 3 h 00"), apresCloture);
        assertTrue(apresCloture.contains("Samia Administratrice"), apresCloture);

        var rectifieur = qui(CENTRE_A, ADMIN_A, "ADMIN", "GMAO_RECTIFICATION");
        assertEquals(200, post(GMAO + "/interventions/" + i + "/rectifier", body("motif", "Erreur de saisie"), rectifieur).status);

        String apresRectification = espacesSimples(textePdf(get(bon(i), adminA())));
        assertTrue(apresRectification.contains("Rectifiée"), apresRectification);
        assertTrue(apresRectification.contains("motif : Erreur de saisie"), apresRectification);
        assertTrue(apresRectification.contains("COÛT TOTAL : 3 000,00 DA"),
                "la rectification retire la ligne automatique de l'intervenant : " + apresRectification);
    }

    @Test
    void le_bon_ne_cite_pas_les_taches_ni_les_pieces_jointes_du_nouveau_module() throws Exception {
        UUID g = generateur("G01");
        UUID i = planifierParHttp(g, "EN_SERVICE", null, maintenant.minusDays(1));
        demarrerParHttp(i);
        assertEquals(200, post(GMAO + "/interventions/" + i + "/taches", body("description", "Purger le circuit"), adminA()).status);
        UUID doc = UUID.randomUUID();
        byte[] pdf = "%PDF-1.4\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        jdbc.update("INSERT INTO gmao_documents_intervention (id, intervention_id, centre_id, type, nom, content_type, taille, "
                + "contenu, ajoute_par, ajoute_le) VALUES (?, ?, ?, 'AUTRE', 'rapport-secret.pdf', 'application/pdf', ?, ?, ?, ?)",
                doc, i, CENTRE_A, (long) pdf.length, pdf, ADMIN_A, maintenant);

        String texte = textePdf(get(bon(i), adminA()));

        assertFalse(texte.contains("Purger le circuit"), texte);
        assertFalse(texte.contains("rapport-secret"), texte);
    }
}
