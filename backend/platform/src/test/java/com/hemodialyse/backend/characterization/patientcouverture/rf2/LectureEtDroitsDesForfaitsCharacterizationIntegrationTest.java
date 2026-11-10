package com.hemodialyse.backend.characterization.patientcouverture.rf2;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * RF2, scénarios 1 et 4 : lecture de l'administration générique (recherche, pagination, tri par code, descriptif du référentiel,
 * modèle d'import), droits des onze profils et de l'anonyme sur chaque route, garde de centre.
 */
class LectureEtDroitsDesForfaitsCharacterizationIntegrationTest extends ForfaitsCharacterizationSupport {

    @Override
    protected String dossier() {
        return "lecture";
    }

    private void amorcerLaListe() {
        forfait(CENTRE_A, "ZEBRA", "Z-HD", "Zebra forfait", "5600.00");
        forfait(CENTRE_A, "ALPHA", "a-hd", "alpha forfait", null);
        forfait(CENTRE_A, "BETA", "B-50%", "Beta 50% forfait", "0.00");
        forfait(CENTRE_A, "GAMMA", "G_MED", "Gamma_Med", "12.50");
        forfait(CENTRE_A, "DELTA", "D-HD", "Delta", "99999999.99");
        forfait(CENTRE_B, "AUTRE", "AUTRE-B", "Forfait du centre B", "1.00");
    }

    @Test
    void la_liste_est_triee_par_code_filtree_et_paginee() throws Exception {
        amorcerLaListe();
        var admin = as(CENTRE_A, "ADMIN");
        ObjectNode m = mapper.createObjectNode();
        m.set("liste par defaut", vue(doGet(ADMIN, admin)));
        m.set("page 0 taille 2", vue(doGet(ADMIN + "?page=0&size=2", admin)));
        m.set("page 1 taille 2", vue(doGet(ADMIN + "?page=1&size=2", admin)));
        m.set("page 2 taille 2", vue(doGet(ADMIN + "?page=2&size=2", admin)));
        m.set("page hors limite", vue(doGet(ADMIN + "?page=99&size=2", admin)));
        m.set("taille zero", vue(doGet(ADMIN + "?size=0", admin)));
        m.set("taille negative", vue(doGet(ADMIN + "?size=-5", admin)));
        m.set("taille 1000", vue(doGet(ADMIN + "?size=1000", admin)));
        m.set("page negative", vue(doGet(ADMIN + "?page=-1", admin)));
        m.set("page illisible", vue(doGet(ADMIN + "?page=abc", admin)));
        m.set("recherche sur le code sans tenir compte de la casse", vue(doGet(ADMIN + "?search=Z-HD", admin)));
        m.set("recherche sur le libelle", vue(doGet(ADMIN + "?search=ALPHA", admin)));
        m.set("recherche sur le prix : non cherche", vue(doGet(ADMIN + "?search=12.50", admin)));
        m.set("recherche entouree d'espaces", vue(doGet(ADMIN + "?search=%20%20zebra%20", admin)));
        m.set("recherche vide", vue(doGet(ADMIN + "?search=", admin)));
        m.set("recherche du joker pourcent", vue(doGet(ADMIN + "?search=%25", admin)));
        m.set("recherche du pourcent litteral", vue(doGet(ADMIN + "?search=50%25", admin)));
        m.set("recherche du souligne litteral", vue(doGet(ADMIN + "?search=_", admin)));
        m.set("recherche sans resultat", vue(doGet(ADMIN + "?search=introuvable", admin)));
        m.set("centre B par le super administrateur", vue(doGet(ADMIN + "?centerId=" + CENTRE_B, superadmin())));
        m.set("centre B par son administrateur", vue(doGet(ADMIN, adminB)));
        m.set("centre illisible", vue(doGet(ADMIN + "?centerId=pas-un-uuid", admin)));
        goldenNode("liste", m);
    }

    @Test
    void le_descriptif_du_referentiel_et_le_modele_d_import() throws Exception {
        var admin = as(CENTRE_A, "ADMIN");
        ObjectNode m = mapper.createObjectNode();
        Resp kinds = doGet("/api/v1/admin/referentials", admin);
        ObjectNode vueKinds = mapper.createObjectNode();
        vueKinds.put("statut", kinds.status());
        JsonNode corps = mapper.readTree(kinds.raw());
        ArrayNode slugs = mapper.createArrayNode();
        corps.forEach(k -> slugs.add(k.path("slug").asString()));
        vueKinds.set("slugs dans l'ordre d'import", slugs);
        corps.forEach(k -> {
            if ("forfaits".equals(k.path("slug").asString())) vueKinds.set("forfaits", k);
        });
        m.set("descriptif", vueKinds);
        m.set("modele csv", modele(doGet(ADMIN + "/template?format=csv", admin)));
        m.set("modele csv en majuscules", modele(doGet(ADMIN + "/template?format=CSV", admin)));
        m.set("modele xlsx", modele(doGet(ADMIN + "/template?format=xlsx", admin)));
        m.set("modele xlsx par defaut", modele(doGet(ADMIN + "/template", admin)));
        m.set("modele format inconnu", modele(doGet(ADMIN + "/template?format=pdf", admin)));
        m.set("referentiel inconnu", vue(doGet("/api/v1/admin/referentials/forfait", admin)));
        goldenNode("descriptif-et-modele", m);
    }

    /** Modèle d'import : le CSV est rendu en texte (BOM nommé), le classeur seulement par son statut et son type (binaire). */
    private JsonNode modele(Resp r) {
        String texte = r.contentType() != null && r.contentType().startsWith("text/")
                ? r.raw().replace("﻿", "<BOM>").replace("\r", "").replace("\n", "\\n") : "<classeur>";
        return mapper.getNodeFactory().stringNode(r.status() + " " + r.contentType() + " | " + texte);
    }

    @Test
    void les_droits_des_onze_profils_et_de_l_anonyme_sur_chaque_route() throws Exception {
        ObjectNode matrice = mapper.createObjectNode();
        String csv = "Code,Libelle\nF-IMPORT,Forfait import\n";
        for (String route : new String[]{"GET liste", "POST creation", "PUT modification", "DELETE suppression", "POST import (verification)",
                "GET modele csv", "GET descriptif"}) {
            ObjectNode ligne = mapper.createObjectNode();
            for (var profil : profils().entrySet()) {
                ligne.put(profil.getKey(), statutEtCode(appeler(route, csv, profil.getValue())));
            }
            ligne.put("anonyme", statutEtCode(appeler(route, csv, null)));
            matrice.set(route, ligne);
        }
        goldenNode("matrice-des-droits", matrice);
    }

    private Resp appeler(String route, String csv, org.springframework.test.web.servlet.request.RequestPostProcessor qui) throws Exception {
        reinitialiser();
        UUID f = forfait(CENTRE_A, "F", "F-EXISTANT", "Forfait existant", "100.00");
        return switch (route) {
            case "GET liste" -> doGet(ADMIN + "?centerId=" + CENTRE_A, qui);
            case "POST creation" -> doPost(ADMIN, "{'centerId':'" + CENTRE_A + "','values':{'code':'F-NEW','libelle':'Forfait nouveau'}}", qui);
            case "PUT modification" -> doPut(ADMIN + "/" + f, "{'centerId':'" + CENTRE_A + "','values':{'code':'F-REN','libelle':'Forfait renomme'}}", qui);
            case "DELETE suppression" -> doDelete(ADMIN + "/" + f + "?centerId=" + CENTRE_A, qui);
            case "POST import (verification)" -> doPostFichier(ADMIN + "/import?centerId=" + CENTRE_A,
                    csv.getBytes(java.nio.charset.StandardCharsets.UTF_8), "f.csv", qui);
            case "GET modele csv" -> doGet(ADMIN + "/template?format=csv", qui);
            default -> doGet("/api/v1/admin/referentials", qui);
        };
    }

    @Test
    void un_admin_ne_voit_et_ne_change_que_son_centre_sur_chaque_route() throws Exception {
        var admin = as(CENTRE_A, "ADMIN");
        UUID fb = forfait(CENTRE_B, "FB", "F-B", "Forfait B", null);
        ObjectNode m = mapper.createObjectNode();
        String csv = "Code,Libelle\nF-IMPORT,Forfait import\n";
        m.put("GET du centre B", statutEtCode(doGet(ADMIN + "?centerId=" + CENTRE_B, admin)));
        m.put("DELETE dans le centre B", statutEtCode(doDelete(ADMIN + "/" + fb + "?centerId=" + CENTRE_B, admin)));
        m.put("import dans le centre B", statutEtCode(doPostFichier(ADMIN + "/import?centerId=" + CENTRE_B + "&dryRun=false",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8), "f.csv", admin)));
        m.put("admin sans centre, GET sans centerId", statutEtCode(doGet(ADMIN, as(null, "ADMIN"))));
        m.put("admin sans centre, GET avec centerId", statutEtCode(doGet(ADMIN + "?centerId=" + CENTRE_A, as(null, "ADMIN"))));
        m.put("super administrateur, GET du centre B", statutEtCode(doGet(ADMIN + "?centerId=" + CENTRE_B, superadmin())));
        m.put("super administrateur sans centre, GET sans centerId", statutEtCode(doGet(ADMIN, as(null, "SUPERADMIN"))));
        goldenNode("garde-de-centre", m);
        etatDesForfaits("etat-apres-garde-de-centre");
    }
}
