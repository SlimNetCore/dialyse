package com.hemodialyse.backend.characterization.dossiermedical.dm3;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM3, lot 4 (droits, cloisonnement des centres, effets de bord observables : audit, notifications durables, diffusions).
 */
class LesDroitsEtLesEffetsDesDemandesDExamenCharacterizationIntegrationTest extends ExamensCharacterizationSupport {

    @Override
    protected String dossier() {
        return "droits";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm3(CENTRE_A, "PAT_A");
        patientB = patientDm3(CENTRE_B, "PAT_B");
    }

    @Test
    void les_droits_par_profil_sur_les_six_routes() throws Exception {
        deuxCentres();
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm3().entrySet()) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("liste", statutEtCode(doGet(demandesApi(patientA), p.getValue())));
            ligne.put("creation", statutEtCode(doPost(demandesApi(patientA), demandeOrdinaire(CENTRE_A, fixe(1)), p.getValue())));
            for (String action : List.of("preleve", "resultat-disponible", "valider", "annuler")) {
                nettoyerDm3();
                deuxCentres();
                UUID d = demandeSimple(CENTRE_A, patientA, "DEM_DROITS", fixe(2), statutAvant(action));
                ligne.put(action, statutEtCode(doPut(actionApi(patientA, d, action), corpsValidation(CENTRE_A, "Conclusion fictive"), p.getValue())));
            }
            profils.put(p.getKey(), ligne);
            nettoyerDm3();
            deuxCentres();
        }
        Map<String, Object> anonyme = new LinkedHashMap<>();
        UUID d = demandeSimple(CENTRE_A, patientA, "DEM_ANON", fixe(2), "DEMANDE");
        anonyme.put("liste", statutEtCode(doGetAnonymous(demandesApi(patientA))));
        anonyme.put("creation", statutEtCode(appel("POST", demandesApi(patientA), demandeOrdinaire(CENTRE_A, fixe(1)), null)));
        anonyme.put("preleve", statutEtCode(appel("PUT", actionApi(patientA, d, "preleve"), corpsCentre(CENTRE_A), null)));
        profils.put("anonyme", anonyme);
        arbre("profils", profils);
        assertThat(statutEnBase(d)).isEqualTo("DEMANDE");
    }

    /** Statut de départ qui rend la transition légale : un 200 prouve alors le droit, un 403 le refus. */
    private static String statutAvant(String action) {
        return switch (action) {
            case "preleve", "annuler" -> "DEMANDE";
            case "resultat-disponible" -> "PRELEVE";
            default -> "RESULTAT_DISPONIBLE";
        };
    }

    @Test
    void un_refus_de_droit_n_ecrit_rien_mais_un_corps_ou_une_pagination_illisible_donne_500_avant_le_controle_des_droits() throws Exception {
        deuxCentres();
        UUID d = demandeSimple(CENTRE_A, patientA, "DEM", fixe(1), "DEMANDE");
        String avant = empreinte(d);
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("INFIRMIER : creation", statutEtCode(doPost(demandesApi(patientA), demandeOrdinaire(CENTRE_A, fixe(1)), as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : creation avec un corps illisible", statutEtCode(doPost(demandesApi(patientA), "{pas du json", as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : transition avec un corps absent", statutEtCode(doPut(actionApi(patientA, d, "preleve"), "", as(CENTRE_A, "INFIRMIER"))));
        cas.put("ADMIN : transition", statutEtCode(doPut(actionApi(patientA, d, "annuler"), corpsCentre(CENTRE_A), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : liste avec une pagination illisible", statutEtCode(doGet(demandesApi(patientA) + "?size=abc", as(CENTRE_A, "ADMIN"))));
        cas.put("INFIRMIER : liste avec une pagination illisible", statutEtCode(doGet(demandesApi(patientA) + "?size=abc", as(CENTRE_A, "INFIRMIER"))));
        arbre("refus", cas);
        assertThat(empreinte(d)).isEqualTo(avant);
        assertThat(nombreDeDemandes()).isEqualTo(1);
    }

    @Test
    void le_cloisonnement_par_centre_repose_sur_le_centre_de_la_session_et_la_recherche_par_centre() throws Exception {
        deuxCentres();
        UUID db = demandeSimple(CENTRE_B, patientB, "DEM_B", fixe(1), "DEMANDE");
        UUID da = demandeSimple(CENTRE_A, patientA, "DEM_A", fixe(2), "DEMANDE");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("liste du patient B par le centre A (centre de la session)", page(patientB, "", as(CENTRE_A, "MEDECIN")));
        cas.put("liste avec centerId du centre B (session A)", statutEtCode(doGet(demandesApi(patientB) + "?centerId=" + CENTRE_B, as(CENTRE_A, "MEDECIN"))));
        cas.put("liste du patient A par le centre B (centre de la session)", page(patientA, "", as(CENTRE_B, "MEDECIN")));
        cas.put("creation avec le centre B dans le corps (session A)", statutEtCode(doPost(demandesApi(patientA),
                demandeOrdinaire(CENTRE_B, fixe(1)), as(CENTRE_A, "MEDECIN"))));
        cas.put("creation sans centre dans le corps (centre de la session)", statutEtCode(doPost(demandesApi(patientA),
                corpsDemande(null, MEDECIN_FICTIF, fixe(3), "BIOLOGIE", false, null, ligneSimple()), as(CENTRE_A, "MEDECIN"))));
        cas.put("transition du centre B avec le corps du centre B (session A)", statutEtCode(doPut(actionApi(patientB, db, "preleve"),
                corpsCentre(CENTRE_B), as(CENTRE_A, "MEDECIN"))));
        cas.put("transition du centre B avec le corps du centre A (session A)", statutEtCode(doPut(actionApi(patientB, db, "preleve"),
                corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"))));
        cas.put("MEDECIN sans centre de session : liste", statutEtCode(doGet(demandesApi(patientA), as(null, "MEDECIN"))));
        cas.put("MEDECIN sans centre de session : creation", statutEtCode(doPost(demandesApi(patientA), demandeOrdinaire(CENTRE_A, fixe(1)),
                as(null, "MEDECIN"))));
        cas.put("SUPERADMIN + MEDECIN lit le centre B depuis la session A", statutEtCode(doGet(demandesApi(patientB) + "?centerId=" + CENTRE_B,
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("SUPERADMIN + MEDECIN prelevement au centre B d'une demande du centre B", statutEtCode(doPut(actionApi(patientB, db, "preleve"),
                corpsCentre(CENTRE_B), as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("SUPERADMIN + MEDECIN prelevement au centre B d'une demande du centre A", statutEtCode(doPut(actionApi(patientA, da, "preleve"),
                corpsCentre(CENTRE_B), as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("SUPERADMIN + MEDECIN cree dans le centre B pour un patient du centre B", statutEtCode(doPost(demandesApi(patientB),
                demandeOrdinaire(CENTRE_B, fixe(4)), as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        arbre("cloisonnement", cas);
        etatDesDemandes("demandes");
    }

    private Map<String, Object> page(UUID patient, String parametres, RequestPostProcessor who) throws Exception {
        Resp r = doGet(demandesApi(patient) + (parametres.isEmpty() ? "" : "?" + parametres), who);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", statutEtCode(r));
        List<Object> items = new ArrayList<>();
        corps(r).path("items").forEach(i -> items.add(demandeLisible(i)));
        m.put("total", corps(r).path("total").asInt());
        m.put("items", items);
        return m;
    }

    @Test
    void les_ecritures_sont_journalisees_sans_notification_ni_diffusion_et_les_lectures_ne_le_sont_pas() throws Exception {
        deuxCentres();
        monterLAudit();
        Resp c = doPost(demandesApi(patientA), demandeOrdinaire(CENTRE_A, fixe(1)), as(CENTRE_A, "MEDECIN"));
        String id = idDe(c);
        UUID d = UUID.fromString(id);
        doPut(actionApi(patientA, d, "preleve"), corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
        doPut(actionApi(patientA, d, "preleve"), corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
        doPut(actionApi(patientA, d, "resultat-disponible"), corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
        doPut(actionApi(patientA, d, "valider"), corpsValidation(CENTRE_A, "Conclusion fictive"), as(CENTRE_A, "MEDECIN"));
        doPut(actionApi(patientA, d, "annuler"), corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
        doGet(demandesApi(patientA), as(CENTRE_A, "MEDECIN"));
        doPost(demandesApi(patientA), demandeOrdinaire(CENTRE_A, fixe(1)), as(CENTRE_A, "INFIRMIER"));
        aliasNommes.put(id, "<DEM>");
        audit("audit");
        notifications("notifications");
        diffusionsCapturees("diffusions");
    }

    @Test
    void la_creation_et_la_reception_de_resultat_levent_des_evenements_de_domaine_que_personne_ne_publie() throws Exception {
        deuxCentres();
        monterLAudit();
        UUID d = UUID.fromString(idDe(doPost(demandesApi(patientA), demandeOrdinaire(CENTRE_A, fixe(1)), as(CENTRE_A, "MEDECIN"))));
        doPut(actionApi(patientA, d, "preleve"), corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
        doPut(actionApi(patientA, d, "resultat-disponible"), corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
        // DemandeExamenEmise et ResultatExamenRecu sont collectes par l'agregat mais pullEvents() n'est jamais appele : rien n'est
        // ni diffuse en temps reel, ni notifie durablement (KB-MED-37). Les diffusions sont verrouillees vides.
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("diffusions", new ArrayList<>(diffusions));
        m.put("notifications durables", jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE center_id IN (?, ?)",
                Integer.class, CENTRE_A, CENTRE_B));
        arbre("evenements", m);
    }
}
