package com.hemodialyse.backend.characterization.comptabilite.referentiel;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Harnais commun du filet de caractérisation de la slice comptabilite-c1-referentiel : plan comptable, journaux et
 * compte client des payeurs observés en HTTP (MockMvc + sécurité réelle + base H2), et état des tables lu en SQL.
 * Aucun mock du code caractérisé. Les identifiants générés par le legacy ({@code UUID.randomUUID()}) n'apparaissent
 * jamais dans les réponses de ces routes ; les fixtures portent des identifiants fixes remplacés par des alias.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class ReferentielCharacterizationSupport {

    protected static final String BASE = "/api/v1/comptabilite";
    protected static final UUID CENTRE_A = UUID.fromString("99996100-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99996100-0000-0000-0000-00000000000b");
    protected static final UUID CENTRE_VIDE = UUID.fromString("99996100-0000-0000-0000-00000000000c");
    protected static final UUID CNAS = UUID.fromString("99996100-0000-0000-0000-000000000011");
    protected static final UUID MUTUELLE = UUID.fromString("99996100-0000-0000-0000-000000000012");
    protected static final UUID CASNOS = UUID.fromString("99996100-0000-0000-0000-000000000013");
    protected static final UUID PAYEUR_B = UUID.fromString("99996100-0000-0000-0000-000000000014");
    protected static final UUID NOUVEAU_PAYEUR = UUID.fromString("99996100-0000-0000-0000-000000000020");
    protected static final UUID PAYEUR_INCONNU = UUID.fromString("99996100-0000-0000-0000-0000000000ff");
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "comptabilite",
            "referentiel");
    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    /**
     * Corps lus en BigDecimal exacts (aucun montant ici, mais même convention que les autres filets).
     */
    protected final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected com.hemodialyse.backend.infrastructure.security.RoleScopeFilter roleScopeFilter;
    protected MockMvc mvc;

    protected static RequestPostProcessor as(UUID centre, String... roles) {
        return user(UserPrincipal.create(UUID.randomUUID().toString(), centre.toString(), "u", "",
                List.of(roles), true));
    }

    // ------------------------------------------------------------------ identités

    protected static RequestPostProcessor adminA() {
        return as(CENTRE_A, "ADMIN");
    }

    protected static RequestPostProcessor adminB() {
        return as(CENTRE_B, "ADMIN");
    }

    protected static RequestPostProcessor secretaireA() {
        return as(CENTRE_A, "SECRETAIRE");
    }

    protected static String json(String corps) {
        return corps.replace('\'', '"');
    }

    // ------------------------------------------------------------------ cycle

    /**
     * Sous-dossier des goldens de la classe (les noms de scénarios sont propres à chaque classe).
     */
    protected abstract String theme();

    @BeforeEach
    void setUpReferentiel() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        cleanupReferentiel();
        payeur(CNAS, CENTRE_A, "CNAS-16", "CNAS Alger");
        payeur(MUTUELLE, CENTRE_A, "MUT-1", "Mutuelle X");
        payeur(CASNOS, CENTRE_A, "CASNOS-16", "CASNOS Alger");
        payeur(PAYEUR_B, CENTRE_B, "CNAS-31", "CNAS Oran");
    }

    @AfterEach
    void cleanupReferentiel() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B, CENTRE_VIDE)) {
            jdbc.update("DELETE FROM lignes_ecriture WHERE ecriture_id IN "
                    + "(SELECT id FROM ecritures_comptables WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM ecritures_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM modeles_piece_lignes WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM modeles_piece WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM comptes_payeurs WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM comptes_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM journaux_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM articles WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM mapping_comptable WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM periodes_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM centre_payeur WHERE center_id = ?", centre);
        }
    }

    protected void payeur(UUID id, UUID centre, String code, String nom) {
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, ?, ?)", id,
                centre, UUID.randomUUID(), code, nom);
    }

    /**
     * Compte enregistré directement (plan déjà paramétré par le centre).
     */
    protected void compte(UUID centre, String numero, String libelle, boolean actif) {
        jdbc.update("INSERT INTO comptes_comptables (id, center_id, numero, libelle, actif) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre, numero, libelle, actif);
    }

    protected void journal(UUID centre, String code, String libelle, boolean actif) {
        jdbc.update("INSERT INTO journaux_comptables (id, center_id, code, libelle, actif) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre, code, libelle, actif);
    }

    protected void comptePayeur(UUID centre, UUID payeur, String compte) {
        jdbc.update("INSERT INTO comptes_payeurs (center_id, payeur_id, compte) VALUES (?, ?, ?)", centre, payeur,
                compte);
    }

    /**
     * Paramétrage hérité d'avant le plan comptable : comptes de base, stock et journaux restent aux valeurs par défaut.
     */
    protected void mapping(UUID centre, String ventes, String clientPatient, String clientDefaut, String banque,
                           String caisse, String tva) {
        jdbc.update("INSERT INTO mapping_comptable (id, center_id, compte_ventes, compte_client_patient, "
                        + "compte_client_autre, compte_banque, compte_caisse, compte_tva_collectee) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), centre, ventes, clientPatient,
                clientDefaut, banque, caisse, tva);
    }

    // ------------------------------------------------------------------ requêtes

    /**
     * Écriture système porteuse d'une ligne sur chaque compte (un seul journal), pour tester « porte des écritures ».
     */
    protected void ecriture(UUID centre, String journalCode, String numeroPiece, String... comptes) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ecritures_comptables (id, center_id, journal_code, date_ecriture, date_piece, "
                        + "numero_piece, libelle, statut, created_at) VALUES (?, ?, ?, DATE '2026-10-05', "
                        + "DATE '2026-10-05', ?, 'Ecriture de test', 'VALIDEE', CURRENT_TIMESTAMP)",
                id, centre, journalCode, numeroPiece);
        int i = 0;
        for (String c : comptes) {
            boolean debit = i++ % 2 == 0;
            jdbc.update("INSERT INTO lignes_ecriture (id, ecriture_id, compte_scf, libelle_ligne, montant_debit, "
                            + "montant_credit) VALUES (?, ?, ?, 'ligne', ?, ?)", UUID.randomUUID(), id, c,
                    debit ? 100 : 0, debit ? 0 : 100);
        }
    }

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

    protected Resp doPut(String path, String json, RequestPostProcessor who) throws Exception {
        return send(put(java.net.URI.create(BASE + path)).contentType(MediaType.APPLICATION_JSON).content(json).with(who));
    }

    protected Resp doPutSansCorps(String path, RequestPostProcessor who) throws Exception {
        return send(put(java.net.URI.create(BASE + path)).with(who));
    }

    protected Resp doDelete(String path, RequestPostProcessor who) throws Exception {
        return send(delete(java.net.URI.create(BASE + path)).with(who));
    }

    protected Resp doRequest(String method, String path, RequestPostProcessor who) throws Exception {
        return send(request(org.springframework.http.HttpMethod.valueOf(method), java.net.URI.create(BASE + path)).with(who));
    }

    protected Resp doPost(String path, String json, RequestPostProcessor who) throws Exception {
        return send(post(java.net.URI.create(BASE + path)).contentType(MediaType.APPLICATION_JSON).content(json).with(who));
    }

    protected JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    protected void golden(String name, Resp r) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, new Scrubber().render(r));
    }

    // ------------------------------------------------------------------ golden

    /**
     * État d'une table (lignes triées, colonnes en minuscules) comme golden.
     */
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

    protected void goldenComptes(String name, UUID centre) {
        goldenSql(name, "SELECT numero, libelle, actif FROM comptes_comptables WHERE center_id = '" + centre
                + "' ORDER BY numero");
    }

    protected void goldenJournaux(String name, UUID centre) {
        goldenSql(name, "SELECT code, libelle, actif FROM journaux_comptables WHERE center_id = '" + centre
                + "' ORDER BY code");
    }

    protected void goldenComptesPayeurs(String name, UUID centre) {
        goldenSql(name, "SELECT payeur_id, compte FROM comptes_payeurs WHERE center_id = '" + centre
                + "' ORDER BY compte, payeur_id");
    }

    protected int count(String table, UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id = ?", Integer.class, centre);
    }

    protected record Resp(int status, String contentType, String raw) {
    }

    /**
     * Normalise : UUID -> alias stable (fixtures nommées).
     */
    private final class Scrubber {
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private int inconnus = 0;

        Scrubber() {
            alias(CENTRE_A, "CENTRE_A");
            alias(CENTRE_B, "CENTRE_B");
            alias(CENTRE_VIDE, "CENTRE_VIDE");
            alias(CNAS, "PAYEUR_CNAS");
            alias(MUTUELLE, "PAYEUR_MUTUELLE");
            alias(CASNOS, "PAYEUR_CASNOS");
            alias(PAYEUR_B, "PAYEUR_AUTRE_CENTRE");
            alias(PAYEUR_INCONNU, "PAYEUR_INCONNU");
            alias(NOUVEAU_PAYEUR, "NOUVEAU_PAYEUR");
        }

        private void alias(UUID u, String label) {
            aliases.put(u.toString(), "<" + label + ">");
        }

        String scrubString(String raw) {
            Matcher m = UUID_RE.matcher(raw);
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
                n.properties().forEach(e -> o.set(e.getKey(), technique && e.getKey().equals("detail")
                        ? mapper.getNodeFactory().stringNode("<message technique Spring/Jackson/SGBD non verrouille>")
                        : scrub(e.getValue())));
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
