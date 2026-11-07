package com.hemodialyse.backend.characterization.dossiermedical.dm7;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM7, lot 3 (checklist d'étapes) : routes {@code greffe/etapes} (liste, création, génération de la liste standard, mise à jour,
 * suppression).
 */
class LaChecklistDuBilanPreGreffeCharacterizationIntegrationTest extends Dm7CharacterizationSupport {

    @Override
    protected String dossier() {
        return "etapes";
    }

    private static final String[] CHAMPS = {"categorie", "libelle", "statut", "dateRealisation", "resultat", "dateExpiration",
            "demandeExamenId", "serologieId"};

    @Test
    void la_generation_de_la_liste_standard_cree_vingt_trois_etapes_a_faire_et_ne_double_rien() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        Map<String, Object> m = new LinkedHashMap<>();
        Resp premiere = doPost(etapesApi(patient) + "/generer-standard", "", infirmierA());
        m.put("01 premiere generation (INFIRMIER), tri categorie puis libelle", liste(premiere, "categorie", "libelle", "statut"));
        m.put("02 nombre apres la premiere generation", nombreDe("etapes_bilan_pre_greffe", CENTRE_A));
        Resp seconde = doPost(etapesApi(patient) + "/generer-standard", "", medecinA());
        m.put("03 seconde generation : meme nombre", corps(seconde).size());
        m.put("04 aucun doublon en base", jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT categorie, libelle FROM etapes_bilan_pre_greffe "
                + "WHERE center_id = ? GROUP BY categorie, libelle HAVING COUNT(*) > 1) t", Integer.class, CENTRE_A));
        m.put("05 etat des statuts", jdbc.queryForList("SELECT statut, COUNT(*) AS n FROM etapes_bilan_pre_greffe WHERE center_id = ? GROUP BY statut",
                CENTRE_A));
        arbre("generation", m);
    }

    @Test
    void la_generation_respecte_les_etapes_deja_presentes_meme_modifiees_et_ajoute_les_manquantes() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        etapeEnBase(CENTRE_A, patient, "ECG", "CARDIOLOGIQUE", "ECG", "FAIT", fixe(5), "Normal");
        etapeEnBase(CENTRE_A, patient, "ECG_BIS", "CARDIOLOGIQUE", "ECG", "A_FAIRE", null, null);
        etapeEnBase(CENTRE_A, patient, "LIBRE", "AUTRE", "Consultation libre", "PLANIFIE", null, null);
        etapeEnBase(CENTRE_A, patient, "LIBELLE_DIFFERENT", "ORL", "consultation orl", "A_FAIRE", null, null);
        UUID autre = patientDm7(CENTRE_A, "PAT_B");
        Map<String, Object> m = new LinkedHashMap<>();
        Resp r = doPost(etapesApi(patient) + "/generer-standard", "", medecinA());
        m.put("01 total apres generation", corps(r).size());
        m.put("02 etapes de depart conservees telles quelles", jdbc.queryForList("SELECT libelle, statut, resultat FROM etapes_bilan_pre_greffe "
                + "WHERE center_id = ? AND (libelle = 'ECG' OR categorie = 'AUTRE') ORDER BY libelle, statut", CENTRE_A));
        m.put("03 un libelle de casse differente n'est pas reconnu : les deux coexistent", jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "etapes_bilan_pre_greffe WHERE center_id = ? AND categorie = 'ORL'", Integer.class, CENTRE_A));
        m.put("04 les etapes d'un patient ne comptent pas pour l'autre", corps(doPost(etapesApi(autre) + "/generer-standard", "", medecinA())).size());
        arbre("generation_partielle", m);
    }

    @Test
    void la_creation_d_une_etape_libre_et_ses_refus() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 creation MEDECIN", resume(doPost(etapesApi(patient), corps("centerId", CENTRE_A, "categorie", "DERMATOLOGIQUE", "libelle", "Examen cutane"),
                medecinA()), CHAMPS));
        m.put("02 creation INFIRMIER", resume(doPost(etapesApi(patient), corps("centerId", CENTRE_A, "categorie", "AUTRE", "libelle", "Autre examen"),
                infirmierA()), CHAMPS));
        m.put("03 meme etape deux fois (aucune unicite)", resume(doPost(etapesApi(patient), corps("centerId", CENTRE_A, "categorie", "AUTRE", "libelle",
                "Autre examen"), medecinA()), CHAMPS));
        m.put("04 compte apres trois creations", nombreDe("etapes_bilan_pre_greffe", CENTRE_A));
        m.put("05 patient inconnu (aucune garde de patient)", resume(doPost(etapesApi(idStable("patient", "inconnu")), corps("centerId", CENTRE_A,
                "categorie", "AUTRE", "libelle", "Etape orpheline"), medecinA()), "categorie", "libelle", "statut"));
        Map<String, String> refus = new LinkedHashMap<>();
        refus.put("libelle absent", refus(doPost(etapesApi(patient), corps("centerId", CENTRE_A, "categorie", "AUTRE"), medecinA())));
        refus.put("libelle blanc", refus(doPost(etapesApi(patient), corps("centerId", CENTRE_A, "categorie", "AUTRE", "libelle", "   "), medecinA())));
        refus.put("categorie absente", refus(doPost(etapesApi(patient), corps("centerId", CENTRE_A, "libelle", "x"), medecinA())));
        refus.put("categorie inconnue", refus(doPost(etapesApi(patient), corps("centerId", CENTRE_A, "categorie", "ASTROLOGIQUE", "libelle", "x"), medecinA())));
        refus.put("libelle de 255 caracteres", refus(doPost(etapesApi(patient), corps("centerId", CENTRE_A, "categorie", "AUTRE", "libelle", "l".repeat(255)),
                medecinA())));
        refus.put("libelle de 256 caracteres", refus(doPost(etapesApi(patient), corps("centerId", CENTRE_A, "categorie", "AUTRE", "libelle", "l".repeat(256)),
                medecinA())));
        refus.put("corps vide", refus(doPost(etapesApi(patient), "", medecinA())));
        refus.put("corps illisible", refus(doPost(etapesApi(patient), "[", medecinA())));
        m.put("06 refus", refus);
        arbre("creation", m);
    }

    @Test
    void la_mise_a_jour_remplace_tous_les_champs_et_exige_une_date_pour_une_etape_faite() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        UUID etape = etapeEnBase(CENTRE_A, patient, "ETAPE", "VIROLOGIQUE", "Serologie CMV", "A_FAIRE", null, null);
        UUID demande = idStable("demande", "quelconque");
        UUID serologie = idStable("serologie", "quelconque");
        aliasNommes.put(demande.toString(), "<DEMANDE_QUELCONQUE>");
        aliasNommes.put(serologie.toString(), "<SEROLOGIE_QUELCONQUE>");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 etape faite avec date, resultat et references libres (aucun controle d'existence)", resume(doPut(etapeApi(patient, etape),
                corps("centerId", CENTRE_A, "statut", "FAIT", "dateRealisation", fixe(8), "resultat", "Negatif", "dateExpiration", fixe(400),
                        "demandeExamenId", demande, "serologieId", serologie), medecinA()), CHAMPS));
        m.put("02 mise a jour INFIRMIER : planifiee, tout le reste efface", resume(doPut(etapeApi(patient, etape),
                corps("centerId", CENTRE_A, "statut", "PLANIFIE"), infirmierA()), CHAMPS));
        m.put("03 non applicable avec une date", resume(doPut(etapeApi(patient, etape),
                corps("centerId", CENTRE_A, "statut", "NON_APPLICABLE", "dateRealisation", fixe(9)), medecinA()), CHAMPS));
        m.put("04 a faire avec une date de realisation (acceptee)", resume(doPut(etapeApi(patient, etape),
                corps("centerId", CENTRE_A, "statut", "A_FAIRE", "dateRealisation", fixe(9)), medecinA()), CHAMPS));
        m.put("05 etat apres les mises a jour", etatDesEtapes());
        String avant = empreinteGreffe();
        Map<String, String> refus = new LinkedHashMap<>();
        refus.put("faite sans date", refus(doPut(etapeApi(patient, etape), corps("centerId", CENTRE_A, "statut", "FAIT"), medecinA())));
        refus.put("statut absent", refus(doPut(etapeApi(patient, etape), corps("centerId", CENTRE_A), medecinA())));
        refus.put("statut inconnu", refus(doPut(etapeApi(patient, etape), corps("centerId", CENTRE_A, "statut", "TERMINE"), medecinA())));
        refus.put("etape inconnue", refus(doPut(etapeApi(patient, idStable("etape", "inconnue")), corps("centerId", CENTRE_A, "statut", "PLANIFIE"), medecinA())));
        refus.put("etape d'un autre patient", refus(doPut(etapeApi(patientDm7(CENTRE_A, "PAT_B"), etape), corps("centerId", CENTRE_A, "statut", "PLANIFIE"),
                medecinA())));
        refus.put("identifiant d'etape illisible", refus(doPut(etapesApi(patient) + "/pas-un-uuid", corps("centerId", CENTRE_A, "statut", "PLANIFIE"), medecinA())));
        refus.put("corps vide", refus(doPut(etapeApi(patient, etape), "", medecinA())));
        m.put("06 refus", refus);
        m.put("07 aucune ecriture par les refus", empreinteGreffe().equals(avant));
        arbre("mise_a_jour", m);
    }

    @Test
    void la_liste_est_triee_par_categorie_puis_libelle_et_la_suppression_ne_touche_que_l_etape_visee() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        UUID autre = patientDm7(CENTRE_A, "PAT_B");
        UUID b = etapeEnBase(CENTRE_A, patient, "B", "ORL", "b libelle", "A_FAIRE", null, null);
        etapeEnBase(CENTRE_A, patient, "A", "ORL", "A libelle", "A_FAIRE", null, null);
        etapeEnBase(CENTRE_A, patient, "C", "CARDIOLOGIQUE", "z", "FAIT", fixe(3), "ok");
        etapeEnBase(CENTRE_A, patient, "D", "AUTRE", "Zzz", "A_FAIRE", null, null);
        UUID chezLAutre = etapeEnBase(CENTRE_A, autre, "E", "AUTRE", "Zzz", "A_FAIRE", null, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 liste du patient", liste(doGet(etapesApi(patient), medecinA()), "categorie", "libelle", "statut"));
        m.put("02 liste du patient sans etape", liste(doGet(etapesApi(patientDm7(CENTRE_A, "PAT_C")), as(CENTRE_A, "ADMIN")), "libelle"));
        m.put("03 liste d'un patient inconnu", liste(doGet(etapesApi(idStable("patient", "inconnu")), infirmierA()), "libelle"));
        m.put("04 suppression", doDelete(etapeApi(patient, b), medecinA()).status());
        m.put("05 apres la suppression", liste(doGet(etapesApi(patient), medecinA()), "libelle"));
        m.put("06 meme suppression rejouee (204 silencieux)", doDelete(etapeApi(patient, b), medecinA()).status());
        m.put("07 suppression d'une etape inconnue (204 silencieux)", doDelete(etapeApi(patient, idStable("etape", "inconnue")), medecinA()).status());
        m.put("08 suppression d'une etape d'un autre patient (204 silencieux, rien supprime)", doDelete(etapeApi(patient, chezLAutre), medecinA()).status());
        m.put("09 etapes de l'autre patient", liste(doGet(etapesApi(autre), medecinA()), "libelle"));
        m.put("10 suppression INFIRMIER (refusee)", refus(doDelete(etapeApi(autre, chezLAutre), infirmierA())));
        m.put("11 etapes restantes", nombreDe("etapes_bilan_pre_greffe", CENTRE_A));
        arbre("liste_et_suppression", m);
    }

    @Test
    void une_ligne_illisible_fait_echouer_toute_la_liste_et_la_generation() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        etapeEnBase(CENTRE_A, patient, "SAINE", "ORL", "Consultation ORL", "A_FAIRE", null, null);
        etapeEnBase(CENTRE_A, patient, "ILLISIBLE", "CATEGORIE_DISPARUE", "Etape ancienne", "A_FAIRE", null, null);
        UUID autre = patientDm7(CENTRE_A, "PAT_B");
        etapeEnBase(CENTRE_A, autre, "STATUT_ILLISIBLE", "ORL", "Etape", "STATUT_DISPARU", null, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 liste, categorie disparue", refus(doGet(etapesApi(patient), medecinA())));
        m.put("02 generation, categorie disparue", refus(doPost(etapesApi(patient) + "/generer-standard", "", medecinA())));
        m.put("03 liste, statut disparu", refus(doGet(etapesApi(autre), medecinA())));
        m.put("04 creation sur un patient aux lignes illisibles (acceptee : aucune relecture)", refus(doPost(etapesApi(patient), corps("centerId", CENTRE_A,
                "categorie", "AUTRE", "libelle", "Nouvelle"), medecinA())));
        m.put("05 mise a jour d'une etape saine voisine d'une ligne illisible", refus(doPut(etapeApi(patient, idStable("etape", "SAINE-" + CENTRE_A)),
                corps("centerId", CENTRE_A, "statut", "PLANIFIE"), medecinA())));
        m.put("06 suppression de la ligne illisible", doDelete(etapeApi(patient, idStable("etape", "ILLISIBLE-" + CENTRE_A)), medecinA()).status());
        m.put("07 lignes restantes du patient", nombreDe("etapes_bilan_pre_greffe", CENTRE_A));
        arbre("lignes_illisibles", m);
    }

    @Test
    void deux_generations_simultanees_peuvent_doubler_les_etapes_faute_d_unicite_en_base() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        List<Resp> reponses = enParallele(() -> doPost(etapesApi(patient) + "/generer-standard", "", medecinA()),
                () -> doPost(etapesApi(patient) + "/generer-standard", "", infirmierA()));
        int total = nombreDe("etapes_bilan_pre_greffe", CENTRE_A);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 statuts des deux appels", reponses.stream().map(Resp::status).toList());
        // Entre 23 (l'un a vu les etapes de l'autre) et 46 (les deux ont lu une liste vide) : l'issue depend du calendrier des threads.
        m.put("02 le total est compris entre 23 et 46", total >= 23 && total <= 46);
        m.put("03 une generation ulterieure ne cree rien de plus", corps(doPost(etapesApi(patient) + "/generer-standard", "", medecinA())).size() == total);
        arbre("simultanees", m);
    }
}
