package com.hemodialyse.backend.characterization.seance.se1;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE1, lot 3 : consultation d'une séance ({@code GET /api/v1/seances/{id}?centerId=}) et liste ({@code GET /api/v1/seances}) des séances
 * créées : forme du détail, forfait courant, générateur du patient, cloisonnement, filtres, tris, pagination, forme des erreurs.
 */
class LaConsultationEtLaListeDesSeancesCharacterizationIntegrationTest extends Se1CharacterizationSupport {

    @Override
    protected String dossier() {
        return "lecture";
    }

    private String liste(String parametres) throws Exception {
        return refusOuListe(doGet(SEANCES_API + "?centerId=" + CENTRE_A + parametres, adminA()));
    }

    private String refusOuListe(Resp r) {
        if (r.status() != 200) return r.status() == 400 && r.raw() != null && !r.raw().startsWith("{") ? "400 | " + r.raw() : refus(r);
        JsonNode c = corps(r);
        List<String> lignes = new ArrayList<>();
        for (JsonNode i : c.path("items")) {
            lignes.add(i.path("patientCode").asString() + " " + i.path("dateSeance").asString() + " " + i.path("status").asString());
        }
        return "200 total=" + c.path("total").asString() + " page=" + c.path("page").asString() + " size=" + c.path("size").asString()
                + " " + lignes;
    }

    private void jeuDeSeances() {
        UUID alpha = patientCouvert(CENTRE_A, "ALPHA");
        UUID bravo = patientCouvert(CENTRE_A, "BRAVO");
        seanceEnBase(CENTRE_A, alpha, "A1", fixe(1), "CREE");
        seanceEnBase(CENTRE_A, alpha, "A2", fixe(2), "VALIDEE");
        seanceEnBase(CENTRE_A, alpha, "A3", fixe(3), "SIGNEE");
        seanceEnBase(CENTRE_A, bravo, "B1", fixe(1), "FACTUREE");
        seanceEnBase(CENTRE_A, bravo, "B2", fixe(2), "ABSENT");
        seanceEnBase(CENTRE_A, bravo, "B3", fixe(3), "CREE");
        UUID autreCentre = patientCouvert(CENTRE_B, "ALPHA");
        seanceEnBase(CENTRE_B, autreCentre, "C1", fixe(1), "CREE");
    }

    @Test
    void le_detail_d_une_seance_creee_donne_la_seance_le_patient_le_forfait_courant_et_aucun_volet() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        Resp c = doPost(SEANCES_API, corps("centerId", CENTRE_A, "patientId", patient, "dateSeance", fixe(10)), infirmierA());
        UUID seance = UUID.fromString(idDe(c));
        aliasNommes.put(seance.toString(), "<SEANCE>");
        Map<String, Object> m = new LinkedHashMap<>();
        Resp r = doGet(detailApi(seance, CENTRE_A), adminA());
        m.put("01 detail ADMIN", reponse(r));
        m.put("02 en-tete Cache-Control", r.cacheControl());
        m.put("03 meme corps pour le MEDECIN, la SECRETAIRE et l'INFIRMIER", List.of(
                doGet(detailApi(seance, CENTRE_A), medecinA()).raw().equals(r.raw()),
                doGet(detailApi(seance, CENTRE_A), secretaireA()).raw().equals(r.raw()),
                doGet(detailApi(seance, CENTRE_A), infirmierA()).raw().equals(r.raw())));
        arbre("detail", m);
    }

    @Test
    void le_detail_enrichit_le_generateur_du_patient_depuis_le_module_de_maintenance_et_garde_l_identifiant_sinon() throws Exception {
        petitDecor();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        jdbc.update("UPDATE patients SET salle_id = ?, position_id = ?, generateur_id = ? WHERE id = ?", salle1, matin, g1a, patient);
        UUID inconnu = patientCouvert(CENTRE_A, "PAT_GEN_INCONNU");
        jdbc.update("UPDATE patients SET generateur_id = ? WHERE id = ?", idStable("generateur", "supprime"), inconnu);
        UUID s1 = seanceEnBase(CENTRE_A, patient, "S1", fixe(1), "CREE");
        UUID s2 = seanceEnBase(CENTRE_A, inconnu, "S2", fixe(1), "CREE");
        Resp creation = doPost(SEANCES_API, corps("centerId", CENTRE_A, "patientId", patient, "dateSeance", fixe(2)), infirmierA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 detail du patient place (generateur en service)", corps(doGet(detailApi(s1, CENTRE_A), adminA())).path("patient"));
        m.put("02 detail du patient dont le generateur est inconnu de la maintenance", corps(doGet(detailApi(s2, CENTRE_A), adminA())).path("patient"));
        m.put("03 la creation renvoie aussi les champs du generateur", resumeGenerateur(creation));
        arbre("generateur", m);
    }

    private Map<String, Object> resumeGenerateur(Resp r) {
        JsonNode c = corps(r);
        Map<String, Object> m = new LinkedHashMap<>();
        for (String champ : List.of("generateurId", "generateurNom", "generateurMarque", "generateurEtat")) {
            m.put(champ, c.path(champ).isNull() ? null : c.path(champ).asString());
        }
        return m;
    }

    @Test
    void le_detail_refuse_en_400_une_seance_inconnue_ou_d_un_autre_centre_et_en_403_un_autre_centre_demande() throws Exception {
        UUID patientA = patientCouvert(CENTRE_A, "PAT_A");
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        UUID seanceA = seanceEnBase(CENTRE_A, patientA, "SEANCE_A", fixe(1), "CREE");
        UUID seanceB = seanceEnBase(CENTRE_B, patientB, "SEANCE_B", fixe(1), "CREE");
        UUID inconnue = idStable("seance", "inconnue");
        aliasNommes.put(inconnue.toString(), "<SEANCE_INCONNUE>");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 seance inconnue", refus(doGet(detailApi(inconnue, CENTRE_A), adminA())));
        m.put("02 seance du centre B lue avec centerId A", refus(doGet(detailApi(seanceB, CENTRE_A), adminA())));
        m.put("03 seance du centre B lue avec centerId B par la session A", refus(doGet(detailApi(seanceB, CENTRE_B), adminA())));
        m.put("04 seance du centre A lue avec centerId B par la session A", refus(doGet(detailApi(seanceA, CENTRE_B), adminA())));
        m.put("05 seance du centre B lue par la session B", reponse(doGet(detailApi(seanceB, CENTRE_B), adminB())).get("status"));
        m.put("06 sans centerId", refus(doGet(SEANCES_API + "/" + seanceA, adminA())));
        m.put("07 centerId illisible", refus(doGet(SEANCES_API + "/" + seanceA + "?centerId=abc", adminA())));
        m.put("08 identifiant de seance illisible", refus(doGet(SEANCES_API + "/abc?centerId=" + CENTRE_A, adminA())));
        m.put("09 session sans centre", refus(doGet(detailApi(seanceA, CENTRE_A), as(null, "ADMIN"))));
        arbre("detail_refus_nouveau_code", m);
    }

    @Test
    void la_liste_est_triee_par_date_decroissante_et_ne_montre_que_le_centre_de_la_session_avec_le_forfait_de_chaque_seance() throws Exception {
        jeuDeSeances();
        Map<String, Object> m = new LinkedHashMap<>();
        Resp r = doGet(SEANCES_API + "?centerId=" + CENTRE_A, adminA());
        m.put("01 liste par defaut (page 0, taille 20, date decroissante, departage par creation puis identifiant)", refusOuListe(r));
        m.put("02 premiere ligne en entier (forfait, dates, drapeaux)", corps(r).path("items").get(0));
        m.put("03 centre B demande par la session A", refus(doGet(SEANCES_API + "?centerId=" + CENTRE_B, adminA())));
        m.put("04 centre B par sa propre session", refusOuListe(doGet(SEANCES_API + "?centerId=" + CENTRE_B, adminB())));
        m.put("05 sans centerId", refus(doGet(SEANCES_API, adminA())));
        arbre("liste_nouveau_code", m);
    }

    @Test
    void les_filtres_de_la_liste_periode_statuts_texte_et_deverrouillage() throws Exception {
        jeuDeSeances();
        jdbc.update("UPDATE seances SET regularisation_deverrouillee_at = ? WHERE id = ?", CREATION_FIXE, idStable("seance", "A1-" + CENTRE_A));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 from=<D+2>", liste("&from=" + fixe(2)));
        m.put("02 to=<D+2>", liste("&to=" + fixe(2)));
        m.put("03 from et to egaux", liste("&from=" + fixe(2) + "&to=" + fixe(2)));
        m.put("04 from apres to", liste("&from=" + fixe(3) + "&to=" + fixe(1)));
        m.put("05 from illisible", liste("&from=pas-une-date"));
        m.put("06 status=CREE", liste("&status=CREE"));
        m.put("07 status=cree,signee (casse et liste)", liste("&status=cree,signee"));
        m.put("08 status=FACTUREE,ABSENT", liste("&status=FACTUREE,ABSENT"));
        m.put("09 status inconnu", liste("&status=EN_COURS"));
        m.put("10 status vide", liste("&status="));
        m.put("11 q=alpha", liste("&q=alpha"));
        m.put("12 q=ALPHA test (deux mots)", liste("&q=ALPHA%20test"));
        m.put("13 q=SE1-BRAVO (code)", liste("&q=SE1-BRAVO"));
        m.put("14 q=% (joker echappe)", liste("&q=%25"));
        m.put("15 q=inconnu", liste("&q=inconnu"));
        m.put("16 deverrouillee=true", liste("&deverrouillee=true"));
        m.put("17 deverrouillee=false", liste("&deverrouillee=false"));
        m.put("18 deverrouillee illisible", liste("&deverrouillee=peut-etre"));
        arbre("filtres_nouveau_code", m);
    }

    /**
     * LEC-PAT (iii), filet complémentaire posé AVANT la lecture par le langage publié : homonymes (départage par création décroissante puis
     * identifiant), patient transféré ou décédé (l'état n'exclut rien), patient sans code (ordre des valeurs nulles), prénom accentué
     * (filtre de texte insensible à la casse), séance d'un centre rattachée à un patient d'un AUTRE centre (absente), séance sans patient.
     */
    @Test
    void l_historique_departage_les_homonymes_et_ne_montre_que_les_patients_du_centre() throws Exception {
        UUID martin1 = patientCouvert(CENTRE_A, "MARTIN1");
        UUID martin2 = patientCouvert(CENTRE_A, "MARTIN2");
        jdbc.update("UPDATE patients SET nom = 'Martin', prenom = 'Paul' WHERE id IN (?, ?)", martin1, martin2);
        UUID dupont = patientCouvert(CENTRE_A, "DUPONT");
        jdbc.update("UPDATE patients SET nom = 'Dupont', prenom = 'Anne', etat_patient = 'TRANSFERE', date_evenement_etat = ? WHERE id = ?",
                java.sql.Date.valueOf(fixe(1)), dupont);
        UUID decede = patientCouvert(CENTRE_A, "DECEDE");
        jdbc.update("UPDATE patients SET nom = 'Zeta', prenom = 'Zoe', etat_patient = 'DECEDE', date_evenement_etat = ? WHERE id = ?",
                java.sql.Date.valueOf(fixe(1)), decede);
        UUID sansCode = patientCouvert(CENTRE_A, "SANSCODE");
        jdbc.update("UPDATE patients SET nom = 'Aubert', prenom = 'Élodie', code_patient = NULL WHERE id = ?", sansCode);
        UUID etranger = patientCouvert(CENTRE_B, "ETRANGER");
        seanceEnBase(CENTRE_A, martin1, "M1A", fixe(1), "CREE");
        seanceEnBase(CENTRE_A, martin1, "M1B", fixe(2), "VALIDEE");
        seanceEnBase(CENTRE_A, martin2, "M2A", fixe(1), "CREE");
        seanceEnBase(CENTRE_A, dupont, "D1", fixe(1), "SIGNEE");
        seanceEnBase(CENTRE_A, decede, "Z1", fixe(3), "FACTUREE");
        seanceEnBase(CENTRE_A, sansCode, "S1", fixe(2), "ABSENT");
        seanceEnBase(CENTRE_A, etranger, "E1", fixe(1), "CREE");
        seanceEnBase(CENTRE_A, idStable("patient", "disparu"), "X1", fixe(1), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 patient asc (homonymes departages par creation puis identifiant)", liste("&sortBy=patient&sortDir=asc"));
        m.put("02 patient desc", liste("&sortBy=patient&sortDir=desc"));
        m.put("03 code asc (code nul)", liste("&sortBy=code&sortDir=asc"));
        m.put("04 code desc (code nul)", liste("&sortBy=code&sortDir=desc"));
        m.put("05 q=martin", liste("&q=martin"));
        m.put("06 q=paul martin", liste("&q=paul%20martin"));
        m.put("07 q=ELODIE accentue en majuscules", liste("&q=%C3%89LODIE"));
        m.put("08 q=SE1-DUPONT (patient transfere)", liste("&q=SE1-DUPONT"));
        m.put("09 q=etranger (patient d'un autre centre)", liste("&q=ETRANGER"));
        m.put("10 patient asc page=1 size=2", liste("&sortBy=patient&sortDir=asc&page=1&size=2"));
        m.put("11 patient asc page=2 size=2", liste("&sortBy=patient&sortDir=asc&page=2&size=2"));
        m.put("12 patient desc statut CREE,VALIDEE", liste("&sortBy=patient&status=CREE,VALIDEE"));
        arbre("homonymes_et_patients_hors_centre", m);
    }

    @Test
    void les_tris_et_la_pagination_de_la_liste_sont_bornes_sans_erreur() throws Exception {
        jeuDeSeances();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 sortBy=patient asc", liste("&sortBy=patient&sortDir=asc"));
        m.put("02 sortBy=code desc", liste("&sortBy=code"));
        m.put("03 sortBy=status asc", liste("&sortBy=statut&sortDir=asc"));
        m.put("04 sortBy=createdAt (egalite, departage par identifiant)", liste("&sortBy=createdAt&sortDir=asc"));
        m.put("05 sortBy inconnu (date)", liste("&sortBy=n_importe_quoi&sortDir=asc"));
        m.put("06 sortDir inconnu (decroissant)", liste("&sortDir=bizarre"));
        m.put("07 page=1 size=2", liste("&page=1&size=2"));
        m.put("08 page au-dela du total", liste("&page=9&size=2"));
        m.put("09 size=0 (ramene a 1)", liste("&size=0"));
        m.put("10 size negatif (ramene a 1)", liste("&size=-5"));
        m.put("11 size=100000 (plafonne)", liste("&size=100000").replaceAll(" \\[.*", " [..]"));
        m.put("12 page negative (ramenee a 0)", liste("&page=-3&size=2"));
        m.put("13 size illisible", liste("&size=abc"));
        arbre("tris_et_pagination_nouveau_code", m);
    }
}
