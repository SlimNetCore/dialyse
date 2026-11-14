package com.hemodialyse.backend.characterization.dossiermedical.pl4;

import com.hemodialyse.backend.characterization.dossiermedical.dm8.Dm8CharacterizationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PL4a : volet paramedical des statistiques du patient ({@code GET stats/paramedical}, {@code POST stats/paramedical/search}).
 * <p>
 * Complete les filets existants (SE7e : comptes, bornes, statuts realises ; DM1 : garde de centre ; DM8 : statistiques medicales et exports) :
 * formes de tension, patient/centre vides, droits par profil, entrees mal formees, parite GET/POST, audit. Aucune ecriture, aucun mock.
 * Donnees fictives ; dates fixes nommees {@code <D+n>}.
 */
class LesStatistiquesParamedicalesDuPatientCharacterizationIntegrationTest extends Dm8CharacterizationSupport {

    @Override
    protected String dossier() {
        return "pl4_paramedical";
    }

    private UUID patientA;
    private UUID patientB;

    private void insererVolet(UUID centre, UUID seance, String avant, String apres, String taAvant, String taApres, String uf) {
        jdbc.update("INSERT INTO volet_paramedical (id, seance_id, center_id, poids_avant_kg, poids_apres_kg, ta_avant, ta_apres, ultrafiltration_ml, "
                        + "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), seance, centre, new BigDecimal(avant),
                new BigDecimal(apres), taAvant, taApres, uf == null ? null : new BigDecimal(uf), CREATION_FIXE, CREATION_FIXE);
    }

    private void jeuDeDonnees() {
        patientA = patientDm8(CENTRE_A, "PAT_A");
        UUID autre = patientDm8(CENTRE_A, "PAT_AUTRE");
        patientB = patientDm8(CENTRE_B, "PAT_B");
        insererVolet(CENTRE_A, seance(CENTRE_A, patientA, fixe(10), "VALIDEE", null, null, null), "70.0", "68.0", "130/80", "120-70", "2000");
        insererVolet(CENTRE_A, seance(CENTRE_A, patientA, fixe(20), "VALIDEE", null, null, null), "71.5", "69.5", "bizarre", null, null);
        insererVolet(CENTRE_A, seance(CENTRE_A, patientA, fixe(30), "SIGNEE", null, null, null), "72.0", "70.0", "9/5", "140/90 mmHg", "1500");
        insererVolet(CENTRE_A, seance(CENTRE_A, patientA, fixe(40), "FACTUREE", null, null, null), "73.0", "71.0", null, "1000/50", "1000");
        seance(CENTRE_A, patientA, fixe(50), "VALIDEE", null, null, null); // realisee sans volet
        insererVolet(CENTRE_A, seance(CENTRE_A, autre, fixe(25), "VALIDEE", null, null, null), "99.0", "98.0", "199/99", "199/99", "9999");
        insererVolet(CENTRE_B, seance(CENTRE_B, patientB, fixe(15), "VALIDEE", null, null, null), "80.0", "78.0", "110/70", "115/75", "800");
        prescriptionEnBase(CENTRE_A, patientA, "PRESC_1", fixe(5), "4000", "SC", 1, "SEMAINE");
        jdbc.update("UPDATE prescriptions_medicales SET poids_sec_cible_kg = 68.0 WHERE id = ?", idStable("prescription", "PRESC_1-" + CENTRE_A));
        prescriptionEnBase(CENTRE_A, patientA, "PRESC_2", fixe(35), "6000", "IV", 3, "SEMAINE");
        jdbc.update("UPDATE prescriptions_medicales SET poids_sec_cible_kg = 66.5 WHERE id = ?", idStable("prescription", "PRESC_2-" + CENTRE_A));
    }

    private String api(UUID patient, UUID centre, String suite) {
        return statsApi(patient, "paramedical") + "?centerId=" + centre + suite;
    }

    private String recherche(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return corps("centerId", centre, "patientId", patient, "dateFrom", du, "dateTo", au);
    }

    private static final String RECHERCHE = PATIENTS_API + "/stats/paramedical/search";

    private String jour(JsonNode n) {
        if (n.isMissingNode() || n.isNull()) return "-";
        if (n.isNumber()) return java.time.Instant.ofEpochMilli(n.asLong()).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
        try {
            return java.time.OffsetDateTime.parse(n.asString()).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate().toString();
        } catch (RuntimeException e) {
            return n.asString();
        }
    }

    private List<String> lignes(JsonNode tableau) {
        List<String> sortie = new ArrayList<>();
        for (JsonNode l : tableau) {
            List<String> champs = new ArrayList<>();
            l.properties().forEach(e -> champs.add(e.getKey() + "=" + (e.getKey().toLowerCase().startsWith("date") ? jour(e.getValue()) : e.getValue())));
            sortie.add(String.join(" ", champs));
        }
        return sortie;
    }

    /** Corps lisible : agregats, puis lignes dans l'ordre servi (les dates des fixtures sont toutes distinctes). */
    private Map<String, Object> synthese(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.status());
        if (r.status() != 200) {
            m.put("corps", refus(r));
            return m;
        }
        JsonNode c = corps(r);
        List<String> cles = new ArrayList<>();
        c.properties().forEach(e -> cles.add(e.getKey()));
        m.put("cles", cles);
        for (String cle : List.of("seanceCount", "avgPoidsAvantKg", "avgPoidsApresKg", "avgUfReelleMl", "poidsSecCibleKg")) {
            m.put(cle, c.path(cle).isMissingNode() ? "absent" : c.path(cle).toString());
        }
        m.put("poidsEvolution", lignes(c.path("poidsEvolution")));
        m.put("taEvolution", lignes(c.path("taEvolution")));
        return m;
    }

    @Test
    void les_tensions_texte_sont_decoupees_en_systolique_et_diastolique_et_illisibles_rendues_nulles() throws Exception {
        jeuDeDonnees();
        String avant = empreinteLecteurs();
        Resp r = doGet(api(patientA, CENTRE_A, ""), infirmierA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 reponse", synthese(r));
        m.put("02 en-tete Cache-Control", r.cacheControl());
        Resp post = doPost(RECHERCHE, recherche(CENTRE_A, patientA, null, null), infirmierA());
        m.put("03 la recherche par corps rend exactement la meme chose que la lecture", r.raw().equals(post.raw()));
        m.put("04 type et format du corps ignores", doPost(RECHERCHE, corps("centerId", CENTRE_A, "patientId", patientA, "type", "N_IMPORTE_QUOI",
                "format", "pdf"), infirmierA()).raw().equals(r.raw()));
        m.put("05 aucune ecriture", empreinteLecteurs().equals(avant));
        arbre("tensions_et_agregats", m);
    }

    @Test
    void la_periode_filtre_les_seances_et_le_poids_sec_cible_est_celui_en_vigueur_a_la_fin_de_la_periode() throws Exception {
        jeuDeDonnees();
        Map<String, Object> m = new LinkedHashMap<>();
        String[][] cas = {
                {"du 11 au 29 (une seule seance)", "&from=" + fixe(11) + "&to=" + fixe(29)},
                {"jusqu'a D+3 (poids sec : aucune prescription encore en vigueur)", "&to=" + fixe(3)},
                {"jusqu'a D+34 (poids sec de la premiere prescription)", "&to=" + fixe(34)},
                {"jusqu'a D+35 (borne incluse : seconde prescription)", "&to=" + fixe(35)},
                {"periode vide apres toutes les seances", "&from=" + fixe(200) + "&to=" + fixe(210)},
                {"from posterieur a to", "&from=" + fixe(40) + "&to=" + fixe(10)},
        };
        int i = 0;
        for (String[] c : cas) {
            m.put(String.format("%02d %s", ++i, c[0]), synthese(doGet(api(patientA, CENTRE_A, c[1]), infirmierA())));
        }
        m.put("07 recherche : dateFrom posterieure a dateTo", synthese(doPost(RECHERCHE, recherche(CENTRE_A, patientA, fixe(40), fixe(10)), infirmierA())));
        m.put("08 recherche : un seul jour", synthese(doPost(RECHERCHE, recherche(CENTRE_A, patientA, fixe(20), fixe(20)), infirmierA())));
        arbre("periodes", m);
    }

    @Test
    void sans_donnee_les_moyennes_valent_zero_et_les_listes_sont_vides_et_un_patient_inconnu_ou_d_un_autre_centre_est_refuse_en_404() throws Exception {
        jeuDeDonnees();
        UUID sans = patientDm8(CENTRE_A, "PAT_SANS");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 patient sans seance", synthese(doGet(api(sans, CENTRE_A, ""), infirmierA())));
        m.put("02 patient inconnu", synthese(doGet(api(idStable("patient", "inconnu"), CENTRE_A, ""), infirmierA())));
        m.put("03 patient du centre B demande par la session A avec centerId=A", synthese(doGet(api(patientB, CENTRE_A, ""), infirmierA())));
        m.put("04 patient du centre A demande par la session B avec centerId=B", synthese(doGet(api(patientA, CENTRE_B, ""),
                as(CENTRE_B, "INFIRMIER"))));
        m.put("05 le centre B voit ses propres seances", synthese(doGet(api(patientB, CENTRE_B, ""), as(CENTRE_B, "INFIRMIER"))));
        m.put("06 recherche : patient sans seance", synthese(doPost(RECHERCHE, recherche(CENTRE_A, sans, null, null), infirmierA())));
        arbre("sans_donnees_nouveau_code", m);
    }

    @Test
    void les_entrees_mal_formees_sont_refusees_en_400_et_la_garde_de_centre_passe_avant_la_plage() throws Exception {
        jeuDeDonnees();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 GET centerId absent", refus(doGet(statsApi(patientA, "paramedical"), infirmierA())));
        m.put("02 GET centerId illisible", refus(doGet(statsApi(patientA, "paramedical") + "?centerId=abc", infirmierA())));
        m.put("03 GET patientId illisible", refus(doGet(PATIENTS_API + "/pas-un-uuid/stats/paramedical?centerId=" + CENTRE_A, infirmierA())));
        m.put("04 GET from illisible", refus(doGet(api(patientA, CENTRE_A, "&from=pas-une-date"), infirmierA())));
        m.put("05 GET to au mauvais format (jj/mm/aaaa)", refus(doGet(api(patientA, CENTRE_A, "&to=31/01/2026"), infirmierA())));
        m.put("06 GET from posterieur a to", refus(doGet(api(patientA, CENTRE_A, "&from=" + fixe(40) + "&to=" + fixe(10)), infirmierA())));
        m.put("07 POST centerId absent", refus(doPost(RECHERCHE, corps("patientId", patientA), infirmierA())));
        m.put("08 POST patientId absent", refus(doPost(RECHERCHE, corps("centerId", CENTRE_A), infirmierA())));
        m.put("09 POST dateFrom posterieure a dateTo", refus(doPost(RECHERCHE, recherche(CENTRE_A, patientA, fixe(40), fixe(10)), infirmierA())));
        m.put("10 POST corps illisible", refus(doPost(RECHERCHE, "{pas du json", infirmierA())));
        m.put("11 POST corps absent", refus(doPost(RECHERCHE, "", infirmierA())));
        m.put("12 POST dateFrom illisible", refus(doPost(RECHERCHE, "{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patientA
                + "\",\"dateFrom\":\"pas-une-date\"}", infirmierA())));
        m.put("13 POST centre d'un autre et plage inversee : garde de centre d'abord (KB-PL4-5)", refus(doPost(RECHERCHE,
                recherche(CENTRE_B, patientB, fixe(40), fixe(10)), infirmierA())));
        m.put("14 GET centre d'un autre et plage inversee : garde de centre d'abord (KB-PL4-5)", refus(doGet(api(patientB, CENTRE_B,
                "&from=" + fixe(40) + "&to=" + fixe(10)), infirmierA())));
        arbre("entrees_mal_formees_nouveau_code", m);
    }

    @Test
    void les_statistiques_paramedicales_sont_ouvertes_a_quatre_roles_et_le_centre_de_session_est_impose() throws Exception {
        jeuDeDonnees();
        String avant = empreinteLecteurs();
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm8().entrySet()) {
            Map<String, String> routes = new LinkedHashMap<>();
            routes.put("01 GET stats/paramedical", statutEtCode(doGet(api(patientA, CENTRE_A, ""), p.getValue())));
            routes.put("02 POST stats/paramedical/search", statutEtCode(doPost(RECHERCHE, recherche(CENTRE_A, patientA, null, null), p.getValue())));
            profils.put(p.getKey(), regrouper(routes));
        }
        Map<String, String> anonyme = new LinkedHashMap<>();
        anonyme.put("lecture", statutEtCode(appel("GET", api(patientA, CENTRE_A, ""), null, null)));
        anonyme.put("recherche", statutEtCode(appel("POST", RECHERCHE, recherche(CENTRE_A, patientA, null, null), null)));
        profils.put("anonyme", anonyme);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 profils", profils);
        Map<String, String> centre = new LinkedHashMap<>();
        centre.put("01 GET session A vise le centre B", refus(doGet(api(patientB, CENTRE_B, ""), infirmierA())));
        centre.put("02 POST session A vise le centre B", refus(doPost(RECHERCHE, recherche(CENTRE_B, patientB, null, null), infirmierA())));
        centre.put("03 GET centerId inconnu", refus(doGet(api(patientA, idStable("centre", "inconnu"), ""), infirmierA())));
        centre.put("04 GET INFIRMIER sans centre de session", refus(doGet(api(patientA, CENTRE_A, ""), as(null, "INFIRMIER"))));
        centre.put("05 POST ADMIN sans centre de session", refus(doPost(RECHERCHE, recherche(CENTRE_A, patientA, null, null), as(null, "ADMIN"))));
        centre.put("06 GET SUPERADMIN + ADMIN (session A) vise le centre B", refus(doGet(api(patientB, CENTRE_B, ""), as(CENTRE_A, "SUPERADMIN", "ADMIN"))));
        m.put("02 garde de centre", centre);
        m.put("03 aucune ecriture", empreinteLecteurs().equals(avant));
        arbre("droits_et_garde_de_centre", m);
    }

    /**
     * LEC-PLN-GMAO (c), ADR 0180 : filet AJOUTÉ avant le déplacement du départage du poids sec dans le Dossier médical. À date de prescription
     * égale, la prescription mise à jour en dernier l'emporte (mise à jour inconnue en dernier), puis l'identifiant le plus élevé.
     */
    @Test
    void a_date_de_prescription_egale_le_poids_sec_de_chaque_seance_est_celui_mis_a_jour_en_dernier_puis_l_identifiant_le_plus_eleve() throws Exception {
        UUID patient = patientDm8(CENTRE_A, "PAT_EGALITE");
        for (int jour : new int[]{50, 60, 65, 80, 85}) {
            insererVolet(CENTRE_A, seance(CENTRE_A, patient, fixe(jour), "VALIDEE", null, null, null), "70.0", "68.0", "130/80", "120-70", "2000");
        }
        // D+60 : mise à jour inconnue contre connue (la connue l'emporte)
        prescriptionEnBase(CENTRE_A, patient, "EGAL_1", fixe(60), "4000", "SC", 1, "SEMAINE");
        jdbc.update("UPDATE prescriptions_medicales SET poids_sec_cible_kg = 60.0, updated_at = NULL WHERE id = ?", idStable("prescription", "EGAL_1-" + CENTRE_A));
        prescriptionEnBase(CENTRE_A, patient, "EGAL_2", fixe(60), "4000", "SC", 1, "SEMAINE");
        jdbc.update("UPDATE prescriptions_medicales SET poids_sec_cible_kg = 61.0, updated_at = ? WHERE id = ?", CREATION_FIXE.plusHours(1),
                idStable("prescription", "EGAL_2-" + CENTRE_A));
        // D+80 : même date, même mise à jour : l'identifiant le plus élevé l'emporte
        prescriptionEnBase(CENTRE_A, patient, "EGAL_3", fixe(80), "4000", "SC", 1, "SEMAINE");
        jdbc.update("UPDATE prescriptions_medicales SET poids_sec_cible_kg = 70.5 WHERE id = ?", idStable("prescription", "EGAL_3-" + CENTRE_A));
        prescriptionEnBase(CENTRE_A, patient, "EGAL_4", fixe(80), "4000", "SC", 1, "SEMAINE");
        jdbc.update("UPDATE prescriptions_medicales SET poids_sec_cible_kg = 71.5 WHERE id = ?", idStable("prescription", "EGAL_4-" + CENTRE_A));
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> reponse = synthese(doGet(api(patient, CENTRE_A, ""), infirmierA()));
        // le poids sec de l'en-tête (poidsSecCibleEnVigueur) départage autrement (sans identifiant) : valeur laissée au SGBD à égalité, hors filet
        reponse.remove("poidsSecCibleKg");
        m.put("01 reponse", reponse);
        arbre("egalite_de_date", m);
    }

    @Test
    void les_lectures_paramedicales_ne_sont_pas_journalisees_mais_la_recherche_par_corps_l_est_comme_une_consultation() throws Exception {
        jeuDeDonnees();
        monterLAudit();
        doGet(api(patientA, CENTRE_A, ""), infirmierA());
        doPost(RECHERCHE, recherche(CENTRE_A, patientA, null, null), infirmierA());
        doPost(RECHERCHE, recherche(CENTRE_A, patientA, null, null), as(CENTRE_A, "SECRETAIRE"));
        doPost(RECHERCHE, recherche(CENTRE_B, patientB, null, null), infirmierA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit_nouveau_code", m);
    }
}
