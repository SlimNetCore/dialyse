package com.hemodialyse.backend.characterization.patientcouverture.rf2;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * RF2, scénarios 1 à 3 : création, modification et suppression d'un forfait par l'administration générique
 * ({@code /api/v1/admin/referentials/forfaits}) : validation des champs, clé naturelle {@code code} par centre, prix facultatif
 * (nul, zéro, négatif, décimales), suppression refusée tant qu'une prise en charge (demandée ou effective), une ligne de facture ou
 * une séance (forfait imposé) l'utilise, cloisonnement par centre.
 */
class SaisieDesForfaitsCharacterizationIntegrationTest extends ForfaitsCharacterizationSupport {

    @Override
    protected String dossier() {
        return "saisie";
    }

    private final RequestPostProcessor admin = as(CENTRE_A, "ADMIN");

    @Test
    void la_creation_valide_normalise_et_refuse_les_doublons_du_centre() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        m.set("creation complete", vue(creer("HD-CONV", "Hemodialyse conventionnelle", "5600.00", admin)));
        m.set("creation sans prix", vue(creer("HD-SANS", "Forfait sans prix", null, admin)));
        m.set("prix vide devient absent", vue(creer("HD-VIDE", "Forfait prix vide", "", admin)));
        m.set("prix d'espaces devient absent", vue(creer("HD-ESP", "Forfait prix espaces", "   ", admin)));
        m.set("prix zero", vue(creer("HD-ZERO", "Forfait gratuit", "0", admin)));
        m.set("prix zero virgule zero zero", vue(creer("HD-ZERO2", "Forfait gratuit bis", "0,00", admin)));
        m.set("prix moins zero", vue(creer("HD-MZERO", "Forfait moins zero", "-0", admin)));
        m.set("prix negatif", vue(creer("HD-NEG", "Forfait negatif", "-1", admin)));
        m.set("prix negatif a trois decimales sous le centime", vue(creer("HD-NEG2", "Forfait negatif minuscule", "-0.001", admin)));
        m.set("prix entier", vue(creer("HD-ENT", "Forfait entier", "5600", admin)));
        m.set("prix a une decimale", vue(creer("HD-1D", "Forfait une decimale", "5600.5", admin)));
        m.set("prix avec virgule decimale", vue(creer("HD-VIR", "Forfait virgule", "5600,5", admin)));
        m.set("prix avec espaces de milliers", vue(creer("HD-MIL", "Forfait milliers", "5 600,50", admin)));
        m.set("prix avec espace insecable", vue(creer("HD-INS", "Forfait insecable", "5 600", admin)));
        m.set("prix avec signe plus", vue(creer("HD-PLUS", "Forfait plus", "+12", admin)));
        m.set("prix en notation scientifique", vue(creer("HD-EXP", "Forfait scientifique", "1e3", admin)));
        m.set("prix a trois decimales arrondi au-dessus", vue(creer("HD-3D-H", "Forfait trois decimales haut", "10.005", admin)));
        m.set("prix a trois decimales arrondi en dessous", vue(creer("HD-3D-B", "Forfait trois decimales bas", "10.004", admin)));
        m.set("prix minuscule arrondi a zero", vue(creer("HD-MIN", "Forfait minuscule", "0.004", admin)));
        m.set("prix lettres", vue(creer("HD-LET", "Forfait lettres", "abc", admin)));
        m.set("prix avec unite", vue(creer("HD-DZD", "Forfait unite", "5600 DA", admin)));
        m.set("prix a la borne", vue(creer("HD-MAX", "Forfait borne", "99999999.99", admin)));
        m.set("prix a la borne apres arrondi", vue(creer("HD-MAX2", "Forfait borne arrondie", "99999999.994", admin)));
        m.set("prix au-dessus de la borne", vue(creer("HD-TROP", "Forfait trop grand", "100000000", admin)));
        m.set("prix au-dessus de la borne apres arrondi", vue(creer("HD-TROP2", "Forfait trop grand arrondi", "99999999.995", admin)));
        m.set("code absent", vue(creer(null, "Sans code", "100", admin)));
        m.set("code vide", vue(creer("", "Sans code", "100", admin)));
        m.set("code d'espaces", vue(creer("   ", "Sans code", "100", admin)));
        m.set("code de 50 caracteres", vue(creer("C".repeat(50), "Code cinquante", null, admin)));
        m.set("code de 51 caracteres", vue(creer("C".repeat(51), "Code cinquante et un", null, admin)));
        m.set("libelle absent", vue(creer("HD-NOLIB", null, "100", admin)));
        m.set("libelle vide", vue(creer("HD-NOLIB2", "", "100", admin)));
        m.set("libelle de 255 caracteres", vue(creer("HD-L255", "L".repeat(255), null, admin)));
        m.set("libelle de 256 caracteres", vue(creer("HD-L256", "L".repeat(256), null, admin)));
        m.set("code et libelle entoures d'espaces", vue(creer("  HD-TRIM  ", "  Forfait espaces  ", "1", admin)));
        m.set("trois anomalies ensemble", vue(creer("", "", "-5", admin)));
        m.set("doublon exact du code", vue(creer("HD-CONV", "Autre libelle", "1", admin)));
        m.set("doublon du code a la casse et aux espaces pres", vue(creer("  hd-CONV ", "Autre libelle", "1", admin)));
        m.set("meme libelle sous un autre code", vue(creer("HD-AUTRE", "Hemodialyse conventionnelle", "5600.00", admin)));
        m.set("meme code dans un autre centre", vue(creer("HD-CONV", "Hemodialyse conventionnelle", "5600.00", adminB)));
        m.set("meme code dans un autre centre (refus du filtre de roles)", vue(creer("HD-CONV", "X", null, superadmin())));
        m.set("champ inconnu ignore", vue(doPost(ADMIN, "{'values':{'code':'HD-INC','libelle':'Inconnu','couleur':'bleu'}}", admin)));
        m.set("prix numerique JSON", vue(doPost(ADMIN, "{'values':{'code':'HD-NUM','libelle':'Numerique','prix':123.456}}", admin)));
        m.set("prix numerique entier JSON", vue(doPost(ADMIN, "{'values':{'code':'HD-NUM2','libelle':'Numerique entier','prix':7}}", admin)));
        m.set("corps sans values", vue(doPost(ADMIN, "{}", admin)));
        m.set("values nul", vue(doPost(ADMIN, "{'values':null}", admin)));
        m.set("corps illisible", vue(doPost(ADMIN, "{'values':", admin)));
        m.set("sans corps", vue(doPost(ADMIN, null, admin)));
        goldenNode("creations", m);
        etatDesForfaits("etat-apres-creations");
    }

    @Test
    void la_creation_est_gardee_par_le_centre_et_le_role() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        String forfaitB = "{'centerId':'" + CENTRE_B + "','values':{'code':'F-B','libelle':'Forfait B'}}";
        String forfaitA = "{'centerId':'" + CENTRE_A + "','values':{'code':'F-A','libelle':'Forfait A'}}";
        m.set("admin du centre A vise le centre B", vue(doPost(ADMIN, forfaitB, admin)));
        m.set("admin du centre A vise son centre", vue(doPost(ADMIN, forfaitA, admin)));
        m.set("super administrateur vise le centre B", vue(doPost(ADMIN, forfaitB, superadmin())));
        m.set("centre illisible", vue(doPost(ADMIN, "{'centerId':'pas-un-uuid','values':{'code':'X','libelle':'X'}}", admin)));
        m.set("secretaire", vue(doPost(ADMIN, forfaitA, as(CENTRE_A, "SECRETAIRE"))));
        // RF2c (KB-RF2-01) : le refus de droits passe AVANT le 400 d'un corps illisible
        m.set("secretaire, corps illisible", vue(doPost(ADMIN, "{'values':", as(CENTRE_A, "SECRETAIRE"))));
        m.set("anonyme", vue(doPost(ADMIN, forfaitA, null)));
        goldenNode("creations-gardees", m);
        etatDesForfaits("etat-apres-creations-gardees");
    }

    @Test
    void la_modification_remplace_tous_les_champs_garde_la_cle_naturelle_unique_et_le_cloisonnement() throws Exception {
        UUID f1 = forfait(CENTRE_A, "F1", "HD-CONV", "Hemodialyse conventionnelle", "5600.00");
        UUID f2 = forfait(CENTRE_A, "F2", "HD-PREM", "Hemodialyse premium", null);
        UUID fb = forfait(CENTRE_B, "FB", "HD-B", "Forfait B", "100.00");
        ObjectNode m = mapper.createObjectNode();
        m.set("renommer le code et changer le prix", vue(modifier(f1, "HD-CONV2", "Hemodialyse conventionnelle", "5700.5", admin)));
        m.set("memes valeurs", vue(modifier(f1, "HD-CONV2", "Hemodialyse conventionnelle", "5700.5", admin)));
        m.set("garder son propre code a la casse pres", vue(modifier(f1, "hd-conv2", "Hemodialyse conventionnelle", "5700.5", admin)));
        m.set("prix omis efface le prix", vue(modifier(f1, "HD-CONV2", "Hemodialyse conventionnelle", null, admin)));
        m.set("prix remis a zero", vue(modifier(f1, "HD-CONV2", "Hemodialyse conventionnelle", "0", admin)));
        m.set("prix negatif", vue(modifier(f1, "HD-CONV2", "Hemodialyse conventionnelle", "-3", admin)));
        m.set("prix a trois decimales", vue(modifier(f1, "HD-CONV2", "Hemodialyse conventionnelle", "12.345", admin)));
        m.set("donner un prix a un forfait sans prix", vue(modifier(f2, "HD-PREM", "Hemodialyse premium", "9000", admin)));
        m.set("prendre le code d'un autre", vue(modifier(f2, "  hd-CONV2 ", "Hemodialyse premium", null, admin)));
        m.set("code vide", vue(modifier(f2, "", "Hemodialyse premium", null, admin)));
        m.set("libelle vide", vue(modifier(f2, "HD-PREM", "", null, admin)));
        m.set("code omis", vue(modifier(f2, null, "Hemodialyse premium", null, admin)));
        m.set("identifiant inconnu", vue(modifier(UUID.nameUUIDFromBytes("rf2-inconnu".getBytes()), "FANTOME", "Fantome", null, admin)));
        m.set("forfait d'un autre centre", vue(modifier(fb, "PIRATE", "Pirate", null, admin)));
        m.set("centre B vise par l'admin du centre A", vue(doPut(ADMIN + "/" + fb,
                "{'centerId':'" + CENTRE_B + "','values':{'code':'PIRATE','libelle':'Pirate'}}", admin)));
        m.set("centre B par son administrateur", vue(doPut(ADMIN + "/" + fb,
                "{'centerId':'" + CENTRE_B + "','values':{'code':'HD-B2','libelle':'Forfait B2','prix':'101'}}", adminB)));
        m.set("identifiant illisible", vue(doPut(ADMIN + "/pas-un-uuid", "{'values':{'code':'X','libelle':'X'}}", admin)));
        m.set("sans values", vue(doPut(ADMIN + "/" + f2, "{}", admin)));
        goldenNode("modifications", m);
        etatDesForfaits("etat-apres-modifications");
    }

    @Test
    void la_suppression_est_refusee_tant_qu_une_pec_une_ligne_de_facture_ou_une_seance_utilise_le_forfait() throws Exception {
        UUID demande = forfait(CENTRE_A, "DEMANDE", "F-DEM", "Utilise par une PEC demandee", "100.00");
        UUID effectif = forfait(CENTRE_A, "EFFECTIF", "F-EFF", "Utilise par une PEC effective", "200.00");
        UUID lesDeux = forfait(CENTRE_A, "DEUX", "F-DEUX", "Utilise de partout", null);
        UUID facture = forfait(CENTRE_A, "FACTURE", "F-FAC", "Utilise par une ligne de facture", "300.00");
        UUID seance = forfait(CENTRE_A, "SEANCE", "F-SEA", "Impose a une seance", "400.00");
        UUID libre = forfait(CENTRE_A, "LIBRE", "F-LIB", "Libre", "500.00");
        UUID autreCentre = forfait(CENTRE_A, "AUTRECENTRE", "F-AUT", "Utilise par une PEC d'un autre centre", "600.00");
        UUID fb = forfait(CENTRE_B, "FB", "F-B", "Forfait B", null);
        pec(CENTRE_A, "PEC1", demande, null);
        pec(CENTRE_A, "PEC2", null, effectif);
        pec(CENTRE_A, "PEC3", lesDeux, lesDeux);
        pec(CENTRE_A, "PEC4", lesDeux, null);
        ligneDeFacture(CENTRE_A, "L1", lesDeux);
        ligneDeFacture(CENTRE_A, "L2", facture);
        seanceImposee(CENTRE_A, "S1", seance);
        seanceImposee(CENTRE_A, "S2", lesDeux);
        pec(CENTRE_B, "PEC5", autreCentre, null);
        ObjectNode m = mapper.createObjectNode();
        m.set("utilise par une PEC demandee", vue(doDelete(ADMIN + "/" + demande, admin)));
        m.set("utilise par une PEC effective", vue(doDelete(ADMIN + "/" + effectif, admin)));
        m.set("utilise par une ligne de facture", vue(doDelete(ADMIN + "/" + facture, admin)));
        m.set("impose a une seance", vue(doDelete(ADMIN + "/" + seance, admin)));
        m.set("utilise partout (demande, effectif, facture, seance)", vue(doDelete(ADMIN + "/" + lesDeux, admin)));
        m.set("utilise par une PEC d'un autre centre", vue(doDelete(ADMIN + "/" + autreCentre, admin)));
        m.set("libre", vue(doDelete(ADMIN + "/" + libre, admin)));
        m.set("deja supprime", vue(doDelete(ADMIN + "/" + libre, admin)));
        m.set("identifiant inconnu", vue(doDelete(ADMIN + "/" + UUID.nameUUIDFromBytes("rf2-inconnu".getBytes()), admin)));
        m.set("forfait d'un autre centre", vue(doDelete(ADMIN + "/" + fb, admin)));
        m.set("centre B vise par l'admin du centre A", vue(doDelete(ADMIN + "/" + fb + "?centerId=" + CENTRE_B, admin)));
        m.set("identifiant illisible", vue(doDelete(ADMIN + "/pas-un-uuid", admin)));
        jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ? AND forfait_demande_id = ? AND forfait_effectif_id IS NULL",
                CENTRE_A, demande);
        m.set("la PEC liberee, le forfait demande est supprimable", vue(doDelete(ADMIN + "/" + demande, admin)));
        jdbc.update("DELETE FROM facture_lignes WHERE center_id = ? AND forfait_id = ?", CENTRE_A, facture);
        m.set("la ligne de facture liberee", vue(doDelete(ADMIN + "/" + facture, admin)));
        jdbc.update("UPDATE seances SET forfait_override_id = NULL WHERE center_id = ?", CENTRE_A);
        m.set("la seance liberee", vue(doDelete(ADMIN + "/" + seance, admin)));
        m.set("utilise encore par les PEC et la facture", vue(doDelete(ADMIN + "/" + lesDeux, admin)));
        jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", CENTRE_A);
        jdbc.update("DELETE FROM facture_lignes WHERE center_id = ?", CENTRE_A);
        m.set("tout libere", vue(doDelete(ADMIN + "/" + lesDeux, admin)));
        m.set("centre B par son administrateur", vue(doDelete(ADMIN + "/" + fb + "?centerId=" + CENTRE_B, adminB)));
        goldenNode("suppressions", m);
        etatDesForfaits("etat-apres-suppressions");
    }
}
