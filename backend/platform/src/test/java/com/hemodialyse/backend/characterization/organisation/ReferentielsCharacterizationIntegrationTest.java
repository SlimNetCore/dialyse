package com.hemodialyse.backend.characterization.organisation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.UUID;

/**
 * Filet de caractérisation O2/O3 (salles, créneaux, médecins, référentiels communs) : routes {@code GET /api/v1/referentials/*}
 * observées sur le legacy ({@code ReferentialRestController}) : droits (11 profils + anonyme), contenu et ordre des listes, garde de
 * centre (le legacy croyait le {@code centerId} du client), paramètres illisibles.
 */
class ReferentielsCharacterizationIntegrationTest extends OrganisationCharacterizationSupport {

    private static final String R = "/api/v1/referentials";
    private static final List<String> LECTURES = List.of("medecins", "salles", "positions", "transporteurs", "categories-transport",
            "forfaits", "etats-patients", "articles", "generateurs", "centres-payeurs", "centres-payeurs-details", "caisses", "agences");

    @Override
    protected String theme() {
        return "o3-referentiels";
    }

    private UUID salleA2;

    private void amorcer() {
        reinitialiser();
        salleA2 = salle(CENTRE_A, "SALLE_A2", "S2", "Salle deux");
        salle(CENTRE_A, "SALLE_A1", "S1", "Salle une");
        salle(CENTRE_B, "SALLE_B1", "SB", "Salle du centre B");
        creneau(CENTRE_A, "CRENEAU_A2", "P2", "Soir");
        creneau(CENTRE_A, "CRENEAU_A1", "P1", "Matin");
        creneau(CENTRE_B, "CRENEAU_B1", "PB", "Nuit B");
        medecin(CENTRE_A, "MEDECIN_A2", "Zerrouki", "Amine", "Néphrologue");
        medecin(CENTRE_A, "MEDECIN_A1", "Benali", "Karim", null);
        medecin(CENTRE_B, "MEDECIN_B1", "Autre", "Centre", "Cardiologue");
    }

    @Test
    void les_lectures_selon_les_onze_profils_et_l_anonyme() throws Exception {
        ObjectNode matrice = mapper.createObjectNode();
        for (String lecture : LECTURES) {
            ObjectNode ligne = mapper.createObjectNode();
            for (var profil : profils().entrySet()) {
                amorcer();
                ligne.put(profil.getKey(), statutEtCode(doGet(R + "/" + lecture + "?centerId=" + CENTRE_A, profil.getValue())));
            }
            amorcer();
            ligne.put("anonyme", statutEtCode(doGet(R + "/" + lecture + "?centerId=" + CENTRE_A, null)));
            matrice.set("GET /referentials/" + lecture, ligne);
        }
        goldenNode("matrice-des-droits", matrice);
    }

    @Test
    void contenu_et_ordre_des_salles_positions_et_medecins_du_centre() throws Exception {
        amorcer();
        golden("salles-du-centre-a", doGet(R + "/salles?centerId=" + CENTRE_A, as(CENTRE_A, "ADMIN")));
        golden("positions-du-centre-a", doGet(R + "/positions?centerId=" + CENTRE_A, as(CENTRE_A, "ADMIN")));
        golden("etats-patients", doGet(R + "/etats-patients?centerId=" + CENTRE_A, as(CENTRE_A, "ADMIN")));
        golden("transporteurs-vide", doGet(R + "/transporteurs?centerId=" + CENTRE_A, as(CENTRE_A, "ADMIN")));
        golden("generateurs-vide", doGet(R + "/generateurs?centerId=" + CENTRE_A, as(CENTRE_A, "ADMIN")));
        golden("generateurs-d-une-salle-vide", doGet(R + "/generateurs?centerId=" + CENTRE_A + "&salleId=" + salleA2, as(CENTRE_A, "ADMIN")));
    }

    @Test
    void garde_de_centre_lecture_d_un_autre_centre() throws Exception {
        amorcer();
        golden("salles-du-centre-b-par-un-admin-du-centre-a", doGet(R + "/salles?centerId=" + CENTRE_B, as(CENTRE_A, "ADMIN")));
        golden("medecins-du-centre-b-par-un-secretaire-du-centre-a", doGet(R + "/medecins?centerId=" + CENTRE_B, as(CENTRE_A, "SECRETAIRE")));
        golden("positions-du-centre-b-par-un-admin-sans-centre", doGet(R + "/positions?centerId=" + CENTRE_B, as(null, "ADMIN")));
    }

    @Test
    void parametres_absents_ou_illisibles() throws Exception {
        amorcer();
        golden("salles-sans-centre", doGet(R + "/salles", as(CENTRE_A, "ADMIN")));
    }

    /** ÉCART DÉCIDÉ (KB-ORG-02, 2026-11-02) : le legacy répondait 500 à un identifiant illisible ; le nouveau code répond 400. */
    @Test
    void identifiants_illisibles_ecart_a_decider() throws Exception {
        amorcer();
        golden("salles-centre-illisible", doGet(R + "/salles?centerId=pas-un-uuid", as(CENTRE_A, "ADMIN")));
        golden("generateurs-salle-illisible", doGet(R + "/generateurs?centerId=" + CENTRE_A + "&salleId=pas-un-uuid", as(CENTRE_A, "ADMIN")));
    }

    /**
     * ÉCART DÉCIDÉ (KB-ORG-03, 2026-11-02) : le legacy listait les médecins dans l'ordre physique de la table ; le nouveau code les
     * trie par nom. Le tri par code des salles et des positions est aussi un écart décidé et accepté.
     */
    @Test
    void ordre_des_medecins_ecart_a_decider() throws Exception {
        amorcer();
        golden("medecins-du-centre-a", doGet(R + "/medecins?centerId=" + CENTRE_A, as(CENTRE_A, "ADMIN")));
    }
}
