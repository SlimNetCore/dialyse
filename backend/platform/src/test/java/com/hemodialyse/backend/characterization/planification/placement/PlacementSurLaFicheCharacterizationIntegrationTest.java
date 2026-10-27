package com.hemodialyse.backend.characterization.planification.placement;

import com.hemodialyse.backend.characterization.planification.PlacementCharacterizationSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MARDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MERCREDI;

/**
 * Filet de caractérisation P5 : contrôle d'un placement enregistré sur la fiche patient ({@code POST} et {@code PUT /api/v1/patients},
 * {@code PlacementPatientService.verifier}, règles RG-PLN-050) et effets sur les colonnes de place de {@code patients}. Les
 * écritures passent par la fiche legacy : la Planification ne fait que refuser ; le seam de la fiche (Patient.java:122-131) n'existe
 * pas encore. Point d'observation : HTTP (contexte Spring complet, H2) puis état SQL des colonnes de place.
 */
class PlacementSurLaFicheCharacterizationIntegrationTest extends PlacementCharacterizationSupport {

    @Override
    protected String theme() {
        return "p5-placement/fiche";
    }

    private Resp enregistrer(String alias, UUID salle, UUID creneau, UUID generateur, String... jours) throws Exception {
        UUID id = UUID.nameUUIDFromBytes(("planification-p3-patient-" + alias).getBytes());
        return doPut(PATIENTS + "/" + id, ficheDe(alias, CENTRE_A, salle, creneau, generateur, jours), adminA());
    }

    private String refus(Resp r) {
        if (r.status() == 200) return "200";
        JsonNode c = corps(r);
        return r.status() + " " + c.path("code").asString() + " : " + c.path("detail").asString();
    }

    // ------------------------------------------------------------------ création

    @Test
    void une_creation_sans_aucun_placement_est_acceptee_et_ne_pose_aucune_place() throws Exception {
        decorDePlacement();
        Resp r = doPost(PATIENTS, fiche(CENTRE_A, "Nouveau", "ASS-NEW-1", null, null, null), adminA());
        ecriture("creation_sans_placement", sansCodeAleatoire(r));
        etatDesPlaces("creation_sans_placement_etat");
    }

    @Test
    void une_creation_avec_un_placement_conforme_ecrit_salle_creneau_generateur_et_jours() throws Exception {
        decorDePlacement();
        Resp r = doPost(PATIENTS, fiche(CENTRE_A, "Nouveau", "ASS-NEW-2", salle1, matin, g1a, "LUNDI", "MERCREDI", "VENDREDI"), adminA());
        ecriture("creation_avec_placement", sansCodeAleatoire(r));
        etatDesPlaces("creation_avec_placement_etat");
    }

    @Test
    void une_creation_avec_un_placement_incoherent_est_refusee_sans_rien_ecrire() throws Exception {
        decorDePlacement();
        Resp r = doPost(PATIENTS, fiche(CENTRE_A, "Nouveau", "ASS-NEW-3", salle1, matin, g1a), adminA());
        ecriture("creation_incomplete", r);
        etatDesPlaces("creation_incomplete_etat");
    }

    // ------------------------------------------------------------------ les huit règles de RG-PLN-050

    @Test
    void chaque_regle_de_placement_a_son_code_et_le_premier_manquement_donne_le_code() throws Exception {
        decorDePlacement();
        UUID hs = generateur(CENTRE_A, "G1HS", salle1, "HORS_SERVICE");
        UUID autreCentre = generateur(CENTRE_B, "GB1", salleB, "EN_SERVICE");
        UUID autreSalle = g2a;
        place(CENTRE_A, "PAT_OCCUPANT", salle1, matin, g1a, LUNDI);
        UUID aRisque = patientPlace(CENTRE_A, "PAT_A_RISQUE", null, null);
        serologie(CENTRE_A, aRisque, "AG_HBS", "POSITIF", "2026-02-01");
        patientPlace(CENTRE_A, "PAT_NEUF", null, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("INCOMPLET : salle seule", refus(enregistrer("PAT_NEUF", salle1, null, null)));
        m.put("INCOMPLET : jours seuls", refus(enregistrer("PAT_NEUF", null, null, null, "LUNDI")));
        m.put("INCOMPLET : sans jour", refus(enregistrer("PAT_NEUF", salle1, matin, g1b)));
        m.put("INCOMPLET : sans generateur", refus(enregistrer("PAT_NEUF", salle1, matin, null, "MARDI")));
        m.put("SALLE_INCONNUE : salle d'un autre centre", refus(enregistrer("PAT_NEUF", salleB, matin, g1b, "MARDI")));
        m.put("SALLE_INCONNUE : salle inexistante", refus(enregistrer("PAT_NEUF", UUID.nameUUIDFromBytes("x".getBytes()), matin, g1b, "MARDI")));
        m.put("CRENEAU_INCONNU : creneau d'un autre centre", refus(enregistrer("PAT_NEUF", salle1, creneauB, g1b, "MARDI")));
        m.put("GENERATEUR_INCONNU : hors service", refus(enregistrer("PAT_NEUF", salle1, matin, hs, "MARDI")));
        m.put("GENERATEUR_INCONNU : d'une autre salle", refus(enregistrer("PAT_NEUF", salle1, matin, autreSalle, "MARDI")));
        m.put("GENERATEUR_INCONNU : d'un autre centre", refus(enregistrer("PAT_NEUF", salle1, matin, autreCentre, "MARDI")));
        m.put("GENERATEUR_INCONNU : inexistant", refus(enregistrer("PAT_NEUF", salle1, matin, UUID.nameUUIDFromBytes("y".getBytes()), "MARDI")));
        m.put("GENERATEUR_OCCUPE : meme generateur, creneau, jour", refus(enregistrer("PAT_NEUF", salle1, matin, g1a, "LUNDI")));
        m.put("GENERATEUR_OCCUPE : un des jours seulement", refus(enregistrer("PAT_NEUF", salle1, matin, g1a, "MARDI", "LUNDI")));
        m.put("ISOLEMENT_REQUIS : patient a risque en salle ordinaire", refus(enregistrer("PAT_A_RISQUE", salle1, matin, g1b, "MARDI")));
        arbre("les_regles_de_placement", m);
        etatDesPlaces("les_regles_de_placement_etat");
    }

    @Test
    void plusieurs_manquements_le_message_les_liste_tous_et_le_code_est_celui_du_premier() throws Exception {
        decorDePlacement();
        joursOuvertsDuCentre(CENTRE_A, "MARDI,MERCREDI,JEUDI,VENDREDI,SAMEDI,DIMANCHE");
        UUID aRisque = patientPlace(CENTRE_A, "PAT_A_RISQUE", null, null);
        serologie(CENTRE_A, aRisque, "VIH_AC", "POSITIF", "2026-02-01");
        place(CENTRE_A, "PAT_OCCUPANT", salle1, matin, g1a, LUNDI);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jour ferme + isolement requis + occupe", refus(enregistrer("PAT_A_RISQUE", salle1, matin, g1a, "LUNDI")));
        m.put("salle inconnue + creneau inconnu + generateur inconnu + jour ferme",
                refus(enregistrer("PAT_OCCUPANT", salleB, creneauB, g1b, "LUNDI")));
        arbre("manquements_multiples", m);
    }

    @Test
    void un_jour_ferme_du_centre_refuse_le_placement_ce_jour_la_seulement() throws Exception {
        decorDePlacement();
        joursOuvertsDuCentre(CENTRE_A, "MARDI,MERCREDI,JEUDI");
        patientPlace(CENTRE_A, "PAT_NEUF", null, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lundi (ferme)", refus(enregistrer("PAT_NEUF", salle1, matin, g1a, "LUNDI")));
        m.put("mardi (ouvert)", refus(enregistrer("PAT_NEUF", salle1, matin, g1a, "MARDI")));
        arbre("jour_ferme", m);
        etatDesPlaces("jour_ferme_etat");
    }

    // ------------------------------------------------------------------ modifications d'un placement

    @Test
    void un_placement_inchange_n_est_jamais_controle_meme_devenu_invalide() throws Exception {
        decorDePlacement();
        UUID hs = generateur(CENTRE_A, "G1HS", salle1, "HORS_SERVICE");
        place(CENTRE_A, "PAT_HERITE", salle1, matin, hs, LUNDI);
        place(CENTRE_A, "PAT_DOUBLON_1", salle2, matin, g2a, MARDI);
        place(CENTRE_A, "PAT_DOUBLON_2", salle2, matin, g2a, MARDI);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("generateur hors service, inchange", refus(enregistrer("PAT_HERITE", salle1, matin, hs, "LUNDI")));
        m.put("generateur hors service, un jour de plus", refus(enregistrer("PAT_HERITE", salle1, matin, hs, "LUNDI", "MARDI")));
        m.put("double occupation heritee, inchangee", refus(enregistrer("PAT_DOUBLON_1", salle2, matin, g2a, "MARDI")));
        m.put("double occupation heritee, autre jour", refus(enregistrer("PAT_DOUBLON_1", salle2, matin, g2a, "MARDI", "MERCREDI")));
        arbre("placement_inchange", m);
        etatDesPlaces("placement_inchange_etat");
    }

    @Test
    void un_patient_garde_sa_propre_place_quand_il_ajoute_un_jour_ou_change_de_creneau() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ajoute mercredi", refus(enregistrer("PAT_1", salle1, matin, g1a, "LUNDI", "MERCREDI")));
        m.put("passe l'apres-midi", refus(enregistrer("PAT_1", salle1, apresMidi, g1a, "LUNDI", "MERCREDI")));
        m.put("change de salle et de generateur", refus(enregistrer("PAT_1", salle2, apresMidi, g2a, "LUNDI", "MERCREDI")));
        m.put("retire un jour", refus(enregistrer("PAT_1", salle2, apresMidi, g2a, "MERCREDI")));
        arbre("deplacement_et_changement_de_jours", m);
        etatDesPlaces("deplacement_et_changement_de_jours_etat");
    }

    @Test
    void un_deplacement_vers_une_place_prise_par_un_autre_patient_est_refuse_et_l_ancienne_place_reste() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        place(CENTRE_A, "PAT_2", salle2, matin, g2a, LUNDI);
        Resp r = enregistrer("PAT_1", salle2, matin, g2a, "LUNDI");
        ecriture("deplacement_refuse", r);
        etatDesPlaces("deplacement_refuse_etat");
    }

    @Test
    void la_desaffectation_efface_salle_creneau_generateur_et_jours() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI, MARDI);
        Resp r = enregistrer("PAT_1", null, null, null);
        ecriture("desaffectation", r);
        etatDesPlaces("desaffectation_etat");
    }

    @Test
    void un_autre_patient_peut_prendre_la_place_liberee_et_une_place_partagee_a_un_autre_jour() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        patientPlace(CENTRE_A, "PAT_2", null, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("meme place, autre jour", refus(enregistrer("PAT_2", salle1, matin, g1a, "MARDI")));
        m.put("autre creneau, meme jour", refus(enregistrer("PAT_2", salle1, apresMidi, g1a, "LUNDI")));
        m.put("meme jour et creneau (occupe)", refus(enregistrer("PAT_2", salle1, matin, g1a, "LUNDI", "MERCREDI")));
        enregistrer("PAT_1", null, null, null);
        m.put("apres la desaffectation du premier", refus(enregistrer("PAT_2", salle1, matin, g1a, "LUNDI", "MERCREDI")));
        arbre("places_partagees_et_liberees", m);
        etatDesPlaces("places_partagees_et_liberees_etat");
    }

    @Test
    void les_sept_jours_de_la_semaine_sont_acceptes_et_ecrits() throws Exception {
        decorDePlacement();
        patientPlace(CENTRE_A, "PAT_1", null, null);
        Resp r = enregistrer("PAT_1", salle1, matin, g1a, "DIMANCHE", "LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI");
        ecriture("sept_jours", r);
        etatDesPlaces("sept_jours_etat");
    }

    // ------------------------------------------------------------------ isolement

    @Test
    void un_patient_a_risque_n_est_accepte_qu_en_salle_d_isolement_et_un_patient_sain_y_est_aussi_accepte() throws Exception {
        decorDePlacement();
        generateur(CENTRE_A, "GISO2", salleIsolement, "EN_SERVICE");
        UUID aRisque = patientPlace(CENTRE_A, "PAT_A_RISQUE", null, null);
        serologie(CENTRE_A, aRisque, "AC_VHC", "POSITIF", "2026-02-01");
        patientPlace(CENTRE_A, "PAT_SAIN", null, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("a risque en isolement", refus(enregistrer("PAT_A_RISQUE", salleIsolement, matin, giso, "LUNDI")));
        m.put("sain dans le generateur d'un patient a risque, autre jour", refus(enregistrer("PAT_SAIN", salleIsolement, matin, giso, "MARDI")));
        m.put("sain en isolement sur un autre generateur", refus(enregistrer("PAT_SAIN", salleIsolement, apresMidi, giso, "MARDI")));
        arbre("isolement_a_l_enregistrement", m);
        etatDesPlaces("isolement_a_l_enregistrement_etat");
    }

    @Test
    void un_patient_a_risque_deja_en_salle_ordinaire_peut_etre_modifie_tant_que_sa_place_ne_change_pas() throws Exception {
        decorDePlacement();
        UUID p = place(CENTRE_A, "PAT_A_RISQUE", salle1, matin, g1a, LUNDI);
        serologie(CENTRE_A, p, "ARN_VHC", "POSITIF", "2026-02-01");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inchange", refus(enregistrer("PAT_A_RISQUE", salle1, matin, g1a, "LUNDI")));
        m.put("un jour de plus", refus(enregistrer("PAT_A_RISQUE", salle1, matin, g1a, "LUNDI", "MARDI")));
        m.put("vers l'isolement", refus(enregistrer("PAT_A_RISQUE", salleIsolement, matin, giso, "LUNDI")));
        arbre("risque_et_place_inchangee", m);
    }

    // ------------------------------------------------------------------ validation de la requête

    @Test
    void les_requetes_illisibles_ou_incompletes_sont_refusees_avant_le_controle_du_placement() throws Exception {
        decorDePlacement();
        UUID id = patientPlace(CENTRE_A, "PAT_1", null, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("corps vide", statutEtCode(doPut(PATIENTS + "/" + id, "{}", adminA())));
        m.put("json illisible", statutEtCode(doPut(PATIENTS + "/" + id, "{pas du json", adminA())));
        m.put("salleId illisible", statutEtCode(doPut(PATIENTS + "/" + id,
                ficheDe("PAT_1", CENTRE_A, null, null, null).replace("\"salleId\":null", "\"salleId\":\"abc\""), adminA())));
        m.put("centerId absent", statutEtCode(doPut(PATIENTS + "/" + id,
                ficheDe("PAT_1", CENTRE_A, null, null, null).replace("\"centerId\":\"" + CENTRE_A + "\",", ""), adminA())));
        m.put("identifiant de patient illisible", statutEtCode(doPut(PATIENTS + "/abc", ficheDe("PAT_1", CENTRE_A, null, null, null), adminA())));
        m.put("patient inconnu, sans placement", statutEtCode(doPut(PATIENTS + "/" + UUID.nameUUIDFromBytes("inconnu".getBytes()),
                ficheDe("PAT_1", CENTRE_A, null, null, null), adminA())));
        m.put("patient inconnu, avec placement", statutEtCode(doPut(PATIENTS + "/" + UUID.nameUUIDFromBytes("inconnu".getBytes()),
                ficheDe("PAT_1", CENTRE_A, salle1, matin, g1a, "LUNDI"), adminA())));
        arbre("requetes_invalides", m);
        etatDesPlaces("requetes_invalides_etat");
    }

    @Test
    void la_fiche_d_un_patient_d_un_autre_centre_n_est_ni_controlee_ni_modifiee() throws Exception {
        decorDePlacement();
        UUID deB = patientPlace(CENTRE_B, "PAT_B", salleB, creneauB, LUNDI);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("admin A sur un patient de B (centerId B)", statutEtCode(doPut(PATIENTS + "/" + deB,
                ficheDe("PAT_B", CENTRE_B, null, null, null), adminA())));
        m.put("admin A sur un patient de B (centerId A)", statutEtCode(doPut(PATIENTS + "/" + deB,
                ficheDe("PAT_B", CENTRE_A, salle1, matin, g1a, "MARDI"), adminA())));
        arbre("autre_centre", m);
        etatDesPlaces("autre_centre_etat");
    }

    // ------------------------------------------------------------------ verrou et concurrence (H2)

    @Test
    void le_controle_ne_cree_aucune_ligne_de_parametres_et_ne_pose_aucun_verrou_de_centre() throws Exception {
        decorDePlacement();
        patientPlace(CENTRE_A, "PAT_1", null, null);
        Integer avant = jdbc.queryForObject("SELECT COUNT(*) FROM planning_parametres WHERE center_id = ?", Integer.class, CENTRE_A);
        enregistrer("PAT_1", salle1, matin, g1a, "LUNDI");
        Integer apres = jdbc.queryForObject("SELECT COUNT(*) FROM planning_parametres WHERE center_id = ?", Integer.class, CENTRE_A);
        arbre("aucune_ligne_de_parametres", Map.of("avant", avant, "apres", apres));
    }

    @Test
    void deux_enregistrements_simultanes_sur_la_meme_place_ne_sont_jamais_serialises_par_un_verrou_de_centre() throws Exception {
        decorDePlacement();
        patientPlace(CENTRE_A, "PAT_1", null, null);
        patientPlace(CENTRE_A, "PAT_2", null, null);
        List<Resp> reponses = enParallele(
                () -> enregistrer("PAT_1", salle1, matin, g1a, "LUNDI"),
                () -> enregistrer("PAT_2", salle1, matin, g1a, "LUNDI"));
        long acceptes = reponses.stream().filter(r -> r.status() == 200).count();
        long refuses = reponses.stream().filter(r -> r.status() == 422).count();
        int occupants = jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE center_id = ? AND generateur_id = ? AND jour_lundi",
                Integer.class, CENTRE_A, g1a);
        // Aucun 409, aucune attente de verrou (RG-PLN-050 contrôle puis écriture, B1) : au moins un enregistrement passe ; le
        // second est refusé (422) s'il lit après l'écriture du premier, sinon (course) les deux passent et le générateur est
        // doublement occupé. Le résultat de la course n'est pas figé (non déterministe) : seules ses bornes le sont.
        arbre("course_sur_la_meme_place", Map.of(
                "au moins un accepte", acceptes >= 1,
                "aucun autre statut que 200 ou 422", acceptes + refuses == 2,
                "occupants du generateur coherents avec les reponses", occupants == acceptes));
    }

    // ------------------------------------------------------------------ effets sur les lectures

    @Test
    void un_placement_enregistre_est_aussitot_visible_dans_la_grille_et_les_propositions() throws Exception {
        decorDePlacement();
        patientPlace(CENTRE_A, "PAT_1", null, null);
        Resp avant = doGet(AFFECTATIONS + "?jours=LUNDI&limite=30&salleId=" + salle1, adminA());
        enregistrer("PAT_1", salle1, matin, g1a, "LUNDI");
        Resp apres = doGet(AFFECTATIONS + "?jours=LUNDI&limite=30&salleId=" + salle1, adminA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("avant", Map.of("grille occupee", grilleOccupee(avant), "propositions", projectionDesPropositions(avant)));
        m.put("apres", Map.of("grille occupee", grilleOccupee(apres), "propositions", projectionDesPropositions(apres)));
        arbre("effet_sur_les_propositions", m);
    }

    @Test
    void un_placement_refuse_ne_change_ni_la_grille_ni_la_fiche() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        patientPlace(CENTRE_A, "PAT_2", null, null);
        Resp avant = doGet(AFFECTATIONS + "?jours=LUNDI&limite=30", adminA());
        Resp r = enregistrer("PAT_2", salle1, matin, g1a, "LUNDI");
        Resp apres = doGet(AFFECTATIONS + "?jours=LUNDI&limite=30", adminA());
        arbre("refus_sans_effet", Map.of("statut", r.status(), "grille identique", grilleOccupee(avant).equals(grilleOccupee(apres))));
        etatDesPlaces("refus_sans_effet_etat");
    }

    @Test
    void les_enregistrements_de_fiche_sont_diffuses_et_les_refus_ne_le_sont_pas() throws Exception {
        decorDePlacement();
        patientPlace(CENTRE_A, "PAT_1", null, null);
        enregistrer("PAT_1", salle1, matin, g1a, "LUNDI");
        enregistrer("PAT_1", salle1, matin, g1a, "MARDI", "LUNDI", "MERCREDI", "JEUDI");
        diffusionsCapturees("diffusions_de_fiche");
        notifications("notifications_de_fiche");
    }

    @Test
    void l_enregistrement_d_une_fiche_et_son_refus_sont_audites() throws Exception {
        decorDePlacement();
        patientPlace(CENTRE_A, "PAT_1", null, null);
        place(CENTRE_A, "PAT_OCCUPANT", salle1, matin, g1a, LUNDI);
        monterLAudit();
        enregistrer("PAT_1", salle1, matin, g1b, "LUNDI");
        enregistrer("PAT_1", salle1, matin, g1a, "LUNDI");
        doPost(PATIENTS, fiche(CENTRE_A, "Nouveau", "ASS-NEW-9", null, null, null), adminA());
        audit("audit_de_fiche");
    }
}
