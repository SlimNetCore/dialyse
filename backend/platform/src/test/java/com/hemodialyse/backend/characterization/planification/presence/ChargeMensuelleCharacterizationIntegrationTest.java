package com.hemodialyse.backend.characterization.planification.presence;

import com.hemodialyse.backend.characterization.planification.PresenceCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation P4 : {@code GET /api/v1/infirmiers/presence/charge} (charge mensuelle : séances du roulement sur les
 * jours ouverts hors absences, remplacements du mois, jours d'absence, total, moyenne arrondie au dixième, pagination).
 * <p>
 * Mois de référence {@code 2026-02} : 28 jours, quatre occurrences exactes de chaque jour de la semaine (le premier jour est un
 * dimanche) : le résultat ne dépend pas de l'horloge. Les dates des fixtures sont des constantes de calendrier du mois de
 * référence (jamais affichées dans un golden). Le mois par défaut est comparé au mois explicite du jour.
 */
class ChargeMensuelleCharacterizationIntegrationTest extends PresenceCharacterizationSupport {

    private static LocalDate fev(int jour) {
        return LocalDate.of(2026, 2, jour);
    }

    @Override
    protected String theme() {
        return "p4-presence/charge";
    }

    private Resp charge(String requete, org.springframework.test.web.servlet.request.RequestPostProcessor who) throws Exception {
        return doGet(PRESENCE + "/charge" + requete, who);
    }

    private Resp chargeDeReference(String complement) throws Exception {
        return charge("?mois=" + MOIS_REFERENCE + complement, adminA());
    }

    // ------------------------------------------------------------------ calcul

    @Test
    void seances_du_roulement_sur_les_jours_du_mois_remplacements_absences_total_et_moyenne() throws Exception {
        decor();
        UUID a = infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, "LUNDI,MERCREDI");
        affectation(CENTRE_A, "AFF_A_AM", a, salle1, apresMidi, "LUNDI");
        infirmierAffecte(CENTRE_A, "INF_B", "Benali", "Karim", false, salle2, matin, tousLesJours());
        UUID c = inf(CENTRE_A, "INF_C", "Cherif", "Lina", false);
        remplacement(CENTRE_A, "REMPL_C_1", fev(10), salle1, matin, c, a);
        remplacement(CENTRE_A, "REMPL_C_2", fev(11), salle1, matin, c, null);
        fiche(CENTRE_A, "INF_OFF", "INF_OFF", "Desactive", "Nour", null, "INFIRMIER", false, false, null);
        affectation(CENTRE_A, "AFF_OFF", UUID.nameUUIDFromBytes("planification-p2-INF_OFF".getBytes()), salle1, matin, tousLesJours());
        Resp r = chargeDeReference("");
        lecture("charge_nominale_forme_complete", r);
        arbre("charge_nominale_lisible", projectionDeLaCharge(r));
    }

    @Test
    void jours_feries_fermetures_exceptionnelles_et_jours_non_ouverts_retirent_les_seances_mais_pas_les_jours_d_absence() throws Exception {
        decor();
        UUID a = infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, "LUNDI,MERCREDI");
        affectation(CENTRE_A, "AFF_A_AM", a, salle1, apresMidi, "LUNDI");
        arbre("sans_fermeture", projectionDeLaCharge(chargeDeReference("")));
        jourFerme(CENTRE_A, fev(9), "Ferie");                             // un lundi : deux séances en moins
        arbre("un_lundi_ferie", projectionDeLaCharge(chargeDeReference("")));
        fermetureExceptionnelle(CENTRE_A, fev(11), "Travaux");            // un mercredi : une séance en moins
        arbre("un_lundi_ferie_et_un_mercredi_ferme", projectionDeLaCharge(chargeDeReference("")));
        joursOuverts(CENTRE_A, "LUNDI,MARDI,JEUDI,VENDREDI,SAMEDI,DIMANCHE");   // plus aucun mercredi
        arbre("mercredis_non_ouverts", projectionDeLaCharge(chargeDeReference("")));
        absence(CENTRE_A, "ABS_FERIE", a, fev(9), fev(11), "CONGE", null);
        arbre("absence_sur_jours_fermes_et_ouverts", projectionDeLaCharge(chargeDeReference("")));
    }

    @Test
    void absences_chevauchant_le_mois_et_remplacements_hors_mois() throws Exception {
        decor();
        UUID a = infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, tousLesJours());
        UUID b = infirmierAffecte(CENTRE_A, "INF_B", "Benali", "Karim", false, salle2, matin, tousLesJours());
        UUID c = inf(CENTRE_A, "INF_C", "Cherif", "Lina", false);
        absence(CENTRE_A, "ABS_A_DEBUT", a, LocalDate.of(2026, 1, 30), fev(2), "CONGE", null);       // 2 jours de février
        absence(CENTRE_A, "ABS_B_FIN", b, fev(27), LocalDate.of(2026, 3, 3), "MALADIE", null);       // 2 jours de février
        absence(CENTRE_A, "ABS_C_TOUT", c, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "CONGE", null);
        remplacement(CENTRE_A, "REMPL_C_JAN", LocalDate.of(2026, 1, 31), salle1, matin, c, null);    // hors mois
        remplacement(CENTRE_A, "REMPL_C_MAR", LocalDate.of(2026, 3, 1), salle1, matin, c, null);     // hors mois
        remplacement(CENTRE_A, "REMPL_C_PREMIER", fev(1), salle1, matin, c, null);                   // premier jour : compté
        remplacement(CENTRE_A, "REMPL_C_DERNIER", fev(28), salle1, matin, c, null);                  // dernier jour : compté
        arbre("absences_et_remplacements_aux_bornes", projectionDeLaCharge(chargeDeReference("")));
    }

    @Test
    void un_remplacement_pose_un_jour_d_absence_ou_de_fermeture_n_est_pas_compte() throws Exception {
        decor();
        UUID c = inf(CENTRE_A, "INF_C", "Cherif", "Lina", false);
        absence(CENTRE_A, "ABS_C", c, fev(10), fev(10), "CONGE", null);
        jourFerme(CENTRE_A, fev(12), "Ferie");
        remplacement(CENTRE_A, "REMPL_ABSENT", fev(10), salle1, matin, c, null);
        remplacement(CENTRE_A, "REMPL_FERIE", fev(12), salle1, matin, c, null);
        arbre("remplacements_comptes_sans_condition", projectionDeLaCharge(chargeDeReference("")));
    }

    @Test
    void le_tri_est_sur_le_nom_de_famille_puis_le_prenom_et_les_desactives_sont_exclus_de_la_moyenne() throws Exception {
        decor();
        infirmierAffecte(CENTRE_A, "INF_1", "Amrani", "Sara", false, salle1, matin, "LUNDI");
        infirmierAffecte(CENTRE_A, "INF_2", "Zebda", "Alia", false, salle1, matin, "LUNDI,MARDI");
        infirmierAffecte(CENTRE_A, "INF_3", "Benali", null, false, salle1, matin, "LUNDI,MARDI,MERCREDI");
        infirmierAffecte(CENTRE_A, "INF_4", "Cherif", "Lina", false, salle1, matin, tousLesJours());
        inf(CENTRE_A, "INF_5", "Dahmani", "Omar", false);
        arbre("tri_sur_le_nom_affiche", projectionDeLaCharge(chargeDeReference("")));
    }

    @Test
    void sans_infirmier_actif_la_page_est_vide_et_la_moyenne_nulle_et_les_autres_centres_ne_comptent_pas() throws Exception {
        decor();
        infirmierAffecte(CENTRE_B, "INF_B", "Brahimi", "Nadia", false, salleB, creneauB, tousLesJours());
        lecture("aucun_infirmier_actif", chargeDeReference(""));
        arbre("centre_B_seul", projectionDeLaCharge(charge("?mois=" + MOIS_REFERENCE, as(CENTRE_B, "ADMIN"))));
    }

    // ------------------------------------------------------------------ pagination

    @Test
    void pagination_page_taille_defaut_vingt_hors_bornes_et_valeurs_illisibles() throws Exception {
        decor();
        for (int i = 1; i <= 5; i++) {
            infirmierAffecte(CENTRE_A, "INF_" + i, String.format("Nom%d", i), "Prenom", false, salle1, matin,
                    String.join(",", java.util.Arrays.copyOf(new String[]{"LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI"}, i)));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        for (String[] c : new String[][]{{"defaut", ""}, {"page 0 taille 2", "&page=0&size=2"}, {"page 1 taille 2", "&page=1&size=2"},
                {"page 2 taille 2", "&page=2&size=2"}, {"page 3 taille 2 (au-dela)", "&page=3&size=2"}, {"taille 0", "&size=0"},
                {"taille 1000", "&size=1000"}, {"taille negative", "&size=-1"}, {"page negative", "&page=-1&size=2"},
                {"page texte", "&page=abc"}, {"taille texte", "&size=abc"}, {"taille 100", "&size=100"},
                {"page tres grande", "&page=2147483647&size=2"}, {"produit page x taille deborde", "&page=1073741824&size=4"}}) {
            Resp resp = chargeDeReference(c[1]);
            r.put(c[0], resp.status() == 200 ? projectionDeLaCharge(resp) : statutEtCode(resp));
        }
        arbre("pagination", r);
    }

    // ------------------------------------------------------------------ mois

    @Test
    void formats_du_mois_accepte_ou_refuse_et_mois_par_defaut_egal_au_mois_courant_utc() throws Exception {
        decor();
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, "LUNDI");
        Map<String, Object> r = new LinkedHashMap<>();
        for (String[] c : new String[][]{{"yyyy-MM", "2026-02"}, {"yyyy-M", "2026-2"}, {"mois 13", "2026-13"}, {"texte", "abc"},
                {"vide", ""}, {"yyyyMM", "202602"}, {"date complete", "2026-02-01"}, {"mois 00", "2026-00"}, {"annee 5 chiffres", "12026-02"},
                {"annee signee", "+2026-02"}, {"espace", "%202026-02"}}) {
            Resp resp = charge("?mois=" + c[1], adminA());
            r.put("mois " + c[0], statutEtCode(resp) + (resp.status() == 200 ? " items=" + corps(resp).path("items").size() : ""));
        }
        arbre("formats_du_mois", r);
        lecture("mois_illisible_400", charge("?mois=abc", adminA()));
        Resp parDefaut = charge("", adminA());
        Resp explicite = charge("?mois=" + YearMonth.now(ZoneOffset.UTC), adminA());
        arbre("mois_par_defaut", Map.of("memeReponseQueLeMoisCourantUtc", parDefaut.raw().equals(explicite.raw()),
                "statut", parDefaut.status()));
    }

    @Test
    void les_bornes_de_calendrier_de_fevrier_a_quatre_occurrences_de_chaque_jour_sont_bien_celles_attendues() {
        org.junit.jupiter.api.Assertions.assertEquals(28, YearMonth.of(2026, 2).lengthOfMonth());
        org.junit.jupiter.api.Assertions.assertEquals(java.time.DayOfWeek.SUNDAY, fev(1).getDayOfWeek());
    }
}
