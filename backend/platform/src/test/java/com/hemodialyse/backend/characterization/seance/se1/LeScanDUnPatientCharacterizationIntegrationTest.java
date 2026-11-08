package com.hemodialyse.backend.characterization.seance.se1;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE1, lot 2 : création de la séance du jour par le scan d'un patient ({@code POST /api/v1/seances/scan}) : infirmier et administrateur
 * créent puis valident, la secrétaire crée seulement ; séance déjà présente selon son statut ; formes de QR ; patient non programmé
 * (motif de séance hors planning, centre fermé) ; prise en charge ; droits, cloisonnement, corps, audit, écritures simultanées.
 * Le jour est celui de la JVM ({@code LocalDate.now()}), nommé {@code <J>} (KB-SEA-01).
 */
class LeScanDUnPatientCharacterizationIntegrationTest extends Se1CharacterizationSupport {

    private static final String SCAN = SEANCES_API + "/scan";

    @Override
    protected String dossier() {
        return "scan";
    }

    private Resp scanner(String qr, RequestPostProcessor who) throws Exception {
        return doPost(SCAN, corps("centerId", CENTRE_A, "qrCode", qr), who);
    }

    private Resp scannerAvecMotif(String qr, String motif, String precision, RequestPostProcessor who) throws Exception {
        return doPost(SCAN, corps("centerId", CENTRE_A, "qrCode", qr, "motifHorsPlanning", motif, "precisionHorsPlanning", precision), who);
    }

    @Test
    void le_scan_de_l_infirmier_cree_puis_valide_la_seance_du_jour_et_un_second_scan_la_renvoie_telle_quelle() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        aujourdhuiNomme();
        Map<String, Object> m = new LinkedHashMap<>();
        Resp r = scanner("SE1-PAT_A", infirmierA());
        aliasNommes.put(idDe(r), "<SEANCE>");
        m.put("01 premier scan INFIRMIER", reponse(r));
        m.put("02 etat (VALIDEE, signee par l'infirmier avec son compte, validee ce jour)", etatDesSeances());
        m.put("03 diffusions (creation, validation, saisie pour le medecin)", diffusionsLues());
        diffusions.clear();
        m.put("04 second scan", reponse(scanner("SE1-PAT_A", infirmierA())));
        m.put("05 etat (inchange : une seule ligne)", etatDesSeances().size());
        m.put("06 diffusions du second scan (aucune)", diffusionsLues());
        arbre("infirmier", m);
    }

    @Test
    void le_scan_de_l_administrateur_cree_et_valide_comme_l_infirmier_avec_son_propre_compte() throws Exception {
        patientCouvert(CENTRE_A, "PAT_A");
        aujourdhuiNomme();
        Map<String, Object> m = new LinkedHashMap<>();
        Resp r = scanner("SE1-PAT_A", adminA());
        aliasNommes.put(idDe(r), "<SEANCE>");
        m.put("01 scan ADMIN", reponse(r));
        m.put("02 etat", etatDesSeances());
        arbre("administrateur", m);
    }

    @Test
    void le_scan_de_la_secretaire_cree_sans_valider_et_renvoie_la_seance_existante_sans_la_valider() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        aujourdhuiNomme();
        Map<String, Object> m = new LinkedHashMap<>();
        Resp r = scanner("SE1-PAT_A", secretaireA());
        aliasNommes.put(idDe(r), "<SEANCE>");
        m.put("01 premier scan SECRETAIRE (statut CREE ; created reste faux alors que la seance vient d'etre creee, KB-SEA-05)", reponse(r));
        m.put("02 etat", etatDesSeances());
        m.put("03 diffusions (creation et saisie, pas de validation)", diffusionsLues());
        diffusions.clear();
        m.put("04 second scan SECRETAIRE", reponse(scanner("SE1-PAT_A", secretaireA())));
        m.put("05 diffusions du second scan (la seance est encore CREE : creation rediffusee)", diffusionsLues());
        m.put("06 lignes", nombreDeSeances(CENTRE_A));
        jdbc.update("UPDATE seances SET statut = 'VALIDEE' WHERE patient_id = ?", patient);
        diffusions.clear();
        m.put("07 scan SECRETAIRE d'une seance deja validee", reponse(scanner("SE1-PAT_A", secretaireA())));
        m.put("08 diffusions (aucune)", diffusionsLues());
        arbre("secretaire", m);
    }

    @Test
    void le_scan_d_une_seance_deja_presente_depend_de_son_statut_cree_est_valide_les_autres_sont_renvoyes_et_absent_est_refuse() throws Exception {
        LocalDate jour = aujourdhuiNomme();
        Map<String, Object> m = new LinkedHashMap<>();
        for (String statut : List.of("CREE", "VALIDEE", "SIGNEE", "FACTUREE", "ABSENT", "EN_COURS")) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            seanceEnBase(CENTRE_A, patient, "SEANCE_" + statut, jour, statut);
            Map<String, Object> cas = new LinkedHashMap<>();
            cas.put("infirmier", reponse(scanner("SE1-PAT_A", infirmierA())));
            cas.put("etat apres", etatDesSeances().stream().map(l -> l.get("statut") + "/" + l.get("validation")).toList());
            nettoyerSe1();
            patient = patientCouvert(CENTRE_A, "PAT_A");
            seanceEnBase(CENTRE_A, patient, "SEANCE_" + statut, jour, statut);
            cas.put("secretaire", refus(scanner("SE1-PAT_A", secretaireA())));
            m.put(statut, cas);
        }
        arbre("statut_existant_nouveau_code", m);
    }

    @Test
    void les_formes_de_qr_acceptees_code_patient_numero_d_assurance_identifiant_prefixe_et_comparaison_tolerante() throws Exception {
        aujourdhuiNomme();
        Map<String, Object> m = new LinkedHashMap<>();
        UUID id = idStable("patient", "PAT_A-" + CENTRE_A);
        aliasNommes.put(id.toString(), "<PAT_A>");
        String[][] cas = {
                {"code patient exact", "SE1-PAT_A"},
                {"code patient en minuscules", "se1-pat_a"},
                {"code patient avec espaces autour", "  SE1-PAT_A  "},
                {"numero d'assurance exact", "ASS-SE1-PAT_A"},
                {"identifiant du patient", id.toString()},
                {"identifiant du patient en majuscules", id.toString().toUpperCase()},
                {"prefixe puis code (tout avant le premier deux-points est ignore)", "PATIENT:SE1-PAT_A"},
                {"prefixe puis identifiant", "ID:" + id},
                {"deux-points en tete (aucun prefixe)", ":SE1-PAT_A"},
                {"deux-points final (aucune valeur apres)", "SE1-PAT_A:"},
                {"comparaison tolerante sans ponctuation", "se1 pat a"},
                {"comparaison tolerante sur le numero d'assurance", "ass se1 pat a"},
                {"code inconnu", "INCONNU-1"},
                {"identifiant inconnu", idStable("patient", "inconnu").toString()},
                {"espaces seuls", "   "},
                {"un seul caractere de ponctuation (KB-SEA-06 : rapproche un patient sans numero d'assure, par comparaison de chaines vides)", "-"},
        };
        int i = 0;
        for (String[] c : cas) {
            nettoyerSe1();
            patientCouvert(CENTRE_A, "PAT_A");
            Resp r = scanner(c[1], secretaireA());
            m.put(String.format("%02d %s", ++i, c[0]), refus(r) + " ; lignes=" + nombreDeSeances(CENTRE_A));
        }
        nettoyerSe1();
        UUID autreCentre = patientCouvert(CENTRE_B, "PAT_A");
        m.put(String.format("%02d code du centre B scanne par la session A", ++i), refus(scanner("SE1-PAT_A", secretaireA())) + " ; lignes A=" + nombreDeSeances(CENTRE_A));
        m.put(String.format("%02d identifiant du patient du centre B scanne par la session A", ++i), refus(scanner(autreCentre.toString(), secretaireA())));
        arbre("formes_de_qr_nouveau_code", m);
    }

    @Test
    void un_patient_non_programme_exige_un_motif_confirme_et_un_centre_ferme_n_est_franchi_que_par_l_administrateur() throws Exception {
        aujourdhuiNomme();
        UUID sansJours = patientCouvert(CENTRE_A, "SANS_JOURS");
        joursDeDialyse(sansJours, false);
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> a = new LinkedHashMap<>();
        a.put("01 INFIRMIER sans motif", refus(scanner("SE1-SANS_JOURS", infirmierA())));
        a.put("02 SECRETAIRE (jamais de motif)", refus(scanner("SE1-SANS_JOURS", secretaireA())));
        a.put("03 INFIRMIER motif vide", refus(scannerAvecMotif("SE1-SANS_JOURS", "   ", null, infirmierA())));
        a.put("04 INFIRMIER motif inconnu", refus(scannerAvecMotif("SE1-SANS_JOURS", "VACANCES", null, infirmierA())));
        a.put("05 aucune ecriture", String.valueOf(nombreDeSeances(CENTRE_A)));
        a.put("06 SECRETAIRE avec motif (ignore : createFromQr ne lit pas la confirmation)", refus(scannerAvecMotif("SE1-SANS_JOURS", "URGENCE", null,
                secretaireA())));
        m.put("01 patient sans jour de dialyse", a);
        m.put("02 etat apres le refus de la secretaire avec motif", etatDesSeances().size());
        nettoyerSe1();
        sansJours = patientCouvert(CENTRE_A, "SANS_JOURS");
        joursDeDialyse(sansJours, false);
        Resp r = scannerAvecMotif("SE1-SANS_JOURS", "rattrapage", "  Rattrapage apres un voyage  ", infirmierA());
        aliasNommes.put(idDe(r), "<SEANCE_HORS_PLANNING>");
        m.put("03 INFIRMIER avec motif en minuscules et precision", reponse(r));
        m.put("04 etat (hors planning, motif RATTRAPAGE, precision enregistree sans les espaces de bord)", etatDesSeances());
        nettoyerSe1();
        sansJours = patientCouvert(CENTRE_A, "SANS_JOURS");
        joursDeDialyse(sansJours, false);
        m.put("05 precision de 255 caracteres", refus(scannerAvecMotif("SE1-SANS_JOURS", "AUTRE", "p".repeat(255), adminA())));
        nettoyerSe1();
        sansJours = patientCouvert(CENTRE_A, "SANS_JOURS");
        joursDeDialyse(sansJours, false);
        m.put("06 precision de 256 caracteres", refus(scannerAvecMotif("SE1-SANS_JOURS", "AUTRE", "p".repeat(256), adminA())));
        arbre("hors_planning", m);
    }

    @Test
    void les_autres_raisons_de_hors_planning_sommeil_patient_pas_encore_admis_decede_et_centre_ferme() throws Exception {
        LocalDate aujourdhui = aujourdhuiNomme();
        Map<String, Object> m = new LinkedHashMap<>();
        UUID sommeil = patientCouvert(CENTRE_A, "SOMMEIL");
        sommeil(sommeil);
        UUID futur = patientCouvert(CENTRE_A, "PAS_ENCORE_ADMIS");
        admission(futur, jour(30));
        UUID decede = patientCouvert(CENTRE_A, "DECEDE");
        etat(decede, "DECEDE", jour(-2));
        UUID transfere = patientCouvert(CENTRE_A, "TRANSFERE_AUJOURD_HUI");
        etat(transfere, "TRANSFERE", aujourdhui);
        UUID vacancier = patientCouvert(CENTRE_A, "VACANCIER_PASSE");
        etat(vacancier, "VACANCIER_LOCAL", jour(-1));
        Map<String, String> a = new LinkedHashMap<>();
        for (String code : List.of("SOMMEIL", "PAS_ENCORE_ADMIS", "DECEDE", "TRANSFERE_AUJOURD_HUI", "VACANCIER_PASSE")) {
            a.put(code + " : sans motif", refus(scanner("SE1-" + code, infirmierA())));
            a.put(code + " : avec motif URGENCE", refus(scannerAvecMotif("SE1-" + code, "URGENCE", null, infirmierA())));
        }
        m.put("01 patients non attendus", a);
        nettoyerSe1();
        patientCouvert(CENTRE_A, "PAT_A");
        fermerLeCentre(CENTRE_A, aujourdhui());
        Map<String, String> b = new LinkedHashMap<>();
        b.put("01 centre ferme : INFIRMIER sans motif", refus(scanner("SE1-PAT_A", infirmierA())));
        b.put("02 centre ferme : INFIRMIER avec motif", refus(scannerAvecMotif("SE1-PAT_A", "URGENCE", null, infirmierA())));
        b.put("03 centre ferme : SECRETAIRE", refus(scanner("SE1-PAT_A", secretaireA())));
        b.put("04 aucune ecriture", String.valueOf(nombreDeSeances(CENTRE_A)));
        Resp r = scannerAvecMotif("SE1-PAT_A", "URGENCE", "Urgence un jour ferme", adminA());
        b.put("05 centre ferme : ADMIN avec motif", refus(r));
        m.put("02 centre ferme", b);
        m.put("03 etat apres le scan de l'administrateur", etatDesSeances());
        arbre("autres_raisons", m);
    }

    @Test
    void le_scan_exige_la_meme_prise_en_charge_que_la_creation_et_un_refus_ne_laisse_aucune_seance() throws Exception {
        aujourdhuiNomme();
        UUID sans = patientSe1(CENTRE_A, "SANS_PEC");
        UUID sansAttestation = patientSe1(CENTRE_A, "SANS_ATTESTATION");
        pecSe1(CENTRE_A, sansAttestation, "VALIDEE", "2020-01-01", "2099-12-31", "2020-01-01", "2099-12-31", true);
        UUID nonValidee = patientSe1(CENTRE_A, "NON_VALIDEE");
        pecSe1(CENTRE_A, nonValidee, "EN_ATTENTE", "2020-01-01", "2099-12-31", "2020-01-01", "2099-12-31", true);
        attestation(CENTRE_A, nonValidee, "2020-01-01", null);
        String avant = empreinteDesSeances();
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> a = new LinkedHashMap<>();
        for (String code : List.of("SANS_PEC", "SANS_ATTESTATION", "NON_VALIDEE")) {
            a.put(code + " : INFIRMIER", refus(scanner("SE1-" + code, infirmierA())));
            a.put(code + " : SECRETAIRE", refus(scanner("SE1-" + code, secretaireA())));
        }
        m.put("01 refus", a);
        m.put("02 aucune ecriture (la creation et la validation sont dans une seule transaction)", empreinteDesSeances().equals(avant));
        m.put("03 aucune diffusion", diffusionsLues());
        arbre("pec", m);
    }

    @Test
    void les_droits_par_profil_du_scan() throws Exception {
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            nettoyerSe1();
            patientCouvert(CENTRE_A, "PAT_A");
            profils.put(p.getKey(), statutEtCode(scanner("SE1-PAT_A", p.getValue())) + " ; lignes=" + nombreDeSeances(CENTRE_A)
                    + " ; statuts=" + jdbc.queryForList("SELECT statut AS s FROM seances WHERE center_id = ?", CENTRE_A));
        }
        nettoyerSe1();
        patientCouvert(CENTRE_A, "PAT_A");
        profils.put("anonyme", statutEtCode(appel("POST", SCAN, corps("centerId", CENTRE_A, "qrCode", "SE1-PAT_A"), null)));
        arbre("profils", profils);
    }

    @Test
    void le_cloisonnement_par_centre_du_scan_et_les_corps_invalides() throws Exception {
        patientCouvert(CENTRE_A, "PAT_A");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 session A vise le centre B", refus(doPost(SCAN, corps("centerId", CENTRE_B, "qrCode", "SE1-PAT_A"), infirmierA())));
        m.put("02 session B vise le centre A", refus(doPost(SCAN, corps("centerId", CENTRE_A, "qrCode", "SE1-PAT_A"), infirmierB())));
        m.put("03 session B scanne un patient du centre A dans son centre", refus(doPost(SCAN, corps("centerId", CENTRE_B, "qrCode", "SE1-PAT_A"),
                infirmierB())));
        m.put("04 session sans centre", refus(doPost(SCAN, corps("centerId", CENTRE_A, "qrCode", "SE1-PAT_A"), as(null, "INFIRMIER"))));
        m.put("05 lignes", nombreDeSeances(CENTRE_A) + nombreDeSeances(CENTRE_B));
        m.put("06 corps sans centerId", refus(doPost(SCAN, corps("qrCode", "SE1-PAT_A"), infirmierA())));
        m.put("07 corps sans qrCode", refus(doPost(SCAN, corps("centerId", CENTRE_A), infirmierA())));
        m.put("08 qrCode vide", refus(doPost(SCAN, corps("centerId", CENTRE_A, "qrCode", ""), infirmierA())));
        m.put("09 qrCode null", refus(doPost(SCAN, "{\"centerId\":\"" + CENTRE_A + "\",\"qrCode\":null}", infirmierA())));
        m.put("10 precision de 300 caracteres", refus(doPost(SCAN, corps("centerId", CENTRE_A, "qrCode", "SE1-PAT_A", "precisionHorsPlanning", "p".repeat(300)),
                infirmierA())));
        m.put("11 corps illisible", refus(doPost(SCAN, "{pas du json", infirmierA())));
        m.put("12 corps absent", refus(doPost(SCAN, "", infirmierA())));
        m.put("13 lignes apres les refus", nombreDeSeances(CENTRE_A) + nombreDeSeances(CENTRE_B));
        arbre("cloisonnement_et_corps_nouveau_code", m);
    }

    @Test
    void les_scans_et_leurs_refus_sont_journalises_avec_la_route_et_le_statut() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID sans = patientSe1(CENTRE_A, "SANS_PEC");
        monterLAudit();
        scanner("SE1-PAT_A", infirmierA());
        scanner("SE1-PAT_A", infirmierA());
        scanner("SE1-SANS_PEC", infirmierA());
        scanner("SE1-INCONNU", secretaireA());
        scanner("SE1-PAT_A", medecinA());
        doPost(SCAN, corps("centerId", CENTRE_B, "qrCode", "SE1-PAT_A"), infirmierA());
        doPost(SCAN, "{pas du json", infirmierA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit_nouveau_code", m);
    }

    @Test
    void deux_scans_simultanes_d_un_patient_sans_seance_ne_sont_proteges_par_aucune_unicite() throws Exception {
        patientCouvert(CENTRE_A, "PAT_A");
        List<Resp> reponses = enParallele(() -> scanner("SE1-PAT_A", infirmierA()), () -> scanner("SE1-PAT_A", infirmierA()));
        int lignes = nombreDeSeances(CENTRE_A);
        Map<String, Object> m = new LinkedHashMap<>();
        // Invariants seulement : ordre d'arrivee non deterministe, H2 n'est pas PostgreSQL. Soit une seance (le second scan voit la
        // premiere), soit deux (course : aucune contrainte d'unicite, le scan suivant devient alors un 500, voir « sans_unicite »).
        m.put("01 chaque requete rend 200", reponses.stream().allMatch(r -> r.status() == 200));
        m.put("02 une ou deux seances", lignes == 1 || lignes == 2);
        m.put("03 toutes validees", jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE center_id = ? AND statut = 'VALIDEE'", Integer.class,
                CENTRE_A) == lignes);
        arbre("ecritures_simultanees", m);
    }
}
