package com.hemodialyse.backend.characterization.patientcouverture.rf3;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * RF3a, scénarios 1 à 3 (niveau agence) : création, modification et suppression d'une agence par l'administration générique
 * ({@code /api/v1/admin/referentials/agences}) : la caisse est obligatoire et se désigne par son code ou son identifiant dans le
 * centre, clé naturelle {@code code} par centre, suppression refusée tant qu'un centre payeur la référence, cloisonnement.
 */
class SaisieDesAgencesCharacterizationIntegrationTest extends PayeursCharacterizationSupport {

    @Override
    protected String dossier() {
        return "saisie-agences";
    }

    @Test
    void la_creation_resout_la_caisse_par_son_code_ou_son_identifiant_et_refuse_les_doublons() throws Exception {
        UUID cnas = caisse(CENTRE_A, "CNAS", "CNAS", "Caisse nationale", "STANDARD");
        caisse(CENTRE_A, "CASNOS", "CASNOS", "Caisse des non-salaries", "VACANCIER");
        UUID caisseB = caisse(CENTRE_B, "CAISSE-B", "CNAS-B", "Caisse B", "STANDARD");
        ObjectNode m = mapper.createObjectNode();
        m.set("caisse par son code", vue(creerAgence("AG-1", "Agence 1", "CNAS", admin)));
        m.set("caisse par son code a la casse et aux espaces pres", vue(creerAgence("AG-2", "Agence 2", "  casnos ", admin)));
        m.set("caisse par son identifiant", vue(creerAgence("AG-3", "Agence 3", cnas.toString(), admin)));
        m.set("caisse inconnue", vue(creerAgence("AG-4", "Agence 4", "NULLE-PART", admin)));
        m.set("caisse d'un autre centre par son code", vue(creerAgence("AG-5", "Agence 5", "CNAS-B", admin)));
        m.set("caisse d'un autre centre par son identifiant", vue(creerAgence("AG-6", "Agence 6", caisseB.toString(), admin)));
        m.set("caisse absente", vue(creerAgence("AG-7", "Agence 7", null, admin)));
        m.set("caisse vide", vue(creerAgence("AG-8", "Agence 8", "", admin)));
        m.set("code absent", vue(creerAgence(null, "Sans code", "CNAS", admin)));
        m.set("code vide", vue(creerAgence("", "Sans code", "CNAS", admin)));
        m.set("code de 50 caracteres", vue(creerAgence("A".repeat(50), "Code cinquante", "CNAS", admin)));
        m.set("code de 51 caracteres", vue(creerAgence("A".repeat(51), "Code cinquante et un", "CNAS", admin)));
        m.set("nom absent", vue(creerAgence("AG-NONOM", null, "CNAS", admin)));
        m.set("nom de 256 caracteres", vue(creerAgence("AG-N256", "N".repeat(256), "CNAS", admin)));
        m.set("trois anomalies ensemble", vue(creerAgence("", "", "NULLE-PART", admin)));
        m.set("doublon exact du code", vue(creerAgence("AG-1", "Autre nom", "CASNOS", admin)));
        m.set("doublon du code a la casse et aux espaces pres", vue(creerAgence("  ag-1 ", "Autre nom", "CNAS", admin)));
        m.set("meme code dans un autre centre", vue(creerAgence("AG-1", "Agence 1", "CNAS-B", adminB)));
        m.set("champ inconnu ignore", vue(doPost(AGENCES, "{'values':{'code':'AG-INC','nom':'Inconnu','caisse':'CNAS','couleur':'bleu'}}", admin)));
        m.set("corps sans values", vue(doPost(AGENCES, "{}", admin)));
        m.set("corps illisible", vue(doPost(AGENCES, "{'values':", admin)));
        goldenNode("creations", m);
        etatDesPayeurs("etat-apres-creations");
    }

    @Test
    void la_creation_est_gardee_par_le_centre_et_le_role() throws Exception {
        caisse(CENTRE_A, "CA", "CNAS", "Caisse", "STANDARD");
        caisse(CENTRE_B, "CB", "CNAS", "Caisse B", "STANDARD");
        ObjectNode m = mapper.createObjectNode();
        String agenceB = "{'centerId':'" + CENTRE_B + "','values':{'code':'AG-B','nom':'Agence B','caisse':'CNAS'}}";
        String agenceA = "{'centerId':'" + CENTRE_A + "','values':{'code':'AG-A','nom':'Agence A','caisse':'CNAS'}}";
        m.set("admin du centre A vise le centre B", vue(doPost(AGENCES, agenceB, admin)));
        m.set("admin du centre A vise son centre", vue(doPost(AGENCES, agenceA, admin)));
        m.set("super administrateur vise le centre B", vue(doPost(AGENCES, agenceB, superadmin())));
        m.set("secretaire", vue(doPost(AGENCES, agenceA, as(CENTRE_A, "SECRETAIRE"))));
        m.set("anonyme", vue(doPost(AGENCES, agenceA, null)));
        goldenNode("creations-gardees", m);
        etatDesPayeurs("etat-apres-creations-gardees");
    }

    @Test
    void la_modification_peut_changer_de_caisse_garde_la_cle_naturelle_unique_et_le_cloisonnement() throws Exception {
        UUID cnas = caisse(CENTRE_A, "CNAS", "CNAS", "Caisse nationale", "STANDARD");
        UUID casnos = caisse(CENTRE_A, "CASNOS", "CASNOS", "Caisse des non-salaries", "VACANCIER");
        caisse(CENTRE_B, "CB", "CNAS-B", "Caisse B", "STANDARD");
        UUID a1 = agence(CENTRE_A, "A1", cnas, "AG-1", "Agence 1");
        UUID a2 = agence(CENTRE_A, "A2", cnas, "AG-2", "Agence 2");
        UUID ab = agence(CENTRE_B, "AB", caisse(CENTRE_B, "CB2", "CB2", "Caisse B2", "STANDARD"), "AG-B", "Agence B");
        ObjectNode m = mapper.createObjectNode();
        m.set("renommer le code et le nom", vue(modifierAgence(a1, "AG-1B", "Agence 1 renommee", "CNAS", admin)));
        m.set("changer de caisse par son code", vue(modifierAgence(a1, "AG-1B", "Agence 1 renommee", "CASNOS", admin)));
        m.set("changer de caisse par son identifiant", vue(modifierAgence(a1, "AG-1B", "Agence 1 renommee", cnas.toString(), admin)));
        m.set("garder son propre code a la casse pres", vue(modifierAgence(a1, "ag-1b", "Agence 1 renommee", "CNAS", admin)));
        m.set("caisse inconnue", vue(modifierAgence(a1, "AG-1B", "Agence 1 renommee", "NULLE-PART", admin)));
        m.set("caisse omise", vue(modifierAgence(a1, "AG-1B", "Agence 1 renommee", null, admin)));
        m.set("caisse d'un autre centre", vue(modifierAgence(a1, "AG-1B", "Agence 1 renommee", "CNAS-B", admin)));
        m.set("prendre le code d'une autre", vue(modifierAgence(a2, "  ag-1b ", "Agence 2", "CNAS", admin)));
        m.set("code vide", vue(modifierAgence(a2, "", "Agence 2", "CNAS", admin)));
        m.set("identifiant inconnu", vue(modifierAgence(UUID.nameUUIDFromBytes("rf3-inconnu".getBytes()), "FANTOME", "Fantome", "CNAS", admin)));
        m.set("agence d'un autre centre", vue(modifierAgence(ab, "PIRATE", "Pirate", "CNAS", admin)));
        m.set("centre B par son administrateur", vue(doPut(AGENCES + "/" + ab,
                "{'centerId':'" + CENTRE_B + "','values':{'code':'AG-B2','nom':'Agence B2','caisse':'CB2'}}", adminB)));
        m.set("sans values", vue(doPut(AGENCES + "/" + a2, "{}", admin)));
        goldenNode("modifications", m);
        etatDesPayeurs("etat-apres-modifications");
        org.junit.jupiter.api.Assertions.assertNotNull(casnos);
    }

    @Test
    void la_suppression_est_refusee_tant_qu_un_centre_payeur_reference_l_agence() throws Exception {
        UUID caisse = caisse(CENTRE_A, "CNAS", "CNAS", "Caisse nationale", "STANDARD");
        UUID utilisee = agence(CENTRE_A, "UTILISEE", caisse, "AG-UTIL", "Agence avec centre payeur");
        UUID libre = agence(CENTRE_A, "LIBRE", caisse, "AG-LIB", "Agence libre");
        UUID ab = agence(CENTRE_B, "AB", caisse(CENTRE_B, "CB", "CB", "Caisse B", "STANDARD"), "AG-B", "Agence B");
        UUID cp = centrePayeur(CENTRE_A, "CP", utilisee, "CP-1", "Centre payeur 1", null);
        ObjectNode m = mapper.createObjectNode();
        m.set("utilisee par un centre payeur", vue(doDelete(AGENCES + "/" + utilisee, admin)));
        m.set("libre", vue(doDelete(AGENCES + "/" + libre, admin)));
        m.set("deja supprimee", vue(doDelete(AGENCES + "/" + libre, admin)));
        m.set("identifiant inconnu", vue(doDelete(AGENCES + "/" + UUID.nameUUIDFromBytes("rf3-inconnu".getBytes()), admin)));
        m.set("agence d'un autre centre", vue(doDelete(AGENCES + "/" + ab, admin)));
        m.set("centre B vise par l'admin du centre A", vue(doDelete(AGENCES + "/" + ab + "?centerId=" + CENTRE_B, admin)));
        jdbc.update("DELETE FROM centre_payeur WHERE id = ?", cp);
        m.set("le centre payeur libere", vue(doDelete(AGENCES + "/" + utilisee, admin)));
        m.set("centre B par son administrateur", vue(doDelete(AGENCES + "/" + ab + "?centerId=" + CENTRE_B, adminB)));
        goldenNode("suppressions", m);
        etatDesPayeurs("etat-apres-suppressions");
    }
}
