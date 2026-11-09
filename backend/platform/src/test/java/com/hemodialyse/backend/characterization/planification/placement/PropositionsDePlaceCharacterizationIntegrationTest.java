package com.hemodialyse.backend.characterization.planification.placement;

import com.hemodialyse.backend.characterization.planification.PlacementCharacterizationSupport;
import com.hemodialyse.backend.characterization.planification.JourSemaine;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.characterization.planification.JourSemaine.LUNDI;
import static com.hemodialyse.backend.characterization.planification.JourSemaine.MARDI;
import static com.hemodialyse.backend.characterization.planification.JourSemaine.MERCREDI;
import static com.hemodialyse.backend.characterization.planification.JourSemaine.VENDREDI;

/**
 * Filet de caractérisation P5 : {@code GET /api/v1/planning/affectations} (aide au placement : propositions de place classées de 0 à
 * 100, grille de disponibilité, alertes). Lecture seule, bornée au centre. Point d'observation : HTTP, contexte Spring complet, H2.
 */
class PropositionsDePlaceCharacterizationIntegrationTest extends PlacementCharacterizationSupport {

    @Override
    protected String theme() {
        return "p5-placement/propositions";
    }

    private Resp proposer(String requete) throws Exception {
        return doGet(AFFECTATIONS + requete, adminA());
    }

    private void projeter(String name, String requete) throws Exception {
        arbre(name, projectionDesPropositions(proposer(requete)));
    }

    // ------------------------------------------------------------------ forme et défauts

    @Test
    void reponse_complete_d_un_centre_a_une_salle_un_creneau_et_deux_generateurs() throws Exception {
        UUID s = salle(CENTRE_A, "SALLE_UNIQUE", "Salle unique", "NON");
        UUID c = creneau(CENTRE_A, "CRENEAU_UNIQUE", "Matin");
        UUID ga = generateur(CENTRE_A, "GA", s, "EN_SERVICE");
        generateur(CENTRE_A, "GB", s, "EN_SERVICE");
        place(CENTRE_A, "PAT_1", s, c, ga, LUNDI, MERCREDI, VENDREDI);
        lecture("reponse_complete", proposer(""));
    }

    @Test
    void sans_parametre_trois_seances_et_douze_propositions_au_plus() throws Exception {
        decorDePlacement();
        Resp r = proposer("");
        arbre("defauts", projectionDesPropositions(r));
        arbre("defauts_en_tetes", Map.of("statut", r.status(), "cacheControl", String.valueOf(r.cacheControl()),
                "contentType", String.valueOf(r.contentType())));
    }

    @Test
    void le_nombre_de_seances_par_semaine_change_les_jours_proposes_et_le_score() throws Exception {
        decorDePlacement();
        Map<String, Object> m = new LinkedHashMap<>();
        for (int n : new int[]{1, 2, 3, 4, 5, 6, 7}) {
            m.put("seances=" + n, projectionDesPropositions(proposer("?seances=" + n + "&limite=3")));
        }
        arbre("seances_de_1_a_7", m);
    }

    @Test
    void un_nombre_de_seances_hors_de_1_a_7_ou_illisible_est_refuse() throws Exception {
        decorDePlacement();
        Map<String, Object> m = new LinkedHashMap<>();
        for (String s : new String[]{"0", "8", "-1", "abc", "2.5", "99999999999"}) {
            m.put("seances=" + s, statutEtCode(proposer("?seances=" + s)));
        }
        arbre("seances_invalides", m);
    }

    @Test
    void les_jours_imposes_remplacent_le_nombre_de_seances() throws Exception {
        decorDePlacement();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jours=LUNDI,MERCREDI,VENDREDI", projectionDesPropositions(proposer("?jours=LUNDI,MERCREDI,VENDREDI&limite=4")));
        m.put("jours=MARDI&seances=5", projectionDesPropositions(proposer("?jours=MARDI&seances=5&limite=2")));
        m.put("jours repetes", projectionDesPropositions(proposer("?jours=LUNDI&jours=LUNDI&jours=JEUDI&limite=2")));
        m.put("jours=lundi (minuscule)", statutEtCode(proposer("?jours=lundi")));
        m.put("jours=FUNDI", statutEtCode(proposer("?jours=FUNDI")));
        m.put("jours vide", projectionDesPropositions(proposer("?jours=&seances=1&limite=1")));
        arbre("jours_imposes", m);
    }

    @Test
    void la_limite_est_bornee_de_1_a_30() throws Exception {
        decorDePlacement();
        Map<String, Object> m = new LinkedHashMap<>();
        for (String l : new String[]{"0", "1", "5", "30", "31", "100", "-3"}) {
            JsonNode c = corps(proposer("?seances=1&limite=" + l));
            m.put("limite=" + l, c.path("propositions").size());
        }
        m.put("limite=abc", statutEtCode(proposer("?limite=abc")));
        arbre("limite", m);
    }

    @Test
    void au_plus_deux_propositions_par_salle_et_creneau() throws Exception {
        decorDePlacement();
        JsonNode c = corps(proposer("?seances=1&limite=30"));
        Map<String, Integer> parCase = new LinkedHashMap<>();
        c.path("propositions").forEach(p -> parCase.merge(p.path("salle").path("nom").asString() + " / "
                + p.path("creneau").path("libelle").asString(), 1, Integer::sum));
        arbre("deux_par_salle_et_creneau", parCase);
    }

    // ------------------------------------------------------------------ créneau et salle souhaités

    @Test
    void le_creneau_et_la_salle_souhaites_ajoutent_des_points_et_des_raisons() throws Exception {
        decorDePlacement();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("creneau souhaite", projectionDesPropositions(proposer("?seances=3&limite=3&creneauId=" + apresMidi)));
        m.put("salle souhaitee", projectionDesPropositions(proposer("?seances=3&limite=3&salleId=" + salle2)));
        m.put("les deux", projectionDesPropositions(proposer("?seances=3&limite=3&creneauId=" + apresMidi + "&salleId=" + salle2)));
        m.put("creneau inconnu", projectionDesPropositions(proposer("?seances=3&limite=2&creneauId=" + UUID.nameUUIDFromBytes("x".getBytes()))));
        m.put("salle d'isolement souhaitee par un patient sans risque", projectionDesPropositions(
                proposer("?seances=3&limite=3&salleId=" + salleIsolement)));
        m.put("creneau illisible", statutEtCode(proposer("?creneauId=pas-un-uuid")));
        arbre("creneau_et_salle_souhaites", m);
    }

    // ------------------------------------------------------------------ occupation des générateurs

    @Test
    void un_generateur_occupe_un_jour_et_un_creneau_n_est_plus_propose_ce_jour_la() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI, MERCREDI);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lundi mercredi matin salle 1", projectionDesPropositions(
                proposer("?jours=LUNDI,MERCREDI&limite=30&salleId=" + salle1 + "&creneauId=" + matin)));
        m.put("mardi matin salle 1", projectionDesPropositions(proposer("?jours=MARDI&limite=30&salleId=" + salle1)));
        arbre("generateur_occupe", m);
    }

    @Test
    void une_salle_dont_tous_les_generateurs_sont_occupes_n_a_plus_de_place_ce_jour_la() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        place(CENTRE_A, "PAT_2", salle1, matin, g1b, LUNDI);
        Resp r = proposer("?jours=LUNDI&limite=30");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("propositions lundi", projectionDesPropositions(r));
        m.put("grille occupee", grilleOccupee(r));
        arbre("salle_pleine", m);
    }

    @Test
    void un_patient_sans_generateur_precis_prend_une_place_de_la_salle() throws Exception {
        decorDePlacement();
        patientPlace(CENTRE_A, "PAT_SANS_GEN_1", salle1, matin, LUNDI);
        patientPlace(CENTRE_A, "PAT_SANS_GEN_2", salle1, matin, LUNDI);
        Resp r = proposer("?jours=LUNDI&limite=30");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("propositions", projectionDesPropositions(r));
        m.put("grille occupee", grilleOccupee(r));
        arbre("patients_sans_generateur", m);
    }

    @Test
    void un_patient_sur_un_generateur_d_une_autre_salle_ou_hors_service_prend_une_place_de_sa_salle() throws Exception {
        decorDePlacement();
        UUID hs = generateur(CENTRE_A, "G1HS", salle1, "HORS_SERVICE");
        place(CENTRE_A, "PAT_AUTRE_SALLE", salle1, matin, g2a, LUNDI);
        place(CENTRE_A, "PAT_HS", salle1, matin, hs, LUNDI);
        Resp r = proposer("?jours=LUNDI&limite=30");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("propositions", projectionDesPropositions(r));
        m.put("grille occupee", grilleOccupee(r));
        arbre("generateur_inconnu_de_la_salle", m);
    }

    @Test
    void seuls_les_generateurs_de_dialyse_en_service_rattaches_a_une_salle_sont_proposes() throws Exception {
        decorDePlacement();
        generateur(CENTRE_A, "G_REFORME", salle2, "REFORME");
        generateur(CENTRE_A, "G_MAINT", salle2, "EN_MAINTENANCE");
        generateur(CENTRE_A, "G_HS", salle2, "HORS_SERVICE");
        generateur(CENTRE_A, "G_SANS_SALLE", null, "EN_SERVICE");
        equipement(CENTRE_A, "E_EAU", "STATION_TRAITEMENT_EAU", salle2, "EN_SERVICE");
        UUID supprime = generateur(CENTRE_A, "G_SUPPRIME", salle2, "EN_SERVICE");
        jdbc.update("UPDATE gmao_equipements SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", supprime);
        Resp r = proposer("?seances=1&salleId=" + salle2 + "&limite=30");
        arbre("generateurs_ecartes", projectionDesPropositions(r));
    }

    @Test
    void un_patient_dont_on_modifie_le_placement_est_libere_dans_le_calcul() throws Exception {
        decorDePlacement();
        UUID p1 = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        place(CENTRE_A, "PAT_2", salle1, matin, g1b, LUNDI);
        Map<String, Object> m = new LinkedHashMap<>();
        String q = "?jours=LUNDI&limite=30&salleId=" + salle1 + "&creneauId=" + matin;
        m.put("sans patientId", projectionDesPropositions(proposer(q)));
        m.put("avec patientId", projectionDesPropositions(proposer(q + "&patientId=" + p1)));
        m.put("patientId inconnu", projectionDesPropositions(proposer(q + "&patientId=" + UUID.nameUUIDFromBytes("inconnu".getBytes()))));
        arbre("patient_libere", m);
    }

    @Test
    void les_patients_sans_creneau_ou_sans_jour_ne_tiennent_aucune_place() throws Exception {
        decorDePlacement();
        UUID sansJour = patientPlace(CENTRE_A, "PAT_SANS_JOUR", salle1, matin);
        UUID sansCreneau = place(CENTRE_A, "PAT_SANS_CRENEAU", salle1, matin, g1a, LUNDI);
        jdbc.update("UPDATE patients SET position_id = NULL WHERE id = ?", sansCreneau);
        arbre("patients_sans_place_effective", Map.of("sansJour", sansJour != null,
                "propositions lundi salle 1", projectionDesPropositions(proposer("?jours=LUNDI&salleId=" + salle1 + "&limite=30"))));
    }

    @Test
    void les_patients_sortis_liberent_leur_place_apres_leur_dernier_jour_et_le_sommeil_la_garde() throws Exception {
        decorDePlacement();
        UUID transfere = place(CENTRE_A, "PAT_TRANSFERE_AVANT", salle1, matin, g1a, LUNDI);
        etat(transfere, "TRANSFERE", jour(-3));
        UUID deces = place(CENTRE_A, "PAT_DECEDE_AUJOURDHUI", salle1, matin, g1b, MARDI);
        etat(deces, "DECEDE", jour(0));
        UUID transfereAujourdhui = place(CENTRE_A, "PAT_TRANSFERE_AUJOURDHUI", salle2, matin, g2a, LUNDI);
        etat(transfereAujourdhui, "TRANSFERE", jour(0));
        UUID guéri = place(CENTRE_A, "PAT_GUERI_SANS_DATE", salle2, matin, g2a, MARDI);
        etat(guéri, "GUERRI", null);
        UUID occasionnelPasse = place(CENTRE_A, "PAT_OCCASIONNEL_PASSE", salle1, apresMidi, g1a, MERCREDI);
        etat(occasionnelPasse, "OCCASIONNEL", jour(-2));
        UUID occasionnelFutur = place(CENTRE_A, "PAT_OCCASIONNEL_FUTUR", salle1, apresMidi, g1b, MERCREDI);
        etat(occasionnelFutur, "OCCASIONNEL", jour(5));
        UUID sommeil = place(CENTRE_A, "PAT_SOMMEIL", salle1, apresMidi, g1a, VENDREDI);
        jdbc.update("UPDATE patients SET en_sommeil = TRUE WHERE id = ?", sommeil);
        Resp r = proposer("?seances=1&limite=30&salleId=" + salle1);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("propositions", projectionDesPropositions(r));
        m.put("grille occupee", grilleOccupee(r));
        arbre("patients_sortis", m);
    }

    @Test
    void les_patients_et_generateurs_d_un_autre_centre_sont_ignores() throws Exception {
        decorDePlacement();
        generateur(CENTRE_B, "GB1", salleB, "EN_SERVICE");
        place(CENTRE_B, "PAT_B", salleB, creneauB, generateur(CENTRE_B, "GB2", salleB, "EN_SERVICE"), LUNDI);
        place(CENTRE_B, "PAT_B_SUR_G_A", salle1, matin, g1a, LUNDI);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centre A", projectionDesPropositions(proposer("?jours=LUNDI&limite=30&salleId=" + salle1)));
        m.put("centre B", projectionDesPropositions(doGet(AFFECTATIONS + "?jours=LUNDI&limite=30", adminB())));
        arbre("isolation_de_centre", m);
    }

    // ------------------------------------------------------------------ isolement

    @Test
    void le_risque_infectieux_force_ou_deduit_oriente_vers_la_salle_d_isolement() throws Exception {
        decorDePlacement();
        UUID p = patientPlace(CENTRE_A, "PAT_POSITIF", salle1, matin, LUNDI);
        UUID sain = patientPlace(CENTRE_A, "PAT_SAIN", salle1, matin, MARDI);
        serologie(CENTRE_A, p, "AG_HBS", "POSITIF", "2026-02-01");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("isolement=true", projectionDesPropositions(proposer("?isolement=true&seances=3&limite=3")));
        m.put("isolement=false", projectionDesPropositions(proposer("?isolement=false&seances=3&limite=2")));
        m.put("deduit patient positif", projectionDesPropositions(proposer("?patientId=" + p + "&jours=JEUDI&limite=3")));
        m.put("deduit patient sain", projectionDesPropositions(proposer("?patientId=" + sain + "&jours=JEUDI&limite=1")));
        m.put("force faux malgre sero positive", projectionDesPropositions(proposer("?patientId=" + p + "&isolement=false&jours=JEUDI&limite=1")));
        m.put("isolement illisible", statutEtCode(proposer("?isolement=peut-etre")));
        arbre("isolement", m);
    }

    @Test
    void les_quatre_marqueurs_a_risque_et_la_derniere_serologie_decident_du_risque() throws Exception {
        decorDePlacement();
        Map<String, UUID> patients = new LinkedHashMap<>();
        for (String marqueur : new String[]{"AG_HBS", "AC_VHC", "ARN_VHC", "VIH_AC", "AC_HBC"}) {
            UUID p = patientPlace(CENTRE_A, "PAT_" + marqueur, salle1, matin);
            serologie(CENTRE_A, p, marqueur, "POSITIF", "2026-02-01");
            patients.put(marqueur + " positif", p);
        }
        UUID gueri = patientPlace(CENTRE_A, "PAT_NEGATIVE_APRES", salle1, matin);
        serologie(CENTRE_A, gueri, "AG_HBS", "POSITIF", "2026-02-01");
        serologie(CENTRE_A, gueri, "AG_HBS", "NEGATIF", "2026-03-01");
        patients.put("positif puis negatif", gueri);
        UUID rechute = patientPlace(CENTRE_A, "PAT_POSITIF_APRES", salle1, matin);
        serologie(CENTRE_A, rechute, "AG_HBS", "NEGATIF", "2026-02-01");
        serologie(CENTRE_A, rechute, "AG_HBS", "POSITIF", "2026-03-01");
        patients.put("negatif puis positif", rechute);
        UUID deuxMarqueurs = patientPlace(CENTRE_A, "PAT_DEUX_MARQUEURS", salle1, matin);
        serologie(CENTRE_A, deuxMarqueurs, "AG_HBS", "POSITIF", "2026-02-01");
        serologie(CENTRE_A, deuxMarqueurs, "AC_VHC", "NEGATIF", "2026-03-01");
        patients.put("un marqueur positif, un autre negatif", deuxMarqueurs);
        UUID autreCentre = patientPlace(CENTRE_B, "PAT_AUTRE_CENTRE", salleB, creneauB, LUNDI);
        serologie(CENTRE_B, autreCentre, "VIH_AC", "POSITIF", "2026-02-01");
        patients.put("patient d'un autre centre", autreCentre);
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, UUID> e : patients.entrySet()) {
            JsonNode c = corps(proposer("?patientId=" + e.getValue() + "&seances=1&limite=1"));
            m.put(e.getKey(), c.path("patientARisque").asBoolean());
        }
        arbre("risque_deduit_des_serologies", m);
    }

    @Test
    void un_patient_a_risque_sans_aucune_salle_d_isolement_recoit_une_alerte() throws Exception {
        decorDePlacement();
        jdbc.update("UPDATE salle SET isolement = 'NON' WHERE center_id = ?", CENTRE_A);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("a risque", projectionDesPropositions(proposer("?isolement=true")));
        m.put("sans risque", projectionDesPropositions(proposer("?isolement=false&limite=1")));
        arbre("aucune_salle_d_isolement", m);
    }

    @Test
    void un_generateur_ne_melange_jamais_patients_a_risque_et_patients_sans_risque() throws Exception {
        decorDePlacement();
        generateur(CENTRE_A, "GISO2", salleIsolement, "EN_SERVICE");
        UUID sansRisque = place(CENTRE_A, "PAT_SAIN_EN_ISOLEMENT", salleIsolement, matin, giso, LUNDI);
        UUID aRisque = place(CENTRE_A, "PAT_POSITIF_EN_SALLE_1", salle1, matin, g1a, MARDI);
        serologie(CENTRE_A, aRisque, "VIH_AC", "POSITIF", "2026-02-01");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("a risque, jeudi, matin", projectionDesPropositions(proposer("?isolement=true&jours=LUNDI&limite=30&creneauId=" + matin)));
        m.put("sans risque, mardi", projectionDesPropositions(proposer("?isolement=false&jours=MARDI&limite=30&salleId=" + salle1)));
        m.put("ids", Map.of("sansRisque", sansRisque != null, "aRisque", aRisque != null));
        arbre("pas_de_melange_sur_un_generateur", m);
    }

    // ------------------------------------------------------------------ jours d'ouverture et fermetures

    @Test
    void seuls_les_jours_d_ouverture_du_centre_sont_proposes_et_une_grille_fermee_a_une_capacite_nulle() throws Exception {
        decorDePlacement();
        joursOuvertsDuCentre(CENTRE_A, "DIMANCHE,LUNDI,MARDI,MERCREDI,JEUDI");
        Resp r = proposer("?seances=5&limite=2");
        Resp samedi = proposer("?jours=SAMEDI");
        Resp mixte = proposer("?jours=LUNDI,SAMEDI");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("5 seances sur 5 jours ouverts", projectionDesPropositions(r));
        m.put("6 seances (impossible)", projectionDesPropositions(proposer("?seances=6")));
        m.put("jour impose ferme", projectionDesPropositions(samedi));
        m.put("jours imposes dont un ferme", projectionDesPropositions(mixte));
        JsonNode cases = corps(r).path("grille");
        int fermeesAvecCapacite = 0;
        int fermees = 0;
        for (JsonNode k : cases) {
            if ("VENDREDI".equals(k.path("jour").asString()) || "SAMEDI".equals(k.path("jour").asString())) {
                fermees++;
                if (k.path("capacite").asInt() != 0) fermeesAvecCapacite++;
            }
        }
        m.put("cases de la grille un jour ferme", fermees);
        m.put("dont avec une capacite", fermeesAvecCapacite);
        arbre("jours_d_ouverture", m);
    }

    @Test
    void sans_ligne_de_parametres_ou_sans_jour_d_ouverture_tous_les_jours_sont_ouverts() throws Exception {
        decorDePlacement();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pas de ligne", projectionDesPropositions(proposer("?seances=7&limite=1")));
        joursOuvertsDuCentre(CENTRE_A, "");
        m.put("jours ouverts vide", projectionDesPropositions(proposer("?seances=7&limite=1")));
        arbre("parametres_absents_ou_vides", m);
    }

    @Test
    void les_fermetures_datees_des_90_prochains_jours_sont_signalees_sur_le_jour_de_la_proposition() throws Exception {
        decorDePlacement();
        LocalDate ferie = jour(3);
        LocalDate exceptionnelle = jour(20);
        LocalDate hors = jour(95);
        jourFerme(CENTRE_A, ferie, "Fete du centre");
        jdbc.update("INSERT INTO center_closure_day (id, center_id, day_date, reason) VALUES (?, ?, ?, ?)", UUID.randomUUID(),
                CENTRE_A, java.sql.Date.valueOf(exceptionnelle), "Travaux");
        jourFerme(CENTRE_A, hors, "Trop loin");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jour du ferie", maskeJour(ferie, projectionDesPropositions(proposer("?jours=" + JourSemaine.de(ferie.getDayOfWeek())
                + "&limite=1&salleId=" + salle1))));
        m.put("jour de la fermeture exceptionnelle", maskeJour(exceptionnelle,
                projectionDesPropositions(proposer("?jours=" + JourSemaine.de(exceptionnelle.getDayOfWeek()) + "&limite=1&salleId=" + salle1))));
        m.put("jour de la fermeture hors horizon", maskeJour(hors,
                projectionDesPropositions(proposer("?jours=" + JourSemaine.de(hors.getDayOfWeek()) + "&limite=1&salleId=" + salle1))));
        arbre("fermetures_datees", m);
    }

    // ------------------------------------------------------------------ garde de centre

    @Test
    void la_garde_de_centre_compare_le_parametre_au_centre_de_la_session() throws Exception {
        decorDePlacement();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centre de la session", statutEtCode(proposer("?centerId=" + CENTRE_A)));
        m.put("autre centre", statutEtCode(proposer("?centerId=" + CENTRE_B)));
        m.put("centre inconnu", statutEtCode(proposer("?centerId=" + UUID.nameUUIDFromBytes("inconnu".getBytes()))));
        m.put("centre illisible", statutEtCode(proposer("?centerId=abc")));
        m.put("admin du centre B sur le centre A", statutEtCode(doGet(AFFECTATIONS + "?centerId=" + CENTRE_A, adminB())));
        m.put("admin sans centre", statutEtCode(doGet(AFFECTATIONS, as(null, "ADMIN"))));
        arbre("garde_de_centre", m);
    }

    /** Masque le nom du jour de la semaine d'une date relative : il dépend du jour d'exécution. */
    private JsonNode maskeJour(LocalDate date, Object projection) {
        String nom = JourSemaine.de(date.getDayOfWeek()).name();
        String brut = mapper.writeValueAsString(projection).replace(nom, "<JOUR_DE_LA_DATE>");
        return mapper.readTree(brut);
    }
}
