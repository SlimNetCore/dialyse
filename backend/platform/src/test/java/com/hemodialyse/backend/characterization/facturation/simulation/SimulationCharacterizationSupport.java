package com.hemodialyse.backend.characterization.facturation.simulation;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Date;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Harnais du filet de caractérisation de la slice facturation-f2-simulation : aperçu de facturation, exclusion d'une séance
 * et changement de forfait ({@code /api/v1/facturation/preview…}) observés en HTTP (MockMvc, sécurité réelle +
 * RoleScopeFilter, H2) et état SQL des séances. Aucun mock du code caractérisé.
 * <p>
 * Non-déterminisme maîtrisé : le centre A a un modèle de numérotation sans année ({@code SIM-{SEQ}}), une séquence
 * courante posée pour l'année en cours et un type de TVA ouvert depuis 2000 (la simulation date la facturation du jour) ;
 * identifiants connus remplacés par des alias lisibles, identifiants générés et horodatages normalisés ; clés JSON triées.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class SimulationCharacterizationSupport {

    protected static final String BASE = "/api/v1/facturation";
    protected static final UUID CENTRE_A = UUID.fromString("99997000-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99997000-0000-0000-0000-00000000000b");
    protected static final UUID FORFAIT_HD = UUID.fromString("99997000-0000-0000-0000-0000000f0001");
    protected static final UUID FORFAIT_PREMIUM = UUID.fromString("99997000-0000-0000-0000-0000000f0002");
    protected static final UUID FORFAIT_B = UUID.fromString("99997000-0000-0000-0000-0000000f00b1");
    protected static final UUID P_EFFECTIF = UUID.fromString("99997000-0000-0000-0000-00000000a001");
    protected static final UUID P_DEMANDE = UUID.fromString("99997000-0000-0000-0000-00000000a002");
    protected static final UUID P_SANS_PEC = UUID.fromString("99997000-0000-0000-0000-00000000a003");
    protected static final UUID P_PEC_ECHUE = UUID.fromString("99997000-0000-0000-0000-00000000a004");
    protected static final UUID P_CENTRE_B = UUID.fromString("99997000-0000-0000-0000-00000000b001");
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "facturation", "simulation");
    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HORODATAGE_RE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}(:?\\d{2})?|Z)?");
    protected final ObjectMapper mapper = JsonMapper.builder().build();
    private final Map<UUID, String> alias = new LinkedHashMap<>();
    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected com.hemodialyse.backend.infrastructure.security.RoleScopeFilter roleScopeFilter;
    protected MockMvc mvc;

    protected static RequestPostProcessor as(UUID centre, String... roles) {
        return user(UserPrincipal.create(UUID.randomUUID().toString(), centre.toString(), "u", "", List.of(roles), true));
    }

    protected static RequestPostProcessor adminA() {
        return as(CENTRE_A, "ADMIN");
    }

    protected static String json(String corps) {
        return corps.replace('\'', '"');
    }

    protected static String mois(UUID centre, String mois, boolean regroupement) {
        return json("{'centerId':'" + centre + "','month':'" + mois + "','regroupementMultiForfait':" + regroupement + "}");
    }

    private static Date date(String jour) {
        return jour == null ? null : Date.valueOf(LocalDate.parse(jour));
    }

    private static Resp reponse(MockHttpServletResponse r) throws Exception {
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(StandardCharsets.UTF_8));
    }

    protected abstract String theme();

    // ------------------------------------------------------------------ données

    @BeforeEach
    void setUpSimulation() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        cleanupSimulation();
        alias.clear();
        nommer(CENTRE_A, "<CENTRE_A>");
        nommer(CENTRE_B, "<CENTRE_B>");
        nommer(FORFAIT_HD, "<FORFAIT_HD>");
        nommer(FORFAIT_PREMIUM, "<FORFAIT_PREMIUM>");
        nommer(FORFAIT_B, "<FORFAIT_B>");
        nommer(P_EFFECTIF, "<P_EFFECTIF>");
        nommer(P_DEMANDE, "<P_DEMANDE>");
        nommer(P_SANS_PEC, "<P_SANS_PEC>");
        nommer(P_PEC_ECHUE, "<P_PEC_ECHUE>");
        nommer(P_CENTRE_B, "<P_CENTRE_B>");
        referentiel();
    }

    @AfterEach
    void cleanupSimulation() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM seances WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM forfait WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM tva_types WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM facturation_settings WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM facture_sequence WHERE center_id = ?", centre);
        }
    }

    protected void nommer(UUID id, String nom) {
        alias.put(id, nom);
    }

    /**
     * Forfaits, patients et PEC des deux centres, paramètres et TVA du centre A (9 % depuis 2000), séquence courante 41.
     */
    private void referentiel() {
        forfait(FORFAIT_HD, CENTRE_A, "F-HD", "Forfait HD", "3500.00");
        forfait(FORFAIT_PREMIUM, CENTRE_A, "F-HDP", "Forfait HD Premium", "5200.00");
        forfait(FORFAIT_B, CENTRE_B, "F-B", "Forfait du centre B", "1000.00");
        patient(P_EFFECTIF, CENTRE_A, "SIM-001", "Amrani", "Lina");
        patient(P_DEMANDE, CENTRE_A, "SIM-002", "Belkacem", "Omar");
        patient(P_SANS_PEC, CENTRE_A, "SIM-003", "Chikhi", "Nadia");
        patient(P_PEC_ECHUE, CENTRE_A, "SIM-004", "Djebbar", "Yanis");
        patient(P_CENTRE_B, CENTRE_B, "SIM-B01", "Zerrouki", "Rania");
        // PEC effective couvrant 2026 (forfait demandé premium, effectif HD : l'effectif l'emporte)
        pec(P_EFFECTIF, CENTRE_A, "2026-01-01", "2026-12-31", "2026-01-01", "2026-12-31", FORFAIT_PREMIUM, FORFAIT_HD, 1);
        // PEC seulement demandée couvrant 2026, forfait premium
        pec(P_DEMANDE, CENTRE_A, "2026-01-01", "2026-12-31", null, null, FORFAIT_PREMIUM, null, 1);
        // PEC échue le 2026-06-30 : repli sur la PEC la plus récente
        pec(P_PEC_ECHUE, CENTRE_A, "2026-01-01", "2026-06-30", "2026-01-01", "2026-06-30", FORFAIT_HD, FORFAIT_HD, 1);
        pec(P_CENTRE_B, CENTRE_B, "2026-01-01", "2026-12-31", "2026-01-01", "2026-12-31", FORFAIT_B, FORFAIT_B, 1);
        jdbc.update("INSERT INTO facturation_settings (center_id, code_format, regroupement_multi_forfait, updated_at, updated_by) "
                + "VALUES (?, 'SIM-{SEQ}', TRUE, CURRENT_TIMESTAMP, 'fixture')", CENTRE_A);
        jdbc.update("INSERT INTO facture_sequence (center_id, seq_year, seq_value) VALUES (?, ?, 41)", CENTRE_A,
                LocalDate.now().getYear());
        tva(CENTRE_A, "9.00");
    }

    protected void tva(UUID centre, String taux) {
        jdbc.update("INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere, date_debut_validite, actif, "
                        + "created_by) VALUES (?, ?, 'TVA', ?, 'HEMODIALYSE', FALSE, DATE '2000-01-01', TRUE, 'fixture')",
                UUID.randomUUID(), centre, new BigDecimal(taux));
    }

    protected void forfait(UUID id, UUID centre, String code, String libelle, String prix) {
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, ?, ?, ?)", id, centre, code, libelle,
                new BigDecimal(prix));
    }

    protected void patient(UUID id, UUID centre, String code, String nom, String prenom) {
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, created_at) VALUES (?, ?, ?, ?, ?, 'F', ?, ?, 'NON_VACANCIER', 'PERMANENT', ?)",
                id, centre, code, nom, prenom, Date.valueOf(LocalDate.of(2026, 1, 10)), "ASS-" + code,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    protected void pec(UUID patient, UUID centre, String debutDemande, String finDemande, String debutEffectif,
                       String finEffectif, UUID forfaitDemande, UUID forfaitEffectif, int anciennete) {
        jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, date_debut_demande, date_fin_demande, "
                        + "date_debut_effectif, date_fin_effectif, forfait_demande_id, forfait_effectif_id, statut, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'VALIDEE', ?)",
                UUID.randomUUID(), centre, patient, date(debutDemande), date(finDemande), date(debutEffectif), date(finEffectif),
                forfaitDemande, forfaitEffectif, OffsetDateTime.of(2026, 1, anciennete, 8, 0, 0, 0, ZoneOffset.UTC));
    }

    /**
     * Une séance du patient, nommée dans les goldens ({@code <S_nom>}) ; renvoie son identifiant.
     */
    protected UUID seance(String nom, UUID patient, UUID centre, String jour, String statut) {
        UUID id = UUID.randomUUID();
        nommer(id, "<S_" + nom + ">");
        OffsetDateTime maintenant = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, validated_at, "
                        + "signed_infirmier_at, signed_medecin_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, patient, centre, Date.valueOf(LocalDate.parse(jour)), statut, maintenant, maintenant, maintenant, maintenant);
        return id;
    }

    // ------------------------------------------------------------------ requêtes

    /**
     * Surcharge de forfait déjà posée sur la séance (comme par l'écran de séance, RG-SEA-033).
     */
    protected void surcharge(UUID seance, UUID forfait, String libelle, String prix) {
        jdbc.update("UPDATE seances SET forfait_override_id = ?, forfait_override_nom = ?, forfait_override_prix = ? WHERE id = ?",
                forfait, libelle, new BigDecimal(prix), seance);
    }

    protected Resp doPost(String path, String corps, RequestPostProcessor who) throws Exception {
        MockHttpServletResponse r = mvc.perform(post(java.net.URI.create(BASE + path)).contentType(MediaType.APPLICATION_JSON)
                .content(corps).with(who)).andReturn().getResponse();
        return reponse(r);
    }

    protected Resp doPostAnonyme(String path, String corps) throws Exception {
        MockHttpServletResponse r = mvc.perform(post(java.net.URI.create(BASE + path)).contentType(MediaType.APPLICATION_JSON)
                .content(corps)).andReturn().getResponse();
        return reponse(r);
    }

    protected JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    // ------------------------------------------------------------------ golden

    protected void golden(String name, Resp r) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, new Scrubber().render(r));
    }

    /**
     * État des séances du centre (colonnes de facturation), triées par date puis alias.
     */
    protected void goldenSeances(String name, UUID centre) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, date_seance, statut, facture_id, forfait_override_id, "
                + "forfait_override_nom, forfait_override_prix, forfait_override_updated_by, "
                + "(forfait_override_updated_at IS NULL) AS override_date_nulle FROM seances WHERE center_id = ?", centre);
        Scrubber s = new Scrubber();
        List<ObjectNode> objets = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            ObjectNode o = mapper.createObjectNode();
            row.forEach((k, v) -> o.put(k.toLowerCase(), v == null ? null : s.scrubString(String.valueOf(v))));
            objets.add(o);
        }
        objets.sort(Comparator.comparing((ObjectNode o) -> o.path("date_seance").asString())
                .thenComparing(o -> o.path("id").asString()));
        ArrayNode arr = mapper.createArrayNode();
        objets.forEach(arr::add);
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
    }

    protected record Resp(int status, String contentType, String raw) {
    }

    /**
     * Normalise : identifiants connus -> alias, autres UUID -> {@code <UUID_GENERE_n>}, horodatages -> {@code <HORODATAGE>},
     * clés triées, messages techniques non verrouillés.
     */
    private final class Scrubber {
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private int inconnus = 0;

        Scrubber() {
            alias.forEach((id, nom) -> aliases.put(id.toString(), nom));
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
