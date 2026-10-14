package com.hemodialyse.backend.characterization.gmao.equipements;

import com.hemodialyse.backend.contexts.gmao.domain.equipement.CalculDIndisponibilite;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.ChangementDeStatut;
import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.domain.planning.port.SalleVuePort;
import com.hemodialyse.backend.domain.referential.port.ReferentialRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Filet de caractérisation de la slice gmao-g3-cycle-vie-equipement : les 8 endpoints
 * {@code /api/v1/gmao/equipements} observés au niveau HTTP (MockMvc + chaîne de sécurité réelle + JPA réel sur H2),
 * et leurs effets observables hors du contrôleur : journal des statuts, alerte « générateur indisponible »
 * (table {@code notification_evenement}), éviction du cache {@code ref.generateurs}, lecteurs de la table
 * ({@code SalleVuePort}, {@code ReferentialRepositoryPort}), interaction avec la clôture d'intervention.
 * Décrit le comportement ACTUEL, y compris les défauts (voir .ddd/safety-net/known-bugs.md, KB-GMAO-32 et suivants).
 *
 * Non-déterminisme : ids générés -> alias ; instants (dateCreation, dateModification, horodatage inséré dans les
 * observations) -> {@code <HORLOGE>} ; aucun seam, aucun mock du code caractérisé.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class EquipementHttpCharacterizationIntegrationTest {

    private static final String BASE = "/api/v1/gmao/equipements";
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "gmao", "equipements");

    private static final UUID CENTRE_A = UUID.fromString("99996300-0000-0000-0000-00000000000a");
    private static final UUID CENTRE_B = UUID.fromString("99996300-0000-0000-0000-00000000000b");
    private static final UUID CENTRE_VIDE = UUID.fromString("99996300-0000-0000-0000-00000000000c");
    private static final UUID ADMIN_A = UUID.fromString("99996300-0000-0000-0000-0000000000f1");
    private static final UUID ADMIN_B = UUID.fromString("99996300-0000-0000-0000-0000000000f2");
    private static final UUID TECH = UUID.fromString("99996300-0000-0000-0000-0000000000f3");
    private static final UUID REFORMEUR = UUID.fromString("99996300-0000-0000-0000-0000000000f4");
    private static final UUID ADMIN_VIDE = UUID.fromString("99996300-0000-0000-0000-0000000000f5");

    private static final UUID SALLE_1 = UUID.fromString("99996300-0000-0000-0000-0000000005a1");
    private static final UUID SALLE_2 = UUID.fromString("99996300-0000-0000-0000-0000000005a2");
    private static final UUID SALLE_B = UUID.fromString("99996300-0000-0000-0000-0000000005b1");

    private static final UUID GEN = id(1);
    private static final UUID EAU = id(2);
    private static final UUID HS = id(3);
    private static final UUID REFORME = id(4);
    private static final UUID AUTRE_CENTRE = id(5);
    private static final UUID SUPPRIME = id(6);
    private static final UUID INCONNU = id(99);

    private static final OffsetDateTime INSTALLATION = OffsetDateTime.of(2020, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final String INSTALLATION_JSON = "2024-03-01T08:00:00Z";

    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern INSTANT_RE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?Z");
    private static final Pattern ENUM_RE = Pattern.compile("No enum constant (?:\\w+\\.)+(\\w+)\\.(\\w+)");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CacheManager caches;
    @Autowired
    private ReferentialRepositoryPort referentiel;
    @Autowired
    private SalleVuePort salles;

    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    private MockMvc mvc;

    private static UUID id(int n) {
        return UUID.fromString(String.format("99996300-0000-0000-0000-0000000001%02d", n));
    }

    // ------------------------------------------------------------------ harnais

    private static RequestPostProcessor qui(UUID centre, UUID userId, String... roles) {
        return user(UserPrincipal.create(userId.toString(), centre.toString(), "u-" + userId, "", List.of(roles), true));
    }

    private static RequestPostProcessor adminA() {
        return qui(CENTRE_A, ADMIN_A, "ADMIN");
    }

    private static RequestPostProcessor adminB() {
        return qui(CENTRE_B, ADMIN_B, "ADMIN");
    }

    private static RequestPostProcessor reformeur() {
        return qui(CENTRE_A, REFORMEUR, "ADMIN", "GMAO_REFORME");
    }

    private static RequestPostProcessor nonAdmin() {
        return qui(CENTRE_A, TECH, "TECHNICIEN");
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        caches.getCache("ref.generateurs").clear();
        preparerPostgres();
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, capacite) VALUES (?, ?, 'G3-S1', 'Salle 1', 1)", SALLE_1, CENTRE_A);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'G3-S2', 'Salle 2')", SALLE_2, CENTRE_A);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, capacite) VALUES (?, ?, 'G3-SB', 'Salle B', 1)", SALLE_B, CENTRE_B);
        inserer(GEN, "G-01", "Generateur 01", "GENERATEUR_DIALYSE", "EN_SERVICE", CENTRE_A, SALLE_1, 1, null);
        inserer(EAU, "E-01", "Station eau", "STATION_TRAITEMENT_EAU", "EN_SERVICE", CENTRE_A, null, 2, null);
        inserer(HS, "G-HS", "Generateur HS", "GENERATEUR_DIALYSE", "HORS_SERVICE", CENTRE_A, SALLE_2, 3, null);
        inserer(REFORME, "G-RF", "Generateur reforme", "GENERATEUR_DIALYSE", "REFORME", CENTRE_A, null, 4, null);
        inserer(AUTRE_CENTRE, "G-01", "Generateur centre B", "GENERATEUR_DIALYSE", "EN_SERVICE", CENTRE_B, SALLE_B, 5, null);
        inserer(SUPPRIME, "G-DEL", "Generateur supprime", "GENERATEUR_DIALYSE", "EN_SERVICE", CENTRE_A, null, 6,
                OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @AfterEach
    void cleanup() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B, CENTRE_VIDE)) {
            jdbc.update("DELETE FROM gmao_lignes_cout_intervention WHERE intervention_id IN "
                    + "(SELECT id FROM gmao_interventions WHERE centre_id = ?)", c);
            jdbc.update("DELETE FROM gmao_interventions WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM gmao_equipement_statut_historique WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM gmao_equipements WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM notification_evenement WHERE center_id = ?", c);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", c);
            jdbc.update("DELETE FROM salle WHERE center_id = ?", c);
        }
        if (PG16) {
            for (UUID u : UTILISATEURS) {
                jdbc.update("DELETE FROM app_user WHERE id = ?", u);
            }
            for (UUID c : List.of(CENTRE_A, CENTRE_B, CENTRE_VIDE)) {
                jdbc.update("DELETE FROM centers WHERE id = ?", c);
            }
        }
    }

    /**
     * Rejeu sur un vrai PostgreSQL 16 ({@code -Dgmao.pg16=true}, voir backend/scripts/gmao-pg16-rejeu.sh) : le schéma Flyway
     * porte les clés étrangères vers centers et app_user que H2 n'a pas. De plus V5 y déclare {@code code UNIQUE} global
     * alors que le modèle JPA déclare (centre, code) (KB-GMAO-33) : le filet insère le même code dans deux centres, la
     * contrainte globale est donc retirée de la base JETABLE pour jouer les scénarios ; le constat de cette contrainte est
     * fait à part (script de rejeu, EquipementPostgresContractIT).
     */
    private static final boolean PG16 = Boolean.getBoolean("gmao.pg16");
    private static final List<UUID> UTILISATEURS = List.of(ADMIN_A, ADMIN_B, TECH, REFORMEUR, ADMIN_VIDE);

    private void preparerPostgres() {
        if (!PG16) {
            return;
        }
        jdbc.execute("ALTER TABLE gmao_equipements DROP CONSTRAINT IF EXISTS gmao_equipements_code_key");
        for (UUID c : List.of(CENTRE_A, CENTRE_B, CENTRE_VIDE)) {
            jdbc.update("INSERT INTO centers (id, societe_id) VALUES (?, ?)", c, c);
        }
        for (UUID u : UTILISATEURS) {
            jdbc.update("INSERT INTO app_user (id, active, password_hash, username, must_change_password) "
                    + "VALUES (?, TRUE, 'x', ?, FALSE)", u, "pg16-g3-" + u);
        }
    }

    private void inserer(UUID id, String code, String designation, String type, String statut, UUID centre,
                         UUID salle, int rang, OffsetDateTime supprimeLe) {
        OffsetDateTime creeLe = OffsetDateTime.of(2026, 1, rang, 9, 0, 0, 0, ZoneOffset.UTC);
        jdbc.update("INSERT INTO gmao_equipements (id, code, designation, type, centre_id, statut, date_installation, "
                        + "date_creation, cree_par, salle_id, deleted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, code, designation, type, centre, statut, INSTALLATION, creeLe, ADMIN_A, salle, supprimeLe);
    }

    private record Resp(int status, String raw) {
    }

    private Resp send(MockHttpServletRequestBuilder b) throws Exception {
        MockHttpServletResponse r = mvc.perform(b).andReturn().getResponse();
        attendreLaLivraisonDesEvenements();
        return new Resp(r.getStatus(), r.getContentAsString(StandardCharsets.UTF_8));
    }

    /**
     * Depuis la refonte sur place (ADR 0024), l'alerte Planification et l'éviction du cache sont des écouteurs du registre
     * d'événements Spring Modulith : livrés après commit, de façon asynchrone. On attend que le registre soit vide (chaque
     * publication livrée est supprimée) avant d'observer leurs effets ; les assertions elles-mêmes ne changent pas.
     */
    private void attendreLaLivraisonDesEvenements() {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(10)).until(
                () -> jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class) == 0);
    }

    private Resp call(HttpMethod m, String path, String json, RequestPostProcessor who) throws Exception {
        MockHttpServletRequestBuilder b = request(m, BASE + path).with(who);
        if (json != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return send(b);
    }

    private Resp get(String path, RequestPostProcessor who) throws Exception {
        return call(HttpMethod.GET, path, null, who);
    }

    private Resp post(String path, String json, RequestPostProcessor who) throws Exception {
        return call(HttpMethod.POST, path, json, who);
    }

    private Resp put(String path, String json, RequestPostProcessor who) throws Exception {
        return call(HttpMethod.PUT, path, json, who);
    }

    private JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    private String code(Resp r) {
        try {
            return mapper.readTree(r.raw).path("code").asString("");
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ normalisation / golden

    private final class Scrubber {
        private final Resp resp;
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private int unknown = 0;

        Scrubber(Resp resp) {
            this.resp = resp;
            alias(CENTRE_A, "CENTRE_A");
            alias(CENTRE_B, "CENTRE_B");
            alias(ADMIN_A, "ADMIN_A");
            alias(ADMIN_B, "ADMIN_B");
            alias(TECH, "TECH");
            alias(REFORMEUR, "REFORMEUR");
            alias(SALLE_1, "SALLE_1");
            alias(SALLE_2, "SALLE_2");
            alias(SALLE_B, "SALLE_B");
            alias(GEN, "GENERATEUR_G01");
            alias(EAU, "STATION_EAU");
            alias(HS, "GENERATEUR_HORS_SERVICE");
            alias(REFORME, "GENERATEUR_REFORME");
            alias(AUTRE_CENTRE, "GENERATEUR_AUTRE_CENTRE");
            alias(SUPPRIME, "GENERATEUR_SUPPRIME");
            alias(INCONNU, "INCONNU");
        }

        private void alias(UUID u, String label) {
            aliases.put(u.toString(), "<" + label + ">");
        }

        String scrubString(String raw) {
            String s = ENUM_RE.matcher(raw).replaceAll("No enum constant <$1>.$2");
            Matcher m = UUID_RE.matcher(s);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String a = aliases.computeIfAbsent(m.group(), k -> "<UUID_GENERE_" + (++unknown) + ">");
                m.appendReplacement(sb, Matcher.quoteReplacement(a));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        JsonNode scrub(JsonNode n) {
            if (n.isString()) {
                return mapper.getNodeFactory().stringNode(scrubString(n.asString()));
            }
            if (n.isObject()) {
                ObjectNode o = mapper.createObjectNode();
                String code = n.path("code").asString("");
                boolean technique = "INTERNAL_ERROR".equals(code) || "DATA_ACCESS_ERROR".equals(code);
                n.properties().forEach(e -> {
                    String k = e.getKey();
                    JsonNode v = e.getValue();
                    if (technique && k.equals("detail")) {
                        o.set(k, mapper.getNodeFactory().stringNode("<message technique Spring/Jackson/SGBD non verrouille>"));
                    } else if (k.equals("observations") && v.isString()) {
                        o.set(k, mapper.getNodeFactory().stringNode(masquer(scrubString(v.asString()))));
                    } else if ((k.equals("dateCreation") || k.equals("dateModification")) && !v.isNull()) {
                        o.set(k, mapper.getNodeFactory().stringNode("<HORLOGE>"));
                    } else {
                        o.set(k, scrub(v));
                    }
                });
                return o;
            }
            if (n.isArray()) {
                ArrayNode a = mapper.createArrayNode();
                n.forEach(x -> a.add(scrub(x)));
                return a;
            }
            return n;
        }

        String render() {
            ObjectNode root = mapper.createObjectNode();
            root.put("status", resp.status);
            if (resp.raw == null || resp.raw.isBlank()) {
                root.putNull("body");
            } else {
                try {
                    root.set("body", scrub(mapper.readTree(resp.raw)));
                } catch (RuntimeException e) {
                    root.put("bodyText", scrubString(resp.raw));
                }
            }
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        }
    }

    private void golden(String name, Resp r) {
        GoldenMaster.verify(GOLDENS, name, new Scrubber(r).render());
    }

    private void goldenNode(String name, JsonNode node) {
        GoldenMaster.verify(GOLDENS, name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
    }

    private String masquer(String s) {
        return s == null ? null : INSTANT_RE.matcher(s).replaceAll("<HORLOGE>");
    }

    // ------------------------------------------------------------------ observations hors HTTP

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT * FROM gmao_equipements WHERE id = ?", id);
    }

    private List<Map<String, Object>> rowsParCode(UUID centre, String code) {
        return jdbc.queryForList("SELECT * FROM gmao_equipements WHERE centre_id = ? AND code = ?", centre, code);
    }

    private int compter(UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM gmao_equipements WHERE centre_id = ?", Integer.class, centre);
    }

    /** Historique « précédent>nouveau|motif » du plus ancien au plus récent. */
    private List<String> historique(UUID equipement) {
        return jdbc.queryForList("SELECT statut_precedent, statut_nouveau, motif FROM gmao_equipement_statut_historique "
                        + "WHERE equipement_id = ? ORDER BY changed_at", equipement).stream()
                .map(m -> m.get("STATUT_PRECEDENT") + ">" + m.get("STATUT_NOUVEAU") + "|" + masquer((String) m.get("MOTIF")))
                .toList();
    }

    private List<String> transitions(UUID equipement) {
        return jdbc.queryForList("SELECT statut_precedent, statut_nouveau FROM gmao_equipement_statut_historique "
                        + "WHERE equipement_id = ? ORDER BY changed_at", equipement).stream()
                .map(m -> m.get("STATUT_PRECEDENT") + ">" + m.get("STATUT_NOUVEAU")).toList();
    }

    /** Alertes « générateur indisponible » journalisées pour le centre : « générateur/statut/nbPatients/patients ». */
    private List<String> alertes(UUID centre) {
        return jdbc.queryForList("SELECT payload, target_roles FROM notification_evenement WHERE center_id = ? "
                        + "AND type = 'GENERATEUR_INDISPONIBLE' ORDER BY cree_le", centre).stream().map(m -> {
            JsonNode p = mapper.readTree(String.valueOf(m.get("PAYLOAD")));
            return p.path("generateur").asString() + "/" + p.path("statut").asString() + "/" + p.path("nbPatients").asString()
                    + "/" + p.path("patients").asString() + "/roles" + m.get("TARGET_ROLES");
        }).toList();
    }

    private boolean cacheAmorce() {
        return caches.getCache("ref.generateurs").get(CENTRE_A.toString()) != null;
    }

    private void amorcerCache() {
        referentiel.findGenerateurs(new CenterId(CENTRE_A));
        assertTrue(cacheAmorce(), "le cache doit être amorcé");
    }

    private static String creation(String code) {
        return creation(code, "GENERATEUR_DIALYSE");
    }

    private static String creation(String code, String type) {
        return "{\"code\":" + q(code) + ",\"designation\":\"Generateur neuf\",\"type\":" + q(type)
                + ",\"dateInstallation\":\"" + INSTALLATION_JSON + "\"}";
    }

    private static String q(Object o) {
        return o == null ? "null" : "\"" + o + "\"";
    }

    private static String miseAJour(String designation) {
        return "{\"designation\":" + q(designation) + "}";
    }

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(n);
    }

    // ------------------------------------------------------------------ autorisation

    @Test
    void les_huit_routes_sont_refusees_en_403_a_un_utilisateur_non_administrateur_sans_toucher_aux_donnees() throws Exception {
        String e = "/" + GEN;
        List<Resp> refus = List.of(
                post("", creation("X-1"), nonAdmin()),
                get("", nonAdmin()),
                get(e, nonAdmin()),
                put(e, miseAJour("Pirate"), nonAdmin()),
                post(e + "/hors-service", "{\"raison\":\"x\"}", nonAdmin()),
                post(e + "/reactiver", null, nonAdmin()),
                post(e + "/reformer", "{\"motif\":\"x\"}", nonAdmin()),
                post(e + "/observations", "{\"observation\":\"x\"}", nonAdmin()));
        for (Resp r : refus) {
            assertEquals(403, r.status, r.raw);
            assertTrue(r.raw.contains("ACCESS_DENIED"), r.raw);
        }
        golden("refus_non_admin_403", refus.get(0));
        assertEquals("EN_SERVICE", row(GEN).get("STATUT"));
        assertEquals("Generateur 01", row(GEN).get("DESIGNATION"));
        assertEquals(0, rowsParCode(CENTRE_A, "X-1").size());
        assertEquals(List.of(), historique(GEN));
    }

    @Test
    void une_requete_anonyme_est_refusee_sans_toucher_aux_donnees() throws Exception {
        golden("anonyme_lecture", send(request(HttpMethod.GET, BASE)));
        golden("anonyme_ecriture", send(request(HttpMethod.POST, BASE + "/" + GEN + "/hors-service")
                .contentType(MediaType.APPLICATION_JSON).content("{\"raison\":\"x\"}")));
        assertEquals("EN_SERVICE", row(GEN).get("STATUT"));
    }

    @Test
    void la_reforme_exige_l_habilitation_GMAO_REFORME_en_plus_de_l_administration() throws Exception {
        String corps = "{\"motif\":\"fin de vie\"}";
        Resp admin = post("/" + GEN + "/reformer", corps, adminA());
        assertEquals(403, admin.status);
        golden("reformer_admin_sans_habilitation_403", admin);
        assertEquals("EN_SERVICE", row(GEN).get("STATUT"));
        assertEquals(List.of(), alertes(CENTRE_A));

        // l'habilitation seule, sans le rôle ADMIN : le legacy réformait (200, KB-GMAO-32 : le @PreAuthorize de méthode
        // remplaçait celui de la classe). ÉCART DÉCIDÉ le 2026-10-11 : le nouveau code exige ADMIN et GMAO_REFORME (403).
        Resp seule = post("/" + GEN + "/reformer", corps, qui(CENTRE_A, REFORMEUR, "GMAO_REFORME"));
        assertEquals(403, seule.status);
        golden("reformer_habilitation_seule_sans_admin_nouveau_code", seule);
        assertEquals("EN_SERVICE", row(GEN).get("STATUT"));
        assertEquals(List.of(), alertes(CENTRE_A));

        // et inversement : un technicien (ni ADMIN ni GMAO_REFORME) est refusé
        assertEquals(403, post("/" + GEN + "/reformer", corps, nonAdmin()).status);
    }

    // ------------------------------------------------------------------ POST (enregistrer)

    @Test
    void un_administrateur_enregistre_un_generateur_en_service_rattache_a_son_centre_avec_son_journal() throws Exception {
        amorcerCache();
        String corps = "{\"code\":\"G-NEUF\",\"designation\":\"Generateur neuf\",\"type\":\"GENERATEUR_DIALYSE\","
                + "\"fabricant\":\"Fresenius\",\"modele\":\"5008S\",\"numeroSerie\":\"SN-123\","
                + "\"dateInstallation\":\"" + INSTALLATION_JSON + "\",\"localisation\":\"Salle 2\","
                + "\"salleId\":\"" + SALLE_2 + "\",\"prixAcquisition\":15000.50}";
        Resp r = post("", corps, adminA());
        assertEquals(201, r.status);
        golden("enregistrer_generateur_201", r);

        UUID nouveau = UUID.fromString(json(r).get("id").asString());
        Map<String, Object> db = row(nouveau);
        assertEquals("G-NEUF", db.get("CODE"));
        assertEquals("EN_SERVICE", db.get("STATUT"));
        assertEquals(CENTRE_A, db.get("CENTRE_ID"));
        assertEquals(ADMIN_A, db.get("CREE_PAR"));
        assertNull(db.get("MODIFIE_PAR"));
        assertNull(db.get("DELETED_AT"));
        assertNull(db.get("OBSERVATIONS"));
        assertEquals(SALLE_2, db.get("SALLE_ID"));
        assertEquals("15000.50", ((BigDecimal) db.get("PRIX_ACQUISITION")).toPlainString());
        // journal : une ligne de création (aucun statut précédent, motif vide) ; aucune alerte à la création
        assertEquals(List.of("null>EN_SERVICE|null"), historique(nouveau));
        assertEquals(List.of(), alertes(CENTRE_A));
        assertFalse(cacheAmorce(), "la création évince ref.generateurs");
    }

    @Test
    void un_json_minimal_suffit_et_le_statut_l_id_et_le_centre_du_corps_sont_ignores() throws Exception {
        Resp r = post("", "{\"code\":\"MIN\",\"designation\":\"Minimal\",\"type\":\"AUTRE\",\"dateInstallation\":\""
                + INSTALLATION_JSON + "\"}", adminA());
        assertEquals(201, r.status);
        golden("enregistrer_json_minimal_201", r);

        String force = "{\"id\":\"" + INCONNU + "\",\"code\":\"FORCE\",\"designation\":\"Force\",\"type\":\"AUTRE\","
                + "\"statut\":\"REFORME\",\"centreId\":\"" + CENTRE_B + "\",\"creePar\":\"" + ADMIN_B + "\","
                + "\"dateInstallation\":\"" + INSTALLATION_JSON + "\"}";
        Resp f = post("", force, adminA());
        assertEquals(201, f.status);
        golden("enregistrer_statut_centre_id_du_corps_ignores_201", f);
        Map<String, Object> db = rowsParCode(CENTRE_A, "FORCE").get(0);
        assertEquals("EN_SERVICE", db.get("STATUT"));
        assertEquals(CENTRE_A, db.get("CENTRE_ID"));
        assertEquals(ADMIN_A, db.get("CREE_PAR"));
        assertFalse(INCONNU.equals(db.get("ID")));
    }

    @Test
    void la_date_d_installation_avec_decalage_est_restituee_puis_relue_normalisee() throws Exception {
        String corps = "{\"code\":\"DEC\",\"designation\":\"Decalage\",\"type\":\"AUTRE\",\"dateInstallation\":\"2024-03-01T08:00:00+01:00\"}";
        Resp cree = post("", corps, adminA());
        assertEquals(201, cree.status);
        golden("enregistrer_date_avec_decalage_201", cree);
        String idNeuf = json(cree).get("id").asString();
        golden("enregistrer_date_avec_decalage_relue", get("/" + idNeuf, adminA()));
    }

    @Test
    void le_prix_d_acquisition_conserve_l_echelle_recue_a_la_creation_et_l_echelle_deux_en_base() throws Exception {
        Resp zero = post("", withPrix("P0", "0"), adminA());
        assertEquals(201, zero.status);
        golden("prix_zero_accepte_201", zero);
        Resp echelle = post("", withPrix("P1", "1234.5"), adminA());
        assertEquals(201, echelle.status);
        golden("prix_echelle_conservee_201", echelle);
        UUID p1 = UUID.fromString(json(echelle).get("id").asString());
        assertEquals("1234.50", ((BigDecimal) row(p1).get("PRIX_ACQUISITION")).toPlainString());
        Resp arrondi = post("", withPrix("P2", "12.345"), adminA());
        assertEquals(201, arrondi.status);
        golden("prix_trois_decimales_201", arrondi);
        UUID p2 = UUID.fromString(json(arrondi).get("id").asString());
        assertEquals("12.35", ((BigDecimal) row(p2).get("PRIX_ACQUISITION")).toPlainString());
        golden("prix_relu_apres_arrondi", get("/" + p2, adminA()));

        golden("prix_negatif_400", post("", withPrix("P3", "-0.01"), adminA()));
        assertEquals(0, rowsParCode(CENTRE_A, "P3").size());
        golden("prix_moins_zero_201", post("", withPrix("P4", "-0"), adminA()));
        golden("prix_depassement_precision_500", post("", withPrix("P5", "1000000000000"), adminA()));
        assertEquals(0, rowsParCode(CENTRE_A, "P5").size());
        assertEquals(201, post("", withPrix("P6", "999999999999.99"), adminA()).status);
    }

    private static String withPrix(String code, String prix) {
        return "{\"code\":\"" + code + "\",\"designation\":\"Prix\",\"type\":\"AUTRE\",\"dateInstallation\":\""
                + INSTALLATION_JSON + "\",\"prixAcquisition\":" + prix + "}";
    }

    @Test
    void les_champs_obligatoires_sont_valides_en_400_et_rien_n_est_cree() throws Exception {
        String d = ",\"dateInstallation\":\"" + INSTALLATION_JSON + "\"";
        golden("valid_code_vide", post("", "{\"code\":\"\",\"designation\":\"D\",\"type\":\"AUTRE\"" + d + "}", adminA()));
        golden("valid_code_blanc", post("", "{\"code\":\"   \",\"designation\":\"D\",\"type\":\"AUTRE\"" + d + "}", adminA()));
        golden("valid_code_absent", post("", "{\"designation\":\"D\",\"type\":\"AUTRE\"" + d + "}", adminA()));
        golden("valid_code_51_caracteres", post("", "{\"code\":\"" + repeat('c', 51) + "\",\"designation\":\"D\",\"type\":\"AUTRE\"" + d + "}", adminA()));
        golden("valid_designation_vide", post("", "{\"code\":\"V1\",\"designation\":\"\",\"type\":\"AUTRE\"" + d + "}", adminA()));
        golden("valid_designation_256_caracteres", post("", "{\"code\":\"V2\",\"designation\":\"" + repeat('d', 256) + "\",\"type\":\"AUTRE\"" + d + "}", adminA()));
        golden("valid_type_vide", post("", "{\"code\":\"V3\",\"designation\":\"D\",\"type\":\"\"" + d + "}", adminA()));
        golden("valid_type_absent", post("", "{\"code\":\"V4\",\"designation\":\"D\"" + d + "}", adminA()));
        golden("valid_date_installation_absente", post("", "{\"code\":\"V5\",\"designation\":\"D\",\"type\":\"AUTRE\"}", adminA()));
        golden("valid_date_installation_null", post("", "{\"code\":\"V5\",\"designation\":\"D\",\"type\":\"AUTRE\",\"dateInstallation\":null}", adminA()));
        for (String c : List.of("V1", "V2", "V3", "V4", "V5")) {
            assertEquals(0, rowsParCode(CENTRE_A, c).size(), c);
        }
        assertEquals(0, rowsParCode(CENTRE_A, repeat('c', 51)).size());
        // les bornes exactes sont acceptées
        assertEquals(201, post("", "{\"code\":\"" + repeat('c', 50) + "\",\"designation\":\"" + repeat('d', 255)
                + "\",\"type\":\"AUTRE\"" + d + "}", adminA()).status);
    }

    @Test
    void le_type_inconnu_ou_en_minuscules_est_refuse_en_400_avec_le_nom_de_l_enum_et_la_date_illisible_en_500() throws Exception {
        String d = ",\"dateInstallation\":\"" + INSTALLATION_JSON + "\"";
        golden("type_inconnu_400", post("", "{\"code\":\"T1\",\"designation\":\"D\",\"type\":\"TOASTER\"" + d + "}", adminA()));
        golden("type_minuscules_400", post("", "{\"code\":\"T2\",\"designation\":\"D\",\"type\":\"autre\"" + d + "}", adminA()));
        golden("date_installation_illisible", post("", "{\"code\":\"T3\",\"designation\":\"D\",\"type\":\"AUTRE\",\"dateInstallation\":\"hier\"}", adminA()));
        golden("date_installation_sans_decalage", post("", "{\"code\":\"T4\",\"designation\":\"D\",\"type\":\"AUTRE\",\"dateInstallation\":\"2024-03-01\"}", adminA()));
        for (String c : List.of("T1", "T2", "T3", "T4")) {
            assertEquals(0, rowsParCode(CENTRE_A, c).size(), c);
        }
    }

    @Test
    void les_longueurs_des_champs_libres_ne_sont_pas_validees_par_l_api_et_la_base_repond_500() throws Exception {
        String d = ",\"dateInstallation\":\"" + INSTALLATION_JSON + "\"";
        golden("fabricant_101_caracteres", post("", "{\"code\":\"L1\",\"designation\":\"D\",\"type\":\"AUTRE\",\"fabricant\":\"" + repeat('f', 101) + "\"" + d + "}", adminA()));
        assertEquals(201, post("", "{\"code\":\"L2\",\"designation\":\"D\",\"type\":\"AUTRE\",\"fabricant\":\"" + repeat('f', 100) + "\"" + d + "}", adminA()).status);
        golden("localisation_256_caracteres", post("", "{\"code\":\"L3\",\"designation\":\"D\",\"type\":\"AUTRE\",\"localisation\":\"" + repeat('l', 256) + "\"" + d + "}", adminA()));
        golden("numero_serie_101_caracteres", post("", "{\"code\":\"L4\",\"designation\":\"D\",\"type\":\"AUTRE\",\"numeroSerie\":\"" + repeat('n', 101) + "\"" + d + "}", adminA()));
        assertEquals(0, rowsParCode(CENTRE_A, "L1").size());
        assertEquals(0, rowsParCode(CENTRE_A, "L3").size());
        assertEquals(0, rowsParCode(CENTRE_A, "L4").size());
    }

    @Test
    void un_code_deja_utilise_dans_le_centre_est_refuse_mais_pas_ses_variantes_de_casse_ou_d_espaces() throws Exception {
        Resp doublon = post("", creation("G-01"), adminA());
        assertEquals(400, doublon.status);
        golden("code_en_double_400", doublon);
        assertEquals(1, rowsParCode(CENTRE_A, "G-01").size());

        // KB-GMAO-09 : la comparaison est exacte (sensible à la casse, sans rognage)
        for (String variante : List.of("g-01", " G-01", "G-01 ", "G -01")) {
            Resp r = post("", creation(variante), adminA());
            assertEquals(201, r.status, variante);
            assertEquals(1, rowsParCode(CENTRE_A, variante).size(), variante);
        }
        golden("code_variante_de_casse_accepte_201", post("", creation("G-01 x".toLowerCase()), adminA()));
        // le même code dans un autre centre est accepté : l'unicité est par centre
        Resp autre = post("", creation("E-01"), adminB());
        assertEquals(201, autre.status);
        assertEquals(1, rowsParCode(CENTRE_B, "E-01").size());
        assertEquals(1, rowsParCode(CENTRE_A, "E-01").size());
    }

    @Test
    void un_code_est_conserve_tel_quel_sans_rognage() throws Exception {
        Resp r = post("", creation("  ESP  "), adminA());
        assertEquals(201, r.status);
        golden("code_avec_espaces_non_rogne_201", r);
        assertEquals(1, rowsParCode(CENTRE_A, "  ESP  ").size());
    }

    @Test
    void un_code_d_equipement_supprime_logiquement_n_est_pas_vu_comme_doublon_par_l_application_mais_la_contrainte_de_base_le_refuse() throws Exception {
        // G-DEL est supprimé logiquement (deleted_at) : findByCentreIdAndCode l'ignore, la contrainte unique (centre, code) non
        Resp r = post("", creation("G-DEL"), adminA());
        golden("code_d_un_equipement_supprime_logiquement", r);
        assertEquals(1, rowsParCode(CENTRE_A, "G-DEL").size());
    }

    @Test
    void le_corps_absent_illisible_ou_d_un_autre_media_type_donne_500() throws Exception {
        golden("creer_corps_absent", send(request(HttpMethod.POST, BASE).with(adminA())));
        golden("creer_json_illisible", post("", "{\"code\":", adminA()));
        golden("creer_media_type_non_supporte", send(request(HttpMethod.POST, BASE).contentType(MediaType.TEXT_PLAIN)
                .content("code=x").with(adminA())));
        assertEquals(compter(CENTRE_A), 5);
    }

    // ------------------------------------------------------------------ capacité des salles (générateurs)

    @Test
    void un_generateur_de_plus_que_la_capacite_de_la_salle_est_refuse_mais_pas_un_autre_type_ni_une_salle_sans_capacite() throws Exception {
        // SALLE_1 : capacité 1, déjà occupée par G-01
        String dansSalle1 = "{\"code\":\"CAP\",\"designation\":\"D\",\"type\":\"GENERATEUR_DIALYSE\",\"salleId\":\"" + SALLE_1
                + "\",\"dateInstallation\":\"" + INSTALLATION_JSON + "\"}";
        Resp refus = post("", dansSalle1, adminA());
        golden("capacite_salle_depassee", refus);
        assertEquals(0, rowsParCode(CENTRE_A, "CAP").size());
        assertEquals(List.of(), historique(GEN));

        Resp autreType = post("", dansSalle1.replace("\"CAP\"", "\"CAP2\"").replace("GENERATEUR_DIALYSE", "POMPE_EAU"), adminA());
        assertEquals(201, autreType.status, "un équipement qui n'est pas un générateur n'occupe pas la capacité");

        // salle sans capacité, salle inconnue, salle d'un autre centre : aucune limite
        for (UUID salle : List.of(SALLE_2, INCONNU, SALLE_B)) {
            Resp r = post("", dansSalle1.replace("\"CAP\"", "\"CAP-" + salle.toString().substring(33) + "\"")
                    .replace(SALLE_1.toString(), salle.toString()), adminA());
            assertEquals(201, r.status, salle.toString());
        }
    }

    @Test
    void reformer_un_generateur_libere_sa_place_dans_la_salle_et_hors_service_la_conserve() throws Exception {
        jdbc.update("UPDATE gmao_equipements SET salle_id = ? WHERE id = ?", SALLE_1, HS);
        // capacité 1 de SALLE_1, deux occupants (G-01 et G-HS) : la salle est « pleine » (hors service compté)
        String corps = "{\"code\":\"CAP\",\"designation\":\"D\",\"type\":\"GENERATEUR_DIALYSE\",\"salleId\":\"" + SALLE_1
                + "\",\"dateInstallation\":\"" + INSTALLATION_JSON + "\"}";
        assertEquals(422, post("", corps, adminA()).status);
        var occupants = salles.findById(CENTRE_A, SALLE_1).orElseThrow().generateurs().stream().map(g -> g.code()).toList();
        assertEquals(List.of("G-01", "G-HS"), occupants);

        assertEquals(200, post("/" + GEN + "/hors-service", "{\"raison\":\"panne\"}", adminA()).status);
        assertEquals(2, salles.findById(CENTRE_A, SALLE_1).orElseThrow().generateurs().size(), "hors service : toujours affecté");
        assertEquals(422, post("", corps, adminA()).status);

        assertEquals(200, post("/" + GEN + "/reformer", "{\"motif\":\"fin\"}", reformeur()).status);
        occupants = salles.findById(CENTRE_A, SALLE_1).orElseThrow().generateurs().stream().map(g -> g.code()).toList();
        assertEquals(List.of("G-HS"), occupants, "réformé : n'occupe plus la salle");
        // HS occupe encore la salle (capacité 1) : le refus subsiste tant que G-HS y reste
        assertEquals(422, post("", corps, adminA()).status);
        jdbc.update("UPDATE gmao_equipements SET statut = 'DESACTIF' WHERE id = ?", HS);
        assertEquals(0, salles.findById(CENTRE_A, SALLE_1).orElseThrow().generateurs().size(), "désactivé : libère la salle");
        jdbc.update("UPDATE gmao_equipements SET deleted_at = CURRENT_TIMESTAMP, statut = 'EN_SERVICE' WHERE id = ?", HS);
        assertEquals(0, salles.findById(CENTRE_A, SALLE_1).orElseThrow().generateurs().size(), "supprimé logiquement : libère la salle");
        assertEquals(201, post("", corps, adminA()).status);
    }

    // ------------------------------------------------------------------ GET /{id}

    @Test
    void un_administrateur_consulte_un_equipement_de_son_centre() throws Exception {
        Resp r = get("/" + GEN, adminA());
        assertEquals(200, r.status);
        golden("consulter_equipement_200", r);
        golden("consulter_equipement_reforme_200", get("/" + REFORME, adminA()));
    }

    @Test
    void un_equipement_inconnu_ou_d_un_autre_centre_est_introuvable_en_404_et_un_identifiant_invalide_en_400() throws Exception {
        golden("consulter_inconnu_404", get("/" + INCONNU, adminA()));
        golden("consulter_autre_centre_404", get("/" + AUTRE_CENTRE, adminA()));
        golden("consulter_identifiant_invalide", get("/pas-un-uuid", adminA()));
        // chacun voit le sien
        assertEquals(200, get("/" + AUTRE_CENTRE, adminB()).status);
    }

    @Test
    void un_equipement_supprime_logiquement_reste_consultable_par_id_mais_disparait_de_la_liste() throws Exception {
        Resp r = get("/" + SUPPRIME, adminA());
        golden("consulter_equipement_supprime_logiquement", r);
        assertFalse(get("?size=50", adminA()).raw.contains("G-DEL"));
        // et il reste pilotable
        assertNotNull(row(SUPPRIME).get("DELETED_AT"));
        assertEquals(200, post("/" + SUPPRIME + "/observations", "{\"observation\":\"o\"}", adminA()).status);
        // toute écriture remet deleted_at à NULL : l'équipement « ressuscite » et réapparaît dans la liste
        assertNull(row(SUPPRIME).get("DELETED_AT"));
        assertTrue(get("?size=50", adminA()).raw.contains("G-DEL"));
    }

    // ------------------------------------------------------------------ GET (liste)

    @Test
    void la_liste_est_bornee_au_centre_triee_par_creation_decroissante_sans_les_supprimes() throws Exception {
        Resp r = get("", adminA());
        assertEquals(200, r.status);
        golden("lister_par_defaut", r);
        golden("lister_centre_b", get("", adminB()));
        golden("lister_centre_sans_equipement", get("", qui(CENTRE_VIDE, ADMIN_VIDE, "ADMIN")));
    }

    @Test
    void la_liste_se_filtre_par_statut_exact_et_se_pagine() throws Exception {
        golden("lister_statut_HORS_SERVICE", get("?statut=HORS_SERVICE", adminA()));
        golden("lister_statut_inconnu", get("?statut=PERDU", adminA()));
        golden("lister_statut_minuscules", get("?statut=hors_service", adminA()));
        golden("lister_statut_vide", get("?statut=", adminA()));
        golden("lister_statut_REFORME", get("?statut=REFORME", adminA()));
        golden("lister_page_0_taille_2", get("?page=0&size=2", adminA()));
        golden("lister_page_1_taille_2", get("?page=1&size=2", adminA()));
        golden("lister_page_hors_limites", get("?page=9&size=2", adminA()));
        golden("lister_taille_zero", get("?size=0", adminA()));
        golden("lister_page_negative", get("?page=-1", adminA()));
        golden("lister_taille_non_numerique", get("?size=abc", adminA()));
        assertEquals(200, get("?size=100000", adminA()).status, "la taille de page n'est pas bornée");
    }

    // ------------------------------------------------------------------ PUT (modifier)

    @Test
    void la_modification_remplace_les_caracteristiques_modifiables_et_laisse_code_type_statut_et_journal() throws Exception {
        amorcerCache();
        jdbc.update("UPDATE gmao_equipements SET fabricant = 'Ancien', modele = 'M0', numero_serie = 'S0', "
                + "localisation = 'L0', prix_acquisition = 100 WHERE id = ?", GEN);
        String corps = "{\"designation\":\"Generateur renomme\",\"fabricant\":\"Nouveau\",\"modele\":\"M1\","
                + "\"numeroSerie\":\"S1\",\"localisation\":\"L1\",\"salleId\":\"" + SALLE_1 + "\",\"prixAcquisition\":250.5,"
                + "\"code\":\"IGNORE\",\"type\":\"AUTRE\",\"statut\":\"REFORME\",\"dateInstallation\":\"2030-01-01T00:00:00Z\"}";
        Resp r = put("/" + GEN, corps, adminA());
        assertEquals(200, r.status);
        golden("modifier_equipement_200", r);

        Map<String, Object> db = row(GEN);
        assertEquals("G-01", db.get("CODE"));
        assertEquals("GENERATEUR_DIALYSE", db.get("TYPE"));
        assertEquals("EN_SERVICE", db.get("STATUT"));
        assertEquals("Generateur renomme", db.get("DESIGNATION"));
        assertEquals(ADMIN_A, db.get("MODIFIE_PAR"));
        assertNotNull(db.get("DATE_MODIFICATION"));
        assertEquals(List.of(), historique(GEN), "modifier ne journalise aucun changement de statut");
        assertEquals(List.of(), alertes(CENTRE_A));
        assertFalse(cacheAmorce(), "la modification évince ref.generateurs");
    }

    @Test
    void la_modification_est_un_remplacement_total_les_champs_omis_sont_effaces() throws Exception {
        jdbc.update("UPDATE gmao_equipements SET fabricant = 'Ancien', modele = 'M0', numero_serie = 'S0', "
                + "localisation = 'L0', prix_acquisition = 100 WHERE id = ?", GEN);
        Resp r = put("/" + GEN, miseAJour("Seule la designation"), adminA());
        assertEquals(200, r.status);
        golden("modifier_champs_omis_effaces_200", r);
        Map<String, Object> db = row(GEN);
        assertNull(db.get("FABRICANT"));
        assertNull(db.get("MODELE"));
        assertNull(db.get("NUMERO_SERIE"));
        assertNull(db.get("LOCALISATION"));
        assertNull(db.get("PRIX_ACQUISITION"));
        assertNull(db.get("SALLE_ID"), "salle omise = équipement retiré de sa salle");
    }

    @Test
    void la_modification_est_acceptee_sur_un_equipement_hors_service_ou_reforme_sans_changer_son_statut() throws Exception {
        Resp hs = put("/" + HS, miseAJour("HS renomme"), adminA());
        Resp rf = put("/" + REFORME, miseAJour("Reforme renomme"), adminA());
        golden("modifier_hors_service_200", hs);
        golden("modifier_reforme_200", rf);
        assertEquals("HORS_SERVICE", row(HS).get("STATUT"));
        assertEquals("REFORME", row(REFORME).get("STATUT"));
    }

    @Test
    void la_modification_refuse_une_designation_vide_un_prix_negatif_et_les_ids_inconnus_ou_d_un_autre_centre() throws Exception {
        golden("modifier_designation_vide", put("/" + GEN, miseAJour(""), adminA()));
        golden("modifier_designation_absente", put("/" + GEN, "{}", adminA()));
        golden("modifier_designation_256", put("/" + GEN, miseAJour(repeat('d', 256)), adminA()));
        golden("modifier_prix_negatif", put("/" + GEN, "{\"designation\":\"D\",\"prixAcquisition\":-1}", adminA()));
        golden("modifier_inconnu_400", put("/" + INCONNU, miseAJour("D"), adminA()));
        golden("modifier_autre_centre_400", put("/" + AUTRE_CENTRE, miseAJour("D"), adminA()));
        golden("modifier_identifiant_invalide", put("/pas-un-uuid", miseAJour("D"), adminA()));
        golden("modifier_corps_absent", send(request(HttpMethod.PUT, BASE + "/" + GEN).with(adminA())));
        assertEquals("Generateur 01", row(GEN).get("DESIGNATION"));
        assertEquals("Generateur centre B", row(AUTRE_CENTRE).get("DESIGNATION"));
    }

    @Test
    void la_modification_accepte_un_prix_nul_et_conserve_l_echelle_recue() throws Exception {
        Resp zero = put("/" + GEN, "{\"designation\":\"D\",\"prixAcquisition\":0}", adminA());
        assertEquals(200, zero.status);
        assertEquals("0.00", ((BigDecimal) row(GEN).get("PRIX_ACQUISITION")).toPlainString());
        Resp echelle = put("/" + GEN, "{\"designation\":\"D\",\"prixAcquisition\":99.9}", adminA());
        golden("modifier_prix_echelle_conservee_200", echelle);
        assertEquals("99.90", ((BigDecimal) row(GEN).get("PRIX_ACQUISITION")).toPlainString());
    }

    @Test
    void la_modification_verifie_la_capacite_de_la_salle_pour_un_generateur_en_s_excluant_lui_meme() throws Exception {
        // G-01 reste dans sa propre salle (capacité 1) : accepté ; G-HS (SALLE_2 sans capacité) rejoint SALLE_1 : refusé
        assertEquals(200, put("/" + GEN, "{\"designation\":\"D\",\"salleId\":\"" + SALLE_1 + "\"}", adminA()).status);
        Resp refus = put("/" + HS, "{\"designation\":\"D\",\"salleId\":\"" + SALLE_1 + "\"}", adminA());
        golden("modifier_capacite_salle_depassee", refus);
        assertEquals(SALLE_2, row(HS).get("SALLE_ID"));
        // un équipement qui n'est pas un générateur : aucun contrôle
        assertEquals(200, put("/" + EAU, "{\"designation\":\"D\",\"salleId\":\"" + SALLE_1 + "\"}", adminA()).status);
    }

    // ------------------------------------------------------------------ hors-service

    @Test
    void mettre_un_generateur_en_service_hors_service_journalise_alerte_la_planification_et_evince_le_cache() throws Exception {
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, created_at, salle_id, position_id, generateur_id, jour_lundi) "
                        + "VALUES (?, ?, 'G3-P1', 'Benali', 'Amine', 'M', DATE '2020-01-01', 'ASS-G3-1', 'NON_VACANCIER', "
                        + "CURRENT_TIMESTAMP, ?, ?, ?, TRUE)", id(50), CENTRE_A, SALLE_1, id(51), GEN);
        amorcerCache();
        Resp r = post("/" + GEN + "/hors-service", "{\"raison\":\"Fuite hydraulique\"}", adminA());
        assertEquals(200, r.status);
        golden("hors_service_generateur_200", r);

        assertEquals("HORS_SERVICE", row(GEN).get("STATUT"));
        assertEquals(ADMIN_A, row(GEN).get("MODIFIE_PAR"));
        assertEquals("Hors service: Fuite hydraulique", row(GEN).get("OBSERVATIONS"));
        assertEquals(List.of("EN_SERVICE>HORS_SERVICE|Hors service: Fuite hydraulique"), historique(GEN));
        assertEquals(List.of("G-01/HORS_SERVICE/1/Benali Amine/roles,ADMIN,SECRETAIRE,"), alertes(CENTRE_A));
        assertEquals(List.of(), alertes(CENTRE_B));
        assertFalse(cacheAmorce());
    }

    @Test
    void mettre_hors_service_un_equipement_qui_n_est_pas_un_generateur_ne_declenche_aucune_alerte() throws Exception {
        Resp r = post("/" + EAU + "/hors-service", "{\"raison\":\"Entretien\"}", adminA());
        assertEquals(200, r.status);
        golden("hors_service_station_eau_200", r);
        assertEquals(List.of("EN_SERVICE>HORS_SERVICE|Hors service: Entretien"), historique(EAU));
        assertEquals(List.of(), alertes(CENTRE_A));
    }

    @Test
    void mettre_hors_service_deux_fois_est_refuse_en_422_et_n_ajoute_ni_journal_ni_alerte() throws Exception {
        assertEquals(200, post("/" + GEN + "/hors-service", "{\"raison\":\"Panne\"}", adminA()).status);
        amorcerCache();
        Resp second = post("/" + GEN + "/hors-service", "{\"raison\":\"Encore\"}", adminA());
        golden("hors_service_deja_hors_service_422", second);
        assertEquals(1, historique(GEN).size());
        assertEquals(1, alertes(CENTRE_A).size());
        assertEquals("Hors service: Panne", row(GEN).get("OBSERVATIONS"));
        assertTrue(cacheAmorce(), "un refus n'évince pas le cache (l'éviction suit l'exécution réussie)");
    }

    @Test
    void mettre_hors_service_exige_une_raison_et_un_equipement_du_centre() throws Exception {
        golden("hors_service_raison_vide", post("/" + GEN + "/hors-service", "{\"raison\":\" \"}", adminA()));
        golden("hors_service_raison_absente", post("/" + GEN + "/hors-service", "{}", adminA()));
        golden("hors_service_corps_absent", post("/" + GEN + "/hors-service", null, adminA()));
        golden("hors_service_inconnu_400", post("/" + INCONNU + "/hors-service", "{\"raison\":\"x\"}", adminA()));
        golden("hors_service_autre_centre_400", post("/" + AUTRE_CENTRE + "/hors-service", "{\"raison\":\"x\"}", adminA()));
        golden("hors_service_identifiant_invalide", post("/pas-un-uuid/hors-service", "{\"raison\":\"x\"}", adminA()));
        golden("hors_service_reforme_422", post("/" + REFORME + "/hors-service", "{\"raison\":\"x\"}", adminA()));
        assertEquals("EN_SERVICE", row(GEN).get("STATUT"));
        assertEquals("EN_SERVICE", row(AUTRE_CENTRE).get("STATUT"));
        assertEquals(List.of(), alertes(CENTRE_A));
    }

    // ------------------------------------------------------------------ réactiver

    @Test
    void reactiver_un_equipement_hors_service_le_remet_en_service_avec_journal_sans_alerte_ni_trace_dans_les_observations() throws Exception {
        assertEquals(200, post("/" + GEN + "/hors-service", "{\"raison\":\"Panne\"}", adminA()).status);
        amorcerCache();
        Resp r = post("/" + GEN + "/reactiver", null, adminA());
        assertEquals(200, r.status);
        golden("reactiver_200", r);
        assertEquals("EN_SERVICE", row(GEN).get("STATUT"));
        assertEquals("Hors service: Panne", row(GEN).get("OBSERVATIONS"), "la remise en service n'ajoute rien aux observations");
        assertEquals(List.of("EN_SERVICE>HORS_SERVICE|Hors service: Panne", "HORS_SERVICE>EN_SERVICE|Hors service: Panne"),
                historique(GEN), "le motif du journal est le cumul des observations");
        assertEquals(1, alertes(CENTRE_A).size(), "aucune alerte à la remise en service");
        assertFalse(cacheAmorce());
    }

    @Test
    void reactiver_n_est_possible_que_depuis_hors_service() throws Exception {
        golden("reactiver_en_service_422", post("/" + GEN + "/reactiver", null, adminA()));
        golden("reactiver_reforme_422", post("/" + REFORME + "/reactiver", null, adminA()));
        golden("reactiver_inconnu_400", post("/" + INCONNU + "/reactiver", null, adminA()));
        golden("reactiver_autre_centre_400", post("/" + AUTRE_CENTRE + "/reactiver", null, adminA()));
        golden("reactiver_identifiant_invalide", post("/pas-un-uuid/reactiver", null, adminA()));
        assertEquals(List.of(), historique(GEN));
        assertEquals(200, post("/" + HS + "/reactiver", "{\"ignore\":true}", adminA()).status, "le corps est ignoré");
    }

    // ------------------------------------------------------------------ réformer

    @Test
    void reformer_un_generateur_en_service_est_definitif_alerte_la_planification_et_journalise() throws Exception {
        amorcerCache();
        Resp r = post("/" + GEN + "/reformer", "{\"motif\":\"Fin de vie\"}", reformeur());
        assertEquals(200, r.status);
        golden("reformer_200", r);
        assertEquals("REFORME", row(GEN).get("STATUT"));
        assertEquals(REFORMEUR, row(GEN).get("MODIFIE_PAR"));
        assertEquals("Réformé: Fin de vie", row(GEN).get("OBSERVATIONS"));
        assertEquals(List.of("EN_SERVICE>REFORME|Réformé: Fin de vie"), historique(GEN));
        assertEquals(List.of("G-01/REFORME/0//roles,ADMIN,SECRETAIRE,"), alertes(CENTRE_A));
        assertFalse(cacheAmorce());

        Resp encore = post("/" + GEN + "/reformer", "{\"motif\":\"Encore\"}", reformeur());
        golden("reformer_deja_reforme_422", encore);
        assertEquals(1, historique(GEN).size());
        assertEquals(1, alertes(CENTRE_A).size());
    }

    @Test
    void reformer_depuis_hors_service_ne_declenche_pas_d_alerte_car_le_generateur_n_etait_plus_en_service() throws Exception {
        Resp r = post("/" + HS + "/reformer", "{\"motif\":\"Irreparable\"}", reformeur());
        assertEquals(200, r.status);
        assertEquals("REFORME", row(HS).get("STATUT"));
        assertEquals(List.of("HORS_SERVICE>REFORME|Réformé: Irreparable"), historique(HS));
        assertEquals(List.of(), alertes(CENTRE_A));
    }

    @Test
    void reformer_exige_un_motif_et_un_equipement_du_centre() throws Exception {
        golden("reformer_motif_vide", post("/" + GEN + "/reformer", "{\"motif\":\"\"}", reformeur()));
        golden("reformer_motif_absent", post("/" + GEN + "/reformer", "{}", reformeur()));
        golden("reformer_corps_absent", post("/" + GEN + "/reformer", null, reformeur()));
        golden("reformer_inconnu_400", post("/" + INCONNU + "/reformer", "{\"motif\":\"x\"}", reformeur()));
        golden("reformer_autre_centre_400", post("/" + AUTRE_CENTRE + "/reformer", "{\"motif\":\"x\"}", reformeur()));
        golden("reformer_identifiant_invalide", post("/pas-un-uuid/reformer", "{\"motif\":\"x\"}", reformeur()));
        assertEquals("EN_SERVICE", row(GEN).get("STATUT"));
        assertEquals(List.of(), alertes(CENTRE_A));
    }

    // ------------------------------------------------------------------ observations

    @Test
    void ajouter_une_observation_horodate_le_texte_sans_toucher_au_statut_au_journal_ni_au_cache() throws Exception {
        amorcerCache();
        Resp r = post("/" + GEN + "/observations", "{\"observation\":\"Bruit anormal\"}", adminA());
        assertEquals(200, r.status);
        golden("observation_200", r);
        Resp seconde = post("/" + GEN + "/observations", "{\"observation\":\"Plus de bruit\"}", adminA());
        golden("observation_seconde_cumulee", seconde);
        assertEquals("EN_SERVICE", row(GEN).get("STATUT"));
        assertEquals(List.of(), historique(GEN));
        assertEquals(ADMIN_A, row(GEN).get("MODIFIE_PAR"));
        assertTrue(cacheAmorce(), "une observation n'évince pas le cache");
        assertTrue(masquer((String) row(GEN).get("OBSERVATIONS"))
                .equals("<HORLOGE> - Bruit anormal\n<HORLOGE> - Plus de bruit"));
    }

    @Test
    void les_observations_sont_acceptees_sur_un_equipement_reforme_et_cumulees_avec_le_motif_du_statut() throws Exception {
        assertEquals(200, post("/" + REFORME + "/observations", "{\"observation\":\"Archive\"}", adminA()).status);
        assertEquals(200, post("/" + GEN + "/observations", "{\"observation\":\"Avant panne\"}", adminA()).status);
        assertEquals(200, post("/" + GEN + "/hors-service", "{\"raison\":\"Panne\"}", adminA()).status);
        assertEquals("REFORME", row(REFORME).get("STATUT"));
        assertEquals("<HORLOGE> - Avant panne; Hors service: Panne", masquer((String) row(GEN).get("OBSERVATIONS")));
        // le journal reprend tout le cumul comme motif
        assertEquals(List.of("EN_SERVICE>HORS_SERVICE|<HORLOGE> - Avant panne; Hors service: Panne"), historique(GEN));
    }

    @Test
    void une_observation_vide_ou_sur_un_equipement_inconnu_est_refusee() throws Exception {
        golden("observation_vide", post("/" + GEN + "/observations", "{\"observation\":\"  \"}", adminA()));
        golden("observation_absente", post("/" + GEN + "/observations", "{}", adminA()));
        golden("observation_corps_absent", post("/" + GEN + "/observations", null, adminA()));
        golden("observation_inconnu_400", post("/" + INCONNU + "/observations", "{\"observation\":\"x\"}", adminA()));
        golden("observation_autre_centre_400", post("/" + AUTRE_CENTRE + "/observations", "{\"observation\":\"x\"}", adminA()));
        golden("observation_identifiant_invalide", post("/pas-un-uuid/observations", "{\"observation\":\"x\"}", adminA()));
        assertNull(row(GEN).get("OBSERVATIONS"));
    }

    // ------------------------------------------------------------------ transitions : 7 statuts x commandes

    private static final List<com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement> STATUTS =
            List.of(com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement.values());

    /** Joue {@code action} sur un équipement fraîchement posé dans chaque statut ; rend la matrice observée. */
    private ObjectNode matrice(String type, Function<UUID, Resp> action) {
        ArrayNode lignes = mapper.createArrayNode();
        int i = 0;
        for (com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement s : STATUTS) {
            UUID eq = id(60 + i);
            jdbc.update("DELETE FROM gmao_lignes_cout_intervention WHERE intervention_id IN "
                    + "(SELECT id FROM gmao_interventions WHERE equipement_id = ?)", eq);
            jdbc.update("DELETE FROM gmao_interventions WHERE equipement_id = ?", eq);
            jdbc.update("DELETE FROM gmao_equipement_statut_historique WHERE equipement_id = ?", eq);
            jdbc.update("DELETE FROM gmao_equipements WHERE id = ?", eq);
            inserer(eq, "M-" + s.name(), "Matrice " + s, type, s.name(), CENTRE_A, null, 10 + i, null);
            jdbc.update("DELETE FROM notification_evenement WHERE center_id = ?", CENTRE_A);
            Resp r = action.apply(eq);
            ObjectNode l = mapper.createObjectNode();
            l.put("statutInitial", s.name());
            l.put("http", r.status);
            l.put("code", r.status >= 400 ? code(r) : "");
            String detail = "";
            try {
                detail = mapper.readTree(r.raw).path("detail").asString("");
            } catch (RuntimeException ignored) {
                // corps vide
            }
            l.put("detail", detail);
            l.put("statutFinal", (String) row(eq).get("STATUT"));
            ArrayNode h = l.putArray("journal");
            transitions(eq).forEach(h::add);
            ArrayNode a = l.putArray("alertes");
            alertes(CENTRE_A).forEach(a::add);
            lignes.add(l);
            i++;
        }
        ObjectNode racine = mapper.createObjectNode();
        racine.put("typeEquipement", type);
        racine.set("lignes", lignes);
        return racine;
    }

    private Resp horsService(UUID e) {
        return exec(() -> post("/" + e + "/hors-service", "{\"raison\":\"matrice\"}", adminA()));
    }

    private Resp exec(ThrowingSupplier s) {
        try {
            return s.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private interface ThrowingSupplier {
        Resp get() throws Exception;
    }

    @Test
    void matrice_mettre_hors_service_selon_le_statut_de_depart() {
        goldenNode("matrice_hors_service_generateur", matrice("GENERATEUR_DIALYSE", this::horsService));
        goldenNode("matrice_hors_service_station_eau", matrice("STATION_TRAITEMENT_EAU", this::horsService));
    }

    @Test
    void matrice_reactiver_selon_le_statut_de_depart() {
        goldenNode("matrice_reactiver_generateur", matrice("GENERATEUR_DIALYSE",
                e -> exec(() -> post("/" + e + "/reactiver", null, adminA()))));
    }

    @Test
    void matrice_reformer_selon_le_statut_de_depart() {
        goldenNode("matrice_reformer_generateur", matrice("GENERATEUR_DIALYSE",
                e -> exec(() -> post("/" + e + "/reformer", "{\"motif\":\"matrice\"}", reformeur()))));
        goldenNode("matrice_reformer_station_eau", matrice("STATION_TRAITEMENT_EAU",
                e -> exec(() -> post("/" + e + "/reformer", "{\"motif\":\"matrice\"}", reformeur()))));
    }

    @Test
    void matrice_observation_et_modification_selon_le_statut_de_depart() {
        goldenNode("matrice_observation_generateur", matrice("GENERATEUR_DIALYSE",
                e -> exec(() -> post("/" + e + "/observations", "{\"observation\":\"matrice\"}", adminA()))));
        goldenNode("matrice_modification_generateur", matrice("GENERATEUR_DIALYSE",
                e -> exec(() -> put("/" + e, miseAJour("Modifie"), adminA()))));
    }

    @Test
    void matrice_demarrage_d_intervention_vers_en_maintenance_selon_le_statut_de_depart() {
        goldenNode("matrice_demarrage_intervention_vers_en_maintenance", matrice("GENERATEUR_DIALYSE",
                e -> exec(() -> interventionAction(intervention(e, "EN_MAINTENANCE"), "demarrer", null))));
        goldenNode("matrice_demarrage_intervention_vers_en_service", matrice("GENERATEUR_DIALYSE",
                e -> exec(() -> interventionAction(intervention(e, "EN_SERVICE"), "demarrer", null))));
    }

    // ------------------------------------------------------------------ cache ref.generateurs et lecteurs de la table

    @Test
    void les_cinq_ecritures_evincent_le_cache_ref_generateurs_pas_les_lectures_ni_les_observations_ni_les_refus() throws Exception {
        List<Function<Void, Resp>> ecritures = List.of(
                v -> exec(() -> post("", creation("C-1"), adminA())),
                v -> exec(() -> put("/" + GEN, miseAJour("D"), adminA())),
                v -> exec(() -> post("/" + GEN + "/hors-service", "{\"raison\":\"r\"}", adminA())),
                v -> exec(() -> post("/" + GEN + "/reactiver", null, adminA())),
                v -> exec(() -> post("/" + GEN + "/reformer", "{\"motif\":\"m\"}", reformeur())));
        for (Function<Void, Resp> e : ecritures) {
            amorcerCache();
            assertTrue(e.apply(null).status < 300);
            assertFalse(cacheAmorce());
        }
        for (Function<Void, Resp> neutre : List.<Function<Void, Resp>>of(
                v -> exec(() -> get("", adminA())),
                v -> exec(() -> get("/" + GEN, adminA())),
                v -> exec(() -> post("/" + EAU + "/observations", "{\"observation\":\"o\"}", adminA())),
                v -> exec(() -> post("/" + GEN + "/reactiver", null, adminA())),
                v -> exec(() -> post("/" + GEN + "/hors-service", "{\"raison\":\"\"}", adminA())),
                v -> exec(() -> put("/" + INCONNU, miseAJour("D"), adminA())),
                v -> exec(() -> post("", creation("G-01"), adminA())))) {
            amorcerCache();
            neutre.apply(null);
            assertTrue(cacheAmorce());
        }
    }

    @Test
    void le_referentiel_des_generateurs_lit_la_table_apres_chaque_ecriture() throws Exception {
        // lecteur ReferentialRepositoryAdapter : générateurs non supprimés du centre, par code, statut ignoré
        List<String> avant = referentiel.findGenerateurs(new CenterId(CENTRE_A)).stream().map(g -> g.code()).toList();
        assertEquals(List.of("G-01", "G-HS", "G-RF"), avant);
        assertEquals(201, post("", creation("G-00"), adminA()).status);
        assertEquals(200, post("/" + GEN + "/reformer", "{\"motif\":\"m\"}", reformeur()).status);
        List<String> apres = referentiel.findGenerateurs(new CenterId(CENTRE_A)).stream().map(g -> g.code()).toList();
        assertEquals(List.of("G-00", "G-01", "G-HS", "G-RF"), apres, "un générateur réformé reste dans le référentiel");
        assertEquals(List.of("G-01"), referentiel.findGenerateursBySalle(new CenterId(CENTRE_A), SALLE_1).stream()
                .map(g -> g.code()).toList());
        assertEquals(200, put("/" + GEN, "{\"designation\":\"D\",\"salleId\":\"" + SALLE_2 + "\"}", adminA()).status);
        assertEquals(List.of("G-01", "G-HS"), referentiel.findGenerateursBySalle(new CenterId(CENTRE_A), SALLE_2).stream()
                .map(g -> g.code()).toList());
    }

    // ------------------------------------------------------------------ alerte de la Planification

    @Test
    void l_alerte_ne_part_que_si_le_generateur_quitte_le_service_et_pas_d_un_autre_statut_indisponible() throws Exception {
        // EN_SERVICE -> HORS_SERVICE : alerte ; HORS_SERVICE -> EN_SERVICE : non ; EN_SERVICE -> REFORME : alerte
        assertEquals(200, post("/" + GEN + "/hors-service", "{\"raison\":\"a\"}", adminA()).status);
        assertEquals(1, alertes(CENTRE_A).size());
        assertEquals(200, post("/" + GEN + "/reformer", "{\"motif\":\"b\"}", reformeur()).status);
        assertEquals(1, alertes(CENTRE_A).size(), "HORS_SERVICE -> REFORME : pas de seconde alerte");
        // un générateur d'un autre centre alerte le centre concerné
        jdbc.update("UPDATE gmao_equipements SET type = 'GENERATEUR_DIALYSE' WHERE id = ?", AUTRE_CENTRE);
        assertEquals(200, post("/" + AUTRE_CENTRE + "/hors-service", "{\"raison\":\"c\"}", adminB()).status);
        assertEquals(1, alertes(CENTRE_B).size());
        assertEquals(1, alertes(CENTRE_A).size());
    }

    @Test
    void l_alerte_cite_au_plus_cinq_patients_et_ignore_les_patients_sortis() throws Exception {
        for (int i = 0; i < 7; i++) {
            jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                            + "type_patient, created_at, salle_id, position_id, generateur_id, jour_lundi) "
                            + "VALUES (?, ?, ?, ?, 'P', 'M', DATE '2020-01-01', ?, 'NON_VACANCIER', CURRENT_TIMESTAMP, ?, ?, ?, TRUE)",
                    id(70 + i), CENTRE_A, "G3-PAT-" + i, "Nom" + i, "ASS-G3-" + i, SALLE_1, id(80), GEN);
        }
        assertEquals(200, post("/" + GEN + "/hors-service", "{\"raison\":\"a\"}", adminA()).status);
        assertEquals(List.of("G-01/HORS_SERVICE/7/Nom0 P, Nom1 P, Nom2 P, Nom3 P, Nom4 P/roles,ADMIN,SECRETAIRE,"), alertes(CENTRE_A));
    }

    // ------------------------------------------------------------------ clôture d'intervention (côté équipement)

    private UUID intervention(UUID equipement, String etatAvant) throws Exception {
        String corps = "{\"equipementId\":\"" + equipement + "\",\"type\":\"CURATIVE\",\"dateDebut\":\"2026-03-10T08:00:00Z\","
                + "\"description\":\"Panne\",\"etatEquipementAvant\":\"" + etatAvant + "\"}";
        Resp r = send(request(HttpMethod.POST, "/api/v1/gmao/interventions").contentType(MediaType.APPLICATION_JSON)
                .content(corps).with(adminA()));
        assertEquals(201, r.status, r.raw);
        return UUID.fromString(json(r).get("id").asString());
    }

    private Resp interventionAction(UUID intervention, String action, String corps) throws Exception {
        MockHttpServletRequestBuilder b = request(HttpMethod.POST, "/api/v1/gmao/interventions/" + intervention + "/" + action)
                .with(adminA());
        if (corps != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(corps);
        }
        return send(b);
    }

    @Test
    void le_demarrage_puis_la_cloture_d_une_intervention_changent_le_statut_de_l_equipement_avec_journal_et_alerte() throws Exception {
        amorcerCache();
        UUID i = intervention(GEN, "EN_MAINTENANCE");
        assertEquals("EN_SERVICE", row(GEN).get("STATUT"), "la création de l'intervention ne change pas l'état");
        assertEquals(200, interventionAction(i, "demarrer", null).status);
        assertEquals("EN_MAINTENANCE", row(GEN).get("STATUT"));
        assertEquals(List.of("EN_SERVICE>EN_MAINTENANCE|Intervention démarrée"), historique(GEN));
        assertEquals(List.of("G-01/EN_MAINTENANCE/0//roles,ADMIN,SECRETAIRE,"), alertes(CENTRE_A));
        assertFalse(cacheAmorce(), "le démarrage évince ref.generateurs");
        golden("equipement_apres_demarrage_intervention", get("/" + GEN, adminA()));

        Resp fin = interventionAction(i, "terminer",
                "{\"actions\":\"Remplacement\",\"etatEquipementApres\":\"EN_SERVICE\",\"dateFin\":\"2026-03-10T10:00:00Z\"}");
        assertEquals(200, fin.status, fin.raw);
        assertEquals("EN_SERVICE", row(GEN).get("STATUT"));
        assertEquals(List.of("EN_SERVICE>EN_MAINTENANCE|Intervention démarrée", "EN_MAINTENANCE>EN_SERVICE|Intervention terminée"),
                historique(GEN));
        assertEquals(1, alertes(CENTRE_A).size(), "le retour en service n'alerte pas");
        golden("equipement_apres_cloture_intervention", get("/" + GEN, adminA()));

        // l'équipement remis en service par la clôture ne peut plus être « réactivé »
        golden("reactiver_apres_cloture_422", post("/" + GEN + "/reactiver", null, adminA()));
    }

    @Test
    void la_cloture_peut_laisser_l_equipement_en_attente_de_piece_ou_hors_service_et_l_alerte_vise_le_nouveau_statut() throws Exception {
        UUID i = intervention(GEN, "EN_SERVICE");
        assertEquals(200, interventionAction(i, "demarrer", null).status);
        assertEquals(List.of(), historique(GEN), "même état : aucun journal, aucune alerte");
        assertEquals(200, interventionAction(i, "terminer",
                "{\"actions\":\"Commande\",\"etatEquipementApres\":\"EN_ATTENTE_PIECE\",\"dateFin\":\"2026-03-10T10:00:00Z\"}").status);
        assertEquals("EN_ATTENTE_PIECE", row(GEN).get("STATUT"));
        assertEquals(List.of("G-01/EN_ATTENTE_PIECE/0//roles,ADMIN,SECRETAIRE,"), alertes(CENTRE_A));
        // depuis cet état, l'administrateur ne peut pas « réactiver » (seul HORS_SERVICE le permet) mais peut mettre hors service
        assertEquals(422, post("/" + GEN + "/reactiver", null, adminA()).status);
        assertEquals(200, post("/" + GEN + "/hors-service", "{\"raison\":\"pièce introuvable\"}", adminA()).status);
        assertEquals(List.of("EN_SERVICE>EN_ATTENTE_PIECE", "EN_ATTENTE_PIECE>HORS_SERVICE"), transitions(GEN));
        assertEquals(1, alertes(CENTRE_A).size(), "EN_ATTENTE_PIECE -> HORS_SERVICE : pas de nouvelle alerte");
    }

    @Test
    void une_intervention_ne_peut_pas_fixer_l_etat_reforme_ni_desactive_et_un_reforme_ne_bouge_plus() throws Exception {
        UUID i = intervention(GEN, "EN_MAINTENANCE");
        assertEquals(200, interventionAction(i, "demarrer", null).status);
        Resp refus = interventionAction(i, "terminer",
                "{\"actions\":\"x\",\"etatEquipementApres\":\"REFORME\",\"dateFin\":\"2026-03-10T10:00:00Z\"}");
        golden("cloture_etat_reforme_refuse", refus);
        assertEquals("EN_MAINTENANCE", row(GEN).get("STATUT"));
        Resp refusDesactif = interventionAction(i, "terminer",
                "{\"actions\":\"x\",\"etatEquipementApres\":\"DESACTIF\",\"dateFin\":\"2026-03-10T10:00:00Z\"}");
        golden("cloture_etat_desactif_refuse", refusDesactif);

        // équipement réformé : une intervention peut être créée mais son démarrage est refusé (KB-GMAO-03)
        UUID sur = intervention(REFORME, "EN_MAINTENANCE");
        Resp demarrage = interventionAction(sur, "demarrer", null);
        golden("demarrage_intervention_sur_equipement_reforme", demarrage);
        assertEquals("REFORME", row(REFORME).get("STATUT"));
        assertEquals(List.of(), historique(REFORME));
    }

    // ------------------------------------------------------------------ isolation de centre

    @Test
    void chaque_centre_ne_voit_et_ne_pilote_que_ses_equipements() throws Exception {
        golden("isolement_liste_centre_b", get("?size=50", adminB()));
        assertEquals(404, get("/" + GEN, adminB()).status);
        assertEquals(400, put("/" + GEN, miseAJour("Pirate"), adminB()).status);
        assertEquals(400, post("/" + GEN + "/hors-service", "{\"raison\":\"x\"}", adminB()).status);
        assertEquals(400, post("/" + GEN + "/reactiver", null, adminB()).status);
        assertEquals(400, post("/" + GEN + "/observations", "{\"observation\":\"x\"}", adminB()).status);
        assertEquals(400, post("/" + GEN + "/reformer", "{\"motif\":\"x\"}", qui(CENTRE_B, ADMIN_B, "ADMIN", "GMAO_REFORME")).status);
        assertEquals("EN_SERVICE", row(GEN).get("STATUT"));
        assertEquals("Generateur 01", row(GEN).get("DESIGNATION"));
        assertNull(row(GEN).get("OBSERVATIONS"));
        assertEquals(List.of(), alertes(CENTRE_B));
    }

    // ------------------------------------------------------------------ parcours complet et indisponibilité

    @Test
    void parcours_complet_enregistrer_hors_service_reactiver_observer_modifier_reformer() throws Exception {
        Resp cree = post("", creation("PARC-1"), adminA());
        assertEquals(201, cree.status);
        UUID e = UUID.fromString(json(cree).get("id").asString());
        assertEquals(200, post("/" + e + "/hors-service", "{\"raison\":\"Panne pompe\"}", adminA()).status);
        assertEquals(200, post("/" + e + "/reactiver", null, adminA()).status);
        assertEquals(200, post("/" + e + "/observations", "{\"observation\":\"Contrôle OK\"}", adminA()).status);
        assertEquals(200, put("/" + e, "{\"designation\":\"Generateur parcours\",\"localisation\":\"Salle 1\"}", adminA()).status);
        assertEquals(200, post("/" + e + "/reformer", "{\"motif\":\"Obsolète\"}", reformeur()).status);
        golden("parcours_etat_final", get("/" + e, adminA()));
        assertEquals(422, post("/" + e + "/hors-service", "{\"raison\":\"x\"}", adminA()).status);
        assertEquals(422, post("/" + e + "/reactiver", null, adminA()).status);
        assertEquals(422, post("/" + e + "/reformer", "{\"motif\":\"x\"}", reformeur()).status);
        assertEquals(List.of("null>EN_SERVICE", "EN_SERVICE>HORS_SERVICE", "HORS_SERVICE>EN_SERVICE", "EN_SERVICE>REFORME"),
                transitions(e));
        goldenNode("parcours_journal", mapper.valueToTree(historique(e)));
        assertEquals(2, alertes(CENTRE_A).size(), "une alerte à la panne, une à la réforme");
    }

    @Test
    void l_indisponibilite_calculee_sur_le_journal_ne_compte_que_les_statuts_de_panne_et_pas_la_reforme() throws Exception {
        Resp cree = post("", creation("INDISPO"), adminA());
        UUID e = UUID.fromString(json(cree).get("id").asString());
        assertEquals(200, post("/" + e + "/hors-service", "{\"raison\":\"Panne\"}", adminA()).status);
        assertEquals(200, post("/" + e + "/reactiver", null, adminA()).status);
        assertEquals(200, post("/" + e + "/hors-service", "{\"raison\":\"Panne 2\"}", adminA()).status);
        assertEquals(200, post("/" + e + "/reformer", "{\"motif\":\"Fin\"}", reformeur()).status);

        List<Journal> h = jdbc.query("SELECT statut_precedent, statut_nouveau, changed_by, changed_at "
                        + "FROM gmao_equipement_statut_historique WHERE equipement_id = ? ORDER BY changed_at",
                (rs, i) -> new Journal(rs.getString("statut_precedent"), rs.getString("statut_nouveau"),
                        rs.getObject("changed_by", UUID.class), rs.getObject("changed_at", OffsetDateTime.class)), e);
        assertEquals(5, h.size());
        OffsetDateTime debut = h.get(0).le();
        OffsetDateTime panne1 = h.get(1).le();
        OffsetDateTime remise = h.get(2).le();
        OffsetDateTime panne2 = h.get(3).le();
        OffsetDateTime reforme = h.get(4).le();
        Duration attendue = Duration.between(panne1, remise).plus(Duration.between(panne2, reforme));
        List<ChangementDeStatut> changements = h.stream().map(j -> new ChangementDeStatut(
                j.precedent() == null ? null : com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement.valueOf(j.precedent()),
                com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement.valueOf(j.nouveau()), null, null,
                j.le().toInstant())).toList();
        Duration calculee = CalculDIndisponibilite.calculer(changements,
                com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement.REFORME, debut.toInstant(),
                reforme.plusDays(30).toInstant());
        assertEquals(attendue, calculee, "la réforme met fin à l'indisponibilité et ne compte pas elle-même");
        assertEquals("EN_SERVICE", h.get(0).nouveau());
        assertNull(h.get(0).precedent());
        assertEquals(ADMIN_A, h.get(1).par());
        assertEquals(REFORMEUR, h.get(4).par());
    }

    private record Journal(String precedent, String nouveau, UUID par, OffsetDateTime le) {
    }
}
