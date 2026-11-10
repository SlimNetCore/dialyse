package com.hemodialyse.backend.characterization.patientcouverture.rf3;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * RF3d (KB-RF3-01 à 03, résiduel RF1-R4, ADR 0129) : les refus de forme de TOUTE l'administration générique des référentiels
 * ({@code /api/v1/admin/referentials/**}) répondent 400 {@code BAD_REQUEST} au lieu de 500 : corps non JSON ou absent,
 * {@code centerId} (corps ou requête), identifiant de ressource et {@code page} illisibles. Le 403 du profil sans droit passe AVANT
 * le 400. Les trois niveaux de payeurs sont couverts par leurs propres classes ; ici les autres référentiels (créneaux, salles,
 * médecins, générateurs) et la page illisible des payeurs.
 */
class RefusDeFormeDeLAdministrationGeneriqueCharacterizationIntegrationTest extends PayeursCharacterizationSupport {

    private static final String RACINE = "/api/v1/admin/referentials/";

    @Override
    protected String dossier() {
        return "admin-generique";
    }

    @Test
    void les_refus_de_forme_des_autres_referentiels_repondent_400_et_le_403_passe_avant() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        for (String type : new String[]{"creneaux", "salles", "medecins", "generateurs"}) {
            String url = RACINE + type;
            String n = type + " : ";
            m.set(n + "creation, corps illisible", vue(doPost(url, "{'values':", admin)));
            m.set(n + "creation, sans corps", vue(doPost(url, null, admin)));
            m.set(n + "creation, centre illisible dans le corps", vue(doPost(url, "{'centerId':'pas-un-uuid','values':{'code':'X'}}", admin)));
            m.set(n + "modification, identifiant illisible", vue(doPut(url + "/pas-un-uuid", "{'values':{'code':'X'}}", admin)));
            m.set(n + "modification, corps illisible", vue(doPut(url + "/" + UUID.randomUUID(), "{'values':", admin)));
            m.set(n + "modification, sans corps", vue(doPut(url + "/" + UUID.randomUUID(), "", admin)));
            m.set(n + "suppression, identifiant illisible", vue(doDelete(url + "/pas-un-uuid", admin)));
            m.set(n + "suppression, centre illisible", vue(doDelete(url + "/" + UUID.randomUUID() + "?centerId=pas-un-uuid", admin)));
            m.set(n + "liste, centre illisible", vue(doGet(url + "?centerId=pas-un-uuid", admin)));
            m.set(n + "liste, page illisible", vue(doGet(url + "?page=abc", admin)));
            m.set(n + "liste, taille illisible", vue(doGet(url + "?size=abc", admin)));
            m.set(n + "secretaire, corps illisible", vue(doPost(url, "{'values':", as(CENTRE_A, "SECRETAIRE"))));
            m.set(n + "anonyme, corps illisible", vue(doPost(url, "{'values':", null)));
        }
        for (String type : new String[]{"caisses", "agences", "centres-payeurs"}) {
            String n = type + " : ";
            m.set(n + "liste, page illisible", vue(doGet(RACINE + type + "?page=abc", admin)));
            m.set(n + "liste, taille illisible", vue(doGet(RACINE + type + "?size=abc", admin)));
            m.set(n + "secretaire, corps illisible", vue(doPost(RACINE + type, "{'values':", as(CENTRE_A, "SECRETAIRE"))));
            m.set(n + "secretaire, sans corps", vue(doPost(RACINE + type, null, as(CENTRE_A, "SECRETAIRE"))));
        }
        goldenNode("refus-de-forme", m);
    }

    @Test
    void les_lectures_communes_des_autres_listes_de_choix_et_les_doublons_de_l_organisation() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        for (String type : new String[]{"caisses", "agences", "centres-payeurs", "centres-payeurs-details"}) {
            m.set(type + " : doublon, centreId illisible",
                    vue(doGet("/api/v1/organisation/referentiels/" + type + "?centreId=pas-un-uuid", admin)));
        }
        m.set("categories-transport : centerId absent", vue(doGet(COMMUNS + "/categories-transport", admin)));
        m.set("categories-transport : centerId illisible", vue(doGet(COMMUNS + "/categories-transport?centerId=pas-un-uuid", admin)));
        m.set("categories-transport : anonyme sans centerId", vue(doGet(COMMUNS + "/categories-transport", null)));
        goldenNode("lectures-communes", m);
    }
}
