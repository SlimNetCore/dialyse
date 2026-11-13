package com.hemodialyse.backend.characterization.documents.doc4b;

import com.hemodialyse.backend.application.audit.AuditWriterService;
import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.AuditRequestFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Filet de DOC4b (types de documents déclarés, ADR 0165) : {@code GET /api/v1/documents/types}. Point d'observation : HTTP (MockMvc,
 * sécurité réelle, JDBC réel sur H2 {@code test}). Aucun mock du code caractérisé. Identifiants masqués, journal d'audit observé.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class TypesDeDocumentCharacterizationIntegrationTest {

    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "documents", "doc4b");
    private static final UUID CENTRE_D = UUID.fromString("99998165-0000-0000-0000-00000000000d");
    private static final UUID UTILISATEUR = UUID.fromString("99998165-0000-0000-0000-0000000000f1");
    private static final String TYPES = "/api/v1/documents/types";

    private static final Pattern UUID_RE = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HORODATAGE = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}(:?\\d{2})?|Z)?");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuditRequestFilter filtreDAudit;
    @Autowired
    private AuditWriterService ecrivainDAudit;

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private MockMvc mvc;
    private String scenario = "?";

    @BeforeEach
    void monter(TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        nettoyer();
    }

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM audit_log WHERE center_id = ?", CENTRE_D);
    }

    private static RequestPostProcessor qui(UUID centre, String role) {
        return user(UserPrincipal.create(UTILISATEUR.toString(), centre == null ? null : centre.toString(), "u-" + role.toLowerCase(), "",
                List.of(role), true));
    }

    private String masquer(String texte) {
        Matcher m = UUID_RE.matcher(texte);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String id = m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(id.equals(CENTRE_D.toString()) ? "<CENTRE_D>"
                    : id.equals(UTILISATEUR.toString()) ? "<UTILISATEUR>" : "<UUID>"));
        }
        m.appendTail(sb);
        return HORODATAGE.matcher(sb.toString()).replaceAll("<HORODATAGE>");
    }

    private Map<String, Object> appel(AbstractMockHttpServletRequestBuilder<?> requete, RequestPostProcessor qui) throws Exception {
        if (qui != null) requete.with(qui);
        return vue(mvc.perform(requete).andReturn().getResponse());
    }

    private Map<String, Object> vue(MockHttpServletResponse r) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.getStatus());
        String type = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        m.put("contentType", type);
        for (String entete : List.of("Allow", "WWW-Authenticate")) {
            if (r.getHeader(entete) != null) m.put(entete, masquer(r.getHeader(entete)));
        }
        String corps = new String(r.getContentAsByteArray(), StandardCharsets.UTF_8);
        if (corps.isEmpty()) return m;
        if (type != null && type.contains("json")) {
            tools.jackson.databind.JsonNode arbre = mapper.readTree(masquer(corps));
            if (arbre instanceof tools.jackson.databind.node.ObjectNode objet && arbre.path("code").asString("").equals("DATA_ACCESS_ERROR")) {
                objet.put("detail", "<message SGBD>");
            }
            m.put("corps", trier(mapper.convertValue(arbre, Object.class)));
        } else {
            // L'ordre des champs d'une entrée est celui d'une Map.of (aléatoire d'une exécution à l'autre) : normalisé (code, puis label).
            m.put("corps", ORDRE_XML.matcher(masquer(corps)).replaceAll("<item>$2$1</item>"));
        }
        return m;
    }

    private static final Pattern ORDRE_XML = Pattern.compile("<item>(<label>.*?</label>)(<code>.*?</code>)</item>");

    /** Les clés d'un objet JSON sont triées : leur ordre n'est pas un comportement observable stable. */
    @SuppressWarnings("unchecked")
    private static Object trier(Object valeur) {
        if (valeur instanceof Map<?, ?> m) {
            Map<String, Object> trie = new java.util.TreeMap<>();
            m.forEach((k, v) -> trie.put(String.valueOf(k), trier(v)));
            return trie;
        }
        if (valeur instanceof List<?> l) return l.stream().map(TypesDeDocumentCharacterizationIntegrationTest::trier).toList();
        return valeur;
    }

    private void golden(String nom, Object valeur) {
        Path dossier = GOLDENS.resolve(scenario);
        String retenu = java.nio.file.Files.exists(dossier.resolve(nom + "_nouveau_code.approved.json")) ? nom + "_nouveau_code" : nom;
        GoldenMaster.verify(dossier, retenu, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(valeur));
    }

    @Test
    void la_liste_des_types_pour_chaque_profil() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        for (String role : List.of("ADMIN", "SUPERADMIN", "MEDECIN", "INFIRMIER", "SECRETAIRE", "SOCIETE_ADMIN")) {
            e.put("profil " + role, appel(MockMvcRequestBuilders.get(TYPES), qui(CENTRE_D, role)));
        }
        e.put("sans centre de session", appel(MockMvcRequestBuilders.get(TYPES), qui(null, "ADMIN")));
        e.put("sans session", appel(MockMvcRequestBuilders.get(TYPES), null));
        e.put("parametres inconnus ignores", appel(MockMvcRequestBuilders.get(TYPES).param("centerId", CENTRE_D.toString()).param("x", "y"),
                qui(CENTRE_D, "ADMIN")));
        golden("types", e);
    }

    @Test
    void les_formes_de_requete_non_prevues() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 Accept JSON", appel(MockMvcRequestBuilders.get(TYPES).accept(MediaType.APPLICATION_JSON), qui(CENTRE_D, "ADMIN")));
        e.put("02 Accept XML", appel(MockMvcRequestBuilders.get(TYPES).accept(MediaType.APPLICATION_XML), qui(CENTRE_D, "ADMIN")));
        e.put("03 Accept texte", appel(MockMvcRequestBuilders.get(TYPES).accept(MediaType.TEXT_PLAIN), qui(CENTRE_D, "ADMIN")));
        e.put("04 verbe HEAD", appel(MockMvcRequestBuilders.head(TYPES), qui(CENTRE_D, "ADMIN")));
        e.put("05 verbe POST", appel(MockMvcRequestBuilders.post(TYPES), qui(CENTRE_D, "ADMIN")));
        e.put("06 verbe PUT", appel(MockMvcRequestBuilders.put(TYPES), qui(CENTRE_D, "ADMIN")));
        e.put("07 verbe DELETE", appel(MockMvcRequestBuilders.delete(TYPES), qui(CENTRE_D, "ADMIN")));
        e.put("08 verbe PATCH", appel(MockMvcRequestBuilders.patch(TYPES), qui(CENTRE_D, "ADMIN")));
        e.put("09 verbe POST par un MEDECIN", appel(MockMvcRequestBuilders.post(TYPES), qui(CENTRE_D, "MEDECIN")));
        e.put("10 verbe POST sans session", appel(MockMvcRequestBuilders.post(TYPES), null));
        e.put("11 barre finale", appel(MockMvcRequestBuilders.get(TYPES + "/"), qui(CENTRE_D, "ADMIN")));
        e.put("12 chemin voisin inconnu", appel(MockMvcRequestBuilders.get(TYPES + "/x"), qui(CENTRE_D, "ADMIN")));
        golden("formes", e);
    }

    @Test
    void l_audit_de_la_lecture_des_types() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(filtreDAudit).build();
        ecrivainDAudit.flush();
        jdbc.update("DELETE FROM audit_log WHERE center_id = ?", CENTRE_D);
        mvc.perform(MockMvcRequestBuilders.get(TYPES).with(qui(CENTRE_D, "ADMIN")));
        mvc.perform(MockMvcRequestBuilders.get(TYPES).with(qui(CENTRE_D, "MEDECIN")));
        mvc.perform(MockMvcRequestBuilders.post(TYPES).with(qui(CENTRE_D, "ADMIN")));
        ecrivainDAudit.flush();
        List<Map<String, Object>> journal = new java.util.ArrayList<>(jdbc.queryForList("SELECT action_code, entity_type, entity_id, http_method, "
                + "route_template, status_code, COUNT(*) AS nombre FROM audit_log WHERE center_id = ? GROUP BY action_code, entity_type, "
                + "entity_id, http_method, route_template, status_code", CENTRE_D));
        journal.sort(java.util.Comparator.comparing((Map<String, Object> l) -> String.valueOf(l.get("route_template")))
                .thenComparing(l -> String.valueOf(l.get("http_method")))
                .thenComparing(l -> String.valueOf(l.get("status_code"))));
        List<Map<String, Object>> vues = new java.util.ArrayList<>();
        for (Map<String, Object> ligne : journal) {
            Map<String, Object> m = new LinkedHashMap<>();
            ligne.forEach((k, v) -> m.put(k, v instanceof String s ? masquer(s) : v));
            vues.add(m);
        }
        golden("journal", vues);
    }
}
