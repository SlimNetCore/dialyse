package com.hemodialyse.backend.characterization.notifications.not1;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Harnais du filet NOT1 (contexte Notifications, ADR 0140) : les trois routes {@code /api/v1/notifications} observees en HTTP
 * (MockMvc, chaine de securite reelle, JDBC reel sur H2 {@code test}) et l'etat des tables {@code notification_evenement} /
 * {@code notification_lecture}. Aucun mock du code caracterise. Decrit le comportement ACTUEL, defauts compris
 * (voir .ddd/safety-net/known-bugs.md, KB-NOT1-n).
 * <p>
 * Non-determinisme : identifiants semes (alias {@code <N01>}...), horodatages {@code cree_le} semes RELATIFS a l'horloge
 * ({@code Instant.now()} est lu en dur par l'adaptateur, aucun seam) donc masques en {@code <HORLOGE>} ; l'ordre (plus recent
 * d'abord) reste verifie par la sequence des alias.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class Not1Support {

    static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "notifications", "not1");
    static final String API = "/api/v1/notifications";

    static final UUID CENTRE_A = UUID.fromString("99998140-0000-0000-0000-00000000000a");
    static final UUID CENTRE_B = UUID.fromString("99998140-0000-0000-0000-00000000000b");
    static final String USER_ADMIN = "99998140-0000-0000-0000-0000000000f1";
    static final String USER_ADMIN_2 = "99998140-0000-0000-0000-0000000000f2";
    static final String USER_INF = "99998140-0000-0000-0000-0000000000f3";
    static final String USER_INF_2 = "99998140-0000-0000-0000-0000000000f4";
    static final String USER_MED = "99998140-0000-0000-0000-0000000000f5";
    static final String USER_B = "99998140-0000-0000-0000-0000000000f6";

    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Autowired
    WebApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;

    final ObjectMapper mapper = JsonMapper.builder().build();
    MockMvc mvc;
    private String scenario = "?";

    @BeforeEach
    void setUpNot1(TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        nettoyer();
    }

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM notification_lecture WHERE notification_id IN "
                + "(SELECT id FROM notification_evenement WHERE center_id IN (?, ?))", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM notification_evenement WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM notification_lecture WHERE user_id LIKE '99998140-%'");
    }

    // ------------------------------------------------------------------ identites

    static RequestPostProcessor qui(UUID centre, String userId, String... roles) {
        return user(UserPrincipal.create(userId, centre == null ? null : centre.toString(), "u-" + userId, "",
                List.of(roles), true));
    }

    static RequestPostProcessor admin() {
        return qui(CENTRE_A, USER_ADMIN, "ADMIN");
    }

    // ------------------------------------------------------------------ semis

    /** Identifiant seme stable {@code 99998140-...-0000000001nn}, alias {@code <Nnn>}. */
    static UUID nid(int n) {
        return UUID.fromString(String.format("99998140-0000-0000-0000-0000000001%02d", n));
    }

    static Timestamp il_y_a(long minutes) {
        return Timestamp.valueOf(LocalDateTime.ofInstant(Instant.now().minus(minutes, ChronoUnit.MINUTES), ZoneOffset.UTC));
    }

    static Timestamp il_y_a_jours(long jours, long minutes) {
        return Timestamp.valueOf(LocalDateTime.ofInstant(
                Instant.now().minus(jours, ChronoUnit.DAYS).minus(minutes, ChronoUnit.MINUTES), ZoneOffset.UTC));
    }

    /** Evenement du journal tel que l'ecrit l'adaptateur ({@code target_roles} entre virgules, vide = tout le monde). */
    void evenement(int n, UUID centre, String type, String payloadJson, String roles, String compte, Timestamp creeLe) {
        jdbc.update("INSERT INTO notification_evenement (id, center_id, type, payload, target_roles, cree_le, target_user_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", nid(n), centre, type, payloadJson,
                roles == null || roles.isEmpty() ? "" : "," + roles + ",", creeLe, compte);
    }

    void evenement(int n, UUID centre, String type, String roles, long minutes) {
        evenement(n, centre, type, "{\"cle\":\"v" + n + "\",\"targetRoles\":\"" + (roles == null ? "" : roles) + "\"}",
                roles, null, il_y_a(minutes));
    }

    int lectures(String userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification_lecture WHERE user_id = ?", Integer.class, userId);
    }

    int evenements() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE center_id IN (?, ?)",
                Integer.class, CENTRE_A, CENTRE_B);
    }

    // ------------------------------------------------------------------ HTTP

    static final class Resp {
        final int status;
        final String raw;
        final MockHttpServletResponse http;

        Resp(MockHttpServletResponse r) throws Exception {
            this.http = r;
            this.status = r.getStatus();
            this.raw = r.getContentAsString(StandardCharsets.UTF_8);
        }
    }

    Resp get(String query, RequestPostProcessor who) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.get(API + (query == null ? "" : "?" + query));
        if (who != null) b.with(who);
        return new Resp(mvc.perform(b).andReturn().getResponse());
    }

    Resp post(String path, String query, String json, RequestPostProcessor who) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.post(API + path + (query == null ? "" : "?" + query));
        if (json != null) b.contentType(MediaType.APPLICATION_JSON).content(json);
        if (who != null) b.with(who);
        return new Resp(mvc.perform(b).andReturn().getResponse());
    }

    String lecture(UUID... ids) {
        StringBuilder sb = new StringBuilder("{\"ids\":[");
        for (int i = 0; i < ids.length; i++) sb.append(i > 0 ? "," : "").append('"').append(ids[i]).append('"');
        return sb.append("]}").toString();
    }

    // ------------------------------------------------------------------ normalisation

    static String alias(String uuid) {
        if (uuid.equals(CENTRE_A.toString())) return "<CENTRE_A>";
        if (uuid.equals(CENTRE_B.toString())) return "<CENTRE_B>";
        if (uuid.equals(USER_ADMIN)) return "<ADMIN>";
        if (uuid.equals(USER_ADMIN_2)) return "<ADMIN_2>";
        if (uuid.equals(USER_INF)) return "<INF>";
        if (uuid.equals(USER_INF_2)) return "<INF_2>";
        if (uuid.equals(USER_MED)) return "<MED>";
        if (uuid.equals(USER_B)) return "<USER_B>";
        for (int n = 0; n < 100; n++) if (uuid.equals(nid(n).toString())) return String.format("<N%02d>", n);
        return "<UUID_INCONNU>";
    }

    String masquer(String texte) {
        Matcher m = UUID_RE.matcher(texte);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(alias(m.group())));
        m.appendTail(sb);
        return sb.toString();
    }

    JsonNode masquer(JsonNode n) {
        if (n.isString()) return mapper.getNodeFactory().stringNode(masquer(n.asString()));
        if (n.isObject()) {
            ObjectNode o = mapper.createObjectNode();
            boolean technique = "INTERNAL_ERROR".equals(n.path("code").asString(""));
            n.properties().forEach(e -> {
                if (technique && e.getKey().equals("detail")) {
                    o.set(e.getKey(), mapper.getNodeFactory().stringNode("<message technique Spring non verrouille>"));
                } else if (e.getKey().equals("timestamp") && !e.getValue().isNull()) {
                    o.set(e.getKey(), mapper.getNodeFactory().stringNode("<HORLOGE>"));
                } else {
                    o.set(e.getKey(), masquer(e.getValue()));
                }
            });
            return o;
        }
        if (n.isArray()) {
            ArrayNode a = mapper.createArrayNode();
            n.forEach(x -> a.add(masquer(x)));
            return a;
        }
        return n;
    }

    /** Reponse complete : statut, en-tete Cache-Control, corps JSON masque (ou texte). */
    Map<String, Object> vue(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status);
        String cache = r.http.getHeader("Cache-Control");
        if (cache != null) m.put("cacheControl", cache);
        if (r.raw != null && !r.raw.isBlank()) {
            try {
                m.put("corps", masquer(mapper.readTree(r.raw)));
            } catch (RuntimeException e) {
                m.put("corpsTexte", masquer(r.raw));
            }
        }
        return m;
    }

    /** Resume d'une liste : statut, total, page, taille, sequence des types/cles (ordre = plus recent d'abord) et etat de lecture. */
    Map<String, Object> resume(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status);
        if (r.status != 200) {
            JsonNode j = lire(r);
            if (j != null && j.has("code")) m.put("code", j.get("code").asString());
            return m;
        }
        JsonNode j = lire(r);
        m.put("total", j.get("total").asLong());
        m.put("page", j.get("page").asInt());
        m.put("size", j.get("size").asInt());
        List<String> items = new java.util.ArrayList<>();
        j.get("items").forEach(i -> items.add(masquer(i.get("id").asString()) + (i.get("lue").asBoolean() ? " lue" : " non lue")));
        m.put("items", items);
        return m;
    }

    JsonNode lire(Resp r) {
        try {
            return mapper.readTree(r.raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    void golden(String nomDuGolden, Object valeur) {
        GoldenMaster.verify(GOLDENS.resolve(scenario), nomDuGolden, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(valeur));
    }
}
