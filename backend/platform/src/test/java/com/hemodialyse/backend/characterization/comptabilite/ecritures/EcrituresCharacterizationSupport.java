package com.hemodialyse.backend.characterization.comptabilite.ecritures;

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
import java.time.LocalDate;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Harnais commun du filet de caractérisation de la slice comptabilite-c3-ecritures : consultation, validation, export
 * et clôture de période des écritures comptables, observés en HTTP (MockMvc + sécurité réelle + {@code RoleScopeFilter}
 * + base H2) et par lecture SQL de {@code ecritures_comptables}, {@code lignes_ecriture} et
 * {@code periodes_comptables}. Aucun mock du code caractérisé.
 * <p>
 * Données : les écritures « de fixture » sont insérées en SQL avec des identifiants fixes (écriture
 * {@code 99996300-…-0001-…}, ligne {@code 99996300-…-0002-…}), ce qui rend le tri des lignes ({@code @OrderBy id})
 * et l'ordre de lecture de la base déterministes. Les écritures générées par les cas d'usage (ventes, règlements, stock,
 * pièces) ont des identifiants aléatoires : le golden les normalise (alias par ordre d'apparition, lignes triées par
 * compte et montants).
 * <p>
 * Non-déterminisme maîtrisé sans seam : {@code UUID.randomUUID()} des écritures et lignes générées (normalisés),
 * {@code OffsetDateTime.now()} de {@code cloturee_at}/{@code created_at}/{@code updated_at} (jamais exposés ni
 * comparés ; {@code cloturee_at} seulement testé non nul), clés d'objet triées dans les réponses ({@code Map.of}).
 * Les dates de fixture sont fixes ; aucune ne dépend du jour d'exécution.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class EcrituresCharacterizationSupport {

    protected static final String BASE = "/api/v1/comptabilite";
    protected static final UUID CENTRE_A = UUID.fromString("99996300-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99996300-0000-0000-0000-00000000000b");
    protected static final UUID CENTRE_VIDE = UUID.fromString("99996300-0000-0000-0000-00000000000c");
    protected static final UUID CNAS = UUID.fromString("99996300-0000-0000-0000-000000000011");
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "comptabilite",
            "ecritures");
    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern ECR_RE = Pattern.compile("99996300-0000-0000-0001-(\\d{12})");
    private static final Pattern LIG_RE = Pattern.compile("99996300-0000-0000-0002-(\\d{12})");
    private static final Pattern MOD_RE = Pattern.compile("99996300-0000-0000-0003-(\\d{12})");
    private static final Pattern FAC_RE = Pattern.compile("99996300-0000-0000-0004-(\\d{12})");
    private static final Pattern REG_RE = Pattern.compile("99996300-0000-0000-0005-(\\d{12})");
    private static final OffsetDateTime CREE_LE = OffsetDateTime.of(2026, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC);
    protected final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected com.hemodialyse.backend.infrastructure.security.RoleScopeFilter roleScopeFilter;
    protected MockMvc mvc;
    @Autowired
    protected com.hemodialyse.backend.characterization.comptabilite.GenerationDEcritures comptabilite;
    private int prochaineLigne;

    // ------------------------------------------------------------------ identités

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

    protected static RequestPostProcessor secretaireA() {
        return as(CENTRE_A, "SECRETAIRE");
    }

    // ------------------------------------------------------------------ cycle

    protected static UUID ecr(int n) {
        return UUID.fromString(String.format("99996300-0000-0000-0001-%012d", n));
    }

    /**
     * Identifiant fixe d'une facture (normalisé {@code <FACTURE_n>}).
     */
    protected static UUID fid(int n) {
        return UUID.fromString(String.format("99996300-0000-0000-0004-%012d", n));
    }

    // ------------------------------------------------------------------ fixtures SQL

    /**
     * Identifiant fixe d'un règlement (normalisé {@code <REGLEMENT_n>}).
     */
    protected static UUID rid(int n) {
        return UUID.fromString(String.format("99996300-0000-0000-0005-%012d", n));
    }

    /**
     * Identifiant fixe d'un modèle de pièce (normalisé {@code <MODELE_n>}).
     */
    protected static UUID mid(int n) {
        return UUID.fromString(String.format("99996300-0000-0000-0003-%012d", n));
    }

    protected static String json(String corps) {
        return corps.replace('\'', '"');
    }

    /**
     * Sous-dossier des goldens de la classe.
     */
    protected abstract String theme();

    @BeforeEach
    void setUpEcritures() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        prochaineLigne = 1;
        cleanupEcritures();
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, ?, ?)", CNAS,
                CENTRE_A, UUID.randomUUID(), "CNAS-16", "CNAS Alger");
    }

    @AfterEach
    void cleanupEcritures() {
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
        }
    }

    /**
     * Écriture de ventes générée par le cas d'usage de production (C5) : HT, TVA, TTC, date, payeur éventuel.
     */
    protected com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable genererVente(
            UUID centre, UUID facture, String numero, String ht, String tva, String ttc, String date, UUID payeur) {
        return comptabilite.genererEcritureFacturation(
                new com.hemodialyse.backend.characterization.comptabilite.GenerationDEcritures.Facture(
                        centre, facture, numero, UUID.randomUUID(), payeur, new BigDecimal(ht), new BigDecimal(tva),
                        new BigDecimal(ttc), LocalDate.parse(date), "Séances"));
    }

    protected com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable genererVente(
            int facture, String ht, String tva, String ttc, String date) {
        return genererVente(CENTRE_A, fid(facture), "FACT-" + facture, ht, tva, ttc, date, null);
    }

    /**
     * Écriture de règlement générée par le cas d'usage de production (C5).
     */
    protected com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable genererReglement(
            UUID centre, UUID facture, UUID paiement, String montant, String date, String mode, UUID payeur) {
        return comptabilite.genererEcritureReglement(
                new com.hemodialyse.backend.characterization.comptabilite.GenerationDEcritures.Reglement(
                        centre, facture, paiement, new BigDecimal(montant), LocalDate.parse(date), mode, "Règlement",
                        payeur));
    }

    /**
     * Écriture de fixture à lignes équilibrées simples (compte client débit / compte ventes crédit).
     */
    protected void ecriture(int n, UUID centre, String journal, String numero, String dateEcriture, String datePiece,
                            String statut, String libelle, UUID source, UUID modele, L... lignes) {
        jdbc.update("INSERT INTO ecritures_comptables (id, center_id, created_at, date_ecriture, date_piece, "
                        + "journal_code, libelle, numero_piece, source_id, statut, updated_at, modele_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", ecr(n), centre, CREE_LE,
                LocalDate.parse(dateEcriture), LocalDate.parse(datePiece), journal, libelle, numero, source, statut,
                CREE_LE, modele);
        for (L l : lignes) {
            jdbc.update("INSERT INTO lignes_ecriture (id, axes_analytiques, compte_scf, libelle_ligne, montant_credit, "
                            + "montant_debit, tiers_id, ecriture_id) VALUES (?, NULL, ?, ?, ?, ?, ?, ?)",
                    UUID.fromString(String.format("99996300-0000-0000-0002-%012d", prochaineLigne++)), l.compte,
                    l.libelle, l.credit == null ? null : new BigDecimal(l.credit),
                    l.debit == null ? null : new BigDecimal(l.debit), l.tiers, ecr(n));
        }
    }

    /**
     * Écriture de ventes de fixture : débit 411100 TTC, crédit 706 HT.
     */
    protected void vente(int n, UUID centre, String journal, String numero, String date, String statut, String montant) {
        ecriture(n, centre, journal, numero, date, date, statut, "Fact. " + numero, null, null,
                L.d("411100", montant), L.c("706", montant));
    }

    protected void vente(int n, String numero, String date, String statut, String montant) {
        vente(n, CENTRE_A, "VE", numero, date, statut, montant);
    }

    protected void compte(UUID centre, String numero) {
        jdbc.update("INSERT INTO comptes_comptables (id, center_id, numero, libelle, actif) VALUES (?, ?, ?, ?, TRUE)",
                UUID.randomUUID(), centre, numero, "Compte " + numero);
    }

    protected void journal(UUID centre, String code) {
        jdbc.update("INSERT INTO journaux_comptables (id, center_id, code, libelle, actif) VALUES (?, ?, ?, ?, TRUE)",
                UUID.randomUUID(), centre, code, "Journal " + code);
    }

    protected void periode(UUID centre, int annee, int mois, boolean cloturee, String par) {
        jdbc.update("INSERT INTO periodes_comptables (id, annee, center_id, cloturee, cloturee_at, cloturee_by, mois) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), annee, centre, cloturee,
                cloturee ? CREE_LE : null, par, mois);
    }

    // ------------------------------------------------------------------ requêtes

    protected Resp send(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse r = mvc.perform(request).andReturn().getResponse();
        String ct = r.getContentType() == null ? null : r.getContentType();
        return new Resp(r.getStatus(), ct, r.getHeader("Content-Disposition"), r.getContentAsString(StandardCharsets.UTF_8));
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

    protected Resp doPostSansCorps(String path, RequestPostProcessor who) throws Exception {
        return send(post(java.net.URI.create(BASE + path)).with(who));
    }

    protected Resp doPostTexte(String path, String texte, RequestPostProcessor who) throws Exception {
        return send(post(java.net.URI.create(BASE + path)).contentType(MediaType.TEXT_PLAIN).content(texte).with(who));
    }

    protected Resp doPut(String path, String json, RequestPostProcessor who) throws Exception {
        return send(put(java.net.URI.create(BASE + path)).contentType(MediaType.APPLICATION_JSON).content(json).with(who));
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

    protected String statutEnBase(int n) {
        return jdbc.queryForObject("SELECT statut FROM ecritures_comptables WHERE id = ?", String.class, ecr(n));
    }

    protected void assertStatuts(String attendu, int... ecritures) {
        List<String> reels = new ArrayList<>();
        for (int n : ecritures) reels.add(statutEnBase(n));
        List<String> voulus = new ArrayList<>();
        for (int ignored : ecritures) voulus.add(attendu);
        org.junit.jupiter.api.Assertions.assertEquals(voulus, reels);
    }

    protected void golden(String name, Resp r) {
        GoldenMaster.verifyJson(GOLDENS.resolve(theme()), name, new Scrubber(false).render(r));
    }

    /**
     * Extourne : la date du jour UTC est observable mais doit rester stable entre exécutions.
     */
    protected void goldenAujourdhui(String name, Resp r) {
        String actual = new Scrubber(false).render(r).replace(LocalDate.now(ZoneOffset.UTC).toString(), "<AUJOURDHUI_UTC>");
        GoldenMaster.verifyJson(GOLDENS.resolve(theme()), name, actual);
    }

    /**
     * Golden qui conserve l'ordre des lignes d'écriture de la réponse (ordre {@code @OrderBy id}, ids de fixture).
     */
    protected void goldenOrdreDesLignes(String name, Resp r) {
        GoldenMaster.verifyJson(GOLDENS.resolve(theme()), name, new Scrubber(true).render(r));
    }

    // ------------------------------------------------------------------ golden

    /**
     * Fichier d'export : une ligne CSV par entrée, ordre des enregistrements conservé.
     */
    protected void goldenCsv(String name, Resp r) {
        GoldenMaster.verifyJson(GOLDENS.resolve(theme()), name, new Scrubber(false).renderCsv(r, false));
    }

    /**
     * Fichier d'export : en-tête puis lignes triées (l'ordre des écritures du fichier n'a pas d'ORDER BY).
     */
    protected void goldenCsvTrie(String name, Resp r) {
        GoldenMaster.verifyJson(GOLDENS.resolve(theme()), name, new Scrubber(false).renderCsv(r, true));
    }

    /**
     * Valeur observée du nouveau code pour un écart métier décidé ; à valider humainement avant approbation.
     */
    protected void goldenCsvNouveauCode(String name, Resp r) {
        verifierOuEnregistrerCandidat(name, new Scrubber(false).renderCsv(r, false));
    }

    /**
     * Variante triée, utile aux exports qui couvrent plusieurs écritures.
     */
    protected void goldenCsvTrieNouveauCode(String name, Resp r) {
        verifierOuEnregistrerCandidat(name, new Scrubber(false).renderCsv(r, true));
    }

    private void verifierOuEnregistrerCandidat(String name, String actual) {
        String nom = name + "_nouveau_code";
        if (Boolean.getBoolean("characterization.recordNewCode")) {
            GoldenMaster.recordCandidate(GOLDENS.resolve(theme()), nom, actual);
        } else {
            GoldenMaster.verifyJson(GOLDENS.resolve(theme()), nom, actual);
        }
    }

    /**
     * État d'une requête SQL (lignes dans l'ordre demandé, colonnes en minuscules) comme golden.
     */
    protected void goldenSql(String name, String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        ArrayNode arr = mapper.createArrayNode();
        Scrubber s = new Scrubber(false);
        for (Map<String, Object> row : rows) {
            ObjectNode o = mapper.createObjectNode();
            row.forEach((k, v) -> o.put(k.toLowerCase(), v == null ? null : s.scrubString(String.valueOf(v))));
            arr.add(o);
        }
        GoldenMaster.verifyJson(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
    }

    /**
     * Valeur JSON construite par le test (déjà déterministe) comme golden.
     */
    protected void goldenNode(String name, JsonNode node) {
        GoldenMaster.verifyJson(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
    }

    /**
     * Écritures du centre (statut, numéro, dates, source), ordre stable : l'état observable après un traitement.
     */
    protected void goldenEcritures(String name, UUID centre) {
        goldenSql(name, "SELECT id, journal_code, numero_piece, date_ecriture, date_piece, statut, libelle, source_id, "
                + "modele_id FROM ecritures_comptables WHERE center_id = '" + centre
                + "' ORDER BY journal_code, numero_piece, date_ecriture, libelle, statut");
    }

    protected void goldenEcrituresAvecAujourdhui(String name, UUID centre) {
        String sql = "SELECT id, journal_code, numero_piece, date_ecriture, date_piece, statut, libelle, source_id, "
                + "modele_id FROM ecritures_comptables WHERE center_id = '" + centre
                + "' ORDER BY journal_code, numero_piece, date_ecriture, libelle, statut";
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        ArrayNode arr = mapper.createArrayNode();
        Scrubber s = new Scrubber(false);
        for (Map<String, Object> row : rows) {
            ObjectNode o = mapper.createObjectNode();
            row.forEach((k, v) -> {
                String valeur = v == null ? null : s.scrubString(String.valueOf(v));
                if ("date_ecriture".equalsIgnoreCase(k) && valeur != null) {
                    valeur = valeur.replace(LocalDate.now(ZoneOffset.UTC).toString(), "<AUJOURDHUI_UTC>");
                }
                o.put(k.toLowerCase(), valeur);
            });
            arr.add(o);
        }
        GoldenMaster.verifyJson(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
    }

    /**
     * Lignes d'écriture du centre (journal, pièce, compte, montants) triées.
     */
    protected void goldenLignes(String name, UUID centre) {
        goldenSql(name, "SELECT e.journal_code, e.numero_piece, l.compte_scf, l.libelle_ligne, l.montant_debit, "
                + "l.montant_credit, l.tiers_id, l.axes_analytiques FROM lignes_ecriture l "
                + "JOIN ecritures_comptables e ON e.id = l.ecriture_id "
                + "WHERE e.center_id = '" + centre + "' ORDER BY e.numero_piece, l.compte_scf, l.montant_debit, l.montant_credit");
    }

    /**
     * Périodes du centre (année, mois, état, auteur ; jamais l'horodatage : seulement s'il est renseigné).
     */
    protected void goldenPeriodes(String name, UUID centre) {
        goldenSql(name, "SELECT annee, mois, cloturee, cloturee_by, (cloturee_at IS NOT NULL) AS horodatee "
                + "FROM periodes_comptables WHERE center_id = '" + centre + "' ORDER BY annee, mois");
    }

    protected int count(String table, UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id = ?", Integer.class, centre);
    }

    /**
     * Une ligne de fixture : compte, débit, crédit (chaînes décimales ou {@code null}), tiers éventuel.
     */
    protected record L(String compte, String debit, String credit, String libelle, UUID tiers) {
        static L d(String compte, String montant) {
            return new L(compte, montant, "0.00", "Débit " + compte, null);
        }

        static L c(String compte, String montant) {
            return new L(compte, "0.00", montant, "Crédit " + compte, null);
        }

        L libelle(String l) {
            return new L(compte, debit, credit, l, tiers);
        }

        L tiers(UUID t) {
            return new L(compte, debit, credit, libelle, t);
        }
    }

    protected record Resp(int status, String contentType, String disposition, String raw) {
    }

    /**
     * Normalise : UUID -> alias stable, clés d'objet triées, lignes d'écriture triées (sauf {@code ordreLignes}).
     */
    private final class Scrubber {
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private final boolean ordreLignes;
        private int inconnus = 0;

        Scrubber(boolean ordreLignes) {
            this.ordreLignes = ordreLignes;
            alias(CENTRE_A, "CENTRE_A");
            alias(CENTRE_B, "CENTRE_B");
            alias(CENTRE_VIDE, "CENTRE_VIDE");
            alias(CNAS, "PAYEUR_CNAS");
        }

        private void alias(UUID u, String label) {
            aliases.put(u.toString(), "<" + label + ">");
        }

        String scrubString(String raw) {
            Matcher m = UUID_RE.matcher(raw);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String id = m.group();
                String a = aliases.get(id);
                if (a == null) {
                    Matcher e = ECR_RE.matcher(id);
                    Matcher l = LIG_RE.matcher(id);
                    Matcher f = FAC_RE.matcher(id);
                    Matcher r = REG_RE.matcher(id);
                    Matcher mo = MOD_RE.matcher(id);
                    if (e.matches()) a = "<ECRITURE_" + Integer.parseInt(e.group(1)) + ">";
                    else if (l.matches()) a = "<LIGNE_" + Integer.parseInt(l.group(1)) + ">";
                    else if (f.matches()) a = "<FACTURE_" + Integer.parseInt(f.group(1)) + ">";
                    else if (r.matches()) a = "<REGLEMENT_" + Integer.parseInt(r.group(1)) + ">";
                    else if (mo.matches()) a = "<MODELE_" + Integer.parseInt(mo.group(1)) + ">";
                    else a = "<UUID_GENERE_" + (++inconnus) + ">";
                    aliases.put(id, a);
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(a));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        private boolean estLigne(JsonNode n) {
            return n.isObject() && n.has("compteSCF");
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
                    if (estLigne(n) && e.getKey().equals("id") && !ordreLignes
                            && !LIG_RE.matcher(e.getValue().asString("")).matches()) {
                        o.put("id", "<LIGNE_GENEREE>");
                    } else {
                        o.set(e.getKey(), technique && e.getKey().equals("detail")
                                ? mapper.getNodeFactory().stringNode("<message technique Spring/Jackson/SGBD non verrouille>")
                                : scrub(e.getValue()));
                    }
                }
                return o;
            }
            if (n.isArray()) {
                List<JsonNode> elements = new ArrayList<>();
                n.forEach(elements::add);
                boolean lignes = !elements.isEmpty() && elements.stream().allMatch(this::estLigne);
                if (lignes && !ordreLignes) {
                    elements.sort(Comparator
                            .comparing((JsonNode x) -> x.path("compteSCF").asString(""))
                            .thenComparing(x -> x.path("montantDebit").decimalValue())
                            .thenComparing(x -> x.path("montantCredit").decimalValue())
                            .thenComparing(x -> x.path("libelleLigne").asString("")));
                }
                ArrayNode a = mapper.createArrayNode();
                elements.forEach(x -> a.add(scrub(x)));
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

        String renderCsv(Resp resp, boolean trierLesLignes) {
            ObjectNode root = mapper.createObjectNode();
            root.put("status", resp.status);
            root.put("contentType", resp.contentType);
            root.put("contentDisposition", resp.disposition);
            if (resp.status != 200) {
                try {
                    root.set("body", scrub(mapper.readTree(resp.raw)));
                } catch (RuntimeException e) {
                    root.put("bodyText", scrubString(resp.raw));
                }
            } else {
                String[] morceaux = resp.raw.split("\r\n", -1);
                root.put("finDeFichierCRLF", morceaux[morceaux.length - 1].isEmpty());
                root.put("octetsUtf8", resp.raw.getBytes(StandardCharsets.UTF_8).length);
                List<String> lignes = new ArrayList<>();
                for (int i = 0; i < morceaux.length - 1; i++) lignes.add(scrubString(morceaux[i]));
                if (trierLesLignes && lignes.size() > 2) {
                    List<String> corps = new ArrayList<>(lignes.subList(1, lignes.size()));
                    java.util.Collections.sort(corps);
                    lignes = new ArrayList<>(lignes.subList(0, 1));
                    lignes.addAll(corps);
                }
                ArrayNode a = root.putArray("lignes");
                lignes.forEach(a::add);
            }
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        }
    }
}
