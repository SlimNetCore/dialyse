package com.hemodialyse.backend.characterization.planification.semaine;

import com.hemodialyse.backend.characterization.planification.SemaineCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.DIMANCHE;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.JEUDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MARDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MERCREDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.SAMEDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.VENDREDI;

/**
 * Filet de caractérisation P6 : jours fermés (jours d'ouverture hebdomadaires, fériés, fermetures exceptionnelles) et état des
 * patients (sommeil, sortis, admis dans la semaine, séjours limités) tels que la semaine les montre.
 */
class JoursFermesEtPatientsDeLaSemaineCharacterizationIntegrationTest extends SemaineCharacterizationSupport {

    @Override
    protected String theme() {
        return "p6-semaine/jours-et-patients";
    }

    // ------------------------------------------------------------------ jours fermés

    @Test
    void un_jour_non_ouvert_a_une_capacite_nulle_et_ses_patients_sont_a_replanifier() throws Exception {
        petitDecor();
        joursOuvertsDuCentre(CENTRE_A, "LUNDI,MERCREDI,VENDREDI");
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI, MARDI);
        place(CENTRE_A, "PAT_2", salle1, matin, null, MARDI, JEUDI);
        place(CENTRE_A, "PAT_3", salle1, matin, null, MERCREDI);
        arbre("jours_ouverts_hebdomadaires", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void les_jours_d_ouverture_absents_ou_vides_ou_illisibles_valent_sept_jours() throws Exception {
        petitDecor();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aucune ligne de parametres", projectionDeLaSemaine(lireLaSemaine(1)).get("jours"));
        for (String valeur : new String[]{"", "LUNDI", "LUNDI,LUNDI", "lundi,mardi", "LUNDI, MARDI", "SAMEDI,DIMANCHE"}) {
            joursOuvertsDuCentre(CENTRE_A, valeur);
            Resp r = lireLaSemaine(1);
            m.put("[" + valeur + "]", r.status() == 200 ? projectionDeLaSemaine(r).get("jours") : statutEtCode(r));
        }
        arbre("jours_ouverts_formes", m);
    }

    @Test
    void un_jour_ferie_ferme_le_jour_avec_son_motif_mais_ne_retire_pas_la_capacite() throws Exception {
        petitDecor();
        LocalDate lundi = semaine(1, LUNDI);
        jourFerme(CENTRE_A, lundi, "Jour ferie test");
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI, MARDI);
        arbre("jour_ferie", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void une_fermeture_exceptionnelle_ferme_le_jour_et_le_ferie_prime_si_les_deux_existent() throws Exception {
        petitDecor();
        fermetureExceptionnelle(CENTRE_A, semaine(1, MARDI), "Travaux");
        jourFerme(CENTRE_A, semaine(1, JEUDI), "Ferie du jeudi");
        fermetureExceptionnelle(CENTRE_A, semaine(1, JEUDI), "Fermeture du jeudi");
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, MARDI, JEUDI, VENDREDI);
        arbre("fermeture_exceptionnelle_et_ferie", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void un_jour_ferie_sans_libelle_reste_ferme_avec_un_motif_vide() throws Exception {
        petitDecor();
        jourFerieSansLibelle(CENTRE_A, semaine(1, SAMEDI));
        fermetureExceptionnelle(CENTRE_A, semaine(1, DIMANCHE), null);
        lecture("ferie_sans_libelle", lireLaSemaine(1));
    }

    @Test
    void les_fermetures_hors_de_la_semaine_ou_d_un_autre_centre_sont_ignorees() throws Exception {
        petitDecor();
        nommerLaSemaine(1);
        jourFerme(CENTRE_A, semaine(0, SAMEDI), "Veille");
        jourFerme(CENTRE_A, semaine(2, DIMANCHE), "Lendemain");
        jourFerme(CENTRE_B, semaine(1, MERCREDI), "Autre centre");
        fermetureExceptionnelle(CENTRE_B, semaine(1, JEUDI), "Autre centre");
        arbre("fermetures_ignorees", projectionDeLaSemaine(lireLaSemaine(1)).get("jours"));
    }

    @Test
    void un_jour_ferme_cumule_hebdomadaire_et_ferie_et_les_patients_a_replanifier_sont_comptes_une_fois() throws Exception {
        petitDecor();
        joursOuvertsDuCentre(CENTRE_A, "LUNDI,MARDI,MERCREDI,JEUDI,VENDREDI,SAMEDI");
        jourFerme(CENTRE_A, semaine(1, MARDI), "Ferie");
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, DIMANCHE, MARDI, MERCREDI);
        place(CENTRE_A, "PAT_2", salle1, matin, g1a, MERCREDI);
        arbre("cumul_ferme", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    // ------------------------------------------------------------------ états des patients

    @Test
    void un_patient_en_sommeil_garde_sa_place_dans_la_grille() throws Exception {
        petitDecor();
        sommeil(place(CENTRE_A, "PAT_SOMMEIL", salle1, matin, g1a, LUNDI));
        arbre("sommeil", projectionDeLaSemaine(lireLaSemaine(1)).get("cases"));
    }

    @Test
    void un_patient_transfere_occupe_sa_place_jusqu_a_la_date_de_l_evenement_incluse() throws Exception {
        petitDecor();
        UUID p = place(CENTRE_A, "PAT_TRANSFERE", salle1, matin, g1a, DIMANCHE, LUNDI, MARDI, MERCREDI, JEUDI, VENDREDI, SAMEDI);
        etat(p, "TRANSFERE", semaine(1, MERCREDI));
        arbre("transfere_au_milieu_de_la_semaine", projectionDeLaSemaine(lireLaSemaine(1)).get("cases"));
    }

    @Test
    void un_patient_decede_ou_greffe_est_libere_la_veille_et_un_gueri_le_jour_meme() throws Exception {
        petitDecor();
        generateur(CENTRE_A, "G1B", salle1, "EN_SERVICE");
        generateur(CENTRE_A, "G1C", salle1, "EN_SERVICE");
        etat(place(CENTRE_A, "PAT_DECEDE", salle1, matin, g1a, DIMANCHE, LUNDI, MARDI, MERCREDI, JEUDI), "DECEDE", semaine(1, MARDI));
        etat(place(CENTRE_A, "PAT_GREFFE", salle1, matin, null, DIMANCHE, LUNDI, MARDI, MERCREDI, JEUDI), "GREFFE", semaine(1, MARDI));
        etat(place(CENTRE_A, "PAT_GUERI", salle1, matin, null, DIMANCHE, LUNDI, MARDI, MERCREDI, JEUDI), "GUERI", semaine(1, MARDI));
        etat(place(CENTRE_A, "PAT_GUERI_BIS", salle1, matin, null, DIMANCHE, LUNDI, MARDI, MERCREDI, JEUDI), "GUERRI", semaine(1, MARDI));
        arbre("deces_greffe_guerison", projectionDeLaSemaine(lireLaSemaine(1)).get("cases"));
    }

    @Test
    void un_patient_sorti_sans_date_d_evenement_ou_avant_la_semaine_ne_tient_plus_de_place() throws Exception {
        petitDecor();
        etat(place(CENTRE_A, "SORTI_SANS_DATE", salle1, matin, g1a, LUNDI), "TRANSFERE", null);
        etat(place(CENTRE_A, "SORTI_AVANT", salle1, matin, g1a, LUNDI), "TRANSFERE", semaine(0, SAMEDI));
        etat(place(CENTRE_A, "SORTI_LA_VEILLE_DU_DEBUT", salle1, matin, g1a, DIMANCHE, LUNDI), "DECEDE", semaine(1, DIMANCHE));
        etat(place(CENTRE_A, "SORTI_LE_DERNIER_JOUR", salle1, matin, g1a, DIMANCHE, LUNDI), "TRANSFERE", semaine(1, DIMANCHE));
        arbre("sortis_avant_la_semaine", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void un_patient_sorti_plus_tard_que_la_semaine_est_libere_a_la_date_lointaine_nommee() throws Exception {
        petitDecor();
        LocalDate lointain = alias(semaine(1, LUNDI).plusDays(400), "<S1.LUN+400>");
        alias(lointain.plusDays(1), "<S1.LUN+401>");
        etat(place(CENTRE_A, "SORTI_PLUS_TARD", salle1, matin, g1a, LUNDI), "TRANSFERE", lointain);
        arbre("sorti_plus_tard", projectionDeLaSemaine(lireLaSemaine(1)).get("cases"));
    }

    @Test
    void un_patient_sorti_est_lu_apres_sa_sortie_tant_que_la_semaine_est_la_derniere_ou_avant() throws Exception {
        petitDecor();
        UUID p = place(CENTRE_A, "PAT_TRANSFERE", salle1, matin, g1a, LUNDI, JEUDI);
        etat(p, "TRANSFERE", semaine(1, MARDI));
        Map<String, Object> m = new LinkedHashMap<>();
        for (int n : new int[]{0, 1, 2}) {
            nommerLaSemaine(n);
            m.put("semaine " + n, projectionDeLaSemaine(lireLaSemaine(n)).get("cases"));
        }
        arbre("sorti_semaines_voisines", m);
    }

    @Test
    void un_patient_admis_dans_la_semaine_est_compte_seulement_a_partir_de_son_admission() throws Exception {
        petitDecor();
        UUID p = place(CENTRE_A, "PAT_ADMIS", salle1, matin, g1a, DIMANCHE, LUNDI, MARDI, MERCREDI, JEUDI);
        admission(p, semaine(1, MARDI));
        arbre("admis_en_cours_de_semaine", projectionDeLaSemaine(lireLaSemaine(1)).get("cases"));
    }

    @Test
    void un_patient_admis_apres_la_semaine_reste_compte_dans_la_grille_par_prudence() throws Exception {
        petitDecor();
        UUID p = place(CENTRE_A, "PAT_ADMIS_PLUS_TARD", salle1, matin, g1a, LUNDI);
        admission(p, alias(semaine(1, LUNDI).plusDays(20), "<S1.LUN+20>"));
        arbre("admis_plus_tard", projectionDeLaSemaine(lireLaSemaine(1)).get("cases"));
    }

    @Test
    void un_sejour_limite_occupe_la_place_jusqu_a_la_fin_de_sejour_incluse() throws Exception {
        petitDecor();
        generateur(CENTRE_A, "G1B", salle1, "EN_SERVICE");
        generateur(CENTRE_A, "G1C", salle1, "EN_SERVICE");
        etat(place(CENTRE_A, "VACANCIER_LOCAL", salle1, matin, g1a, DIMANCHE, LUNDI, MARDI, MERCREDI), "VACANCIER_LOCAL", semaine(1, LUNDI));
        etat(place(CENTRE_A, "OCCASIONNEL", salle1, matin, null, DIMANCHE, LUNDI, MARDI, MERCREDI), "OCCASIONNEL", semaine(1, LUNDI));
        etat(place(CENTRE_A, "VACANCIER_ETRANGER_SANS_FIN", salle1, matin, null, MARDI), "VACANCIER_ETRANGER", null);
        arbre("sejour_limite", projectionDeLaSemaine(lireLaSemaine(1)).get("cases"));
    }

    @Test
    void un_etat_inconnu_ou_non_renseigne_occupe_la_place_sans_limite() throws Exception {
        petitDecor();
        generateur(CENTRE_A, "G1B", salle1, "EN_SERVICE");
        etat(place(CENTRE_A, "ETAT_INCONNU", salle1, matin, g1a, LUNDI), "AUTRE_ETAT", semaine(0, LUNDI));
        jdbc.update("UPDATE patients SET etat_patient = NULL WHERE id = ?", place(CENTRE_A, "ETAT_NUL", salle1, matin, null, LUNDI));
        arbre("etat_inconnu", projectionDeLaSemaine(lireLaSemaine(1)).get("cases"));
    }

    @Test
    void un_patient_a_risque_est_marque_a_partir_de_sa_derniere_serologie_positive_d_un_marqueur_de_risque() throws Exception {
        decorDePlacement();
        UUID positif = place(CENTRE_A, "PAT_POSITIF", salleIsolement, matin, giso, LUNDI);
        UUID negatifDepuis = place(CENTRE_A, "PAT_NEGATIF_DEPUIS", salleIsolement, matin, null, LUNDI);
        UUID autreMarqueur = place(CENTRE_A, "PAT_AC_HBS_SEUL", salle1, matin, g1a, LUNDI);
        serologie(CENTRE_A, positif, "AG_HBS", "POSITIF", "2026-03-01");
        serologie(CENTRE_A, negatifDepuis, "AC_VHC", "POSITIF", "2026-03-01");
        serologie(CENTRE_A, negatifDepuis, "AC_VHC", "NEGATIF", "2026-04-01");
        serologie(CENTRE_A, autreMarqueur, "AC_HBC", "POSITIF", "2026-03-01");
        arbre("risque_infectieux", projectionDeLaSemaine(lireLaSemaine(1)));
    }
}
