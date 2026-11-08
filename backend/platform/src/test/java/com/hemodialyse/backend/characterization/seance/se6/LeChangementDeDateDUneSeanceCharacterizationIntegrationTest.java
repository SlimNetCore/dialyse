package com.hemodialyse.backend.characterization.seance.se6;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE6 : changement de la date d'une séance ({@code PUT /api/v1/seances/{id}} avec {@code centerId} et {@code dateSeance} dans le corps,
 * réservé à l'administrateur) : colonnes réécrites, statuts, bornes de date, jours du centre, doublons, interaction avec le verrou des
 * jours passés de la validation (SE2), droits, cloisonnement, diffusions, audit. Le cas de {@code facture_id} (KB-SEA-49) est
 * caractérisé par SE5 ({@code se5/date}) et n'est pas répété ici. Décrit le comportement ACTUEL.
 */
class LeChangementDeDateDUneSeanceCharacterizationIntegrationTest extends Se6CharacterizationSupport {

    @Override
    protected String dossier() {
        return "date";
    }

    private String corpsBrut(String dateJson) {
        return "{\"centerId\":\"" + CENTRE_A + "\"" + (dateJson == null ? "" : ",\"dateSeance\":" + dateJson) + "}";
    }

    @Test
    void le_changement_de_date_ne_reecrit_que_la_date_d_une_seance_signee_et_ne_diffuse_rien() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        lotSe2(CENTRE_A, a1, "LOT_1", "10");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "SIGNEE");
        aliasNommes.put(idStable("salle", "S").toString(), "<SALLE>");
        aliasNommes.put(idStable("creneau", "C").toString(), "<CRENEAU>");
        aliasNommes.put(idStable("generateur", "G").toString(), "<GENERATEUR>");
        jdbc.update("UPDATE seances SET salle_id = ?, creneau_id = ?, generateur_id = ? WHERE id = ?",
                idStable("salle", "S"), idStable("creneau", "C"), idStable("generateur", "G"), seance);
        ajouter(seance, a1, 2, infirmierA());
        Map<String, String> avant = ligneBrute(seance);
        diffusions.clear();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 avant", lignes("SELECT date_seance, statut, signed_infirmier_by, signed_medecin_by FROM seances WHERE id = ?", seance));
        m.put("02 changement de date vers J+2", reponse(changerLaDate(seance, jour(2), adminA())));
        m.put("03 colonnes modifiees", colonnesModifiees(avant, ligneBrute(seance)));
        m.put("04 apres", lignes("SELECT date_seance, statut, signed_infirmier_by, signed_medecin_by FROM seances WHERE id = ?", seance));
        m.put("05 place conservee", lignes("SELECT salle_id, creneau_id, generateur_id FROM seances WHERE id = ?", seance));
        m.put("06 consommations inchangees", consommationsImputees());
        m.put("07 diffusions", diffusionsLues());
        m.put("08 notifications", notificationsEnregistrees());
        m.put("09 seconde modification vers la meme date", refus(changerLaDate(seance, jour(2), adminA())));
        m.put("10 colonnes modifiees par la seconde", colonnesModifiees(avant, ligneBrute(seance)));
        arbre("nominal", m);
    }

    @Test
    void le_changement_de_date_n_est_refuse_que_pour_une_seance_facturee_la_matrice_des_statuts() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String statut : List.of("CREE", "ABSENT", "VALIDEE", "SIGNEE", "FACTUREE", "EN_COURS")) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "CREE");
            jdbc.update("UPDATE seances SET statut = ? WHERE id = ?", statut, seance);
            diffusions.clear();
            m.put(statut, issueEtDate(changerLaDate(seance, jour(2), adminA()), seance) + " ; diffusions=" + diffusions.size());
        }
        arbre("matrice_des_statuts", m);
    }

    @Test
    void les_bornes_de_la_date_modifiee_aucune_n_est_controlee_hors_format() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        jour(0); // nomme le jour du centre <J> (applique quand la date est absente)
        Map<String, String> dates = new LinkedHashMap<>();
        dates.put("01 date absente du corps (jour du centre)", null);
        dates.put("02 date nulle (jour du centre)", "null");
        dates.put("03 meme date que l'actuelle (J+3)", "\"" + jour(3) + "\"");
        dates.put("04 veille", "\"" + jour(-1) + "\"");
        dates.put("05 un an avant", "\"" + jour(-365) + "\"");
        dates.put("06 lendemain", "\"" + jour(1) + "\"");
        dates.put("07 plus d'un an apres", "\"" + jour(400) + "\"");
        dates.put("08 an 1900", "\"" + alias(LocalDate.of(1900, 1, 1), "<1900>") + "\"");
        dates.put("09 an 9999", "\"" + alias(LocalDate.of(9999, 12, 31), "<9999>") + "\"");
        dates.put("10 format jour/mois/annee", "\"31/12/2026\"");
        dates.put("11 chaine vide", "\"\"");
        dates.put("12 texte", "\"demain\"");
        dates.put("13 nombre 20261231 (accepte, lu comme une date lointaine)", "20261231");
        dates.put("14 date avec heure", "\"" + jour(2) + "T10:00:00\"");
        for (Map.Entry<String, String> e : dates.entrySet()) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour(3), "CREE"); // J+3 : le jour du centre se distingue de l'origine
            Resp r = doPut(SEANCES_API + "/" + seance, corpsBrut(e.getValue()), adminA());
            if (e.getKey().startsWith("13")) {
                // la date lue d'un nombre est aberrante : seule son annee (en base) est publiee
                m.put(e.getKey(), refus(r) + " ; annee en base=" + jdbc.queryForObject(
                        "SELECT YEAR(date_seance) FROM seances WHERE id = ?", Integer.class, seance));
                continue;
            }
            m.put(e.getKey(), issueEtDate(r, seance));
        }
        arbre("bornes", m);
    }

    @Test
    void le_changement_de_date_ignore_l_admission_les_jours_du_centre_la_programmation_et_les_doublons() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID autrePatient = patientCouvert(CENTRE_A, "PAT_B");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour(0), "CREE");
        seanceEnBase(CENTRE_A, patient, "DEJA_LA", jour(3), "VALIDEE");
        seanceEnBase(CENTRE_A, autrePatient, "AUTRE_PATIENT", jour(4), "CREE");
        fermerLeCentre(CENTRE_A, jour(5));
        jourFerieSansLibelle(CENTRE_A, jour(6));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 avant l'admission du patient", issueEtDate(changerLaDate(seance, alias(REPERE.minusDays(500), "<AVANT_ADMISSION>"), adminA()), seance));
        m.put("02 jour de fermeture exceptionnelle", issueEtDate(changerLaDate(seance, jour(5), adminA()), seance));
        m.put("03 jour ferie", issueEtDate(changerLaDate(seance, jour(6), adminA()), seance));
        joursDeDialyse(patient, false);
        m.put("04 jour sans dialyse programmee", issueEtDate(changerLaDate(seance, jour(7), adminA()), seance));
        joursDeDialyse(patient, true);
        m.put("05 jour deja pris par une autre seance du meme patient", issueEtDate(changerLaDate(seance, jour(3), adminA()), seance));
        m.put("06 seances du patient ce jour-la", compter("SELECT COUNT(*) FROM seances WHERE patient_id = ? AND date_seance = ?", patient,
                java.sql.Date.valueOf(jour(3))));
        m.put("07 jour d'une seance d'un autre patient", issueEtDate(changerLaDate(seance, jour(4), adminA()), seance));
        m.put("08 seances du jour J+4", compter("SELECT COUNT(*) FROM seances WHERE center_id = ? AND date_seance = ?", CENTRE_A,
                java.sql.Date.valueOf(jour(4))));
        arbre("regles_ignorees", m);
    }

    @Test
    void le_changement_de_date_contourne_le_verrou_des_jours_passes_de_la_validation_et_n_atteint_pas_les_sorties_de_stock() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        lotSe2(CENTRE_A, a1, "LOT_1", "10");
        UUID passee = seanceEnBase(CENTRE_A, patient, "PASSEE", jour(-3), "CREE");
        UUID duJour = seanceEnBase(CENTRE_A, patient, "DU_JOUR", jour(0), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 validation d'une seance passee", issue(valider(passee, infirmierA()), passee));
        m.put("02 la date est ramenee au jour du centre", issueEtDate(changerLaDate(passee, jour(0), adminA()), passee));
        m.put("03 validation apres le changement de date", issue(valider(passee, infirmierA()), passee));
        m.put("04 la seance du jour est reculee de trois jours", issueEtDate(changerLaDate(duJour, jour(-3), adminA()), duJour));
        m.put("05 validation de la seance reculee", issue(valider(duJour, infirmierA()), duJour));
        m.put("06 signature du medecin de la seance reculee", issue(signer(duJour, medecinA()), duJour));
        UUID signee = seanceValidee(CENTRE_A, patient, "SIGNEE_DU_JOUR", jour(1), "SIGNEE");
        m.put("07 la seance signee est reculee de quatre jours (jour passe)", issueEtDate(changerLaDate(signee, jour(-3), adminA()), signee));
        m.put("08 etat de signature apres le changement de date", etatCourt(signee));
        m.put("09 validation puis signature a nouveau de la seance signee reculee", issue(valider(signee, infirmierA()), signee)
                + " ; " + issue(signer(signee, medecinA()), signee));
        arbre("verrou_des_jours_passes", m);

        nettoyerSe1();
        UUID patient2 = patientCouvert(CENTRE_A, "PAT_A");
        UUID a2 = articleSe2(CENTRE_A, "ART_1", true, "10");
        lotSe2(CENTRE_A, a2, "LOT_1", "10");
        UUID seance = seanceEnBase(CENTRE_A, patient2, "SEANCE", jour(0), "CREE");
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("01 validation avec une consommation", refus(doPost(validerApi(seance), validationAvecConsommations(ligne(a2, 3)), infirmierA())));
        s.put("02 jour de la sortie de stock avant", lignes("SELECT jour FROM sorties_pour_seances WHERE center_id = ?", CENTRE_A));
        s.put("03 changement de date vers J+2", issueEtDate(changerLaDate(seance, jour(2), adminA()), seance));
        s.put("04 jour de la sortie de stock apres", lignes("SELECT jour FROM sorties_pour_seances WHERE center_id = ?", CENTRE_A));
        s.put("05 consommations", consommationsImputees());
        arbre("sorties_de_stock", s);
    }

    @Test
    void les_parametres_du_changement_de_date_sont_controles_seance_inconnue_et_formes_illisibles() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour(0), "CREE");
        UUID inconnue = idStable("seance", "inconnue");
        aliasNommes.put(inconnue.toString(), "<SEANCE_INCONNUE>");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 seance inconnue", refus(changerLaDate(inconnue, jour(2), adminA())));
        m.put("02 identifiant illisible", refus(doPut(SEANCES_API + "/pas-un-uuid", corpsBrut("\"" + jour(2) + "\""), adminA())));
        m.put("03 centerId absent", refus(doPut(SEANCES_API + "/" + seance, "{\"dateSeance\":\"" + jour(2) + "\"}", adminA())));
        m.put("04 centerId illisible", refus(doPut(SEANCES_API + "/" + seance,
                "{\"centerId\":\"pas-un-uuid\",\"dateSeance\":\"" + jour(2) + "\"}", adminA())));
        m.put("05 corps JSON illisible", refus(doPut(SEANCES_API + "/" + seance, "{pas du json", adminA())));
        m.put("06 corps vide", refus(doPut(SEANCES_API + "/" + seance, "", adminA())));
        m.put("07 corps objet vide", refus(doPut(SEANCES_API + "/" + seance, "{}", adminA())));
        m.put("08 la seance n'a pas bouge", dateEtStatut(seance));
        m.put("09 champs inconnus (statut, factureId) ignores ou refuses", refus(doPut(SEANCES_API + "/" + seance,
                "{\"centerId\":\"" + CENTRE_A + "\",\"dateSeance\":\"" + jour(2) + "\",\"status\":\"SIGNEE\"}", adminA())));
        m.put("10 la seance", dateEtStatut(seance));
        arbre("parametres", m);
    }

    @Test
    void les_droits_par_profil_du_changement_de_date() throws Exception {
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour(0), "CREE");
            profils.put(p.getKey(), statutEtCode(changerLaDate(seance, jour(2), p.getValue())) + " ; " + dateEtStatut(seance));
        }
        nettoyerSe1();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour(0), "CREE");
        profils.put("anonyme", statutEtCode(appel("PUT", SEANCES_API + "/" + seance, corpsBrut("\"" + jour(2) + "\""), null)) + " ; "
                + dateEtStatut(seance));
        arbre("profils", profils);
    }

    @Test
    void le_cloisonnement_par_centre_du_changement_de_date() throws Exception {
        UUID patientA = patientCouvert(CENTRE_A, "PAT_A");
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        UUID seanceA = seanceEnBase(CENTRE_A, patientA, "SEANCE_A", jour(0), "CREE");
        UUID seanceB = seanceEnBase(CENTRE_B, patientB, "SEANCE_B", jour(0), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 administrateur A, centre A, seance B", refus(changerLaDate(seanceB, jour(2), adminA())));
        m.put("02 administrateur A, centre B dans le corps, seance B", refus(doPut(SEANCES_API + "/" + seanceB,
                corps("centerId", CENTRE_B, "dateSeance", jour(2)), adminA())));
        m.put("03 administrateur B, centre A dans le corps, seance A", refus(doPut(SEANCES_API + "/" + seanceA,
                corps("centerId", CENTRE_A, "dateSeance", jour(2)), adminB())));
        m.put("04 administrateur B, centre B dans le corps, seance A", refus(doPut(SEANCES_API + "/" + seanceA,
                corps("centerId", CENTRE_B, "dateSeance", jour(2)), adminB())));
        m.put("05 etat", List.of(dateEtStatut(seanceA), dateEtStatut(seanceB)));
        m.put("06 SUPERADMIN + ADMIN du centre A vise le centre B", refus(doPut(SEANCES_API + "/" + seanceB,
                corps("centerId", CENTRE_B, "dateSeance", jour(2)), as(CENTRE_A, "SUPERADMIN", "ADMIN"))));
        m.put("07 etat", List.of(dateEtStatut(seanceA), dateEtStatut(seanceB)));
        arbre("cloisonnement", m);
    }

    @Test
    void les_changements_de_date_et_leurs_refus_sont_journalises_avec_la_route_et_le_statut() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID cree = seanceEnBase(CENTRE_A, patient, "SEANCE_CREE", jour(0), "CREE");
        UUID facturee = seanceEnBase(CENTRE_A, patient, "SEANCE_FACTUREE", jour(1), "FACTUREE");
        UUID inconnue = idStable("seance", "inconnue");
        aliasNommes.put(inconnue.toString(), "<SEANCE_INCONNUE>");
        monterLAudit();
        changerLaDate(cree, jour(2), adminA());
        changerLaDate(facturee, jour(3), adminA());
        changerLaDate(cree, jour(3), infirmierA());
        changerLaDate(inconnue, jour(3), adminA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }
}
