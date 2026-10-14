package com.hemodialyse.backend.characterization.gmao.pilotage;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.RoleScopeFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Harnais commun du filet de caractérisation de la slice gmao-g6-consultation-pilotage : les lectures de la GMAO
 * (bon d'intervention PDF, indicateurs, chronologie, fiche équipement, disponibilité patient, statistiques GMAO) et du
 * pilotage direction (GET /api/v1/direction/gmao, /capacite) observées au niveau HTTP (MockMvc + chaîne de sécurité
 * réelle + JPA réel sur H2). Les tables {@code gmao_*} sont semées par SQL (valeurs exactes maîtrisées) ou écrites par
 * le NOUVEAU module (routes G1 à G5) pour vérifier la cohérence des lectures avec ses écritures.
 * Décrit le comportement ACTUEL, y compris les défauts (voir .ddd/safety-net/known-bugs.md, KB-GMAO-69 et suivants).
 * <p>
 * Non-déterminisme : ids -> alias déterministes ({@link #nom}) ; instants relatifs à « maintenant » (fenêtre de 12
 * mois de la fiche et des indicateurs) -> masqués ({@code <DATE>}) dans les goldens et vérifiés par assertion ;
 * durées d'indisponibilité en minutes entières, intervalles tous clos et inclus dans la fenêtre, donc exacts ;
 * horodatage d'édition du PDF -> normalisé dans le texte extrait.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class PilotageCharacterizationSupport {

    static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "gmao", "pilotage");
    static final String GMAO = "/api/v1/gmao";
    static final String DIRECTION = "/api/v1/direction";

    /** Alias lisibles des identifiants fixes, pour les goldens (rempli par {@link #nom}). */
    private static final Map<UUID, String> NOMS = new ConcurrentHashMap<>();

    static final UUID SOC_1 = nom("SOCIETE_1");
    static final UUID SOC_2 = nom("SOCIETE_2");
    static final UUID CENTRE_A = nom("CENTRE_A");
    static final UUID CENTRE_B = nom("CENTRE_B");
    static final UUID CENTRE_C = nom("CENTRE_AUTRE_SOCIETE");
    static final UUID ADMIN_A = nom("ADMIN_A");
    static final UUID ADMIN_B = nom("ADMIN_B");
    static final UUID TECH = nom("TECHNICIEN");
    static final UUID DIRECTEUR = nom("DIRECTEUR");
    static final UUID INTERVENANT_INTERNE = nom("INTERVENANT_INTERNE");
    static final UUID INTERVENANT_AUTRE_CENTRE = nom("INTERVENANT_AUTRE_CENTRE");
    static final UUID INCONNU = nom("INCONNU");

    /** Instant fixe des interventions semées pour les goldens à dates figées (bon, chronologie). */
    static final OffsetDateTime T0 = OffsetDateTime.of(2026, 3, 10, 8, 0, 0, 0, ZoneOffset.UTC);
    static final OffsetDateTime INSTALLATION = OffsetDateTime.of(2020, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);

    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern INSTANT_RE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?Z");
    private static final Pattern ENUM_RE = Pattern.compile("No enum constant (?:\\w+\\.)+(\\w+)\\.(\\w*)");

    @Autowired
    WebApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RoleScopeFilter roleScopeFilter;

    /** Lecture en BigDecimal exacts : l'échelle JSON (0.60, 45.50) fait partie du contrat observé. */
    final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    MockMvc mvc;
    /** « Maintenant » du test, tronqué à la seconde ; les dates relatives des fixtures en dérivent. */
    OffsetDateTime maintenant;

    static UUID nom(String label) {
        UUID u = UUID.nameUUIDFromBytes(("g6:" + label).getBytes(StandardCharsets.UTF_8));
        NOMS.put(u, label);
        return u;
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

    /** ADMIN + habilitation de réforme (écart décidé KB-GMAO-32 : la réforme exige les deux). */
    static RequestPostProcessor reformeur() {
        return qui(CENTRE_A, ADMIN_A, "ADMIN", "GMAO_REFORME");
    }

    static RequestPostProcessor roleSeul(String role) {
        return qui(CENTRE_A, TECH, role);
    }

    /** Compte direction rattaché à la société 1 (le périmètre vient du jeton, revérifié en base). */
    static RequestPostProcessor direction() {
        return user(new UserPrincipal(DIRECTEUR.toString(), null, "u-direction", "",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, SOC_1.toString()));
    }

    // ------------------------------------------------------------------ cycle des tests

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        maintenant = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC_1, "ZT-P6-S1", "Société Pilotage Un");
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC_2, "ZT-P6-S2", "Société Pilotage Deux");
        centre(CENTRE_A, "ZT-P6-A", "Centre Pilotage A", SOC_1);
        centre(CENTRE_B, "ZT-P6-B", "Centre Pilotage B", SOC_1);
        centre(CENTRE_C, "ZT-P6-C", "Centre Autre Société", SOC_2);
        utilisateur(ADMIN_A, "zt-p6-admin-a", "Samia Administratrice");
        utilisateur(TECH, "zt-p6-tech", "Karim Technicien");
        utilisateur(DIRECTEUR, "zt-p6-direction", "Directeur Un");
        jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", DIRECTEUR, SOC_1);
        intervenant(INTERVENANT_INTERNE, CENTRE_A, "Bernard Technicien", "INTERNE", new BigDecimal("35.00"));
        intervenant(INTERVENANT_AUTRE_CENTRE, CENTRE_B, "Prestataire Autre Centre", "EXTERNE", new BigDecimal("99.99"));
    }

    @AfterEach
    void cleanup() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B, CENTRE_C)) {
            jdbc.update("DELETE FROM gmao_documents_intervention WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM gmao_taches_intervention WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM gmao_rectifications_intervention WHERE intervention_id IN "
                    + "(SELECT id FROM gmao_interventions WHERE centre_id = ?)", c);
            jdbc.update("DELETE FROM gmao_lignes_cout_intervention WHERE intervention_id IN "
                    + "(SELECT id FROM gmao_interventions WHERE centre_id = ?)", c);
            jdbc.update("DELETE FROM gmao_interventions WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM gmao_plans_maintenance WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM gmao_equipement_statut_historique WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", c);
            jdbc.update("DELETE FROM gmao_equipements WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM gmao_intervenants WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM modele_document WHERE center_id = ?", c);
            jdbc.update("DELETE FROM notification_evenement WHERE center_id = ?", c);
        }
        jdbc.update("DELETE FROM app_user_societe WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE 'zt-p6-%')");
        jdbc.update("DELETE FROM app_user_societe WHERE societe_id IN (SELECT id FROM societes WHERE code LIKE 'ZT-P6-%')");
        jdbc.update("DELETE FROM app_user WHERE username LIKE 'zt-p6-%'");
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-P6-%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-P6-%'");
    }

    // ------------------------------------------------------------------ fixtures SQL

    void centre(UUID id, String code, String name, UUID societe) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", id, code, name, societe);
    }

    void utilisateur(UUID id, String username, String nomComplet) {
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)",
                id, username, "x", null, nomComplet);
    }

    void intervenant(UUID id, UUID centre, String nom, String type, BigDecimal tarif) {
        jdbc.update("INSERT INTO gmao_intervenants (id, centre_id, nom, type, tarif_horaire_defaut, actif) "
                + "VALUES (?, ?, ?, ?, ?, TRUE)", id, centre, nom, type, tarif);
    }

    /** Paramètres d'un équipement semé (valeurs par défaut : générateur en service, installé le 15/01/2020, sans prix). */
    static final class Equip {
        String code;
        String designation;
        String type = "GENERATEUR_DIALYSE";
        String fabricant;
        String modele;
        String serie;
        String statut = "EN_SERVICE";
        BigDecimal prix;
        OffsetDateTime installation = INSTALLATION;
        OffsetDateTime supprimeLe;
    }

    UUID equipement(String label, UUID centre, Consumer<Equip> parametres) {
        Equip e = new Equip();
        e.code = "P6-" + label;
        e.designation = "Equipement " + label;
        parametres.accept(e);
        UUID id = nom(label);
        jdbc.update("INSERT INTO gmao_equipements (id, code, designation, type, fabricant, modele, numero_serie, "
                        + "date_installation, centre_id, statut, date_creation, cree_par, deleted_at, prix_acquisition) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, e.code, e.designation, e.type, e.fabricant, e.modele, e.serie, e.installation, centre, e.statut,
                OffsetDateTime.of(2026, 1, 5, 9, 0, 0, 0, ZoneOffset.UTC), ADMIN_A, e.supprimeLe, e.prix);
        return id;
    }

    UUID equipement(String label, UUID centre) {
        return equipement(label, centre, e -> {
        });
    }

    void historique(UUID equipement, UUID centre, String precedent, String nouveau, OffsetDateTime quand) {
        jdbc.update("INSERT INTO gmao_equipement_statut_historique (id, equipement_id, centre_id, statut_precedent, "
                        + "statut_nouveau, motif, changed_at, changed_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), equipement, centre, precedent, nouveau, "motif", quand, ADMIN_A);
    }

    /** Paramètres d'une intervention semée (valeurs par défaut : curative terminée du 10/03/2026, 2 h). */
    static final class Interv {
        String type = "CURATIVE";
        String statut = "TERMINEE";
        OffsetDateTime debut = T0;
        OffsetDateTime fin = T0.plusHours(2);
        OffsetDateTime creation = T0.minusHours(1);
        OffsetDateTime modification;
        OffsetDateTime demarreLe;
        OffsetDateTime clotureLe;
        OffsetDateTime annuleLe;
        OffsetDateTime echeance;
        OffsetDateTime supprimeLe;
        UUID intervenant;
        UUID creePar = ADMIN_A;
        UUID demarrePar;
        UUID cloturePar;
        UUID annulePar;
        UUID modifiePar;
        String description = "Panne pompe";
        String symptome;
        String cause;
        String actions;
        String pieceRemplacee;
        String priorite = "NORMALE";
        String etatAvant;
        String etatApres;
    }

    UUID intervention(String label, UUID equipement, UUID centre, Consumer<Interv> parametres) {
        Interv i = new Interv();
        parametres.accept(i);
        UUID id = nom(label);
        jdbc.update("INSERT INTO gmao_interventions (id, equipement_id, centre_id, type, statut, date_debut, date_fin, "
                        + "intervenant_id, description, actions, piece_remplacee, date_creation, date_modification, cree_par, "
                        + "modifie_par, deleted_at, priorite, echeance, symptome, cause, demarre_par, demarre_le, cloture_par, "
                        + "cloture_le, annule_par, annule_le, etat_equipement_avant, etat_equipement_apres) VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, equipement, centre, i.type, i.statut, i.debut, i.fin, i.intervenant, i.description, i.actions,
                i.pieceRemplacee, i.creation, i.modification, i.creePar, i.modifiePar, i.supprimeLe, i.priorite,
                i.echeance, i.symptome, i.cause, i.demarrePar, i.demarreLe, i.cloturePar, i.clotureLe, i.annulePar,
                i.annuleLe, i.etatAvant, i.etatApres);
        return id;
    }

    /** Intervention terminée, sans autre particularité, démarrée à {@code debut} (durée {@code minutes}). */
    UUID terminee(String label, UUID equipement, UUID centre, OffsetDateTime debut, long minutes) {
        return intervention(label, equipement, centre, i -> {
            i.debut = debut;
            i.fin = debut.plusMinutes(minutes);
            i.creation = debut.minusHours(1);
        });
    }

    void ligne(UUID intervention, String type, String libelle, String quantite, String prix) {
        jdbc.update("INSERT INTO gmao_lignes_cout_intervention (id, intervention_id, type, libelle, quantite, "
                        + "prix_unitaire, automatique) VALUES (?, ?, ?, ?, ?, ?, FALSE)",
                UUID.randomUUID(), intervention, type, libelle, new BigDecimal(quantite), new BigDecimal(prix));
    }

    void rectification(UUID intervention, String motif, UUID par, OffsetDateTime le, OffsetDateTime clotureAnterieureLe,
                       UUID clotureAnterieurePar) {
        jdbc.update("INSERT INTO gmao_rectifications_intervention (id, intervention_id, motif, par, le, "
                        + "cloture_anterieure_le, cloture_anterieure_par) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), intervention, motif, par, le, clotureAnterieureLe, clotureAnterieurePar);
    }

    UUID plan(String label, UUID equipement, UUID centre, String statut, OffsetDateTime prochaine, OffsetDateTime supprimeLe) {
        UUID id = nom(label);
        jdbc.update("INSERT INTO gmao_plans_maintenance (id, equipement_id, centre_id, designation, description, "
                        + "frequence, statut, prochaine_date_prevue, derniere_date_execution, nombre_executions, "
                        + "tachesaeffectuer, date_creation, cree_par, deleted_at) VALUES "
                        + "(?, ?, ?, ?, ?, 'MENSUELLE', ?, ?, NULL, 0, 'Contrôle', ?, ?, ?)",
                id, equipement, centre, "Plan " + label, "Description " + label, statut, prochaine,
                OffsetDateTime.of(2026, 1, 6, 9, 0, 0, 0, ZoneOffset.UTC), ADMIN_A, supprimeLe);
        return id;
    }

    /** Patient affecté à un générateur (alerte de sécurité patient du pilotage direction). */
    void patientSur(UUID centre, UUID generateur, String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, numero_assurance, "
                        + "date_admission, type_patient, qualite_assure, sous_kt, epo_enabled, created_at, generateur_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,FALSE,CURRENT_TIMESTAMP,?)",
                id, centre, code, "NOM", "Prenom", "M", java.sql.Date.valueOf("1970-05-05"), "ASS-" + code,
                java.sql.Date.valueOf("2026-01-02"), "PERMANENT", "ASSURE", false, generateur);
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

    Resp send(MockHttpServletRequestBuilder b) throws Exception {
        Resp r = new Resp(mvc.perform(b).andReturn().getResponse());
        attendreLaLivraisonDesEvenements();
        return r;
    }

    /** Écouteurs du registre d'événements (alerte, cache) livrés après commit : attendre le registre vide avant d'observer. */
    void attendreLaLivraisonDesEvenements() {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(10)).until(
                () -> jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class) == 0);
    }

    Resp call(HttpMethod m, String path, String json, RequestPostProcessor who) throws Exception {
        MockHttpServletRequestBuilder b = request(m, path);
        if (who != null) b.with(who);
        if (json != null) b.contentType(MediaType.APPLICATION_JSON).content(json);
        return send(b);
    }

    Resp get(String path, RequestPostProcessor who) throws Exception {
        return call(HttpMethod.GET, path, null, who);
    }

    /** GET avec un paramètre de requête transmis tel quel (sans réencodage du modèle d'URI). */
    Resp getParam(String path, RequestPostProcessor who, String nom, String... valeurs) throws Exception {
        MockHttpServletRequestBuilder b = request(HttpMethod.GET, path).param(nom, valeurs);
        if (who != null) b.with(who);
        return send(b);
    }

    Resp post(String path, String json, RequestPostProcessor who) throws Exception {
        return call(HttpMethod.POST, path, json, who);
    }

    JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    String body(Object... kv) {
        ObjectNode o = mapper.createObjectNode();
        for (int i = 0; i < kv.length; i += 2) {
            String k = (String) kv[i];
            Object v = kv[i + 1];
            if (v == null) continue;
            if (v instanceof BigDecimal bd) o.put(k, bd);
            else if (v instanceof Integer n) o.put(k, n);
            else o.put(k, v.toString());
        }
        return mapper.writeValueAsString(o);
    }

    // ------------------------------------------------------------------ écritures par le NOUVEAU module (cohérence)

    /** Planifie une intervention par la route du nouveau module (201 attendu) et renvoie son id. */
    UUID planifierParHttp(UUID equipement, String etatAvant, UUID intervenant, OffsetDateTime debut) throws Exception {
        Resp r = post(GMAO + "/interventions", body("equipementId", equipement, "type", "CURATIVE", "dateDebut", debut,
                "description", "Panne pompe", "intervenantId", intervenant, "etatEquipementAvant", etatAvant), adminA());
        assertEquals(201, r.status, r.raw);
        return UUID.fromString(json(r).get("id").asString());
    }

    void demarrerParHttp(UUID intervention) throws Exception {
        Resp r = post(GMAO + "/interventions/" + intervention + "/demarrer", null, adminA());
        assertEquals(200, r.status, r.raw);
    }

    void terminerParHttp(UUID intervention, String etatApres, OffsetDateTime fin) throws Exception {
        Resp r = post(GMAO + "/interventions/" + intervention + "/terminer",
                body("actions", "Remplacement pompe", "etatEquipementApres", etatApres, "dateFin", fin), adminA());
        assertEquals(200, r.status, r.raw);
    }

    // ------------------------------------------------------------------ PDF

    /** Texte d'un PDF (extraction PDFBox), fins de ligne normalisées. */
    static String texteDuPdf(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc).replace("\r\n", "\n");
        }
    }

    static int pagesDuPdf(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    /**
     * Texte du bon (200 attendu) : seul l'horodatage d'édition (« Édité le », posé par {@code now()} côté serveur) est
     * masqué ; les identifiants connus sont remplacés par leur alias.
     */
    String textePdf(Resp r) throws Exception {
        assertEquals(200, r.status, r.raw);
        String t = texteDuPdf(r.bytes).replaceAll("Édité le \\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}", "Édité le <DATE>");
        return new Scrubber(r, false).scrubString(t);
    }

    /** Espaces insécables et fines remplacés par une espace simple (assertions de montants). */
    static String espacesSimples(String s) {
        return s.replaceAll("[\\u00a0\\u202f]", " ");
    }

    // ------------------------------------------------------------------ normalisation / golden

    String masquerInstants(String s) {
        return INSTANT_RE.matcher(s).replaceAll("<DATE>");
    }

    final class Scrubber {
        private final Resp resp;
        private final boolean masquerLesInstants;
        private final Map<String, String> aliases = new java.util.LinkedHashMap<>();
        private int inconnus = 0;

        Scrubber(Resp resp, boolean masquerLesInstants) {
            this.resp = resp;
            this.masquerLesInstants = masquerLesInstants;
            NOMS.forEach((u, label) -> aliases.put(u.toString(), "<" + label + ">"));
        }

        String scrubString(String raw0) {
            String raw = ENUM_RE.matcher(raw0).replaceAll("No enum constant <$1>.$2");
            Matcher m = UUID_RE.matcher(raw);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String a = aliases.computeIfAbsent(m.group(), k -> "<UUID_GENERE_" + (++inconnus) + ">");
                m.appendReplacement(sb, Matcher.quoteReplacement(a));
            }
            m.appendTail(sb);
            String s = sb.toString();
            return masquerLesInstants ? masquerInstants(s) : s;
        }

        JsonNode scrub(JsonNode n) {
            if (n.isString()) return mapper.getNodeFactory().stringNode(scrubString(n.asString()));
            if (n.isObject()) {
                ObjectNode o = mapper.createObjectNode();
                String code = n.path("code").asString("");
                boolean technique = "INTERNAL_ERROR".equals(code) || "DATA_ACCESS_ERROR".equals(code);
                n.properties().forEach(e -> {
                    String k = e.getKey();
                    JsonNode v = e.getValue();
                    if (technique && k.equals("detail")) {
                        o.set(k, mapper.getNodeFactory().stringNode("<message technique Spring/Jackson/SGBD non verrouille>"));
                    } else if (k.equals("generatedAt") && !v.isNull()) {
                        o.set(k, mapper.getNodeFactory().stringNode("<HORLOGE>"));
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

        /** Résumé compact pour les matrices : « statut HTTP code message ». */
        String resume() {
            if (resp.raw == null || resp.raw.isBlank()) return resp.status + " (sans corps)";
            JsonNode j;
            try {
                j = mapper.readTree(resp.raw);
            } catch (RuntimeException e) {
                return resp.status + " bodyText=" + scrubString(resp.raw);
            }
            if (resp.status / 100 == 2) return resp.status + " ok";
            boolean technique = "INTERNAL_ERROR".equals(j.path("code").asString(""))
                    || "DATA_ACCESS_ERROR".equals(j.path("code").asString(""));
            return resp.status + " " + j.path("code").asString("") + " "
                    + (technique ? "<technique>" : scrubString(j.path("detail").asString("")));
        }
    }

    /** Golden de la réponse (dates des fixtures figées conservées). */
    void golden(String name, Resp r) {
        GoldenMaster.verify(GOLDENS, name, new Scrubber(r, false).render());
    }

    /** Golden de la réponse dont les instants (relatifs à « maintenant ») sont masqués. */
    void goldenRelatif(String name, Resp r) {
        GoldenMaster.verify(GOLDENS, name, new Scrubber(r, true).render());
    }

    /** Golden d'un texte : une ligne du texte par ligne du golden. */
    void goldenTexte(String name, String texte) {
        goldenLignes(name, texte.lines().toList());
    }

    /** Golden d'une matrice : tableau JSON de lignes de texte. */
    void goldenLignes(String name, List<String> lignes) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < lignes.size(); i++) {
            sb.append("  ").append(mapper.writeValueAsString(lignes.get(i))).append(i + 1 < lignes.size() ? ",\n" : "\n");
        }
        GoldenMaster.verify(GOLDENS, name, sb.append("]").toString());
    }

    String resume(Resp r) {
        return new Scrubber(r, true).resume();
    }
}
