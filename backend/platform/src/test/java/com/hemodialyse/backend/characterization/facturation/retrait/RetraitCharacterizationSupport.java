package com.hemodialyse.backend.characterization.facturation.retrait;

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

/**
 * Harnais du filet de la slice facturation-f6-retrait-bc-facturation : consommateurs de la Facturation hors du contexte
 * (Séance, Stock, Absences, Migration, Comptabilité) observés en HTTP (MockMvc, Spring complet, sécurité réelle +
 * RoleScopeFilter, H2). Aucun mock du code caractérisé, aucun seam.
 * <p>
 * Non-déterminisme : tous les identifiants de fixture sont fixes et NOMMÉS ({@code <CENTRE_A>}, {@code <SEANCE_FACTUREE>}…) ;
 * tout autre UUID devient {@code <UUID_GENERE_n>} (aucun ne doit apparaître dans un golden) ; les horodatages deviennent
 * {@code <HORODATAGE>} ; l'unique alias de date est {@code <AUJOURD_HUI>} (jour d'exécution). Les dates des fixtures
 * sont des constantes de 2026 indépendantes du jour d'exécution.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class RetraitCharacterizationSupport {

    protected static final UUID CENTRE_A = UUID.fromString("99997600-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99997600-0000-0000-0000-00000000000b");
    protected static final UUID PATIENT_1 = UUID.fromString("99997600-0000-0000-0000-00000000a001");
    protected static final UUID PATIENT_2 = UUID.fromString("99997600-0000-0000-0000-00000000a002");
    protected static final UUID PATIENT_B = UUID.fromString("99997600-0000-0000-0000-00000000b001");
    protected static final UUID PAYEUR_1 = UUID.fromString("99997600-0000-0000-0000-0000000000e1");
    protected static final UUID AGENCE_1 = UUID.fromString("99997600-0000-0000-0000-000000000a01");
    protected static final UUID CAISSE_1 = UUID.fromString("99997600-0000-0000-0000-000000000c01");
    protected static final UUID FORFAIT_1 = UUID.fromString("99997600-0000-0000-0000-000000000f01");
    protected static final UUID ARTICLE_1 = UUID.fromString("99997600-0000-0000-0000-000000000b01");
    protected static final UUID LOT_1 = UUID.fromString("99997600-0000-0000-0000-000000000b02");
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "facturation", "retrait");
    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HORODATAGE_RE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}(:?\\d{2})?|Z)?");
    private static final Pattern CODE_PATIENT_RE = Pattern.compile("PAT-[0-9A-F]{8}\\b");
    private static final Pattern CODE_REGLEMENT_RE = Pattern.compile("REG-[0-9A-F]{8}\\b|REP-[0-9A-F]{8}\\b");
    protected final ObjectMapper mapper = JsonMapper.builder().build();
    private final Map<UUID, String> alias = new LinkedHashMap<>();
    private final Map<String, String> datesNommees = new LinkedHashMap<>();
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

    /**
     * Utilisateur d'identifiant fixe (nommé par le test) : {@code saisi_par} des règlements prend l'identifiant du principal.
     */
    protected static RequestPostProcessor asUtilisateur(UUID utilisateur, UUID centre, String... roles) {
        return user(UserPrincipal.create(utilisateur.toString(), centre.toString(), "u", "", List.of(roles), true));
    }

    protected static RequestPostProcessor adminA() {
        return as(CENTRE_A, "ADMIN");
    }

    protected static String json(String corps) {
        return corps.replace('\'', '"');
    }

    protected static Date date(String jour) {
        return jour == null ? null : Date.valueOf(LocalDate.parse(jour));
    }

    /**
     * Thème (sous-dossier de goldens) de la classe de test.
     */
    protected abstract String theme();

    /**
     * Jour relatif à l'exécution (règles à fenêtre glissante, ex. 60 jours de délai de déclaration d'une absence) : sa date
     * est NOMMÉE ({@code nom}) dans tous les goldens, qui ne dépendent donc pas du jour d'exécution.
     */
    protected String jour(int joursAvant, String nom) {
        String d = LocalDate.now().minusDays(joursAvant).toString();
        datesNommees.put(d, nom);
        return d;
    }

    protected void nommer(UUID id, String nom) {
        alias.put(id, nom);
    }

    @BeforeEach
    void setUpRetrait() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        nettoyerRetrait();
        alias.clear();
        datesNommees.clear();
        nommer(CENTRE_A, "<CENTRE_A>");
        nommer(CENTRE_B, "<CENTRE_B>");
        nommer(PATIENT_1, "<PATIENT_1>");
        nommer(PATIENT_2, "<PATIENT_2>");
        nommer(PATIENT_B, "<PATIENT_B>");
        nommer(PAYEUR_1, "<PAYEUR_1>");
        nommer(AGENCE_1, "<AGENCE_1>");
        nommer(CAISSE_1, "<CAISSE_1>");
        nommer(FORFAIT_1, "<FORFAIT_1>");
        nommer(ARTICLE_1, "<ARTICLE_1>");
        nommer(LOT_1, "<LOT_1>");
    }

    @AfterEach
    void nettoyerRetrait() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM lignes_ecriture WHERE ecriture_id IN "
                    + "(SELECT id FROM ecritures_comptables WHERE center_id = ?)", centre);
            for (String table : List.of("ecritures_comptables", "comptes_payeurs", "periodes_comptables",
                    "facture_reglements", "facture_lignes", "factures", "facture_sequence")) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
            jdbc.update("DELETE FROM bons_sortie_lignes WHERE bon_sortie_id IN (SELECT id FROM bons_sortie WHERE center_id = ?)",
                    centre);
            for (String table : List.of("bons_sortie", "stock_movements", "volet_paramedical", "volet_medical", "seance_suppression",
                    "seances", "lots", "articles", "absence_patient", "attestation_droit", "prise_en_charge", "assure_patient",
                    "patients", "forfait", "centre_payeur", "agence", "caisse_assurance", "migration_entity_run",
                    "migration_id_map", "migration_value_map", "migration_batch")) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
    }

    // ------------------------------------------------------------------ données

    protected void article() {
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, "
                        + "gere_par_lot, active, created_at) VALUES (?, ?, 'ZT-F6', 'Dialyseur', 'u', 10, 1, 10, TRUE, TRUE, CURRENT_TIMESTAMP)",
                ARTICLE_1, CENTRE_A);
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, quantite_initiale, quantite_restante, pmp, "
                        + "created_at) VALUES (?, ?, ?, 'ZT-F6-L1', 10, 10, 10, CURRENT_TIMESTAMP)",
                LOT_1, CENTRE_A, ARTICLE_1);
    }

    protected void forfait() {
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, 'F-HD', 'Forfait HD', 3500.00)",
                FORFAIT_1, CENTRE_A);
    }

    protected void patient(UUID id, UUID centre, String nom) {
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, created_at) "
                        + "VALUES (?, ?, ?, ?, 'Test', 'F', ?, ?, 'NON_VACANCIER', 'PERMANENT', CURRENT_TIMESTAMP)",
                id, centre, "P-" + id.toString().substring(31), nom, date("2025-01-10"), "ASS-" + id.toString().substring(31));
    }

    /**
     * Facture posée en SQL (identifiant fixe nommé), période et date de facturation fixes.
     */
    protected void facture(UUID id, String nomAlias, UUID centre, UUID patient, String numero, String debut, String fin,
                           String dateFacturation, String ttc) {
        nommer(id, nomAlias);
        BigDecimal total = new BigDecimal(ttc);
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, patient_code, patient_full_name, "
                        + "numero_immatriculation_snapshot, centre_payeur_id_snapshot, agence_id_snapshot, period_start, "
                        + "period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc, created_at) "
                        + "VALUES (?, ?, ?, ?, 'PAT', ?, 'ASS-SNAP', NULL, NULL, ?, ?, ?, 0.00, ?, 0.00, ?, CURRENT_TIMESTAMP)",
                id, centre, patient, numero, "Snapshot " + numero, date(debut), date(fin), date(dateFacturation), total, total);
    }

    protected void seance(UUID id, String nomAlias, UUID centre, UUID patient, String statut, String jour, UUID factureId) {
        nommer(id, nomAlias);
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, facture_id, created_at, hors_planning) "
                + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)", id, patient, centre, date(jour), statut, factureId);
    }

    protected int compter(String table, UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id = ?", Integer.class, centre);
    }

    // ------------------------------------------------------------------ requêtes

    protected Resp appeler(MockHttpServletRequestBuilder requete, RequestPostProcessor qui) throws Exception {
        return reponse(mvc.perform(requete.with(qui)).andReturn().getResponse());
    }

    protected Resp appeler(MockHttpServletRequestBuilder requete, RequestPostProcessor qui, String corps) throws Exception {
        return reponse(mvc.perform(requete.with(qui).contentType(MediaType.APPLICATION_JSON).content(corps)).andReturn()
                .getResponse());
    }

    protected Resp executer(org.springframework.test.web.servlet.RequestBuilder requete) throws Exception {
        return reponse(mvc.perform(requete).andReturn().getResponse());
    }

    private static Resp reponse(MockHttpServletResponse r) throws Exception {
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(StandardCharsets.UTF_8));
    }

    protected JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    // ------------------------------------------------------------------ golden

    protected void golden(String name, Resp r) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, new Scrubber().render(r));
    }

    protected void goldenNode(String name, JsonNode node) {
        Scrubber s = new Scrubber();
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(s.scrub(node)));
    }

    /**
     * Lignes d'une requête SQL (colonnes nommées) sous forme de JSON ; l'ordre est celui de la requête (donner un ORDER BY).
     */
    protected ArrayNode lignes(String sql, Object... args) {
        ArrayNode a = mapper.createArrayNode();
        for (Map<String, Object> row : jdbc.queryForList(sql, args)) {
            ObjectNode o = mapper.createObjectNode();
            row.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                Object v = e.getValue();
                if (v == null) o.putNull(e.getKey().toLowerCase());
                else if (v instanceof BigDecimal b) o.put(e.getKey().toLowerCase(), b.toPlainString());
                else o.put(e.getKey().toLowerCase(), String.valueOf(v));
            });
            a.add(o);
        }
        return a;
    }

    protected record Resp(int status, String contentType, String raw) {
    }

    /**
     * Normalise : identifiants connus -> alias, autres UUID -> {@code <UUID_GENERE_n>}, horodatages -> {@code <HORODATAGE>},
     * jour d'exécution -> {@code <AUJOURD_HUI>}, codes aléatoires de règlement -> {@code <CODE_REGLEMENT>}, clés triées.
     */
    protected final class Scrubber {
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private int inconnus = 0;

        Scrubber() {
            alias.forEach((id, nom) -> aliases.put(id.toString(), nom));
        }

        String scrubString(String raw) {
            String s = HORODATAGE_RE.matcher(raw).replaceAll("<HORODATAGE>");
            s = CODE_REGLEMENT_RE.matcher(s).replaceAll("<CODE_REGLEMENT>");
            s = CODE_PATIENT_RE.matcher(s).replaceAll("<CODE_PATIENT>");
            Matcher m = UUID_RE.matcher(s);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String a = aliases.computeIfAbsent(m.group(), k -> "<UUID_GENERE_" + (++inconnus) + ">");
                m.appendReplacement(sb, Matcher.quoteReplacement(a));
            }
            m.appendTail(sb);
            String resultat = sb.toString();
            for (Map.Entry<String, String> d : datesNommees.entrySet()) resultat = resultat.replace(d.getKey(), d.getValue());
            return resultat.replace(LocalDate.now().toString(), "<AUJOURD_HUI>");
        }

        JsonNode scrub(JsonNode n) {
            if (n.isString()) return mapper.getNodeFactory().stringNode(scrubString(n.asString()));
            if (n.isObject()) {
                ObjectNode o = mapper.createObjectNode();
                List<Map.Entry<String, JsonNode>> entrees = new ArrayList<>();
                n.properties().forEach(entrees::add);
                entrees.sort(Map.Entry.comparingByKey(Comparator.naturalOrder()));
                for (Map.Entry<String, JsonNode> e : entrees) o.set(e.getKey(), scrub(e.getValue()));
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
