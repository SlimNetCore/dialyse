package com.hemodialyse.backend.characterization.planification.placement;

import com.hemodialyse.backend.characterization.planification.PlacementCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MARDI;

/**
 * Filet de caractérisation P5 : réaction de la Planification aux événements de la GMAO ({@code EquipementMisHorsService},
 * {@code EquipementReforme} reçus par {@code GmaoEvenementsPlanificationListener} puis {@code GenerateurIndisponibleService}) :
 * alerte « générateur indisponible » avec les patients placés, jamais de déplacement automatique. Les événements sont produits par
 * les routes GMAO réelles ({@code POST /api/v1/gmao/equipements/{id}/hors-service|reformer|reactiver}) ; la livraison est asynchrone
 * (registre d'événements) : le harnais attend que le registre soit vide. Aucune source d'événement n'est simulée.
 */
class AlerteGenerateurIndisponibleCharacterizationIntegrationTest extends PlacementCharacterizationSupport {

    @Override
    protected String theme() {
        return "p5-placement/gmao";
    }

    private Resp gmaoPost(UUID equipement, String action, String corps, org.springframework.test.web.servlet.request.RequestPostProcessor who)
            throws Exception {
        Resp r = doPost(GMAO + "/" + equipement + "/" + action, corps == null ? "{}" : corps, who);
        attendreLesEvenements();
        return r;
    }

    private Resp horsService(UUID equipement) throws Exception {
        return gmaoPost(equipement, "hors-service", "{\"raison\":\"Panne\"}", gmao());
    }

    @Test
    void la_mise_hors_service_d_un_generateur_alerte_avec_les_patients_et_ne_deplace_personne() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_AMRANI", salle1, matin, g1a, LUNDI, MARDI);
        place(CENTRE_A, "PAT_BENALI", salle1, apresMidi, g1a, LUNDI);
        place(CENTRE_A, "PAT_AUTRE_GENERATEUR", salle1, matin, g1b, LUNDI);
        Resp r = horsService(g1a);
        arbre("reponse", Map.of("statut", r.status()));
        notifications("alerte_hors_service");
        diffusionsCapturees("diffusions_hors_service");
        etatDesPlaces("places_apres_hors_service");
    }

    @Test
    void l_alerte_cite_cinq_patients_au_plus_dans_l_ordre_du_nom_et_compte_tous_les_autres() throws Exception {
        decorDePlacement();
        for (String nom : new String[]{"F", "E", "D", "C", "B", "A", "G"}) {
            place(CENTRE_A, "PAT_" + nom, salle1, matin, g1a, LUNDI);
        }
        horsService(g1a);
        notifications("alerte_sept_patients");
    }

    @Test
    void les_patients_sortis_ne_sont_pas_cites_et_un_patient_sans_jour_l_est() throws Exception {
        decorDePlacement();
        UUID transfereAvant = place(CENTRE_A, "PAT_TRANSFERE_AVANT", salle1, matin, g1a, LUNDI);
        etat(transfereAvant, "TRANSFERE", jour(-2));
        UUID transfereAujourdhui = place(CENTRE_A, "PAT_TRANSFERE_AUJOURDHUI", salle1, matin, g1a, MARDI);
        etat(transfereAujourdhui, "TRANSFERE", jour(0));
        UUID occasionnel = place(CENTRE_A, "PAT_OCCASIONNEL_FUTUR", salle1, matin, g1a, MARDI);
        etat(occasionnel, "OCCASIONNEL", jour(4));
        UUID sansJour = place(CENTRE_A, "PAT_SANS_JOUR", salle1, matin, g1a);
        UUID dArriere = place(CENTRE_B, "PAT_AUTRE_CENTRE", salleB, creneauB, g1a, LUNDI);
        horsService(g1a);
        notifications("alerte_patients_sortis");
        arbre("ids", Map.of("sansJour", sansJour != null, "dArriere", dArriere != null));
    }

    @Test
    void un_generateur_sans_patient_declenche_quand_meme_l_alerte() throws Exception {
        decorDePlacement();
        horsService(g2a);
        notifications("alerte_sans_patient");
    }

    @Test
    void la_reforme_d_un_generateur_en_service_alerte_avec_le_statut_reforme() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        Resp r = gmaoPost(g1a, "reformer", "{\"motif\":\"Fin de vie\"}", reformeur());
        arbre("reponse", Map.of("statut", r.status()));
        notifications("alerte_reforme");
        etatDesPlaces("places_apres_reforme");
    }

    @Test
    void les_autres_cas_ne_declenchent_aucune_alerte() throws Exception {
        decorDePlacement();
        UUID eau = equipement(CENTRE_A, "E_EAU", "STATION_TRAITEMENT_EAU", salle1, "EN_SERVICE");
        UUID hs = generateur(CENTRE_A, "G1HS", salle1, "HORS_SERVICE");
        UUID autreCentre = generateur(CENTRE_B, "GB1", salleB, "EN_SERVICE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("station d'eau hors service", horsService(eau).status());
        m.put("reforme depuis hors service", gmaoPost(hs, "reformer", "{\"motif\":\"HS\"}", reformeur()).status());
        m.put("generateur d'un autre centre", horsService(autreCentre).status());
        m.put("generateur d'une autre salle mis hors service puis reactive", horsService(g1b).status() + "/"
                + gmaoPost(g1b, "reactiver", null, gmao()).status());
        m.put("deuxieme mise hors service du meme generateur", horsService(g1a).status() + "/" + horsService(g1a).status());
        arbre("autres_cas", m);
        notifications("autres_cas_alertes");
    }

    @Test
    void apres_la_mise_hors_service_le_generateur_n_est_plus_propose_et_son_patient_garde_sa_place_modifiable() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        horsService(g1a);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("propositions salle 1 lundi", projectionDesPropositions(doGet(AFFECTATIONS + "?jours=LUNDI&salleId=" + salle1 + "&limite=30", adminA())));
        m.put("fiche inchangee", statutEtCode(doPut(PATIENTS + "/" + UUID.nameUUIDFromBytes("planification-p3-patient-PAT_1".getBytes()),
                ficheDe("PAT_1", CENTRE_A, salle1, matin, g1a, "LUNDI"), adminA())));
        m.put("fiche avec un jour de plus", statutEtCode(doPut(PATIENTS + "/" + UUID.nameUUIDFromBytes("planification-p3-patient-PAT_1".getBytes()),
                ficheDe("PAT_1", CENTRE_A, salle1, matin, g1a, "LUNDI", "MARDI"), adminA())));
        arbre("effets_sur_les_lectures", m);
        etatDesPlaces("effets_sur_les_lectures_etat");
    }

    @Test
    void l_alerte_est_rejouee_a_chaque_evenement_sans_dedoublonnage() throws Exception {
        decorDePlacement();
        horsService(g1a);
        gmaoPost(g1a, "reactiver", null, gmao());
        horsService(g1a);
        arbre("alertes_rejouees", Map.of("nombre", jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_evenement WHERE center_id = ? AND type = 'GENERATEUR_INDISPONIBLE'", Integer.class, CENTRE_A)));
    }
}
