package com.hemodialyse.backend.characterization.stock.referentiel;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Harnais du filet de caractérisation de la slice {@code stock-s1-referentiel} : articles, fournisseurs et emplacements
 * ({@code /api/v1/stock/referentiel}) observés en HTTP (MockMvc, sécurité réelle + RoleScopeFilter, H2) et état des tables
 * {@code articles}, {@code fournisseurs}, {@code emplacements} lu en SQL. Aucun mock du code caractérisé.
 * <p>
 * Non-déterminisme : identifiants générés (alias {@code <UUID_GENERE_n>}, alias nommés pour les identifiants connus) et
 * horodatages ({@code <HORODATAGE>}) normalisés ; clés des objets JSON triées.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class ReferentielStockCharacterizationSupport {

    protected static final String BASE = "/api/v1/stock/referentiel";
    protected static final UUID CENTRE_A = UUID.fromString("99997500-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99997500-0000-0000-0000-00000000000b");
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "stock", "referentiel");
    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HORODATAGE_RE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}(:?\\d{2})?|Z)?");
    protected final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    /**
     * Alias nommés posés par les scénarios (identifiants de fixtures connus).
     */
    protected final Map<String, String> aliasNommes = new LinkedHashMap<>();
    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected com.hemodialyse.backend.infrastructure.security.RoleScopeFilter roleScopeFilter;
    @Autowired
    protected org.springframework.cache.CacheManager caches;
    protected MockMvc mvc;

    protected static RequestPostProcessor as(UUID centre, String... roles) {
        return user(UserPrincipal.create(UUID.randomUUID().toString(), centre.toString(), "u", "",
                List.of(roles), true));
    }

    protected static RequestPostProcessor adminA() {
        return as(CENTRE_A, "ADMIN");
    }

    protected static RequestPostProcessor adminB() {
        return as(CENTRE_B, "ADMIN");
    }

    protected static String json(String corps) {
        return corps.replace('\'', '"');
    }

    protected abstract String theme();

    @BeforeEach
    void setUpReferentielStock() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        cleanupReferentielStock();
        caches.getCacheNames().forEach(nom -> java.util.Optional.ofNullable(caches.getCache(nom))
                .ifPresent(org.springframework.cache.Cache::clear));
    }

    @AfterEach
    void cleanupReferentielStock() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM articles WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM fournisseurs WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM emplacements WHERE center_id = ?", centre);
        }
    }

    // ------------------------------------------------------------------ données

    /**
     * Article existant (enregistré en SQL, avec stock et PMP posés comme s'ils résultaient de mouvements). Alias nommé.
     */
    protected UUID article(UUID centre, String alias, String code, String libelle, String dci, String codeBarres,
                           boolean actif, String typeAnemie, String stock, String pmp) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, "
                        + "gere_par_lot, active, type_traitement_anemie, created_at, dci, code_barres, peremption_obligatoire, "
                        + "produit_dangereux, dechet_dasri) VALUES (?, ?, ?, ?, 'piece', ?, 0, ?, false, ?, ?, CURRENT_TIMESTAMP, "
                        + "?, ?, false, false, false)",
                id, centre, code, libelle, new BigDecimal(stock), new BigDecimal(pmp), actif, typeAnemie, dci, codeBarres);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        return id;
    }

    protected UUID fournisseur(UUID centre, String alias, String raisonSociale, boolean actif) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO fournisseurs (id, center_id, code, raison_sociale, contact, telephone, email, actif) "
                + "VALUES (?, ?, ?, ?, NULL, NULL, NULL, ?)", id, centre, alias, raisonSociale, actif);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        return id;
    }

    protected UUID emplacement(UUID centre, String alias, String libelle, boolean actif) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO emplacements (id, center_id, code, libelle, actif) VALUES (?, ?, ?, ?, ?)",
                id, centre, alias, libelle, actif);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        return id;
    }

    // ------------------------------------------------------------------ requêtes

    protected Resp send(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse r = mvc.perform(request).andReturn().getResponse();
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(StandardCharsets.UTF_8));
    }

    protected Resp doGet(String path, RequestPostProcessor who) throws Exception {
        return send(get(java.net.URI.create(BASE + path)).with(who));
    }

    protected Resp doGetAnonymous(String path) throws Exception {
        return send(get(java.net.URI.create(BASE + path)));
    }

    protected Resp doPost(String path, String json, RequestPostProcessor who) throws Exception {
        return send(post(java.net.URI.create(BASE + path)).contentType(MediaType.APPLICATION_JSON).content(json).with(who));
    }

    protected Resp doPut(String path, String json, RequestPostProcessor who) throws Exception {
        return send(put(java.net.URI.create(BASE + path)).contentType(MediaType.APPLICATION_JSON).content(json).with(who));
    }

    protected Resp doPatch(String path, RequestPostProcessor who) throws Exception {
        return send(patch(java.net.URI.create(BASE + path)).with(who));
    }

    protected JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    // ------------------------------------------------------------------ golden

    protected void golden(String name, Resp r) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, new Scrubber().render(r));
    }

    protected void goldenSql(String name, String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        ArrayNode arr = mapper.createArrayNode();
        Scrubber s = new Scrubber();
        for (Map<String, Object> row : rows) {
            ObjectNode o = mapper.createObjectNode();
            row.forEach((k, v) -> o.put(k.toLowerCase(), v == null ? null : s.scrubString(String.valueOf(v))));
            arr.add(o);
        }
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
    }

    /**
     * Articles d'un centre (toutes les colonnes métier, ordre par code ; ni id ni horodatage).
     */
    protected void goldenArticles(String name, UUID centre) {
        goldenSql(name, "SELECT code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, gere_par_lot, active, "
                + "type_traitement_anemie, dci, forme_galenique, code_barres, reference_fabricant, unite_achat, coefficient_achat, "
                + "dosage_par_unite, unite_dosage, fournisseur_id, tva_type_id, prix_achat, stock_max, peremption_obligatoire, "
                + "condition_conservation, produit_dangereux, dechet_dasri, compte_stock, compte_charge, "
                + "(created_at IS NULL) AS created_at_nul FROM articles WHERE center_id = '" + centre + "' ORDER BY code");
    }

    protected int count(String table, UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id = ?", Integer.class, centre);
    }

    protected record Resp(int status, String contentType, String raw) {
    }

    /**
     * Normalise : UUID -> alias stable, horodatages -> {@code <HORODATAGE>}, clés d'objet triées, messages techniques
     * non verrouillés.
     */
    private final class Scrubber {
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private int inconnus = 0;

        Scrubber() {
            aliases.put(CENTRE_A.toString(), "<CENTRE_A>");
            aliases.put(CENTRE_B.toString(), "<CENTRE_B>");
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
                List<Map.Entry<String, JsonNode>> entrees = new ArrayList<>();
                n.properties().forEach(entrees::add);
                entrees.sort(Map.Entry.comparingByKey(Comparator.naturalOrder()));
                for (Map.Entry<String, JsonNode> e : entrees) {
                    o.set(e.getKey(), technique && e.getKey().equals("detail")
                            ? mapper.getNodeFactory().stringNode("<message technique Spring/Jackson/SGBD non verrouille>")
                            : scrub(e.getValue()));
                }
                return o;
            }
            if (n.isArray()) {
                ArrayNode a = mapper.createArrayNode();
                n.forEach(x -> a.add(scrub(x)));
                return a;
            }
            return n;
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

