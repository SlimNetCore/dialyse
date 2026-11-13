package com.hemodialyse.backend.characterization.organisation.pl11;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * PL11-0 : filet transverse de l'administration generique : descripteurs, droits complementaires et cumuls, garde de centre,
 * formes illisibles (400/404/405/406/415), audit des refus. Complete la matrice {@code o2-administration/matrice-des-droits} (qui
 * ne contient que des statuts pour 12 routes) sans la dupliquer.
 */
class TransversePl11CharacterizationIntegrationTest extends Pl11FiletSupport {

    private static final UUID INCONNU = UUID.fromString("99998200-0000-0000-0000-0000000000e9");

    @Override
    protected String theme() {
        return "pl11-0-transverse";
    }

    private interface Appel {
        Resp executer(RequestPostProcessor who) throws Exception;
    }

    private UUID salleA;
    private UUID creneauA;
    private UUID medecinA;

    private void amorcer() {
        reinitialiser();
        salleA = salle(CENTRE_A, "SALLE_A1", "S1", "Salle une");
        creneauA = creneau(CENTRE_A, "CRENEAU_A1", "P1", "Matin");
        medecinA = medecin(CENTRE_A, "MEDECIN_A1", "Benali", "Karim", "Nephrologue");
    }

    @Test
    void les_descripteurs_des_huit_referentiels() throws Exception {
        trace("descripteurs (champs, libelles, alias, ordre d'import)", doGet(A, admin()));
        trace("descripteurs par un superadmin", doGet(A, as(CENTRE_A, "SUPERADMIN")));
        verrouiller("descripteurs");
    }

    private Map<String, RequestPostProcessor> profilsEtendus() {
        Map<String, RequestPostProcessor> p = new LinkedHashMap<>(profils());
        p.put("ADMIN du centre B", as(CENTRE_B, "ADMIN"));
        p.put("ADMIN + DIRECTION", user(new com.hemodialyse.backend.infrastructure.security.UserPrincipal(UUID.randomUUID().toString(),
                CENTRE_A.toString(), "u-admin-direction", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_DIRECTION")), true, SOCIETE_TEST.toString())));
        p.put("SUPERADMIN sans centre", as(null, "SUPERADMIN"));
        p.put("ADMIN + SECRETAIRE", as(CENTRE_A, "ADMIN", "SECRETAIRE"));
        p.put("role sans prefixe (admin en minuscules)", as(CENTRE_A, "admin"));
        return p;
    }

    @Test
    void les_droits_complementaires_et_les_cumuls() throws Exception {
        Map<String, Appel> routes = new LinkedHashMap<>();
        routes.put("PUT creneau", w -> modifier("creneaux", creneauA, CENTRE_A, "{'code':'P1','libelle':'Renomme'}", w));
        routes.put("PUT medecin", w -> modifier("medecins", medecinA, CENTRE_A, "{'nom':'Benali','prenom':'Karim','specialite':'X'}", w));
        routes.put("GET creneaux/template", w -> doGet(A + "/creneaux/template?format=csv", w));
        routes.put("GET medecins/template xlsx", w -> doGet(A + "/medecins/template", w));
        routes.put("POST salles/import", w -> importer("salles", CENTRE_A, false, "code,nom\nS9,Neuf\n", w));
        routes.put("POST creneaux/import reel", w -> importer("creneaux", CENTRE_A, true, "code,libelle\nP9,Neuf\n", w));
        routes.put("POST medecins/import", w -> importer("medecins", CENTRE_A, false, "nom\nNeuf\n", w));
        routes.put("GET salles avec recherche", w -> lister("salles", CENTRE_A, "search=S1", w));
        routes.put("GET kind inconnu", w -> doGet(A + "/inconnu?centerId=" + CENTRE_A, w));
        routes.put("DELETE salle (centre B demande)", w -> supprimer("salles", salleA, CENTRE_B, w));
        routes.put("GET salles (centre B demande)", w -> lister("salles", CENTRE_B, null, w));
        routes.put("POST salles (centre B demande)", w -> creer("salles", CENTRE_B, "{'code':'X','nom':'x'}", w));
        routes.put("PUT salle (centre B demande)", w -> modifier("salles", salleA, CENTRE_B, "{'code':'X','nom':'x'}", w));
        routes.put("POST salles/import (centre B demande)", w -> importer("salles", CENTRE_B, false, "code,nom\nS9,Neuf\n", w));

        ObjectNode matrice = mapper.createObjectNode();
        for (var route : routes.entrySet()) {
            ObjectNode ligne = mapper.createObjectNode();
            for (var profil : profilsEtendus().entrySet()) {
                amorcer();
                ligne.put(profil.getKey(), statutEtCode(route.getValue().executer(profil.getValue())));
            }
            amorcer();
            ligne.put("anonyme", statutEtCode(route.getValue().executer(null)));
            matrice.set(route.getKey(), ligne);
        }
        goldenNode("matrice-complementaire", matrice);
    }

    @Test
    void la_garde_de_centre() throws Exception {
        salle(CENTRE_A, "A", "S1", "Salle A");
        salle(CENTRE_B, "B", "S1", "Salle B");
        RequestPostProcessor superadmin = as(CENTRE_A, "SUPERADMIN");
        RequestPostProcessor superadminSansCentre = as(null, "SUPERADMIN");
        trace("admin : centre non indique = son centre", doGet(A + "/salles", admin()));
        trace("admin : son centre explicite", lister("salles", CENTRE_A, null, admin()));
        trace("admin : centre B", lister("salles", CENTRE_B, null, admin()));
        trace("admin : centre inconnu", lister("salles", INCONNU, null, admin()));
        trace("admin : centerId vide", doGet(A + "/salles?centerId=", admin()));
        trace("admin : centerId illisible", doGet(A + "/salles?centerId=abc", admin()));
        trace("admin sans centre de session, centre indique", lister("salles", CENTRE_A, null, as(null, "ADMIN")));
        trace("admin sans centre de session, centre omis", doGet(A + "/salles", as(null, "ADMIN")));
        trace("superadmin : centre A", lister("salles", CENTRE_A, null, superadmin));
        trace("superadmin : centre B", lister("salles", CENTRE_B, null, superadmin));
        trace("superadmin : centre omis (centre de session)", doGet(A + "/salles", superadmin));
        trace("superadmin sans centre de session : centre omis", doGet(A + "/salles", superadminSansCentre));
        trace("superadmin sans centre de session : centre B", lister("salles", CENTRE_B, null, superadminSansCentre));
        trace("superadmin ecrit dans le centre B", creer("salles", CENTRE_B, "{'code':'S2','nom':'Deux B'}", superadmin));
        trace("superadmin : corps sans centerId", doPost(A + "/salles", "{'values':{'code':'S3','nom':'Trois'}}", superadmin));
        trace("admin : corps sans centerId", doPost(A + "/salles", "{'values':{'code':'S3','nom':'Trois'}}", admin()));
        trace("admin : corps avec centerId nul", doPost(A + "/salles", "{'centerId':null,'values':{'code':'S4','nom':'Quatre'}}", admin()));
        trace("suppression sans centerId (centre de session)", doDelete(A + "/salles/" + INCONNU, admin()));
        trace("import sans centerId (centre de session)", doPostFichier(A + "/salles/import", "code,nom\nS5,Cinq\n".getBytes(StandardCharsets.UTF_8), "i.csv", admin()));
        traceSql("etat A", "SELECT code, nom FROM salle WHERE center_id = ? ORDER BY code", CENTRE_A);
        traceSql("etat B", "SELECT code, nom FROM salle WHERE center_id = ? ORDER BY code", CENTRE_B);
        verrouiller("garde-de-centre");
    }

    @Test
    void les_formes_illisibles_de_requete() throws Exception {
        UUID s = salle(CENTRE_A, "A", "S1", "Salle A");
        RequestPostProcessor admin = admin();
        String cree = "/salles";
        trace("POST JSON tronque", doPost(A + cree, "{", admin));
        trace("POST corps vide", send(post(java.net.URI.create(A + cree)).contentType(MediaType.APPLICATION_JSON), admin));
        trace("POST sans corps ni type", send(post(java.net.URI.create(A + cree)), admin));
        trace("POST tableau JSON", doPost(A + cree, "[]", admin));
        trace("POST values nul", doPost(A + cree, "{'centerId':'" + CENTRE_A + "','values':null}", admin));
        trace("POST values chaine", doPost(A + cree, "{'centerId':'" + CENTRE_A + "','values':'x'}", admin));
        trace("POST values avec valeur imbriquee", doPost(A + cree, "{'centerId':'" + CENTRE_A + "','values':{'code':{'a':1},'nom':'x'}}", admin));
        trace("POST centerId illisible", doPost(A + cree, "{'centerId':'abc','values':{}}", admin));
        trace("POST texte brut", send(post(java.net.URI.create(A + cree)).contentType(MediaType.TEXT_PLAIN).content("x"), admin));
        trace("POST XML", send(post(java.net.URI.create(A + cree)).contentType(MediaType.APPLICATION_XML).content("<a/>"), admin));
        trace("PUT id illisible", doPut(A + "/salles/pas-un-uuid", "{'centerId':'" + CENTRE_A + "','values':{}}", admin));
        trace("PUT JSON tronque", doPut(A + "/salles/" + s, "{", admin));
        trace("DELETE id illisible", doDelete(A + "/salles/pas-un-uuid?centerId=" + CENTRE_A, admin));
        trace("GET page illisible", doGet(A + "/salles?centerId=" + CENTRE_A + "&page=abc", admin));
        trace("GET taille illisible", doGet(A + "/salles?centerId=" + CENTRE_A + "&size=1.5", admin));
        trace("GET taille enorme", doGet(A + "/salles?centerId=" + CENTRE_A + "&size=99999999999", admin));
        trace("PATCH sur une salle", send(patch(java.net.URI.create(A + "/salles/" + s)).contentType(MediaType.APPLICATION_JSON).content("{}"), admin));
        trace("PUT sans identifiant", doPut(A + "/salles", "{}", admin));
        trace("DELETE sans identifiant", doDelete(A + "/salles?centerId=" + CENTRE_A, admin));
        trace("POST sur une salle identifiee", doPost(A + "/salles/" + s, "{}", admin));
        trace("GET d'une salle identifiee", doGet(A + "/salles/" + s, admin));
        trace("PUT sur les descripteurs", doPut(A, "{}", admin));
        trace("POST sur les descripteurs", doPost(A, "{}", admin));
        trace("DELETE sur le modele", doDelete(A + "/salles/template", admin));
        trace("POST sur le modele", doPost(A + "/salles/template", "{}", admin));
        trace("GET import", doGet(A + "/salles/import", admin));
        trace("Accept XML sur la liste", send(get(java.net.URI.create(A + "/salles?centerId=" + CENTRE_A)).accept(MediaType.APPLICATION_XML), admin));
        trace("Accept texte sur la liste", send(get(java.net.URI.create(A + "/salles?centerId=" + CENTRE_A)).accept(MediaType.TEXT_PLAIN), admin));
        trace("Accept XML sur les descripteurs", send(get(java.net.URI.create(A)).accept(MediaType.APPLICATION_XML), admin));
        trace("Accept JSON sur le modele csv", send(get(java.net.URI.create(A + "/salles/template?format=csv")).accept(MediaType.APPLICATION_JSON), admin));
        trace("Accept csv sur le modele csv", send(get(java.net.URI.create(A + "/salles/template?format=csv")).accept("text/csv"), admin));
        trace("Accept XML sur la suppression", send(delete(java.net.URI.create(A + "/salles/" + INCONNU + "?centerId=" + CENTRE_A)).accept(MediaType.APPLICATION_XML), admin));
        trace("import sans fichier", send(multipart(java.net.URI.create(A + "/salles/import?centerId=" + CENTRE_A)), admin));
        trace("import non multipart", doPost(A + "/salles/import?centerId=" + CENTRE_A, "{}", admin));
        trace("import dryRun illisible", doPostFichier(A + "/salles/import?centerId=" + CENTRE_A + "&dryRun=peutetre",
                "code,nom\nS9,Neuf\n".getBytes(StandardCharsets.UTF_8), "i.csv", admin));
        trace("import dryRun=1", doPostFichier(A + "/salles/import?centerId=" + CENTRE_A + "&dryRun=1",
                "code,nom\nS9,Neuf\n".getBytes(StandardCharsets.UTF_8), "i.csv", admin));
        trace("import dryRun=FALSE", doPostFichier(A + "/salles/import?centerId=" + CENTRE_A + "&dryRun=FALSE",
                "code,nom\nS8,Huit\n".getBytes(StandardCharsets.UTF_8), "i.csv", admin));
        trace("import d'un fichier .pdf", doPostFichier(A + "/salles/import?centerId=" + CENTRE_A, "abc".getBytes(StandardCharsets.UTF_8), "x.pdf", admin));
        trace("import d'un faux .xlsx", doPostFichier(A + "/salles/import?centerId=" + CENTRE_A, "abc".getBytes(StandardCharsets.UTF_8), "x.xlsx", admin));
        trace("import sans nom de fichier", doPostFichier(A + "/salles/import?centerId=" + CENTRE_A, "code,nom\nS7,Sept\n".getBytes(StandardCharsets.UTF_8), "", admin));
        trace("import d'un .txt", doPostFichier(A + "/salles/import?centerId=" + CENTRE_A, "code,nom\nS6,Six\n".getBytes(StandardCharsets.UTF_8), "x.txt", admin));
        traceSql("etat apres tous ces refus", "SELECT code, nom FROM salle WHERE center_id = ? ORDER BY code", CENTRE_A);
        verrouiller("formes-illisibles");
    }

    @Test
    void le_referentiel_demande_est_inconnu_ou_retire() throws Exception {
        RequestPostProcessor admin = admin();
        for (String kind : List.of("inconnu", "generateurs", "SALLES", "Creneaux", "salles%20", "cr%C3%A9neaux", "medecin", "salle")) {
            trace("GET " + kind, doGet(A + "/" + kind + "?centerId=" + CENTRE_A, admin));
        }
        trace("POST inconnu", creer("inconnu", CENTRE_A, "{'code':'x'}", admin));
        trace("PUT inconnu", modifier("inconnu", INCONNU, CENTRE_A, "{'code':'x'}", admin));
        trace("DELETE inconnu", supprimer("inconnu", INCONNU, CENTRE_A, admin));
        trace("IMPORT inconnu", importer("inconnu", CENTRE_A, false, "code\nx\n", admin));
        trace("TEMPLATE inconnu", doGet(A + "/inconnu/template", admin));
        trace("PUT inconnu, JSON tronque", doPut(A + "/inconnu/" + INCONNU, "{", admin));
        trace("DELETE inconnu, id illisible", doDelete(A + "/inconnu/abc", admin));
        trace("POST inconnu, JSON tronque", doPost(A + "/inconnu", "{", admin));
        trace("POST SALLES en majuscules", creer("SALLES", CENTRE_A, "{'code':'S1','nom':'Une'}", admin));
        verrouiller("referentiel-inconnu-ou-retire");
    }

    @Test
    void les_refus_et_les_ecritures_sont_traces_par_le_journal_d_audit() throws Exception {
        UUID s = salle(CENTRE_A, "A", "S1", "Une");
        trace("secretaire : creation refusee", creer("salles", CENTRE_A, "{'code':'S2','nom':'x'}", as(CENTRE_A, "SECRETAIRE")));
        trace("medecin : suppression refusee", supprimer("salles", s, CENTRE_A, as(CENTRE_A, "MEDECIN")));
        trace("admin d'un autre centre : modification refusee", modifier("salles", s, CENTRE_A, "{'code':'S1','nom':'x'}", as(CENTRE_B, "ADMIN")));
        trace("admin : JSON tronque", doPost(A + "/salles", "{", admin()));
        trace("admin : kind inconnu en creation", creer("inconnu", CENTRE_A, "{'code':'x'}", admin()));
        trace("admin : PATCH", send(patch(java.net.URI.create(A + "/salles/" + s)).contentType(MediaType.APPLICATION_JSON).content("{}"), admin()));
        trace("admin : PUT id illisible", doPut(A + "/salles/abc", "{}", admin()));
        trace("admin : lecture", lister("salles", CENTRE_A, null, admin()));
        trace("admin : descripteurs", doGet(A, admin()));
        trace("superadmin : creation dans B", creer("salles", CENTRE_B, "{'code':'S3','nom':'x'}", as(CENTRE_A, "SUPERADMIN")));
        trace("anonyme : creation", creer("salles", CENTRE_A, "{'code':'S4','nom':'x'}", null));
        traceAudit("journal d'audit (centre de session A et B)");
        verrouiller("audit-des-refus");
    }
}
