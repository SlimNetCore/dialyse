package com.hemodialyse.backend.characterization.organisation;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.node.ObjectNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation O1 (sociétés et centres) : routes {@code /api/v1/societes/**} observées sur le legacy
 * ({@code SocieteRestController}, SUPERADMIN) : droits (11 profils + anonyme), lectures, créations, validations, invariant
 * « au moins un centre actif », transfert, logo. Chaque cellule d'une matrice repart d'une base réinitialisée.
 */
class SocietesCharacterizationIntegrationTest extends OrganisationCharacterizationSupport {

    private static final String S = "/api/v1/societes";

    @Override
    protected String theme() {
        return "o1-societes";
    }

    private interface Appel {
        Resp executer(RequestPostProcessor who) throws Exception;
    }

    private UUID societe1;
    private UUID societe2;
    private UUID centre1;
    private UUID centre2;

    private void amorcer() {
        reinitialiser();
        societe1 = societe("SOCIETE_1", "S1", true);
        societe2 = societe("SOCIETE_2", "S2", true);
        centre1 = centre("CENTRE_1", societe1, "C1", true);
        centre2 = centre("CENTRE_2", societe1, "C2", true);
        centre("CENTRE_3", societe2, "C3", true);
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ImageIO.write(image, "png", sortie);
        return sortie.toByteArray();
    }

    private static final String SOCIETE_JSON = "{'code':'ORGT-N1','raisonSociale':'Nouvelle société','nif':'123','ville':'Alger',"
            + "'telephone':'0555000000','email':'a@b.dz','piedDePage':'Pied'}";
    private static final String CENTRE_JSON = "{'code':'ORGT-NC1','nom':'Premier centre','ville':'Alger'}";

    @Test
    void les_routes_selon_les_onze_profils_et_l_anonyme() throws Exception {
        Map<String, Appel> routes = new LinkedHashMap<>();
        routes.put("GET /societes", w -> doGet(S + "?q=" + CODE_PREFIXE, w));
        routes.put("GET /societes/{id}", w -> doGet(S + "/" + societe1, w));
        routes.put("POST /societes", w -> doPost(S, "{'societe':" + SOCIETE_JSON + ",'premierCentre':" + CENTRE_JSON + "}", w));
        routes.put("PUT /societes/{id}", w -> doPut(S + "/" + societe1, "{'code':'ORGT-S1','raisonSociale':'Modifiée'}", w));
        routes.put("POST /societes/{id}/activer", w -> doPost(S + "/" + societe1 + "/activer", null, w));
        routes.put("POST /societes/{id}/desactiver", w -> doPost(S + "/" + societe1 + "/desactiver", null, w));
        routes.put("PUT /societes/{id}/logo", w -> doPutLogo(S + "/" + societe1 + "/logo", png(), "image/png", w));
        routes.put("GET /societes/{id}/logo", w -> doGet(S + "/" + societe1 + "/logo", w));
        routes.put("DELETE /societes/{id}/logo", w -> doDelete(S + "/" + societe1 + "/logo", w));
        routes.put("POST /societes/{id}/centres", w -> doPost(S + "/" + societe1 + "/centres", CENTRE_JSON, w));
        routes.put("PUT /societes/{id}/centres/{c}", w -> doPut(S + "/" + societe1 + "/centres/" + centre1, "{'code':'ORGT-C1','nom':'Renommé'}", w));
        routes.put("POST /societes/{id}/centres/{c}/activer", w -> doPost(S + "/" + societe1 + "/centres/" + centre1 + "/activer", null, w));
        routes.put("POST /societes/{id}/centres/{c}/desactiver", w -> doPost(S + "/" + societe1 + "/centres/" + centre1 + "/desactiver", null, w));
        routes.put("POST /societes/{id}/centres/{c}/transfert", w -> doPost(S + "/" + societe1 + "/centres/" + centre1 + "/transfert",
                "{'societeCibleId':'" + societe2 + "'}", w));

        ObjectNode matrice = mapper.createObjectNode();
        for (var route : routes.entrySet()) {
            ObjectNode ligne = mapper.createObjectNode();
            for (var profil : profils().entrySet()) {
                amorcer();
                ligne.put(profil.getKey(), statutEtCode(route.getValue().executer(profil.getValue())));
            }
            amorcer();
            ligne.put("anonyme", statutEtCode(route.getValue().executer(null)));
            matrice.set(route.getKey(), ligne);
        }
        goldenNode("matrice-des-droits", matrice);
    }

    @Test
    void lectures_liste_pagination_detail_et_inconnu() throws Exception {
        amorcer();
        golden("liste-par-defaut", doGet(S + "?q=" + CODE_PREFIXE, superadmin()));
        golden("liste-recherche-s1", doGet(S + "?q=ORGT-S1", superadmin()));
        golden("liste-page-1-taille-1", doGet(S + "?q=" + CODE_PREFIXE + "&page=1&size=1", superadmin()));
        golden("liste-taille-hors-bornes", doGet(S + "?q=" + CODE_PREFIXE + "&size=1000", superadmin()));
        golden("liste-page-negative", doGet(S + "?q=" + CODE_PREFIXE + "&page=-1", superadmin()));
        golden("detail", doGet(S + "/" + societe1, superadmin()));
        golden("detail-inconnu", doGet(S + "/" + UUID.fromString("99998200-0000-0000-0000-0000000000e9"), superadmin()));
    }

    /** ÉCART À DÉCIDER (KB-ORG-02) : 500 legacy contre 400 nouveau code pour un identifiant de société illisible. */
    @org.junit.jupiter.api.Disabled("KB-ORG-02 : 500 legacy contre 400 nouveau code, décision de l'utilisateur attendue")
    @Test
    void identifiant_de_societe_illisible_ecart_a_decider() throws Exception {
        amorcer();
        golden("detail-identifiant-illisible", doGet(S + "/pas-un-uuid", superadmin()));
    }

    @Test
    void creation_validations_et_unicite_des_codes() throws Exception {
        amorcer();
        golden("creation", doPost(S, "{'societe':" + SOCIETE_JSON + ",'premierCentre':" + CENTRE_JSON + "}", superadmin()));
        goldenSql("creation-etat-en-base", "SELECT code, raison_sociale, nif, ville, telephone, email, pied_page, actif FROM societes WHERE code = ?", "ORGT-N1");
        goldenSql("creation-centre-en-base", "SELECT code, name, ville, actif FROM centers WHERE code = ?", "ORGT-NC1");
        golden("creation-code-societe-deja-utilise", doPost(S, "{'societe':" + SOCIETE_JSON + ",'premierCentre':{'code':'ORGT-NC2','nom':'x'}}", superadmin()));
        golden("creation-code-centre-deja-utilise", doPost(S,
                "{'societe':{'code':'ORGT-N2','raisonSociale':'x'},'premierCentre':" + CENTRE_JSON + "}", superadmin()));
        golden("creation-code-invalide", doPost(S, "{'societe':{'code':'a b!','raisonSociale':'x'},'premierCentre':" + CENTRE_JSON + "}", superadmin()));
        golden("creation-sans-premier-centre", doPost(S, "{'societe':" + SOCIETE_JSON + "}", superadmin()));
        golden("creation-raison-sociale-vide", doPost(S, "{'societe':{'code':'ORGT-N3','raisonSociale':''},'premierCentre':" + CENTRE_JSON + "}", superadmin()));
        golden("creation-email-invalide", doPost(S, "{'societe':{'code':'ORGT-N4','raisonSociale':'x','email':'pas-un-email'},'premierCentre':{'code':'ORGT-NC4','nom':'x'}}", superadmin()));
        golden("creation-corps-illisible", doPost(S, "{pas du json", superadmin()));
        golden("creation-sans-corps", doPost(S, null, superadmin()));
    }

    @Test
    void modification_activation_et_desactivation_d_une_societe() throws Exception {
        amorcer();
        golden("modification", doPut(S + "/" + societe1, "{'code':'ORGT-S1','raisonSociale':'Raison modifiée','ville':'Oran'}", superadmin()));
        golden("modification-code-d-une-autre-societe", doPut(S + "/" + societe1, "{'code':'ORGT-S2','raisonSociale':'x'}", superadmin()));
        golden("modification-inconnue", doPut(S + "/" + UUID.fromString("99998200-0000-0000-0000-0000000000e9"), "{'code':'ORGT-ZZ','raisonSociale':'x'}", superadmin()));
        golden("desactivation", doPost(S + "/" + societe1 + "/desactiver", null, superadmin()));
        golden("activation", doPost(S + "/" + societe1 + "/activer", null, superadmin()));
        goldenSql("societes-en-base", "SELECT code, raison_sociale, ville, actif FROM societes WHERE code LIKE ? ORDER BY code", CODE_PREFIXE + "%");
    }

    @Test
    void centres_ajout_modification_activation_dernier_centre_et_transfert() throws Exception {
        amorcer();
        golden("centre-ajout", doPost(S + "/" + societe1 + "/centres", "{'code':'ORGT-C4','nom':'Centre 4','ville':'Blida'}", superadmin()));
        golden("centre-ajout-code-deja-utilise", doPost(S + "/" + societe1 + "/centres", "{'code':'ORGT-C1','nom':'x'}", superadmin()));
        golden("centre-modification", doPut(S + "/" + societe1 + "/centres/" + centre1, "{'code':'ORGT-C1','nom':'Centre renommé'}", superadmin()));
        golden("centre-modification-d-une-autre-societe", doPut(S + "/" + societe2 + "/centres/" + centre1, "{'code':'ORGT-C1','nom':'x'}", superadmin()));
        golden("centre-desactivation-1", doPost(S + "/" + societe1 + "/centres/" + centre2 + "/desactiver", null, superadmin()));
        golden("centre-reactivation", doPost(S + "/" + societe1 + "/centres/" + centre2 + "/activer", null, superadmin()));
        golden("centre-transfert", doPost(S + "/" + societe1 + "/centres/" + centre2 + "/transfert", "{'societeCibleId':'" + societe2 + "'}", superadmin()));
        golden("centre-transfert-meme-societe", doPost(S + "/" + societe2 + "/centres/" + centre2 + "/transfert", "{'societeCibleId':'" + societe2 + "'}", superadmin()));
        golden("centre-transfert-vers-societe-inconnue", doPost(S + "/" + societe2 + "/centres/" + centre2 + "/transfert", "{'societeCibleId':'99998200-0000-0000-0000-0000000000e9'}", superadmin()));
        golden("centre-transfert-sans-cible", doPost(S + "/" + societe2 + "/centres/" + centre2 + "/transfert", "{}", superadmin()));
        golden("centre-inconnu", doPost(S + "/" + societe1 + "/centres/99998200-0000-0000-0000-0000000000e9/activer", null, superadmin()));
        goldenSql("centres-en-base", "SELECT code, name, actif, societe_id FROM centers WHERE code LIKE ? ORDER BY code", CODE_PREFIXE + "%");
    }

    @Test
    void invariant_au_moins_un_centre_actif_par_societe() throws Exception {
        amorcer();
        // societe2 n'a qu'un centre actif (CENTRE_3) : le désactiver ou le transférer est refusé.
        UUID dernier = jdbc.queryForObject("SELECT id FROM centers WHERE code = ?", UUID.class, CODE_PREFIXE + "C3");
        golden("dernier-centre-desactivation", doPost(S + "/" + societe2 + "/centres/" + dernier + "/desactiver", null, superadmin()));
        golden("dernier-centre-transfert", doPost(S + "/" + societe2 + "/centres/" + dernier + "/transfert",
                "{'societeCibleId':'" + societe1 + "'}", superadmin()));
        goldenSql("dernier-centre-en-base", "SELECT code, actif, societe_id FROM centers WHERE code = ?", CODE_PREFIXE + "C3");
        // société avec deux centres : le premier se désactive, le dernier non.
        golden("deux-centres-desactivation-du-premier", doPost(S + "/" + societe1 + "/centres/" + centre2 + "/desactiver", null, superadmin()));
        golden("deux-centres-desactivation-du-dernier", doPost(S + "/" + societe1 + "/centres/" + centre1 + "/desactiver", null, superadmin()));
        golden("deux-centres-transfert-du-dernier", doPost(S + "/" + societe1 + "/centres/" + centre1 + "/transfert",
                "{'societeCibleId':'" + societe2 + "'}", superadmin()));
    }

    @Test
    void transfert_vers_une_societe_desactivee() throws Exception {
        amorcer();
        doPost(S + "/" + societe2 + "/desactiver", null, superadmin());
        golden("centre-transfert-vers-societe-desactivee", doPost(S + "/" + societe1 + "/centres/" + centre2 + "/transfert",
                "{'societeCibleId':'" + societe2 + "'}", superadmin()));
    }

    @Test
    void logo_envoi_lecture_refus_et_suppression() throws Exception {
        amorcer();
        golden("logo-lecture-sans-logo", doGet(S + "/" + societe1 + "/logo", superadmin()));
        golden("logo-suppression-sans-logo", doDelete(S + "/" + societe1 + "/logo", superadmin()));
        golden("logo-envoi", doPutLogo(S + "/" + societe1 + "/logo", png(), "image/png", superadmin()));
        Resp lecture = doGet(S + "/" + societe1 + "/logo", superadmin());
        ObjectNode entetes = mapper.createObjectNode();
        entetes.put("status", lecture.status());
        entetes.put("contentType", lecture.contentType());
        entetes.put("cacheControl", lecture.cacheControl());
        entetes.put("octets", lecture.raw().length() > 0);
        goldenNode("logo-lecture", entetes);
        golden("logo-envoi-contenu-illisible", doPutLogo(S + "/" + societe1 + "/logo", new byte[]{1, 2, 3, 4}, "image/png", superadmin()));
        golden("logo-envoi-fichier-vide", doPutLogo(S + "/" + societe1 + "/logo", new byte[0], "image/png", superadmin()));
        golden("logo-envoi-societe-inconnue", doPutLogo(S + "/99998200-0000-0000-0000-0000000000e9/logo", png(), "image/png", superadmin()));
        golden("logo-suppression", doDelete(S + "/" + societe1 + "/logo", superadmin()));
        golden("logo-lecture-apres-suppression", doGet(S + "/" + societe1 + "/logo", superadmin()));
    }
}
