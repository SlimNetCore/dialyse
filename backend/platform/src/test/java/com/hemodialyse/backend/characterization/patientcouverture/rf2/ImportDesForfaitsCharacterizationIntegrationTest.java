package com.hemodialyse.backend.characterization.patientcouverture.rf2;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * RF2, scénario 4 : import CSV des forfaits, de la vérification à l'écriture : en-têtes et alias, colonnes ignorées ou manquantes,
 * prix (virgule, vide, négatif, trop grand), doublons dans le fichier, mise à jour des lignes connues, rejeu, refus du fichier
 * entier (rien n'est écrit), bornes.
 */
class ImportDesForfaitsCharacterizationIntegrationTest extends ForfaitsCharacterizationSupport {

    @Override
    protected String dossier() {
        return "import";
    }

    private final org.springframework.test.web.servlet.request.RequestPostProcessor admin = as(CENTRE_A, "ADMIN");

    @Test
    void la_verification_ne_change_rien_l_import_cree_et_met_a_jour_puis_le_rejeu_met_tout_a_jour() throws Exception {
        UUID existant = forfait(CENTRE_A, "EXISTANT", "HD-CONV", "Ancien libelle", "1.00");
        forfait(CENTRE_B, "AUTRE", "HD-PREM", "Forfait du centre B", "2.00");
        String csv = "Code,Libellé,Prix\nHD-CONV,Hemodialyse conventionnelle,\"5600,50\"\nhd-prem,Hemodialyse premium,\n  HD-NEW ,Nouveau,0\n";
        ObjectNode m = mapper.createObjectNode();
        m.set("1. verification", vue(importer(csv, true, admin)));
        etatDesForfaits("etat-apres-verification");
        m.set("2. import", vue(importer(csv, false, admin)));
        etatDesForfaits("etat-apres-import");
        m.set("3. rejeu", vue(importer(csv, false, admin)));
        etatDesForfaits("etat-apres-rejeu");
        m.set("4. verification explicite", vue(doPostFichier(ADMIN + "/import?dryRun=true",
                csv.getBytes(StandardCharsets.UTF_8), "f.csv", admin)));
        goldenNode("verification-import-rejeu", m);
        org.junit.jupiter.api.Assertions.assertNotNull(existant);
    }

    @Test
    void les_en_tetes_sont_reconnus_par_code_libelle_ou_alias_les_autres_sont_ignores() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        m.set("alias code forfait, description et tarif", vue(importer("Code forfait;Description;Tarif;Commentaire\nA1;Forfait A;5600;ignore\n", true, admin)));
        m.set("alias designation et montant, casse et accents", vue(importer("CODE,DÉSIGNATION,MONTANT\nA2,Forfait B,12,5\n", true, admin)));
        m.set("colonnes obligatoires seules", vue(importer("Code,Libelle\nA3,Forfait C\n", true, admin)));
        m.set("colonne code absente", vue(importer("Libelle,Prix\nForfait D,100\n", true, admin)));
        m.set("colonne libelle absente", vue(importer("Code,Prix\nA4,100\n", true, admin)));
        m.set("colonnes code et libelle absentes", vue(importer("Prix\n100\n", true, admin)));
        m.set("colonne code en double ignoree", vue(importer("Code,Code,Libelle\nA5,A6,Forfait E\n", true, admin)));
        m.set("separateur point-virgule", vue(importer("Code;Libelle;Prix\nA7;Forfait F;5600,5\n", true, admin)));
        m.set("champ entre guillemets avec virgule", vue(importer("Code,Libelle,Prix\nA8,\"Forfait, G\",100\n", true, admin)));
        m.set("lignes vides ignorees", vue(importer("Code,Libelle\n\nA9,Forfait H\n\n", true, admin)));
        m.set("en-tetes seuls", vue(importer("Code,Libelle,Prix\n", true, admin)));
        m.set("fichier d'un seul retour a la ligne", vue(importer("\n", true, admin)));
        m.set("fichier vide", vue(importer("", true, admin)));
        goldenNode("en-tetes-et-formats", m);
        etatDesForfaits("etat-apres-verifications");
    }

    @Test
    void les_prix_du_fichier_sont_controles_comme_a_la_saisie() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        String csv = "Code,Libelle,Prix\nP1,Prix entier,5600\nP2,Prix virgule,\"5600,5\"\nP3,Prix vide,\nP4,Prix zero,0\nP5,Prix trois decimales,10.005\n"
                + "P6,Prix milliers,\"5 600,50\"\n";
        m.set("prix valides, verification", vue(importer(csv, true, admin)));
        m.set("prix valides, import", vue(importer(csv, false, admin)));
        etatDesForfaits("etat-apres-prix-valides");
        m.set("prix negatif", vue(importer("Code,Libelle,Prix\nN1,Negatif,-1\n", true, admin)));
        m.set("prix lettres", vue(importer("Code,Libelle,Prix\nN2,Lettres,abc\n", true, admin)));
        m.set("prix trop grand", vue(importer("Code,Libelle,Prix\nN3,Trop grand,100000000\n", true, admin)));
        goldenNode("prix", m);
    }

    @Test
    void une_seule_anomalie_refuse_le_fichier_entier_sans_rien_ecrire() throws Exception {
        forfait(CENTRE_A, "EXISTANT", "HD-CONV", "Hemodialyse conventionnelle", "5600.00");
        ObjectNode m = mapper.createObjectNode();
        String csv = "Code,Libelle,Prix\nF-OK,Forfait valide,100\nHD-CONV,Forfait existant,abc\n,Sans code,100\nF-OK,Doublon,50\n"
                + "f-ok  ,Doublon casse,\n" + "F-LONG," + "L".repeat(256) + ",\n" + "G".repeat(51) + ",Code trop long,\n";
        m.set("verification", vue(importer(csv, true, admin)));
        m.set("import", vue(importer(csv, false, admin)));
        etatDesForfaits("etat-apres-refus");
        goldenNode("anomalies", m);
    }

    @Test
    void les_bornes_du_fichier() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        StringBuilder trop = new StringBuilder("Code,Libelle\n");
        for (int i = 0; i < 5001; i++) trop.append("F").append(i).append(",Forfait ").append(i).append('\n');
        m.set("5001 lignes", vue(importer(trop.toString(), true, admin)));
        StringBuilder limite = new StringBuilder("Code,Libelle\n");
        for (int i = 0; i < 5000; i++) limite.append("F").append(i).append(",Forfait ").append(i).append('\n');
        m.set("5000 lignes en verification", vue(importer(limite.toString(), true, admin)));
        StringBuilder invalides = new StringBuilder("Code,Libelle,Prix\n");
        for (int i = 0; i < 520; i++) invalides.append("F").append(i).append(",Forfait ").append(i).append(",x\n");
        Resp refus = importer(invalides.toString(), true, admin);
        ObjectNode resume = mapper.createObjectNode();
        resume.put("statut", refus.status());
        var corps = mapper.readTree(refus.raw());
        resume.put("nombre d'anomalies rendues", corps.path("errors").size());
        resume.set("derniere anomalie", corps.path("errors").get(corps.path("errors").size() - 1));
        resume.set("500e anomalie", corps.path("errors").get(499));
        corps.properties().forEach(e -> {
            if (!"errors".equals(e.getKey())) resume.set("champ " + e.getKey(), e.getValue());
        });
        m.set("520 lignes invalides", resume);
        goldenNode("bornes", m);
        etatDesForfaits("etat-apres-bornes");
    }
}
