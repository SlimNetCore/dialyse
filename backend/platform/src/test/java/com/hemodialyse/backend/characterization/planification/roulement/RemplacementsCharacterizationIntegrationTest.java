package com.hemodialyse.backend.characterization.planification.roulement;

import com.hemodialyse.backend.characterization.planification.RoulementCharacterizationSupport;
import com.hemodialyse.backend.characterization.planification.JourSemaine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filet de caractérisation P3 : remplacements ponctuels ({@code POST|DELETE /api/v1/infirmiers/presence/remplacements}) et leurs
 * effets sur la présence, les remplaçants proposés et la charge (lectures de P4). Éligibilité : ni absent, ni déjà prévu sur le
 * créneau (dans une salle quelconque), habilité si la salle est d'isolement, infirmier actif du centre.
 */
class RemplacementsCharacterizationIntegrationTest extends RoulementCharacterizationSupport {

    private static final String REMPLACEMENTS = INFIRMIERS + "/presence/remplacements";
    private UUID amrani;
    private UUID benali;
    private UUID chikhi;
    private UUID dahmani;
    private UUID duCentreB;

    @Override
    protected String theme() {
        return "p3-roulement/remplacements";
    }

    @BeforeEach
    void amorcer() {
        decor();
        amrani = inf(CENTRE_A, "INF_AMRANI", "Amrani", "Sara", true);
        benali = inf(CENTRE_A, "INF_BENALI", "Benali", "Karim", false);
        chikhi = fiche(CENTRE_A, "INF_CHIKHI", "CHIKHI", "Chikhi", "Nadia", null, "INFIRMIER", false, false, null);
        dahmani = inf(CENTRE_A, "INF_DAHMANI", "Dahmani", "Yanis", false);
        duCentreB = inf(CENTRE_B, "INF_CENTRE_B", "Zerrouki", "Rania", false);
        semaine(1);
    }

    private LocalDate s(JourSemaine jour) {
        return semaine(1, jour);
    }

    private Resp affecter(LocalDate date, UUID salle, UUID creneau, UUID infirmier, UUID remplace) throws Exception {
        return doPost(REMPLACEMENTS, json(corpsRemplacement(date, salle, creneau, infirmier, remplace)), adminA());
    }

    private String remplacantsUrl(LocalDate date, UUID salle, UUID creneau) {
        return INFIRMIERS + "/presence/remplacants?date=" + date + "&salleId=" + salle + "&creneauId=" + creneau;
    }

    @Test
    void un_remplacant_eligible_est_affecte_a_la_case_et_apparait_dans_la_presence_la_liste_des_remplacants_et_la_charge() throws Exception {
        patientsPlaces(CENTRE_A, "PAT", 4, salle1, matin, JourSemaine.LUNDI);
        affectation(CENTRE_A, "R_AMRANI", amrani, salle1, matin, "LUNDI");
        LocalDate lundi = s(JourSemaine.LUNDI);
        lecture("remplacants_avant", doGet(remplacantsUrl(lundi, salle1, matin), adminA()));
        int avant = remplacementsDansLaCharge(lundi, "Karim Benali");
        Resp r = affecter(lundi, salle1, matin, benali, null);
        assertEquals(201, r.status());
        ecriture("affectation_nominale", r);
        etatRemplacements("affectation_nominale_base");
        arbre("presence_apres", casesDePresence(presenceDeLaSemaine(1, adminA())));
        lecture("remplacants_apres", doGet(remplacantsUrl(lundi, salle1, matin), adminA()));
        assertEquals(avant + 1, remplacementsDansLaCharge(lundi, "Karim Benali"));
        assertEquals(0, diffusions.size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE center_id = ?", Integer.class, CENTRE_A));
    }

    @Test
    void le_remplace_nomme_doit_etre_absent_ou_prevu_sur_la_case_sinon_422_KB_PLN_27() throws Exception {
        affectation(CENTRE_A, "R_AMRANI", amrani, salle1, matin, "LUNDI,MARDI");
        absence(CENTRE_A, "ABS_AMRANI", amrani, s(JourSemaine.LUNDI), s(JourSemaine.LUNDI), "MALADIE", null);
        Resp absent = affecter(s(JourSemaine.LUNDI), salle1, matin, benali, amrani);
        Resp prevu = affecter(s(JourSemaine.MARDI), salle1, matin, benali, amrani);
        Resp ni_l_un_ni_l_autre = affecter(s(JourSemaine.MERCREDI), salle1, matin, benali, amrani);
        assertEquals(201, absent.status());
        assertEquals(201, prevu.status());
        assertEquals(422, ni_l_un_ni_l_autre.status());
        ecriture("remplace_un_infirmier_absent", absent);
        ecriture("remplace_un_infirmier_prevu_sur_la_case", prevu);
        ecriture("remplace_un_infirmier_non_concerne_refuse", ni_l_un_ni_l_autre);
        etatRemplacements("remplace_base");
        arbre("presence_remplace", casesDePresence(presenceDeLaSemaine(1, adminA())));
    }

    @Test
    void l_eligibilite_exclut_l_absent_le_deja_prevu_sur_le_creneau_l_inactif_l_inconnu_l_autre_centre_et_le_non_habilite_en_isolement() throws Exception {
        UUID absent = inf(CENTRE_A, "INF_ABSENT", "Absent", "Aziz", false);
        UUID prevuAutreSalle = inf(CENTRE_A, "INF_PREVU_AUTRE", "Prevu", "Autre", false);
        UUID prevuMemeSalle = inf(CENTRE_A, "INF_PREVU_MEME", "Prevu", "Meme", false);
        UUID dejaRemplacant = inf(CENTRE_A, "INF_REMPLACANT", "Remplacant", "Deja", false);
        UUID doubleVacation = inf(CENTRE_A, "INF_DOUBLE", "Double", "Vacation", false);
        UUID habilite = inf(CENTRE_A, "INF_HABILITE", "Habilite", "Iso", true);
        UUID inconnu = UUID.nameUUIDFromBytes("inconnu".getBytes());
        aliasNommes.put(inconnu.toString(), "<INCONNU>");
        LocalDate lundi = s(JourSemaine.LUNDI);
        absence(CENTRE_A, "ABS_1", absent, lundi, lundi, "CONGE", null);
        affectation(CENTRE_A, "R_AUTRE", prevuAutreSalle, salle2, matin, "LUNDI");
        affectation(CENTRE_A, "R_MEME", prevuMemeSalle, salle1, matin, "LUNDI");
        remplacement(CENTRE_A, "REMPL_EXISTANT", lundi, salle2, matin, dejaRemplacant, null);
        affectation(CENTRE_A, "R_DOUBLE", doubleVacation, salle1, apresMidi, "LUNDI");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("absent ce jour", statutEtCode(affecter(lundi, salle1, matin, absent, null)));
        r.put("prévu sur le créneau dans une autre salle", statutEtCode(affecter(lundi, salle1, matin, prevuAutreSalle, null)));
        r.put("prévu sur le créneau dans la même salle", statutEtCode(affecter(lundi, salle1, matin, prevuMemeSalle, null)));
        r.put("déjà remplaçant sur le créneau (autre salle)", statutEtCode(affecter(lundi, salle1, matin, dejaRemplacant, null)));
        r.put("déjà remplaçant sur la même case (doublon)", statutEtCode(affecter(lundi, salle2, matin, dejaRemplacant, null)));
        r.put("infirmier désactivé", statutEtCode(affecter(lundi, salle1, matin, chikhi, null)));
        r.put("infirmier inconnu", statutEtCode(affecter(lundi, salle1, matin, inconnu, null)));
        r.put("infirmier d'un autre centre", statutEtCode(affecter(lundi, salle1, matin, duCentreB, null)));
        r.put("non habilité en salle d'isolement", statutEtCode(affecter(lundi, salleIsolement, matin, dahmani, null)));
        r.put("habilité en salle d'isolement", statutEtCode(affecter(lundi, salleIsolement, matin, habilite, null)));
        r.put("déjà prévu sur l'autre créneau (double vacation)", statutEtCode(affecter(lundi, salle1, matin, doubleVacation, null)));
        r.put("l'absent un autre jour", statutEtCode(affecter(s(JourSemaine.MARDI), salle1, matin, absent, null)));
        r.put("le prévu sur le même créneau un autre jour", statutEtCode(affecter(s(JourSemaine.MARDI), salle1, matin, prevuMemeSalle, null)));
        arbre("eligibilite", r);
        etatRemplacements("eligibilite_base");
        ecriture("ineligible_absent", affecter(lundi, salle1, matin, absent, null));
        ecriture("ineligible_deja_prevu", affecter(lundi, salle1, matin, prevuMemeSalle, null));
    }

    @Test
    void un_jour_ferme_du_centre_ou_une_date_passee_refusent_l_affectation_une_date_future_lointaine_non_KB_PLN_28() throws Exception {
        jourFerme(CENTRE_A, s(JourSemaine.MERCREDI), "Jour férié");
        LocalDate passe = alias(LocalDate.of(2020, 1, 6), "<PASSE_LOINTAIN>");
        LocalDate futur = alias(aujourdhui().plusYears(10).withMonth(1).withDayOfMonth(6), "<FUTUR_LOINTAIN>");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("jour férié du centre", statutEtCode(affecter(s(JourSemaine.MERCREDI), salle1, matin, benali, null)));
        r.put("date passée lointaine", statutEtCode(affecter(passe, salle1, matin, benali, null)));
        r.put("date future lointaine", statutEtCode(affecter(futur, salle1, matin, benali, null)));
        arbre("jour_ferme_et_dates_lointaines", r);
        etatRemplacements("jour_ferme_et_dates_lointaines_base");
        arbre("presence_jour_ferme", casesDePresence(presenceDeLaSemaine(1, adminA())));
    }

    @Test
    void l_ordre_des_controles_est_salle_creneau_infirmier_remplace_puis_eligibilite_puis_soi_meme() throws Exception {
        UUID inconnu = UUID.nameUUIDFromBytes("inconnu".getBytes());
        aliasNommes.put(inconnu.toString(), "<INCONNU>");
        LocalDate lundi = s(JourSemaine.LUNDI);
        absence(CENTRE_A, "ABS_BENALI", benali, lundi, lundi, "CONGE", null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("salle inconnue + créneau inconnu + infirmier inconnu", statutEtCode(affecter(lundi, inconnu, inconnu, inconnu, inconnu)));
        r.put("salle d'un autre centre", statutEtCode(affecter(lundi, salleB, matin, amrani, null)));
        r.put("créneau d'un autre centre", statutEtCode(affecter(lundi, salle1, creneauB, amrani, null)));
        r.put("remplacé inconnu + infirmier inéligible", statutEtCode(affecter(lundi, salle1, matin, benali, inconnu)));
        r.put("remplacé d'un autre centre", statutEtCode(affecter(lundi, salle1, matin, amrani, duCentreB)));
        r.put("remplacé désactivé et non concerné (refusé)", statutEtCode(affecter(lundi, salle1, matin, amrani, chikhi)));
        r.put("soi-même, inéligible (400 en premier)", statutEtCode(affecter(lundi, salle1, matin, benali, benali)));
        r.put("soi-même, éligible (400)", statutEtCode(affecter(lundi, salle1, matin, dahmani, dahmani)));
        arbre("ordre_des_controles", r);
        etatRemplacements("ordre_des_controles_base");
    }

    @Test
    void les_demandes_mal_formees_sont_refusees_avant_toute_ecriture() throws Exception {
        LocalDate lundi = s(JourSemaine.LUNDI);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("corps {}", statutEtCode(doPost(REMPLACEMENTS, "{}", adminA())));
        r.put("date absente", statutEtCode(doPost(REMPLACEMENTS, json("{'salleId':'" + salle1 + "','creneauId':'" + matin + "','infirmierId':'" + benali + "'}"), adminA())));
        r.put("salleId absent", statutEtCode(doPost(REMPLACEMENTS, json("{'date':'" + lundi + "','creneauId':'" + matin + "','infirmierId':'" + benali + "'}"), adminA())));
        r.put("creneauId absent", statutEtCode(doPost(REMPLACEMENTS, json("{'date':'" + lundi + "','salleId':'" + salle1 + "','infirmierId':'" + benali + "'}"), adminA())));
        r.put("infirmierId absent", statutEtCode(doPost(REMPLACEMENTS, json("{'date':'" + lundi + "','salleId':'" + salle1 + "','creneauId':'" + matin + "'}"), adminA())));
        r.put("date illisible", statutEtCode(doPost(REMPLACEMENTS, json("{'date':'demain','salleId':'" + salle1 + "','creneauId':'" + matin + "','infirmierId':'" + benali + "'}"), adminA())));
        r.put("identifiant illisible", statutEtCode(doPost(REMPLACEMENTS, json("{'date':'" + lundi + "','salleId':'x','creneauId':'" + matin + "','infirmierId':'" + benali + "'}"), adminA())));
        r.put("JSON illisible", statutEtCode(doPost(REMPLACEMENTS, "{", adminA())));
        r.put("remplaceId null", statutEtCode(doPost(REMPLACEMENTS, json("{'date':'" + lundi + "','salleId':'" + salle1 + "','creneauId':'" + matin + "','infirmierId':'" + benali + "','remplaceId':null}"), adminA())));
        r.put("champ inconnu ignoré (mais doublon du remplacement posé par remplaceId null)", statutEtCode(doPost(REMPLACEMENTS, json("{'date':'" + lundi + "','salleId':'" + salle1 + "','creneauId':'" + matin + "','infirmierId':'" + benali + "','x':1}"), adminA())));
        arbre("demandes_mal_formees", r);
        etatRemplacements("demandes_mal_formees_base");
    }

    @Test
    void l_annulation_retire_le_remplacement_et_rend_le_remplacant_de_nouveau_eligible() throws Exception {
        LocalDate lundi = s(JourSemaine.LUNDI);
        UUID id = remplacement(CENTRE_A, "REMPL_1", lundi, salle1, matin, benali, null);
        UUID autre = remplacement(CENTRE_A, "REMPL_AUTRE_CENTRE", lundi, salleB, creneauB, duCentreB, null);
        jdbc.update("UPDATE infirmier_remplacement SET center_id = ? WHERE id = ?", CENTRE_B, autre);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("inconnu", statutEtCode(doDelete(REMPLACEMENTS + "/" + UUID.nameUUIDFromBytes("inconnu".getBytes()), adminA())));
        r.put("d'un autre centre", statutEtCode(doDelete(REMPLACEMENTS + "/" + autre, adminA())));
        r.put("identifiant illisible", statutEtCode(doDelete(REMPLACEMENTS + "/pas-un-uuid", adminA())));
        r.put("nominale", statutEtCode(doDelete(REMPLACEMENTS + "/" + id, adminA())));
        r.put("deuxième annulation", statutEtCode(doDelete(REMPLACEMENTS + "/" + id, adminA())));
        r.put("de nouveau éligible", statutEtCode(affecter(lundi, salle1, matin, benali, null)));
        arbre("annulation_regles", r);
        etatRemplacements("annulation_base");
        diffusionsCapturees("annulation_diffusions");
    }

    @Test
    void un_remplacant_devenu_absent_cesse_d_etre_compte_mais_son_remplacement_reste_enregistre() throws Exception {
        patientsPlaces(CENTRE_A, "PAT", 1, salle1, matin, JourSemaine.LUNDI);
        LocalDate lundi = s(JourSemaine.LUNDI);
        remplacement(CENTRE_A, "REMPL_BENALI", lundi, salle1, matin, benali, null);
        arbre("presence_remplacant_present", casesDePresence(presenceDeLaSemaine(1, adminA())));
        doPost(INFIRMIERS + "/absences", json(corpsAbsence(benali, lundi, lundi, "MALADIE", null)), adminA());
        arbre("presence_remplacant_devenu_absent", casesDePresence(presenceDeLaSemaine(1, adminA())));
        assertEquals(1, nombreDe("infirmier_remplacement"));
        int jours = 0;
        for (var i : corps(doGet(INFIRMIERS + "/presence/charge?mois=" + lundi.toString().substring(0, 7) + "&size=100", adminA())).path("items")) {
            if ("Karim Benali".equals(i.path("nom").asString())) jours = i.path("joursAbsence").asInt();
        }
        assertEquals(1, jours);
    }

    @Test
    void deux_affectations_simultanees_du_meme_remplacant_sur_le_meme_creneau_ne_creent_jamais_deux_lignes_grace_a_l_unicite_KB_PLN_29() throws Exception {
        LocalDate lundi = s(JourSemaine.LUNDI);
        int deux422 = 0;
        int un500 = 0;
        for (int tour = 0; tour < 6; tour++) {
            jdbc.update("DELETE FROM infirmier_remplacement WHERE center_id = ?", CENTRE_A);
            List<Resp> reponses = enParallele(
                    () -> affecter(lundi, salle1, matin, benali, null),
                    () -> affecter(lundi, salle2, matin, benali, null));
            List<Integer> statuts = new ArrayList<>(reponses.stream().map(Resp::status).toList());
            statuts.sort(Integer::compare);
            assertEquals(201, statuts.get(0).intValue(), statuts.toString());
            assertTrue(statuts.get(1) == 422 || statuts.get(1) == 500, statuts.toString());
            assertEquals(1, nombreDe("infirmier_remplacement"));
            if (statuts.get(1) == 422) deux422++;
            else un500++;
        }
        System.out.println("[KB-PLN-29] deux remplacements simultanes du meme remplacant : 201+422 " + deux422 + " fois, 201+500 " + un500 + " fois sur 6");
    }

}
