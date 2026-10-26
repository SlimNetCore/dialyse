package com.hemodialyse.backend.characterization.planification;

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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Harnais de base des filets de caractérisation du contexte Planification (slices P1 à P9) : MockMvc sur le contexte Spring
 * complet (sécurité réelle + {@code RoleScopeFilter}), base H2 de test, aucun mock du code caractérisé.
 * <p>
 * Contenu réutilisable : les 11 profils d'accès, les deux centres de test, les fixtures de l'organisation (salles, créneaux),
 * des patients et des infirmiers, la lecture SQL normalisée, le rendu normalisé d'une réponse HTTP.
 * <p>
 * Non-déterminisme : identifiants générés ({@code <UUID_GENERE_n>}), identifiants de fixtures par alias nommés
 * ({@code <SALLE_1>}...), horodatages ({@code <HORODATAGE>}) ; clés d'objets JSON triées ; les ordres de collections
 * sont ceux de l'API (jamais retriés), sauf mention contraire dans le scénario. Aucune date réelle ni dépendance au jour de
 * la semaine : les scénarios de P1 n'utilisent aucune date.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public abstract class PlanificationCharacterizationSupport {

    protected static final String PLANNING = "/api/v1/planning";
    protected static final UUID CENTRE_A = UUID.fromString("99997800-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99997800-0000-0000-0000-00000000000b");
    protected static final UUID SOCIETE = UUID.fromString("99997800-0000-0000-0000-0000000000f1");
    protected static final UUID U_ADMIN = UUID.fromString("99997800-0000-0000-0000-0000000000c1");
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "planification");
    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HORODATAGE_RE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}(:?\\d{2})?|Z)?");

    protected final ObjectMapper mapper = JsonMapper.builder().build();
    /** Alias nommés posés par les fixtures (identifiant connu -> {@code <ALIAS>}). */
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

    /** Dossier des goldens, sous {@code characterization/planification} (ex. {@code p1-parametrage/parametres}). */
    protected abstract String theme();

    // ------------------------------------------------------------------ profils

    protected static RequestPostProcessor as(UUID centre, String... roles) {
        return user(UserPrincipal.create(UUID.randomUUID().toString(), centre == null ? null : centre.toString(), "u-"
                + String.join("-", roles).toLowerCase(), "", List.of(roles), true));
    }

    protected static RequestPostProcessor adminA() {
        return as(CENTRE_A, "ADMIN");
    }

    protected static RequestPostProcessor adminB() {
        return as(CENTRE_B, "ADMIN");
    }

    /** Profil de direction de la société : sans centre de session, société portée par le jeton. */
    protected static RequestPostProcessor direction() {
        return user(new UserPrincipal(U_ADMIN.toString(), null, "u-direction", "",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, SOCIETE.toString()));
    }

    /** Les onze profils d'accès observés par tous les filets de Planification (nom -> session). */
    protected Map<String, RequestPostProcessor> profils() {
        Map<String, RequestPostProcessor> p = new LinkedHashMap<>();
        p.put("ADMIN", as(CENTRE_A, "ADMIN"));
        p.put("SECRETAIRE", as(CENTRE_A, "SECRETAIRE"));
        p.put("MEDECIN seul", as(CENTRE_A, "MEDECIN"));
        p.put("INFIRMIER seul", as(CENTRE_A, "INFIRMIER"));
        p.put("MEDECIN + INFIRMIER", as(CENTRE_A, "MEDECIN", "INFIRMIER"));
        p.put("DIRECTION", direction());
        p.put("SUPERADMIN", as(CENTRE_A, "SUPERADMIN"));
        p.put("SUPERADMIN + ADMIN", as(CENTRE_A, "SUPERADMIN", "ADMIN"));
        p.put("role inconnu (COMPTABLE)", as(CENTRE_A, "COMPTABLE"));
        p.put("aucun role", as(CENTRE_A));
        p.put("ADMIN sans centre", as(null, "ADMIN"));
        return p;
    }

    protected static String json(String corps) {
        return corps.replace('\'', '"');
    }

    // ------------------------------------------------------------------ cycle de test

    @BeforeEach
    protected void setUpPlanification() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        cleanupPlanification();
        aliasNommes.clear();
        sequence = 0;
        caches.getCacheNames().forEach(nom -> java.util.Optional.ofNullable(caches.getCache(nom))
                .ifPresent(org.springframework.cache.Cache::clear));
    }

    @AfterEach
    protected void cleanupPlanification() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            for (String table : List.of("planning_parametres", "planification_reglages", "planning_preference_patient",
                    "infirmier_profil_planning", "infirmier", "patients", "position_creneau", "salle")) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
    }

    // ------------------------------------------------------------------ fixtures

    private UUID id(String alias) {
        UUID id = UUID.nameUUIDFromBytes(("planification-p1-" + alias + "-" + (++sequence)).getBytes(StandardCharsets.UTF_8));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        return id;
    }

    protected UUID salle(UUID centre, String alias, String nom, String isolement) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, isolement) VALUES (?, ?, ?, ?, ?)",
                id, centre, alias, nom, isolement);
        return id;
    }

    protected UUID creneau(UUID centre, String alias, String libelle) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, ?, ?)", id, centre, alias, libelle);
        return id;
    }

    /** Patient du centre ; {@code etat} nul = état non renseigné ; {@code jours} = colonnes {@code jour_*} à vrai. */
    protected UUID patient(UUID centre, String alias, String nom, String prenom, String etat, UUID creneau, String... jours) {
        UUID id = id(alias);
        List<String> tous = List.of("dimanche", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi");
        List<String> choisis = List.of(jours);
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, position_id, jour_dimanche, jour_lundi, jour_mardi, jour_mercredi, "
                        + "jour_jeudi, jour_vendredi, jour_samedi, created_at) VALUES (?, ?, ?, ?, ?, 'M', DATE '2026-01-01', ?, "
                        + "'NON_VACANCIER', ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                id, centre, "PLN-" + alias, nom, prenom, "ASS-" + alias, etat, creneau,
                choisis.contains(tous.get(0)), choisis.contains(tous.get(1)), choisis.contains(tous.get(2)),
                choisis.contains(tous.get(3)), choisis.contains(tous.get(4)), choisis.contains(tous.get(5)),
                choisis.contains(tous.get(6)));
        return id;
    }

    protected UUID infirmier(UUID centre, String alias, String nom, String prenom, String qualification, boolean actif) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO infirmier (id, center_id, matricule, nom, prenom, qualification, habilite_isolement, actif) "
                + "VALUES (?, ?, ?, ?, ?, ?, FALSE, ?)", id, centre, alias, nom, prenom, qualification, actif);
        return id;
    }

    // ------------------------------------------------------------------ requêtes

    protected Resp send(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse r = mvc.perform(request).andReturn().getResponse();
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(StandardCharsets.UTF_8), r.getHeader("Cache-Control"));
    }

    protected Resp doGet(String path, RequestPostProcessor who) throws Exception {
        return send(get(java.net.URI.create(path)).with(who));
    }

    protected Resp doGetAnonymous(String path) throws Exception {
        return send(get(java.net.URI.create(path)));
    }

    protected Resp doPut(String path, String corps, RequestPostProcessor who) throws Exception {
        return send(put(java.net.URI.create(path)).contentType(MediaType.APPLICATION_JSON).content(corps).with(who));
    }

    protected Resp doPutAnonymous(String path, String corps) throws Exception {
        return send(put(java.net.URI.create(path)).contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    protected Resp doPost(String path, String corps, RequestPostProcessor who) throws Exception {
        return send(post(java.net.URI.create(path)).contentType(MediaType.APPLICATION_JSON).content(corps).with(who));
    }

    // ------------------------------------------------------------------ goldens

    protected void golden(String name, Resp r) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), nom(name), new Scrubber().render(r, true));
    }

    /** Variante sans l'en-tête Cache-Control (écritures). */
    protected void goldenSimple(String name, Resp r) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), nom(name), new Scrubber().render(r, false));
    }

    protected void goldenNode(String name, JsonNode node) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), nom(name),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(new Scrubber().scrub(node)));
    }

    protected void goldenSql(String name, String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        ArrayNode arr = mapper.createArrayNode();
        Scrubber s = new Scrubber();
        for (Map<String, Object> row : rows) {
            ObjectNode o = mapper.createObjectNode();
            row.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> o.put(e.getKey().toLowerCase(), e.getValue() == null ? null : s.scrubString(String.valueOf(e.getValue()))));
            arr.add(o);
        }
        GoldenMaster.verify(GOLDENS.resolve(theme()), nom(name), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
    }

    /**
     * Un scénario dont le comportement a été volontairement changé par la refonte (décisions du 26/10/2026) a son golden
     * {@code <nom>_nouveau_code} ; l'ancien comportement du legacy est conservé dans {@code p1-parametrage/_ecarts-legacy}.
     */
    private String nom(String name) {
        return Files.exists(GOLDENS.resolve(theme()).resolve(name + "_nouveau_code.approved.json")) ? name + "_nouveau_code" : name;
    }

    protected JsonNode corps(Resp r) {
        return mapper.readTree(r.raw);
    }

    /** Statut + code d'erreur (si présent) : forme compacte pour les matrices. */
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

    protected record Resp(int status, String contentType, String raw, String cacheControl) {
    }

    /** Normalise : UUID -> alias stable, horodatages -> {@code <HORODATAGE>}, clés d'objet triées. */
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
                            : scrubTrie(e.getValue(), e.getKey()));
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

        /**
         * Les ensembles immuables Java ({@code Set.copyOf}) itèrent dans un ordre salé par exécution de JVM : les tableaux
         * sérialisés depuis un tel ensemble (ex. {@code competences}) sont triés pour la comparaison (cf. KB-PLN-xx).
         */
        JsonNode scrubTrie(JsonNode n, String cle) {
            JsonNode s = scrub(n);
            if (s.isArray() && "competences".equals(cle)) {
                List<String> valeurs = new ArrayList<>();
                s.forEach(x -> valeurs.add(x.asString()));
                valeurs.sort(Comparator.naturalOrder());
                ArrayNode a = mapper.createArrayNode();
                valeurs.forEach(a::add);
                return a;
            }
            return s;
        }

        String render(Resp resp, boolean avecCache) {
            ObjectNode root = mapper.createObjectNode();
            root.put("status", resp.status);
            root.put("contentType", resp.contentType);
            if (avecCache) root.put("cacheControl", resp.cacheControl);
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
