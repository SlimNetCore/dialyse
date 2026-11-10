package com.hemodialyse.backend.characterization.patientcouverture.rf3;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

/**
 * RF3a, scénarios 1 et 4 : lecture de l'administration générique pour les trois niveaux (recherche, pagination, tri par code, la
 * recherche d'une agence ou d'un centre payeur porte aussi sur le code et le nom de son parent), descriptif des référentiels et
 * modèles d'import, droits des onze profils et de l'anonyme sur chaque route, garde de centre.
 */
class LectureEtDroitsDesPayeursCharacterizationIntegrationTest extends PayeursCharacterizationSupport {

    @Override
    protected String dossier() {
        return "lecture";
    }

    private void amorcerLesListes() {
        UUID zebra = caisse(CENTRE_A, "ZEBRA", "Z-CA", "Zebra caisse", "STANDARD");
        UUID alpha = caisse(CENTRE_A, "ALPHA", "a-ca", "alpha caisse", "VACANCIER");
        UUID beta = caisse(CENTRE_A, "BETA", "B-50%", "Beta 50% caisse", "STANDARD");
        caisse(CENTRE_A, "GAMMA", "G_MED", "Gamma_Med", "STANDARD");
        caisse(CENTRE_B, "AUTRE", "AUTRE-B", "Caisse du centre B", "STANDARD");
        UUID agZ = agence(CENTRE_A, "AGZ", zebra, "Z-AG", "Zebra agence");
        UUID agA = agence(CENTRE_A, "AGA", alpha, "a-ag", "alpha agence");
        agence(CENTRE_A, "AGB", beta, "B-AG", "Beta agence");
        centrePayeur(CENTRE_A, "CPZ", agZ, "Z-CP", "Zebra centre", "Adresse Z");
        centrePayeur(CENTRE_A, "CPA", agA, "a-cp", "alpha centre", null);
        centrePayeur(CENTRE_A, "CPA2", agA, "B-CP", "Beta centre 50%", "Adresse B");
    }

    @Test
    void les_listes_sont_triees_par_code_filtrees_et_paginees_pour_chaque_niveau() throws Exception {
        amorcerLesListes();
        ObjectNode m = mapper.createObjectNode();
        for (String[] niveau : new String[][]{{"caisses", CAISSES}, {"agences", AGENCES}, {"centres-payeurs", CENTRES}}) {
            String url = niveau[1];
            String n = niveau[0] + " : ";
            m.set(n + "liste par defaut", vue(doGet(url, admin)));
            m.set(n + "page 0 taille 2", vue(doGet(url + "?page=0&size=2", admin)));
            m.set(n + "page 1 taille 2", vue(doGet(url + "?page=1&size=2", admin)));
            m.set(n + "page hors limite", vue(doGet(url + "?page=99&size=2", admin)));
            m.set(n + "taille zero", vue(doGet(url + "?size=0", admin)));
            m.set(n + "page negative", vue(doGet(url + "?page=-1", admin)));
            m.set(n + "recherche sur le code sans tenir compte de la casse", vue(doGet(url + "?search=Z-", admin)));
            m.set(n + "recherche sur le nom", vue(doGet(url + "?search=ALPHA", admin)));
            m.set(n + "recherche entouree d'espaces", vue(doGet(url + "?search=%20%20zebra%20", admin)));
            m.set(n + "recherche vide", vue(doGet(url + "?search=", admin)));
            m.set(n + "recherche du joker pourcent", vue(doGet(url + "?search=%25", admin)));
            m.set(n + "recherche du pourcent litteral", vue(doGet(url + "?search=50%25", admin)));
            m.set(n + "recherche du souligne litteral", vue(doGet(url + "?search=_", admin)));
            m.set(n + "recherche sans resultat", vue(doGet(url + "?search=introuvable", admin)));
            m.set(n + "centre B par le super administrateur", vue(doGet(url + "?centerId=" + CENTRE_B, superadmin())));
            m.set(n + "centre B par son administrateur", vue(doGet(url, adminB)));
            m.set(n + "centre illisible", vue(doGet(url + "?centerId=pas-un-uuid", admin)));
        }
        m.set("agences : recherche sur le code de la caisse", vue(doGet(AGENCES + "?search=Z-CA", admin)));
        m.set("agences : recherche sur le nom de la caisse", vue(doGet(AGENCES + "?search=caisse", admin)));
        m.set("centres payeurs : recherche sur le code de l'agence", vue(doGet(CENTRES + "?search=a-ag", admin)));
        m.set("centres payeurs : recherche sur le nom de l'agence", vue(doGet(CENTRES + "?search=agence", admin)));
        m.set("centres payeurs : recherche sur l'adresse", vue(doGet(CENTRES + "?search=Adresse", admin)));
        m.set("caisses : recherche sur le type : non cherche", vue(doGet(CAISSES + "?search=VACANCIER", admin)));
        goldenNode("listes", m);
    }

    @Test
    void le_descriptif_des_referentiels_et_les_modeles_d_import() throws Exception {
        ObjectNode m = mapper.createObjectNode();
        Resp kinds = doGet("/api/v1/admin/referentials", admin);
        ObjectNode vueKinds = mapper.createObjectNode();
        vueKinds.put("statut", kinds.status());
        JsonNode corps = mapper.readTree(kinds.raw());
        ArrayNode slugs = mapper.createArrayNode();
        corps.forEach(k -> slugs.add(k.path("slug").asString()));
        vueKinds.set("slugs dans l'ordre d'import", slugs);
        corps.forEach(k -> {
            String slug = k.path("slug").asString();
            if ("caisses".equals(slug) || "agences".equals(slug) || "centres-payeurs".equals(slug)) vueKinds.set(slug, k);
        });
        m.set("descriptif", vueKinds);
        for (String type : new String[]{"caisses", "agences", "centres-payeurs"}) {
            String base = "/api/v1/admin/referentials/" + type;
            m.set(type + " : modele csv", modele(doGet(base + "/template?format=csv", admin)));
            m.set(type + " : modele xlsx", modele(doGet(base + "/template?format=xlsx", admin)));
        }
        m.set("modele format inconnu", modele(doGet(CAISSES + "/template?format=pdf", admin)));
        m.set("referentiel inconnu", vue(doGet("/api/v1/admin/referentials/caisse", admin)));
        goldenNode("descriptif-et-modeles", m);
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
        for (String type : new String[]{"caisses", "agences", "centres-payeurs"}) {
            for (String route : new String[]{"GET liste", "POST creation", "PUT modification", "DELETE suppression",
                    "POST import (verification)", "GET modele csv"}) {
                ObjectNode ligne = mapper.createObjectNode();
                for (var profil : profils().entrySet()) {
                    ligne.put(profil.getKey(), statutEtCode(appeler(type, route, profil.getValue())));
                }
                ligne.put("anonyme", statutEtCode(appeler(type, route, null)));
                matrice.set(type + " : " + route, ligne);
            }
        }
        goldenNode("matrice-des-droits", matrice);
    }

    private Resp appeler(String type, String route, RequestPostProcessor qui) throws Exception {
        reinitialiser();
        UUID[] c = chaine(CENTRE_A, "X");
        String base = "/api/v1/admin/referentials/" + type;
        UUID cible = switch (type) {
            case "caisses" -> c[0];
            case "agences" -> c[1];
            default -> c[2];
        };
        String nouveau = switch (type) {
            case "caisses" -> "{'code':'NEW','nom':'Nouveau'}";
            case "agences" -> "{'code':'NEW','nom':'Nouveau','caisse':'X-CA'}";
            default -> "{'code':'NEW','nom':'Nouveau','agence':'X-AG'}";
        };
        String modifie = nouveau.replace("NEW", "REN");
        String csv = "Code,Nom" + ("agences".equals(type) ? ",Caisse (code)\nIMP,Import,X-CA\n"
                : "centres-payeurs".equals(type) ? ",Agence (code)\nIMP,Import,X-AG\n" : "\nIMP,Import\n");
        return switch (route) {
            case "GET liste" -> doGet(base + "?centerId=" + CENTRE_A, qui);
            case "POST creation" -> doPost(base, "{'centerId':'" + CENTRE_A + "','values':" + nouveau + "}", qui);
            case "PUT modification" -> doPut(base + "/" + cible, "{'centerId':'" + CENTRE_A + "','values':" + modifie + "}", qui);
            case "DELETE suppression" -> doDelete(base + "/" + cible + "?centerId=" + CENTRE_A, qui);
            case "POST import (verification)" -> doPostFichier(base + "/import?centerId=" + CENTRE_A,
                    csv.getBytes(java.nio.charset.StandardCharsets.UTF_8), "f.csv", qui);
            default -> doGet(base + "/template?format=csv", qui);
        };
    }

    @Test
    void un_admin_ne_voit_et_ne_change_que_son_centre_sur_chaque_route() throws Exception {
        UUID[] b = chaine(CENTRE_B, "B");
        ObjectNode m = mapper.createObjectNode();
        for (String[] niveau : new String[][]{{"caisses", CAISSES}, {"agences", AGENCES}, {"centres-payeurs", CENTRES}}) {
            String url = niveau[1];
            UUID cible = "caisses".equals(niveau[0]) ? b[0] : "agences".equals(niveau[0]) ? b[1] : b[2];
            String csv = "Code,Nom" + ("agences".equals(niveau[0]) ? ",Caisse (code)\nIMP,Import,B-CA\n"
                    : "centres-payeurs".equals(niveau[0]) ? ",Agence (code)\nIMP,Import,B-AG\n" : "\nIMP,Import\n");
            String n = niveau[0] + " : ";
            m.put(n + "GET du centre B", statutEtCode(doGet(url + "?centerId=" + CENTRE_B, admin)));
            m.put(n + "DELETE dans le centre B", statutEtCode(doDelete(url + "/" + cible + "?centerId=" + CENTRE_B, admin)));
            m.put(n + "import dans le centre B", statutEtCode(doPostFichier(url + "/import?centerId=" + CENTRE_B + "&dryRun=false",
                    csv.getBytes(java.nio.charset.StandardCharsets.UTF_8), "f.csv", admin)));
            m.put(n + "admin sans centre, GET sans centerId", statutEtCode(doGet(url, as(null, "ADMIN"))));
            m.put(n + "admin sans centre, GET avec centerId", statutEtCode(doGet(url + "?centerId=" + CENTRE_A, as(null, "ADMIN"))));
            m.put(n + "super administrateur, GET du centre B", statutEtCode(doGet(url + "?centerId=" + CENTRE_B, superadmin())));
            m.put(n + "super administrateur sans centre, GET sans centerId", statutEtCode(doGet(url, as(null, "SUPERADMIN"))));
        }
        goldenNode("garde-de-centre", m);
        etatDesPayeurs("etat-apres-garde-de-centre");
    }
}
