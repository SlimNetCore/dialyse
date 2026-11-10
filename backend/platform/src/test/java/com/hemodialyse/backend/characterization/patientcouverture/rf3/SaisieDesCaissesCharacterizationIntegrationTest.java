package com.hemodialyse.backend.characterization.patientcouverture.rf3;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * RF3a, scénarios 1 à 3 (niveau caisse) : création, modification et suppression d'une caisse d'assurance par l'administration
 * générique ({@code /api/v1/admin/referentials/caisses}) : validation des champs, type STANDARD ou VACANCIER (STANDARD par défaut),
 * clé naturelle {@code code} par centre, suppression refusée tant qu'une agence la référence, cloisonnement par centre.
 */
class SaisieDesCaissesCharacterizationIntegrationTest extends PayeursCharacterizationSupport {

    @Override
    protected String dossier() {
        return "saisie-caisses";
    }

    @Test
    void la_creation_normalise_le_type_et_refuse_les_doublons_du_centre() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        m.set("creation complete", vue(creerCaisse("CNAS", "Caisse nationale", "STANDARD", admin)));
        m.set("type omis devient STANDARD", vue(creerCaisse("C-DEF", "Type omis", null, admin)));
        m.set("type vide devient STANDARD", vue(creerCaisse("C-VIDE", "Type vide", "", admin)));
        m.set("type vacancier", vue(creerCaisse("C-VAC", "Caisse vacancier", "VACANCIER", admin)));
        m.set("type en minuscules", vue(creerCaisse("C-MIN", "Type minuscules", "vacancier", admin)));
        m.set("type avec espaces et tiret", vue(creerCaisse("C-ESP", "Type espaces", " Vacancier ", admin)));
        m.set("type inconnu", vue(creerCaisse("C-INC", "Type inconnu", "PREMIUM", admin)));
        m.set("code absent", vue(creerCaisse(null, "Sans code", "STANDARD", admin)));
        m.set("code vide", vue(creerCaisse("", "Sans code", "STANDARD", admin)));
        m.set("code d'espaces", vue(creerCaisse("   ", "Sans code", "STANDARD", admin)));
        m.set("code de 50 caracteres", vue(creerCaisse("C".repeat(50), "Code cinquante", null, admin)));
        m.set("code de 51 caracteres", vue(creerCaisse("C".repeat(51), "Code cinquante et un", null, admin)));
        m.set("nom absent", vue(creerCaisse("C-NONOM", null, null, admin)));
        m.set("nom de 255 caracteres", vue(creerCaisse("C-N255", "N".repeat(255), null, admin)));
        m.set("nom de 256 caracteres", vue(creerCaisse("C-N256", "N".repeat(256), null, admin)));
        m.set("code et nom entoures d'espaces", vue(creerCaisse("  C-TRIM  ", "  Nom espaces  ", null, admin)));
        m.set("trois anomalies ensemble", vue(creerCaisse("", "", "PREMIUM", admin)));
        m.set("doublon exact du code", vue(creerCaisse("CNAS", "Autre nom", "STANDARD", admin)));
        m.set("doublon du code a la casse et aux espaces pres", vue(creerCaisse("  cnas ", "Autre nom", "STANDARD", admin)));
        m.set("meme nom sous un autre code", vue(creerCaisse("CNAS2", "Caisse nationale", "STANDARD", admin)));
        m.set("meme code dans un autre centre", vue(creerCaisse("CNAS", "Caisse nationale", "STANDARD", adminB)));
        m.set("champ inconnu ignore", vue(doPost(CAISSES, "{'values':{'code':'C-INC2','nom':'Inconnu','couleur':'bleu'}}", admin)));
        m.set("corps sans values", vue(doPost(CAISSES, "{}", admin)));
        m.set("values nul", vue(doPost(CAISSES, "{'values':null}", admin)));
        m.set("corps illisible", vue(doPost(CAISSES, "{'values':", admin)));
        m.set("sans corps", vue(doPost(CAISSES, null, admin)));
        goldenNode("creations", m);
        etatDesPayeurs("etat-apres-creations");
    }

    @Test
    void la_creation_est_gardee_par_le_centre_et_le_role() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        String caisseB = "{'centerId':'" + CENTRE_B + "','values':{'code':'C-B','nom':'Caisse B'}}";
        String caisseA = "{'centerId':'" + CENTRE_A + "','values':{'code':'C-A','nom':'Caisse A'}}";
        m.set("admin du centre A vise le centre B", vue(doPost(CAISSES, caisseB, admin)));
        m.set("admin du centre A vise son centre", vue(doPost(CAISSES, caisseA, admin)));
        m.set("super administrateur vise le centre B", vue(doPost(CAISSES, caisseB, superadmin())));
        m.set("centre illisible", vue(doPost(CAISSES, "{'centerId':'pas-un-uuid','values':{'code':'X','nom':'X'}}", admin)));
        m.set("secretaire", vue(doPost(CAISSES, caisseA, as(CENTRE_A, "SECRETAIRE"))));
        // RF3d (KB-RF3-01) : le refus de droits passe AVANT le 400 d'un corps illisible
        m.set("secretaire, corps illisible", vue(doPost(CAISSES, "{'values':", as(CENTRE_A, "SECRETAIRE"))));
        m.set("anonyme", vue(doPost(CAISSES, caisseA, null)));
        goldenNode("creations-gardees", m);
        etatDesPayeurs("etat-apres-creations-gardees");
    }

    @Test
    void la_modification_remplace_tous_les_champs_garde_la_cle_naturelle_unique_et_le_cloisonnement() throws Exception {
        UUID c1 = caisse(CENTRE_A, "C1", "CNAS", "Caisse nationale", "STANDARD");
        UUID c2 = caisse(CENTRE_A, "C2", "CASNOS", "Caisse des non-salaries", "VACANCIER");
        UUID cb = caisse(CENTRE_B, "CB", "CNAS-B", "Caisse B", "STANDARD");
        ObjectNode m = mapper.createObjectNode();
        m.set("renommer le code et le nom", vue(modifierCaisse(c1, "CNAS2", "Caisse nationale renommee", "STANDARD", admin)));
        m.set("memes valeurs", vue(modifierCaisse(c1, "CNAS2", "Caisse nationale renommee", "STANDARD", admin)));
        m.set("garder son propre code a la casse pres", vue(modifierCaisse(c1, "cnas2", "Caisse nationale renommee", "STANDARD", admin)));
        m.set("passer en vacancier", vue(modifierCaisse(c1, "CNAS2", "Caisse nationale renommee", "VACANCIER", admin)));
        m.set("type omis revient a STANDARD", vue(modifierCaisse(c2, "CASNOS", "Caisse des non-salaries", null, admin)));
        m.set("type inconnu", vue(modifierCaisse(c2, "CASNOS", "Caisse des non-salaries", "PREMIUM", admin)));
        m.set("prendre le code d'une autre", vue(modifierCaisse(c2, "  cnas2 ", "Caisse des non-salaries", null, admin)));
        m.set("code vide", vue(modifierCaisse(c2, "", "Caisse des non-salaries", null, admin)));
        m.set("nom vide", vue(modifierCaisse(c2, "CASNOS", "", null, admin)));
        m.set("code omis", vue(modifierCaisse(c2, null, "Caisse des non-salaries", null, admin)));
        m.set("identifiant inconnu", vue(modifierCaisse(UUID.nameUUIDFromBytes("rf3-inconnu".getBytes()), "FANTOME", "Fantome", null, admin)));
        m.set("caisse d'un autre centre", vue(modifierCaisse(cb, "PIRATE", "Pirate", null, admin)));
        m.set("centre B vise par l'admin du centre A", vue(doPut(CAISSES + "/" + cb,
                "{'centerId':'" + CENTRE_B + "','values':{'code':'PIRATE','nom':'Pirate'}}", admin)));
        m.set("centre B par son administrateur", vue(doPut(CAISSES + "/" + cb,
                "{'centerId':'" + CENTRE_B + "','values':{'code':'CNAS-B2','nom':'Caisse B2'}}", adminB)));
        m.set("identifiant illisible", vue(doPut(CAISSES + "/pas-un-uuid", "{'values':{'code':'X','nom':'X'}}", admin)));
        m.set("sans values", vue(doPut(CAISSES + "/" + c2, "{}", admin)));
        goldenNode("modifications", m);
        etatDesPayeurs("etat-apres-modifications");
    }

    @Test
    void la_suppression_est_refusee_tant_qu_une_agence_reference_la_caisse() throws Exception {
        UUID utilisee = caisse(CENTRE_A, "UTILISEE", "C-UTIL", "Caisse avec agence", "STANDARD");
        UUID libre = caisse(CENTRE_A, "LIBRE", "C-LIB", "Caisse libre", "STANDARD");
        UUID cb = caisse(CENTRE_B, "CB", "C-B", "Caisse B", "STANDARD");
        UUID agence = agence(CENTRE_A, "AG", utilisee, "AG-1", "Agence 1");
        agence(CENTRE_A, "AG2", utilisee, "AG-2", "Agence 2");
        ObjectNode m = mapper.createObjectNode();
        m.set("utilisee par deux agences", vue(doDelete(CAISSES + "/" + utilisee, admin)));
        m.set("libre", vue(doDelete(CAISSES + "/" + libre, admin)));
        m.set("deja supprimee", vue(doDelete(CAISSES + "/" + libre, admin)));
        m.set("identifiant inconnu", vue(doDelete(CAISSES + "/" + UUID.nameUUIDFromBytes("rf3-inconnu".getBytes()), admin)));
        m.set("caisse d'un autre centre", vue(doDelete(CAISSES + "/" + cb, admin)));
        m.set("centre B vise par l'admin du centre A", vue(doDelete(CAISSES + "/" + cb + "?centerId=" + CENTRE_B, admin)));
        m.set("identifiant illisible", vue(doDelete(CAISSES + "/pas-un-uuid", admin)));
        jdbc.update("DELETE FROM agence WHERE id = ?", agence);
        m.set("une agence liberee, l'autre retient encore", vue(doDelete(CAISSES + "/" + utilisee, admin)));
        jdbc.update("DELETE FROM agence WHERE center_id = ?", CENTRE_A);
        m.set("toutes les agences liberees", vue(doDelete(CAISSES + "/" + utilisee, admin)));
        m.set("centre B par son administrateur", vue(doDelete(CAISSES + "/" + cb + "?centerId=" + CENTRE_B, adminB)));
        goldenNode("suppressions", m);
        etatDesPayeurs("etat-apres-suppressions");
    }
}
