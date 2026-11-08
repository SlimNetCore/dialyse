package com.hemodialyse.backend.characterization.seance.se6;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE6 : suppression d'une séance par l'administrateur ({@code DELETE /api/v1/seances/{id}?centerId=} avec un corps {@code {"motif"}}) :
 * état SQL (séance, journal {@code seance_suppression}, consommations, stock, volets, administrations, raccourcis), statut et facture,
 * ordre des gardes, motif, paramètres, droits, cloisonnement, diffusions, notifications, audit. Décrit le comportement ACTUEL.
 */
class LaSuppressionDUneSeanceCharacterizationIntegrationTest extends Se6CharacterizationSupport {

    private static final String MOTIF = "Seance saisie en double";

    @Override
    protected String dossier() {
        return "suppression";
    }

    private UUID raccourci(UUID centre, UUID article, int ordre) {
        jdbc.update("INSERT INTO seance_raccourcis_articles (center_id, article_id, ordre) VALUES (?, ?, ?)", centre, article, ordre);
        return article;
    }

    @Test
    void la_suppression_efface_la_seance_ses_volets_ses_administrations_et_ses_consommations_et_laisse_une_trace() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID a2 = articleSe2(CENTRE_A, "ART_2", true, "10");
        lotSe2(CENTRE_A, a1, "LOT_1", "10");
        lotSe2(CENTRE_A, a2, "LOT_2", "10");
        raccourci(CENTRE_A, a1, 1);
        UUID seance = seanceComplete(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        UUID autre = seanceComplete(CENTRE_A, patient, "AUTRE_SEANCE", jour(1), "CREE");
        ajouter(seance, a1, 2, infirmierA());
        ajouter(seance, a2, 3, infirmierA());
        diffusions.clear();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 avant : traces de la seance", traces(seance));
        m.put("02 avant : consommations", consommationsImputees());
        m.put("03 avant : lots et stock", List.of(lotsRestants(), stocksDesArticles()));
        m.put("04 suppression par l'administrateur", reponse(supprimer(seance, MOTIF, adminA())));
        m.put("05 apres : traces de la seance", traces(seance));
        m.put("06 apres : consommations", consommationsImputees());
        m.put("07 apres : lots et stock inchanges", List.of(lotsRestants(), stocksDesArticles()));
        m.put("08 journal des suppressions", journalDesSuppressions());
        m.put("09 diffusions", diffusionsLues());
        m.put("10 notifications", notificationsEnregistrees());
        m.put("11 raccourcis inchanges", etatDesRaccourcis());
        m.put("12 l'autre seance du patient est intacte", traces(autre));
        m.put("13 seconde suppression de la meme seance", refus(supprimer(seance, MOTIF, adminA())));
        m.put("14 journal apres la seconde suppression", journalDesSuppressions().size());
        arbre("nominal", m);
    }

    @Test
    void la_suppression_d_une_seance_validee_avec_consommations_declarees_a_la_validation_restitue_ou_non_le_stock() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        lotSe2(CENTRE_A, a1, "LOT_1", "10");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour(0), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 validation avec une consommation de 4", refus(doPost(validerApi(seance), validationAvecConsommations(ligne(a1, 4)),
                infirmierA())));
        m.put("02 avant : consommations", consommationsImputees());
        m.put("03 avant : lots et stock", List.of(lotsRestants(), stocksDesArticles()));
        m.put("04 avant : mouvements de stock", mouvementsDeStock());
        m.put("05 avant : pieces de sortie", lignes("SELECT COUNT(*) AS sorties FROM sorties_pour_seances WHERE center_id = ?", CENTRE_A));
        m.put("06 avant : lignes de sortie", lignes("SELECT COUNT(*) AS lignes FROM sorties_pour_seances_lignes WHERE sortie_id IN "
                + "(SELECT id FROM sorties_pour_seances WHERE center_id = ?)", CENTRE_A));
        diffusions.clear();
        m.put("07 suppression", refus(supprimer(seance, MOTIF, adminA())));
        m.put("08 apres : traces", traces(seance));
        m.put("09 apres : consommations", consommationsImputees());
        m.put("10 apres : lots et stock", List.of(lotsRestants(), stocksDesArticles()));
        m.put("11 apres : mouvements de stock", mouvementsDeStock());
        m.put("12 apres : pieces de sortie", lignes("SELECT COUNT(*) AS sorties FROM sorties_pour_seances WHERE center_id = ?", CENTRE_A));
        m.put("13 apres : lignes de sortie", lignes("SELECT COUNT(*) AS lignes FROM sorties_pour_seances_lignes WHERE sortie_id IN "
                + "(SELECT id FROM sorties_pour_seances WHERE center_id = ?)", CENTRE_A));
        m.put("14 journal", journalDesSuppressions());
        arbre("avec_consommations_validees", m);
    }

    @Test
    void la_matrice_des_statuts_de_la_suppression_sans_et_avec_facture() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (boolean avecFacture : List.of(false, true)) {
            for (String statut : List.of("CREE", "ABSENT", "VALIDEE", "SIGNEE", "FACTUREE", "EN_COURS")) {
                nettoyerSe1();
                UUID patient = patientCouvert(CENTRE_A, "PAT_A");
                UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "CREE");
                UUID facture = idStable("facture", "F-SE6");
                aliasNommes.put(facture.toString(), "<FACTURE>");
                jdbc.update("UPDATE seances SET statut = ?, facture_id = ? WHERE id = ?", statut, avecFacture ? facture : null, seance);
                diffusions.clear();
                String issue = refus(supprimer(seance, MOTIF, adminA()));
                m.put((avecFacture ? "avec facture_id : " : "sans facture_id : ") + statut, issue + " ; seance restante="
                        + compter("SELECT COUNT(*) FROM seances WHERE id = ?", seance) + " ; suppressions="
                        + nombreDeSuppressions() + " ; diffusions=" + diffusions.size());
            }
        }
        arbre("matrice_des_statuts", m);
    }

    @Test
    void une_seance_signee_ou_validee_portant_une_facture_est_supprimee_et_la_facture_reference_une_seance_disparue() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String statut : List.of("VALIDEE", "SIGNEE")) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceComplete(CENTRE_A, patient, "SEANCE", jour(0), statut);
            UUID facture = idStable("facture", "F-SE6");
            aliasNommes.put(facture.toString(), "<FACTURE>");
            jdbc.update("UPDATE seances SET facture_id = ? WHERE id = ?", facture, seance);
            m.put(statut + " 01 avant", lignes("SELECT statut, facture_id FROM seances WHERE id = ?", seance));
            m.put(statut + " 02 suppression", refus(supprimer(seance, MOTIF, adminA())));
            m.put(statut + " 03 apres : traces", traces(seance));
            m.put(statut + " 04 le journal ne garde ni la facture ni son lien", journalDesSuppressions());
            m.put(statut + " 05 seances encore rattachees a la facture", compter("SELECT COUNT(*) FROM seances WHERE facture_id = ?", facture));
        }
        arbre("avec_facture", m);
    }

    @Test
    void l_ordre_des_gardes_de_la_suppression_est_seance_puis_motif_puis_statut() throws Exception {
        UUID patientA = patientCouvert(CENTRE_A, "PAT_A");
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        UUID facturee = seanceEnBase(CENTRE_A, patientA, "FACTUREE_A", jour(0), "FACTUREE");
        UUID cree = seanceEnBase(CENTRE_A, patientA, "CREE_A", jour(1), "CREE");
        UUID chezB = seanceEnBase(CENTRE_B, patientB, "CREE_B", jour(0), "CREE");
        UUID factureeB = seanceEnBase(CENTRE_B, patientB, "FACTUREE_B", jour(1), "FACTUREE");
        UUID inconnue = idStable("seance", "inconnue");
        aliasNommes.put(inconnue.toString(), "<SEANCE_INCONNUE>");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 facturee + motif valide", refus(supprimer(facturee, MOTIF, adminA())));
        m.put("02 facturee + motif vide (le motif passe avant le statut)", refus(supprimer(facturee, " ", adminA())));
        m.put("03 inconnue + motif vide (la recherche passe avant le motif)", refus(supprimer(inconnue, " ", adminA())));
        m.put("04 inconnue + motif valide", refus(supprimer(inconnue, MOTIF, adminA())));
        m.put("05 seance du centre B + motif vide (centre de la session A)", refus(supprimer(chezB, " ", adminA())));
        m.put("06 seance facturee du centre B + motif valide", refus(supprimer(factureeB, MOTIF, adminA())));
        m.put("07 centerId du centre B pour un administrateur A", refus(doDeleteAvecCorps(suppressionApi(chezB, CENTRE_B),
                corps("motif", MOTIF), adminA())));
        m.put("08 centerId absent + motif vide", refus(doDeleteAvecCorps(suppressionApi(cree, null), corps("motif", " "), adminA())));
        m.put("09 non facturee + motif vide", refus(supprimer(cree, " ", adminA())));
        m.put("10 etat des seances", etatDesSeances().stream().map(l -> l.get("statut") + "@" + l.get("center_id")).toList());
        m.put("11 journal vide", nombreDeSuppressions());
        arbre("ordre_des_gardes", m);
    }

    @Test
    void le_motif_doit_compter_de_5_a_500_caracteres_apres_suppression_des_espaces_de_bord() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> motifs = new LinkedHashMap<>();
        motifs.put("01 absent (corps vide d'objet)", null);
        motifs.put("02 chaine vide", "");
        motifs.put("03 espaces seuls", "        ");
        motifs.put("04 quatre caracteres", "abcd");
        motifs.put("05 quatre caracteres entoures d'espaces", "   abcd   ");
        motifs.put("06 cinq caracteres", "abcde");
        motifs.put("07 cinq caracteres entoures d'espaces", "   abcde   ");
        motifs.put("08 cinq cents caracteres", "x".repeat(500));
        motifs.put("09 cinq cents caracteres entoures d'espaces", "  " + "x".repeat(500) + "  ");
        motifs.put("10 cinq cent un caracteres", "x".repeat(501));
        motifs.put("11 accents et ponctuation", "Erreur de patient (doublon) : à vérifier");
        for (Map.Entry<String, String> e : motifs.entrySet()) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour(0), "CREE");
            Resp r = e.getValue() == null
                    ? doDeleteAvecCorps(suppressionApi(seance, CENTRE_A), "{}", adminA())
                    : supprimer(seance, e.getValue(), adminA());
            List<Map<String, Object>> journal = lignes("SELECT LENGTH(motif) AS longueur, (CASE WHEN motif = TRIM(motif) THEN 'sans espace de bord' "
                    + "ELSE 'espaces de bord conserves' END) AS bord FROM seance_suppression WHERE center_id = ?", CENTRE_A);
            m.put(e.getKey(), refus(r) + " ; journal=" + journal);
        }
        arbre("motif", m);
    }

    @Test
    void les_parametres_de_la_suppression_sont_controles_centre_identifiant_et_corps() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        // une seance neuve par cas : un cas qui reussit la supprime
        String[] cas = {"01 centerId absent", "02 centerId illisible", "03 identifiant de seance illisible", "04 corps absent",
                "05 corps JSON illisible", "06 corps null", "07 motif numerique", "08 motif en tableau",
                "09 champ inconnu dans le corps", "10 centerId du jeton explicite"};
        for (String nom : cas) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour(0), "CREE");
            String valide = corps("motif", MOTIF);
            Resp r = switch (nom.substring(0, 2)) {
                case "01" -> doDeleteAvecCorps(suppressionApi(seance, null), valide, adminA());
                case "02" -> doDeleteAvecCorps(SEANCES_API + "/" + seance + "?centerId=pas-un-uuid", valide, adminA());
                case "03" -> doDeleteAvecCorps(SEANCES_API + "/pas-un-uuid?centerId=" + CENTRE_A, valide, adminA());
                case "04" -> doDelete(suppressionApi(seance, CENTRE_A), adminA());
                case "05" -> doDeleteAvecCorps(suppressionApi(seance, CENTRE_A), "{pas du json", adminA());
                case "06" -> doDeleteAvecCorps(suppressionApi(seance, CENTRE_A), "null", adminA());
                case "07" -> doDeleteAvecCorps(suppressionApi(seance, CENTRE_A), "{\"motif\":12345678}", adminA());
                case "08" -> doDeleteAvecCorps(suppressionApi(seance, CENTRE_A), "{\"motif\":[\"abcdefgh\"]}", adminA());
                case "09" -> doDeleteAvecCorps(suppressionApi(seance, CENTRE_A), "{\"motif\":\"" + MOTIF + "\",\"extra\":1}", adminA());
                default -> doDeleteAvecCorps(suppressionApi(seance, CENTRE_A), valide, adminA());
            };
            m.put(nom, refus(r) + " ; seance restante=" + compter("SELECT COUNT(*) FROM seances WHERE id = ?", seance)
                    + " ; suppressions=" + nombreDeSuppressions());
        }
        arbre("parametres", m);
    }

    @Test
    void les_droits_par_profil_de_la_suppression() throws Exception {
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour(0), "CREE");
            diffusions.clear();
            profils.put(p.getKey(), statutEtCode(supprimer(seance, MOTIF, p.getValue())) + " ; seance restante="
                    + compter("SELECT COUNT(*) FROM seances WHERE id = ?", seance) + " ; suppressions=" + nombreDeSuppressions()
                    + " ; diffusions=" + diffusions.size());
        }
        nettoyerSe1();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour(0), "CREE");
        profils.put("anonyme", statutEtCode(appel("DELETE", suppressionApi(seance, CENTRE_A), null, null)) + " ; seance restante="
                + compter("SELECT COUNT(*) FROM seances WHERE id = ?", seance));
        arbre("profils", profils);
    }

    @Test
    void le_cloisonnement_par_centre_de_la_suppression() throws Exception {
        UUID patientA = patientCouvert(CENTRE_A, "PAT_A");
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        UUID seanceA = seanceEnBase(CENTRE_A, patientA, "SEANCE_A", jour(0), "CREE");
        UUID seanceB = seanceEnBase(CENTRE_B, patientB, "SEANCE_B", jour(0), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 administrateur A, centre B, seance B", refus(doDeleteAvecCorps(suppressionApi(seanceB, CENTRE_B), corps("motif", MOTIF), adminA())));
        m.put("02 administrateur B, centre A, seance A", refus(doDeleteAvecCorps(suppressionApi(seanceA, CENTRE_A), corps("motif", MOTIF), adminB())));
        m.put("03 administrateur B, centre B, seance A", refus(doDeleteAvecCorps(suppressionApi(seanceA, CENTRE_B), corps("motif", MOTIF), adminB())));
        m.put("04 administrateur A, centre A, seance B", refus(supprimer(seanceB, MOTIF, adminA())));
        m.put("05 seances restantes", etatDesSeances().stream().map(l -> l.get("statut") + "@" + l.get("center_id")).toList());
        m.put("06 SUPERADMIN + ADMIN du centre A vise le centre B", refus(doDeleteAvecCorps(suppressionApi(seanceB, CENTRE_B),
                corps("motif", MOTIF), as(CENTRE_A, "SUPERADMIN", "ADMIN"))));
        m.put("07 apres : seance B", compter("SELECT COUNT(*) FROM seances WHERE id = ?", seanceB));
        m.put("08 journal", journalDesSuppressions());
        m.put("09 administrateur A supprime la seance A", refus(supprimer(seanceA, MOTIF, adminA())));
        m.put("10 journal", journalDesSuppressions());
        arbre("cloisonnement", m);
    }

    @Test
    void la_suppression_ne_depend_ni_de_la_date_de_la_seance_ni_du_patient() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, LocalDate> e : Map.of("01 passee (J-30)", jour(-30), "02 du jour (J)", jour(0),
                "03 future (J+30)", jour(30)).entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE_" + e.getKey().substring(0, 2), e.getValue(), "SIGNEE");
            m.put(e.getKey() + " SIGNEE", refus(supprimer(seance, MOTIF, adminA())) + " ; restante=" + compter(
                    "SELECT COUNT(*) FROM seances WHERE id = ?", seance));
        }
        UUID orpheline = idStable("seance", "ORPHELINE");
        aliasNommes.put(orpheline.toString(), "<ORPHELINE>");
        aliasNommes.put(idStable("patient", "inexistant").toString(), "<PATIENT_ABSENT>");
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, hors_planning) VALUES (?,?,?,?,?,?,FALSE)",
                orpheline, idStable("patient", "inexistant"), CENTRE_A, java.sql.Date.valueOf(jour(0)), "CREE", CREATION_FIXE);
        m.put("04 seance d'un patient absent de la base", refus(supprimer(orpheline, MOTIF, adminA())) + " ; restante=" + compter(
                "SELECT COUNT(*) FROM seances WHERE id = ?", orpheline));
        m.put("05 journal", journalDesSuppressions());
        arbre("date_et_patient", m);
    }

    @Test
    void les_suppressions_et_leurs_refus_sont_journalises_avec_la_route_et_le_statut() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID cree = seanceEnBase(CENTRE_A, patient, "SEANCE_CREE", jour(0), "CREE");
        UUID facturee = seanceEnBase(CENTRE_A, patient, "SEANCE_FACTUREE", jour(1), "FACTUREE");
        UUID autre = seanceEnBase(CENTRE_A, patient, "SEANCE_AUTRE", jour(2), "CREE");
        UUID inconnue = idStable("seance", "inconnue");
        aliasNommes.put(inconnue.toString(), "<SEANCE_INCONNUE>");
        monterLAudit();
        supprimer(facturee, MOTIF, adminA());
        supprimer(cree, " ", adminA());
        supprimer(autre, MOTIF, infirmierA());
        supprimer(inconnue, MOTIF, adminA());
        supprimer(cree, MOTIF, adminA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }
}
