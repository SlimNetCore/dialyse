package com.hemodialyse.backend.characterization.gmao.intervenants;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
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
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Filet de caractérisation de la slice gmao-g2-intervenants : les endpoints {@code /api/v1/gmao/intervenants}
 * observés au niveau HTTP (MockMvc + chaîne de sécurité réelle + JPA réel sur H2), et la lecture indirecte du tarif
 * et de la nature par la clôture d'intervention. Il décrit le comportement ACTUEL, y compris les défauts (voir
 * .ddd/safety-net/known-bugs.md, KB-GMAO-22 et suivants).
 *
 * Non-déterminisme : les identifiants générés ({@code UUID.randomUUID()}) sont remplacés par des alias ; aucun
 * instant n'apparaît dans les réponses des intervenants. Aucun seam, aucun mock du code caractérisé.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class IntervenantHttpCharacterizationIntegrationTest {

    protected static final String BASE = "/api/v1/gmao/intervenants";
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "gmao", "intervenants");

    protected static final UUID CENTRE_A = UUID.fromString("99995200-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99995200-0000-0000-0000-00000000000b");
    protected static final UUID ADMIN_A = UUID.fromString("99995200-0000-0000-0000-0000000000f1");
    protected static final UUID ADMIN_B = UUID.fromString("99995200-0000-0000-0000-0000000000f2");
    protected static final UUID TECH = UUID.fromString("99995200-0000-0000-0000-0000000000f3");
    protected static final UUID EQUIP = UUID.fromString("99995200-0000-0000-0000-0000000000e1");

    protected static final UUID I_INTERNE = id(1);
    protected static final UUID I_EXTERNE = id(2);
    protected static final UUID I_SANS_TARIF = id(3);
    protected static final UUID I_DESACTIVE = id(4);
    protected static final UUID I_AUTRE_CENTRE = id(5);
    protected static final UUID I_INCONNU = id(99);

    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern ENUM_RE = Pattern.compile("No enum constant (?:\\w+\\.)+(\\w+)\\.(\\w+)");

    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;

    /** Lecture des corps en BigDecimal exacts : l'échelle JSON (45.50 vs 45.5) fait partie du contrat observé. */
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    protected MockMvc mvc;

    protected static UUID id(int n) {
        return UUID.fromString(String.format("99995200-0000-0000-0000-0000000001%02d", n));
    }

    // ------------------------------------------------------------------ harnais

    protected static RequestPostProcessor admin(UUID centre, UUID userId) {
        return user(UserPrincipal.create(userId.toString(), centre.toString(), "admin", "", List.of("ADMIN"), true));
    }

    protected static RequestPostProcessor adminA() {
        return admin(CENTRE_A, ADMIN_A);
    }

    private static RequestPostProcessor adminB() {
        return admin(CENTRE_B, ADMIN_B);
    }

    protected static RequestPostProcessor nonAdmin() {
        return user(UserPrincipal.create(TECH.toString(), CENTRE_A.toString(), "tech", "", List.of("TECHNICIEN"), true));
    }

    protected MockMvc construireMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    protected MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn();
    }

    @BeforeEach
    void setUp() {
        mvc = construireMvc();
        cleanup();
        preparerReferentiels();
        seed(I_INTERNE, CENTRE_A, "Bernard Technicien", "INTERNE", "0555000001", "bernard@centre.test",
                new BigDecimal("35.00"), true);
        seed(I_EXTERNE, CENTRE_A, "Acme Dialyse Services", "EXTERNE", "0555000002", "contact@acme.test",
                new BigDecimal("120.50"), true);
        seed(I_SANS_TARIF, CENTRE_A, "Claire Sans Tarif", "INTERNE", null, null, null, true);
        seed(I_DESACTIVE, CENTRE_A, "Ancien Prestataire", "EXTERNE", "0555000004", null,
                new BigDecimal("80.00"), false);
        seed(I_AUTRE_CENTRE, CENTRE_B, "Prestataire Autre Centre", "EXTERNE", null, null,
                new BigDecimal("99.99"), true);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM gmao_lignes_cout_intervention WHERE intervention_id IN "
                + "(SELECT id FROM gmao_interventions WHERE centre_id IN (?, ?))", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM gmao_interventions WHERE centre_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM gmao_equipements WHERE id = ?", EQUIP);
        jdbc.update("DELETE FROM gmao_intervenants WHERE centre_id IN (?, ?)", CENTRE_A, CENTRE_B);
        if (PG16) {
            jdbc.update("DELETE FROM app_user WHERE id IN (?, ?, ?)", ADMIN_A, ADMIN_B, TECH);
            jdbc.update("DELETE FROM centers WHERE id IN (?, ?)", CENTRE_A, CENTRE_B);
        }
    }

    /**
     * Rejeu sur un vrai PostgreSQL 16 ({@code -Dgmao.pg16=true}, voir backend/scripts/gmao-pg16-rejeu.sh) : le schéma Flyway
     * porte les clés étrangères vers centers / app_user que H2 (DDL Hibernate) n'a pas ; les parents des lignes créées par
     * la clôture d'intervention (équipement, intervention) sont donc créés ici. Sans effet sous H2.
     */
    protected static final boolean PG16 = Boolean.getBoolean("gmao.pg16");

    protected void preparerReferentiels() {
        if (!PG16) {
            return;
        }
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("INSERT INTO centers (id, societe_id) VALUES (?, ?)", centre, centre);
        }
        for (UUID u : List.of(ADMIN_A, ADMIN_B, TECH)) {
            jdbc.update("INSERT INTO app_user (id, active, password_hash, username, must_change_password) "
                    + "VALUES (?, TRUE, 'x', ?, FALSE)", u, "pg16-g2-" + u);
        }
    }

    private void seed(UUID id, UUID centre, String nom, String type, String tel, String email,
                      BigDecimal tarif, boolean actif) {
        jdbc.update("INSERT INTO gmao_intervenants (id, centre_id, nom, type, telephone, email, tarif_horaire_defaut, actif) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, centre, nom, type, tel, email, tarif, actif);
    }

    protected record Resp(int status, String contentType, String raw) {
    }

    protected Resp send(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse r = perform(request).getResponse();
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(StandardCharsets.UTF_8));
    }

    private Resp postJson(String path, String json, RequestPostProcessor who) throws Exception {
        return send(post(BASE + path).contentType(MediaType.APPLICATION_JSON).content(json).with(who));
    }

    private Resp putJson(String path, String json, RequestPostProcessor who) throws Exception {
        return send(put(BASE + path).contentType(MediaType.APPLICATION_JSON).content(json).with(who));
    }

    private Resp postNoBody(String path, RequestPostProcessor who) throws Exception {
        return send(post(BASE + path).with(who));
    }

    private Resp getAs(String path, RequestPostProcessor who) throws Exception {
        return send(get(BASE + path).with(who));
    }

    /** Normalise : UUID -> alias stable (fixtures nommées, générés numérotés par ordre d'apparition). */
    private final class Scrubber {
        private final Resp resp;
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private int unknown = 0;

        Scrubber(Resp resp) {
            this.resp = resp;
            alias(CENTRE_A, "CENTRE_A");
            alias(CENTRE_B, "CENTRE_B");
            alias(I_INTERNE, "INTERVENANT_INTERNE");
            alias(I_EXTERNE, "INTERVENANT_EXTERNE");
            alias(I_SANS_TARIF, "INTERVENANT_SANS_TARIF");
            alias(I_DESACTIVE, "INTERVENANT_DESACTIVE");
            alias(I_AUTRE_CENTRE, "INTERVENANT_AUTRE_CENTRE");
            alias(I_INCONNU, "INTERVENANT_INCONNU");
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
                var a = mapper.createArrayNode();
                n.forEach(x -> a.add(scrub(x)));
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

    protected void golden(String name, Resp r) {
        GoldenMaster.verify(GOLDENS, name, new Scrubber(r).render());
    }

    protected JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    /** Ligne de l'intervenant, colonnes nommées comme dans la table legacy (la parité les restitue sous les mêmes noms). */
    protected Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT * FROM gmao_intervenants WHERE id = ?", id);
    }

    protected int countRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM gmao_intervenants WHERE centre_id IN (?, ?)", Integer.class,
                CENTRE_A, CENTRE_B);
    }

    private static String body(String nom, String type, String tel, String email, String tarif) {
        return "{\"nom\":" + q(nom) + ",\"type\":" + q(type) + ",\"telephone\":" + q(tel) + ",\"email\":" + q(email)
                + ",\"tarifHoraireDefaut\":" + (tarif == null ? "null" : tarif) + "}";
    }

    private static String q(Object o) {
        return o == null ? "null" : "\"" + o + "\"";
    }

    private static void assertTarifDb(Object attendu, UUID id, Map<String, Object> row) {
        Object v = row.get("TARIF_HORAIRE_DEFAUT");
        if (attendu == null) {
            assertNull(v, "tarif en base de " + id);
        } else {
            assertEquals(attendu.toString(), ((BigDecimal) v).toPlainString(), "tarif en base de " + id);
        }
    }

    // ------------------------------------------------------------------ autorisation

    @Test
    void les_routes_sont_refusees_en_403_a_un_utilisateur_non_administrateur_sans_toucher_aux_donnees() throws Exception {
        String corps = body("Intrus", "INTERNE", null, null, "10");
        List<Resp> refus = List.of(
                send(post(BASE).contentType(MediaType.APPLICATION_JSON).content(corps).with(nonAdmin())),
                getAs("", nonAdmin()),
                putJson("/" + I_INTERNE, corps, nonAdmin()),
                postNoBody("/" + I_INTERNE + "/desactiver", nonAdmin()));
        for (Resp r : refus) {
            assertEquals(403, r.status, r.raw);
            assertTrue(r.raw.contains("ACCESS_DENIED"), r.raw);
        }
        golden("refus_non_admin_403", refus.get(0));
        assertEquals(5, countRows());
        assertEquals("Bernard Technicien", row(I_INTERNE).get("NOM"));
        assertEquals(Boolean.TRUE, row(I_INTERNE).get("ACTIF"));
    }

    @Test
    void une_requete_anonyme_est_refusee_sans_toucher_aux_donnees() throws Exception {
        golden("anonyme_lecture", send(get(BASE)));
        golden("anonyme_ecriture", send(post(BASE + "/" + I_INTERNE + "/desactiver")));
        assertEquals(Boolean.TRUE, row(I_INTERNE).get("ACTIF"));
    }

    // ------------------------------------------------------------------ POST (enregistrer)

    @Test
    void un_administrateur_enregistre_un_intervenant_interne_actif_rattache_a_son_centre() throws Exception {
        Resp r = postJson("", body("Karim Maintenance", "INTERNE", "0555123456", "karim@centre.test", "42.50"), adminA());
        assertEquals(201, r.status);
        golden("enregistrer_intervenant_interne_201", r);

        UUID newId = UUID.fromString(json(r).get("id").asString());
        Map<String, Object> db = row(newId);
        assertEquals(CENTRE_A, db.get("CENTRE_ID"));
        assertEquals("Karim Maintenance", db.get("NOM"));
        assertEquals("INTERNE", db.get("TYPE"));
        assertEquals("0555123456", db.get("TELEPHONE"));
        assertEquals("karim@centre.test", db.get("EMAIL"));
        assertEquals(Boolean.TRUE, db.get("ACTIF"));
        assertTarifDb("42.50", newId, db);
    }

    @Test
    void un_prestataire_externe_sans_coordonnees_ni_tarif_est_enregistre() throws Exception {
        Resp r = postJson("", body("Prestataire Minimal", "EXTERNE", null, null, null), adminA());
        assertEquals(201, r.status);
        golden("enregistrer_intervenant_externe_sans_tarif_201", r);
        UUID newId = UUID.fromString(json(r).get("id").asString());
        assertTarifDb(null, newId, row(newId));
        assertNull(row(newId).get("TELEPHONE"));
        assertNull(row(newId).get("EMAIL"));
    }

    @Test
    void un_champ_absent_du_json_equivaut_a_un_champ_null() throws Exception {
        Resp r = postJson("", "{\"nom\":\"Minimal JSON\",\"type\":\"INTERNE\"}", adminA());
        assertEquals(201, r.status);
        golden("enregistrer_json_minimal_201", r);
    }

    @Test
    void le_centre_de_l_intervenant_vient_de_l_utilisateur_connecte_pas_de_la_requete() throws Exception {
        Resp r = postJson("", "{\"nom\":\"Cree par B\",\"type\":\"EXTERNE\",\"centreId\":\"" + CENTRE_A
                + "\",\"actif\":false,\"id\":\"" + I_INCONNU + "\"}", adminB());
        assertEquals(201, r.status);
        golden("enregistrer_centre_et_id_ignores_dans_le_corps_201", r);
        assertEquals(CENTRE_B.toString(), json(r).get("centreId").asString());
        assertTrue(json(r).get("actif").asBoolean());
        assertFalse(json(r).get("id").asString().equals(I_INCONNU.toString()));
        assertFalse(json(getAs("", adminA())).get("items").toString().contains("Cree par B"));
    }

    @Test
    void le_nom_n_est_ni_rogne_ni_dedoublonne() throws Exception {
        Resp r1 = postJson("", body("  Bernard Technicien  ", "INTERNE", null, null, null), adminA());
        Resp r2 = postJson("", body("Bernard Technicien", "INTERNE", null, null, null), adminA());
        assertEquals(201, r1.status);
        assertEquals(201, r2.status);
        golden("enregistrer_nom_avec_espaces_201", r1);
        assertEquals("  Bernard Technicien  ", json(r1).get("nom").asString());
        assertEquals(7, countRows());
    }

    @Test
    void creation_invalide_sur_le_nom_ou_la_nature_est_refusee_en_400_sans_rien_enregistrer() throws Exception {
        Resp nomVide = postJson("", body("", "INTERNE", null, null, null), adminA());
        Resp nomBlanc = postJson("", body("   ", "INTERNE", null, null, null), adminA());
        Resp nomAbsent = postJson("", "{\"type\":\"INTERNE\"}", adminA());
        Resp natureAbsente = postJson("", "{\"nom\":\"X\"}", adminA());
        Resp natureBlanche = postJson("", body("X", "  ", null, null, null), adminA());
        Resp natureNull = postJson("", body("X", null, null, null, null), adminA());
        for (Resp r : List.of(nomVide, nomBlanc, nomAbsent, natureAbsente, natureBlanche, natureNull)) {
            assertEquals(400, r.status, r.raw);
            assertTrue(r.raw.contains("VALIDATION_ERROR"), r.raw);
        }
        golden("enregistrer_nom_vide_400", nomVide);
        golden("enregistrer_nature_absente_400", natureAbsente);
        assertEquals(5, countRows());
    }

    @Test
    void une_nature_inconnue_ou_en_minuscules_est_refusee_en_400_avec_le_message_de_l_enum() throws Exception {
        Resp inconnue = postJson("", body("X", "SOUS_TRAITANT", null, null, null), adminA());
        Resp minuscules = postJson("", body("X", "interne", null, null, null), adminA());
        assertEquals(400, inconnue.status, inconnue.raw);
        assertEquals(400, minuscules.status, minuscules.raw);
        golden("enregistrer_nature_inconnue_400", inconnue);
        golden("enregistrer_nature_minuscules_400", minuscules);
        assertEquals(5, countRows());
    }

    @Test
    void le_tarif_negatif_est_refuse_en_400_validation_le_zero_et_l_absence_de_tarif_sont_acceptes() throws Exception {
        Resp negatif = postJson("", body("X", "INTERNE", null, null, "-0.01"), adminA());
        Resp tresNegatif = postJson("", body("X", "INTERNE", null, null, "-100"), adminA());
        assertEquals(400, negatif.status, negatif.raw);
        assertEquals(400, tresNegatif.status, tresNegatif.raw);
        assertTrue(negatif.raw.contains("VALIDATION_ERROR"), negatif.raw);
        golden("enregistrer_tarif_negatif_400", negatif);
        assertEquals(5, countRows());

        Resp zero = postJson("", body("Gratuit", "INTERNE", null, null, "0"), adminA());
        assertEquals(201, zero.status);
        golden("enregistrer_tarif_zero_201", zero);
        assertTarifDb("0.00", UUID.fromString(json(zero).get("id").asString()), row(UUID.fromString(json(zero).get("id").asString())));

        Resp zeroNegatif = postJson("", body("Zero negatif", "INTERNE", null, null, "-0"), adminA());
        assertEquals(201, zeroNegatif.status);
        golden("enregistrer_tarif_moins_zero_201", zeroNegatif);
    }

    @Test
    void le_tarif_conserve_l_echelle_envoyee_dans_la_reponse_mais_la_base_arrondit_a_deux_decimales() throws Exception {
        Resp deuxDecimales = postJson("", body("Tarif 45.50", "INTERNE", null, null, "45.50"), adminA());
        Resp uneDecimale = postJson("", body("Tarif 45.5", "INTERNE", null, null, "45.5"), adminA());
        Resp entier = postJson("", body("Tarif 45", "INTERNE", null, null, "45"), adminA());
        Resp troisDecimales = postJson("", body("Tarif 12.345", "INTERNE", null, null, "12.345"), adminA());
        Resp arrondiBas = postJson("", body("Tarif 12.344", "INTERNE", null, null, "12.344"), adminA());
        Resp notation = postJson("", body("Tarif 1E+1", "INTERNE", null, null, "1E+1"), adminA());
        for (Resp r : List.of(deuxDecimales, uneDecimale, entier, troisDecimales, arrondiBas, notation)) {
            assertEquals(201, r.status, r.raw);
        }
        golden("tarif_echelle_45_50_201", deuxDecimales);
        golden("tarif_echelle_45_5_201", uneDecimale);
        golden("tarif_echelle_45_201", entier);
        golden("tarif_echelle_12_345_201", troisDecimales);
        golden("tarif_echelle_12_344_201", arrondiBas);
        golden("tarif_notation_scientifique_201", notation);
        // la réponse reprend l'objet du domaine tel que reçu (échelle conservée) ...
        assertTrue(deuxDecimales.raw.contains("\"tarifHoraireDefaut\":45.50"), deuxDecimales.raw);
        assertTrue(uneDecimale.raw.contains("\"tarifHoraireDefaut\":45.5,"), uneDecimale.raw);
        assertTrue(entier.raw.contains("\"tarifHoraireDefaut\":45,"), entier.raw);
        assertTrue(troisDecimales.raw.contains("\"tarifHoraireDefaut\":12.345"), troisDecimales.raw);
        // ... la base (NUMERIC(10,2)) arrondit à deux décimales
        assertTarifDb("45.50", null, row(UUID.fromString(json(uneDecimale).get("id").asString())));
        assertTarifDb("45.00", null, row(UUID.fromString(json(entier).get("id").asString())));
        assertTarifDb("12.35", null, row(UUID.fromString(json(troisDecimales).get("id").asString())));
        assertTarifDb("12.34", null, row(UUID.fromString(json(arrondiBas).get("id").asString())));
        assertTarifDb("10.00", null, row(UUID.fromString(json(notation).get("id").asString())));
        // relue par la liste, la valeur est celle de la base (échelle 2)
        JsonNode liste = json(getAs("?size=50", adminA())).get("items");
        for (JsonNode i : liste) {
            if ("Tarif 45.5".equals(i.get("nom").asString())) {
                assertEquals("45.50", i.get("tarifHoraireDefaut").decimalValue().toPlainString());
            }
            if ("Tarif 12.345".equals(i.get("nom").asString())) {
                assertEquals("12.35", i.get("tarifHoraireDefaut").decimalValue().toPlainString());
            }
        }
    }

    @Test
    void un_tarif_depassant_dix_chiffres_de_precision_est_refuse_par_la_base() throws Exception {
        Resp huit = postJson("", body("Tarif 8 chiffres", "INTERNE", null, null, "99999999.99"), adminA());
        assertEquals(201, huit.status, huit.raw);
        golden("tarif_precision_maximale_201", huit);
        Resp neuf = postJson("", body("Tarif 9 chiffres", "INTERNE", null, null, "100000000"), adminA());
        golden("tarif_precision_depassee", neuf);
        assertTrue(neuf.status >= 400, neuf.raw);
    }

    @Test
    void un_tarif_non_numerique_ou_une_chaine_est_refuse_sans_rien_enregistrer() throws Exception {
        Resp texte = postJson("", body("X", "INTERNE", null, null, "\"abc\""), adminA());
        Resp chaineNumerique = postJson("", body("Tarif chaine", "INTERNE", null, null, "\"12.5\""), adminA());
        golden("tarif_texte_non_numerique", texte);
        golden("tarif_chaine_numerique", chaineNumerique);
        assertTrue(texte.status >= 400, texte.raw);
    }

    @Test
    void les_limites_de_longueur_des_champs_sont_portees_par_la_base() throws Exception {
        assertEquals(201, postJson("", body("n".repeat(255), "INTERNE", "t".repeat(50), "e".repeat(255), null), adminA()).status);
        Resp nom256 = postJson("", body("n".repeat(256), "INTERNE", null, null, null), adminA());
        Resp tel51 = postJson("", body("Tel 51", "INTERNE", "t".repeat(51), null, null), adminA());
        Resp email256 = postJson("", body("Email 256", "INTERNE", null, "e".repeat(256), null), adminA());
        golden("enregistrer_nom_256_caracteres", nom256);
        golden("enregistrer_telephone_51_caracteres", tel51);
        golden("enregistrer_email_256_caracteres", email256);
        assertTrue(nom256.status >= 400, nom256.raw);
        assertTrue(tel51.status >= 400, tel51.raw);
        assertTrue(email256.status >= 400, email256.raw);
        assertEquals(6, countRows());
    }

    @Test
    void un_corps_absent_ou_illisible_est_refuse() throws Exception {
        Resp absent = send(post(BASE).contentType(MediaType.APPLICATION_JSON).with(adminA()));
        Resp illisible = postJson("", "{nom:", adminA());
        Resp mauvaisType = send(post(BASE).contentType(MediaType.TEXT_PLAIN).content("x").with(adminA()));
        golden("enregistrer_corps_absent", absent);
        golden("enregistrer_json_illisible", illisible);
        golden("enregistrer_media_type_non_supporte", mauvaisType);
        assertEquals(5, countRows());
    }

    // ------------------------------------------------------------------ GET liste

    @Test
    void la_liste_est_limitee_au_centre_triee_par_nom_et_inclut_les_intervenants_desactives() throws Exception {
        Resp r = getAs("", adminA());
        assertEquals(200, r.status);
        golden("lister_centre_A_200", r);
        List<String> noms = json(r).get("items").valueStream().map(n -> n.get("nom").asString()).toList();
        assertEquals(List.of("Acme Dialyse Services", "Ancien Prestataire", "Bernard Technicien", "Claire Sans Tarif"), noms);
        golden("lister_centre_B_200", getAs("", adminB()));
    }

    @Test
    void la_liste_est_paginee_page_et_taille_par_defaut_20() throws Exception {
        golden("lister_page_0_taille_2", getAs("?page=0&size=2", adminA()));
        golden("lister_page_1_taille_2", getAs("?page=1&size=2", adminA()));
        golden("lister_page_hors_limites", getAs("?page=5&size=2", adminA()));
        Resp defaut = getAs("", adminA());
        assertEquals(20, json(defaut).get("size").asInt());
        assertEquals(0, json(defaut).get("page").asInt());
        assertEquals(4, json(defaut).get("total").asInt());
    }

    @Test
    void les_parametres_de_pagination_invalides_sont_refuses() throws Exception {
        golden("lister_taille_zero", getAs("?size=0", adminA()));
        golden("lister_page_negative", getAs("?page=-1", adminA()));
        golden("lister_taille_non_numerique", getAs("?size=abc", adminA()));
        golden("lister_taille_demesuree", getAs("?size=100000", adminA()));
    }

    @Test
    void la_liste_d_un_centre_sans_intervenant_est_vide() throws Exception {
        jdbc.update("DELETE FROM gmao_intervenants WHERE centre_id = ?", CENTRE_B);
        golden("lister_centre_vide_200", getAs("", adminB()));
    }

    // ------------------------------------------------------------------ GET /{id}

    @Test
    void la_consultation_d_un_intervenant_par_son_identifiant() throws Exception {
        // KB-GMAO-22 (écart décidé) : le legacy répondait 500 ; GET /{id} répond 200 pour un intervenant du centre, 404 sinon.
        golden("consulter_par_id_non_admin_nouveau_code", getAs("/" + I_INTERNE, nonAdmin()));
        golden("consulter_par_id_anonyme", send(get(BASE + "/" + I_INTERNE)));
        golden("consulter_par_id_existant_nouveau_code", getAs("/" + I_INTERNE, adminA()));
        golden("consulter_par_id_inconnu_nouveau_code", getAs("/" + I_INCONNU, adminA()));
        golden("consulter_par_id_autre_centre_nouveau_code", getAs("/" + I_AUTRE_CENTRE, adminA()));
        golden("consulter_par_id_invalide_nouveau_code", getAs("/pas-un-uuid", adminA()));
    }

    // ------------------------------------------------------------------ PUT /{id}

    @Test
    void la_modification_remplace_nom_nature_coordonnees_et_tarif_sans_changer_identifiant_centre_ni_etat() throws Exception {
        Resp r = putJson("/" + I_INTERNE, body("Bernard Prestataire", "EXTERNE", "0666000000", "b@ext.test", "150.75"), adminA());
        assertEquals(200, r.status);
        golden("modifier_intervenant_200", r);
        Map<String, Object> db = row(I_INTERNE);
        assertEquals("Bernard Prestataire", db.get("NOM"));
        assertEquals("EXTERNE", db.get("TYPE"));
        assertEquals("0666000000", db.get("TELEPHONE"));
        assertEquals("b@ext.test", db.get("EMAIL"));
        assertEquals(CENTRE_A, db.get("CENTRE_ID"));
        assertEquals(Boolean.TRUE, db.get("ACTIF"));
        assertTarifDb("150.75", I_INTERNE, db);
        assertEquals(5, countRows());
    }

    @Test
    void la_modification_est_un_remplacement_total_les_champs_omis_sont_effaces() throws Exception {
        Resp r = putJson("/" + I_EXTERNE, "{\"nom\":\"Acme renomme\",\"type\":\"EXTERNE\"}", adminA());
        assertEquals(200, r.status);
        golden("modifier_champs_omis_effaces_200", r);
        assertNull(row(I_EXTERNE).get("TELEPHONE"));
        assertNull(row(I_EXTERNE).get("EMAIL"));
        assertNull(row(I_EXTERNE).get("TARIF_HORAIRE_DEFAUT"));
    }

    @Test
    void la_modification_d_un_intervenant_desactive_le_laisse_desactive() throws Exception {
        Resp r = putJson("/" + I_DESACTIVE, body("Ancien Renomme", "INTERNE", null, null, "0"), adminA());
        assertEquals(200, r.status);
        golden("modifier_intervenant_desactive_reste_desactive_200", r);
        assertEquals(Boolean.FALSE, row(I_DESACTIVE).get("ACTIF"));
        assertTarifDb("0.00", I_DESACTIVE, row(I_DESACTIVE));
    }

    @Test
    void la_revision_du_tarif_accepte_zero_et_vide_et_refuse_le_negatif() throws Exception {
        Resp vide = putJson("/" + I_INTERNE, body("Bernard Technicien", "INTERNE", "0555000001", "bernard@centre.test", null), adminA());
        assertEquals(200, vide.status);
        golden("modifier_tarif_vide_200", vide);
        assertNull(row(I_INTERNE).get("TARIF_HORAIRE_DEFAUT"));

        Resp zero = putJson("/" + I_INTERNE, body("Bernard Technicien", "INTERNE", null, null, "0.00"), adminA());
        assertEquals(200, zero.status);
        golden("modifier_tarif_zero_200", zero);
        assertTarifDb("0.00", I_INTERNE, row(I_INTERNE));

        Resp negatif = putJson("/" + I_INTERNE, body("Bernard Technicien", "INTERNE", null, null, "-5"), adminA());
        assertEquals(400, negatif.status);
        assertTrue(negatif.raw.contains("VALIDATION_ERROR"));
        golden("modifier_tarif_negatif_400", negatif);
        assertTarifDb("0.00", I_INTERNE, row(I_INTERNE));
    }

    @Test
    void la_modification_invalide_est_refusee_en_400_sans_rien_changer() throws Exception {
        Resp nomVide = putJson("/" + I_INTERNE, body("", "INTERNE", null, null, "10"), adminA());
        Resp natureAbsente = putJson("/" + I_INTERNE, "{\"nom\":\"X\"}", adminA());
        Resp natureInconnue = putJson("/" + I_INTERNE, body("X", "SOUS_TRAITANT", null, null, "10"), adminA());
        for (Resp r : List.of(nomVide, natureAbsente, natureInconnue)) {
            assertEquals(400, r.status, r.raw);
        }
        golden("modifier_nom_vide_400", nomVide);
        golden("modifier_nature_absente_400", natureAbsente);
        golden("modifier_nature_inconnue_400", natureInconnue);
        assertEquals("Bernard Technicien", row(I_INTERNE).get("NOM"));
        assertTarifDb("35.00", I_INTERNE, row(I_INTERNE));
    }

    @Test
    void la_modification_d_un_intervenant_inconnu_ou_d_un_identifiant_invalide_est_refusee_en_400() throws Exception {
        Resp inconnu = putJson("/" + I_INCONNU, body("X", "INTERNE", null, null, null), adminA());
        Resp invalide = putJson("/pas-un-uuid", body("X", "INTERNE", null, null, null), adminA());
        golden("modifier_intervenant_inconnu_400", inconnu);
        golden("modifier_identifiant_invalide", invalide);
        assertEquals(400, inconnu.status);
        assertEquals(5, countRows());
    }

    @Test
    void la_modification_d_un_intervenant_d_un_autre_centre_est_refusee_comme_inconnu_sans_rien_changer() throws Exception {
        Resp r = putJson("/" + I_AUTRE_CENTRE, body("Pirate", "INTERNE", null, null, "1"), adminA());
        golden("modifier_intervenant_d_un_autre_centre_400", r);
        assertEquals(400, r.status);
        assertEquals("Prestataire Autre Centre", row(I_AUTRE_CENTRE).get("NOM"));
        assertEquals(CENTRE_B, row(I_AUTRE_CENTRE).get("CENTRE_ID"));
        assertTarifDb("99.99", I_AUTRE_CENTRE, row(I_AUTRE_CENTRE));
    }

    @Test
    void la_modification_sans_corps_est_refusee() throws Exception {
        Resp r = send(put(BASE + "/" + I_INTERNE).contentType(MediaType.APPLICATION_JSON).with(adminA()));
        golden("modifier_corps_absent", r);
        assertEquals("Bernard Technicien", row(I_INTERNE).get("NOM"));
    }

    // ------------------------------------------------------------------ POST /{id}/desactiver

    @Test
    void la_desactivation_rend_l_intervenant_inactif_sans_le_supprimer_ni_toucher_aux_autres_champs() throws Exception {
        Resp r = postNoBody("/" + I_EXTERNE + "/desactiver", adminA());
        assertEquals(200, r.status);
        golden("desactiver_intervenant_200", r);
        Map<String, Object> db = row(I_EXTERNE);
        assertEquals(Boolean.FALSE, db.get("ACTIF"));
        assertEquals("Acme Dialyse Services", db.get("NOM"));
        assertEquals("EXTERNE", db.get("TYPE"));
        assertTarifDb("120.50", I_EXTERNE, db);
        assertEquals(5, countRows());
        // reste visible dans la liste (jamais supprimé, R24)
        assertTrue(getAs("", adminA()).raw.contains("Acme Dialyse Services"));
    }

    @Test
    void la_desactivation_est_idempotente_et_repond_200_a_chaque_appel() throws Exception {
        Resp premiere = postNoBody("/" + I_INTERNE + "/desactiver", adminA());
        Resp deuxieme = postNoBody("/" + I_INTERNE + "/desactiver", adminA());
        Resp dejaDesactive = postNoBody("/" + I_DESACTIVE + "/desactiver", adminA());
        assertEquals(200, premiere.status);
        assertEquals(200, deuxieme.status);
        assertEquals(200, dejaDesactive.status);
        assertEquals(premiere.raw, deuxieme.raw);
        golden("desactiver_deux_fois_200", deuxieme);
        golden("desactiver_deja_desactive_200", dejaDesactive);
        assertEquals(Boolean.FALSE, row(I_INTERNE).get("ACTIF"));
    }

    @Test
    void la_desactivation_d_un_intervenant_inconnu_ou_invalide_est_refusee_en_400() throws Exception {
        golden("desactiver_intervenant_inconnu_400", postNoBody("/" + I_INCONNU + "/desactiver", adminA()));
        golden("desactiver_identifiant_invalide", postNoBody("/pas-un-uuid/desactiver", adminA()));
    }

    @Test
    void la_desactivation_d_un_intervenant_d_un_autre_centre_est_refusee_comme_inconnu_sans_rien_changer() throws Exception {
        Resp r = postNoBody("/" + I_AUTRE_CENTRE + "/desactiver", adminA());
        assertEquals(400, r.status);
        golden("desactiver_intervenant_d_un_autre_centre_400", r);
        assertEquals(Boolean.TRUE, row(I_AUTRE_CENTRE).get("ACTIF"));
    }

    @Test
    void il_n_existe_ni_reactivation_ni_suppression_par_http() throws Exception {
        Resp reactiver = postNoBody("/" + I_DESACTIVE + "/reactiver", adminA());
        Resp activer = postNoBody("/" + I_DESACTIVE + "/activer", adminA());
        Resp supprimer = send(request(HttpMethod.DELETE, BASE + "/" + I_INTERNE).with(adminA()));
        Resp patch = send(request(HttpMethod.PATCH, BASE + "/" + I_INTERNE).contentType(MediaType.APPLICATION_JSON)
                .content("{}").with(adminA()));
        golden("reactiver_route_inexistante", reactiver);
        golden("activer_route_inexistante", activer);
        golden("supprimer_methode_non_supportee", supprimer);
        golden("patch_methode_non_supportee", patch);
        assertEquals(Boolean.FALSE, row(I_DESACTIVE).get("ACTIF"));
        assertEquals(5, countRows());
    }

    // ------------------------------------------------------------------ parcours

    @Test
    void parcours_complet_enregistrer_reviser_le_tarif_lister_desactiver_et_modifier_encore() throws Exception {
        Resp cree = postJson("", body("Nadia Ingenieure", "INTERNE", "0777", "nadia@centre.test", "50"), adminA());
        assertEquals(201, cree.status);
        String id = json(cree).get("id").asString();

        Resp revise = putJson("/" + id, body("Nadia Ingenieure", "INTERNE", "0777", "nadia@centre.test", "55.555"), adminA());
        assertEquals(200, revise.status);

        Resp liste = getAs("?size=50", adminA());
        assertEquals(5, json(liste).get("total").asInt());

        Resp desactive = postNoBody("/" + id + "/desactiver", adminA());
        assertEquals(200, desactive.status);
        Resp modifieApres = putJson("/" + id, body("Nadia Ingenieure Senior", "EXTERNE", null, null, "60"), adminA());
        assertEquals(200, modifieApres.status);

        Resp listeFinale = getAs("?size=50", adminA());
        golden("parcours_liste_finale", listeFinale);
        golden("parcours_revision_tarif_55_555", revise);
        golden("parcours_modification_apres_desactivation", modifieApres);
        assertEquals(Boolean.FALSE, row(UUID.fromString(id)).get("ACTIF"));
        assertTarifDb("60.00", UUID.fromString(id), row(UUID.fromString(id)));
    }

    // ------------------------------------------------------------------ lectures indirectes : clôture d'intervention

    private static final OffsetDateTime DEBUT = OffsetDateTime.of(2026, 3, 10, 8, 0, 0, 0, ZoneOffset.UTC);

    private void insererEquipement() {
        jdbc.update("INSERT INTO gmao_equipements (id, code, designation, type, centre_id, statut, date_installation, "
                        + "date_creation, cree_par) VALUES (?, ?, ?, 'GENERATEUR_DIALYSE', ?, 'EN_SERVICE', ?, ?, ?)",
                EQUIP, "G2-EQ", "Generateur G2", CENTRE_A, OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC), ADMIN_A);
    }

    /** Crée, démarre et clôture une intervention (durée en minutes) ; renvoie la réponse de clôture. */
    private Resp cloturer(UUID intervenant, long minutes) throws Exception {
        insererEquipement();
        String creation = "{\"equipementId\":\"" + EQUIP + "\",\"type\":\"CURATIVE\",\"dateDebut\":\"" + DEBUT
                + "\",\"description\":\"Panne pompe\",\"etatEquipementAvant\":\"EN_SERVICE\""
                + (intervenant == null ? "" : ",\"intervenantId\":\"" + intervenant + "\"") + "}";
        Resp cree = send(post("/api/v1/gmao/interventions").contentType(MediaType.APPLICATION_JSON).content(creation)
                .with(adminA()));
        assertEquals(201, cree.status, cree.raw);
        String iid = json(cree).get("id").asString();
        Resp demarre = send(post("/api/v1/gmao/interventions/" + iid + "/demarrer").with(adminA()));
        assertEquals(200, demarre.status, demarre.raw);
        String fin = "{\"actions\":\"Remplacement\",\"etatEquipementApres\":\"EN_SERVICE\",\"dateFin\":\""
                + DEBUT.plusMinutes(minutes) + "\"}";
        return send(post("/api/v1/gmao/interventions/" + iid + "/terminer").contentType(MediaType.APPLICATION_JSON)
                .content(fin).with(adminA()));
    }

    /** Ne conserve que les lignes de coût (seule partie dépendant de l'intervenant), id normalisés. */
    private Resp lignesDeCout(Resp cloture) {
        assertEquals(200, cloture.status, cloture.raw);
        ObjectNode o = mapper.createObjectNode();
        o.set("lignesCout", json(cloture).get("lignesCout"));
        return new Resp(200, "application/json", mapper.writeValueAsString(o));
    }

    @Test
    void la_cloture_valorise_le_temps_d_un_intervenant_interne_en_main_d_oeuvre_au_tarif_horaire() throws Exception {
        Resp r = lignesDeCout(cloturer(I_INTERNE, 90));
        golden("cloture_intervenant_interne_main_d_oeuvre", r);
        assertTrue(r.raw.contains("\"MAIN_OEUVRE\""), r.raw);
    }

    @Test
    void la_cloture_valorise_le_temps_d_un_prestataire_externe_en_honoraires() throws Exception {
        Resp r = lignesDeCout(cloturer(I_EXTERNE, 150));
        golden("cloture_intervenant_externe_honoraires", r);
        assertTrue(r.raw.contains("\"INTERVENANT\""), r.raw);
    }

    @Test
    void la_cloture_arrondit_les_heures_a_deux_decimales_demi_superieur() throws Exception {
        // 20 min = 0,333.. h -> 0,33 ; 50 min = 0,8333.. h -> 0,83 ; 55 min = 0,91666.. h -> 0,92
        golden("cloture_arrondi_20_minutes", lignesDeCout(cloturer(I_INTERNE, 20)));
        cleanupInterventions();
        golden("cloture_arrondi_55_minutes", lignesDeCout(cloturer(I_INTERNE, 55)));
    }

    @Test
    void la_cloture_ne_valorise_rien_sans_tarif_ou_avec_un_tarif_nul_ou_sans_intervenant() throws Exception {
        golden("cloture_intervenant_sans_tarif_aucune_ligne", lignesDeCout(cloturer(I_SANS_TARIF, 60)));
        cleanupInterventions();
        jdbc.update("UPDATE gmao_intervenants SET tarif_horaire_defaut = 0 WHERE id = ?", I_INTERNE);
        golden("cloture_intervenant_tarif_zero_aucune_ligne", lignesDeCout(cloturer(I_INTERNE, 60)));
        cleanupInterventions();
        golden("cloture_sans_intervenant_aucune_ligne", lignesDeCout(cloturer(null, 60)));
    }

    @Test
    void la_cloture_utilise_le_tarif_courant_de_l_intervenant_et_ignore_un_intervenant_desactive_ou_d_un_autre_centre() throws Exception {
        // un intervenant désactivé est tout de même valorisé (le filtre ne porte que sur le centre)
        Resp desactive = lignesDeCout(cloturer(I_DESACTIVE, 60));
        golden("cloture_intervenant_desactive_valorise", desactive);
        cleanupInterventions();
        Resp autreCentre = lignesDeCout(cloturer(I_AUTRE_CENTRE, 60));
        golden("cloture_intervenant_autre_centre_aucune_ligne", autreCentre);
        cleanupInterventions();
        // tarif révisé par HTTP juste avant la clôture
        assertEquals(200, putJson("/" + I_INTERNE, body("Bernard Technicien", "INTERNE", null, null, "40.10"), adminA()).status);
        golden("cloture_apres_revision_du_tarif", lignesDeCout(cloturer(I_INTERNE, 60)));
    }

    private void cleanupInterventions() {
        jdbc.update("DELETE FROM gmao_lignes_cout_intervention WHERE intervention_id IN "
                + "(SELECT id FROM gmao_interventions WHERE centre_id IN (?, ?))", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM gmao_interventions WHERE centre_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM gmao_equipements WHERE id = ?", EQUIP);
    }
}
