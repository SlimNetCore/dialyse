package com.hemodialyse.backend.characterization.planification.liberation;

import com.hemodialyse.backend.characterization.planification.LiberationCharacterizationSupport;
import com.hemodialyse.backend.contexts.absencespatients.application.PatientsAttendus;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MERCREDI;

/**
 * Filet de caractérisation P7 : libération à l'enregistrement d'une fiche (échéance jugée à J-1, {@code libererALEnregistrement}), lecture du
 * mouvement {@code PLACE_LIBEREE} par {@code GET /api/v1/mouvements-patients}, absence de route manuelle, rafraîchissement des caches de la
 * fiche et lectures consommatrices de la place (propositions de place de P5, contrôle des absences).
 */
class EnregistrementEtLecturesDeLaLiberationCharacterizationIntegrationTest extends LiberationCharacterizationSupport {

    @Autowired
    private PatientsAttendus attendus;

    @Override
    protected String theme() {
        return "p7-liberation/enregistrement";
    }

    private String ficheSortie(String alias, String etat, LocalDate evenement, String... jours) {
        String corps = ficheDe(alias, CENTRE_A, salle1, matin, g1a, jours);
        return corps.replace("\"etatPatient\":\"PERMANENT\"", "\"etatPatient\":\"" + etat + "\""
                + (evenement == null ? "" : ",\"dateEvenementEtat\":\"" + evenement + "\""));
    }

    private int enregistrerLaSortie(String alias, String etat, LocalDate evenement) throws Exception {
        UUID id = UUID.nameUUIDFromBytes(("planification-p3-patient-" + alias).getBytes());
        return doPut(PATIENTS + "/" + id, ficheSortie(alias, etat, evenement, "LUNDI", "MERCREDI"), adminA()).status();
    }

    // ------------------------------------------------------------------ libération à l'enregistrement (J-1)

    @Test
    void a_l_enregistrement_d_une_sortie_la_place_n_est_liberee_que_si_l_echeance_est_depassee_d_au_moins_deux_jours() throws Exception {
        decorDePlacement();
        nommerLesJours();
        Map<String, Object> statuts = new LinkedHashMap<>();
        String[][] cas = {{"TRANSFERE", "-2"}, {"TRANSFERE", "-1"}, {"TRANSFERE", "0"}, {"GUERRI", "-2"}, {"GUERRI", "-1"},
                {"DECEDE", "-1"}, {"DECEDE", "0"}, {"DECEDE", "1"}, {"GREFFE", "-1"}, {"GREFFE", "0"}, {"OCCASIONNEL", "-2"},
                {"OCCASIONNEL", "-1"}, {"VACANCIER_LOCAL", "-3"}};
        for (String[] c : cas) {
            LocalDate date = jour(Integer.parseInt(c[1]));
            String alias = c[0] + "_J" + (c[1].equals("0") ? "" : (c[1].startsWith("-") ? c[1] : "+" + c[1]));
            place(CENTRE_A, alias, salle1, matin, g1a, LUNDI, MERCREDI);
            statuts.put(alias, enregistrerLaSortie(alias, c[0], date));
        }
        arbre("statuts", statuts);
        etatDesPatients("enregistrement_patients");
        etatDesMouvements("enregistrement_mouvements");
    }

    @Test
    void un_etat_de_sortie_sans_date_d_evenement_est_refuse_a_l_enregistrement_et_la_place_est_conservee() throws Exception {
        decorDePlacement();
        nommerLesJours();
        place(CENTRE_A, "TRANSFERE_SANS_DATE", salle1, matin, g1a, LUNDI, MERCREDI);
        UUID id = UUID.nameUUIDFromBytes("planification-p3-patient-TRANSFERE_SANS_DATE".getBytes());
        ecriture("sans_date_reponse", doPut(PATIENTS + "/" + id, ficheSortie("TRANSFERE_SANS_DATE", "TRANSFERE", null, "LUNDI", "MERCREDI"), adminA()));
        etatDesPatients("sans_date_patients");
        etatDesMouvements("sans_date_mouvements");
    }

    @Test
    void la_place_conservee_a_l_enregistrement_est_liberee_la_nuit_suivante_avec_la_date_du_jour() throws Exception {
        decorDePlacement();
        nommerLesJours();
        place(CENTRE_A, "TRANSFERE_J-1", salle1, matin, g1a, LUNDI, MERCREDI);
        place(CENTRE_A, "TRANSFERE_J-2", salle1, matin, g1a, LUNDI, MERCREDI);
        enregistrerLaSortie("TRANSFERE_J-1", "TRANSFERE", jour(-1));
        enregistrerLaSortie("TRANSFERE_J-2", "TRANSFERE", jour(-2));
        etatDesMouvements("avant_la_nuit_mouvements");
        scheduler.libererPlaces();
        etatDesPatients("apres_la_nuit_patients");
        etatDesMouvements("apres_la_nuit_mouvements");
    }

    @Test
    void une_modification_de_fiche_d_un_patient_deja_libere_ne_replace_pas_et_n_ajoute_pas_de_mouvement_de_liberation() throws Exception {
        decorDePlacement();
        nommerLesJours();
        place(CENTRE_A, "TRANSFERE_J-2", salle1, matin, g1a, LUNDI, MERCREDI);
        enregistrerLaSortie("TRANSFERE_J-2", "TRANSFERE", jour(-2));
        enregistrerLaSortie("TRANSFERE_J-2", "TRANSFERE", jour(-2));
        etatDesPatients("deux_enregistrements_patients");
        etatDesMouvements("deux_enregistrements_mouvements");
    }

    // ------------------------------------------------------------------ lecture du mouvement et routes

    @Test
    void le_mouvement_de_liberation_est_lisible_dans_la_liste_des_mouvements_du_centre() throws Exception {
        decorDePlacement();
        nommerLesJours();
        sorti(CENTRE_A, "A_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        sorti(CENTRE_B, "B_DECEDE_J", "DECEDE", jour(0));
        scheduler.libererPlaces();
        lecture("liste_centre_a", doGet(MOUVEMENTS, adminA()));
        lecture("liste_filtre_type", doGet(MOUVEMENTS + "?type=PLACE_LIBEREE&from=" + jour(0) + "&to=" + jour(0), adminA()));
        lecture("liste_centre_b", doGet(MOUVEMENTS, adminB()));
        lecture("liste_autre_jour", doGet(MOUVEMENTS + "?from=" + jour(1), adminA()));
    }

    @Test
    void la_liste_des_mouvements_est_reservee_aux_roles_de_soins_et_d_administration_et_aucune_route_ne_declenche_la_liberation() throws Exception {
        decorDePlacement();
        nommerLesJours();
        sorti(CENTRE_A, "A_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        scheduler.libererPlaces();
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, org.springframework.test.web.servlet.request.RequestPostProcessor> p : profils().entrySet()) {
            m.put("GET mouvements : " + p.getKey(), statutEtCode(doGet(MOUVEMENTS, p.getValue())));
        }
        m.put("GET mouvements : anonyme", statutEtCode(doGetAnonymous(MOUVEMENTS)));
        m.put("POST mouvements : ADMIN", statutEtCode(doPost(MOUVEMENTS, "{}", adminA())));
        m.put("POST planning/liberation : ADMIN", statutEtCode(doPost(PLANNING + "/liberation", "{}", adminA())));
        m.put("POST patients/liberer-places : ADMIN", statutEtCode(doPost(PATIENTS + "/liberer-places", "{}", adminA())));
        m.put("GET mouvements autre centre : ADMIN du centre B", statutEtCode(doGet(MOUVEMENTS + "?centerId=" + CENTRE_A, adminB())));
        arbre("matrice_des_roles", m);
    }

    // ------------------------------------------------------------------ caches et lectures consommatrices

    @Test
    void la_fiche_lue_avant_la_nuit_est_rafraichie_apres_la_liberation() throws Exception {
        decorDePlacement();
        nommerLesJours();
        UUID p = sorti(CENTRE_A, "A_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        UUID autre = sorti(CENTRE_A, "A_PERMANENT", "PERMANENT", null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("avant", fichePatient(CENTRE_A, p));
        m.put("avant_permanent", fichePatient(CENTRE_A, autre));
        scheduler.libererPlaces();
        m.put("apres", fichePatient(CENTRE_A, p));
        m.put("apres_permanent", fichePatient(CENTRE_A, autre));
        arbre("fiche_avant_apres", m);
    }

    @Test
    void les_propositions_de_place_ignorent_deja_les_sortis_echus_la_liberation_ne_change_pas_la_grille() throws Exception {
        decorDePlacement();
        nommerLesJours();
        sorti(CENTRE_A, "A_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        sorti(CENTRE_A, "A_DECEDE_J", "DECEDE", jour(0));
        sorti(CENTRE_A, "A_TRANSFERE_J+2", "TRANSFERE", jour(2));
        place(CENTRE_A, "A_PERMANENT", salle1, matin, g1a, LUNDI, MERCREDI);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("avant", grilleOccupee(doGet(AFFECTATIONS + "?jours=LUNDI,MERCREDI&limite=30", adminA())));
        scheduler.libererPlaces();
        m.put("apres", grilleOccupee(doGet(AFFECTATIONS + "?jours=LUNDI,MERCREDI&limite=30", adminA())));
        arbre("grille_avant_apres", m);
    }

    @Test
    void l_ordre_de_nuit_importe_la_liberation_efface_les_jours_dont_le_controle_des_absences_a_besoin() {
        decorDePlacement();
        nommerLesJours();
        joursOuvertsDuCentre(CENTRE_A, TOUS_LES_JOURS);
        JourSemaine[] tous = JourSemaine.values();
        creerAvecTousLesJours("OCCASIONNEL_FIN_J-1", "OCCASIONNEL", jour(-1), tous);
        creerAvecTousLesJours("OCCASIONNEL_FIN_J-2", "OCCASIONNEL", jour(-2), tous);
        creerAvecTousLesJours("TRANSFERE_J-1", "TRANSFERE", jour(-1), tous);
        creerAvecTousLesJours("TRANSFERE_J", "TRANSFERE", jour(0), tous);
        creerAvecTousLesJours("DECEDE_J-1", "DECEDE", jour(-1), tous);
        creerAvecTousLesJours("DECEDE_J", "DECEDE", jour(0), tous);
        creerAvecTousLesJours("GUERRI_J-1", "GUERRI", jour(-1), tous);
        creerAvecTousLesJours("GREFFE_J-1", "GREFFE", jour(-1), tous);
        creerAvecTousLesJours("PERMANENT", "PERMANENT", null, tous);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("avant_04h00_attendus_hier", attendusLe(jour(-1)));
        m.put("avant_04h00_attendus_avant_hier", attendusLe(jour(-2)));
        scheduler.libererPlaces();
        m.put("apres_04h00_attendus_hier", attendusLe(jour(-1)));
        m.put("apres_04h00_attendus_avant_hier", attendusLe(jour(-2)));
        m.put("apres_04h00_places", patientsPlaces(CENTRE_A));
        arbre("ordre_de_nuit", m);
    }

    private void creerAvecTousLesJours(String alias, String etat, LocalDate evenement, JourSemaine[] jours) {
        UUID id = place(CENTRE_A, alias, salle1, matin, g1a, jours);
        etat(id, etat, evenement);
    }

    private List<String> attendusLe(LocalDate jour) {
        List<String> noms = new ArrayList<>();
        attendus.sansSeanceRealisee(new CentreId(CENTRE_A), jour).forEach(p -> noms.add(aliasNommes.getOrDefault(p.valeur().toString(), "?")));
        noms.sort(String::compareTo);
        return noms;
    }
}
