package com.hemodialyse.backend.characterization.dossiermedical.dm3;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM3, lot 2 (cycle de statut) : {@code PUT .../demandes-examen/{id}/preleve | resultat-disponible | valider | annuler}.
 * Machine à états {@code DEMANDE -> PRELEVE -> RESULTAT_DISPONIBLE -> VALIDE}, {@code ANNULE} depuis DEMANDE ou PRELEVE.
 */
class LesTransitionsDeLaDemandeDExamenCharacterizationIntegrationTest extends ExamensCharacterizationSupport {

    private static final List<String> STATUTS = List.of("DEMANDE", "PRELEVE", "RESULTAT_DISPONIBLE", "VALIDE", "ANNULE");
    private static final List<String> ACTIONS = List.of("preleve", "resultat-disponible", "valider", "annuler");

    @Override
    protected String dossier() {
        return "transitions";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm3(CENTRE_A, "PAT_A");
        patientB = patientDm3(CENTRE_B, "PAT_B");
    }

    private Resp agir(UUID patient, UUID demande, String action) throws Exception {
        return doPut(actionApi(patient, demande, action), "valider".equals(action) ? corpsValidation(CENTRE_A, "Conclusion fictive")
                : corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
    }

    @Test
    void le_parcours_nominal_demande_preleve_resultat_disponible_valide_conserve_les_lignes_et_pose_la_conclusion() throws Exception {
        deuxCentres();
        Resp creation = doPost(demandesApi(patientA), corpsDemande(CENTRE_A, MEDECIN_FICTIF, fixe(2), "BIOLOGIE", true, "Bilan fictif",
                "[{\"codeSystem\":\"LOINC\",\"code\":\"718-7\",\"libelle\":\"Hemoglobine\"},{\"libelle\":\"Autre examen\"}]"), as(CENTRE_A, "MEDECIN"));
        UUID demande = UUID.fromString(idDe(creation));
        aliasNommes.put(demande.toString(), "<DEM>");
        String lignesAvant = jdbc.queryForList("SELECT id FROM lignes_demande_examen WHERE demande_id = ? ORDER BY id", demande).toString();
        String creeLe = jdbc.queryForObject("SELECT created_at FROM demandes_examen WHERE id = ?", Object.class, demande).toString();
        Map<String, Object> etapes = new LinkedHashMap<>();
        for (String action : List.of("preleve", "resultat-disponible", "valider")) {
            Resp r = agir(patientA, demande, action);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("reponse", statutEtCode(r));
            e.put("demande", demandeLisible(corps(r)));
            e.put("lignes conservees (memes identifiants)",
                    jdbc.queryForList("SELECT id FROM lignes_demande_examen WHERE demande_id = ? ORDER BY id", demande).toString().equals(lignesAvant));
            e.put("created_at conserve", creeLe.equals(jdbc.queryForObject("SELECT created_at FROM demandes_examen WHERE id = ?",
                    Object.class, demande).toString()));
            e.put("updated_at posterieur a created_at", Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT updated_at > created_at FROM demandes_examen WHERE id = ?", Boolean.class, demande)));
            etapes.put(action, e);
        }
        arbre("etapes", etapes);
        etatDesDemandes("demandes");
        etatDesLignes("lignes");
    }

    @Test
    void la_matrice_des_transitions_depuis_chaque_etat_pour_chaque_action() throws Exception {
        deuxCentres();
        Map<String, Object> matrice = new LinkedHashMap<>();
        int jour = 0;
        for (String depart : STATUTS) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            for (String action : ACTIONS) {
                nettoyerDm3();
                deuxCentres();
                UUID d = demandeSimple(CENTRE_A, patientA, "DEM", fixe(++jour % 5), depart);
                Resp r = agir(patientA, d, action);
                Map<String, Object> cas = new LinkedHashMap<>();
                cas.put("reponse", statutEtCode(r));
                if (r.status() == 200) {
                    cas.put("statut", corps(r).path("statut").asString());
                    cas.put("conclusion", texte(corps(r), "conclusion"));
                }
                cas.put("statut en base", statutEnBase(d));
                ligne.put(action, cas);
            }
            matrice.put("depuis " + depart, ligne);
        }
        arbre("matrice", matrice);
    }

    @Test
    void l_annulation_est_possible_avant_le_resultat_et_pas_apres_et_les_etats_terminaux_ne_bougent_plus() throws Exception {
        deuxCentres();
        UUID demande = demandeSimple(CENTRE_A, patientA, "DEM_DEMANDE", fixe(1), "DEMANDE");
        UUID preleve = demandeSimple(CENTRE_A, patientA, "DEM_PRELEVE", fixe(2), "PRELEVE");
        UUID resultat = demandeSimple(CENTRE_A, patientA, "DEM_RESULTAT", fixe(3), "RESULTAT_DISPONIBLE");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("annuler une DEMANDE", statutEtCode(agir(patientA, demande, "annuler")));
        cas.put("annuler une PRELEVE", statutEtCode(agir(patientA, preleve, "annuler")));
        cas.put("annuler une RESULTAT_DISPONIBLE", statutEtCode(agir(patientA, resultat, "annuler")));
        for (String action : ACTIONS) {
            cas.put(action + " sur l'annulee", statutEtCode(agir(patientA, demande, action)));
        }
        arbre("annulations", cas);
        etatDesDemandes("demandes");
    }

    @Test
    void rejouer_une_transition_deja_faite_est_refuse_en_422_et_ne_change_rien() throws Exception {
        deuxCentres();
        UUID d = UUID.fromString(idDe(doPost(demandesApi(patientA), demandeOrdinaire(CENTRE_A, fixe(1)), as(CENTRE_A, "MEDECIN"))));
        aliasNommes.put(d.toString(), "<DEM>");
        assertThat(agir(patientA, d, "preleve").status()).isEqualTo(200);
        String apresPreleve = empreinte(d);
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("preleve une seconde fois", statutEtCode(agir(patientA, d, "preleve")));
        assertThat(empreinte(d)).isEqualTo(apresPreleve);
        assertThat(agir(patientA, d, "resultat-disponible").status()).isEqualTo(200);
        String apresResultat = empreinte(d);
        cas.put("resultat-disponible une seconde fois", statutEtCode(agir(patientA, d, "resultat-disponible")));
        cas.put("retour en arriere : preleve apres resultat", statutEtCode(agir(patientA, d, "preleve")));
        assertThat(empreinte(d)).isEqualTo(apresResultat);
        assertThat(agir(patientA, d, "valider").status()).isEqualTo(200);
        String apresValidation = empreinte(d);
        cas.put("valider une seconde fois", statutEtCode(agir(patientA, d, "valider")));
        cas.put("annuler une validee", statutEtCode(agir(patientA, d, "annuler")));
        cas.put("preleve une validee", statutEtCode(agir(patientA, d, "preleve")));
        assertThat(empreinte(d)).isEqualTo(apresValidation);
        arbre("rejeux", cas);
        etatDesDemandes("demandes");
    }

    @Test
    void la_conclusion_n_est_posee_que_par_la_validation_et_n_est_pas_controlee() throws Exception {
        deuxCentres();
        Map<String, Object> cas = new LinkedHashMap<>();
        String[][] conclusions = {
                {"conclusion renseignee", "Resultats normaux fictifs"},
                {"conclusion absente", null},
                {"conclusion vide", ""},
                {"conclusion blanche", "   "},
                {"conclusion tres longue (20000 caracteres)", "c".repeat(20000)},
                {"conclusion avec accents et guillemets echappes", "Hemoglobine a 11,5 g\\/dL \\\"stable\\\" - controle à 1 mois"},
        };
        int jour = 0;
        for (String[] c : conclusions) {
            UUID d = demandeSimple(CENTRE_A, patientA, "DEM" + jour, fixe(++jour), "RESULTAT_DISPONIBLE");
            Resp r = doPut(actionApi(patientA, d, "valider"), corpsValidation(CENTRE_A, c[1]), as(CENTRE_A, "MEDECIN"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reponse", statutEtCode(r));
            String rendue = texte(corps(r), "conclusion");
            m.put("longueur rendue", rendue == null ? null : rendue.length());
            m.put("conclusion (tronquee a 60)", rendue == null ? null : rendue.substring(0, Math.min(60, rendue.length())));
            String enBase = jdbc.queryForObject("SELECT conclusion FROM demandes_examen WHERE id = ?", String.class, d);
            m.put("identique en base", java.util.Objects.equals(rendue, enBase));
            cas.put(c[0], m);
        }
        // Une conclusion fournie a une autre action est ignoree.
        UUID autre = demandeSimple(CENTRE_A, patientA, "DEM_AUTRE", fixe(4), "DEMANDE");
        Resp r = doPut(actionApi(patientA, autre, "preleve"), corpsValidation(CENTRE_A, "Conclusion ignoree"), as(CENTRE_A, "MEDECIN"));
        cas.put("conclusion envoyee avec preleve", Map.of("reponse", statutEtCode(r), "conclusion", String.valueOf(texte(corps(r), "conclusion"))));
        arbre("conclusions", cas);
    }

    @Test
    void la_demande_est_cherchee_par_id_patient_et_centre_sinon_400_et_les_routes_inexistantes_ne_repondent_pas() throws Exception {
        deuxCentres();
        UUID da = demandeSimple(CENTRE_A, patientA, "DEM_A", fixe(1), "DEMANDE");
        UUID db = demandeSimple(CENTRE_B, patientB, "DEM_B", fixe(1), "DEMANDE");
        UUID autrePatient = patientDm3(CENTRE_A, "PAT_A2");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("demande inconnue", statutEtCode(agir(patientA, idStable("demande", "INCONNUE"), "preleve")));
        cas.put("demande du patient A sous le patient A2", statutEtCode(agir(autrePatient, da, "preleve")));
        cas.put("demande du patient A sous un patient inconnu", statutEtCode(agir(idStable("patient", "INCONNU"), da, "preleve")));
        cas.put("demande du centre B par le centre A", statutEtCode(agir(patientB, db, "preleve")));
        cas.put("demande du centre B, corps du centre B (session A)", statutEtCode(doPut(actionApi(patientB, db, "preleve"), corpsCentre(CENTRE_B),
                as(CENTRE_A, "MEDECIN"))));
        cas.put("corps de transition sans centre (centre de la session)", statutEtCode(doPut(actionApi(patientA, da, "preleve"), "{}",
                as(CENTRE_A, "MEDECIN"))));
        cas.put("corps de transition absent", statutEtCode(doPut(actionApi(patientA, da, "annuler"), "", as(CENTRE_A, "MEDECIN"))));
        cas.put("corps de transition mal forme", statutEtCode(doPut(actionApi(patientA, da, "annuler"), "{pas du json", as(CENTRE_A, "MEDECIN"))));
        cas.put("identifiant de demande illisible", statutEtCode(doPut(demandesApi(patientA) + "/pas-un-uuid/preleve", corpsCentre(CENTRE_A),
                as(CENTRE_A, "MEDECIN"))));
        cas.put("identifiant de patient illisible", statutEtCode(doPut(PATIENTS_API + "/pas-un-uuid/demandes-examen/" + da + "/preleve",
                corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"))));
        cas.put("action inconnue", statutEtCode(doPut(actionApi(patientA, da, "reouvrir"), corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"))));
        cas.put("PUT de modification du contenu (route inexistante)", statutEtCode(doPut(demandesApi(patientA) + "/" + da,
                demandeOrdinaire(CENTRE_A, fixe(1)), as(CENTRE_A, "MEDECIN"))));
        cas.put("DELETE (route inexistante)", statutEtCode(doDelete(demandesApi(patientA) + "/" + da, as(CENTRE_A, "MEDECIN"))));
        cas.put("GET d'une demande seule (route inexistante)", statutEtCode(doGet(demandesApi(patientA) + "/" + da, as(CENTRE_A, "MEDECIN"))));
        cas.put("POST sur une action (mauvaise methode)", statutEtCode(doPost(actionApi(patientA, da, "preleve"), corpsCentre(CENTRE_A),
                as(CENTRE_A, "MEDECIN"))));
        arbre("erreurs", cas);
        etatDesDemandes("demandes");
    }

    @Test
    void un_statut_illisible_en_base_fait_refuser_la_transition_et_toute_la_liste_du_patient() throws Exception {
        deuxCentres();
        UUID bon = demandeSimple(CENTRE_A, patientA, "DEM_BONNE", fixe(1), "DEMANDE");
        UUID mauvais = demandeSimple(CENTRE_A, patientA, "DEM_MAUVAISE", fixe(2), "EN_COURS");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("transition de la demande au statut inconnu", statutEtCode(agir(patientA, mauvais, "preleve")));
        cas.put("transition de la bonne demande (temoin)", statutEtCode(agir(patientA, bon, "preleve")));
        cas.put("liste du patient", statutEtCode(doGet(demandesApi(patientA), as(CENTRE_A, "MEDECIN"))));
        cas.put("liste avec une categorie inconnue", statutEtCode(listeAvecCategorieInconnue()));
        arbre("donnees_incoherentes", cas);
        etatDesDemandes("demandes");
    }

    private Resp listeAvecCategorieInconnue() throws Exception {
        UUID autre = patientDm3(CENTRE_A, "PAT_CAT");
        demande(CENTRE_A, autre, "DEM_CAT", fixe(1), "SCANNER", "DEMANDE", false, null, null);
        return doGet(demandesApi(autre), as(CENTRE_A, "MEDECIN"));
    }

    @Test
    void deux_prelevements_simultanes_n_ont_pas_de_verrou_et_laissent_la_demande_preleve() throws Exception {
        deuxCentres();
        UUID d = demandeSimple(CENTRE_A, patientA, "DEM", fixe(1), "DEMANDE");
        List<Resp> reponses = enParallele(
                () -> agir(patientA, d, "preleve"),
                () -> agir(patientA, d, "preleve"));
        // Invariants seulement (la concurrence n'est pas verrouillee en golden) : aucun verrou optimiste, l'etat final est PRELEVE.
        for (Resp r : reponses) {
            assertThat(r.status()).isIn(200, 409, 422, 500);
        }
        assertThat(reponses.stream().filter(r -> r.status() == 200).count()).isGreaterThanOrEqualTo(1);
        assertThat(statutEnBase(d)).isEqualTo("PRELEVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lignes_demande_examen WHERE demande_id = ?", Integer.class, d)).isEqualTo(1);
    }
}
