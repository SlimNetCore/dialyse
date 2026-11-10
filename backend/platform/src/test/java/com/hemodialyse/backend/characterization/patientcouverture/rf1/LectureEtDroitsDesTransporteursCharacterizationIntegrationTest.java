package com.hemodialyse.backend.characterization.patientcouverture.rf1;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * RF1, scénarios 1, 5 et 6 : lecture de l'administration générique (recherche, pagination, tri, descriptif du référentiel, modèle
 * d'import), droits des onze profils et de l'anonyme sur chaque route, garde de centre.
 */
class LectureEtDroitsDesTransporteursCharacterizationIntegrationTest extends TransporteursCharacterizationSupport {

    @Override
    protected String dossier() {
        return "lecture";
    }

    private void amorcerLaListe() {
        transporteur(CENTRE_A, "ZEBRA", "Zebra Transport", "0795006136");
        transporteur(CENTRE_A, "ALPHA", "alpha ambulances", null);
        transporteur(CENTRE_A, "BETA", "Beta 50% Taxi", "021 11 22 33");
        transporteur(CENTRE_A, "GAMMA", "Gamma_Med", "0555 55 55 55");
        transporteur(CENTRE_A, "DELTA", "Delta", "+213 6 61 61 61 61");
        transporteur(CENTRE_B, "AUTRE", "Transport du centre B", null);
    }

    @Test
    void la_liste_est_triee_par_nom_filtree_et_paginee() throws Exception {
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
        m.set("recherche sur le nom sans tenir compte de la casse", vue(doGet(ADMIN + "?search=ALPHA", admin)));
        m.set("recherche sur le telephone", vue(doGet(ADMIN + "?search=6136", admin)));
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
            if ("transporteurs".equals(k.path("slug").asString())) vueKinds.set("transporteurs", k);
        });
        m.set("descriptif", vueKinds);
        m.set("modele csv", modele(doGet(ADMIN + "/template?format=csv", admin)));
        m.set("modele csv en majuscules", modele(doGet(ADMIN + "/template?format=CSV", admin)));
        m.set("modele xlsx", modele(doGet(ADMIN + "/template?format=xlsx", admin)));
        m.set("modele xlsx par defaut", modele(doGet(ADMIN + "/template", admin)));
        m.set("modele format inconnu", modele(doGet(ADMIN + "/template?format=pdf", admin)));
        m.set("referentiel inconnu", vue(doGet("/api/v1/admin/referentials/transporteur", admin)));
        goldenNode("descriptif-et-modele", m);
    }

    /** Modèle d'import : le CSV est rendu en texte (BOM nommé), le classeur seulement par son statut et son type (binaire). */
    private tools.jackson.databind.JsonNode modele(Resp r) {
        String texte = r.contentType() != null && r.contentType().startsWith("text/")
                ? r.raw().replace("﻿", "<BOM>").replace("\r", "").replace("\n", "\\n") : "<classeur>";
        return mapper.getNodeFactory().stringNode(r.status() + " " + r.contentType() + " | " + texte);
    }

    @Test
    void les_droits_des_onze_profils_et_de_l_anonyme_sur_chaque_route() throws Exception {
        ObjectNode matrice = mapper.createObjectNode();
        String csv = "Nom,Telephone\nTransport Import,0795006136\n";
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
        UUID t = transporteur(CENTRE_A, "T", "Transport Existant", null);
        return switch (route) {
            case "GET liste" -> doGet(ADMIN + "?centerId=" + CENTRE_A, qui);
            case "POST creation" -> doPost(ADMIN, "{'centerId':'" + CENTRE_A + "','values':{'nom':'Transport Nouveau'}}", qui);
            case "PUT modification" -> doPut(ADMIN + "/" + t, "{'centerId':'" + CENTRE_A + "','values':{'nom':'Transport Renomme'}}", qui);
            case "DELETE suppression" -> doDelete(ADMIN + "/" + t + "?centerId=" + CENTRE_A, qui);
            case "POST import (verification)" -> doPostFichier(ADMIN + "/import?centerId=" + CENTRE_A,
                    csv.getBytes(java.nio.charset.StandardCharsets.UTF_8), "t.csv", qui);
            case "GET modele csv" -> doGet(ADMIN + "/template?format=csv", qui);
            default -> doGet("/api/v1/admin/referentials", qui);
        };
    }

    @Test
    void un_admin_ne_voit_et_ne_change_que_son_centre_sur_chaque_route() throws Exception {
        var admin = as(CENTRE_A, "ADMIN");
        UUID tb = transporteur(CENTRE_B, "TB", "Transport B", null);
        ObjectNode m = mapper.createObjectNode();
        String csv = "Nom\nTransport Import\n";
        m.put("GET du centre B", statutEtCode(doGet(ADMIN + "?centerId=" + CENTRE_B, admin)));
        m.put("DELETE dans le centre B", statutEtCode(doDelete(ADMIN + "/" + tb + "?centerId=" + CENTRE_B, admin)));
        m.put("import dans le centre B", statutEtCode(doPostFichier(ADMIN + "/import?centerId=" + CENTRE_B + "&dryRun=false",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8), "t.csv", admin)));
        m.put("admin sans centre, GET sans centerId", statutEtCode(doGet(ADMIN, as(null, "ADMIN"))));
        m.put("admin sans centre, GET avec centerId", statutEtCode(doGet(ADMIN + "?centerId=" + CENTRE_A, as(null, "ADMIN"))));
        m.put("super administrateur, GET du centre B", statutEtCode(doGet(ADMIN + "?centerId=" + CENTRE_B, superadmin())));
        m.put("super administrateur sans centre, GET sans centerId", statutEtCode(doGet(ADMIN, as(null, "SUPERADMIN"))));
        goldenNode("garde-de-centre", m);
        etatDesTransporteurs("etat-apres-garde-de-centre");
    }
}
