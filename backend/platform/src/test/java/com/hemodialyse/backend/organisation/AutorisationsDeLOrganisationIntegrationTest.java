package com.hemodialyse.backend.organisation;

import com.hemodialyse.backend.infrastructure.security.RoleScopeFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Droits des routes du contexte Organisation ({@code /api/v1/organisation/**}) : matrice routes x 11 profils + anonyme, garde de
 * centre (le centre vient de la session), 403 avant la lecture du corps, suppressions filtrées sur le centre.
 * <p>
 * Droits retenus (routes nouvelles, rapprochées du legacy : sociétés/centres = SUPERADMIN, administration des référentiels =
 * ADMIN de son centre ou SUPERADMIN, lectures = utilisateur authentifié). Le {@code RoleScopeFilter} de la plateforme limite un
 * compte SUPERADMIN à {@code /api/v1/societes|licenses|audit|supervision} : les routes {@code /organisation/centres} (SUPERADMIN)
 * et les écritures {@code /organisation/*} sont donc inatteignables pour lui (fermées, jamais ouvertes) : à décider.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AutorisationsDeLOrganisationIntegrationTest {

    private static final UUID CENTRE_A = UUID.fromString("99998000-0000-0000-0000-00000000000a");
    private static final UUID CENTRE_B = UUID.fromString("99998000-0000-0000-0000-00000000000b");
    private static final UUID SOCIETE = UUID.fromString("99998000-0000-0000-0000-0000000000f1");
    private static final UUID SALLE_A = UUID.fromString("99998000-0000-0000-0000-0000000000a1");
    private static final UUID MEDECIN_A = UUID.fromString("99998000-0000-0000-0000-0000000000a2");
    private static final UUID MACHINE_A = UUID.fromString("99998000-0000-0000-0000-0000000000a3");
    private static final UUID PROTOCOLE_A = UUID.fromString("99998000-0000-0000-0000-0000000000a4");
    private static final UUID CRENEAU_A = UUID.fromString("99998000-0000-0000-0000-0000000000a5");
    private static final String A = CENTRE_A.toString();

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RoleScopeFilter roleScopeFilter;
    @Autowired
    private CacheManager caches;
    private MockMvc mvc;

    private interface Appel {
        MockHttpServletRequestBuilder construire();
    }

    private static RequestPostProcessor as(UUID centre, String... roles) {
        return user(UserPrincipal.create(UUID.randomUUID().toString(), centre == null ? null : centre.toString(),
                "u-" + String.join("-", roles).toLowerCase(), "", List.of(roles), true));
    }

    private static Map<String, RequestPostProcessor> profils() {
        Map<String, RequestPostProcessor> p = new LinkedHashMap<>();
        p.put("ADMIN", as(CENTRE_A, "ADMIN"));
        p.put("SECRETAIRE", as(CENTRE_A, "SECRETAIRE"));
        p.put("MEDECIN seul", as(CENTRE_A, "MEDECIN"));
        p.put("INFIRMIER seul", as(CENTRE_A, "INFIRMIER"));
        p.put("MEDECIN + INFIRMIER", as(CENTRE_A, "MEDECIN", "INFIRMIER"));
        p.put("DIRECTION", user(new UserPrincipal(UUID.randomUUID().toString(), null, "u-direction", "",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, SOCIETE.toString())));
        p.put("SUPERADMIN", as(CENTRE_A, "SUPERADMIN"));
        p.put("SUPERADMIN + ADMIN", as(CENTRE_A, "SUPERADMIN", "ADMIN"));
        p.put("role inconnu (COMPTABLE)", as(CENTRE_A, "COMPTABLE"));
        p.put("aucun role", as(CENTRE_A));
        p.put("ADMIN sans centre", as(null, "ADMIN"));
        return p;
    }

    private static final Set<String> TOUS_SAUF_FERMES = Set.of("ADMIN", "SECRETAIRE", "MEDECIN seul", "INFIRMIER seul",
            "MEDECIN + INFIRMIER", "role inconnu (COMPTABLE)", "aucun role", "SUPERADMIN", "SUPERADMIN + ADMIN");
    /** Décision du 2026-11-03 : le RoleScopeFilter ouvre /api/v1/organisation au SUPERADMIN. */
    private static final Set<String> ADMIN_DU_CENTRE = Set.of("ADMIN", "SUPERADMIN", "SUPERADMIN + ADMIN");
    private static final Set<String> AUCUN = Set.of("SUPERADMIN", "SUPERADMIN + ADMIN");

    @BeforeEach
    void monter() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        nettoyer();
        caches.getCacheNames().forEach(n -> {
            var c = caches.getCache(n);
            if (c != null) c.clear();
        });
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'S-A', 'Salle A')", SALLE_A, CENTRE_A);
        jdbc.update("INSERT INTO medecin (id, center_id, nom, prenom, specialite) VALUES (?, ?, 'Nom', 'Prenom', 'Neph')", MEDECIN_A, CENTRE_A);
        jdbc.update("INSERT INTO machine (id, center_id, code, libelle, actif) VALUES (?, ?, 'M-A', 'Machine A', TRUE)", MACHINE_A, CENTRE_A);
        jdbc.update("INSERT INTO protocole (id, center_id, code, libelle) VALUES (?, ?, 'P-A', 'Protocole A')", PROTOCOLE_A, CENTRE_A);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'C-A', 'Matin')", CRENEAU_A, CENTRE_A);
    }

    @AfterEach
    void nettoyer() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            for (String t : List.of("salle", "medecin", "machine", "protocole", "position_creneau")) {
                jdbc.update("DELETE FROM " + t + " WHERE center_id = ?", c);
            }
        }
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String corps) {
        return b.contentType(MediaType.APPLICATION_JSON).content(corps.replace('\'', '"'));
    }

    private int statut(Appel appel, RequestPostProcessor qui) throws Exception {
        MockHttpServletRequestBuilder b = appel.construire();
        if (qui != null) b = b.with(qui);
        return mvc.perform(b).andReturn().getResponse().getStatus();
    }

    private Map<String, Appel> routes() {
        Map<String, Appel> r = new LinkedHashMap<>();
        String o = "/api/v1/organisation";
        r.put("POST /centres", () -> json(post(o + "/centres"), "{'societeId':'" + SOCIETE + "','code':'ZZ-1','nom':'N','fuseauHoraire':'UTC'}"));
        r.put("PUT /centres/{id}", () -> json(put(o + "/centres/" + CENTRE_A), "{'code':'ZZ-1','nom':'N'}"));
        r.put("POST /centres/{id}/transferer", () -> json(post(o + "/centres/" + CENTRE_A + "/transferer"), "{'nouvelleSocieteId':'" + SOCIETE + "'}"));
        r.put("GET /centres", () -> get(o + "/centres").param("societeId", SOCIETE.toString()));
        r.put("GET /centres/actifs", () -> get(o + "/centres/actifs").param("societeId", SOCIETE.toString()));
        r.put("POST /salles/{id}/ouvrir", () -> post(o + "/salles/" + SALLE_A + "/ouvrir").param("centreId", A));
        r.put("POST /salles/{id}/fermer", () -> post(o + "/salles/" + SALLE_A + "/fermer").param("centreId", A));
        r.put("POST /creneaux", () -> json(post(o + "/creneaux"), "{'centreId':'" + A + "','code':'X','libelle':'Soir'}"));
        r.put("PUT /creneaux/{id}", () -> json(put(o + "/creneaux/" + CRENEAU_A), "{'centreId':'" + A + "','code':'X','libelle':'Soir'}"));
        r.put("POST /referentiels/medecins", () -> json(post(o + "/referentiels/medecins"), "{'centreId':'" + A + "','nom':'N','prenom':'P','specialite':'S'}"));
        r.put("PUT /referentiels/medecins/{id}", () -> json(put(o + "/referentiels/medecins/" + MEDECIN_A).param("centreId", A), "{'nom':'N2','prenom':'P','specialite':'S'}"));
        r.put("DELETE /referentiels/medecins/{id}", () -> delete(o + "/referentiels/medecins/" + MEDECIN_A).param("centreId", A));
        r.put("POST /referentiels/machines", () -> json(post(o + "/referentiels/machines"), "{'centreId':'" + A + "','code':'MX','libelle':'L'}"));
        r.put("PUT /referentiels/machines/{id}", () -> json(put(o + "/referentiels/machines/" + MACHINE_A).param("centreId", A), "{'code':'MY','libelle':'L','actif':true}"));
        r.put("DELETE /referentiels/machines/{id}", () -> delete(o + "/referentiels/machines/" + MACHINE_A).param("centreId", A));
        r.put("POST /referentiels/protocoles", () -> json(post(o + "/referentiels/protocoles"), "{'centreId':'" + A + "','code':'PX','libelle':'L'}"));
        r.put("PUT /referentiels/protocoles/{id}", () -> json(put(o + "/referentiels/protocoles/" + PROTOCOLE_A).param("centreId", A), "{'code':'PY','libelle':'L'}"));
        r.put("DELETE /referentiels/protocoles/{id}", () -> delete(o + "/referentiels/protocoles/" + PROTOCOLE_A).param("centreId", A));
        for (String lecture : List.of("/salles", "/creneaux", "/referentiels/medecins", "/referentiels/machines",
                "/referentiels/protocoles", "/referentiels/articles", "/referentiels/generateurs",
                "/referentiels/centres-payeurs", "/referentiels/centres-payeurs-details", "/referentiels/agences",
                "/referentiels/caisses", "/referentiels/transporteurs", "/referentiels/categories-transport",
                "/referentiels/forfaits")) {
            r.put("GET " + lecture, () -> get(o + lecture).param("centreId", A));
        }
        r.put("GET /centres/{id}/parametres", () -> get(o + "/centres/" + CENTRE_A + "/parametres"));
        return r;
    }

    private static Set<String> autorises(String route) {
        if (route.startsWith("POST /centres") || route.startsWith("PUT /centres") || route.equals("GET /centres")
                || route.equals("GET /centres/actifs")) return AUCUN;
        if (route.startsWith("GET")) return TOUS_SAUF_FERMES;
        return ADMIN_DU_CENTRE;
    }

    @Test
    void les_routes_selon_les_onze_profils_et_l_anonyme() throws Exception {
        List<String> ecarts = new ArrayList<>();
        for (var route : routes().entrySet()) {
            Set<String> attendus = autorises(route.getKey());
            for (var profil : profils().entrySet()) {
                int s = statut(route.getValue(), profil.getValue());
                boolean refuse = s == 403;
                if (refuse == attendus.contains(profil.getKey())) {
                    ecarts.add(route.getKey() + " / " + profil.getKey() + " -> " + s);
                }
                setup();
            }
            int anonyme = statut(route.getValue(), null);
            if (anonyme != 401 && anonyme != 403) ecarts.add(route.getKey() + " / anonyme -> " + anonyme);
            setup();
        }
        assertThat(ecarts).as("cellules de la matrice contraires aux droits retenus").isEmpty();
    }

    private void setup() {
        monter();
    }

    @Test
    void un_admin_d_un_autre_centre_est_refuse_en_lecture_comme_en_ecriture() throws Exception {
        RequestPostProcessor adminB = as(CENTRE_B, "ADMIN");
        for (String cle : List.of("POST /salles/{id}/ouvrir", "POST /salles/{id}/fermer", "POST /creneaux",
                "PUT /creneaux/{id}", "POST /referentiels/medecins", "POST /referentiels/machines",
                "POST /referentiels/protocoles", "GET /salles", "GET /referentiels/medecins", "GET /creneaux")) {
            assertThat(statut(routes().get(cle), adminB)).as(cle).isEqualTo(403);
        }
        for (String cle : List.of("PUT /referentiels/medecins/{id}", "DELETE /referentiels/medecins/{id}")) {
            assertThat(statut(routes().get(cle), adminB)).as(cle).isEqualTo(403);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM medecin WHERE center_id = ?", Integer.class, CENTRE_A)).isEqualTo(1);
    }

    @Test
    void sans_centre_demande_les_ecritures_portent_sur_le_centre_de_la_session_et_ne_touchent_pas_un_autre_centre() throws Exception {
        UUID medecinB = UUID.randomUUID();
        jdbc.update("INSERT INTO medecin (id, center_id, nom) VALUES (?, ?, 'B')", medecinB, CENTRE_B);
        RequestPostProcessor adminA = as(CENTRE_A, "ADMIN");

        int suppression = mvc.perform(delete("/api/v1/organisation/referentiels/medecins/" + medecinB).with(adminA))
                .andReturn().getResponse().getStatus();
        assertThat(suppression).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM medecin WHERE id = ? AND actif = TRUE", Integer.class, medecinB))
                .as("le médecin du centre B reste actif après la désactivation demandée par l'admin du centre A").isEqualTo(1);

        int modification = mvc.perform(json(put("/api/v1/organisation/referentiels/medecins/" + medecinB),
                "{'nom':'Pirate','prenom':'x','specialite':'x'}").with(adminA)).andReturn().getResponse().getStatus();
        assertThat(modification).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT nom FROM medecin WHERE id = ?", String.class, medecinB)).isEqualTo("B");
    }

    @Test
    void un_superadmin_atteint_les_societes_sous_les_deux_familles_d_urls_et_les_autres_profils_aucune() throws Exception {
        UUID societe = UUID.fromString("99998000-0000-0000-0000-0000000000f2");
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?, 'ORGT-COEX', 'Coexistence', TRUE, CURRENT_TIMESTAMP)", societe);
        try {
            for (String base : List.of("/api/v1/societes", "/api/v1/organisation/societes")) {
                assertThat(statut(() -> get(base + "/" + societe), as(CENTRE_A, "SUPERADMIN"))).as(base).isEqualTo(200);
                assertThat(statut(() -> get(base + "/" + societe), as(CENTRE_A, "ADMIN"))).as(base + " ADMIN").isEqualTo(403);
                assertThat(statut(() -> get(base + "/" + societe), as(CENTRE_A, "SECRETAIRE"))).as(base + " SECRETAIRE").isEqualTo(403);
            }
            String legacy = mvc.perform(get("/api/v1/societes/" + societe).with(as(CENTRE_A, "SUPERADMIN"))).andReturn().getResponse().getContentAsString();
            String canonique = mvc.perform(get("/api/v1/organisation/societes/" + societe).with(as(CENTRE_A, "SUPERADMIN"))).andReturn().getResponse().getContentAsString();
            assertThat(canonique).as("même contenu sous les deux URLs").isEqualTo(legacy);
        } finally {
            jdbc.update("DELETE FROM societes WHERE id = ?", societe);
        }
    }

    @Test
    void les_reponses_sont_des_dto_non_vides_et_la_suppression_d_un_medecin_le_desactive() throws Exception {
        RequestPostProcessor admin = as(CENTRE_A, "ADMIN");
        for (String chemin : List.of("/salles", "/creneaux", "/referentiels/medecins", "/referentiels/machines", "/referentiels/protocoles")) {
            String corps = mvc.perform(get("/api/v1/organisation" + chemin).param("centreId", A).with(admin)).andReturn().getResponse().getContentAsString();
            assertThat(corps).as(chemin).isNotEqualTo("[]").doesNotContain("{}").contains("\"centreId\"");
        }
        assertThat(mvc.perform(get("/api/v1/organisation/salles").param("centreId", A).with(admin)).andReturn().getResponse().getContentAsString())
                .contains("\"code\":\"S-A\"").contains("\"ouverte\":true");
        assertThat(mvc.perform(get("/api/v1/organisation/referentiels/medecins").param("centreId", A).with(admin)).andReturn().getResponse().getContentAsString())
                .contains("\"nom\":\"Nom\"");
        assertThat(mvc.perform(delete("/api/v1/organisation/referentiels/medecins/" + MEDECIN_A).with(admin)).andReturn().getResponse().getStatus())
                .isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT actif FROM medecin WHERE id = ?", Boolean.class, MEDECIN_A))
                .as("la ligne reste, désactivée").isFalse();
        assertThat(mvc.perform(get("/api/v1/organisation/referentiels/medecins").param("centreId", A).with(admin)).andReturn().getResponse().getContentAsString())
                .doesNotContain("\"nom\":\"Nom\"");
    }

    @Test
    void le_refus_de_droits_passe_avant_un_corps_illisible_ou_un_type_de_contenu_non_supporte() throws Exception {
        RequestPostProcessor secretaire = as(CENTRE_A, "SECRETAIRE");
        for (String chemin : List.of("/api/v1/organisation/referentiels/medecins", "/api/v1/organisation/creneaux",
                "/api/v1/organisation/centres")) {
            int illisible = mvc.perform(post(chemin).contentType(MediaType.APPLICATION_JSON).content("{pas du json")
                    .with(secretaire)).andReturn().getResponse().getStatus();
            int autreType = mvc.perform(post(chemin).contentType(MediaType.TEXT_PLAIN).content("x").with(secretaire))
                    .andReturn().getResponse().getStatus();
            assertThat(illisible).as(chemin + " corps illisible").isEqualTo(403);
            assertThat(autreType).as(chemin + " type non supporté").isEqualTo(403);
        }
        RequestPostProcessor admin = as(CENTRE_A, "ADMIN");
        assertThat(mvc.perform(post("/api/v1/organisation/referentiels/medecins").contentType(MediaType.APPLICATION_JSON)
                .content("{pas du json").with(admin)).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(post("/api/v1/organisation/referentiels/medecins").contentType(MediaType.TEXT_PLAIN)
                .content("x").with(admin)).andReturn().getResponse().getStatus()).isEqualTo(415);
    }
}
