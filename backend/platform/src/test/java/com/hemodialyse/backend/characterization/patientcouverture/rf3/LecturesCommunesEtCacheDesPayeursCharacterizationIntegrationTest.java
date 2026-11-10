package com.hemodialyse.backend.characterization.patientcouverture.rf3;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * RF3a, scénarios 5 et 9 : lectures communes {@code /api/v1/referentials/{caisses,agences,centres-payeurs,centres-payeurs-details}}
 * (forme {@code RefItem}, tri par nom, garde de centre) et caches {@code ref.caisses}, {@code ref.agences},
 * {@code ref.centresPayeurs}, {@code ref.centresPayeursDetails} : invalidés APRÈS une écriture de l'API (centre du seul écrivain),
 * jamais par un refus, une vérification ou une écriture directe en base.
 */
class LecturesCommunesEtCacheDesPayeursCharacterizationIntegrationTest extends PayeursCharacterizationSupport {

    @Override
    protected String dossier() {
        return "lectures-communes";
    }

    private JsonNode lire(String chemin, RequestPostProcessor qui) throws Exception {
        return vue(doGet(chemin, qui));
    }

    private ObjectNode toutLire(ObjectNode m, String etape, UUID centre, RequestPostProcessor qui) throws Exception {
        for (String type : new String[]{"caisses", "agences", "centres-payeurs", "centres-payeurs-details"}) {
            m.set(etape + " " + type, lire(COMMUNS + "/" + type + "?centerId=" + centre, qui));
        }
        return m;
    }

    @Test
    void les_lectures_sont_rendues_en_ref_item_triees_par_nom() throws Exception {
        UUID zebra = caisse(CENTRE_A, "ZEBRA", "Z-CA", "Zebra caisse", "STANDARD");
        UUID alpha = caisse(CENTRE_A, "ALPHA", "a-ca", "alpha caisse", "VACANCIER");
        UUID agZ = agence(CENTRE_A, "AGZ", zebra, "Z-AG", "Zebra agence");
        UUID agA = agence(CENTRE_A, "AGA", alpha, "A-AG", "alpha agence");
        centrePayeur(CENTRE_A, "CPZ", agZ, "Z-CP", "Zebra centre", "Adresse Z");
        centrePayeur(CENTRE_A, "CPA", agA, "A-CP", "alpha centre", null);
        centrePayeur(CENTRE_A, "CPA2", agA, "B-CP", "Beta centre", "Adresse B");
        chaine(CENTRE_B, "B");
        ObjectNode m = mapper.createObjectNode();
        toutLire(m, "centre A", CENTRE_A, admin);
        toutLire(m, "centre B par un admin du centre A :", CENTRE_B, admin);
        toutLire(m, "centre B par le super administrateur :", CENTRE_B, superadmin());
        toutLire(m, "centre B par son administrateur :", CENTRE_B, adminB);
        toutLire(m, "secretaire :", CENTRE_A, as(CENTRE_A, "SECRETAIRE"));
        toutLire(m, "infirmier :", CENTRE_A, as(CENTRE_A, "INFIRMIER"));
        toutLire(m, "medecin :", CENTRE_A, as(CENTRE_A, "MEDECIN"));
        toutLire(m, "anonyme :", CENTRE_A, null);
        toutLire(m, "sans centre dans la session :", CENTRE_A, as(null, "ADMIN"));
        for (String type : new String[]{"caisses", "agences", "centres-payeurs", "centres-payeurs-details"}) {
            m.set(type + " sans centerId", lire(COMMUNS + "/" + type, admin));
            m.set(type + " centerId illisible", lire(COMMUNS + "/" + type + "?centerId=pas-un-uuid", admin));
        }
        goldenNode("lectures", m);
    }

    @Test
    void les_listes_sont_gardees_en_cache_par_centre_une_ecriture_de_l_api_les_renouvelle_une_ecriture_directe_reste_ignoree() throws Exception {
        UUID[] a = chaine(CENTRE_A, "A");
        chaine(CENTRE_B, "B");
        ObjectNode m = mapper.createObjectNode();
        toutLire(m, "1. premiere lecture A :", CENTRE_A, admin);
        toutLire(m, "1. premiere lecture B :", CENTRE_B, adminB);
        chaine(CENTRE_A, "DIRECT");
        chaine(CENTRE_B, "DIRECTB");
        toutLire(m, "2. apres ecritures directes, A perimee :", CENTRE_A, admin);
        m.set("3. creation de caisse refusee (doublon)", vue(creerCaisse("A-CA", "Doublon", null, admin)));
        toutLire(m, "3. A toujours perimee :", CENTRE_A, admin);
        m.set("4. creation de caisse", vue(creerCaisse("C-API", "Cree par l API", null, admin)));
        toutLire(m, "4. A renouvelee, B perimee :", CENTRE_A, admin);
        toutLire(m, "4. B :", CENTRE_B, adminB);
        chaine(CENTRE_A, "DIRECT2");
        m.set("5. creation d'agence", vue(creerAgence("AG-API", "Agence API", "C-API", admin)));
        toutLire(m, "5. A renouvelee :", CENTRE_A, admin);
        chaine(CENTRE_A, "DIRECT3");
        m.set("6. creation de centre payeur", vue(creerCentre("CP-API", "Centre API", null, "AG-API", admin)));
        toutLire(m, "6. A renouvelee :", CENTRE_A, admin);
        chaine(CENTRE_A, "DIRECT4");
        m.set("7. modification de la caisse", vue(modifierCaisse(a[0], "A-CA", "Caisse renommee", "VACANCIER", admin)));
        toutLire(m, "7. A renouvelee :", CENTRE_A, admin);
        chaine(CENTRE_A, "DIRECT5");
        m.set("8. modification de l'agence", vue(modifierAgence(a[1], "A-AG", "Agence renommee", "A-CA", admin)));
        toutLire(m, "8. A renouvelee :", CENTRE_A, admin);
        chaine(CENTRE_A, "DIRECT6");
        UUID cp = centrePayeur(CENTRE_A, "A-LIBRE", a[1], "CP-LIB", "Centre libre", null);
        patientDuCentrePayeur(CENTRE_A, "P", cp);
        m.set("9. suppression refusee", vue(doDelete(CENTRES + "/" + cp, admin)));
        toutLire(m, "9. A reste perimee :", CENTRE_A, admin);
        jdbc.update("DELETE FROM patients WHERE center_id = ?", CENTRE_A);
        m.set("10. suppression reussie", vue(doDelete(CENTRES + "/" + cp, admin)));
        toutLire(m, "10. A renouvelee :", CENTRE_A, admin);
        toutLire(m, "10. B toujours perimee :", CENTRE_B, adminB);
        m.set("11. creation dans le centre B par son administrateur", vue(doPost(CAISSES,
                "{'centerId':'" + CENTRE_B + "','values':{'code':'C-CREE-B','nom':'Cree en B'}}", adminB)));
        toutLire(m, "11. B renouvelee :", CENTRE_B, adminB);
        goldenNode("cache-des-payeurs", m);
    }

    @Test
    void un_import_renouvelle_les_listes_en_cache_et_une_verification_non() throws Exception {
        chaine(CENTRE_A, "A");
        ObjectNode m = mapper.createObjectNode();
        toutLire(m, "1. premiere lecture :", CENTRE_A, admin);
        m.set("2. verification de l'import", vue(importer("caisses", "Code,Nom\nC-2,Seconde\n", true, admin)));
        toutLire(m, "2. listes inchangees :", CENTRE_A, admin);
        chaine(CENTRE_A, "DIRECT");
        m.set("3. import refuse", vue(importer("caisses", "Code,Nom,Type\nC-3,Troisieme,PREMIUM\n", false, admin)));
        toutLire(m, "3. listes toujours perimees :", CENTRE_A, admin);
        m.set("4. import reussi", vue(importer("caisses", "Code,Nom\nC-2,Seconde\n", false, admin)));
        toutLire(m, "4. listes renouvelees :", CENTRE_A, admin);
        goldenNode("cache-et-import", m);
    }
}
