package com.hemodialyse.backend.characterization.planification.roulement;

import com.hemodialyse.backend.characterization.planification.RoulementCharacterizationSupport;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation P3 : absences des infirmiers saisies par l'administration et le secrétariat
 * ({@code /api/v1/infirmiers/absences}). Les dates sont des semaines nommées ({@code <S1.LUN>}) : aucune règle de ces routes ne
 * compare à « aujourd'hui » (l'administration saisit aussi le passé).
 */
class AbsencesDesInfirmiersCharacterizationIntegrationTest extends RoulementCharacterizationSupport {

    private static final String ABSENCES = INFIRMIERS + "/absences";
    private UUID amrani;
    private UUID benali;
    private UUID chikhi;
    private UUID duCentreB;

    @Override
    protected String theme() {
        return "p3-roulement/absences";
    }

    @BeforeEach
    void amorcer() {
        decor();
        amrani = inf(CENTRE_A, "INF_AMRANI", "Amrani", "Sara", true);
        benali = inf(CENTRE_A, "INF_BENALI", "Benali", "Karim", false);
        chikhi = fiche(CENTRE_A, "INF_CHIKHI", "CHIKHI", "Chikhi", "Nadia", null, "AIDE_SOIGNANT", false, false, null);
        duCentreB = inf(CENTRE_B, "INF_CENTRE_B", "Zerrouki", "Rania", false);
    }

    private LocalDate s(int semaine, JourSemaine jour) {
        return semaine(semaine, jour);
    }

    @Test
    void la_liste_est_vide_sans_absence() throws Exception {
        lecture("liste_vide", doGet(ABSENCES, adminA()));
    }

    @Test
    void la_liste_est_triee_par_debut_decroissant_puis_identifiant_et_bornee_au_centre() throws Exception {
        absence(CENTRE_A, "ABS_ANCIENNE", amrani, s(1, JourSemaine.LUNDI), s(1, JourSemaine.MARDI), "CONGE", "Congé annuel");
        absence(CENTRE_A, "ABS_RECENTE", benali, s(2, JourSemaine.MERCREDI), s(2, JourSemaine.JEUDI), "MALADIE", null);
        absence(CENTRE_A, "ABS_MEME_DEBUT", chikhi, s(1, JourSemaine.LUNDI), s(1, JourSemaine.SAMEDI), "FORMATION", "Stage");
        absence(CENTRE_B, "ABS_AUTRE_CENTRE", duCentreB, s(1, JourSemaine.LUNDI), s(1, JourSemaine.LUNDI), "AUTRE", null);
        lecture("liste_ordre_et_centre", doGet(ABSENCES, adminA()));
        lecture("liste_du_centre_b", doGet(ABSENCES + "?centerId=" + CENTRE_B, adminB()));
    }

    @Test
    void la_pagination_de_la_liste_reproduit_les_conventions_du_legacy() throws Exception {
        for (int i = 0; i < 3; i++) {
            absence(CENTRE_A, "ABS_" + i, amrani, s(1, JourSemaine.LUNDI).plusDays(i), s(1, JourSemaine.LUNDI).plusDays(i), "CONGE", null);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        for (String q : new String[]{"?page=0&size=2", "?page=1&size=2", "?page=2&size=2", "?size=0", "?size=1000", "?page=-1", "?size=-1",
                "?page=abc", "?size=", "?size=500&page=0"}) {
            Resp x = doGet(ABSENCES + q, adminA());
            JsonNodeResume.ajouter(r, q, x, mapper);
        }
        arbre("pagination", r);
    }

    @Test
    void l_administrateur_declare_une_absence_valide_et_seul_le_medecin_est_prevenu() throws Exception {
        semaine(1);
        Resp r = doPost(ABSENCES, json(corpsAbsence(amrani, s(1, JourSemaine.LUNDI), s(1, JourSemaine.MERCREDI), "CONGE", "Congé annuel")), adminA());
        assertEquals(201, r.status());
        ecriture("declaration_par_l_administrateur", r);
        etatAbsences("declaration_par_l_administrateur_base");
        auteursDesAbsences("declaration_par_l_administrateur_auteur");
        notifications("declaration_par_l_administrateur_notification");
        diffusionsCapturees("declaration_par_l_administrateur_diffusions");
    }

    @Test
    void le_secretariat_declare_aussi_et_l_administrateur_est_alors_prevenu_avec_le_medecin() throws Exception {
        semaine(1);
        Resp r = doPost(ABSENCES, json(corpsAbsence(amrani, s(1, JourSemaine.LUNDI), s(1, JourSemaine.LUNDI), "MALADIE", null)), secretaire());
        assertEquals(201, r.status());
        ecriture("declaration_par_le_secretariat", r);
        auteursDesAbsences("declaration_par_le_secretariat_auteur");
        notifications("declaration_par_le_secretariat_notification");
        diffusionsCapturees("declaration_par_le_secretariat_diffusions");
    }

    @Test
    void les_bornes_de_duree_sont_un_jour_au_minimum_et_366_jours_au_maximum_bornes_incluses() throws Exception {
        LocalDate debut = s(1, JourSemaine.LUNDI);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("1 jour (début = fin)", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, debut, debut, "CONGE", null)), adminA())));
        r.put("366 jours", statutEtCode(doPost(ABSENCES, json(corpsAbsence(benali, debut, alias(debut.plusDays(365), "<S1.LUN+365>"), "CONGE", null)), adminA())));
        r.put("367 jours", statutEtCode(doPost(ABSENCES, json(corpsAbsence(chikhi, debut, debut.plusDays(366), "CONGE", null)), adminA())));
        r.put("fin la veille du début", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, debut, debut.minusDays(1), "CONGE", null)), adminA())));
        arbre("bornes_de_duree", r);
        etatAbsences("bornes_de_duree_base");
        ecriture("refus_367_jours", doPost(ABSENCES, json(corpsAbsence(chikhi, debut, debut.plusDays(366), "CONGE", null)), adminA()));
        ecriture("refus_fin_avant_debut", doPost(ABSENCES, json(corpsAbsence(chikhi, debut, debut.minusDays(1), "CONGE", null)), adminA()));
    }

    @Test
    void l_annee_bissextile_compte_366_jours_exactement() throws Exception {
        // Première année bissextile strictement postérieure à l'année d'exécution (revue de P9, m8 : plus de date fixe 2028 qui
        // deviendrait passée) : du 1er janvier au 31 décembre = 366 jours (accepté), un jour de plus = 367 (refusé). Les dates sont
        // nommées par alias : le golden ne dépend pas de l'année calculée.
        int annee = aujourdhui().getYear() + 1;
        while (!java.time.Year.isLeap(annee)) annee++;
        LocalDate debut = LocalDate.of(annee, 1, 1);
        LocalDate fin = LocalDate.of(annee, 12, 31);
        LocalDate finPlus1 = LocalDate.of(annee + 1, 1, 1);
        datesNommees.put(debut.toString(), "<BISSEXTILE_DEBUT>");
        datesNommees.put(fin.toString(), "<BISSEXTILE_FIN>");
        datesNommees.put(finPlus1.toString(), "<BISSEXTILE_FIN_PLUS_1>");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put(debut + " au " + fin, statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, debut, fin, "CONGE", null)), adminA())));
        r.put(debut + " au " + finPlus1, statutEtCode(doPost(ABSENCES, json(corpsAbsence(benali, debut, finPlus1, "CONGE", null)), adminA())));
        arbre("bornes_annee_bissextile", r);
    }

    @Test
    void les_demandes_mal_formees_et_l_ordre_des_controles_sont_celui_du_controleur_puis_du_service() throws Exception {
        LocalDate d = s(1, JourSemaine.LUNDI);
        UUID inconnu = UUID.nameUUIDFromBytes("inconnu".getBytes());
        aliasNommes.put(inconnu.toString(), "<INCONNU>");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("type inconnu", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, d, d, "VACANCES", null)), adminA())));
        r.put("type en minuscules", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, d, d, "conge", null)), adminA())));
        r.put("type blanc", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, d, d, " ", null)), adminA())));
        r.put("type absent", statutEtCode(doPost(ABSENCES, json("{'infirmierId':'" + amrani + "','debut':'" + d + "','fin':'" + d + "'}"), adminA())));
        r.put("infirmierId absent", statutEtCode(doPost(ABSENCES, json("{'debut':'" + d + "','fin':'" + d + "','type':'CONGE'}"), adminA())));
        r.put("début absent", statutEtCode(doPost(ABSENCES, json("{'infirmierId':'" + amrani + "','fin':'" + d + "','type':'CONGE'}"), adminA())));
        r.put("fin absente", statutEtCode(doPost(ABSENCES, json("{'infirmierId':'" + amrani + "','debut':'" + d + "','type':'CONGE'}"), adminA())));
        r.put("date impossible", statutEtCode(doPost(ABSENCES, json("{'infirmierId':'" + amrani + "','debut':'2026-13-45','fin':'2026-13-46','type':'CONGE'}"), adminA())));
        r.put("date en texte libre", statutEtCode(doPost(ABSENCES, json("{'infirmierId':'" + amrani + "','debut':'demain','fin':'demain','type':'CONGE'}"), adminA())));
        r.put("JSON illisible", statutEtCode(doPost(ABSENCES, "{", adminA())));
        r.put("type inconnu + infirmier inconnu", statutEtCode(doPost(ABSENCES, json(corpsAbsence(inconnu, d, d, "VACANCES", null)), adminA())));
        r.put("infirmier inconnu + période inversée", statutEtCode(doPost(ABSENCES, json(corpsAbsence(inconnu, d, d.minusDays(1), "CONGE", null)), adminA())));
        r.put("infirmier d'un autre centre", statutEtCode(doPost(ABSENCES, json(corpsAbsence(duCentreB, d, d, "CONGE", null)), adminA())));
        r.put("infirmier désactivé", statutEtCode(doPost(ABSENCES, json(corpsAbsence(chikhi, d, d, "CONGE", null)), adminA())));
        r.put("champ inconnu ignoré", statutEtCode(doPost(ABSENCES, json("{'infirmierId':'" + benali + "','debut':'" + d + "','fin':'" + d + "','type':'AUTRE','x':1}"), adminA())));
        arbre("demandes_mal_formees", r);
        etatAbsences("demandes_mal_formees_base");
    }

    @Test
    void le_motif_est_nettoye_les_motifs_blancs_deviennent_nuls_et_au_dela_de_255_caracteres_la_base_refuse() throws Exception {
        LocalDate d = s(1, JourSemaine.LUNDI);
        LocalDate mar = s(1, JourSemaine.MARDI);
        LocalDate mer = s(1, JourSemaine.MERCREDI);
        LocalDate jeu = s(1, JourSemaine.JEUDI);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("motif avec espaces de bord", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, d, d, "CONGE", "  Mariage  ")), adminA())));
        r.put("motif blanc", statutEtCode(doPost(ABSENCES, json(corpsAbsence(benali, d, d, "CONGE", "   ")), adminA())));
        r.put("motif vide", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, mar, mar, "CONGE", "")), adminA())));
        r.put("motif de 255 caractères", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, mer, mer, "CONGE", "x".repeat(255))), adminA())));
        r.put("motif de 256 caractères", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, jeu, jeu, "CONGE", "x".repeat(256))), adminA())));
        arbre("motif", r);
        sql("motif_base", "SELECT infirmier_id, date_debut, type, CASE WHEN motif IS NULL THEN '<NUL>' ELSE '<' || CAST(LENGTH(motif) AS VARCHAR) || '>' || SUBSTRING(motif, 1, 10) END AS motif "
                + "FROM infirmier_absence WHERE center_id = ? ORDER BY infirmier_id, date_debut", CENTRE_A);
    }

    @Test
    void l_administration_peut_saisir_le_passe_et_le_futur_lointains_sans_controle_de_date() throws Exception {
        LocalDate passe = LocalDate.of(2020, 3, 1);
        LocalDate futur = aujourdhui().plusYears(10).withMonth(3).withDayOfMonth(1);
        datesNommees.put(passe.toString(), "<PASSE_LOINTAIN>");
        datesNommees.put(futur.toString(), "<FUTUR_LOINTAIN>");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("passé lointain", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, passe, passe.plusDays(5), "MALADIE", null)), adminA())));
        r.put("futur lointain", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, futur, futur.plusDays(5), "FORMATION", null)), adminA())));
        arbre("passe_et_futur_lointains", r);
    }

    @Test
    void des_absences_qui_se_chevauchent_ou_se_repetent_sont_refusees_sauf_si_contigues_KB_PLN_26() throws Exception {
        LocalDate dim = s(1, JourSemaine.DIMANCHE);
        LocalDate lun = s(1, JourSemaine.LUNDI);
        LocalDate mar = s(1, JourSemaine.MARDI);
        LocalDate mer = s(1, JourSemaine.MERCREDI);
        LocalDate jeu = s(1, JourSemaine.JEUDI);
        LocalDate sam = s(1, JourSemaine.SAMEDI);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("première (lundi au jeudi)", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, lun, jeu, "CONGE", null)), adminA())));
        r.put("doublon exact", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, lun, jeu, "CONGE", null)), adminA())));
        r.put("chevauchement partiel, autre type (mercredi au samedi)", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, mer, sam, "MALADIE", null)), adminA())));
        r.put("incluse dans la première (mardi au mercredi)", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, mar, mer, "FORMATION", null)), adminA())));
        r.put("contiguë (dimanche, veille du début)", statutEtCode(doPost(ABSENCES, json(corpsAbsence(amrani, dim, dim, "AUTRE", null)), adminA())));
        arbre("chevauchements_refuses", r);
        etatAbsences("chevauchements_refuses_base");
        lecture("chevauchements_liste", doGet(ABSENCES, adminA()));
    }

    @Test
    void la_suppression_par_l_administration_retire_meme_une_absence_commencee_ou_passee_et_refuse_l_inconnue_ou_d_un_autre_centre() throws Exception {
        absence(CENTRE_A, "ABS_A_RETIRER", amrani, s(1, JourSemaine.LUNDI), s(1, JourSemaine.MARDI), "CONGE", null);
        absence(CENTRE_A, "ABS_PASSEE_LOINTAINE", benali, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 3), "CONGE", null);
        datesNommees.put("2020-01-01", "<PASSE_LOINTAIN_DEBUT>");
        datesNommees.put("2020-01-03", "<PASSE_LOINTAIN_FIN>");
        absence(CENTRE_B, "ABS_AUTRE_CENTRE", duCentreB, s(1, JourSemaine.LUNDI), s(1, JourSemaine.LUNDI), "CONGE", null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("inconnue", statutEtCode(doDelete(ABSENCES + "/" + UUID.nameUUIDFromBytes("inconnue".getBytes()), adminA())));
        r.put("d'un autre centre", statutEtCode(doDelete(ABSENCES + "/" + absenceId("ABS_AUTRE_CENTRE"), adminA())));
        r.put("identifiant illisible", statutEtCode(doDelete(ABSENCES + "/pas-un-uuid", adminA())));
        r.put("nominale", statutEtCode(doDelete(ABSENCES + "/" + absenceId("ABS_A_RETIRER"), adminA())));
        r.put("deuxième suppression", statutEtCode(doDelete(ABSENCES + "/" + absenceId("ABS_A_RETIRER"), adminA())));
        r.put("passée, par le secrétariat", statutEtCode(doDelete(ABSENCES + "/" + absenceId("ABS_PASSEE_LOINTAINE"), secretaire())));
        arbre("suppression_regles", r);
        etatAbsences("suppression_base");
    }

    @Test
    void une_absence_declaree_met_l_infirmier_en_absent_dans_la_presence_et_le_retire_des_remplacants_et_la_suppression_le_retablit() throws Exception {
        semaine(1);
        patientsPlaces(CENTRE_A, "PAT", 4, salle1, matin, JourSemaine.LUNDI, JourSemaine.MARDI);
        affectation(CENTRE_A, "R_AMRANI", amrani, salle1, matin, "LUNDI,MARDI");
        Resp avant = presenceDeLaSemaine(1, adminA());
        arbre("presence_avant", casesDePresence(avant));
        String remplacantsUrl = INFIRMIERS + "/presence/remplacants?date=" + s(1, JourSemaine.LUNDI) + "&salleId=" + salle1 + "&creneauId=" + matin;
        lecture("remplacants_avant", doGet(remplacantsUrl, adminA()));
        String id = corps(doPost(ABSENCES, json(corpsAbsence(benali, s(1, JourSemaine.LUNDI), s(1, JourSemaine.LUNDI), "CONGE", null)), adminA())).path("id").asString();
        String idAmrani = corps(doPost(ABSENCES, json(corpsAbsence(amrani, s(1, JourSemaine.LUNDI), s(1, JourSemaine.LUNDI), "MALADIE", null)), adminA())).path("id").asString();
        arbre("presence_apres_absence", casesDePresence(presenceDeLaSemaine(1, adminA())));
        lecture("remplacants_apres_absence", doGet(remplacantsUrl, adminA()));
        doDelete(ABSENCES + "/" + idAmrani, adminA());
        doDelete(ABSENCES + "/" + id, adminA());
        arbre("presence_apres_suppression", casesDePresence(presenceDeLaSemaine(1, adminA())));
    }

    @Test
    void les_jours_d_absence_sont_comptes_dans_la_charge_mensuelle_et_decomptes_a_la_suppression() throws Exception {
        LocalDate debut = s(1, JourSemaine.LUNDI);
        int avant = chargeTotale(debut, "Sara Amrani");
        String id = corps(doPost(ABSENCES, json(corpsAbsence(amrani, debut, debut, "CONGE", null)), adminA())).path("id").asString();
        Resp charge = doGet(INFIRMIERS + "/presence/charge?mois=" + debut.toString().substring(0, 7) + "&size=100", adminA());
        int jours = 0;
        for (var i : corps(charge).path("items"))
            if ("Sara Amrani".equals(i.path("nom").asString())) jours = i.path("joursAbsence").asInt();
        assertEquals(1, jours);
        doDelete(ABSENCES + "/" + id, adminA());
        assertEquals(avant, chargeTotale(debut, "Sara Amrani"));
    }

    /**
     * Résumé d'une page : statut, taille réelle, total et identifiants d'absence vus.
     */
    private static final class JsonNodeResume {
        static void ajouter(Map<String, Object> cible, String requete, Resp r, tools.jackson.databind.ObjectMapper mapper) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("statut", r.status());
            try {
                tools.jackson.databind.JsonNode c = mapper.readTree(r.raw());
                if (c.has("items")) {
                    m.put("elements", c.path("items").size());
                    m.put("total", c.path("total").asInt());
                    m.put("page", c.path("page").asInt());
                    m.put("size", c.path("size").asInt());
                } else {
                    m.put("code", c.path("code").asString());
                }
            } catch (RuntimeException e) {
                m.put("corps", "illisible");
            }
            cible.put(requete.isEmpty() ? "(défaut)" : requete, m);
        }
    }
}
