package com.hemodialyse.backend.characterization.seance.se2;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE2, lot 3 : déverrouillage d'une séance d'un jour passé restée « créée » par l'administrateur (RG-SEA-046,
 * {@code POST /api/v1/seances/{id}/deverrouiller-regularisation?centerId=}) : effet, idempotence, diffusion, bornes de jour (la fenêtre de
 * sept jours ne borne que le rappel, pas le déverrouillage), matrice des statuts, droits par profil, cloisonnement par centre,
 * paramètres invalides, audit, écritures simultanées. Le jour de comparaison est celui de la JVM ({@code LocalDate.now()}, KB-SEA-01).
 */
class LeDeverrouillagePourRegularisationCharacterizationIntegrationTest extends Se2CharacterizationSupport {

    @Override
    protected String dossier() {
        return "deverrouillage";
    }

    @Test
    void l_administrateur_deverrouille_une_seance_oubliee_l_infirmier_peut_alors_la_valider_et_un_second_deverrouillage_ne_change_rien() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour(-2), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 validation de l'infirmier avant le deverrouillage", refus(valider(seance, infirmierA())));
        m.put("02 deverrouillage par l'ADMIN", reponse(deverrouiller(seance, adminA())));
        m.put("03 etat (statut CREE, deverrouillee par le compte de la session)", etatSe2());
        m.put("04 diffusions (destinataire : les infirmiers)", diffusionsLues());
        String horodatage = deverrouillageBrut(seance);
        diffusions.clear();
        m.put("05 second deverrouillage (retour anticipe du domaine)", reponse(deverrouiller(seance, adminA())));
        m.put("06 horodatage de deverrouillage inchange", horodatage.equals(deverrouillageBrut(seance)));
        m.put("07 diffusions du second deverrouillage (rediffusees)", diffusionsLues());
        diffusions.clear();
        m.put("08 validation de l'infirmier apres le deverrouillage", refus(valider(seance, infirmierA())));
        m.put("09 etat final (le deverrouillage est conserve apres la validation)", etatSe2());
        arbre("nominal", m);
    }

    @Test
    void le_deverrouillage_n_est_borne_que_par_le_jour_passe_la_fenetre_de_sept_jours_ne_concerne_que_le_rappel() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int decalage : new int[]{-400, -30, -8, -7, -1, 0, 1, 30}) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour(decalage), "CREE");
            m.put(String.format("%+04d jours", decalage), issue(deverrouiller(seance, adminA()), seance));
        }
        arbre("bornes_de_jour", m);
    }

    @Test
    void la_matrice_des_statuts_seule_une_seance_creee_d_un_jour_passe_est_deverrouillable() throws Exception {
        LocalDate passe = jour(-2);
        Map<String, Object> m = new LinkedHashMap<>();
        for (String statut : List.of("CREE", "VALIDEE", "SIGNEE", "FACTUREE", "ABSENT", "EN_COURS")) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", passe, statut);
            diffusions.clear();
            m.put(statut, issue(deverrouiller(seance, adminA()), seance) + " ; diffusions=" + diffusions.size());
        }
        nettoyerSe1();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID deja = seanceEnBase(CENTRE_A, patient, "SEANCE_DEJA", passe, "CREE");
        deverrouillerEnBase(deja);
        m.put("CREE deja deverrouillee (idempotent)", issue(deverrouiller(deja, adminA()), deja));
        m.put("etat de la seance deja deverrouillee", etatSe2());
        arbre("matrice_des_statuts_nouveau_code", m);
    }

    @Test
    void les_droits_par_profil_du_deverrouillage() throws Exception {
        LocalDate passe = jour(-2);
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", passe, "CREE");
            profils.put(p.getKey(), statutEtCode(deverrouiller(seance, p.getValue())) + " ; " + etatCourt(seance) + " ; diffusions=" + diffusions.size());
        }
        nettoyerSe1();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", passe, "CREE");
        profils.put("anonyme", statutEtCode(appel("POST", deverrouillerApi(seance, CENTRE_A), "", null)) + " ; " + etatCourt(seance));
        arbre("profils", profils);
    }

    @Test
    void le_cloisonnement_par_centre_la_garde_de_la_seance_et_le_parametre_de_centre() throws Exception {
        LocalDate passe = jour(-2);
        UUID patientA = patientCouvert(CENTRE_A, "PAT_A");
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        UUID seanceA = seanceEnBase(CENTRE_A, patientA, "SEANCE_A", passe, "CREE");
        UUID seanceB = seanceEnBase(CENTRE_B, patientB, "SEANCE_B", passe, "CREE");
        UUID inconnue = idStable("seance", "inconnue");
        aliasNommes.put(inconnue.toString(), "<SEANCE_INCONNUE>");
        String avant = empreinteDesSeances();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 admin A vise le centre B", refus(doPost(deverrouillerApi(seanceB, CENTRE_B), "", adminA())));
        m.put("02 admin B vise le centre A", refus(doPost(deverrouillerApi(seanceA, CENTRE_A), "", adminB())));
        m.put("03 admin B, centre B, seance du centre A", refus(doPost(deverrouillerApi(seanceA, CENTRE_B), "", adminB())));
        m.put("04 admin A, centre A, seance du centre B", refus(doPost(deverrouillerApi(seanceB, CENTRE_A), "", adminA())));
        m.put("05 admin sans centre", refus(doPost(deverrouillerApi(seanceA, CENTRE_A), "", as(null, "ADMIN"))));
        m.put("06 seance inconnue", refus(doPost(deverrouillerApi(inconnue, CENTRE_A), "", adminA())));
        m.put("07 centerId absent", refus(doPost(deverrouillerApi(seanceA, null), "", adminA())));
        m.put("08 centerId illisible", refus(doPost(SEANCES_API + "/" + seanceA + "/deverrouiller-regularisation?centerId=pas-un-uuid", "", adminA())));
        m.put("09 identifiant de seance mal forme", refus(doPost(SEANCES_API + "/pas-un-uuid/deverrouiller-regularisation?centerId=" + CENTRE_A, "",
                adminA())));
        m.put("10 aucune ecriture", empreinteDesSeances().equals(avant));
        m.put("11 SUPERADMIN + ADMIN de la session A vise le centre B (transverse)", refus(doPost(deverrouillerApi(seanceB, CENTRE_B), "",
                as(CENTRE_A, "SUPERADMIN", "ADMIN"))));
        m.put("12 etat apres le superadmin", etatSe2());
        arbre("cloisonnement_et_parametres_nouveau_code", m);
    }

    @Test
    void les_deverrouillages_et_leurs_refus_sont_journalises_avec_la_route_et_le_statut() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID passee = seanceEnBase(CENTRE_A, patient, "SEANCE_PASSEE", jour(-2), "CREE");
        UUID duJour = seanceEnBase(CENTRE_A, patient, "SEANCE_DU_JOUR", jour(0), "CREE");
        UUID inconnue = idStable("seance", "inconnue");
        aliasNommes.put(inconnue.toString(), "<SEANCE_INCONNUE>");
        monterLAudit();
        deverrouiller(passee, adminA());
        deverrouiller(passee, adminA());
        deverrouiller(duJour, adminA());
        deverrouiller(inconnue, adminA());
        deverrouiller(passee, infirmierA());
        doPost(deverrouillerApi(passee, CENTRE_B), "", adminA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }

    @Test
    void deux_deverrouillages_simultanes_ne_sont_proteges_par_aucun_verrou() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour(-2), "CREE");
        List<Resp> reponses = enParallele(() -> deverrouiller(seance, adminA()), () -> deverrouiller(seance, adminA()));
        Map<String, Object> m = new LinkedHashMap<>();
        // Invariants seulement : sans verrou ni version, les deux requetes peuvent ecrire chacune leur horodatage (200 et 200).
        m.put("01 toutes les reponses sont 200", reponses.stream().allMatch(r -> r.status() == 200));
        m.put("02 une seule ligne, CREE et deverrouillee", etatSe2());
        arbre("ecritures_simultanees", m);
    }

    @Test
    void le_deverrouillage_ne_change_ni_le_statut_ni_la_place_ni_les_consommations() throws Exception {
        decorDePlacement();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        placerLePatient(patient, salle1, matin, g1a);
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour(-2), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 deverrouillage", refus(deverrouiller(seance, adminA())));
        m.put("02 etat (statut CREE, aucune place, aucune validation)", etatSe2());
        m.put("03 consommations (aucune)", consommationsImputees());
        m.put("04 l'infirmier valide avec une consommation", refus(doPost(validerApi(seance), validationAvecConsommations(ligne(a1, 2)), infirmierA())));
        m.put("05 etat (place figee a la validation)", etatSe2());
        m.put("06 consommations", consommationsImputees());
        arbre("effets_limites", m);
    }
}
