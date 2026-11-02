package com.hemodialyse.backend.characterization.organisation;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.RoleScopeFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Harnais des filets de caractérisation du contexte Organisation (O1 à O5), écrit sur le legacy (commit {@code b365a63a^}) puis
 * rejoué sur le nouveau code. MockMvc sur le contexte Spring complet (sécurité réelle + {@code RoleScopeFilter}), H2 de test,
 * aucun mock du code caractérisé. Ne dépend que de l'API HTTP, de {@code UserPrincipal}, de {@code RoleScopeFilter} et des tables.
 * <p>
 * Non-déterminisme : identifiants de fixtures par alias nommés ({@code <SOCIETE_1>}...), identifiants générés
 * ({@code <UUID_GENERE_n>}), horodatages ({@code <HORODATAGE>}) ; clés d'objets JSON triées ; ordres de collections = ceux de
 * l'API. Aucune date réelle, aucun jour de mois fixe, aucun minuit : aucune date n'intervient dans ces scénarios.
 * <p>
 * Un scénario dont le comportement a été volontairement changé par une décision de l'utilisateur a son golden
 * {@code <nom>_nouveau_code} ; le comportement du legacy est alors conservé dans {@code _ecarts-legacy}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public abstract class OrganisationCharacterizationSupport {

    protected static final UUID CENTRE_A = UUID.fromString("99998200-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99998200-0000-0000-0000-00000000000b");
    protected static final UUID SOCIETE_TEST = UUID.fromString("99998200-0000-0000-0000-0000000000f1");
    protected static final String CODE_PREFIXE = "ORGT-";
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "organisation");
    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HORODATAGE_RE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}(:?\\d{2})?|Z)?");

    protected final ObjectMapper mapper = JsonMapper.builder().build();
    protected final Map<String, String> aliasNommes = new LinkedHashMap<>();
    private int sequence = 0;

    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected RoleScopeFilter roleScopeFilter;
    @Autowired
    protected org.springframework.cache.CacheManager caches;
    protected MockMvc mvc;

    /** Dossier des goldens, sous {@code characterization/organisation}. */
    protected abstract String theme();

    // ------------------------------------------------------------------ profils

    protected static RequestPostProcessor as(UUID centre, String... roles) {
        return user(UserPrincipal.create(UUID.randomUUID().toString(), centre == null ? null : centre.toString(), "u-"
                + String.join("-", roles).toLowerCase(), "", List.of(roles), true));
    }

    /** Les onze profils d'accès observés par les filets (nom -> session). Centre de session : A. */
    protected Map<String, RequestPostProcessor> profils() {
        Map<String, RequestPostProcessor> p = new LinkedHashMap<>();
        p.put("ADMIN", as(CENTRE_A, "ADMIN"));
        p.put("SECRETAIRE", as(CENTRE_A, "SECRETAIRE"));
        p.put("MEDECIN seul", as(CENTRE_A, "MEDECIN"));
        p.put("INFIRMIER seul", as(CENTRE_A, "INFIRMIER"));
        p.put("MEDECIN + INFIRMIER", as(CENTRE_A, "MEDECIN", "INFIRMIER"));
        p.put("DIRECTION", user(new UserPrincipal(UUID.randomUUID().toString(), null, "u-direction", "",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, SOCIETE_TEST.toString())));
        p.put("SUPERADMIN", as(CENTRE_A, "SUPERADMIN"));
        p.put("SUPERADMIN + ADMIN", as(CENTRE_A, "SUPERADMIN", "ADMIN"));
        p.put("role inconnu (COMPTABLE)", as(CENTRE_A, "COMPTABLE"));
        p.put("aucun role", as(CENTRE_A));
        p.put("ADMIN sans centre", as(null, "ADMIN"));
        return p;
    }

    protected static RequestPostProcessor superadmin() {
        return as(CENTRE_A, "SUPERADMIN");
    }

    protected static String json(String corps) {
        return corps.replace('\'', '"');
    }

    // ------------------------------------------------------------------ cycle de test

    @BeforeEach
    protected void setUpOrganisation() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        nettoyer();
        aliasNommes.clear();
        sequence = 0;
        videLesCaches();
    }

    @AfterEach
    protected void nettoyer() {
        jdbc.update("DELETE FROM centers WHERE code LIKE ?", CODE_PREFIXE + "%");
        jdbc.update("DELETE FROM societes WHERE code LIKE ?", CODE_PREFIXE + "%");
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            for (String table : List.of("salle", "position_creneau", "medecin")) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
    }

    protected void videLesCaches() {
        caches.getCacheNames().forEach(nom -> java.util.Optional.ofNullable(caches.getCache(nom))
                .ifPresent(org.springframework.cache.Cache::clear));
    }

    /** Remet la base et les alias à zéro entre deux cellules d'une matrice. */
    protected void reinitialiser() {
        nettoyer();
        aliasNommes.clear();
        sequence = 0;
        videLesCaches();
    }

    // ------------------------------------------------------------------ fixtures

    protected UUID id(String alias) {
        UUID id = UUID.nameUUIDFromBytes(("organisation-" + alias + "-" + (++sequence)).getBytes(StandardCharsets.UTF_8));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        return id;
    }

    protected UUID societe(String alias, String code, boolean actif) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                id, CODE_PREFIXE + code, "Société " + code, actif);
        return id;
    }

    protected UUID centre(String alias, UUID societe, String code, boolean actif) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, ?, ?, ?, ?)",
                id, CODE_PREFIXE + code, "Centre " + code, societe, actif);
        return id;
    }

    protected UUID salle(UUID centre, String alias, String code, String nom) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, ?, ?)", id, centre, code, nom);
        return id;
    }

    protected UUID creneau(UUID centre, String alias, String code, String libelle) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, ?, ?)", id, centre, code, libelle);
        return id;
    }

    protected UUID medecin(UUID centre, String alias, String nom, String prenom, String specialite) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO medecin (id, center_id, nom, prenom, specialite) VALUES (?, ?, ?, ?, ?)", id, centre, nom, prenom, specialite);
        return id;
    }

    // ------------------------------------------------------------------ requêtes

    protected record Resp(int status, String contentType, String raw, String cacheControl) {
    }

    protected Resp send(org.springframework.test.web.servlet.request.ConfigurableSmartRequestBuilder<?> request,
                        RequestPostProcessor who) throws Exception {
        if (who != null) request.with(who);
        MockHttpServletResponse r = mvc.perform(request).andReturn().getResponse();
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(StandardCharsets.UTF_8), r.getHeader("Cache-Control"));
    }

    protected Resp doGet(String path, RequestPostProcessor who) throws Exception {
        return send(get(java.net.URI.create(path)), who);
    }

    protected Resp doDelete(String path, RequestPostProcessor who) throws Exception {
        return send(delete(java.net.URI.create(path)), who);
    }

    protected Resp doPost(String path, String corps, RequestPostProcessor who) throws Exception {
        MockHttpServletRequestBuilder b = post(java.net.URI.create(path));
        if (corps != null) b = b.contentType(MediaType.APPLICATION_JSON).content(json(corps));
        return send(b, who);
    }

    protected Resp doPut(String path, String corps, RequestPostProcessor who) throws Exception {
        return send(put(java.net.URI.create(path)).contentType(MediaType.APPLICATION_JSON).content(json(corps)), who);
    }

    protected Resp doPutLogo(String path, byte[] contenu, String contentType, RequestPostProcessor who) throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("file", "logo", contentType, contenu);
        return send(multipart(org.springframework.http.HttpMethod.PUT, java.net.URI.create(path)).file(fichier), who);
    }

    protected String statutEtCode(Resp r) {
        if (r.raw == null || r.raw.isBlank()) return String.valueOf(r.status);
        try {
            JsonNode c = mapper.readTree(r.raw);
            if (c.has("code")) return r.status + " " + c.path("code").asString();
            return String.valueOf(r.status);
        } catch (RuntimeException e) {
            return String.valueOf(r.status);
        }
    }

    // ------------------------------------------------------------------ goldens

    protected void golden(String name, Resp r) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), nom(name), new Scrubber().render(r));
    }

    protected void goldenNode(String name, JsonNode node) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), nom(name),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(new Scrubber().scrub(node)));
    }

    protected void goldenSql(String name, String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        tools.jackson.databind.node.ArrayNode arr = mapper.createArrayNode();
        Scrubber s = new Scrubber();
        for (Map<String, Object> row : rows) {
            ObjectNode o = mapper.createObjectNode();
            row.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> o.put(e.getKey().toLowerCase(), e.getValue() == null ? null : s.scrubString(String.valueOf(e.getValue()))));
            arr.add(o);
        }
        GoldenMaster.verify(GOLDENS.resolve(theme()), nom(name), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
    }

    /** Golden {@code _nouveau_code} lorsqu'un écart décidé par l'utilisateur a changé le comportement. */
    private String nom(String name) {
        return Files.exists(GOLDENS.resolve(theme()).resolve(name + "_nouveau_code.approved.json")) ? name + "_nouveau_code" : name;
    }

    /** Normalise : UUID -> alias stable, horodatages -> {@code <HORODATAGE>}, clés d'objet triées. */
    private final class Scrubber {
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private int inconnus = 0;

        Scrubber() {
            aliases.put(CENTRE_A.toString(), "<CENTRE_A>");
            aliases.put(CENTRE_B.toString(), "<CENTRE_B>");
            aliases.put(SOCIETE_TEST.toString(), "<SOCIETE_TEST>");
            aliases.putAll(aliasNommes);
        }

        String scrubString(String raw) {
            String sansHeure = HORODATAGE_RE.matcher(raw).replaceAll("<HORODATAGE>");
            Matcher m = UUID_RE.matcher(sansHeure);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String a = aliases.computeIfAbsent(m.group(), k -> "<UUID_GENERE_" + (++inconnus) + ">");
                m.appendReplacement(sb, Matcher.quoteReplacement(a));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        JsonNode scrub(JsonNode n) {
            if (n.isString()) return mapper.getNodeFactory().stringNode(scrubString(n.asString()));
            if (n.isObject()) {
                ObjectNode o = mapper.createObjectNode();
                String code = n.path("code").asString("");
                boolean technique = "INTERNAL_ERROR".equals(code) || "DATA_ACCESS_ERROR".equals(code);
                List<Map.Entry<String, JsonNode>> entrees = new java.util.ArrayList<>();
                n.properties().forEach(entrees::add);
                entrees.sort(Map.Entry.comparingByKey());
                for (Map.Entry<String, JsonNode> e : entrees) {
                    o.set(e.getKey(), technique && e.getKey().equals("detail")
                            ? mapper.getNodeFactory().stringNode("<message technique Spring/Jackson/SGBD non verrouille>")
                            : scrubTrie(e.getValue(), e.getKey()));
                }
                return o;
            }
            if (n.isArray()) {
                tools.jackson.databind.node.ArrayNode a = mapper.createArrayNode();
                n.forEach(x -> a.add(scrub(x)));
                return a;
            }
            return n;
        }

        /**
         * L'ordre des centres d'une société n'est pas déterministe dans le legacy (collection non ordonnée, KB-ORG-04) : le
         * tableau {@code centres} est trié par code pour la comparaison.
         */
        JsonNode scrubTrie(JsonNode n, String cle) {
            JsonNode s = scrub(n);
            if (s.isArray() && "centres".equals(cle)) {
                List<JsonNode> elements = new java.util.ArrayList<>();
                s.forEach(elements::add);
                elements.sort(java.util.Comparator.comparing(x -> x.path("code").asString("")));
                tools.jackson.databind.node.ArrayNode a = mapper.createArrayNode();
                elements.forEach(a::add);
                return a;
            }
            return s;
        }

        String render(Resp resp) {
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
}
