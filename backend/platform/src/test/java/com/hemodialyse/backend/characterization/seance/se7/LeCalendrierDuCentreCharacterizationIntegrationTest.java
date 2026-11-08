package com.hemodialyse.backend.characterization.seance.se7;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SE7a : le calendrier du centre, c'est-à-dire ses jours fériés et ses fermetures exceptionnelles
 * ({@code GET /api/v1/seances/calendar}, {@code POST|DELETE /api/v1/seances/calendar/holiday} et {@code .../closure}, servis par
 * {@code SeanceRestController} en SQL direct sur {@code center_holiday} et {@code center_closure_day}). Le garde de centre est déjà
 * verrouillé par DM1 ; le contenu, les bornes du mois, l'ajout, la suppression et les droits ne le sont pas. Décrit le comportement ACTUEL.
 */
class LeCalendrierDuCentreCharacterizationIntegrationTest extends Se7CharacterizationSupport {

    @Override
    protected String dossier() {
        return "calendrier";
    }

    // Mois de reference : janvier 2026 (REPERE du harnais = 2026-01-01 = <D+0>, 31 jours) ; les dates sont nommees par fixe(n).

    @Test
    void le_calendrier_d_un_mois_borne_ses_jours_ordonne_et_ignore_l_autre_centre() throws Exception {
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?,?,?,?)", idStable("ferie", "mai"), CENTRE_A,
                java.sql.Date.valueOf(fixe(-1)), "Veille du mois");
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?,?,?,?)", idStable("ferie", "debut"), CENTRE_A,
                java.sql.Date.valueOf(fixe(0)), "Premier du mois");
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?,?,?,?)", idStable("ferie", "fin"), CENTRE_A,
                java.sql.Date.valueOf(fixe(30)), "Dernier du mois");
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?,?,?,?)", idStable("ferie", "juillet"), CENTRE_A,
                java.sql.Date.valueOf(fixe(31)), "Lendemain du mois");
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?,?,?,?)", idStable("ferie", "milieu"), CENTRE_A,
                java.sql.Date.valueOf(fixe(14)), null);
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?,?,?,?)", idStable("ferie", "autre"), CENTRE_B,
                java.sql.Date.valueOf(fixe(3)), "Autre centre");
        jdbc.update("INSERT INTO center_closure_day (id, center_id, day_date, reason) VALUES (?,?,?,?)", idStable("fermeture", "a"), CENTRE_A,
                java.sql.Date.valueOf(fixe(9)), "Travaux");
        jdbc.update("INSERT INTO center_closure_day (id, center_id, day_date, reason) VALUES (?,?,?,?)", idStable("fermeture", "b"), CENTRE_B,
                java.sql.Date.valueOf(fixe(9)), "Autre centre");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 janvier 2026", reponse(calendrier(CENTRE_A, "2026", "1", infirmierA())));
        m.put("02 decembre 2025 (un seul jour, la veille)", reponse(calendrier(CENTRE_A, "2025", "12", infirmierA())));
        m.put("03 mois sans jour", reponse(calendrier(CENTRE_A, "2026", "3", infirmierA())));
        m.put("04 centre B", reponse(calendrier(CENTRE_B, "2026", "1", infirmierB())));
        arbre("lecture", m);
    }

    @Test
    void les_parametres_du_calendrier_hors_bornes_ou_illisibles() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 mois 0", refus(calendrier(CENTRE_A, "2026", "0", infirmierA())));
        m.put("02 mois 13", refus(calendrier(CENTRE_A, "2026", "13", infirmierA())));
        m.put("03 mois negatif", refus(calendrier(CENTRE_A, "2026", "-1", infirmierA())));
        m.put("04 annee 0", refus(calendrier(CENTRE_A, "0", "6", infirmierA())));
        m.put("05 annee 1000000000 (hors bornes)", refus(calendrier(CENTRE_A, "1000000000", "6", infirmierA())));
        m.put("06 annee absente", refus(calendrier(CENTRE_A, null, "6", infirmierA())));
        m.put("07 mois absent", refus(calendrier(CENTRE_A, "2026", null, infirmierA())));
        m.put("08 annee texte", refus(calendrier(CENTRE_A, "deux", "6", infirmierA())));
        m.put("09 annee 1900", refus(calendrier(CENTRE_A, "1900", "1", infirmierA())));
        m.put("10 centre absent", refus(doGet(SEANCES_API + "/calendar?year=2026&month=6", infirmierA())));
        arbre("parametres", m);
    }

    @Test
    void l_ajout_d_un_jour_ferie_et_d_une_fermeture_ecrit_une_ligne_et_ne_controle_ni_doublon_ni_date() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 ferie le 15/01", reponse(ajouterJour("holiday", CENTRE_A, fixe(14), "Fete", adminA())));
        m.put("02 meme ferie une seconde fois", reponse(ajouterJour("holiday", CENTRE_A, fixe(14), "Fete", adminA())));
        m.put("03 ferie sans libelle", refus(ajouterJour("holiday", CENTRE_A, fixe(15), null, adminA())));
        m.put("04 ferie sans date", refus(ajouterJour("holiday", CENTRE_A, null, "Sans date", adminA())));
        m.put("05 ferie dans le passe lointain (an 1900)", refus(ajouterJour("holiday", CENTRE_A, alias(LocalDate.of(1900, 1, 1), "<1900>"), "Ancien", adminA())));
        m.put("06 ferie avec un libelle de 2000 caracteres", refus(ajouterJour("holiday", CENTRE_A, fixe(16), "x".repeat(2000), adminA())));
        m.put("07 fermeture le 20/01", reponse(ajouterJour("closure", CENTRE_A, fixe(19), "Travaux", adminA())));
        m.put("08 fermeture en double", reponse(ajouterJour("closure", CENTRE_A, fixe(19), "Travaux", adminA())));
        m.put("09 fermeture sans motif", refus(ajouterJour("closure", CENTRE_A, fixe(20), null, adminA())));
        m.put("10 fermeture sans date", refus(ajouterJour("closure", CENTRE_A, null, "Sans date", adminA())));
        m.put("11 corps vide", refus(doPost(SEANCES_API + "/calendar/holiday", "{}", adminA())));
        m.put("12 corps absent", refus(doPost(SEANCES_API + "/calendar/holiday", "", adminA())));
        m.put("13 date illisible", refus(doPost(SEANCES_API + "/calendar/holiday", "{\"centerId\":\"" + CENTRE_A + "\",\"dayDate\":\"demain\"}", adminA())));
        m.put("14 centre B demande par la session A", refus(ajouterJour("holiday", CENTRE_B, fixe(0), "Autre centre", adminA())));
        arbre("ajout", m);
        Map<String, Object> etat = new LinkedHashMap<>();
        etat.put("jours feries du centre A", joursFeries(CENTRE_A));
        etat.put("fermetures du centre A", joursDeFermeture(CENTRE_A));
        etat.put("jours feries du centre B", joursFeries(CENTRE_B));
        etat.put("fermetures du centre B", joursDeFermeture(CENTRE_B));
        arbre("ajout_etat_en_base", etat);
    }

    @Test
    void la_suppression_d_un_jour_n_efface_que_la_ligne_du_centre_et_reussit_toujours() throws Exception {
        UUID ferie = idStable("ferie", "a");
        UUID ferieB = idStable("ferie", "b");
        UUID fermeture = idStable("fermeture", "a");
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?,?,?,?)", ferie, CENTRE_A,
                java.sql.Date.valueOf(fixe(0)), "Ferie A");
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?,?,?,?)", ferieB, CENTRE_B,
                java.sql.Date.valueOf(fixe(0)), "Ferie B");
        jdbc.update("INSERT INTO center_closure_day (id, center_id, day_date, reason) VALUES (?,?,?,?)", fermeture, CENTRE_A,
                java.sql.Date.valueOf(fixe(0)), "Fermeture A");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 suppression d'un ferie inconnu", reponse(supprimerJour("holiday", UUID.randomUUID(), CENTRE_A, adminA())));
        m.put("02 suppression d'un ferie par la route des fermetures (autre table)", reponse(supprimerJour("closure", ferie, CENTRE_A, adminA())));
        m.put("03 etat apres 01 et 02", Map.of("feries", joursFeries(CENTRE_A), "fermetures", joursDeFermeture(CENTRE_A)));
        m.put("04 suppression du ferie de l'autre centre avec le centre A", reponse(supprimerJour("holiday", ferieB, CENTRE_A, adminA())));
        m.put("05 le ferie de l'autre centre est toujours la", joursFeries(CENTRE_B));
        m.put("06 suppression sans centre", refus(supprimerJour("holiday", ferie, null, adminA())));
        m.put("07 suppression avec le centre B demande par la session A", refus(supprimerJour("holiday", ferieB, CENTRE_B, adminA())));
        m.put("08 identifiant illisible", refus(doDelete(SEANCES_API + "/calendar/holiday/xx?centerId=" + CENTRE_A, adminA())));
        m.put("09 suppression du ferie du centre A", reponse(supprimerJour("holiday", ferie, CENTRE_A, adminA())));
        m.put("10 suppression de la fermeture du centre A", reponse(supprimerJour("closure", fermeture, CENTRE_A, adminA())));
        m.put("11 seconde suppression de la fermeture", reponse(supprimerJour("closure", fermeture, CENTRE_A, adminA())));
        m.put("12 etat final du centre A", Map.of("feries", joursFeries(CENTRE_A), "fermetures", joursDeFermeture(CENTRE_A)));
        arbre("suppression", m);
    }

    @Test
    void les_droits_du_calendrier_par_profil_lecture_ouverte_aux_soignants_ecriture_reservee_a_l_administrateur() throws Exception {
        UUID ferie = idStable("ferie", "droits");
        Map<String, Object> m = new LinkedHashMap<>();
        int rang = 0;
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            rang++; // une date distincte par profil : le doublon de date est caracterise ailleurs (ajout)
            Map<String, String> profil = new LinkedHashMap<>();
            profil.put("GET calendar", refus(calendrier(CENTRE_A, "2026", "1", p.getValue())));
            profil.put("POST holiday", refus(ajouterJour("holiday", CENTRE_A, fixe(rang), "Droits", p.getValue())));
            profil.put("POST closure", refus(ajouterJour("closure", CENTRE_A, fixe(rang), "Droits", p.getValue())));
            profil.put("DELETE holiday", refus(supprimerJour("holiday", ferie, CENTRE_A, p.getValue())));
            profil.put("DELETE closure", refus(supprimerJour("closure", ferie, CENTRE_A, p.getValue())));
            m.put(p.getKey(), profil);
        }
        Map<String, String> anonyme = new LinkedHashMap<>();
        anonyme.put("GET calendar", refus(doGetAnonyme(SEANCES_API + "/calendar?centerId=" + CENTRE_A + "&year=2026&month=6")));
        anonyme.put("POST holiday", refus(doSansSession("POST", SEANCES_API + "/calendar/holiday",
                corps("centerId", CENTRE_A, "dayDate", fixe(0), "labelOrReason", "x"))));
        m.put("anonyme", anonyme);
        arbre("droits", m);
        arbre("droits_etat_en_base", Map.of("feries", joursFeries(CENTRE_A), "fermetures", joursDeFermeture(CENTRE_A)));
    }
}
