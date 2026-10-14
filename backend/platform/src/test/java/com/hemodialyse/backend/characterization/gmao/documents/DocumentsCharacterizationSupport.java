package com.hemodialyse.backend.characterization.gmao.documents;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Harnais commun du filet de caractérisation de la slice gmao-g5-pieces-jointes : les quatre endpoints
 * {@code /api/v1/gmao/interventions/{id}/documents} observés au niveau HTTP (MockMvc multipart + chaîne de sécurité
 * réelle + JPA réel sur H2), plus l'état de la table {@code gmao_documents_intervention} (octets compris).
 * Décrit le comportement ACTUEL, y compris les défauts (voir .ddd/safety-net/known-bugs.md, KB-GMAO-58 et suivants).
 * <p>
 * Non-déterminisme : ids générés -> alias ; {@code ajouteLe} -> {@code <HORLOGE>} ; les interventions sont semées par
 * SQL (indépendantes du code de G4) ; les fichiers de test sont des octets constants minuscules.
 * Limite : MockMvc ne passe pas par le conteneur de servlets, donc {@code spring.servlet.multipart.max-file-size}
 * (5 Mo / 6 Mo par requête) n'est PAS observable ici.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class DocumentsCharacterizationSupport {

    static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "gmao", "documents");

    static final UUID CENTRE_A = UUID.fromString("99996500-0000-0000-0000-00000000000a");
    static final UUID CENTRE_B = UUID.fromString("99996500-0000-0000-0000-00000000000b");
    static final UUID ADMIN_A = UUID.fromString("99996500-0000-0000-0000-0000000000f1");
    static final UUID ADMIN_B = UUID.fromString("99996500-0000-0000-0000-0000000000f2");
    static final UUID TECH = UUID.fromString("99996500-0000-0000-0000-0000000000f3");
    static final UUID HABILITE = UUID.fromString("99996500-0000-0000-0000-0000000000f4");

    static final UUID I_PLAN = id(11);
    static final UUID I_COURS = id(12);
    static final UUID I_TERM = id(13);
    static final UUID I_ANN = id(14);
    static final UUID I_ATT = id(15);
    static final UUID I_SUPPR = id(16);
    static final UUID I_B = id(17);
    static final UUID I_AUTRE = id(18);
    static final UUID I_INCONNU = id(99);
    static final UUID EQUIPEMENT = id(1);

    static final byte[] PDF = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);
    /** Jeux d'essai complets (KB-GMAO-59) : le PNG pèse 69 octets (11 avant la correction), le JPEG garde ses 10 octets et finit par EOI. */
    static final byte[] PNG = pngComplet();
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', (byte) 0xFF, (byte) 0xD9};

    private static final Pattern ENUM_RE = Pattern.compile("No enum constant (?:\\w+\\.)+(\\w+)\\.(\\w*)");
    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Autowired
    WebApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;

    final ObjectMapper mapper = JsonMapper.builder().build();
    MockMvc mvc;

    static UUID id(int n) {
        return UUID.fromString(String.format("99996500-0000-0000-0000-0000000001%02d", n));
    }

    /** Identifiant d'un document semé (stable, donc aliasé en {@code <DOC_nn>}). */
    static UUID doc(int n) {
        return UUID.fromString(String.format("99996500-0000-0000-0000-0000000002%02d", n));
    }

    // ------------------------------------------------------------------ identités

    static RequestPostProcessor qui(UUID centre, UUID userId, String... roles) {
        return user(UserPrincipal.create(userId.toString(), centre.toString(), "u-" + userId, "", List.of(roles), true));
    }

    static RequestPostProcessor adminA() {
        return qui(CENTRE_A, ADMIN_A, "ADMIN");
    }

    static RequestPostProcessor adminB() {
        return qui(CENTRE_B, ADMIN_B, "ADMIN");
    }

    static RequestPostProcessor nonAdmin() {
        return qui(CENTRE_A, TECH, "TECHNICIEN");
    }

    /** Habilitations GMAO sans ADMIN (aucune habilitation propre aux pièces jointes n'existe). */
    static RequestPostProcessor habiliteSansAdmin() {
        return qui(CENTRE_A, HABILITE, "GMAO_RECTIFICATION", "GMAO_REFORME");
    }

    // ------------------------------------------------------------------ cycle des tests

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        intervention(I_PLAN, CENTRE_A, "PLANIFIEE", false);
        intervention(I_COURS, CENTRE_A, "EN_COURS", false);
        intervention(I_TERM, CENTRE_A, "TERMINEE", false);
        intervention(I_ANN, CENTRE_A, "ANNULEE", false);
        intervention(I_ATT, CENTRE_A, "EN_ATTENTE_VALIDATION", false);
        intervention(I_SUPPR, CENTRE_A, "PLANIFIEE", true);
        intervention(I_B, CENTRE_B, "PLANIFIEE", false);
        intervention(I_AUTRE, CENTRE_A, "PLANIFIEE", false);
    }

    @AfterEach
    void cleanup() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM gmao_documents_intervention WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM gmao_interventions WHERE centre_id = ?", c);
        }
    }

    void intervention(UUID id, UUID centre, String statut, boolean supprimee) {
        OffsetDateTime t = OffsetDateTime.of(2026, 3, 10, 8, 0, 0, 0, ZoneOffset.UTC);
        jdbc.update("INSERT INTO gmao_interventions (id, equipement_id, centre_id, type, statut, date_debut, "
                        + "description, date_creation, cree_par, deleted_at) VALUES (?, ?, ?, 'CURATIVE', ?, ?, 'Panne pompe', ?, ?, ?)",
                id, EQUIPEMENT, centre, statut, t, t, ADMIN_A,
                supprimee ? OffsetDateTime.of(2026, 3, 11, 8, 0, 0, 0, ZoneOffset.UTC) : null);
    }

    /** Document semé directement en base ({@code rang} : minute d'ajout, pour un tri déterministe). */
    void semer(UUID id, UUID intervention, UUID centre, String type, String nom, String contentType, byte[] contenu,
               int rang) {
        jdbc.update("INSERT INTO gmao_documents_intervention (id, intervention_id, centre_id, type, nom, content_type, "
                        + "taille, contenu, ajoute_par, ajoute_le) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, intervention, centre, type, nom, contentType, (long) contenu.length, contenu, ADMIN_A,
                OffsetDateTime.of(2026, 4, 1, 9, rang % 60, rang / 60, 0, ZoneOffset.UTC));
    }

    // ------------------------------------------------------------------ appels HTTP

    static final class Resp {
        final int status;
        final byte[] bytes;
        final String raw;
        final MockHttpServletResponse http;

        Resp(MockHttpServletResponse r) throws Exception {
            this.http = r;
            this.status = r.getStatus();
            this.bytes = r.getContentAsByteArray();
            this.raw = r.getContentAsString(StandardCharsets.UTF_8);
        }

        String header(String name) {
            return http.getHeader(name);
        }
    }

    Resp send(org.springframework.test.web.servlet.RequestBuilder b) throws Exception {
        return new Resp(mvc.perform(b).andReturn().getResponse());
    }

    String base(UUID intervention) {
        return "/api/v1/gmao/interventions/" + intervention + "/documents";
    }

    Resp upload(UUID intervention, String nom, byte[] contenu, String type, RequestPostProcessor who) throws Exception {
        return upload(intervention, nom, "application/octet-stream", contenu, type, who);
    }

    Resp upload(UUID intervention, String nom, String contentType, byte[] contenu, String type,
                RequestPostProcessor who) throws Exception {
        var b = multipart(base(intervention))
                .file(new MockMultipartFile("file", nom, contentType, contenu));
        if (type != null) b.param("type", type);
        if (who != null) b.with(who);
        return send(b);
    }

    Resp upload(UUID intervention, String nom, byte[] contenu) throws Exception {
        return upload(intervention, nom, contenu, null, adminA());
    }

    /** Chemin libre (id d'intervention illisible, etc.). */
    Resp envoyer(HttpMethod m, String path, RequestPostProcessor who) throws Exception {
        MockHttpServletRequestBuilder b = request(m, path);
        if (who != null) b.with(who);
        return send(b);
    }

    Resp liste(UUID intervention, String query, RequestPostProcessor who) throws Exception {
        return envoyer(HttpMethod.GET, base(intervention) + (query == null ? "" : "?" + query), who);
    }

    Resp telecharger(UUID intervention, UUID document, RequestPostProcessor who) throws Exception {
        return envoyer(HttpMethod.GET, base(intervention) + "/" + document, who);
    }

    Resp retirer(UUID intervention, UUID document, RequestPostProcessor who) throws Exception {
        return envoyer(HttpMethod.DELETE, base(intervention) + "/" + document, who);
    }

    JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    UUID idCree(Resp r) {
        return UUID.fromString(json(r).get("id").asString());
    }

    // ------------------------------------------------------------------ observations hors HTTP

    /** Lignes « type|nom|contentType|taille|centre|ajoutePar » des documents d'une intervention, triées par nom. */
    List<String> documentsEnBase(UUID intervention) {
        return jdbc.queryForList("SELECT type, nom, content_type, taille, centre_id, ajoute_par "
                        + "FROM gmao_documents_intervention WHERE intervention_id = ? ORDER BY nom, ajoute_le", intervention)
                .stream().map(m -> m.get("TYPE") + "|" + m.get("NOM") + "|" + m.get("CONTENT_TYPE") + "|" + m.get("TAILLE")
                        + "|" + alias((UUID) m.get("CENTRE_ID")) + "|" + alias((UUID) m.get("AJOUTE_PAR"))).toList();
    }

    int nombre(UUID intervention) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM gmao_documents_intervention WHERE intervention_id = ?",
                Integer.class, intervention);
    }

    int nombreTotal() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM gmao_documents_intervention WHERE centre_id IN (?, ?)",
                Integer.class, CENTRE_A, CENTRE_B);
    }

    byte[] octetsEnBase(UUID document) {
        return jdbc.queryForObject("SELECT contenu FROM gmao_documents_intervention WHERE id = ?", byte[].class, document);
    }

    static String alias(UUID u) {
        if (u == null) return "null";
        if (u.equals(CENTRE_A)) return "<CENTRE_A>";
        if (u.equals(CENTRE_B)) return "<CENTRE_B>";
        if (u.equals(ADMIN_A)) return "<ADMIN_A>";
        if (u.equals(ADMIN_B)) return "<ADMIN_B>";
        if (u.equals(TECH)) return "<TECH>";
        if (u.equals(HABILITE)) return "<HABILITE>";
        return u.toString();
    }

    static Timestamp ts(OffsetDateTime t) {
        return Timestamp.from(t.toInstant());
    }

    /**
     * Fichier de {@code tailleTotale} octets commençant par {@code entete}. Depuis KB-GMAO-59 un PDF doit se terminer par son
     * marqueur de fin : le remplissage est donc inséré avant le marqueur {@code %%EOF\n} de l'entête quand il y en a un.
     */
    static byte[] avecEntete(byte[] entete, int tailleTotale) {
        byte[] fin = octets("%%EOF\n");
        boolean finale = entete.length >= fin.length
                && Arrays.equals(Arrays.copyOfRange(entete, entete.length - fin.length, entete.length), fin);
        int debutFin = finale ? tailleTotale - fin.length : tailleTotale;
        byte[] r = Arrays.copyOf(entete, tailleTotale);
        int fixe = finale ? entete.length - fin.length : entete.length;
        for (int i = fixe; i < debutFin; i++) r[i] = (byte) ('a' + (i % 26));
        if (finale) System.arraycopy(fin, 0, r, debutFin, fin.length);
        return r;
    }

    /** PNG complet minimal (signature, IHDR 1x1, IDAT, IEND) : depuis KB-GMAO-59 la structure entière est vérifiée. */
    static byte[] pngComplet() {
        java.io.ByteArrayOutputStream flux = new java.io.ByteArrayOutputStream();
        flux.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        blocPng(flux, "IHDR", new byte[]{0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0});
        blocPng(flux, "IDAT", new byte[]{0x78, (byte) 0x9C, 0x63, 0x60, 0x60, 0x60, 0, 0, 0, 4, 0, 1});
        blocPng(flux, "IEND", new byte[0]);
        return flux.toByteArray();
    }

    private static void blocPng(java.io.ByteArrayOutputStream flux, String type, byte[] donnees) {
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(t);
        crc.update(donnees);
        flux.writeBytes(entier32(donnees.length));
        flux.writeBytes(t);
        flux.writeBytes(donnees);
        flux.writeBytes(entier32(crc.getValue()));
    }

    private static byte[] entier32(long v) {
        return new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }

    static byte[] octets(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    // ------------------------------------------------------------------ normalisation / golden

    final class Scrubber {
        private final Resp resp;
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private int unknown = 0;

        Scrubber(Resp resp) {
            this.resp = resp;
            for (UUID u : List.of(CENTRE_A, CENTRE_B, ADMIN_A, ADMIN_B, TECH, HABILITE)) {
                aliases.put(u.toString(), DocumentsCharacterizationSupport.alias(u));
            }
            String[] noms = {"I_PLAN", "I_COURS", "I_TERM", "I_ANN", "I_ATT", "I_SUPPR", "I_B", "I_AUTRE"};
            for (int i = 0; i < noms.length; i++) aliases.put(id(11 + i).toString(), "<" + noms[i] + ">");
            aliases.put(I_INCONNU.toString(), "<I_INCONNU>");
            for (int n = 0; n < 100; n++) aliases.put(doc(n).toString(), String.format("<DOC_%02d>", n));
        }

        String scrubString(String raw0) {
            String raw = ENUM_RE.matcher(raw0).replaceAll("No enum constant <$1>.$2");
            Matcher m = UUID_RE.matcher(raw);
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
                n.properties().forEach(e -> {
                    String k = e.getKey();
                    if (technique && k.equals("detail")) {
                        o.set(k, mapper.getNodeFactory().stringNode("<message technique Spring/Jackson/SGBD non verrouille>"));
                    } else if (k.equals("ajouteLe") && !e.getValue().isNull()) {
                        o.set(k, mapper.getNodeFactory().stringNode("<HORLOGE>"));
                    } else {
                        o.set(k, scrub(e.getValue()));
                    }
                });
                return o;
            }
            if (n.isArray()) {
                ArrayNode a = mapper.createArrayNode();
                n.forEach(x -> a.add(scrub(x)));
                return a;
            }
            return n;
        }

        String render() {
            ObjectNode root = mapper.createObjectNode();
            root.put("status", resp.status);
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

        /** Résumé compact : « statut HTTP code detail » ou, si 2xx, les métadonnées stables du document. */
        String resume() {
            if (resp.raw == null || resp.raw.isBlank()) return resp.status + " (sans corps)";
            JsonNode j;
            try {
                j = mapper.readTree(resp.raw);
            } catch (RuntimeException e) {
                return resp.status + " bodyText=" + scrubString(resp.raw);
            }
            if (resp.status / 100 == 2) {
                return resp.status + " type=" + j.path("type").asString("?") + " nom=[" + j.path("nom").asString("?")
                        + "] contentType=" + j.path("contentType").asString("?") + " taille=" + j.path("taille").asString("?");
            }
            boolean technique = "INTERNAL_ERROR".equals(j.path("code").asString(""))
                    || "DATA_ACCESS_ERROR".equals(j.path("code").asString(""));
            return resp.status + " " + j.path("code").asString("") + " "
                    + (technique ? "<technique>" : scrubString(j.path("detail").asString("")));
        }
    }

    void golden(String name, Resp r) {
        GoldenMaster.verify(GOLDENS, name, new Scrubber(r).render());
    }

    void goldenNode(String name, JsonNode node) {
        GoldenMaster.verify(GOLDENS, name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
    }

    /** Golden d'une matrice : tableau JSON de lignes de texte. */
    void goldenLignes(String name, List<String> lignes) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < lignes.size(); i++) {
            sb.append("  ").append(mapper.writeValueAsString(rendreVisible(lignes.get(i))))
                    .append(i + 1 < lignes.size() ? ",\n" : "\n");
        }
        GoldenMaster.verify(GOLDENS, name, sb.append("]").toString());
    }

    /** Les caractères invisibles/de contrôle et les demi-paires de substitution deviennent {@code \\uXXXX} (golden relisible). */
    static String rendreVisible(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean paireValide = Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1));
            if (paireValide) {
                sb.append(c).append(s.charAt(++i));
            } else if (Character.isSurrogate(c) || c < 0x20 || (c >= 0x7f && c < 0xa0) || c == 0xa0 || c == 0x2028
                    || c == 0x2029 || (c >= 0x200b && c <= 0x200f) || (c >= 0x202a && c <= 0x202e) || c == 0xfeff) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    String resume(Resp r) {
        return new Scrubber(r).resume();
    }
}
