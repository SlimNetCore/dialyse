package com.hemodialyse.backend.characterization.patientcouverture.rf1;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * RF1, scénarios 1 à 4 : création, modification et suppression d'un transporteur par l'administration générique
 * ({@code /api/v1/admin/referentials/transporteurs}) : validation des champs, clé naturelle {@code nom} par centre, formats de
 * téléphone, suppression refusée tant qu'un patient l'utilise (aller OU retour), cloisonnement par centre.
 */
class SaisieDesTransporteursCharacterizationIntegrationTest extends TransporteursCharacterizationSupport {

    @Override
    protected String dossier() {
        return "saisie";
    }

    private final RequestPostProcessor admin = as(CENTRE_A, "ADMIN");

    @Test
    void la_creation_valide_normalise_et_refuse_les_doublons_du_centre() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        m.set("creation complete", vue(creer("Ambulances Rouiba", "0795 00 61 36", admin)));
        m.set("creation sans telephone", vue(creer("Taxi Medical", null, admin)));
        m.set("nom et telephone entoures d'espaces et d'espaces insecables", vue(creer("   Transport Sud  ", " 021 12 34 56 ", admin)));
        m.set("telephone avec signes admis", vue(creer("Transport Signes", "+213 (0)21-12.34/56", admin)));
        m.set("telephone vide devient absent", vue(creer("Transport Vide", "", admin)));
        m.set("telephone d'espaces devient absent", vue(creer("Transport Espaces", "   ", admin)));
        m.set("telephone avec lettres", vue(creer("Transport Lettres", "abcdef", admin)));
        m.set("telephone de cinq caracteres", vue(creer("Transport Court", "12345", admin)));
        m.set("telephone de six caracteres", vue(creer("Transport Six", "123456", admin)));
        m.set("telephone avec un caractere interdit", vue(creer("Transport Interdit", "0795 00 61 3x", admin)));
        m.set("telephone de 50 caracteres", vue(creer("Transport Cinquante", "1".repeat(50), admin)));
        m.set("telephone de 51 caracteres", vue(creer("Transport Cinquante et un", "1".repeat(51), admin)));
        m.set("nom absent", vue(creer(null, "0795006136", admin)));
        m.set("nom vide", vue(creer("", "0795006136", admin)));
        m.set("nom d'espaces", vue(creer("   ", "0795006136", admin)));
        m.set("nom de 255 caracteres", vue(creer("N".repeat(255), null, admin)));
        m.set("nom de 256 caracteres", vue(creer("N".repeat(256), null, admin)));
        m.set("nom et telephone invalides ensemble", vue(creer("", "x", admin)));
        m.set("doublon exact", vue(creer("Ambulances Rouiba", "0555 55 55 55", admin)));
        m.set("doublon a la casse et aux espaces pres", vue(creer("  AMBULANCES   rouiba ", null, admin)));
        m.set("meme nom dans un autre centre par le super administrateur", vue(creer("Ambulances Rouiba", null, adminB)));
        m.set("meme nom dans un autre centre par le super administrateur (refus du filtre de roles)", vue(creer("Ambulances Rouiba", null, superadmin())));
        m.set("meme telephone sous un autre nom", vue(creer("Autre Nom", "0795 00 61 36", admin)));
        m.set("champ inconnu ignore", vue(doPost(ADMIN, "{'values':{'nom':'Transport Inconnu','couleur':'bleu'}}", admin)));
        m.set("nom numerique", vue(doPost(ADMIN, "{'values':{'nom':123}}", admin)));
        m.set("corps sans values", vue(doPost(ADMIN, "{}", admin)));
        m.set("values nul", vue(doPost(ADMIN, "{'values':null}", admin)));
        m.set("corps illisible", vue(doPost(ADMIN, "{'values':", admin)));
        m.set("sans corps", vue(doPost(ADMIN, null, admin)));
        goldenNode("creations", m);
        etatDesTransporteurs("etat-apres-creations");
    }

    @Test
    void la_creation_est_gardee_par_le_centre_et_le_role() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        String nomB = "{'centerId':'" + CENTRE_B + "','values':{'nom':'Transport B'}}";
        String nomA = "{'centerId':'" + CENTRE_A + "','values':{'nom':'Transport A'}}";
        m.set("admin du centre A vise le centre B", vue(doPost(ADMIN, nomB, admin)));
        m.set("admin du centre A vise son centre", vue(doPost(ADMIN, nomA, admin)));
        m.set("super administrateur vise le centre B", vue(doPost(ADMIN, nomB, superadmin())));
        m.set("centre illisible", vue(doPost(ADMIN, "{'centerId':'pas-un-uuid','values':{'nom':'X'}}", admin)));
        m.set("secretaire", vue(doPost(ADMIN, nomA, as(CENTRE_A, "SECRETAIRE"))));
        m.set("anonyme", vue(doPost(ADMIN, nomA, null)));
        goldenNode("creations-gardees", m);
        etatDesTransporteurs("etat-apres-creations-gardees");
    }

    @Test
    void la_modification_garde_la_cle_naturelle_unique_et_le_cloisonnement() throws Exception {
        UUID t1 = transporteur(CENTRE_A, "T1", "Ambulances Rouiba", "0795006136");
        UUID t2 = transporteur(CENTRE_A, "T2", "Taxi Medical", null);
        UUID tb = transporteur(CENTRE_B, "TB", "Transport B", null);
        ObjectNode m = mapper.createObjectNode();
        m.set("renommer", vue(modifier(t1, "Ambulances Rouiba Est", "0795 00 61 37", admin)));
        m.set("memes valeurs", vue(modifier(t1, "Ambulances Rouiba Est", "0795 00 61 37", admin)));
        m.set("garder son propre nom a la casse pres", vue(modifier(t1, "ambulances rouiba est", "0795 00 61 37", admin)));
        m.set("effacer le telephone", vue(modifier(t1, "Ambulances Rouiba Est", null, admin)));
        m.set("prendre le nom d'un autre", vue(modifier(t2, "  ambulances rouiba EST ", null, admin)));
        m.set("nom vide", vue(modifier(t2, "", null, admin)));
        m.set("telephone invalide", vue(modifier(t2, "Taxi Medical", "abc", admin)));
        m.set("identifiant inconnu", vue(modifier(UUID.nameUUIDFromBytes("rf1-inconnu".getBytes()), "Fantome", null, admin)));
        m.set("transporteur d'un autre centre", vue(modifier(tb, "Pirate", null, admin)));
        m.set("centre B vise par l'admin du centre A", vue(doPut(ADMIN + "/" + tb,
                "{'centerId':'" + CENTRE_B + "','values':{'nom':'Pirate'}}", admin)));
        m.set("centre B par le super administrateur", vue(doPut(ADMIN + "/" + tb,
                "{'centerId':'" + CENTRE_B + "','values':{'nom':'Transport B2'}}", adminB)));
        m.set("identifiant illisible", vue(doPut(ADMIN + "/pas-un-uuid", "{'values':{'nom':'X'}}", admin)));
        m.set("sans values", vue(doPut(ADMIN + "/" + t2, "{}", admin)));
        goldenNode("modifications", m);
        etatDesTransporteurs("etat-apres-modifications");
    }

    @Test
    void la_suppression_est_refusee_tant_qu_un_patient_utilise_le_transporteur_a_l_aller_ou_au_retour() throws Exception {
        UUID t1 = transporteur(CENTRE_A, "T1", "Utilise a l'aller", null);
        UUID t2 = transporteur(CENTRE_A, "T2", "Utilise au retour", null);
        UUID t3 = transporteur(CENTRE_A, "T3", "Utilise partout", null);
        UUID t4 = transporteur(CENTRE_A, "T4", "Libre", "0795006136");
        UUID t5 = transporteur(CENTRE_A, "T5", "Utilise par un autre centre", null);
        UUID tb = transporteur(CENTRE_B, "TB", "Transport B", null);
        patient(CENTRE_A, "P1", t1, null);
        patient(CENTRE_A, "P2", null, t2);
        patient(CENTRE_A, "P3", t3, t3);
        patient(CENTRE_A, "P4", t3, null);
        patient(CENTRE_B, "P5", t5, null);
        ObjectNode m = mapper.createObjectNode();
        m.set("utilise a l'aller", vue(doDelete(ADMIN + "/" + t1, admin)));
        m.set("utilise au retour", vue(doDelete(ADMIN + "/" + t2, admin)));
        m.set("utilise trois fois", vue(doDelete(ADMIN + "/" + t3, admin)));
        m.set("utilise par un patient d'un autre centre", vue(doDelete(ADMIN + "/" + t5, admin)));
        m.set("libre", vue(doDelete(ADMIN + "/" + t4, admin)));
        m.set("deja supprime", vue(doDelete(ADMIN + "/" + t4, admin)));
        m.set("identifiant inconnu", vue(doDelete(ADMIN + "/" + UUID.nameUUIDFromBytes("rf1-inconnu".getBytes()), admin)));
        m.set("transporteur d'un autre centre", vue(doDelete(ADMIN + "/" + tb, admin)));
        m.set("centre B vise par l'admin du centre A", vue(doDelete(ADMIN + "/" + tb + "?centerId=" + CENTRE_B, admin)));
        m.set("identifiant illisible", vue(doDelete(ADMIN + "/pas-un-uuid", admin)));
        jdbc.update("UPDATE patients SET transporteur_aller_id = NULL WHERE center_id = ?", CENTRE_A);
        m.set("l'aller libere, le retour de P2 et de P3 utilisent encore", vue(doDelete(ADMIN + "/" + t3, admin)));
        jdbc.update("UPDATE patients SET transporteur_retour_id = NULL WHERE center_id = ?", CENTRE_A);
        m.set("tout libere", vue(doDelete(ADMIN + "/" + t3, admin)));
        m.set("centre B par le super administrateur", vue(doDelete(ADMIN + "/" + tb + "?centerId=" + CENTRE_B, adminB)));
        goldenNode("suppressions", m);
        etatDesTransporteurs("etat-apres-suppressions");
    }
}
