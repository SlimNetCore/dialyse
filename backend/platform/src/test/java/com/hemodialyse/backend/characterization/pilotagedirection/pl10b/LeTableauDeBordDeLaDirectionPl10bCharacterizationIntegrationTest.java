package com.hemodialyse.backend.characterization.pilotagedirection.pl10b;

import com.hemodialyse.backend.application.audit.AuditWriterService;
import com.hemodialyse.backend.characterization.pilotagedirection.PilotageDirectionCharacterizationSupport;
import com.hemodialyse.backend.infrastructure.security.AuditRequestFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * PL10b : complement du filet du tableau de bord de la direction ({@code DirectionDashboardRestController}, {@code GET /api/v1/direction/}
 * {@code societe, overview, breakdown, indicators, gmao, stock-groupes, capacite, absences, alerts/history} ; services de lecture
 * {@code DirectionAbsences/Capacite/Gmao/StockGroupesQueryService}). Complete PD1 (societe, overview, breakdown), PD3 (alertes), PD6/DM8/DM9
 * (indicateurs), les filets de GMAO, Stock, Planification et Absences (contenu de chaque route) sans les dupliquer : matrice des neuf
 * routes par role/cumul/garde de societe/anonyme, formes de la periode sur les sept routes a periode, valeurs par defaut au fuseau
 * d'Alger, societe sans centre, cloisonnement et seuil d'anonymat route par route (PD5-R9), volume, bornes de {@code limit}, audit,
 * methodes, chemins voisins et {@code Accept}.
 */
class LeTableauDeBordDeLaDirectionPl10bCharacterizationIntegrationTest extends PilotageDirectionCharacterizationSupport {

    private static final List<String> NEUF_ROUTES = List.of("/societe", "/overview", "/breakdown", "/indicators", "/gmao", "/stock-groupes",
            "/capacite", "/absences", "/alerts/history");
    private static final List<String> SEPT_ROUTES_A_PERIODE = List.of("/overview", "/breakdown", "/indicators", "/gmao", "/stock-groupes",
            "/capacite", "/absences");
    private static final List<String> CINQ_ROUTES_DES_LECTURES = List.of("/indicators", "/gmao", "/stock-groupes", "/capacite", "/absences");

    @Autowired
    private AuditRequestFilter filtreDAudit;
    @Autowired
    private AuditWriterService ecrivainDAudit;

    @Override
    protected String slice() {
        return "pl10b";
    }

    @Override
    protected String dossier() {
        return "tableau_de_bord";
    }

    // ------------------------------------------------------------------ outils

    private static RequestPostProcessor jeton(String utilisateur, String centre, String societe, String... roles) {
        List<GrantedAuthority> droits = new ArrayList<>();
        for (String r : roles) droits.add(new SimpleGrantedAuthority(r));
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(new UserPrincipal(utilisateur, centre, "u-pl10b", "", droits, true, societe));
    }

    private String periode() {
        return "?from=" + fixe(0) + "&to=" + fixe(100);
    }

    private String chemin(String route) {
        return DIRECTION + route + (route.equals("/societe") || route.equals("/alerts/history") ? "" : periode());
    }

    private String court(Resp r) {
        return statutEtCode(r);
    }

    /** Une ligne : le statut (et code) des neuf routes pour un profil. */
    private String neufRoutes(RequestPostProcessor who) throws Exception {
        StringBuilder s = new StringBuilder();
        for (String route : NEUF_ROUTES) s.append(route).append('=').append(court(doGet(chemin(route), who))).append(" | ");
        return s.toString();
    }

    /** Dit si la fin par defaut est le jour du Pilotage (Alger) ou celui de la JVM (KB-PL10b-1). */
    private static String origineDeLaFin(String fin) {
        if (fin.equals(AUJOURDHUI_DU_PILOTAGE.toString())) return "aujourd'hui du Pilotage (Alger)";
        if (fin.equals(LocalDate.now().toString())) return "jour de la JVM (horloge systeme)";
        return "autre";
    }

    private String sansHorodatage(Resp r) {
        JsonNode n = corps(r);
        if (n instanceof tools.jackson.databind.node.ObjectNode o) o.remove("generatedAt");
        return n.toString();
    }

    private static List<String> cles(JsonNode n) {
        List<String> l = new ArrayList<>();
        n.propertyNames().forEach(l::add);
        return l;
    }

    // ------------------------------------------------------------------ roles, cumuls, garde de societe

    @Test
    void les_neuf_routes_selon_le_role_le_cumul_la_garde_de_societe_et_l_anonyme() throws Exception {
        fixe(0);
        fixe(100);
        String membre = U_ADMIN.toString();
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profils().entrySet()) profils.put(p.getKey(), neufRoutes(p.getValue()));
        profils.put("DIRECTION portant un centre", neufRoutes(jeton(membre, CENTRE_A.toString(), SOCIETE.toString(), "ROLE_DIRECTION")));
        m.put("01 profils (DIRECTION est le nominal)", profils);
        Map<String, String> cumuls = new LinkedHashMap<>();
        cumuls.put("DIRECTION + ADMIN", neufRoutes(jeton(membre, null, SOCIETE.toString(), "ROLE_DIRECTION", "ROLE_ADMIN")));
        cumuls.put("DIRECTION + SUPERADMIN", neufRoutes(jeton(membre, null, SOCIETE.toString(), "ROLE_DIRECTION", "ROLE_SUPERADMIN")));
        cumuls.put("DIRECTION + MEDECIN", neufRoutes(jeton(membre, null, SOCIETE.toString(), "ROLE_DIRECTION", "ROLE_MEDECIN")));
        cumuls.put("DIRECTION + INFIRMIER", neufRoutes(jeton(membre, null, SOCIETE.toString(), "ROLE_DIRECTION", "ROLE_INFIRMIER")));
        cumuls.put("DIRECTION + SECRETAIRE", neufRoutes(jeton(membre, null, SOCIETE.toString(), "ROLE_DIRECTION", "ROLE_SECRETAIRE")));
        cumuls.put("SUPERADMIN sans societe", neufRoutes(jeton(membre, null, null, "ROLE_SUPERADMIN")));
        cumuls.put("role en minuscules", neufRoutes(jeton(membre, null, SOCIETE.toString(), "ROLE_direction")));
        cumuls.put("role sans prefixe", neufRoutes(jeton(membre, null, SOCIETE.toString(), "DIRECTION")));
        m.put("02 cumuls de roles", cumuls);
        Map<String, String> gardes = new LinkedHashMap<>();
        gardes.put("jeton d'une autre societe, utilisateur membre de la premiere", neufRoutes(directionDe(U_ADMIN, AUTRE_SOCIETE)));
        gardes.put("utilisateur inconnu en base", neufRoutes(directionDe(UUID.randomUUID(), SOCIETE)));
        gardes.put("utilisateur inactif", neufRoutes(directionDe(U_DIRECTION_INACTIVE, SOCIETE)));
        gardes.put("jeton sans societe", neufRoutes(directionDe(U_DIRECTION_DETACHEE, null)));
        gardes.put("utilisateur detache, jeton portant la societe", neufRoutes(directionDe(U_DIRECTION_DETACHEE, SOCIETE)));
        gardes.put("societe inactive", neufRoutes(directionDe(U_DIRECTION_SOCIETE_INACTIVE, SOCIETE_INACTIVE)));
        gardes.put("societe du jeton blanche", neufRoutes(jeton(membre, null, "  ", "ROLE_DIRECTION")));
        gardes.put("societe du jeton illisible", neufRoutes(jeton(membre, null, "pas-un-uuid", "ROLE_DIRECTION")));
        gardes.put("identifiant d'utilisateur illisible", neufRoutes(jeton("abc", null, SOCIETE.toString(), "ROLE_DIRECTION")));
        gardes.put("societe active sans centre", neufRoutes(directionDe(U_DIRECTION_SANS_CENTRE, SOCIETE_SANS_CENTRE)));
        m.put("03 garde de societe", gardes);
        Map<String, String> anonyme = new LinkedHashMap<>();
        for (String route : NEUF_ROUTES) anonyme.put(route, court(doGetAnonymous(chemin(route))));
        m.put("04 anonyme", anonyme);
        Map<String, String> sansDroitEtIllisible = new LinkedHashMap<>();
        for (String route : SEPT_ROUTES_A_PERIODE) {
            sansDroitEtIllisible.put(route + " MEDECIN, from=hier", court(doGet(DIRECTION + route + "?from=hier", as(CENTRE_A, "MEDECIN"))));
            sansDroitEtIllisible.put(route + " anonyme, from=hier", court(doGetAnonymous(DIRECTION + route + "?from=hier")));
            sansDroitEtIllisible.put(route + " societe inactive, from=hier", court(doGet(DIRECTION + route + "?from=hier",
                    directionDe(U_DIRECTION_SOCIETE_INACTIVE, SOCIETE_INACTIVE))));
        }
        m.put("05 un parametre illisible n'ouvre rien : 403 ou garde avant le 400", sansDroitEtIllisible);
        arbrePd("garde_roles_et_jetons", m);
    }

    // ------------------------------------------------------------------ periodes

    @Test
    void les_formes_de_la_periode_sur_les_sept_routes_et_les_valeurs_par_defaut_des_cinq_lectures() throws Exception {
        LocalDate d0 = fixe(0);
        LocalDate d100 = fixe(100);
        Map<String, String> formes = new LinkedHashMap<>();
        formes.put("debut apres fin", "?from=" + d100 + "&to=" + d0);
        formes.put("un seul jour", "?from=" + d0 + "&to=" + d0);
        formes.put("exactement 5 ans", "?from=" + d0 + "&to=" + alias(d0.plusYears(5), "<D+0 plus 5 ans>"));
        formes.put("5 ans et 1 jour", "?from=" + d0 + "&to=" + alias(d0.plusYears(5).plusDays(1), "<D+0 plus 5 ans et 1 jour>"));
        formes.put("from vide", "?from=&to=" + d100);
        formes.put("to vide", "?from=" + d0 + "&to=");
        formes.put("les deux vides", "?from=&to=");
        formes.put("mois et jour sur un chiffre", "?from=2026-1-1&to=" + d100);
        formes.put("sans tirets", "?from=20260101&to=" + d100);
        formes.put("barres obliques", "?from=2026/01/01&to=" + d100);
        formes.put("jour inexistant (30 fevrier)", "?from=2026-02-30&to=" + d100);
        formes.put("jour 00", "?from=2026-01-00&to=" + d100);
        formes.put("avec heure", "?from=2026-01-01T00:00:00&to=" + d100);
        formes.put("avec fuseau Z", "?from=2026-01-01Z&to=" + d100);
        formes.put("espace initial encode", "?from=%20" + d0 + "&to=" + d100);
        formes.put("annee 0001", "?from=0001-01-01&to=" + d100);
        formes.put("annee 999999", "?from=999999-01-01&to=" + d100);
        formes.put("fin au dernier jour de l'an 9999","?from=" + d0 + "&to=9999-12-31");
        formes.put("chiffres arabes-indiens", "?from=%D9%A2%D9%A0%D9%A2%D9%A6-%D9%A0%D9%A1-%D9%A0%D9%A1&to=" + d100);
        formes.put("from double (le premier gagne ?)", "?from=" + d0 + "&from=hier&to=" + d100);
        formes.put("from double inverse", "?from=hier&from=" + d0 + "&to=" + d100);
        formes.put("casse du nom (FROM)", "?FROM=hier&to=" + d100);
        formes.put("parametres parasites seuls", "?centreId=" + CENTRE_A + "&societeId=" + AUTRE_SOCIETE + "&page=3");
        Map<String, Object> m = new LinkedHashMap<>();
        for (String route : SEPT_ROUTES_A_PERIODE) {
            Map<String, String> cas = new LinkedHashMap<>();
            for (Map.Entry<String, String> f : formes.entrySet()) cas.put(f.getKey(), court(direction(route + f.getValue())));
            m.put(route, cas);
        }
        Map<String, Object> parDefaut = new LinkedHashMap<>();
        for (String route : CINQ_ROUTES_DES_LECTURES) {
            Resp rien = direction(route);
            Resp fromSeul = direction(route + "?from=" + d0);
            Resp toSeul = direction(route + "?to=" + d100);
            JsonNode a = corps(rien);
            parDefaut.put(route, "sans parametre : " + court(rien) + " from=" + a.path("from").asString() + " to=" + origineDeLaFin(a.path("to").asString())
                    + " | from seul : " + court(fromSeul) + " to=" + origineDeLaFin(corps(fromSeul).path("to").asString())
                    + " | to seul : " + court(toSeul) + " from=" + corps(toSeul).path("from").asString() + " | cles : " + cles(a));
        }
        m.put("valeurs par defaut (fin = aujourd'hui : horloge du Pilotage a Alger ou horloge de la JVM ; debut = 1er janvier de l'annee)", parDefaut);
        Map<String, Object> memeCorps = new LinkedHashMap<>();
        for (String route : CINQ_ROUTES_DES_LECTURES) {
            memeCorps.put(route, "societeId/centreId ignores : " + sansHorodatage(direction(route + periode())).equals(
                    sansHorodatage(direction(route + periode() + "&societeId=" + AUTRE_SOCIETE + "&centreId=" + CENTRE_AUTRE))));
        }
        m.put("la societe vient toujours du jeton", memeCorps);
        arbrePd("formes_de_periode_et_valeurs_par_defaut", m);
    }

    @Test
    void les_bornes_de_limit_de_l_historique_des_alertes_au_dela_de_celles_de_pd3() throws Exception {
        OffsetDateTime base = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        for (int i = 0; i < 210; i++) {
            alerteEnBase(SOCIETE, CENTRE_A, "Centre A", "STOCK_BAS_" + i, "WARNING", "1.5", base.plusMinutes(i), null);
        }
        Map<String, String> cas = new LinkedHashMap<>();
        for (String limit : List.of("", "1", "199", "200", "201", "2147483647", "2147483648", "-2147483648", "1.5", "%205", "5%20", "0x10", "1e2",
                "%D9%A5", "1&limit=2", "abc&limit=2", "+7", "007")) {
            Resp r = direction("/alerts/history?limit=" + limit);
            cas.put("limit=" + limit, court(r) + (r.status() == 200 ? " x" + corps(r).size() : ""));
        }
        Resp sans = direction("/alerts/history");
        cas.put("sans limit (50 par defaut)", court(sans) + " x" + corps(sans).size());
        Resp parasites = direction("/alerts/history?limit=3&societeId=" + AUTRE_SOCIETE + "&from=hier");
        cas.put("limit=3 avec societeId et from illisible", court(parasites) + (parasites.status() == 200 ? " x" + corps(parasites).size() : ""));
        Resp sansCentre = doGet(DIRECTION + "/alerts/history", directionDe(U_DIRECTION_SANS_CENTRE, SOCIETE_SANS_CENTRE));
        cas.put("societe sans centre", court(sansCentre) + " x" + corps(sansCentre).size());
        arbrePd("limit_de_l_historique", cas);
    }

    // ------------------------------------------------------------------ societe sans centre, volume

    @Test
    void une_societe_sans_centre_et_une_societe_a_soixante_centres_sur_les_neuf_routes() throws Exception {
        fixe(-1);
        fixe(0);
        fixe(100);
        RequestPostProcessor sansCentre = directionDe(U_DIRECTION_SANS_CENTRE, SOCIETE_SANS_CENTRE);
        Map<String, Object> m = new LinkedHashMap<>();
        for (String route : NEUF_ROUTES) {
            m.put("01 sans centre " + route, reponse(doGet(chemin(route), sansCentre)));
        }
        List<String> uuids = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            UUID id = idStable("centre", "volume-" + i);
            uuids.add(id.toString());
            jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", id, "ZT-PD-V" + i, "Volume " + String.format("%02d", i), SOCIETE);
        }
        try {
            Map<String, String> volume = new LinkedHashMap<>();
            for (String route : NEUF_ROUTES) {
                long debut = System.nanoTime();
                Resp r = doGet(chemin(route), direction());
                long ms = (System.nanoTime() - debut) / 1_000_000;
                JsonNode c = r.status() == 200 ? corps(r) : null;
                int centres = c == null ? -1 : c.has("centres") ? c.path("centres").size() : c.has("byCentre") ? c.path("byCentre").size() : -2;
                volume.put(route, court(r) + " | rapide (< 20 s) : " + (ms < 20_000) + " | nb de centres dans le corps : " + centres
                        + " | cles : " + (c == null ? null : c.isObject() ? cles(c) : "tableau x" + c.size()));
            }
            m.put("02 soixante centres actifs de plus (centres A et B inclus : 62)", volume);
        } finally {
            jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-PD-V%'");
        }
        arbrePd("sans_centre_et_volume", m);
    }

    // ------------------------------------------------------------------ cloisonnement et seuil d'anonymat

    @Test
    void le_cloisonnement_entre_societes_et_le_seuil_d_anonymat_route_par_route() throws Exception {
        jeuComplet();
        patientPd(CENTRE_AUTRE, "X1");
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> cloisonnement = new LinkedHashMap<>();
        for (String route : NEUF_ROUTES) {
            String miens = direction(route.equals("/societe") || route.equals("/alerts/history") ? route : route + periode()).raw();
            String autres = doGet(chemin(route), directionAutre()).raw();
            cloisonnement.put(route, "la societe voit 'Centre Confidentiel' : " + miens.contains("Centre Confidentiel")
                    + " | l'autre societe voit 'Centre A' : " + autres.contains("Centre A") + " | l'autre voit 'Centre B' : " + autres.contains("Centre B")
                    + " | corps identiques : " + miens.equals(autres));
        }
        m.put("01 cloisonnement (jeu complet, un patient dans l'autre societe)", cloisonnement);
        // Le centre B compte 3 patients : sous le seuil. On le monte a 4 puis 5 et on observe chaque route.
        Map<String, Object> seuil = new LinkedHashMap<>();
        for (int effectif = 3; effectif <= 6; effectif++) {
            if (effectif > 3) patientPd(CENTRE_B, "B" + effectif);
            Map<String, String> parRoute = new LinkedHashMap<>();
            for (String route : SEPT_ROUTES_A_PERIODE) {
                Resp r = direction(route + periode());
                String corps = r.raw();
                parRoute.put(route, court(r) + " | 'Centre B' present : " + corps.contains("Centre B")
                        + " | 'Centre A' present : " + corps.contains("Centre A") + " | nulls : " + (corps.length() - corps.replace("null", "").length()) / 4
                        + " | centresMasques : " + (corps(r).has("centresMasques") ? corps(r).path("centresMasques").asString() : "absent")
                        + " | seuilAnonymat : " + (corps(r).has("seuilAnonymat") ? corps(r).path("seuilAnonymat").asString() : "absent"));
            }
            seuil.put("centre B a " + effectif + " patients (A en a 9, jeu complet)", parRoute);
        }
        m.put("02 seuil d'anonymat par route (PD5-R9 : 4 masque, 5 publie ?)", seuil);
        arbrePd("cloisonnement_et_seuil", m);
    }

    @Test
    void les_corps_des_cinq_lectures_sur_le_jeu_complet() throws Exception {
        jeuComplet();
        Map<String, Object> m = new LinkedHashMap<>();
        for (String route : CINQ_ROUTES_DES_LECTURES) {
            m.put(route, reponse(direction(route + periode())));
        }
        arbrePd("corps_du_jeu_complet", m);
    }

    // ------------------------------------------------------------------ audit

    @Test
    void le_tableau_de_bord_dans_le_journal_d_audit() throws Exception {
        fixe(0);
        fixe(100);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter, filtreDAudit).build();
        ecrivainDAudit.flush();
        jdbc.update("DELETE FROM audit_log WHERE route_template LIKE '%/direction/%'");
        for (String route : NEUF_ROUTES) {
            doGet(chemin(route), direction());
            doGet(chemin(route), as(CENTRE_A, "ADMIN"));
        }
        doGet(DIRECTION + "/overview?from=hier", direction());
        ecrivainDAudit.flush();
        List<String> lignes = new ArrayList<>();
        for (String route : NEUF_ROUTES) {
            lignes.add("GET " + route + " suivi = " + com.hemodialyse.backend.application.audit.AuditActionResolver.isTracked("GET", DIRECTION + route));
        }
        for (Map<String, Object> row : jdbc.queryForList("SELECT action_code, http_method, route_template, status_code, COUNT(*) AS n "
                + "FROM audit_log WHERE route_template LIKE '%/direction/%' GROUP BY action_code, http_method, route_template, status_code "
                + "ORDER BY route_template, http_method, status_code")) {
            lignes.add("journal : " + row.get("ACTION_CODE") + " " + row.get("HTTP_METHOD") + " " + row.get("ROUTE_TEMPLATE") + " "
                    + row.get("STATUS_CODE") + " x" + row.get("N"));
        }
        jdbc.update("DELETE FROM audit_log WHERE route_template LIKE '%/direction/%'");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lignes", lignes);
        arbrePd("audit", m);
    }

    // ------------------------------------------------------------------ methodes, chemins voisins, Accept

    private String enveloppe(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requete) throws Exception {
        MockHttpServletResponse r = mvc.perform(requete).andReturn().getResponse();
        String type = r.getContentType() == null ? "-" : r.getContentType().split(";")[0];
        byte[] corps = r.getContentAsByteArray();
        String code = "";
        if (r.getStatus() >= 400 && corps.length > 0) {
            try {
                JsonNode c = mapper.readTree(new String(corps, StandardCharsets.UTF_8));
                code = c.has("code") ? " code=" + c.path("code").asString() : "";
            } catch (RuntimeException e) {
                code = "";
            }
        }
        return r.getStatus() + code + " type=" + type + " allow=" + (r.getHeader("Allow") == null ? null : java.util.Arrays.stream(r.getHeader("Allow").split(",\\s*")).sorted()
                .collect(java.util.stream.Collectors.joining(","))) + " cache=" + r.getHeader("Cache-Control") + " corps=" + (corps.length == 0 ? "vide" : "present");
    }

    @Test
    void les_methodes_les_chemins_voisins_et_les_en_tetes_du_tableau_de_bord() throws Exception {
        fixe(0);
        fixe(100);
        Map<String, String> cas = new LinkedHashMap<>();
        for (String route : NEUF_ROUTES) {
            String uri = chemin(route);
            for (String methode : List.of("HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")) {
                cas.put(methode + " " + route, enveloppe(request(HttpMethod.valueOf(methode), java.net.URI.create(uri)).with(direction())));
            }
            cas.put("POST " + route + " anonyme", enveloppe(request(HttpMethod.POST, java.net.URI.create(uri))));
            cas.put("POST " + route + " par un ADMIN", enveloppe(request(HttpMethod.POST, java.net.URI.create(uri)).with(as(CENTRE_A, "ADMIN"))));
            for (String accept : List.of("application/json", "application/xml", "text/html", "application/pdf", "*/*", "n'importe quoi")) {
                cas.put("GET " + route + " Accept: " + accept, enveloppe(request(HttpMethod.GET, java.net.URI.create(uri)).header("Accept", accept).with(direction())));
            }
        }
        Map<String, String> voisins = new LinkedHashMap<>();
        for (String voisin : List.of("", "/", "/Overview", "/overview/", "/overview.json", "/overview/x", "/societe/", "/societes", "/alerts", "/alerts/",
                "/alerts/history/", "/alerts/History", "/stock-groupe", "/stock-groupes/", "/report/", "/report.pdf", "/inconnu", "/snapshots/")) {
            voisins.put("GET /direction" + voisin, enveloppe(request(HttpMethod.GET, java.net.URI.create(DIRECTION + voisin)).with(direction())));
        }
        voisins.put("GET /overview avec corps JSON", enveloppe(request(HttpMethod.GET, java.net.URI.create(chemin("/overview"))).contentType("application/json")
                .content("{\"societeId\":\"" + AUTRE_SOCIETE + "\"}").with(direction())));
        voisins.put("GET /overview avec en-tete X-Societe-Id d'une autre societe", enveloppe(request(HttpMethod.GET, java.net.URI.create(chemin("/overview")))
                .header("X-Societe-Id", AUTRE_SOCIETE.toString()).with(direction())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 methodes et Accept", cas);
        m.put("02 chemins voisins et en-tetes", voisins);
        arbrePd("methodes_chemins_et_accept", m);
    }
}
