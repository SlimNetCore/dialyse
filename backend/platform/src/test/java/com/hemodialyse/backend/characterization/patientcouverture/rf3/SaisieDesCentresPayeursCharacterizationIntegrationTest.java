package com.hemodialyse.backend.characterization.patientcouverture.rf3;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * RF3a, scénarios 1 à 3 (niveau centre payeur) : création, modification et suppression d'un centre payeur par l'administration
 * générique ({@code /api/v1/admin/referentials/centres-payeurs}) : l'agence est obligatoire et se désigne par son code ou son
 * identifiant, l'adresse est facultative, clé naturelle {@code code} par centre, suppression refusée tant qu'un patient en relève.
 */
class SaisieDesCentresPayeursCharacterizationIntegrationTest extends PayeursCharacterizationSupport {

    @Override
    protected String dossier() {
        return "saisie-centres-payeurs";
    }

    @Test
    void la_creation_resout_l_agence_garde_l_adresse_facultative_et_refuse_les_doublons() throws Exception {
        UUID[] a = chaine(CENTRE_A, "A");
        UUID[] b = chaine(CENTRE_B, "B");
        ObjectNode m = mapper.createObjectNode();
        m.set("agence par son code", vue(creerCentre("CP-1", "Centre payeur 1", "12 rue Didouche Mourad, Alger", "A-AG", admin)));
        m.set("sans adresse", vue(creerCentre("CP-2", "Centre payeur 2", null, "A-AG", admin)));
        m.set("adresse vide", vue(creerCentre("CP-3", "Centre payeur 3", "", "A-AG", admin)));
        m.set("adresse de 255 caracteres", vue(creerCentre("CP-4", "Centre payeur 4", "R".repeat(255), "A-AG", admin)));
        m.set("adresse de 256 caracteres", vue(creerCentre("CP-5", "Centre payeur 5", "R".repeat(256), "A-AG", admin)));
        m.set("agence par son identifiant", vue(creerCentre("CP-6", "Centre payeur 6", null, a[1].toString(), admin)));
        m.set("agence par son code a la casse et aux espaces pres", vue(creerCentre("CP-7", "Centre payeur 7", null, "  a-ag ", admin)));
        m.set("agence inconnue", vue(creerCentre("CP-8", "Centre payeur 8", null, "NULLE-PART", admin)));
        m.set("agence d'un autre centre par son code", vue(creerCentre("CP-9", "Centre payeur 9", null, "B-AG", admin)));
        m.set("agence d'un autre centre par son identifiant", vue(creerCentre("CP-10", "Centre payeur 10", null, b[1].toString(), admin)));
        m.set("agence absente", vue(creerCentre("CP-11", "Centre payeur 11", null, null, admin)));
        m.set("caisse donnee a la place de l'agence", vue(creerCentre("CP-12", "Centre payeur 12", null, "A-CA", admin)));
        m.set("code absent", vue(creerCentre(null, "Sans code", null, "A-AG", admin)));
        m.set("code de 51 caracteres", vue(creerCentre("P".repeat(51), "Code cinquante et un", null, "A-AG", admin)));
        m.set("nom absent", vue(creerCentre("CP-NONOM", null, null, "A-AG", admin)));
        m.set("nom de 256 caracteres", vue(creerCentre("CP-N256", "N".repeat(256), null, "A-AG", admin)));
        m.set("quatre anomalies ensemble", vue(creerCentre("", "", "R".repeat(256), "NULLE-PART", admin)));
        m.set("doublon exact du code", vue(creerCentre("CP-1", "Autre nom", null, "A-AG", admin)));
        m.set("doublon du code a la casse et aux espaces pres", vue(creerCentre("  cp-1 ", "Autre nom", null, "A-AG", admin)));
        m.set("meme code dans un autre centre", vue(creerCentre("CP-1", "Centre payeur 1", null, "B-AG", adminB)));
        m.set("corps sans values", vue(doPost(CENTRES, "{}", admin)));
        m.set("corps illisible", vue(doPost(CENTRES, "{'values':", admin)));
        goldenNode("creations", m);
        etatDesPayeurs("etat-apres-creations");
    }

    @Test
    void la_creation_est_gardee_par_le_centre_et_le_role() throws Exception {
        chaine(CENTRE_A, "A");
        chaine(CENTRE_B, "B");
        ObjectNode m = mapper.createObjectNode();
        String centreB = "{'centerId':'" + CENTRE_B + "','values':{'code':'CP-B','nom':'Centre B','agence':'B-AG'}}";
        String centreA = "{'centerId':'" + CENTRE_A + "','values':{'code':'CP-A','nom':'Centre A','agence':'A-AG'}}";
        m.set("admin du centre A vise le centre B", vue(doPost(CENTRES, centreB, admin)));
        m.set("admin du centre A vise son centre", vue(doPost(CENTRES, centreA, admin)));
        m.set("super administrateur vise le centre B", vue(doPost(CENTRES, centreB, superadmin())));
        m.set("secretaire", vue(doPost(CENTRES, centreA, as(CENTRE_A, "SECRETAIRE"))));
        m.set("anonyme", vue(doPost(CENTRES, centreA, null)));
        goldenNode("creations-gardees", m);
        etatDesPayeurs("etat-apres-creations-gardees");
    }

    @Test
    void la_modification_peut_changer_d_agence_garde_la_cle_naturelle_unique_et_le_cloisonnement() throws Exception {
        UUID[] a = chaine(CENTRE_A, "A");
        UUID ag2 = agence(CENTRE_A, "A-AG2", a[0], "A-AG2", "Agence 2");
        UUID cp2 = centrePayeur(CENTRE_A, "CP2", a[1], "CP-2", "Centre payeur 2", null);
        UUID[] b = chaine(CENTRE_B, "B");
        ObjectNode m = mapper.createObjectNode();
        m.set("renommer le code, le nom et l'adresse", vue(modifierCentre(a[2], "A-CP2", "Centre renomme", "Nouvelle adresse", "A-AG", admin)));
        m.set("changer d'agence par son code", vue(modifierCentre(a[2], "A-CP2", "Centre renomme", "Nouvelle adresse", "A-AG2", admin)));
        m.set("changer d'agence par son identifiant", vue(modifierCentre(a[2], "A-CP2", "Centre renomme", "Nouvelle adresse", a[1].toString(), admin)));
        m.set("adresse omise efface l'adresse", vue(modifierCentre(a[2], "A-CP2", "Centre renomme", null, "A-AG", admin)));
        m.set("garder son propre code a la casse pres", vue(modifierCentre(a[2], "a-cp2", "Centre renomme", null, "A-AG", admin)));
        m.set("agence inconnue", vue(modifierCentre(a[2], "A-CP2", "Centre renomme", null, "NULLE-PART", admin)));
        m.set("agence omise", vue(modifierCentre(a[2], "A-CP2", "Centre renomme", null, null, admin)));
        m.set("agence d'un autre centre", vue(modifierCentre(a[2], "A-CP2", "Centre renomme", null, "B-AG", admin)));
        m.set("prendre le code d'un autre", vue(modifierCentre(cp2, "  a-cp2 ", "Centre payeur 2", null, "A-AG", admin)));
        m.set("code vide", vue(modifierCentre(cp2, "", "Centre payeur 2", null, "A-AG", admin)));
        m.set("identifiant inconnu", vue(modifierCentre(UUID.nameUUIDFromBytes("rf3-inconnu".getBytes()), "FANTOME", "Fantome", null, "A-AG", admin)));
        m.set("centre payeur d'un autre centre", vue(modifierCentre(b[2], "PIRATE", "Pirate", null, "A-AG", admin)));
        m.set("centre B par son administrateur", vue(doPut(CENTRES + "/" + b[2],
                "{'centerId':'" + CENTRE_B + "','values':{'code':'B-CP2','nom':'Centre B2','agence':'B-AG'}}", adminB)));
        m.set("sans values", vue(doPut(CENTRES + "/" + cp2, "{}", admin)));
        goldenNode("modifications", m);
        etatDesPayeurs("etat-apres-modifications");
        org.junit.jupiter.api.Assertions.assertNotNull(ag2);
    }

    @Test
    void la_suppression_est_refusee_tant_qu_un_patient_releve_du_centre_payeur() throws Exception {
        UUID[] a = chaine(CENTRE_A, "A");
        UUID libre = centrePayeur(CENTRE_A, "LIBRE", a[1], "CP-LIB", "Centre payeur libre", null);
        UUID[] b = chaine(CENTRE_B, "B");
        patientDuCentrePayeur(CENTRE_A, "P1", a[2]);
        patientDuCentrePayeur(CENTRE_A, "P2", a[2]);
        ObjectNode m = mapper.createObjectNode();
        m.set("utilise par deux patients", vue(doDelete(CENTRES + "/" + a[2], admin)));
        m.set("libre", vue(doDelete(CENTRES + "/" + libre, admin)));
        m.set("deja supprime", vue(doDelete(CENTRES + "/" + libre, admin)));
        m.set("identifiant inconnu", vue(doDelete(CENTRES + "/" + UUID.nameUUIDFromBytes("rf3-inconnu".getBytes()), admin)));
        m.set("centre payeur d'un autre centre", vue(doDelete(CENTRES + "/" + b[2], admin)));
        m.set("centre B vise par l'admin du centre A", vue(doDelete(CENTRES + "/" + b[2] + "?centerId=" + CENTRE_B, admin)));
        jdbc.update("DELETE FROM patients WHERE center_id = ?", CENTRE_A);
        m.set("les patients liberes", vue(doDelete(CENTRES + "/" + a[2], admin)));
        m.set("centre B par son administrateur", vue(doDelete(CENTRES + "/" + b[2] + "?centerId=" + CENTRE_B, adminB)));
        goldenNode("suppressions", m);
        etatDesPayeurs("etat-apres-suppressions");
    }
}
