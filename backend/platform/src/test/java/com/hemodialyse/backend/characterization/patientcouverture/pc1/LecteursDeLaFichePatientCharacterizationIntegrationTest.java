package com.hemodialyse.backend.characterization.patientcouverture.pc1;

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

/**
 * PC1 : effet des écritures de la fiche (état, date d'événement, sommeil, admission) sur les lecteurs hors module : patients attendus
 * d'Absences (présence de Planification) et tableau de bord de la Séance. Les lecteurs de la grille de places et des propositions sont
 * verrouillés par P5/P7 ; Direction (effectif par centre) n'est pas rejoué ici (voir la note du filet).
 */
class LecteursDeLaFichePatientCharacterizationIntegrationTest extends PatientCouvertureCharacterizationSupport {

    @Autowired
    private PatientsAttendus attendus;

    @Override
    protected String dossier() {
        return "lecteurs";
    }

    private Map<String, Object> ficheEnPlace(String alias, String etat, Integer decalage, boolean sommeil) {
        Map<String, Object> f = ficheDuPatient(alias);
        f.put("salleId", salle1.toString());
        f.put("positionId", matin.toString());
        f.put("generateurId", g1a.toString());
        for (JourSemaine j : JourSemaine.values()) {
            f.put("jour" + j.name().charAt(0) + j.name().substring(1).toLowerCase(), true);
        }
        f.put("etatPatient", etat);
        if (decalage != null) f.put("dateEvenementEtat", jour(decalage).toString());
        f.put("enSommeil", sommeil);
        return f;
    }

    private List<String> attendusLe(LocalDate jour) {
        List<String> noms = new ArrayList<>();
        attendus.sansSeanceRealisee(new CentreId(CENTRE_A), jour).forEach(p -> noms.add(aliasNommes.getOrDefault(p.valeur().toString(), "?")));
        noms.sort(String::compareTo);
        return noms;
    }

    private Map<String, Object> attendusAutourDeAujourdhui() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int d = -2; d <= 2; d++) m.put("attendus " + (d == 0 ? "J" : d < 0 ? "J" + d : "J+" + d), attendusLe(jour(d)));
        return m;
    }

    @Test
    void l_etat_et_la_date_d_evenement_de_la_fiche_decident_des_patients_attendus_jour_par_jour() throws Exception {
        decorDePlacement();
        joursOuvertsDuCentre(CENTRE_A, TOUS_LES_JOURS);
        Object[][] cas = {
                {"PERMANENT", null, false}, {"EN_SOMMEIL", null, true}, {"TRANSFERE_J", 0, false}, {"TRANSFERE_J+1", 1, false},
                {"DECEDE_J", 0, false}, {"DECEDE_J+1", 1, false}, {"GREFFE_J", 0, false}, {"GUERRI_J", 0, false}, {"GUERRI_J-1", -1, false},
                {"OCCASIONNEL_J", 0, false}, {"OCCASIONNEL_J+1", 1, false}, {"VACANCIER_LOCAL_J", 0, false},
                {"VACANCIER_ETRANGER_J-1", -1, false}, {"OCCASIONNEL_SANS_FIN", null, false}};
        for (Object[] c : cas) {
            String alias = (String) c[0];
            place(CENTRE_A, alias, salle1, matin, g1a, JourSemaine.values());
            jdbc.update("UPDATE patients SET date_admission = ? WHERE id = ?", java.sql.Date.valueOf(jour(-400)), idDe(alias));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("avant : tous permanents", attendusAutourDeAujourdhui());
        Map<String, Object> statuts = new LinkedHashMap<>();
        for (Object[] c : cas) {
            String alias = (String) c[0];
            String etat = alias.startsWith("TRANSFERE") ? "TRANSFERE" : alias.startsWith("DECEDE") ? "DECEDE" : alias.startsWith("GREFFE") ? "GREFFE"
                    : alias.startsWith("GUERRI") ? "GUERRI" : alias.startsWith("OCCASIONNEL") ? "OCCASIONNEL"
                    : alias.startsWith("VACANCIER_LOCAL") ? "VACANCIER_LOCAL" : alias.startsWith("VACANCIER_ETRANGER") ? "VACANCIER_ETRANGER" : "PERMANENT";
            statuts.put(alias, refus(modifier(idDe(alias), ficheEnPlace(alias, etat, (Integer) c[1], (Boolean) c[2]), adminA())));
        }
        m.put("enregistrements", statuts);
        m.put("après les enregistrements", attendusAutourDeAujourdhui());
        arbre("attendus", m);
        etatDesMouvements("mouvements");
    }

    @Test
    void un_patient_admis_dans_le_futur_n_est_attendu_qu_a_partir_de_son_admission() throws Exception {
        decorDePlacement();
        joursOuvertsDuCentre(CENTRE_A, TOUS_LES_JOURS);
        Map<String, Object> f = ficheEnPlace("FUTUR", "PERMANENT", null, false);
        f.put("nom", "Futur");
        f.put("numeroAssurance", "PC1-FUTUR");
        f.put("dateAdmission", jour(3).toString());
        Resp r = creer("FUTUR", f, adminA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("création", refus(r));
        m.put("attendus", attendusAutourDeAujourdhui());
        Map<String, Object> apres = new LinkedHashMap<>();
        for (int d = 2; d <= 4; d++) apres.put("attendus J+" + d, attendusLe(jour(d)));
        m.put("autour de l'admission", apres);
        arbre("admission_future", m);
        etatDesMouvements("mouvement_d_admission");
    }

    @Test
    void le_tableau_de_bord_de_la_seance_compte_l_effectif_du_mois_selon_l_etat_des_fiches() throws Exception {
        Object[][] cas = {{"PERMANENT", null}, {"TRANSFERE", 100}, {"DECEDE", 100}, {"GUERRI", -100}, {"OCCASIONNEL", 100}, {"VACANCIER_LOCAL", -100},
                {"PERMANENT", null}};
        int i = 0;
        for (Object[] c : cas) {
            String alias = "D" + (++i);
            UUID id = patientSansPlace(alias);
            etat(id, (String) c[0], c[1] == null ? null : jour((Integer) c[1]));
        }
        jdbc.update("UPDATE patients SET en_sommeil = TRUE WHERE id = ?", idDe("D7"));
        LocalDate ce = aujourdhui();
        String mois = "?centerId=" + CENTRE_A + "&year=" + ce.getYear() + "&month=" + ce.getMonthValue();
        lectureDuMois("tableau_de_bord_du_mois", doGet("/api/v1/seances/dashboard" + mois, adminA()), ce.getYear(), ce.getMonthValue(),
                "<MOIS_COURANT>");
        lectureDuMois("tableau_de_bord_d_un_mois_lointain", doGet("/api/v1/seances/dashboard?centerId=" + CENTRE_A + "&year=2099&month=12", adminA()),
                2099, 12, "<MOIS_LOINTAIN>");
    }

    /** Année, mois et bornes du mois (premier et dernier jour) dépendent de l'exécution : masqués ; le reste du corps est verrouillé. */
    private void lectureDuMois(String name, Resp r, int annee, int mois, String libelle) {
        String texte = r.raw()
                .replaceAll(java.util.regex.Pattern.quote(String.format("%d-%02d", annee, mois)) + "-\\d\\d", libelle + "-JJ")
                .replace("\"year\":" + annee, "\"year\":\"<ANNEE>\"")
                .replace("\"month\":" + mois, "\"month\":\"<MOIS>\"");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.status());
        m.put("cacheControl", r.cacheControl());
        m.put("body", mapper.readTree(texte));
        arbre(name, m);
    }
}
