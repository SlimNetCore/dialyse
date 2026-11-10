package com.hemodialyse.backend.characterization.patientcouverture.rf3;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;

/**
 * RF3a, scénario 4 : import CSV des caisses, des agences puis des centres payeurs, dans cet ordre (le parent doit exister avant
 * l'enfant) : vérification sans écriture, import, rejeu, rejet d'un parent absent (y compris celui d'un autre centre), en-têtes et
 * alias, type de caisse, doublons dans le fichier, refus du fichier entier, bornes.
 */
class ImportDesPayeursCharacterizationIntegrationTest extends PayeursCharacterizationSupport {

    @Override
    protected String dossier() {
        return "import";
    }

    private static final String CAISSES_CSV = "Code,Nom,Type de caisse\nCNAS,Caisse nationale,STANDARD\nCASNOS,Caisse des non-salaries,vacancier\nCVAC,Caisse sans type,\n";
    private static final String AGENCES_CSV = "Code,Nom,Caisse (code)\nAG-1,Agence 1,CNAS\nAG-2,Agence 2,casnos\nAG-3,Agence 3,CNAS\n";
    private static final String CENTRES_CSV = "Code,Nom,Adresse,Agence (code)\nCP-1,Centre payeur 1,\"12 rue Didouche Mourad, Alger\",AG-1\nCP-2,Centre payeur 2,,AG-2\n";

    @Test
    void les_trois_niveaux_s_importent_dans_l_ordre_la_verification_ne_change_rien_et_le_rejeu_met_tout_a_jour() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        m.set("1. agences avant les caisses (verification)", vue(importer("agences", AGENCES_CSV, true, admin)));
        m.set("1. centres payeurs avant les agences (verification)", vue(importer("centres-payeurs", CENTRES_CSV, true, admin)));
        etatDesPayeurs("etat-apres-refus-d-ordre");
        m.set("2. caisses, verification", vue(importer("caisses", CAISSES_CSV, true, admin)));
        etatDesPayeurs("etat-apres-verification");
        m.set("3. caisses, import", vue(importer("caisses", CAISSES_CSV, false, admin)));
        m.set("3. agences, verification", vue(importer("agences", AGENCES_CSV, true, admin)));
        m.set("4. agences, import", vue(importer("agences", AGENCES_CSV, false, admin)));
        m.set("5. centres payeurs, import", vue(importer("centres-payeurs", CENTRES_CSV, false, admin)));
        etatDesPayeurs("etat-apres-import");
        m.set("6. rejeu des caisses", vue(importer("caisses", CAISSES_CSV, false, admin)));
        m.set("6. rejeu des agences", vue(importer("agences", AGENCES_CSV, false, admin)));
        m.set("6. rejeu des centres payeurs", vue(importer("centres-payeurs", CENTRES_CSV, false, admin)));
        etatDesPayeurs("etat-apres-rejeu");
        m.set("7. verification explicite", vue(doPostFichier("/api/v1/admin/referentials/caisses/import?dryRun=true",
                CAISSES_CSV.getBytes(StandardCharsets.UTF_8), "c.csv", admin)));
        goldenNode("ordre-verification-import-rejeu", m);
    }

    @Test
    void un_parent_d_un_autre_centre_est_absent_et_une_mise_a_jour_peut_changer_de_parent() throws Exception {
        chaine(CENTRE_B, "B");
        chaine(CENTRE_A, "A");
        ObjectNode m = mapper.createObjectNode();
        m.set("agence dont la caisse est d'un autre centre", vue(importer("agences", "Code,Nom,Caisse (code)\nAG-X,Agence X,B-CA\n", true, admin)));
        m.set("centre payeur dont l'agence est d'un autre centre", vue(importer("centres-payeurs",
                "Code,Nom,Agence (code)\nCP-X,Centre X,B-AG\n", true, admin)));
        m.set("centre payeur dont l'agence est donnee par son code a la casse pres", vue(importer("centres-payeurs",
                "Code,Nom,Agence (code)\nCP-Y,Centre Y,a-ag\n", true, admin)));
        m.set("mise a jour d'une agence existante vers une autre caisse", vue(importer("agences",
                "Code,Nom,Caisse (code)\nA-AG,Agence A renommee,A-CA\n", false, admin)));
        m.set("mise a jour d'un centre payeur existant, adresse effacee", vue(importer("centres-payeurs",
                "Code,Nom,Adresse,Agence (code)\nA-CP,Centre renomme,,A-AG\n", false, admin)));
        goldenNode("parents-et-mises-a-jour", m);
        etatDesPayeurs("etat-apres-parents-et-mises-a-jour");
    }

    @Test
    void les_en_tetes_sont_reconnus_par_code_libelle_ou_alias_les_autres_sont_ignores() throws Exception {
        caisse(CENTRE_A, "CNAS", "CNAS", "Caisse nationale", "STANDARD");
        ObjectNode m = mapper.createObjectNode();
        m.set("caisses : alias code caisse et type", vue(importer("caisses", "Code caisse;Libelle;Type;Commentaire\nC1;Caisse 1;VACANCIER;ignore\n", true, admin)));
        m.set("caisses : colonnes obligatoires seules", vue(importer("caisses", "Code,Nom\nC2,Caisse 2\n", true, admin)));
        m.set("caisses : colonne code absente", vue(importer("caisses", "Nom\nCaisse 3\n", true, admin)));
        m.set("caisses : type inconnu", vue(importer("caisses", "Code,Nom,Type\nC4,Caisse 4,PREMIUM\n", true, admin)));
        m.set("agences : alias code agence, caisse code", vue(importer("agences", "Code agence;Libelle;Code caisse\nA1;Agence 1;CNAS\n", true, admin)));
        m.set("agences : colonne caisse absente", vue(importer("agences", "Code,Nom\nA2,Agence 2\n", true, admin)));
        m.set("agences : caisse vide", vue(importer("agences", "Code,Nom,Caisse (code)\nA3,Agence 3,\n", true, admin)));
        m.set("centres : alias code centre payeur, agence code", vue(importer("centres-payeurs", "Code centre payeur;Libelle;Code agence\nP1;Centre 1;AG-NULLE\n", true, admin)));
        m.set("centres : colonne agence absente", vue(importer("centres-payeurs", "Code,Nom,Adresse\nP2,Centre 2,Rue\n", true, admin)));
        m.set("separateur point-virgule et champ entre guillemets", vue(importer("caisses", "Code;Nom\nC5;\"Caisse, cinq\"\n", true, admin)));
        m.set("lignes vides ignorees", vue(importer("caisses", "Code,Nom\n\nC6,Caisse 6\n\n", true, admin)));
        m.set("en-tetes seuls", vue(importer("caisses", "Code,Nom,Type\n", true, admin)));
        m.set("fichier vide", vue(importer("caisses", "", true, admin)));
        goldenNode("en-tetes-et-formats", m);
        etatDesPayeurs("etat-apres-verifications");
    }

    @Test
    void une_seule_anomalie_refuse_le_fichier_entier_sans_rien_ecrire() throws Exception {
        caisse(CENTRE_A, "CNAS", "CNAS", "Caisse nationale", "STANDARD");
        ObjectNode m = mapper.createObjectNode();
        String csv = "Code,Nom,Type\nC-OK,Caisse valide,STANDARD\nCNAS,Caisse existante,PREMIUM\n,Sans code,STANDARD\nC-OK,Doublon,STANDARD\n"
                + "c-ok  ,Doublon casse,\nC-LONG," + "N".repeat(256) + ",\n" + "G".repeat(51) + ",Code trop long,\n";
        m.set("caisses, verification", vue(importer("caisses", csv, true, admin)));
        m.set("caisses, import", vue(importer("caisses", csv, false, admin)));
        String agences = "Code,Nom,Caisse (code)\nA-OK,Agence valide,CNAS\nA-KO,Agence orpheline,NULLE-PART\nA-OK,Doublon,CNAS\n";
        m.set("agences, import", vue(importer("agences", agences, false, admin)));
        goldenNode("anomalies", m);
        etatDesPayeurs("etat-apres-refus");
    }

    @Test
    void les_bornes_du_fichier() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        StringBuilder trop = new StringBuilder("Code,Nom\n");
        for (int i = 0; i < 5001; i++) trop.append("C").append(i).append(",Caisse ").append(i).append('\n');
        m.set("5001 lignes", vue(importer("caisses", trop.toString(), true, admin)));
        StringBuilder limite = new StringBuilder("Code,Nom\n");
        for (int i = 0; i < 5000; i++) limite.append("C").append(i).append(",Caisse ").append(i).append('\n');
        m.set("5000 lignes en verification", vue(importer("caisses", limite.toString(), true, admin)));
        StringBuilder invalides = new StringBuilder("Code,Nom,Type\n");
        for (int i = 0; i < 520; i++) invalides.append("C").append(i).append(",Caisse ").append(i).append(",x\n");
        Resp refus = importer("caisses", invalides.toString(), true, admin);
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
        etatDesPayeurs("etat-apres-bornes");
    }
}
