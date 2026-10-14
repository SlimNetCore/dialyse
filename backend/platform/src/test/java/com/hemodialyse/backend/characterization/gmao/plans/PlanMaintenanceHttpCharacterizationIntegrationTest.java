package com.hemodialyse.backend.characterization.gmao.plans;

import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.FrequenceMaintenance;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.StatutPlan;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Filet de caractérisation de la slice gmao-g1-plans-maintenance : les 9 endpoints
 * {@code /api/v1/gmao/plans-maintenance} observés au niveau HTTP (MockMvc + chaîne de sécurité réelle + JPA réel).
 * Il décrit le comportement ACTUEL, y compris les défauts (voir .ddd/safety-net/known-bugs.md, KB-GMAO-xx).
 *
 * Cette suite est la référence de la slice : elle tourne contre le NOUVEAU code (contexte contexts/gmao, mêmes URLs, mêmes
 * tables). Écart décidé : KB-GMAO-14, équipement inconnu refusé en 400 EQUIPEMENT_INCONNU (golden *_nouveau_code).
 *
 * Non-déterminisme : l'horloge système et {@code UUID.randomUUID()} ne sont pas substituables sans seam ; ils sont
 * normalisés dans les assertions (fenêtre [avant, après] la requête, jetons NOW / NOW+1M ..., alias d'UUID).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PlanMaintenanceHttpCharacterizationIntegrationTest {

    protected static final String BASE = "/api/v1/gmao/plans-maintenance";

    protected static final UUID CENTRE_A = UUID.fromString("99995000-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99995000-0000-0000-0000-00000000000b");
    protected static final UUID EQUIP_1 = UUID.fromString("99995000-0000-0000-0000-0000000000e1");
    protected static final UUID EQUIP_2 = UUID.fromString("99995000-0000-0000-0000-0000000000e2");
    protected static final UUID EQUIP_3 = UUID.fromString("99995000-0000-0000-0000-0000000000e3");
    private static final UUID EQUIP_INCONNU = UUID.fromString("99995000-0000-0000-0000-0000000000e9");
    protected static final UUID ADMIN_A = UUID.fromString("99995000-0000-0000-0000-0000000000f1");
    private static final UUID ADMIN_B = UUID.fromString("99995000-0000-0000-0000-0000000000f2");
    private static final UUID TECH = UUID.fromString("99995000-0000-0000-0000-0000000000f3");

    private static final UUID P_HEBDO_ACTIF = id(1);
    private static final UUID P_MENSUEL_EN_RETARD = id(2);
    private static final UUID P_ANNUEL_INACTIF = id(3);
    private static final UUID P_ARCHIVE = id(4);
    private static final UUID P_SUPPRIME = id(5);
    private static final UUID P_AUTRE_CENTRE = id(6);
    private static final UUID P_INCONNU = id(99);

    private static final OffsetDateTime FUTUR = OffsetDateTime.of(2099, 1, 5, 8, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime PASSE = OffsetDateTime.of(2020, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime CREE = OffsetDateTime.of(2019, 6, 1, 9, 0, 0, 0, ZoneOffset.UTC);

    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern INSTANT_RE = Pattern.compile("^\\d{4}-\\d\\d-\\d\\dT.*");
    private static final Pattern ENUM_RE = Pattern.compile("No enum constant (?:\\w+\\.)+(\\w+)\\.(\\w+)");

    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper mapper;

    protected MockMvc mvc;

    // ------------------------------------------------------------------ semis et nettoyage

    protected MockMvc construireMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    protected MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn();
    }

    protected String tablePlans() {
        return "gmao_plans_maintenance";
    }

    /** Vide les tables des centres de test. */
    protected void nettoyerTables() {
        jdbc.update("DELETE FROM gmao_plans_maintenance WHERE centre_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM gmao_equipements WHERE id IN (?, ?, ?)", EQUIP_1, EQUIP_2, EQUIP_3);
        if (PG16) {
            jdbc.update("DELETE FROM app_user WHERE id IN (?, ?, ?)", ADMIN_A, ADMIN_B, TECH);
            jdbc.update("DELETE FROM centers WHERE id IN (?, ?)", CENTRE_A, CENTRE_B);
        }
    }

    /**
     * Rejeu sur un vrai PostgreSQL 16 ({@code -Dgmao.pg16=true}, voir backend/scripts/gmao-pg16-rejeu.sh) : le schéma Flyway
     * porte les clés étrangères vers centers / app_user que H2 (DDL Hibernate) n'a pas. Les équipements sont créés dans
     * les deux cas (le contexte vérifie leur existence) ; EQUIP_INCONNU reste volontairement absent (KB-GMAO-14).
     */
    protected static final boolean PG16 = Boolean.getBoolean("gmao.pg16");

    protected void preparerReferentiels() {
        if (PG16) {
            for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
                jdbc.update("INSERT INTO centers (id, societe_id) VALUES (?, ?)", centre, centre);
            }
            for (UUID u : List.of(ADMIN_A, ADMIN_B, TECH)) {
                jdbc.update("INSERT INTO app_user (id, active, password_hash, username, must_change_password) "
                        + "VALUES (?, TRUE, 'x', ?, FALSE)", u, "pg16-" + u);
            }
        }
        int n = 0;
        for (UUID e : List.of(EQUIP_1, EQUIP_2, EQUIP_3)) {
            jdbc.update("INSERT INTO gmao_equipements (id, code, designation, type, date_installation, centre_id, statut, "
                            + "cree_par, date_creation) VALUES (?, ?, 'Equipement de test', 'OSMOSEUR', CURRENT_TIMESTAMP, ?, 'EN_SERVICE', ?, CURRENT_TIMESTAMP)",
                    e, "PG16-EQ-" + (++n), e.equals(EQUIP_3) ? CENTRE_B : CENTRE_A, ADMIN_A);
        }
    }

    protected static UUID id(int n) {
        return UUID.fromString(String.format("99995000-0000-0000-0000-0000000001%02d", n));
    }

    // ------------------------------------------------------------------ harnais

    private static RequestPostProcessor admin(UUID centre, UUID userId) {
        return user(UserPrincipal.create(userId.toString(), centre.toString(), "admin", "", List.of("ADMIN"), true));
    }

    protected static RequestPostProcessor adminA() {
        return admin(CENTRE_A, ADMIN_A);
    }

    private static RequestPostProcessor nonAdmin() {
        return user(UserPrincipal.create(TECH.toString(), CENTRE_A.toString(), "tech", "", List.of("TECHNICIEN"), true));
    }

    @BeforeEach
    void setUp() {
        mvc = construireMvc();
        cleanup();
        preparerReferentiels();
        seed(P_HEBDO_ACTIF, EQUIP_1, CENTRE_A, "Controle hebdomadaire osmoseur", FrequenceMaintenance.HEBDOMADAIRE,
                StatutPlan.ACTIF, FUTUR, 0, null);
        seed(P_MENSUEL_EN_RETARD, EQUIP_1, CENTRE_A, "Desinfection mensuelle generateur",
                FrequenceMaintenance.MENSUELLE, StatutPlan.ACTIF, PASSE, 3, PASSE.minusDays(30));
        seed(P_ANNUEL_INACTIF, EQUIP_2, CENTRE_A, "Revision annuelle", FrequenceMaintenance.ANNUELLE,
                StatutPlan.INACTIF, PASSE, 1, PASSE.minusYears(1));
        seed(P_ARCHIVE, EQUIP_2, CENTRE_A, "Plan archive", FrequenceMaintenance.SEMESTRIELLE,
                StatutPlan.ARCHIVE, PASSE, 0, null);
        seed(P_SUPPRIME, EQUIP_1, CENTRE_A, "Plan supprime", FrequenceMaintenance.TRIMESTRIELLE,
                StatutPlan.ACTIF, PASSE, 0, null);
        jdbc.update("UPDATE gmao_plans_maintenance SET deleted_at = ? WHERE id = ?", PASSE.plusDays(1), P_SUPPRIME);
        seed(P_AUTRE_CENTRE, EQUIP_3, CENTRE_B, "Plan autre centre", FrequenceMaintenance.BIMENSUELLE,
                StatutPlan.ACTIF, PASSE, 0, null);
    }

    @AfterEach
    void cleanup() {
        nettoyerTables();
    }

    private void seed(UUID id, UUID equipement, UUID centre, String designation, FrequenceMaintenance frequence,
                      StatutPlan statut, OffsetDateTime prochaine, int nbExecutions, OffsetDateTime derniere) {
        jdbc.update("INSERT INTO gmao_plans_maintenance (id, equipement_id, centre_id, designation, description, frequence, "
                        + "statut, prochaine_date_prevue, derniere_date_execution, nombre_executions, tachesaeffectuer, "
                        + "date_creation, date_modification, cree_par, modifie_par) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, equipement, centre, designation, "Description " + designation, frequence.name(), statut.name(), prochaine,
                derniere, nbExecutions, "Taches " + designation, CREE, null, ADMIN_A, null);
    }

    /** Réponse HTTP + fenêtre temporelle de l'appel, pour normaliser l'horloge. */
    record Resp(int status, String contentType, String raw, Instant t0, Instant t1) {
    }

    private Resp send(MockHttpServletRequestBuilder request) throws Exception {
        Instant t0 = Instant.now();
        MockHttpServletResponse r = perform(request).getResponse();
        Instant t1 = Instant.now();
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8), t0, t1);
    }

    private Resp postJson(String path, String json, RequestPostProcessor who) throws Exception {
        return send(post(BASE + path).contentType(MediaType.APPLICATION_JSON).content(json).with(who));
    }

    private Resp postNoBody(String path, RequestPostProcessor who) throws Exception {
        return send(post(BASE + path).with(who));
    }

    private Resp getAs(String path, RequestPostProcessor who) throws Exception {
        return send(get(BASE + path).with(who));
    }

    private static final Map<String, String> OFFSETS = new LinkedHashMap<>();

    static {
        OFFSETS.put("NOW", "0");
        OFFSETS.put("NOW+1W", "1W");
        OFFSETS.put("NOW+15D", "15D");
        OFFSETS.put("NOW+1M", "1M");
        OFFSETS.put("NOW+3M", "3M");
        OFFSETS.put("NOW+6M", "6M");
        OFFSETS.put("NOW+1Y", "1Y");
    }

    private static OffsetDateTime plus(OffsetDateTime base, String off) {
        return switch (off) {
            case "0" -> base;
            case "1W" -> base.plusWeeks(1);
            case "15D" -> base.plusDays(15);
            case "1M" -> base.plusMonths(1);
            case "3M" -> base.plusMonths(3);
            case "6M" -> base.plusMonths(6);
            case "1Y" -> base.plusYears(1);
            default -> throw new IllegalArgumentException(off);
        };
    }

    /** Normalise : UUID -> alias stable, instants relatifs à l'horloge -> jetons, listes triées par désignation. */
    private final class Scrubber {
        private final Resp resp;
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private int unknown = 0;

        Scrubber(Resp resp) {
            this.resp = resp;
            alias(CENTRE_A, "CENTRE_A");
            alias(CENTRE_B, "CENTRE_B");
            alias(EQUIP_1, "EQUIP_1");
            alias(EQUIP_2, "EQUIP_2");
            alias(EQUIP_3, "EQUIP_3");
            alias(EQUIP_INCONNU, "EQUIP_INCONNU");
            alias(ADMIN_A, "ADMIN_A");
            alias(ADMIN_B, "ADMIN_B");
            alias(P_HEBDO_ACTIF, "PLAN_HEBDO_ACTIF");
            alias(P_MENSUEL_EN_RETARD, "PLAN_MENSUEL_EN_RETARD");
            alias(P_ANNUEL_INACTIF, "PLAN_ANNUEL_INACTIF");
            alias(P_ARCHIVE, "PLAN_ARCHIVE");
            alias(P_SUPPRIME, "PLAN_SUPPRIME");
            alias(P_AUTRE_CENTRE, "PLAN_AUTRE_CENTRE");
            alias(P_INCONNU, "PLAN_INCONNU");
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
            String out = sb.toString();
            if (INSTANT_RE.matcher(s).matches()) {
                try {
                    Instant i = OffsetDateTime.parse(s).toInstant();
                    OffsetDateTime a = resp.t0.atOffset(ZoneOffset.UTC);
                    OffsetDateTime b = resp.t1.atOffset(ZoneOffset.UTC);
                    for (Map.Entry<String, String> e : OFFSETS.entrySet()) {
                        Instant lo = plus(a, e.getValue()).toInstant();
                        Instant hi = plus(b, e.getValue()).toInstant();
                        if (!i.isBefore(lo) && !i.isAfter(hi)) {
                            return "<" + e.getKey() + ">";
                        }
                    }
                } catch (RuntimeException ignored) {
                    // pas un instant
                }
            }
            return out;
        }

        JsonNode scrub(JsonNode n) {
            if (n.isString()) return mapper.getNodeFactory().stringNode(scrubString(n.asString()));
            if (n.isObject()) {
                ObjectNode o = mapper.createObjectNode();
                boolean erreurInterne = "INTERNAL_ERROR".equals(n.path("code").asString(""));
                n.properties().forEach(e -> o.set(e.getKey(), erreurInterne && e.getKey().equals("detail")
                        ? mapper.getNodeFactory().stringNode("<message technique Spring/Jackson non verrouille>")
                        : scrub(e.getValue())));
                return o;
            }
            if (n.isArray()) {
                List<JsonNode> items = new ArrayList<>();
                n.forEach(x -> items.add(x));
                items.sort(Comparator.comparing(x -> x.path("designation").asString("")));
                ArrayNode a = mapper.createArrayNode();
                items.forEach(x -> a.add(scrub(x)));
                return a;
            }
            return n;
        }

        String render() {
            ObjectNode root = mapper.createObjectNode();
            root.put("status", resp.status);
            root.put("contentType", resp.contentType);
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

    void golden(String name, Resp r) {
        GoldenMaster.verify(name, new Scrubber(r).render());
    }

    JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT * FROM " + tablePlans() + " WHERE id = ?", id);
    }

    private static String body(UUID equip, String designation, String description, String frequence, String date,
                               String taches) {
        return "{\"equipementId\":" + q(equip) + ",\"designation\":" + q(designation) + ",\"description\":"
                + q(description) + ",\"frequence\":" + q(frequence) + ",\"prochaineDatePrevue\":" + q(date)
                + ",\"tachesAEffectuer\":" + q(taches) + "}";
    }

    private static String q(Object o) {
        return o == null ? "null" : "\"" + o + "\"";
    }

    // ------------------------------------------------------------------ autorisation

    @Test
    void les_neuf_routes_sont_refusees_en_403_a_un_utilisateur_non_administrateur() throws Exception {
        String json = body(EQUIP_1, "X", null, "MENSUELLE", "2099-01-01T00:00:00Z", null);
        String react = "{\"nouvelleDatePrevue\":\"2099-01-01T00:00:00Z\"}";
        List<Resp> refus = List.of(
                send(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json).with(nonAdmin())),
                getAs("/" + P_HEBDO_ACTIF, nonAdmin()),
                getAs("", nonAdmin()),
                getAs("/actifs", nonAdmin()),
                getAs("/en-retard", nonAdmin()),
                getAs("/equipement/" + EQUIP_1, nonAdmin()),
                postNoBody("/" + P_HEBDO_ACTIF + "/executer", nonAdmin()),
                postNoBody("/" + P_HEBDO_ACTIF + "/desactiver", nonAdmin()),
                postJson("/" + P_ANNUEL_INACTIF + "/reactiver", react, nonAdmin()));
        for (Resp r : refus) {
            assertEquals(403, r.status, r.raw);
            assertTrue(r.raw.contains("ACCESS_DENIED"), r.raw);
        }
        golden("refus_non_admin_403", refus.get(0));
        assertEquals("ACTIF", row(P_HEBDO_ACTIF).get("STATUT"));
        assertEquals(0, ((Number) row(P_HEBDO_ACTIF).get("NOMBRE_EXECUTIONS")).intValue());
    }

    @Test
    void une_requete_anonyme_est_refusee_sans_toucher_aux_donnees() throws Exception {
        Resp lecture = send(get(BASE));
        Resp ecriture = send(post(BASE + "/" + P_HEBDO_ACTIF + "/desactiver"));
        golden("anonyme_lecture", lecture);
        golden("anonyme_ecriture", ecriture);
        assertEquals("ACTIF", row(P_HEBDO_ACTIF).get("STATUT"));
    }

    // ------------------------------------------------------------------ POST (creer)

    @Test
    void un_administrateur_etablit_un_plan_actif_rattache_a_son_centre() throws Exception {
        Resp r = postJson("", body(EQUIP_1, "Verification pompes", "Pompes a sang", "TRIMESTRIELLE",
                "2030-03-01T08:00:00Z", "Graisser; controler"), adminA());
        assertEquals(201, r.status);
        golden("etablir_plan_201", r);

        UUID newId = UUID.fromString(json(r).get("id").asString());
        Map<String, Object> db = row(newId);
        assertEquals("ACTIF", db.get("STATUT"));
        assertEquals("TRIMESTRIELLE", db.get("FREQUENCE"));
        assertEquals(CENTRE_A, db.get("CENTRE_ID"));
        assertEquals(ADMIN_A, db.get("CREE_PAR"));
        assertEquals(0, ((Number) db.get("NOMBRE_EXECUTIONS")).intValue());
        assertNull(db.get("MODIFIE_PAR"));
        assertNull(db.get("DATE_MODIFICATION"));
        assertNull(db.get("DERNIERE_DATE_EXECUTION"));
        assertNull(db.get("DELETED_AT"));
    }

    @Test
    void le_centre_du_plan_vient_de_l_utilisateur_connecte_pas_de_la_requete() throws Exception {
        Resp r = postJson("", body(EQUIP_3, "Plan cree par admin B", null, "ANNUELLE", "2031-01-01T00:00:00Z", null),
                admin(CENTRE_B, ADMIN_B));
        assertEquals(201, r.status);
        assertEquals(CENTRE_B.toString(), json(r).get("centreId").asString());
        assertEquals(ADMIN_B.toString(), json(r).get("creePar").asString());
        assertEquals(0, mvcListSize("", adminA(), "Plan cree par admin B"));
    }

    int mvcListSize(String path, RequestPostProcessor who, String designation) throws Exception {
        int n = 0;
        for (JsonNode x : json(getAs(path, who))) if (designation.equals(x.get("designation").asString())) n++;
        return n;
    }

    @Test
    void les_six_frequences_sont_acceptees_a_la_creation_sans_champs_optionnels() throws Exception {
        for (FrequenceMaintenance f : FrequenceMaintenance.values()) {
            Resp r = postJson("", body(EQUIP_1, "Plan " + f, null, f.name(), "2030-01-01T00:00:00Z", null), adminA());
            assertEquals(201, r.status, f.name());
            assertEquals(f.name(), json(r).get("frequence").asString());
            assertTrue(json(r).get("description").isNull());
            assertTrue(json(r).get("tachesAEffectuer").isNull());
        }
    }

    @Test
    void creation_avec_designation_de_255_caracteres_acceptee_et_256_refusee_en_400() throws Exception {
        assertEquals(201, postJson("", body(EQUIP_1, "d".repeat(255), null, "MENSUELLE", "2030-01-01T00:00:00Z", null),
                adminA()).status);
        Resp r = postJson("", body(EQUIP_1, "d".repeat(256), null, "MENSUELLE", "2030-01-01T00:00:00Z", null), adminA());
        assertEquals(400, r.status);
        golden("etablir_designation_256_400", r);
    }

    @Test
    void creation_invalide_est_refusee_en_400_validation_sans_rien_enregistrer() throws Exception {
        int avant = countPlans();
        Resp sansDesignation = postJson("", body(EQUIP_1, "   ", null, "MENSUELLE", "2030-01-01T00:00:00Z", null), adminA());
        Resp sansEquipement = postJson("", body(null, "X", null, "MENSUELLE", "2030-01-01T00:00:00Z", null), adminA());
        Resp sansFrequence = postJson("", body(EQUIP_1, "X", null, "", "2030-01-01T00:00:00Z", null), adminA());
        Resp sansDate = postJson("", body(EQUIP_1, "X", null, "MENSUELLE", null, null), adminA());
        for (Resp r : List.of(sansDesignation, sansEquipement, sansFrequence, sansDate)) {
            assertEquals(400, r.status, r.raw);
            assertTrue(r.raw.contains("VALIDATION_ERROR"), r.raw);
        }
        golden("etablir_designation_blanche_400", sansDesignation);
        golden("etablir_equipement_absent_400", sansEquipement);
        golden("etablir_frequence_vide_400", sansFrequence);
        golden("etablir_date_absente_400", sansDate);
        assertEquals(avant, countPlans());
    }

    @Test
    void une_frequence_inconnue_ou_en_minuscules_est_refusee_en_400_bad_request() throws Exception {
        Resp inconnue = postJson("", body(EQUIP_1, "X", null, "QUOTIDIENNE", "2030-01-01T00:00:00Z", null), adminA());
        Resp minuscules = postJson("", body(EQUIP_1, "X", null, "mensuelle", "2030-01-01T00:00:00Z", null), adminA());
        assertEquals(400, inconnue.status);
        assertEquals(400, minuscules.status);
        golden("etablir_frequence_inconnue_400", inconnue);
        golden("etablir_frequence_minuscules_400", minuscules);
    }

    @Test
    void un_json_illisible_ou_absent_est_traite_par_le_handler_generique() throws Exception {
        Resp illisible = postJson("", "{pas du json", adminA());
        Resp absent = send(post(BASE).contentType(MediaType.APPLICATION_JSON).with(adminA()));
        Resp dateIllisible = postJson("", body(EQUIP_1, "X", null, "MENSUELLE", "pas-une-date", null), adminA());
        golden("etablir_json_illisible", illisible);
        golden("etablir_corps_absent", absent);
        golden("etablir_date_illisible", dateIllisible);
    }

    @Test
    void creation_refusee_pour_un_equipement_inconnu_et_permise_dans_le_passe_pour_un_equipement_connu() throws Exception {
        // KB-GMAO-14 (écart décidé) : le legacy répondait 500 (clé étrangère PostgreSQL) ou 201 (H2) ; le contexte refuse
        // proprement l'équipement inconnu (400 EQUIPEMENT_INCONNU) et n'enregistre rien. La date passée reste permise
        // pour un équipement connu (second appel).
        Resp r = postJson("", body(EQUIP_INCONNU, "Plan equipement inconnu", null, "MENSUELLE",
                "2001-01-01T00:00:00Z", null), adminA());
        assertEquals(400, r.status);
        golden("etablir_equipement_inconnu_refuse_400_nouveau_code", r);
        assertEquals(0, mvcListSize("/en-retard", adminA(), "Plan equipement inconnu"));
        Resp datePassee = postJson("", body(EQUIP_1, "Plan date passee", null, "MENSUELLE",
                "2001-01-01T00:00:00Z", null), adminA());
        assertEquals(201, datePassee.status);
        golden("etablir_date_passee_equipement_connu_201", datePassee);
        assertEquals(1, mvcListSize("/en-retard", adminA(), "Plan date passee"));
    }

    // ------------------------------------------------------------------ GET /{id}

    @Test
    void consulter_un_plan_existant_retourne_tous_ses_champs() throws Exception {
        Resp r = getAs("/" + P_MENSUEL_EN_RETARD, adminA());
        assertEquals(200, r.status);
        golden("consulter_plan_200", r);
    }

    @Test
    void consulter_un_plan_inconnu_retourne_404_sans_corps() throws Exception {
        Resp r = getAs("/" + P_INCONNU, adminA());
        assertEquals(404, r.status);
        golden("consulter_plan_inconnu_404", r);
    }

    @Test
    void consulter_avec_un_identifiant_non_uuid_retourne_400() throws Exception {
        golden("consulter_id_invalide_400", getAs("/pas-un-uuid", adminA()));
    }

    @Test
    void consulter_ne_filtre_pas_par_suppression_logique() throws Exception {
        // KB-GMAO-17 : la suppression logique n'est pas appliquée par id.
        Resp supprime = getAs("/" + P_SUPPRIME, adminA());
        assertEquals(200, supprime.status);
        golden("consulter_plan_supprime_logiquement_200", supprime);
    }

    @Test
    void consulter_un_plan_d_un_autre_centre_est_traite_comme_inexistant() throws Exception {
        // KB-GMAO-15 corrigé (décision utilisateur 2026-10-12) : le legacy répondait 200 (écart accepté).
        Resp autreCentre = getAs("/" + P_MENSUEL_EN_RETARD, admin(CENTRE_B, ADMIN_B));
        assertEquals(404, autreCentre.status);
        golden("consulter_plan_d_un_autre_centre_404_nouveau_code", autreCentre);
    }

    // ------------------------------------------------------------------ listes

    @Test
    void lister_les_plans_du_centre_inclut_inactifs_et_archives_mais_ni_supprimes_ni_autre_centre() throws Exception {
        Resp r = getAs("", adminA());
        assertEquals(200, r.status);
        assertEquals(4, json(r).size());
        golden("lister_plans_centre_A", r);
        golden("lister_plans_centre_B", getAs("", admin(CENTRE_B, ADMIN_B)));
    }

    @Test
    void lister_les_plans_actifs_exclut_inactifs_archives_supprimes_et_autres_centres() throws Exception {
        Resp r = getAs("/actifs", adminA());
        assertEquals(200, r.status);
        golden("lister_plans_actifs_centre_A", r);
    }

    @Test
    void lister_les_plans_en_retard_ne_retient_que_les_actifs_echus_du_centre() throws Exception {
        Resp r = getAs("/en-retard", adminA());
        assertEquals(200, r.status);
        golden("lister_plans_en_retard_centre_A", r);
        Resp b = getAs("/en-retard", admin(CENTRE_B, ADMIN_B));
        golden("lister_plans_en_retard_centre_B", b);
    }

    @Test
    void un_centre_sans_plan_obtient_une_liste_vide() throws Exception {
        Resp r = getAs("", admin(UUID.fromString("99995000-0000-0000-0000-00000000000c"), ADMIN_A));
        assertEquals(200, r.status);
        assertEquals("[]", r.raw);
    }

    @Test
    void lister_par_equipement_ignore_le_statut_mais_pas_la_suppression_logique_ni_le_centre() throws Exception {
        Resp e1 = getAs("/equipement/" + EQUIP_1, adminA());
        // KB-GMAO-15 corrigé (décision utilisateur 2026-10-12) : le legacy listait les plans du centre A pour le centre B.
        Resp e2 = getAs("/equipement/" + EQUIP_2, admin(CENTRE_B, ADMIN_B));
        Resp inconnu = getAs("/equipement/" + EQUIP_INCONNU, adminA());
        assertEquals(200, e1.status);
        golden("lister_plans_equipement_1", e1);
        assertEquals("[]", e2.raw);
        golden("lister_plans_equipement_2_vu_du_centre_B_nouveau_code", e2);
        assertEquals("[]", inconnu.raw);
        golden("lister_plans_equipement_id_invalide_400", getAs("/equipement/xyz", adminA()));
    }

    // ------------------------------------------------------------------ POST /{id}/executer

    @Test
    void executer_un_plan_actif_reporte_la_prochaine_date_a_maintenant_plus_la_frequence() throws Exception {
        // KB-GMAO-12 : la base du calcul est l'instant de l'exécution, pas la date prévue (ici 2099).
        Resp r = postNoBody("/" + P_HEBDO_ACTIF + "/executer", adminA());
        assertEquals(200, r.status);
        golden("executer_plan_hebdomadaire_200", r);
        Map<String, Object> db = row(P_HEBDO_ACTIF);
        assertEquals(1, ((Number) db.get("NOMBRE_EXECUTIONS")).intValue());
        assertEquals(ADMIN_A, db.get("MODIFIE_PAR"));
        assertEquals("ACTIF", db.get("STATUT"));
    }

    @Test
    void executer_calcule_la_prochaine_date_pour_chacune_des_six_frequences() throws Exception {
        Map<FrequenceMaintenance, String> attendu = Map.of(
                FrequenceMaintenance.HEBDOMADAIRE, "<NOW+1W>",
                FrequenceMaintenance.BIMENSUELLE, "<NOW+15D>",
                FrequenceMaintenance.MENSUELLE, "<NOW+1M>",
                FrequenceMaintenance.TRIMESTRIELLE, "<NOW+3M>",
                FrequenceMaintenance.SEMESTRIELLE, "<NOW+6M>",
                FrequenceMaintenance.ANNUELLE, "<NOW+1Y>");
        int n = 20;
        for (FrequenceMaintenance f : FrequenceMaintenance.values()) {
            UUID pid = id(n++);
            seed(pid, EQUIP_1, CENTRE_A, "Freq " + f, f, StatutPlan.ACTIF, PASSE, 0, null);
            Resp r = postNoBody("/" + pid + "/executer", adminA());
            assertEquals(200, r.status, f.name());
            Scrubber s = new Scrubber(r);
            JsonNode scrubbed = s.scrub(json(r));
            assertEquals(attendu.get(f), scrubbed.get("prochaineDatePrevue").asString(), f.name());
            assertEquals("<NOW>", scrubbed.get("derniereDateExecution").asString(), f.name());
            assertEquals("<NOW>", scrubbed.get("dateModification").asString(), f.name());
            assertEquals(1, scrubbed.get("nombreExecutions").asInt());
        }
    }

    @Test
    void executer_deux_fois_incremente_le_compteur_et_garde_le_plan_actif() throws Exception {
        postNoBody("/" + P_MENSUEL_EN_RETARD + "/executer", adminA());
        Resp r = postNoBody("/" + P_MENSUEL_EN_RETARD + "/executer", adminA());
        assertEquals(200, r.status);
        golden("executer_plan_en_retard_deux_fois_200", r);
        assertEquals(0, mvcListSize("/en-retard", adminA(), "Desinfection mensuelle generateur"));
    }

    @Test
    void executer_un_plan_inactif_ou_archive_est_refuse_en_422_et_ne_change_rien() throws Exception {
        Resp inactif = postNoBody("/" + P_ANNUEL_INACTIF + "/executer", adminA());
        Resp archive = postNoBody("/" + P_ARCHIVE + "/executer", adminA());
        assertEquals(422, inactif.status);
        assertEquals(422, archive.status);
        golden("executer_plan_inactif_422", inactif);
        golden("executer_plan_archive_422", archive);
        assertEquals(1, ((Number) row(P_ANNUEL_INACTIF).get("NOMBRE_EXECUTIONS")).intValue());
        assertNull(row(P_ARCHIVE).get("MODIFIE_PAR"));
    }

    @Test
    void executer_un_plan_inconnu_ou_un_id_invalide_retourne_400_et_non_404() throws Exception {
        // KB-GMAO-16 : « Plan non trouvé » est une IllegalArgumentException traduite en 400 (GET /{id} répond 404).
        golden("executer_plan_inconnu_400", postNoBody("/" + P_INCONNU + "/executer", adminA()));
        golden("executer_id_invalide_400", postNoBody("/xyz/executer", adminA()));
    }

    @Test
    void les_commandes_sur_un_plan_d_un_autre_centre_sont_refusees_comme_un_plan_inconnu() throws Exception {
        // KB-GMAO-15 corrigé (décision utilisateur 2026-10-12) : le legacy répondait 200 et modifiait le plan.
        // Même réponse que « plan inconnu » (400, KB-GMAO-16) ; le plan n'est pas modifié.
        Resp executer = postNoBody("/" + P_AUTRE_CENTRE + "/executer", adminA());
        Resp desactiver = postNoBody("/" + P_AUTRE_CENTRE + "/desactiver", adminA());
        Resp reactiver = postJson("/" + P_AUTRE_CENTRE + "/reactiver", "{\"nouvelleDatePrevue\":\"2031-05-01T07:30:00Z\"}", adminA());
        assertEquals(400, executer.status);
        assertEquals(400, desactiver.status);
        assertEquals(400, reactiver.status);
        golden("executer_plan_d_un_autre_centre_400_nouveau_code", executer);
        golden("desactiver_plan_d_un_autre_centre_400_nouveau_code", desactiver);
        golden("reactiver_plan_d_un_autre_centre_400_nouveau_code", reactiver);
        assertNull(row(P_AUTRE_CENTRE).get("MODIFIE_PAR"));
        assertEquals("ACTIF", row(P_AUTRE_CENTRE).get("STATUT"));
        assertEquals(0, ((Number) row(P_AUTRE_CENTRE).get("NOMBRE_EXECUTIONS")).intValue());
    }

    @Test
    void executer_un_plan_supprime_logiquement_est_permis_et_le_ressuscite() throws Exception {
        // KB-GMAO-17 : l'adaptateur ne reporte pas deleted_at vers l'entité ; toute commande sur un plan supprimé
        // logiquement le fait réapparaître dans les listes (deleted_at remis à NULL).
        assertEquals(0, mvcListSize("", adminA(), "Plan supprime"));
        Resp r = postNoBody("/" + P_SUPPRIME + "/executer", adminA());
        assertEquals(200, r.status);
        golden("executer_plan_supprime_logiquement_200", r);
        assertNull(row(P_SUPPRIME).get("DELETED_AT"));
        assertEquals(1, mvcListSize("", adminA(), "Plan supprime"));
    }

    // ------------------------------------------------------------------ POST /{id}/desactiver

    @Test
    void desactiver_un_plan_actif_le_rend_inactif_et_conserve_sa_prochaine_date() throws Exception {
        Resp r = postNoBody("/" + P_MENSUEL_EN_RETARD + "/desactiver", adminA());
        assertEquals(200, r.status);
        golden("desactiver_plan_actif_200", r);
        assertEquals("INACTIF", row(P_MENSUEL_EN_RETARD).get("STATUT"));
        assertEquals(0, mvcListSize("/actifs", adminA(), "Desinfection mensuelle generateur"));
        assertEquals(0, mvcListSize("/en-retard", adminA(), "Desinfection mensuelle generateur"));
        assertEquals(1, mvcListSize("", adminA(), "Desinfection mensuelle generateur"));
    }

    @Test
    void desactiver_un_plan_non_actif_est_refuse_en_422() throws Exception {
        Resp inactif = postNoBody("/" + P_ANNUEL_INACTIF + "/desactiver", adminA());
        Resp archive = postNoBody("/" + P_ARCHIVE + "/desactiver", adminA());
        assertEquals(422, inactif.status);
        assertEquals(422, archive.status);
        golden("desactiver_plan_inactif_422", inactif);
        golden("desactiver_plan_archive_422", archive);
    }

    @Test
    void desactiver_un_plan_inconnu_retourne_400() throws Exception {
        golden("desactiver_plan_inconnu_400", postNoBody("/" + P_INCONNU + "/desactiver", adminA()));
    }

    // ------------------------------------------------------------------ POST /{id}/reactiver

    @Test
    void reactiver_un_plan_inactif_le_remet_actif_avec_la_nouvelle_date() throws Exception {
        Resp r = postJson("/" + P_ANNUEL_INACTIF + "/reactiver", "{\"nouvelleDatePrevue\":\"2031-05-01T07:30:00Z\"}",
                adminA());
        assertEquals(200, r.status);
        golden("reactiver_plan_inactif_200", r);
        assertEquals("ACTIF", row(P_ANNUEL_INACTIF).get("STATUT"));
        assertEquals(1, mvcListSize("/actifs", adminA(), "Revision annuelle"));
    }

    @Test
    void les_dates_recues_avec_un_decalage_horaire_sont_restituees_normalisees_en_utc() throws Exception {
        // ajout au filet lors du build de la parité : le décalage horaire d'entrée (+01:00, +02:00) est-il écho ou normalisé ?
        Resp etabli = postJson("", body(EQUIP_1, "Plan decale", null, "MENSUELLE", "2030-03-01T09:00:00+01:00", null), adminA());
        Resp repris = postJson("/" + P_ANNUEL_INACTIF + "/reactiver", "{\"nouvelleDatePrevue\":\"2031-05-01T09:30:00+02:00\"}",
                adminA());
        assertEquals(201, etabli.status);
        assertEquals(200, repris.status);
        golden("etablir_plan_decalage_horaire_201", etabli);
        golden("reactiver_plan_decalage_horaire_200", repris);
    }

    @Test
    void reactiver_avec_une_date_passee_est_accepte_et_met_le_plan_en_retard() throws Exception {
        Resp r = postJson("/" + P_ANNUEL_INACTIF + "/reactiver", "{\"nouvelleDatePrevue\":\"2010-01-01T00:00:00Z\"}",
                adminA());
        assertEquals(200, r.status);
        assertEquals(1, mvcListSize("/en-retard", adminA(), "Revision annuelle"));
    }

    @Test
    void reactiver_un_plan_actif_ou_archive_est_refuse_en_422() throws Exception {
        String corps = "{\"nouvelleDatePrevue\":\"2031-05-01T07:30:00Z\"}";
        Resp actif = postJson("/" + P_HEBDO_ACTIF + "/reactiver", corps, adminA());
        Resp archive = postJson("/" + P_ARCHIVE + "/reactiver", corps, adminA());
        assertEquals(422, actif.status);
        assertEquals(422, archive.status);
        golden("reactiver_plan_actif_422", actif);
        golden("reactiver_plan_archive_422", archive);
    }

    @Test
    void reactiver_exige_un_corps_avec_la_nouvelle_date() throws Exception {
        Resp sansDate = postJson("/" + P_ANNUEL_INACTIF + "/reactiver", "{}", adminA());
        Resp sansCorps = postNoBody("/" + P_ANNUEL_INACTIF + "/reactiver", adminA());
        golden("reactiver_sans_date_400", sansDate);
        golden("reactiver_sans_corps", sansCorps);
        golden("reactiver_plan_inconnu_400",
                postJson("/" + P_INCONNU + "/reactiver", "{\"nouvelleDatePrevue\":\"2031-05-01T07:30:00Z\"}", adminA()));
        assertEquals("INACTIF", row(P_ANNUEL_INACTIF).get("STATUT"));
    }

    // ------------------------------------------------------------------ parcours complet

    @Test
    void cycle_de_vie_etablir_executer_desactiver_reactiver_executer() throws Exception {
        Resp cree = postJson("", body(EQUIP_1, "Cycle complet", null, "SEMESTRIELLE", "2030-01-01T00:00:00Z", null), adminA());
        String id = json(cree).get("id").asString();
        Resp e1 = postNoBody("/" + id + "/executer", adminA());
        Resp d = postNoBody("/" + id + "/desactiver", adminA());
        Resp e2 = postNoBody("/" + id + "/executer", adminA());
        Resp rea = postJson("/" + id + "/reactiver", "{\"nouvelleDatePrevue\":\"2040-02-02T02:02:00Z\"}", adminA());
        Resp e3 = postNoBody("/" + id + "/executer", adminA());
        assertEquals(List.of(201, 200, 200, 422, 200, 200),
                List.of(cree.status, e1.status, d.status, e2.status, rea.status, e3.status));
        // dateCreation date de la première requête : hors fenêtre de la dernière, on la retire du golden
        ObjectNode sansCreation = (ObjectNode) json(e3);
        sansCreation.remove("dateCreation");
        golden("cycle_vie_apres_derniere_execution",
                new Resp(e3.status, e3.contentType, mapper.writeValueAsString(sansCreation), e3.t0, e3.t1));
        // la désactivation conserve le compteur et la date d'exécution : le dernier état cumule 2 exécutions
        assertEquals(2, json(e3).get("nombreExecutions").asInt());
        assertEquals("SEMESTRIELLE", json(e3).get("frequence").asString());
    }

    private int countPlans() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tablePlans() + " WHERE centre_id IN (?, ?)",
                Integer.class, CENTRE_A, CENTRE_B);
    }
}
