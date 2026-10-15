package com.hemodialyse.backend.characterization.comptabilite.mappingtva;

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
import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Harnais commun du filet de caractérisation de la slice comptabilite-c2-mapping-tva : mapping comptable
 * ({@code /mapping}) et règles de TVA ({@code /regles-tva}) observés en HTTP (MockMvc + sécurité réelle + base H2), et
 * état des tables lu en SQL. Aucun mock du code caractérisé.
 * <p>
 * Non-déterminisme : {@code UUID.randomUUID()} n'alimente que les clés techniques de {@code mapping_comptable} et de
 * {@code tva_types} (jamais exposées) ; les réponses de {@code /regles-tva} sont des {@code Map.of} dont l'ordre des
 * clés change d'une JVM à l'autre : le golden trie donc les clés de chaque objet JSON.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class MappingTvaCharacterizationSupport {

    protected static final String BASE = "/api/v1/comptabilite";
    protected static final UUID CENTRE_A = UUID.fromString("99996200-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99996200-0000-0000-0000-00000000000b");
    protected static final UUID CENTRE_VIDE = UUID.fromString("99996200-0000-0000-0000-00000000000c");
    protected static final UUID CNAS = UUID.fromString("99996200-0000-0000-0000-000000000011");
    protected static final UUID MUTUELLE = UUID.fromString("99996200-0000-0000-0000-000000000012");
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "comptabilite",
            "mapping-tva");
    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
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

    protected static String json(String corps) {
        return corps.replace('\'', '"');
    }

    // ------------------------------------------------------------------ cycle

    /**
     * Sous-dossier des goldens de la classe (les noms de scénarios sont propres à chaque classe).
     */
    protected abstract String theme();

    @BeforeEach
    void setUpMappingTva() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        cleanupMappingTva();
        payeur(CNAS, CENTRE_A, "CNAS-16", "CNAS Alger");
        payeur(MUTUELLE, CENTRE_A, "MUT-1", "Mutuelle X");
    }

    @AfterEach
    void cleanupMappingTva() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B, CENTRE_VIDE)) {
            jdbc.update("DELETE FROM lignes_ecriture WHERE ecriture_id IN "
                    + "(SELECT id FROM ecritures_comptables WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM ecritures_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM inventaire_lignes WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM inventaires WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM bons_reception_lignes WHERE bon_reception_id IN "
                    + "(SELECT id FROM bons_reception WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM bons_reception WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM modeles_piece_lignes WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM modeles_piece WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM comptes_payeurs WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM comptes_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM journaux_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM articles WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM mapping_comptable WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM periodes_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM centre_payeur WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM tva_types WHERE center_id = ?", centre);
        }
    }

    protected void payeur(UUID id, UUID centre, String code, String nom) {
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, ?, ?)", id,
                centre, UUID.randomUUID(), code, nom);
    }

    protected void compte(UUID centre, String numero, String libelle, boolean actif) {
        jdbc.update("INSERT INTO comptes_comptables (id, center_id, numero, libelle, actif) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre, numero, libelle, actif);
    }

    protected void journal(UUID centre, String code, String libelle, boolean actif) {
        jdbc.update("INSERT INTO journaux_comptables (id, center_id, code, libelle, actif) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre, code, libelle, actif);
    }

    /**
     * Les cinq journaux par défaut, enregistrés.
     */
    protected void journauxParDefaut(UUID centre) {
        journal(centre, "VE", "Ventes", true);
        journal(centre, "BQ", "Banque", true);
        journal(centre, "CA", "Caisse", true);
        journal(centre, "AC", "Achats", true);
        journal(centre, "ST", "Stocks", true);
    }

    /**
     * Plan enregistré : comptes du mapping par défaut (10 + TVA).
     */
    protected void planDuMappingParDefaut(UUID centre) {
        for (String c : new String[]{"706", "411100", "411500", "512", "530", "44571", "322", "602", "408", "757", "657"}) {
            compte(centre, c, "Compte " + c, true);
        }
    }

    protected void comptePayeur(UUID centre, UUID payeur, String compte) {
        jdbc.update("INSERT INTO comptes_payeurs (center_id, payeur_id, compte) VALUES (?, ?, ?)", centre, payeur,
                compte);
    }

    /**
     * Ligne héritée d'avant le stock : comptes de base seulement, stock et journaux à NULL.
     */
    protected void mappingHerite(UUID centre, String ventes, String clientPatient, String clientDefaut, String banque,
                                 String caisse, String tva) {
        jdbc.update("INSERT INTO mapping_comptable (id, center_id, compte_ventes, compte_client_patient, "
                        + "compte_client_autre, compte_banque, compte_caisse, compte_tva_collectee) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), centre, ventes, clientPatient,
                clientDefaut, banque, caisse, tva);
    }

    // ------------------------------------------------------------------ requêtes

    /**
     * Règle de TVA enregistrée directement (donnée de la Facturation ou ancienne).
     */
    protected void regle(UUID centre, String type, String taux, boolean exonere, String debut, String fin,
                         boolean actif) {
        jdbc.update("INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere, "
                        + "date_debut_validite, date_fin_validite, texte_reference, actif, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, 'fixture')",
                UUID.randomUUID(), centre, type + " " + taux + "%", new java.math.BigDecimal(taux), type, exonere,
                LocalDate.parse(debut), fin == null ? null : LocalDate.parse(fin), actif);
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

    protected Resp doPutTexte(String path, String texte, RequestPostProcessor who) throws Exception {
        return send(put(java.net.URI.create(BASE + path)).contentType(MediaType.TEXT_PLAIN).content(texte).with(who));
    }

    protected Resp doPost(String path, String json, RequestPostProcessor who) throws Exception {
        return send(post(java.net.URI.create(BASE + path)).contentType(MediaType.APPLICATION_JSON).content(json).with(who));
    }

    protected Resp doPostSansCorps(String path, RequestPostProcessor who) throws Exception {
        return send(post(java.net.URI.create(BASE + path)).with(who));
    }

    protected Resp doPostTexte(String path, String texte, RequestPostProcessor who) throws Exception {
        return send(post(java.net.URI.create(BASE + path)).contentType(MediaType.TEXT_PLAIN).content(texte).with(who));
    }

    protected Resp doDelete(String path, RequestPostProcessor who) throws Exception {
        return send(delete(java.net.URI.create(BASE + path)).with(who));
    }

    protected Resp doRequest(String method, String path, RequestPostProcessor who) throws Exception {
        return send(request(org.springframework.http.HttpMethod.valueOf(method), java.net.URI.create(BASE + path)).with(who));
    }

    protected JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    protected void golden(String name, Resp r) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, new Scrubber().render(r));
    }

    // ------------------------------------------------------------------ golden

    /**
     * État d'une requête SQL (lignes dans l'ordre demandé, colonnes en minuscules) comme golden.
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

    /**
     * Valeur JSON construite par le test (déjà déterministe) comme golden.
     */
    protected void goldenNode(String name, JsonNode node) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
    }

    /**
     * Lignes d'écriture du centre (journal, pièce, compte, montants) triées : l'effet observable du paramétrage.
     */
    protected void goldenLignes(String name, UUID centre) {
        goldenSql(name, "SELECT e.journal_code, e.numero_piece, l.compte_scf, l.montant_debit, l.montant_credit "
                + "FROM lignes_ecriture l JOIN ecritures_comptables e ON e.id = l.ecriture_id "
                + "WHERE e.center_id = '" + centre + "' ORDER BY e.numero_piece, l.compte_scf, l.montant_debit");
    }

    /**
     * Ligne de paramétrage d'un centre (toutes les colonnes de mapping utiles ; aucune colonne volatile).
     */
    protected void goldenMapping(String name, UUID centre) {
        goldenSql(name, "SELECT compte_ventes, compte_client_patient, compte_client_autre, compte_banque, compte_caisse, "
                + "compte_tva_collectee, compte_stock, compte_consommation, compte_factures_non_parvenues, "
                + "compte_boni_inventaire, compte_mali_inventaire, journal_vente, journal_reglement_banque, "
                + "journal_reglement_caisse, journal_stock_reception, journal_stock_sortie, journal_stock_inventaire "
                + "FROM mapping_comptable WHERE center_id = '" + centre + "'");
    }

    /**
     * Règles d'un centre (colonnes métier, ordre stable ; ni id ni horodatage).
     */
    protected void goldenRegles(String name, UUID centre) {
        goldenSql(name, "SELECT type_prestation, taux, exonere, date_debut_validite, date_fin_validite, texte_reference, "
                + "libelle, actif, created_by FROM tva_types WHERE center_id = '" + centre
                + "' ORDER BY type_prestation, date_debut_validite, taux, date_fin_validite, texte_reference");
    }

    protected int count(String table, UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id = ?", Integer.class, centre);
    }

    protected record Resp(int status, String contentType, String raw) {
    }

    /**
     * Normalise : UUID -> alias stable (fixtures nommées), clés d'objet triées.
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
