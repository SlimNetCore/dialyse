package com.hemodialyse.backend.characterization.seance.se1;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE1, lot 1 : création d'une séance ({@code POST /api/v1/seances}, table {@code seances}) : statut initial, date par défaut,
 * conditions de prise en charge et d'attestation, absence de contrôle du planning, absence d'unicité par patient et par jour,
 * valeurs et corps, droits, cloisonnement par centre, audit, écritures simultanées, lignes illisibles.
 */
class LaCreationDUneSeanceCharacterizationIntegrationTest extends Se1CharacterizationSupport {

    @Override
    protected String dossier() {
        return "creation";
    }

    private Resp creer(UUID patient, LocalDate date, RequestPostProcessor who) throws Exception {
        return doPost(SEANCES_API, corps("centerId", CENTRE_A, "patientId", patient, "dateSeance", date), who);
    }

    @Test
    void une_creation_sans_date_cree_une_seance_au_statut_CREE_au_jour_du_centre_sans_rien_valider() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        aujourdhuiNomme();
        Map<String, Object> m = new LinkedHashMap<>();
        Resp r = doPost(SEANCES_API, corps("centerId", CENTRE_A, "patientId", patient), infirmierA());
        aliasNommes.put(idDe(r), "<SEANCE>");
        m.put("01 reponse INFIRMIER", reponse(r));
        m.put("02 en-tete Cache-Control", r.cacheControl());
        m.put("03 etat (statut CREE, aucune validation ni signature, auteur non conserve, place non memorisee)", etatDesSeances());
        m.put("04 relecture par le detail", reponse(doGet(detailApi(UUID.fromString(idDe(r)), CENTRE_A), infirmierA())).get("status"));
        m.put("05 diffusions temps reel (creation puis saisie de l'infirmier pour le medecin)", diffusionsLues());
        arbre("nominal", m);
    }

    @Test
    void une_creation_avec_date_accepte_le_passe_et_le_futur_couverts_par_la_pec_et_ne_valide_rien() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        Map<String, Object> m = new LinkedHashMap<>();
        LocalDate lointain = alias(LocalDate.of(2099, 12, 31), "<FIN_PEC>");
        LocalDate[] dates = {fixe(10), fixe(-30), lointain};
        int i = 0;
        for (LocalDate d : dates) {
            Resp r = creer(patient, d, adminA());
            aliasNommes.put(idDe(r), "<SEANCE_" + (++i) + ">");
            m.put(String.format("%02d POST ADMIN date %s", i, d), reponse(r));
        }
        m.put("04 etat", etatDesSeances());
        arbre("avec_date", m);
    }

    @Test
    void la_creation_exige_une_pec_validee_avec_forfait_et_une_attestation_et_un_refus_n_ecrit_rien() throws Exception {
        LocalDate jour = fixe(20);
        UUID sans = patientSe1(CENTRE_A, "SANS_PEC");
        UUID nonValidee = patientSe1(CENTRE_A, "PEC_NON_VALIDEE");
        pecSe1(CENTRE_A, nonValidee, "EN_ATTENTE", "2020-01-01", "2099-12-31", "2020-01-01", "2099-12-31", true);
        attestation(CENTRE_A, nonValidee, "2020-01-01", null);
        UUID sansAttestation = patientSe1(CENTRE_A, "SANS_ATTESTATION");
        pecSe1(CENTRE_A, sansAttestation, "VALIDEE", "2020-01-01", "2099-12-31", "2020-01-01", "2099-12-31", true);
        UUID attestationExpiree = patientSe1(CENTRE_A, "ATTESTATION_EXPIREE");
        pecSe1(CENTRE_A, attestationExpiree, "VALIDEE", "2020-01-01", "2099-12-31", "2020-01-01", "2099-12-31", true);
        attestation(CENTRE_A, attestationExpiree, "2020-01-01", "2025-12-31");
        UUID sansForfait = patientSe1(CENTRE_A, "PEC_SANS_FORFAIT");
        pecSe1(CENTRE_A, sansForfait, "VALIDEE", "2020-01-01", "2099-12-31", "2020-01-01", "2099-12-31", false);
        attestation(CENTRE_A, sansForfait, "2020-01-01", null);
        UUID pecAutreCentre = patientSe1(CENTRE_A, "PEC_AUTRE_CENTRE");
        pecSe1(CENTRE_B, pecAutreCentre, "VALIDEE", "2020-01-01", "2099-12-31", "2020-01-01", "2099-12-31", true);
        attestation(CENTRE_A, pecAutreCentre, "2020-01-01", null);
        UUID couvert = patientCouvert(CENTRE_A, "COUVERT");
        String avant = empreinteDesSeances();
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> refus = new LinkedHashMap<>();
        refus.put("01 aucune PEC", refus(creer(sans, jour, adminA())));
        refus.put("02 PEC non validee", refus(creer(nonValidee, jour, adminA())));
        refus.put("03 PEC validee sans attestation", refus(creer(sansAttestation, jour, adminA())));
        refus.put("04 attestation expiree avant la date", refus(creer(attestationExpiree, jour, adminA())));
        refus.put("05 PEC sans forfait", refus(creer(sansForfait, jour, adminA())));
        refus.put("06 PEC enregistree dans un autre centre", refus(creer(pecAutreCentre, jour, adminA())));
        m.put("01 refus", refus);
        m.put("02 aucune ecriture", empreinteDesSeances().equals(avant));
        m.put("03 patient couvert (temoin)", refus(creer(couvert, jour, adminA())));
        arbre("pec_et_attestation", m);
    }

    @Test
    void les_bornes_de_la_periode_de_pec_et_d_attestation_sont_incluses_et_la_periode_demandee_vaut_a_defaut_de_l_effective() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        UUID effective = patientSe1(CENTRE_A, "EFFECTIVE");
        pecSe1(CENTRE_A, effective, "VALIDEE", "2026-01-01", "2026-12-31", "2026-02-01", "2026-02-28", true);
        attestation(CENTRE_A, effective, "2026-01-01", null);
        Map<String, String> a = new LinkedHashMap<>();
        for (int n : new int[]{30, 31, 58, 59}) {
            a.put(String.format("%02d jour %s", n, fixe(n)), refus(creer(effective, fixe(n), adminA())));
        }
        m.put("01 PEC effective du 2026-02-01 au 2026-02-28 (jours <D+30> a <D+59>), periode demandee plus large", a);
        UUID ouverte = patientSe1(CENTRE_A, "OUVERTE");
        pecSe1(CENTRE_A, ouverte, "VALIDEE", null, null, "2026-02-01", null, true);
        attestation(CENTRE_A, ouverte, "2026-02-01", "2026-02-10");
        Map<String, String> b = new LinkedHashMap<>();
        for (int n : new int[]{30, 31, 40, 41, 400}) {
            b.put(String.format("%03d jour %s", n, fixe(n)), refus(creer(ouverte, fixe(n), adminA())));
        }
        m.put("02 PEC effective ouverte sans fin, attestation du 2026-02-01 au 2026-02-10", b);
        UUID demandee = patientSe1(CENTRE_A, "DEMANDEE");
        pecSe1(CENTRE_A, demandee, "VALIDEE", "2026-03-01", "2026-03-31", null, null, true);
        attestation(CENTRE_A, demandee, "2026-01-01", null);
        Map<String, String> c = new LinkedHashMap<>();
        for (int n : new int[]{58, 59, 89, 90}) {
            c.put(String.format("%02d jour %s", n, fixe(n)), refus(creer(demandee, fixe(n), adminA())));
        }
        m.put("03 seule la periode demandee (2026-03-01 au 2026-03-31)", c);
        arbre("bornes_de_periode", m);
    }

    @Test
    void la_creation_ne_controle_pas_le_planning_jour_sans_dialyse_centre_ferme_patient_en_sommeil_ou_pas_encore_admis() throws Exception {
        LocalDate jour = fixe(40);
        UUID sansJours = patientCouvert(CENTRE_A, "SANS_JOURS");
        joursDeDialyse(sansJours, false);
        UUID ferme = patientCouvert(CENTRE_A, "CENTRE_FERME");
        fermerLeCentre(CENTRE_A, jour);
        UUID sommeil = patientCouvert(CENTRE_A, "SOMMEIL");
        sommeil(sommeil);
        UUID futur = patientCouvert(CENTRE_A, "PAS_ENCORE_ADMIS");
        admission(futur, fixe(300));
        UUID decede = patientCouvert(CENTRE_A, "DECEDE");
        etat(decede, "DECEDE", fixe(5));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 patient sans jour de dialyse", refus(creer(sansJours, jour, infirmierA())));
        m.put("02 centre ferme ce jour", refus(creer(ferme, jour, infirmierA())));
        m.put("03 patient en sommeil", refus(creer(sommeil, jour, infirmierA())));
        m.put("04 patient admis plus tard", refus(creer(futur, jour, infirmierA())));
        m.put("05 patient decede avant la date", refus(creer(decede, jour, infirmierA())));
        m.put("06 etat (aucune seance hors planning : drapeau a faux)", etatDesSeances());
        arbre("sans_controle_du_planning", m);
    }

    @Test
    void un_patient_inconnu_ou_d_un_autre_centre_est_refuse_en_400_et_le_centre_du_corps_doit_etre_celui_de_la_session() throws Exception {
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        UUID inconnu = idStable("patient", "inconnu");
        aliasNommes.put(inconnu.toString(), "<PATIENT_INCONNU>");
        UUID patientA = patientCouvert(CENTRE_A, "PAT_A");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 patient inconnu", refus(creer(inconnu, fixe(1), infirmierA())));
        m.put("02 patient du centre B, session A, corps centre A", refus(creer(patientB, fixe(1), infirmierA())));
        m.put("03 patient du centre A, corps centre B, session A", refus(doPost(SEANCES_API, corps("centerId", CENTRE_B, "patientId", patientA,
                "dateSeance", fixe(1)), infirmierA())));
        m.put("04 patient du centre B, corps centre B, session A", refus(doPost(SEANCES_API, corps("centerId", CENTRE_B, "patientId", patientB,
                "dateSeance", fixe(1)), infirmierA())));
        m.put("05 patient du centre B, corps centre B, session B (nominal du centre B)", refus(doPost(SEANCES_API, corps("centerId", CENTRE_B,
                "patientId", patientB, "dateSeance", fixe(1)), infirmierB())));
        m.put("06 patient du centre A, corps centre A, session B", refus(doPost(SEANCES_API, corps("centerId", CENTRE_A, "patientId", patientA,
                "dateSeance", fixe(1)), infirmierB())));
        m.put("07 SUPERADMIN + INFIRMIER (session A) cree dans le centre B", refus(doPost(SEANCES_API, corps("centerId", CENTRE_B, "patientId",
                patientB, "dateSeance", fixe(2)), as(CENTRE_A, "SUPERADMIN", "INFIRMIER"))));
        m.put("08 INFIRMIER sans centre de session", refus(doPost(SEANCES_API, corps("centerId", CENTRE_A, "patientId", patientA,
                "dateSeance", fixe(1)), as(null, "INFIRMIER"))));
        m.put("09 etat", etatDesSeances());
        arbre("patient_et_centre", m);
    }

    @Test
    void les_corps_incomplets_ou_illisibles_et_les_dates_exotiques() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        String[][] cas = {
                {"corps sans patientId", corps("centerId", CENTRE_A, "dateSeance", fixe(1))},
                {"corps sans centerId (le centre de la session est retenu par la garde, pas par la suite)", corps("patientId", patient, "dateSeance", fixe(1))},
                {"objet vide", "{}"},
                {"patientId illisible", "{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"abc\"}"},
                {"dateSeance illisible", "{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patient + "\",\"dateSeance\":\"pas une date\"}"},
                {"dateSeance inexistante (30 fevrier)", "{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patient + "\",\"dateSeance\":\"2026-02-30\"}"},
                {"dateSeance nulle explicite (jour du centre)", "{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patient + "\",\"dateSeance\":null}"},
                {"champ inconnu ignore", "{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patient + "\",\"dateSeance\":\"" + fixe(2)
                        + "\",\"statut\":\"VALIDEE\",\"horsPlanning\":true}"},
                {"corps illisible", "{pas du json"},
                {"corps absent", ""},
                {"corps null", "null"},
                {"corps tableau", "[]"},
        };
        aujourdhuiNomme();
        int i = 0;
        for (String[] c : cas) {
            nettoyerSe1();
            patientCouvert(CENTRE_A, "PAT_A"); // meme identifiant stable que le patient du corps
            Resp r = doPost(SEANCES_API, c[1], infirmierA());
            m.put(String.format("%02d %s", ++i, c[0]), refus(r) + " ; lignes=" + nombreDeSeances(CENTRE_A) + " ; statuts=" + statuts());
        }
        arbre("corps_et_valeurs", m);
    }

    private String statuts() {
        return jdbc.queryForList("SELECT statut || '/' || hors_planning AS s FROM seances WHERE center_id = ? ORDER BY s", CENTRE_A).toString();
    }

    @Test
    void aucune_unicite_par_patient_et_par_jour_deux_creations_donnent_deux_seances_et_le_scan_du_jour_devient_un_500() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        LocalDate jour = aujourdhuiNomme();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 premiere creation", refus(creer(patient, jour, infirmierA())));
        m.put("02 seconde creation du meme patient le meme jour", refus(creer(patient, jour, infirmierA())));
        m.put("03 troisieme creation par un administrateur", refus(creer(patient, jour, adminA())));
        m.put("04 lignes", nombreDeSeances(CENTRE_A));
        m.put("05 etat", etatDesSeances());
        m.put("06 scan du jour par l'infirmier (deux seances trouvees)", refus(doPost(SEANCES_API + "/scan", corps("centerId", CENTRE_A, "qrCode",
                "SE1-PAT_A"), infirmierA())));
        m.put("07 scan du jour par la secretaire", refus(doPost(SEANCES_API + "/scan", corps("centerId", CENTRE_A, "qrCode", "SE1-PAT_A"),
                secretaireA())));
        m.put("08 liste du jour (les trois seances)", resumeListe(doGet(SEANCES_API + "?centerId=" + CENTRE_A, infirmierA())));
        m.put("09 etat apres les scans", etatDesSeances().size());
        arbre("sans_unicite", m);
    }

    private String resumeListe(Resp r) {
        if (r.status() != 200) return refus(r);
        var c = corps(r);
        return "200 total=" + c.path("total").asString() + " items=" + c.path("items").size();
    }

    @Test
    void les_droits_par_profil_sur_la_creation_la_consultation_et_la_liste() throws Exception {
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE_A", fixe(3), "CREE");
            Map<String, String> routes = new LinkedHashMap<>();
            routes.put("01 POST seances", statutEtCode(creer(patient, fixe(1), p.getValue())));
            routes.put("02 GET seances/{id}", statutEtCode(doGet(detailApi(seance, CENTRE_A), p.getValue())));
            routes.put("03 GET seances", statutEtCode(doGet(SEANCES_API + "?centerId=" + CENTRE_A, p.getValue())));
            profils.put(p.getKey(), regrouper(routes));
        }
        nettoyerSe1();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        Map<String, String> anonyme = new LinkedHashMap<>();
        anonyme.put("POST", statutEtCode(appel("POST", SEANCES_API, corps("centerId", CENTRE_A, "patientId", patient), null)));
        anonyme.put("GET liste", statutEtCode(appel("GET", SEANCES_API + "?centerId=" + CENTRE_A, null, null)));
        profils.put("anonyme", anonyme);
        arbre("profils", profils);
    }

    @Test
    void un_refus_de_droit_n_ecrit_rien_mais_le_corps_illisible_est_lu_avant_le_403_et_donne_un_500() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        String avant = empreinteDesSeances();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("SECRETAIRE : POST valide", statutEtCode(creer(patient, fixe(1), secretaireA())));
        cas.put("MEDECIN : POST valide", statutEtCode(creer(patient, fixe(1), medecinA())));
        cas.put("SECRETAIRE : POST avec un corps illisible", statutEtCode(doPost(SEANCES_API, "{pas du json", secretaireA())));
        cas.put("DIRECTION : POST valide", statutEtCode(creer(patient, fixe(1), direction())));
        cas.put("INFIRMIER du centre B : POST du centre A", statutEtCode(creer(patient, fixe(1), infirmierB())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("aucune ecriture", empreinteDesSeances().equals(avant));
        arbre("refus", m);
    }

    @Test
    void les_creations_et_leurs_refus_sont_journalises_mais_ni_le_detail_ni_la_liste_ne_le_sont() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID sans = patientSe1(CENTRE_A, "SANS_PEC");
        monterLAudit();
        Resp ok = creer(patient, fixe(1), infirmierA());
        aliasNommes.put(idDe(ok), "<SEANCE>");
        creer(sans, fixe(1), infirmierA());
        creer(patient, fixe(1), secretaireA());
        doPost(SEANCES_API, corps("centerId", CENTRE_B, "patientId", patient), infirmierA());
        doGet(detailApi(UUID.fromString(idDe(ok)), CENTRE_A), infirmierA());
        doGet(SEANCES_API + "?centerId=" + CENTRE_A, infirmierA());
        doPost(SEANCES_API, "{pas du json", infirmierA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }

    @Test
    void deux_creations_simultanees_du_meme_patient_donnent_deux_seances() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        List<Resp> reponses = enParallele(() -> creer(patient, fixe(1), infirmierA()), () -> creer(patient, fixe(1), infirmierA()));
        Map<String, Object> m = new LinkedHashMap<>();
        // Invariants seulement : l'ordre d'arrivee n'est pas deterministe ; H2 n'est pas PostgreSQL.
        m.put("01 chaque requete rend 200", reponses.stream().allMatch(r -> r.status() == 200));
        m.put("02 deux identifiants distincts", reponses.stream().map(this::idDe).distinct().count() == 2);
        m.put("03 deux lignes pour le meme patient et le meme jour (aucune unicite)", nombreDeSeances(CENTRE_A) == 2);
        arbre("ecritures_simultanees", m);
    }

    @Test
    void des_lignes_illisibles_statut_inconnu_ou_patient_absent_rendent_la_consultation_en_erreur_sans_toucher_aux_autres() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID bonne = seanceEnBase(CENTRE_A, patient, "BONNE", fixe(1), "CREE");
        UUID statutInconnu = seanceEnBase(CENTRE_A, patient, "STATUT_INCONNU", fixe(2), "EN_COURS");
        UUID absente = seanceEnBase(CENTRE_A, patient, "ABSENTE", fixe(3), "ABSENT");
        UUID orpheline = seanceEnBase(CENTRE_A, idStable("patient", "disparu"), "ORPHELINE", fixe(4), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 detail de la ligne saine", reponse(doGet(detailApi(bonne, CENTRE_A), infirmierA())).get("status"));
        m.put("02 detail du statut inconnu", refus(doGet(detailApi(statutInconnu, CENTRE_A), infirmierA())));
        m.put("03 detail du statut ABSENT (statut historique)", reponse(doGet(detailApi(absente, CENTRE_A), infirmierA())).get("status"));
        m.put("04 detail d'une seance dont le patient n'existe plus", refus(doGet(detailApi(orpheline, CENTRE_A), infirmierA())));
        m.put("05 liste avec une ligne de statut inconnu", refus(doGet(SEANCES_API + "?centerId=" + CENTRE_A, infirmierA())));
        jdbc.update("DELETE FROM seances WHERE id = ?", statutInconnu);
        m.put("06 liste sans la ligne de statut inconnu (la seance orpheline est exclue par la jointure)", resumeListe(doGet(SEANCES_API
                + "?centerId=" + CENTRE_A, infirmierA())));
        arbre("lignes_illisibles", m);
    }
}
