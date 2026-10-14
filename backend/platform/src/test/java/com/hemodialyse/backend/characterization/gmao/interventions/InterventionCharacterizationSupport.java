package com.hemodialyse.backend.characterization.gmao.interventions;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpMethod;
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
import java.time.Duration;
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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Harnais commun du filet de caractérisation de la slice gmao-g4-interventions : les neuf endpoints
 * {@code /api/v1/gmao/interventions} observés au niveau HTTP (MockMvc + chaîne de sécurité réelle + JPA réel sur H2),
 * leurs effets hors du contrôleur (équipement, journal des statuts, alerte « générateur indisponible », cache
 * {@code ref.generateurs}, lignes de coût, rectifications). Décrit le comportement ACTUEL, y compris les défauts
 * (voir .ddd/safety-net/known-bugs.md, KB-GMAO-42 et suivants).
 * <p>
 * Non-déterminisme : ids générés -> alias ; instants posés par {@code now()} (dateCreation, dateModification, clôture,
 * chronologie) -> {@code <HORLOGE>} ; dates de début/fin, échéances et tarifs viennent de la requête ou des fixtures,
 * donc la valorisation (durée x tarif) est exacte sans seam ; ordre des lignes de coût non garanti -> tri canonique.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class InterventionCharacterizationSupport {

    static final String BASE = "/api/v1/gmao/interventions";
    static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "gmao", "interventions");

    static final UUID CENTRE_A = UUID.fromString("99996400-0000-0000-0000-00000000000a");
    static final UUID CENTRE_B = UUID.fromString("99996400-0000-0000-0000-00000000000b");
    static final UUID ADMIN_A = UUID.fromString("99996400-0000-0000-0000-0000000000f1");
    static final UUID ADMIN_B = UUID.fromString("99996400-0000-0000-0000-0000000000f2");
    static final UUID TECH = UUID.fromString("99996400-0000-0000-0000-0000000000f3");
    static final UUID RECTIF = UUID.fromString("99996400-0000-0000-0000-0000000000f4");
    static final UUID RECTIF_SEUL = UUID.fromString("99996400-0000-0000-0000-0000000000f5");

    static final UUID GEN = id(1);
    static final UUID EAU = id(2);
    static final UUID REFORME = id(3);
    static final UUID AUTRE_CENTRE = id(4);
    static final UUID SUPPRIME = id(5);
    static final UUID INCONNU = id(99);

    static final UUID I_INTERNE = id(11);
    static final UUID I_EXTERNE = id(12);
    static final UUID I_SANS_TARIF = id(13);
    static final UUID I_TARIF_ZERO = id(14);
    static final UUID I_DESACTIVE = id(15);
    static final UUID I_AUTRE_CENTRE = id(16);
    static final UUID I_INCONNU = id(98);

    /** Début fixe des interventions : dans le passé (enRetard=true tant qu'elles sont planifiées). */
    static final OffsetDateTime DEBUT = OffsetDateTime.of(2026, 3, 10, 8, 0, 0, 0, ZoneOffset.UTC);
    static final OffsetDateTime INSTALLATION = OffsetDateTime.of(2020, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);

    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern INSTANT_RE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?Z");
    private static final Pattern ENUM_RE = Pattern.compile("No enum constant (?:\\w+\\.)+(\\w+)\\.(\\w+)");

    @Autowired
    WebApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CacheManager caches;

    /** Lecture en BigDecimal exacts : l'échelle JSON (45.50 vs 45.5) fait partie du contrat observé. */
    final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    MockMvc mvc;

    static UUID id(int n) {
        return UUID.fromString(String.format("99996400-0000-0000-0000-0000000001%02d", n));
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

    /** ADMIN + habilitation de rectification. */
    static RequestPostProcessor rectifieur() {
        return qui(CENTRE_A, RECTIF, "ADMIN", "GMAO_RECTIFICATION");
    }

    /** Habilitation de rectification sans ADMIN (KB-GMAO-32 : le @PreAuthorize de méthode remplace celui de la classe). */
    static RequestPostProcessor rectifieurSansAdmin() {
        return qui(CENTRE_A, RECTIF_SEUL, "GMAO_RECTIFICATION");
    }

    // ------------------------------------------------------------------ cycle des tests

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        caches.getCache("ref.generateurs").clear();
        equipement(GEN, "G-01", "Generateur 01", "GENERATEUR_DIALYSE", "EN_SERVICE", CENTRE_A, 1, null);
        equipement(EAU, "E-01", "Station eau", "STATION_TRAITEMENT_EAU", "EN_SERVICE", CENTRE_A, 2, null);
        equipement(REFORME, "G-RF", "Generateur reforme", "GENERATEUR_DIALYSE", "REFORME", CENTRE_A, 3, null);
        equipement(AUTRE_CENTRE, "G-B1", "Generateur centre B", "GENERATEUR_DIALYSE", "EN_SERVICE", CENTRE_B, 4, null);
        equipement(SUPPRIME, "G-DEL", "Generateur supprime", "GENERATEUR_DIALYSE", "EN_SERVICE", CENTRE_A, 5,
                OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        intervenant(I_INTERNE, CENTRE_A, "Bernard Technicien", "INTERNE", new BigDecimal("35.00"), true);
        intervenant(I_EXTERNE, CENTRE_A, "Acme Dialyse Services", "EXTERNE", new BigDecimal("120.50"), true);
        intervenant(I_SANS_TARIF, CENTRE_A, "Claire Sans Tarif", "INTERNE", null, true);
        intervenant(I_TARIF_ZERO, CENTRE_A, "Dina Tarif Zero", "INTERNE", BigDecimal.ZERO, true);
        intervenant(I_DESACTIVE, CENTRE_A, "Ancien Prestataire", "EXTERNE", new BigDecimal("80.00"), false);
        intervenant(I_AUTRE_CENTRE, CENTRE_B, "Prestataire Autre Centre", "EXTERNE", new BigDecimal("99.99"), true);
    }

    @AfterEach
    void cleanup() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM gmao_taches_intervention WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM gmao_rectifications_intervention WHERE intervention_id IN "
                    + "(SELECT id FROM gmao_interventions WHERE centre_id = ?)", c);
            jdbc.update("DELETE FROM gmao_lignes_cout_intervention WHERE intervention_id IN "
                    + "(SELECT id FROM gmao_interventions WHERE centre_id = ?)", c);
            jdbc.update("DELETE FROM gmao_interventions WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM gmao_equipement_statut_historique WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM gmao_equipements WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM gmao_intervenants WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM notification_evenement WHERE center_id = ?", c);
        }
    }

    void equipement(UUID id, String code, String designation, String type, String statut, UUID centre, int rang,
                    OffsetDateTime supprimeLe) {
        OffsetDateTime creeLe = OffsetDateTime.of(2026, 1, rang, 9, 0, 0, 0, ZoneOffset.UTC);
        jdbc.update("INSERT INTO gmao_equipements (id, code, designation, type, centre_id, statut, date_installation, "
                        + "date_creation, cree_par, deleted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, code, designation, type, centre, statut, INSTALLATION, creeLe, ADMIN_A, supprimeLe);
    }

    void intervenant(UUID id, UUID centre, String nom, String type, BigDecimal tarif, boolean actif) {
        jdbc.update("INSERT INTO gmao_intervenants (id, centre_id, nom, type, tarif_horaire_defaut, actif) "
                + "VALUES (?, ?, ?, ?, ?, ?)", id, centre, nom, type, tarif, actif);
    }

    // ------------------------------------------------------------------ appels HTTP

    static final class Resp {
        final int status;
        final String raw;

        Resp(int status, String raw) {
            this.status = status;
            this.raw = raw;
        }
    }

    Resp send(MockHttpServletRequestBuilder b) throws Exception {
        MockHttpServletResponse r = mvc.perform(b).andReturn().getResponse();
        attendreLaLivraisonDesEvenements();
        return new Resp(r.getStatus(), r.getContentAsString(StandardCharsets.UTF_8));
    }

    /**
     * L'alerte Planification et l'éviction du cache sont des écouteurs du registre d'événements Spring Modulith,
     * livrés après commit de façon asynchrone (refonte G3) : on attend que le registre soit vide avant d'observer.
     */
    void attendreLaLivraisonDesEvenements() {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(10)).until(
                () -> jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class) == 0);
    }

    Resp call(HttpMethod m, String path, String json, RequestPostProcessor who) throws Exception {
        MockHttpServletRequestBuilder b = request(m, BASE + path);
        if (who != null) {
            b.with(who);
        }
        if (json != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return send(b);
    }

    Resp get(String path, RequestPostProcessor who) throws Exception {
        return call(HttpMethod.GET, path, null, who);
    }

    Resp post(String path, String json, RequestPostProcessor who) throws Exception {
        return call(HttpMethod.POST, path, json, who);
    }

    Resp delete(String path, RequestPostProcessor who) throws Exception {
        return call(HttpMethod.DELETE, path, null, who);
    }

    JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    String code(Resp r) {
        try {
            return mapper.readTree(r.raw).path("code").asString("");
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ constructeurs de corps

    /** Corps JSON à partir de paires clé/valeur ; les valeurs {@code null} sont omises. */
    String body(Object... kv) {
        ObjectNode o = mapper.createObjectNode();
        for (int i = 0; i < kv.length; i += 2) {
            String k = (String) kv[i];
            Object v = kv[i + 1];
            if (v == null) continue;
            if (v instanceof BigDecimal bd) o.put(k, bd);
            else if (v instanceof Integer n) o.put(k, n);
            else if (v instanceof Boolean b) o.put(k, b);
            else o.put(k, v.toString());
        }
        return mapper.writeValueAsString(o);
    }

    String creation(UUID equipement, String etatAvant, UUID intervenant) {
        return body("equipementId", equipement, "type", "CURATIVE", "dateDebut", DEBUT, "description", "Panne pompe",
                "intervenantId", intervenant, "etatEquipementAvant", etatAvant);
    }

    /** Corps de création nominal (générateur G-01, état avant EN_SERVICE) dont une clé est remplacée ({@code null} = retirée). */
    String creationAvec(String cle, Object valeur) {
        return modifie(creation(GEN, "EN_SERVICE", null), cle, valeur);
    }

    /** Corps de clôture nominal (60 min, EN_SERVICE) dont une clé est remplacée ({@code null} = retirée). */
    String clotureAvec(String cle, Object valeur) {
        return modifie(cloture(60), cle, valeur);
    }

    String modifie(String json, String cle, Object valeur) {
        ObjectNode o = (ObjectNode) mapper.readTree(json);
        o.remove(cle);
        if (valeur instanceof BigDecimal bd) o.put(cle, bd);
        else if (valeur instanceof Integer n) o.put(cle, n);
        else if (valeur != null) o.put(cle, valeur.toString());
        return mapper.writeValueAsString(o);
    }

    String cloture(String etatApres, OffsetDateTime fin) {
        return body("actions", "Remplacement pompe", "etatEquipementApres", etatApres, "dateFin", fin);
    }

    String cloture(long minutes) {
        return cloture("EN_SERVICE", DEBUT.plusMinutes(minutes));
    }

    String ligne(String type, String libelle, Object quantite, Object prix) {
        return body("type", type, "libelle", libelle, "quantite", quantite, "prixUnitaire", prix);
    }

    static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    // ------------------------------------------------------------------ scénarios réutilisables

    /** Planifie une intervention (201 attendu) et renvoie son id. */
    UUID planifier(UUID equipement, String etatAvant, UUID intervenant) throws Exception {
        Resp r = post("", creation(equipement, etatAvant, intervenant), adminA());
        assertEquals(201, r.status, r.raw);
        return UUID.fromString(json(r).get("id").asString());
    }

    UUID planifier(UUID intervenant) throws Exception {
        return planifier(GEN, "EN_SERVICE", intervenant);
    }

    UUID demarree(UUID intervenant) throws Exception {
        UUID i = planifier(intervenant);
        Resp r = post("/" + i + "/demarrer", null, adminA());
        assertEquals(200, r.status, r.raw);
        return i;
    }

    /** Planifiée, démarrée puis clôturée en {@code minutes} (état après EN_SERVICE) ; renvoie la réponse de clôture. */
    Resp cloturer(UUID intervenant, long minutes) throws Exception {
        UUID i = demarree(intervenant);
        return post("/" + i + "/terminer", cloture(minutes), adminA());
    }

    UUID terminee(UUID intervenant, long minutes) throws Exception {
        UUID i = demarree(intervenant);
        Resp r = post("/" + i + "/terminer", cloture(minutes), adminA());
        assertEquals(200, r.status, r.raw);
        return i;
    }

    /** Amène une intervention dans un statut donné (EN_ATTENTE_VALIDATION n'a aucune commande : posé en base). */
    UUID dansLeStatut(String statut) throws Exception {
        return switch (statut) {
            case "PLANIFIEE" -> planifier(null);
            case "EN_COURS" -> demarree(null);
            case "TERMINEE" -> terminee(null, 60);
            case "ANNULEE" -> {
                UUID i = planifier(null);
                assertEquals(200, post("/" + i + "/annuler", body("raison", "doublon"), adminA()).status);
                yield i;
            }
            case "EN_ATTENTE_VALIDATION" -> {
                UUID i = planifier(null);
                jdbc.update("UPDATE gmao_interventions SET statut = 'EN_ATTENTE_VALIDATION' WHERE id = ?", i);
                yield i;
            }
            default -> throw new IllegalArgumentException(statut);
        };
    }

    // ------------------------------------------------------------------ observations hors HTTP

    Map<String, Object> row(UUID intervention) {
        return jdbc.queryForMap("SELECT * FROM gmao_interventions WHERE id = ?", intervention);
    }

    String statutIntervention(UUID intervention) {
        return (String) row(intervention).get("STATUT");
    }

    String statutEquipement(UUID equipement) {
        return jdbc.queryForObject("SELECT statut FROM gmao_equipements WHERE id = ?", String.class, equipement);
    }

    /** Lignes de coût « type|libelle|quantité|prix|automatique », triées (l'ordre de lecture n'est pas garanti). */
    List<String> lignes(UUID intervention) {
        return jdbc.queryForList("SELECT type, libelle, quantite, prix_unitaire, automatique FROM "
                        + "gmao_lignes_cout_intervention WHERE intervention_id = ?", intervention).stream()
                .map(m -> m.get("TYPE") + "|" + m.get("LIBELLE") + "|" + m.get("QUANTITE") + "|" + m.get("PRIX_UNITAIRE")
                        + "|" + m.get("AUTOMATIQUE"))
                .sorted().toList();
    }

    /** Historique « précédent>nouveau|motif » du plus ancien au plus récent. */
    List<String> historique(UUID equipement) {
        return jdbc.queryForList("SELECT statut_precedent, statut_nouveau, motif FROM gmao_equipement_statut_historique "
                        + "WHERE equipement_id = ? ORDER BY changed_at", equipement).stream()
                .map(m -> m.get("STATUT_PRECEDENT") + ">" + m.get("STATUT_NOUVEAU") + "|" + masquer((String) m.get("MOTIF")))
                .toList();
    }

    /** Alertes « générateur indisponible » du centre A : « générateur/statut/nbPatients/patients/roles ». */
    List<String> alertes() {
        return jdbc.queryForList("SELECT payload, target_roles FROM notification_evenement WHERE center_id = ? "
                        + "AND type = 'GENERATEUR_INDISPONIBLE' ORDER BY cree_le", CENTRE_A).stream().map(m -> {
            JsonNode p = mapper.readTree(String.valueOf(m.get("PAYLOAD")));
            return p.path("generateur").asString() + "/" + p.path("statut").asString() + "/" + p.path("nbPatients").asString()
                    + "/" + p.path("patients").asString() + "/roles" + m.get("TARGET_ROLES");
        }).toList();
    }

    /** Types de toutes les notifications durables du centre A (pour constater l'absence de notification de réforme). */
    List<String> typesDeNotifications() {
        return jdbc.queryForList("SELECT type FROM notification_evenement WHERE center_id = ? ORDER BY cree_le", String.class,
                CENTRE_A);
    }

    int nombreDInterventions(UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM gmao_interventions WHERE centre_id = ?", Integer.class, centre);
    }

    String masquer(String s) {
        return s == null ? null : INSTANT_RE.matcher(s).replaceAll("<HORLOGE>");
    }

    // ------------------------------------------------------------------ normalisation / golden

    final class Scrubber {
        private final Resp resp;
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private int unknown = 0;

        Scrubber(Resp resp) {
            this.resp = resp;
            alias(CENTRE_A, "CENTRE_A");
            alias(CENTRE_B, "CENTRE_B");
            alias(ADMIN_A, "ADMIN_A");
            alias(ADMIN_B, "ADMIN_B");
            alias(TECH, "TECH");
            alias(RECTIF, "RECTIFIEUR");
            alias(RECTIF_SEUL, "RECTIFIEUR_SANS_ADMIN");
            alias(GEN, "GENERATEUR_G01");
            alias(EAU, "STATION_EAU");
            alias(REFORME, "GENERATEUR_REFORME");
            alias(AUTRE_CENTRE, "GENERATEUR_AUTRE_CENTRE");
            alias(SUPPRIME, "GENERATEUR_SUPPRIME");
            alias(INCONNU, "INCONNU");
            alias(I_INTERNE, "INTERVENANT_INTERNE");
            alias(I_EXTERNE, "INTERVENANT_EXTERNE");
            alias(I_SANS_TARIF, "INTERVENANT_SANS_TARIF");
            alias(I_TARIF_ZERO, "INTERVENANT_TARIF_ZERO");
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
            if (n.isString()) {
                return mapper.getNodeFactory().stringNode(scrubString(n.asString()));
            }
            if (n.isObject()) {
                ObjectNode o = mapper.createObjectNode();
                String code = n.path("code").asString("");
                boolean technique = "INTERNAL_ERROR".equals(code) || "DATA_ACCESS_ERROR".equals(code);
                n.properties().forEach(e -> {
                    String k = e.getKey();
                    JsonNode v = e.getValue();
                    if (technique && k.equals("detail")) {
                        o.set(k, mapper.getNodeFactory().stringNode("<message technique Spring/Jackson/SGBD non verrouille>"));
                    } else if ((k.equals("dateCreation") || k.equals("dateModification") || k.equals("at") || k.equals("dateDemarrage")
                            || k.equals("dateAchevement")) && !v.isNull()) {
                        o.set(k, mapper.getNodeFactory().stringNode("<HORLOGE>"));
                    } else if (k.equals("lignesCout") && v.isArray()) {
                        o.set(k, scrub(trier(v)));
                    } else {
                        o.set(k, scrub(v));
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

        /** L'ordre de lecture des lignes de coût n'est pas garanti : tri canonique pour le golden. */
        private JsonNode trier(JsonNode lignes) {
            List<JsonNode> l = new ArrayList<>();
            lignes.forEach(l::add);
            l.sort(Comparator.comparing((JsonNode x) -> x.path("type").asString(""))
                    .thenComparing(x -> x.path("libelle").asString(""))
                    .thenComparing(x -> x.path("prixUnitaire").toString())
                    .thenComparing(x -> x.path("quantite").toString())
                    .thenComparing(x -> x.path("automatique").toString()));
            ArrayNode a = mapper.createArrayNode();
            l.forEach(a::add);
            return a;
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

        /** Résumé compact pour les matrices : « statut HTTP code message » (ou statut de l'intervention si 2xx). */
        String resume() {
            if (resp.raw == null || resp.raw.isBlank()) return resp.status + " (sans corps)";
            JsonNode j;
            try {
                j = mapper.readTree(resp.raw);
            } catch (RuntimeException e) {
                return resp.status + " bodyText=" + scrubString(resp.raw);
            }
            if (resp.status / 100 == 2) {
                return resp.status + " statut=" + j.path("statut").asString("?");
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

    String resume(Resp r) {
        return new Scrubber(r).resume();
    }

    /** Détail du corps d'erreur : on affiche le JSON brut scrubbé d'une réponse d'erreur pour découvrir ses clés. */
    String scrubbed(Resp r) {
        return new Scrubber(r).render();
    }
}
