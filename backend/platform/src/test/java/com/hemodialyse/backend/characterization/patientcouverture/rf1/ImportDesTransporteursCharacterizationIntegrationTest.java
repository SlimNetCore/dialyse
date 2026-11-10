package com.hemodialyse.backend.characterization.patientcouverture.rf1;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * RF1, scénario 5 : import CSV des transporteurs, de la vérification à l'écriture : en-têtes et alias, colonnes ignorées ou
 * manquantes, doublons dans le fichier, mise à jour des lignes connues, rejeu, refus du fichier entier (rien n'est écrit), bornes.
 */
class ImportDesTransporteursCharacterizationIntegrationTest extends TransporteursCharacterizationSupport {

    @Override
    protected String dossier() {
        return "import";
    }

    private final org.springframework.test.web.servlet.request.RequestPostProcessor admin = as(CENTRE_A, "ADMIN");

    @Test
    void la_verification_ne_change_rien_l_import_cree_et_met_a_jour_puis_le_rejeu_met_tout_a_jour() throws Exception {
        UUID existant = transporteur(CENTRE_A, "EXISTANT", "Ambulances Rouiba", "0111 11 11 11");
        transporteur(CENTRE_B, "AUTRE", "Transport Nouveau", null);
        String csv = "Nom,Téléphone\nAmbulances Rouiba,0795 00 61 36\nTransport Nouveau,\n  taxi medical ,021 12 34 56\n";
        ObjectNode m = mapper.createObjectNode();
        m.set("1. verification", vue(importer(csv, true, admin)));
        etatDesTransporteurs("etat-apres-verification");
        m.set("2. import", vue(importer(csv, false, admin)));
        etatDesTransporteurs("etat-apres-import");
        m.set("3. rejeu", vue(importer(csv, false, admin)));
        etatDesTransporteurs("etat-apres-rejeu");
        m.set("4. verification explicite", vue(doPostFichier(ADMIN + "/import?dryRun=true",
                csv.getBytes(StandardCharsets.UTF_8), "t.csv", admin)));
        goldenNode("verification-import-rejeu", m);
        org.junit.jupiter.api.Assertions.assertNotNull(existant);
    }

    @Test
    void les_en_tetes_sont_reconnus_par_nom_libelle_ou_alias_les_autres_sont_ignores() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        m.set("alias raison sociale et mobile", vue(importer("Raison sociale;Mobile;Commentaire\nTransport A;0795006136;ignore\n", true, admin)));
        m.set("alias transporteur et tel, casse et accents", vue(importer("TRANSPORTEUR,TÉL\nTransport B,0795006136\n", true, admin)));
        m.set("colonne nom seule", vue(importer("Nom\nTransport C\n", true, admin)));
        m.set("colonne nom absente", vue(importer("Telephone\n0795006136\n", true, admin)));
        m.set("colonne nom en double ignoree", vue(importer("Nom,Nom\nTransport D,Transport E\n", true, admin)));
        m.set("separateur point-virgule", vue(importer("Nom;Telephone\nTransport F;0795006136\n", true, admin)));
        m.set("champ entre guillemets avec virgule", vue(importer("Nom,Telephone\n\"Transport, G\",0795006136\n", true, admin)));
        m.set("lignes vides ignorees", vue(importer("Nom\n\nTransport H\n\n", true, admin)));
        m.set("en-tetes seuls", vue(importer("Nom,Telephone\n", true, admin)));
        m.set("fichier d'un seul retour a la ligne", vue(importer("\n", true, admin)));
        m.set("fichier vide", vue(importer("", true, admin)));
        goldenNode("en-tetes-et-formats", m);
        etatDesTransporteurs("etat-apres-verifications");
    }

    @Test
    void une_seule_anomalie_refuse_le_fichier_entier_sans_rien_ecrire() throws Exception {
        transporteur(CENTRE_A, "EXISTANT", "Ambulances Rouiba", null);
        ObjectNode m = mapper.createObjectNode();
        String csv = "Nom,Telephone\nTransport Valide,0795006136\nAmbulances Rouiba,abc\n,0795006136\nTransport Valide,0555 55 55 55\n"
                + "transport  VALIDE,\n" + "N".repeat(256) + ",\n";
        m.set("verification", vue(importer(csv, true, admin)));
        m.set("import", vue(importer(csv, false, admin)));
        etatDesTransporteurs("etat-apres-refus");
        goldenNode("anomalies", m);
    }

    @Test
    void les_bornes_du_fichier() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        StringBuilder trop = new StringBuilder("Nom\n");
        for (int i = 0; i < 5001; i++) trop.append("Transport ").append(i).append('\n');
        m.set("5001 lignes", vue(importer(trop.toString(), true, admin)));
        StringBuilder limite = new StringBuilder("Nom\n");
        for (int i = 0; i < 5000; i++) limite.append("Transport ").append(i).append('\n');
        m.set("5000 lignes en verification", vue(importer(limite.toString(), true, admin)));
        StringBuilder invalides = new StringBuilder("Nom,Telephone\n");
        for (int i = 0; i < 520; i++) invalides.append("Transport ").append(i).append(",x\n");
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
        etatDesTransporteurs("etat-apres-bornes");
    }
}
