package com.hemodialyse.backend.characterization.planification.presence;

import com.hemodialyse.backend.characterization.planification.PresenceCharacterizationSupport;
import com.hemodialyse.backend.characterization.planification.JourSemaine;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation P4 : effets des écritures de la slice P3 (absences, remplacements, roulement) et de P2 (désactivation)
 * sur les lectures de présence (alertes, remplaçants, charge). Chaque écriture passe par HTTP (contrôleurs P2 et P3 réels) ; la
 * lecture qui suit montre l'effet. Famille d'alias {@code <J+n>} ; patients sur les sept jours ; la charge n'est observée qu'en
 * différence (sa valeur absolue dépend du calendrier du mois).
 */
class EffetsDeP3SurLaPresenceCharacterizationIntegrationTest extends PresenceCharacterizationSupport {

    private UUID amrani;
    private UUID cherif;
    private LocalDate j1;
    private LocalDate j2;

    @Override
    protected String theme() {
        return "p4-presence/effets-p3";
    }

    private void amorcer() {
        decor();
        nommerJours(-1, 16);
        j1 = jour(1);
        j2 = jour(2);
        patientsPlaces(CENTRE_A, "PAT", 4, salle1, matin, JourSemaine.values());
        amrani = infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, tousLesJours());
        cherif = inf(CENTRE_A, "INF_C", "Cherif", "Lina", false);
    }

    private List<String> alertesSur(int jours) throws Exception {
        return lignesDAlertes(alertes("?jours=" + jours, adminA()));
    }

    private UUID idUnique(String table, String colonne, UUID valeur) {
        return jdbc.queryForObject("SELECT id FROM " + table + " WHERE " + colonne + " = ?", UUID.class, valeur);
    }

    @Test
    void declarer_puis_retirer_une_absence_cree_puis_leve_les_alertes() throws Exception {
        amorcer();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("avant", alertesSur(4));
        Resp post = doPost(INFIRMIERS + "/absences", json(corpsAbsence(amrani, j1, j2, "CONGE", "Conge")), adminA());
        r.put("declaration", statutEtCode(post));
        r.put("apres_declaration", alertesSur(4));
        UUID absenceId = idUnique("infirmier_absence", "infirmier_id", amrani);
        r.put("retrait", statutEtCode(doDelete(INFIRMIERS + "/absences/" + absenceId, adminA())));
        r.put("apres_retrait", alertesSur(4));
        arbre("absence_declaree_puis_retiree", r);
    }

    @Test
    void un_remplacement_pose_par_l_administration_leve_l_alerte_du_jour_et_retire_le_candidat_de_la_liste() throws Exception {
        amorcer();
        absence(CENTRE_A, "ABS_A", amrani, j1, j2, "CONGE", null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("avant", alertesSur(4));
        r.put("remplacants_j1_avant", noms(doGet(PRESENCE + "/remplacants?date=" + j1 + "&salleId=" + salle1 + "&creneauId=" + matin, adminA())));
        int remplacementsAvant = remplacementsDansLaCharge(j1, "Lina Cherif");
        Resp post = doPost(INFIRMIERS + "/presence/remplacements", json(corpsRemplacement(j1, salle1, matin, cherif, amrani)), adminA());
        r.put("pose", statutEtCode(post));
        r.put("apres_pose", alertesSur(4));
        r.put("remplacants_j1_apres", noms(doGet(PRESENCE + "/remplacants?date=" + j1 + "&salleId=" + salle1 + "&creneauId=" + matin, adminA())));
        r.put("remplacements_dans_la_charge_du_mois_en_plus", remplacementsDansLaCharge(j1, "Lina Cherif") - remplacementsAvant);
        UUID id = idUnique("infirmier_remplacement", "infirmier_id", cherif);
        r.put("retrait", statutEtCode(doDelete(INFIRMIERS + "/presence/remplacements/" + id, adminA())));
        r.put("apres_retrait", alertesSur(4));
        arbre("remplacement_pose_puis_retire", r);
    }

    @Test
    void ajouter_puis_retirer_une_affectation_du_roulement_couvre_puis_decouvre_la_case() throws Exception {
        amorcer();
        absence(CENTRE_A, "ABS_A", amrani, j1, j2, "CONGE", null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("avant", alertesSur(4));
        r.put("ajout", statutEtCode(doPost(INFIRMIERS + "/" + cherif + "/affectations",
                json(corpsAffectation(salle1, matin, "DIMANCHE", "LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI")), adminA())));
        r.put("apres_ajout", alertesSur(4));
        UUID affectation = idUnique("infirmier_affectation", "infirmier_id", cherif);
        r.put("retrait", statutEtCode(doDelete(INFIRMIERS + "/" + cherif + "/affectations/" + affectation, adminA())));
        r.put("apres_retrait", alertesSur(4));
        arbre("roulement_ajoute_puis_retire", r);
    }

    @Test
    void desactiver_puis_reactiver_une_fiche_retire_puis_remet_l_infirmier_dans_les_effectifs_et_la_charge() throws Exception {
        amorcer();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("avant", alertesSur(2));
        r.put("fiches_dans_la_charge_avant", corps(doGet(PRESENCE + "/charge?mois=" + MOIS_REFERENCE, adminA())).path("total").asLong());
        r.put("desactivation", statutEtCode(doPost(INFIRMIERS + "/" + amrani + "/desactiver", "", adminA())));
        r.put("apres_desactivation", alertesSur(2));
        r.put("fiches_dans_la_charge_apres", corps(doGet(PRESENCE + "/charge?mois=" + MOIS_REFERENCE, adminA())).path("total").asLong());
        r.put("reactivation", statutEtCode(doPost(INFIRMIERS + "/" + amrani + "/reactiver", "", adminA())));
        r.put("apres_reactivation", alertesSur(2));
        arbre("fiche_desactivee_puis_reactivee", r);
    }

    @Test
    void une_ecriture_refusee_ne_change_aucune_lecture() throws Exception {
        amorcer();
        String avant = String.valueOf(alertesSur(4));
        Resp refus = doPost(INFIRMIERS + "/absences", json(corpsAbsence(amrani, j1, j2, "CONGE", null)), as(CENTRE_A, "MEDECIN", "INFIRMIER"));
        Resp invalide = doPost(INFIRMIERS + "/absences", json(corpsAbsence(amrani, j2, j1, "CONGE", null)), adminA());
        arbre("ecritures_refusees", Map.of("refus", statutEtCode(refus), "periodeInversee", statutEtCode(invalide),
                "alertesInchangees", avant.equals(String.valueOf(alertesSur(4)))));
    }

    private List<String> noms(Resp r) {
        List<String> noms = new java.util.ArrayList<>();
        corps(r).forEach(c -> noms.add(c.path("nom").asString() + ":" + c.path("score").asInt()));
        return noms;
    }
}
