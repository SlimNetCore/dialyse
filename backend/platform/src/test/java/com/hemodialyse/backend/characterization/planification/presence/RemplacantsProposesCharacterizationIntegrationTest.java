package com.hemodialyse.backend.characterization.planification.presence;

import com.hemodialyse.backend.characterization.planification.PresenceCharacterizationSupport;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation P4 : {@code GET /api/v1/infirmiers/presence/remplacants} (candidats classés pour une case salle x
 * créneau x date : exclusions, score, raisons, limite de dix, paramètres obligatoires). Famille d'alias {@code <J+n>} ; le roulement
 * couvre les sept jours : la charge de la semaine (séances) ne dépend pas du jour d'exécution.
 */
class RemplacantsProposesCharacterizationIntegrationTest extends PresenceCharacterizationSupport {

    @Override
    protected String theme() {
        return "p4-presence/remplacants";
    }

    private void amorcer() {
        decor();
        nommerJours(-7, 14);
    }

    /** Nom du jour de la semaine au format du roulement (DIMANCHE à SAMEDI, comme {@link JourSemaine}). */
    private static String nomDuJour(LocalDate date) {
        return TOUS_LES_JOURS[date.getDayOfWeek().getValue() % 7].name();
    }

    private String url(LocalDate date, UUID salle, UUID creneau) {
        return PRESENCE + "/remplacants?date=" + date + "&salleId=" + salle + "&creneauId=" + creneau;
    }

    private List<String> candidats(Resp r) {
        List<String> lignes = new ArrayList<>();
        for (JsonNode c : corps(r)) {
            List<String> raisons = new ArrayList<>();
            c.path("raisons").forEach(x -> raisons.add(x.asString()));
            lignes.add(c.path("nom").asString() + " score=" + c.path("score").asInt() + " seances=" + c.path("seancesSemaine").asInt()
                    + " isolement=" + c.path("habiliteIsolement").asBoolean() + " " + c.path("qualification").asString() + " " + raisons);
        }
        return lignes;
    }

    @Test
    void forme_complete_d_un_candidat_et_classement_par_score_puis_par_nom() throws Exception {
        amorcer();
        // N1 : connait la salle 1 et le matin mais est déjà prévu sur le matin => exclu ; N2 : connait la salle 1 l'après-midi
        infirmierAffecte(CENTRE_A, "INF_PREVU", "Prevu", "Pierre", false, salle1, matin, tousLesJours());
        infirmierAffecte(CENTRE_A, "INF_AM", "Apresmidi", "Alia", false, salle1, apresMidi, tousLesJours());
        inf(CENTRE_A, "INF_LIBRE", "Libre", "Lina", false);
        inf(CENTRE_A, "INF_LIBRE2", "Autre", "Zoe", false);
        Resp r = doGet(url(jour(2), salle1, matin), adminA());
        lecture("forme_complete_et_classement", r);
        arbre("classement_lisible", candidats(r));
    }

    @Test
    void les_exclus_deja_prevu_sur_le_creneau_absent_ce_jour_la_desactive_et_autre_centre() throws Exception {
        amorcer();
        infirmierAffecte(CENTRE_A, "INF_PREVU_SALLE1", "Prevu1", "Anis", false, salle1, matin, tousLesJours());
        infirmierAffecte(CENTRE_A, "INF_PREVU_SALLE2", "Prevu2", "Bilal", false, salle2, matin, tousLesJours());
        UUID absent = infirmierAffecte(CENTRE_A, "INF_ABSENT", "Absent", "Chams", false, salle1, apresMidi, tousLesJours());
        absence(CENTRE_A, "ABS", absent, jour(2), jour(2), "CONGE", null);
        fiche(CENTRE_A, "INF_OFF", "INF_OFF", "Desactive", "Eden", null, "INFIRMIER", false, false, null);
        inf(CENTRE_B, "INF_B", "Autrecentre", "Farid", false);
        inf(CENTRE_A, "INF_OK", "Disponible", "Gaya", false);
        arbre("exclusions", candidats(doGet(url(jour(2), salle1, matin), adminA())));
    }

    @Test
    void en_salle_d_isolement_seuls_les_habilites_sont_proposes_avec_leur_bonus() throws Exception {
        amorcer();
        inf(CENTRE_A, "INF_HAB", "Habilite", "Sara", true);
        inf(CENTRE_A, "INF_NON", "Nonhabilite", "Karim", false);
        arbre("salle_isolement", candidats(doGet(url(jour(2), salleIsolement, matin), adminA())));
        arbre("salle_ordinaire_meme_effectif", candidats(doGet(url(jour(2), salle1, matin), adminA())));
    }

    @Test
    void les_raisons_et_les_points_salle_connue_creneau_habituel_charge_faible_double_vacation_jour_libre() throws Exception {
        amorcer();
        // salle connue + créneau habituel (autre jour du créneau : roulement sur le matin dans la salle 1 mais case saturée par ailleurs)
        infirmierAffecte(CENTRE_A, "INF_SALLE", "Salle", "Anis", false, salle1, apresMidi, tousLesJours());     // salle connue, double vacation
        // Le roulement est calculé par rapport au jour demandé (J+2) pour que le résultat ne dépende pas du jour d'exécution :
        // Bilal travaille cinq jours de la semaine, tous sauf le jour demandé et le lendemain (jour libre le J+2, 5 séances) ;
        // Chams travaille un seul jour, différent du jour demandé (jour libre le J+2, 1 séance).
        String jourDemande = nomDuJour(jour(2));
        String lendemain = nomDuJour(jour(3));
        List<String> cinqJours = new ArrayList<>();
        for (JourSemaine j : TOUS_LES_JOURS) {
            if (!j.name().equals(jourDemande) && !j.name().equals(lendemain)) cinqJours.add(j.name());
        }
        infirmierAffecte(CENTRE_A, "INF_CHARGE", "Charge", "Bilal", false, salle2, apresMidi, String.join(",", cinqJours));
        infirmierAffecte(CENTRE_A, "INF_ISO", "Iso", "Chams", true, salleIsolement, apresMidi, lendemain);
        inf(CENTRE_A, "INF_RIEN", "Rien", "Dalila", false);
        // remplaçant déjà posé le même jour dans une autre salle, sur l'autre créneau
        UUID deja = inf(CENTRE_A, "INF_DEJA", "Deja", "Eden", false);
        remplacement(CENTRE_A, "REMPL", jour(2), salle2, apresMidi, deja, null);
        arbre("points_par_raison_salle1_matin", candidats(doGet(url(jour(2), salle1, matin), adminA())));
        arbre("points_par_raison_salle2_apres_midi", candidats(doGet(url(jour(2), salle2, apresMidi), adminA())));
    }

    @Test
    void au_plus_dix_candidats_sont_proposes() throws Exception {
        amorcer();
        for (int i = 1; i <= 13; i++) {
            inf(CENTRE_A, "INF_" + i, String.format("Nom%02d", i), "Prenom", false);
        }
        Resp r = doGet(url(jour(2), salle1, matin), adminA());
        arbre("dix_candidats_maximum", Map.of("statut", r.status(), "nombre", corps(r).size(), "noms", candidats(r)));
    }

    @Test
    void les_parametres_obligatoires_et_illisibles_et_les_cases_inconnues() throws Exception {
        amorcer();
        inf(CENTRE_A, "INF_LIBRE", "Libre", "Lina", false);
        UUID inconnu = UUID.nameUUIDFromBytes("inconnu".getBytes());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sans date", statutEtCode(doGet(PRESENCE + "/remplacants?salleId=" + salle1 + "&creneauId=" + matin, adminA())));
        r.put("sans salle", statutEtCode(doGet(PRESENCE + "/remplacants?date=" + jour(2) + "&creneauId=" + matin, adminA())));
        r.put("sans creneau", statutEtCode(doGet(PRESENCE + "/remplacants?date=" + jour(2) + "&salleId=" + salle1, adminA())));
        r.put("sans aucun parametre", statutEtCode(doGet(PRESENCE + "/remplacants", adminA())));
        r.put("date illisible", statutEtCode(doGet(PRESENCE + "/remplacants?date=abc&salleId=" + salle1 + "&creneauId=" + matin, adminA())));
        r.put("salle illisible", statutEtCode(doGet(PRESENCE + "/remplacants?date=" + jour(2) + "&salleId=abc&creneauId=" + matin, adminA())));
        r.put("salle inconnue", statutEtCode(doGet(url(jour(2), inconnu, matin), adminA())) + " " + candidats(doGet(url(jour(2), inconnu, matin), adminA())));
        r.put("creneau inconnu", statutEtCode(doGet(url(jour(2), salle1, inconnu), adminA())) + " " + candidats(doGet(url(jour(2), salle1, inconnu), adminA())));
        r.put("salle de l'autre centre", statutEtCode(doGet(url(jour(2), salleB, creneauB), adminA())) + " " + candidats(doGet(url(jour(2), salleB, creneauB), adminA())));
        r.put("date passee (J-5)", statutEtCode(doGet(url(jour(-5), salle1, matin), adminA())) + " " + candidats(doGet(url(jour(-5), salle1, matin), adminA())));
        r.put("date lointaine (J+400)", statutEtCode(doGet(url(alias(jour(0).plusDays(400), "<J+400>"), salle1, matin), adminA())));
        arbre("parametres", r);
        lecture("sans_date_400", doGet(PRESENCE + "/remplacants?salleId=" + salle1 + "&creneauId=" + matin, adminA()));
    }

    @Test
    void un_jour_ferme_est_refuse_en_422_et_n_ajoute_pas_de_seance_a_la_charge_de_la_semaine() throws Exception {
        amorcer();
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, apresMidi, tousLesJours());
        jourFerme(CENTRE_A, jour(2), "Ferie");
        arbre("jour_ferme", candidats(doGet(url(jour(2), salle1, matin), adminA())));
    }
}
