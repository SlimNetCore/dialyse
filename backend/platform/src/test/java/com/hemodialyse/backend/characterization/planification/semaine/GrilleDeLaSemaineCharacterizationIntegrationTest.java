package com.hemodialyse.backend.characterization.planification.semaine;

import com.hemodialyse.backend.characterization.planification.SemaineCharacterizationSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.characterization.planification.JourSemaine.DIMANCHE;
import static com.hemodialyse.backend.characterization.planification.JourSemaine.JEUDI;
import static com.hemodialyse.backend.characterization.planification.JourSemaine.LUNDI;
import static com.hemodialyse.backend.characterization.planification.JourSemaine.MARDI;
import static com.hemodialyse.backend.characterization.planification.JourSemaine.MERCREDI;
import static com.hemodialyse.backend.characterization.planification.JourSemaine.SAMEDI;
import static com.hemodialyse.backend.characterization.planification.JourSemaine.VENDREDI;

/**
 * Filet de caractérisation P6 : {@code GET /api/v1/planning/semaine} (grille de la semaine du dimanche au samedi : jours, salles,
 * créneaux, cases, occupants, capacité, conflits). Sans déplacement temporaire ni séance réalisée (autres classes du dossier).
 */
class GrilleDeLaSemaineCharacterizationIntegrationTest extends SemaineCharacterizationSupport {

    @Override
    protected String theme() {
        return "p6-semaine/grille";
    }

    @Test
    void reponse_complete_d_une_semaine_d_un_centre_minimal() throws Exception {
        petitDecor();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI, MERCREDI);
        lecture("reponse_complete", lireLaSemaine(1));
    }

    @Test
    void la_grille_du_decor_standard_liste_les_cases_occupees_et_la_capacite_par_salle() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI, MERCREDI, VENDREDI);
        place(CENTRE_A, "PAT_2", salle1, matin, g1b, LUNDI, MERCREDI, VENDREDI);
        place(CENTRE_A, "PAT_3", salle2, apresMidi, g2a, MARDI, JEUDI, SAMEDI);
        place(CENTRE_A, "PAT_4", salle1, apresMidi, g1a, DIMANCHE);
        arbre("decor_standard", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void la_semaine_commence_le_dimanche_quel_que_soit_le_jour_demande() throws Exception {
        petitDecor();
        nommerLaSemaine(0);
        nommerLaSemaine(1);
        nommerLaSemaine(2);
        Map<String, Object> m = new LinkedHashMap<>();
        for (LocalDate demande : List.of(semaine(1, DIMANCHE), semaine(1, LUNDI), semaine(1, MERCREDI), semaine(1, SAMEDI),
                semaine(2, DIMANCHE), semaine(0, SAMEDI))) {
            JsonNode s = corps(doGet(SEMAINE + "?date=" + demande, adminA()));
            m.put(demande.toString(), s.path("debut").asString() + " -> " + s.path("fin").asString());
        }
        arbre("debut_de_semaine", m);
    }

    @Test
    void les_semaines_passees_et_futures_sont_lues_a_toute_distance() throws Exception {
        petitDecor();
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        // admission très ancienne : le résultat ne dépend pas de la date d'exécution (jamais écrite dans un golden)
        jdbc.update("UPDATE patients SET date_admission = DATE '2000-01-01' WHERE id = ?", p);
        Map<String, Object> m = new LinkedHashMap<>();
        for (int n : new int[]{-60, -1, 0, 1, 52, 400}) {
            nommerLaSemaine(n);
            Resp r = doGet(SEMAINE + "?date=" + semaine(n, MERCREDI), adminA());
            JsonNode s = corps(r);
            m.put("semaine " + n, r.status() + " cellules=" + s.path("cellules").size() + " lundi=" + occupantsDuLundi(r));
        }
        arbre("passe_et_futur", m);
    }

    private List<String> occupantsDuLundi(Resp r) {
        List<String> l = new java.util.ArrayList<>();
        for (JsonNode c : corps(r).path("cellules")) {
            if ("LUNDI".equals(c.path("jour").asString())) c.path("occupants").forEach(o -> l.add(o.path("nom").asString()));
        }
        return l;
    }

    @Test
    void sans_date_la_semaine_courante_est_celle_d_aujourd_hui() throws Exception {
        petitDecor();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        LocalDate aujourdhui = jour(0);
        JsonNode sansDate = corps(doGet(SEMAINE, adminA()));
        JsonNode avecDate = corps(doGet(SEMAINE + "?date=" + aujourdhui, adminA()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("identique a ?date=aujourd'hui", sansDate.equals(avecDate));
        m.put("contient aujourd'hui", !aujourdhui.isBefore(LocalDate.parse(sansDate.path("debut").asString()))
                && !aujourdhui.isAfter(LocalDate.parse(sansDate.path("fin").asString())));
        m.put("sept jours", sansDate.path("jours").size());
        arbre("sans_date", m);
    }

    @Test
    void un_centre_sans_salle_et_sans_creneau_repond_une_grille_vide_meme_avec_des_patients_places() throws Exception {
        UUID s = salle(CENTRE_A, "SALLE_1", "Salle 1", "NON");
        UUID c = creneau(CENTRE_A, "CRENEAU_MATIN", "Matin");
        patientPlace(CENTRE_A, "PAT_1", s, c, LUNDI);
        jdbc.update("DELETE FROM salle WHERE center_id = ?", CENTRE_A);
        jdbc.update("DELETE FROM position_creneau WHERE center_id = ?", CENTRE_A);
        lecture("sans_salle_ni_creneau", lireLaSemaine(1));
    }

    @Test
    void un_centre_sans_creneau_ou_sans_salle_n_a_aucune_case() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        UUID s = salle(CENTRE_A, "SALLE_1", "Salle 1", "NON");
        UUID c = creneau(CENTRE_A, "CRENEAU_MATIN", "Matin");
        patientPlace(CENTRE_A, "PAT_1", s, c, LUNDI);
        jdbc.update("DELETE FROM position_creneau WHERE center_id = ?", CENTRE_A);
        m.put("sans creneau", projectionDeLaSemaine(lireLaSemaine(1)));
        UUID c2 = creneau(CENTRE_A, "CRENEAU_SOIR", "Soir");
        jdbc.update("UPDATE patients SET position_id = ? WHERE center_id = ?", c2, CENTRE_A);
        jdbc.update("DELETE FROM salle WHERE center_id = ?", CENTRE_A);
        m.put("sans salle", projectionDeLaSemaine(lireLaSemaine(1)));
        arbre("sans_creneau_ou_sans_salle", m);
    }

    @Test
    void un_centre_sans_aucune_donnee_repond_sept_jours_ouverts_et_aucune_case() throws Exception {
        lecture("centre_vide", lireLaSemaine(1));
    }

    @Test
    void les_salles_sont_triees_par_code_et_les_creneaux_par_code() throws Exception {
        salle(CENTRE_A, "Z_SALLE", "Aaa derniere par code", "NON");
        salle(CENTRE_A, "A_SALLE", "Zzz premiere par code", "NON");
        creneau(CENTRE_A, "Z_CRENEAU", "Soir");
        creneau(CENTRE_A, "A_CRENEAU", "Matin");
        Map<String, Object> m = projectionDeLaSemaine(lireLaSemaine(1));
        arbre("tri_salles_creneaux", Map.of("salles", m.get("salles"), "creneaux", m.get("creneaux")));
    }

    @Test
    void la_capacite_d_une_case_est_le_nombre_de_generateurs_en_service_de_la_salle_et_zero_un_jour_ferme() throws Exception {
        decorDePlacement();
        generateur(CENTRE_A, "G2B", salle2, "EN_SERVICE");
        generateur(CENTRE_A, "G_HS", salle2, "HORS_SERVICE");
        generateur(CENTRE_A, "G_MAINT", salle2, "EN_MAINTENANCE");
        generateur(CENTRE_A, "G_SANS_SALLE", null, "EN_SERVICE");
        equipement(CENTRE_A, "E_EAU", "STATION_TRAITEMENT_EAU", salle2, "EN_SERVICE");
        UUID supprime = generateur(CENTRE_A, "G_SUPPRIME", salle2, "EN_SERVICE");
        jdbc.update("UPDATE gmao_equipements SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", supprime);
        generateur(CENTRE_B, "G_AUTRE_CENTRE", salleB, "EN_SERVICE");
        joursOuvertsDuCentre(CENTRE_A, "LUNDI,MARDI");
        Map<String, Object> m = projectionDeLaSemaine(lireLaSemaine(1));
        arbre("capacite_des_cases", Map.of("capaciteParSalleEtJour", m.get("capaciteParSalleEtJour"), "jours", m.get("jours")));
    }

    @Test
    void les_noms_sont_prenom_puis_nom_et_les_occupants_d_une_case_sont_tries_par_ce_nom() throws Exception {
        petitDecor();
        generateur(CENTRE_A, "G1B", salle1, "EN_SERVICE");
        generateur(CENTRE_A, "G1C", salle1, "EN_SERVICE");
        UUID p1 = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        UUID p2 = place(CENTRE_A, "PAT_2", salle1, matin, null, LUNDI);
        UUID p3 = place(CENTRE_A, "PAT_3", salle1, matin, null, LUNDI);
        nommerPatient(p1, "Durand", "Zoe");
        nommerPatient(p2, "Martin", "Alice");
        nommerPatient(p3, "Bernard", "alice");
        arbre("noms_et_tri", projectionDeLaSemaine(lireLaSemaine(1)).get("cases"));
    }

    @Test
    void les_patients_sans_salle_ou_sans_creneau_ou_sans_jour_ou_d_un_autre_centre_n_apparaissent_pas() throws Exception {
        decorDePlacement();
        patientPlace(CENTRE_A, "SANS_JOUR", salle1, matin);
        UUID sansSalle = patientPlace(CENTRE_A, "SANS_SALLE", salle1, matin, LUNDI);
        jdbc.update("UPDATE patients SET salle_id = NULL WHERE id = ?", sansSalle);
        UUID sansCreneau = patientPlace(CENTRE_A, "SANS_CRENEAU", salle1, matin, LUNDI);
        jdbc.update("UPDATE patients SET position_id = NULL WHERE id = ?", sansCreneau);
        patientPlace(CENTRE_B, "AUTRE_CENTRE", salleB, creneauB, LUNDI);
        UUID autreSalle = patientPlace(CENTRE_A, "SALLE_B_INCONNUE", salle1, matin, LUNDI);
        jdbc.update("UPDATE patients SET salle_id = ? WHERE id = ?", salleB, autreSalle);
        UUID autreCreneau = patientPlace(CENTRE_A, "CRENEAU_B_INCONNU", salle1, matin, LUNDI);
        jdbc.update("UPDATE patients SET position_id = ? WHERE id = ?", creneauB, autreCreneau);
        arbre("patients_non_affiches", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void les_sept_jours_de_la_semaine_peuvent_tous_etre_occupes() throws Exception {
        petitDecor();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, DIMANCHE, LUNDI, MARDI, MERCREDI, JEUDI, VENDREDI, SAMEDI);
        arbre("sept_jours", projectionDeLaSemaine(lireLaSemaine(1)).get("cases"));
    }

    @Test
    void une_lecture_n_ecrit_rien_et_n_est_jamais_mise_en_cache() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        String avant = empreinteDesPlaces();
        Resp r = lireLaSemaine(1);
        String apres = empreinteDesPlaces();
        arbre("lecture_seule", Map.of("statut", r.status(), "cacheControl", String.valueOf(r.cacheControl()),
                "contentType", String.valueOf(r.contentType()), "base inchangee", avant.equals(apres),
                "diffusions", diffusions.size(),
                "notifications", jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE center_id = ?", Integer.class,
                        CENTRE_A)));
    }
}
