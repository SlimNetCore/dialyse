package com.hemodialyse.backend.characterization.facturation.cloture;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.PeriodeDeFacturationCloturee;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.PeriodeDeFacturationRouverte;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Harnais des scénarios de la slice facturation-f7-cloture-mensuelle (nouvelle fonctionnalité : aucun legacy, donc des goldens
 * {@code *_nouveau_code}) : routes {@code /api/v1/facturation} observées en HTTP (MockMvc, Spring complet, sécurité réelle +
 * RoleScopeFilter, H2), état SQL ({@code periodes_facturation} et son journal, séances, factures) et événements publiés.
 * <p>
 * Déterminisme : toutes les dates de séances et de mois sont fixes (2026) ; les instants de clôture et de réouverture sont ceux de
 * l'exécution (remplacés par {@code <HORODATAGE>}) ; les identifiants sont nommés ({@code <ADMIN_A>}, {@code <CENTRE_A>}, ...) ;
 * le jour d'exécution et l'année du numéro de facture sont remplacés par {@code <AUJOURD_HUI>} et {@code FAC-<ANNEE>-}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class ClotureCharacterizationSupport {

    protected static final String BASE = "/api/v1/facturation";
    protected static final UUID CENTRE_A = UUID.fromString("99997100-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99997100-0000-0000-0000-00000000000b");
    protected static final UUID ADMIN_A = UUID.fromString("99997100-0000-0000-0000-00000000ad01");
    protected static final UUID AUTRE_ADMIN_A = UUID.fromString("99997100-0000-0000-0000-00000000ad02");
    protected static final UUID ADMIN_B = UUID.fromString("99997100-0000-0000-0000-00000000ad0b");
    protected static final UUID FORFAIT_HD = UUID.fromString("99997100-0000-0000-0000-0000000f0001");
    protected static final UUID FORFAIT_PREMIUM = UUID.fromString("99997100-0000-0000-0000-0000000f0002");
    protected static final UUID FORFAIT_B = UUID.fromString("99997100-0000-0000-0000-0000000f00b1");
    protected static final UUID P_AMRANI = UUID.fromString("99997100-0000-0000-0000-00000000a001");
    protected static final UUID P_BELKACEM = UUID.fromString("99997100-0000-0000-0000-00000000a002");
    protected static final UUID P_CENTRE_B = UUID.fromString("99997100-0000-0000-0000-00000000b001");
    protected static final String AOUT = "2026-08";
    protected static final String SEPTEMBRE = "2026-09";
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "facturation", "cloture");
    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HORODATAGE_RE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}(:?\\d{2})?|Z)?");
    protected final ObjectMapper mapper = JsonMapper.builder().build();
    private final Map<UUID, String> alias = new LinkedHashMap<>();
    private final Map<String, String> textes = new LinkedHashMap<>();
    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected com.hemodialyse.backend.infrastructure.security.RoleScopeFilter roleScopeFilter;
    @Autowired
    protected EvenementsDeClotureEnregistres evenements;
    protected MockMvc mvc;

    protected static RequestPostProcessor as(UUID utilisateur, UUID centre, String... roles) {
        return user(UserPrincipal.create(utilisateur.toString(), centre.toString(), "u", "", List.of(roles), true));
    }

    protected static RequestPostProcessor adminA() {
        return as(ADMIN_A, CENTRE_A, "ADMIN");
    }

    protected static RequestPostProcessor autreAdminA() {
        return as(AUTRE_ADMIN_A, CENTRE_A, "ADMIN");
    }

    protected static RequestPostProcessor adminB() {
        return as(ADMIN_B, CENTRE_B, "ADMIN");
    }

    protected static String json(String corps) {
        return corps.replace('\'', '"');
    }

    protected static String validation(UUID centre, String mois) {
        return json("{'centerId':'" + centre + "','userId':'u1','month':'" + mois + "','regroupementMultiForfait':true}");
    }

    protected static String validationParDates(UUID centre, String debut, String fin) {
        return json("{'centerId':'" + centre + "','userId':'u1','periodStart':'" + debut + "','periodEnd':'" + fin
                + "','regroupementMultiForfait':true}");
    }

    protected static String reouverture(String motif) {
        return motif == null ? json("{}") : json("{'motif':'" + motif + "'}");
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
    void setUpCloture() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        cleanupCloture();
        evenements.oublier();
        alias.clear();
        textes.clear();
        nommer(CENTRE_A, "<CENTRE_A>");
        nommer(CENTRE_B, "<CENTRE_B>");
        nommer(ADMIN_A, "<ADMIN_A>");
        nommer(AUTRE_ADMIN_A, "<AUTRE_ADMIN_A>");
        nommer(ADMIN_B, "<ADMIN_B>");
        nommer(FORFAIT_HD, "<FORFAIT_HD>");
        nommer(FORFAIT_PREMIUM, "<FORFAIT_PREMIUM>");
        nommer(FORFAIT_B, "<FORFAIT_B>");
        nommer(P_AMRANI, "<P_AMRANI>");
        nommer(P_BELKACEM, "<P_BELKACEM>");
        nommer(P_CENTRE_B, "<P_CENTRE_B>");
        // jour d'exécution (date de facturation) et année du numéro de facture : remplacés pour rester indépendants du jour
        nommerTexte(LocalDate.now().toString(), "<AUJOURD_HUI>");
        nommerTexte("FAC-" + LocalDate.now().getYear() + "-", "FAC-<ANNEE>-");
        referentiel();
    }

    @AfterEach
    void cleanupCloture() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM periodes_facturation_journal WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM periodes_facturation WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM lignes_ecriture WHERE ecriture_id IN "
                    + "(SELECT id FROM ecritures_comptables WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM ecritures_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM comptes_payeurs WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM periodes_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM facture_reglements WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM facture_lignes WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM factures WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM absence_patient WHERE center_id = ?", centre);
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

    protected void nommerTexte(String texte, String nom) {
        textes.put(texte, nom);
    }

    /**
     * Forfaits, patients et prises en charge des deux centres, paramètres du centre A (modèle {@code FAC-{YYYY}-{SEQ}}), TVA 9 %,
     * séquence courante 41 pour l'année en cours. Aucun mois n'est clos.
     */
    private void referentiel() {
        forfait(FORFAIT_HD, CENTRE_A, "F-HD", "Forfait HD", "3500.00");
        forfait(FORFAIT_PREMIUM, CENTRE_A, "F-HDP", "Forfait HD Premium", "5200.00");
        forfait(FORFAIT_B, CENTRE_B, "F-B", "Forfait du centre B", "1000.00");
        patient(P_AMRANI, CENTRE_A, "CLO-001", "Amrani", "Lina");
        patient(P_BELKACEM, CENTRE_A, "CLO-002", "Belkacem", "Omar");
        patient(P_CENTRE_B, CENTRE_B, "CLO-B01", "Zerrouki", "Rania");
        pec(P_AMRANI, CENTRE_A, FORFAIT_HD);
        pec(P_BELKACEM, CENTRE_A, FORFAIT_HD);
        pec(P_CENTRE_B, CENTRE_B, FORFAIT_B);
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("INSERT INTO facturation_settings (center_id, code_format, regroupement_multi_forfait, updated_at, updated_by) "
                    + "VALUES (?, 'FAC-{YYYY}-{SEQ}', TRUE, CURRENT_TIMESTAMP, 'fixture')", centre);
            jdbc.update("INSERT INTO facture_sequence (center_id, seq_year, seq_value) VALUES (?, ?, 41)", centre,
                    LocalDate.now().getYear());
            jdbc.update("INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere, date_debut_validite, actif, "
                    + "created_by) VALUES (?, ?, 'TVA', 9.00, 'HEMODIALYSE', FALSE, DATE '2000-01-01', TRUE, 'fixture')",
                    UUID.randomUUID(), centre);
        }
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

    protected void pec(UUID patient, UUID centre, UUID forfait) {
        jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, date_debut_demande, date_fin_demande, "
                        + "date_debut_effectif, date_fin_effectif, forfait_demande_id, forfait_effectif_id, statut, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'VALIDEE', ?)",
                UUID.randomUUID(), centre, patient, date("2025-01-01"), date("2027-12-31"), date("2025-01-01"),
                date("2027-12-31"), forfait, forfait, OffsetDateTime.of(2025, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC));
    }

    protected UUID seance(String nom, UUID patient, UUID centre, String jour, String statut) {
        UUID id = UUID.randomUUID();
        nommer(id, "<S_" + nom + ">");
        OffsetDateTime maintenant = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, validated_at, "
                        + "signed_infirmier_at, signed_medecin_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, patient, centre, Date.valueOf(LocalDate.parse(jour)), statut, maintenant, maintenant, maintenant, maintenant);
        return id;
    }

    /** Facture déjà émise (décor), nommée dans les goldens. */
    protected UUID factureExistante(String nom, UUID centre, UUID patient, String numero, String debut, String fin) {
        UUID id = UUID.randomUUID();
        nommer(id, "<F_" + nom + ">");
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, patient_full_name, period_start, "
                        + "period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc) "
                        + "VALUES (?, ?, ?, ?, 'Existante', ?, ?, ?, 9.00, 100.00, 9.00, 109.00)",
                id, centre, patient, numero, date(debut), date(fin), date(fin));
        return id;
    }

    /** La période comptable du mois est clôturée (décor de la Comptabilité). */
    protected void periodeComptableCloturee(UUID centre, int annee, int mois) {
        jdbc.update("INSERT INTO periodes_comptables (id, annee, center_id, cloturee, cloturee_at, cloturee_by, mois) "
                        + "VALUES (?, ?, ?, TRUE, ?, 'comptable', ?)", UUID.randomUUID(), annee, centre,
                OffsetDateTime.now(ZoneOffset.UTC), mois);
    }

    /** Un mois clos directement en SQL (décor), sans passer par l'API. */
    protected void moisClosParDecor(UUID centre, int annee, int mois, UUID par) {
        jdbc.update("INSERT INTO periodes_facturation (center_id, annee, mois, statut, cloturee_par, cloturee_le) "
                + "VALUES (?, ?, ?, 'CLOTURE', ?, ?)", centre, annee, mois, par, OffsetDateTime.now(ZoneOffset.UTC));
    }

    // ------------------------------------------------------------------ requêtes

    protected Resp doPost(String path, String corps, RequestPostProcessor who) throws Exception {
        MockHttpServletResponse r = mvc.perform(post(java.net.URI.create(BASE + path)).contentType(MediaType.APPLICATION_JSON)
                .content(corps).with(who)).andReturn().getResponse();
        return reponse(r);
    }

    protected Resp doPostSansCorps(String path, RequestPostProcessor who) throws Exception {
        MockHttpServletResponse r = mvc.perform(post(java.net.URI.create(BASE + path)).with(who)).andReturn().getResponse();
        return reponse(r);
    }

    protected Resp doPostAnonyme(String path, String corps) throws Exception {
        MockHttpServletResponse r = mvc.perform(post(java.net.URI.create(BASE + path)).contentType(MediaType.APPLICATION_JSON)
                .content(corps)).andReturn().getResponse();
        return reponse(r);
    }

    protected Resp doGet(String path, RequestPostProcessor who) throws Exception {
        MockHttpServletResponse r = mvc.perform(get(java.net.URI.create(BASE + path)).with(who)).andReturn().getResponse();
        return reponse(r);
    }

    protected Resp doPostBrut(String url, String corps, RequestPostProcessor who) throws Exception {
        MockHttpServletResponse r = mvc.perform(post(java.net.URI.create(url)).contentType(MediaType.APPLICATION_JSON)
                .content(corps).with(who)).andReturn().getResponse();
        return reponse(r);
    }

    protected Resp cloturer(String mois, RequestPostProcessor who) throws Exception {
        return doPostSansCorps("/periodes/" + mois + "/cloturer", who);
    }

    protected Resp rouvrir(String mois, String motif, RequestPostProcessor who) throws Exception {
        return doPost("/periodes/" + mois + "/rouvrir", reouverture(motif), who);
    }

    protected Resp valider(UUID centre, String mois, RequestPostProcessor who) throws Exception {
        return doPost("/validate", validation(centre, mois), who);
    }

    protected JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    protected int compter(String table, UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id = ?", Integer.class, centre);
    }

    // ------------------------------------------------------------------ golden

    protected void golden(String name, Resp r) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, new Scrubber().render(r));
    }

    /** Le journal append-only du centre, dans l'ordre des actes. */
    protected void goldenJournal(String name, UUID centre) {
        Scrubber s = new Scrubber();
        ArrayNode arr = mapper.createArrayNode();
        for (Map<String, Object> row : jdbc.queryForList("SELECT annee, mois, type_acte, effectue_par, effectue_le, motif, "
                + "seances_eligibles_restantes FROM periodes_facturation_journal WHERE center_id = ? ORDER BY id", centre)) {
            arr.add(ligne(row, s));
        }
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
    }

    /** Les lignes d'état des mois du centre (statut, auteur, motif). */
    protected void goldenEtatDesMois(String name, UUID centre) {
        Scrubber s = new Scrubber();
        ArrayNode arr = mapper.createArrayNode();
        for (Map<String, Object> row : jdbc.queryForList("SELECT annee, mois, statut, cloturee_par, (cloturee_le IS NULL) AS cloturee_le_nulle, "
                + "rouverte_par, (rouverte_le IS NULL) AS rouverte_le_nulle, motif_reouverture FROM periodes_facturation "
                + "WHERE center_id = ? ORDER BY annee, mois", centre)) {
            arr.add(ligne(row, s));
        }
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
    }

    /** Ce que les gardes ont laissé : séances (statut, facture), factures et séquence du centre. */
    protected void goldenFacturation(String name, UUID centre) {
        Scrubber s = new Scrubber();
        ObjectNode racine = mapper.createObjectNode();
        List<ObjectNode> seances = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT id, date_seance, statut, facture_id, statut_avant_exclusion, "
                + "exclusion_absence_id, forfait_override_id FROM seances WHERE center_id = ?", centre)) {
            seances.add(ligne(row, s));
        }
        seances.sort(Comparator.comparing((ObjectNode o) -> o.path("date_seance").asString())
                .thenComparing(o -> o.path("id").asString()));
        ArrayNode a = racine.putArray("seances");
        seances.forEach(a::add);
        List<ObjectNode> factures = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT numero_facture, patient_id, period_start, period_end, total_ttc "
                + "FROM factures WHERE center_id = ?", centre)) {
            factures.add(ligne(row, s));
        }
        factures.sort(Comparator.comparing((ObjectNode o) -> o.path("numero_facture").asString()));
        ArrayNode b = racine.putArray("factures");
        factures.forEach(b::add);
        ArrayNode c = racine.putArray("sequences");
        for (Map<String, Object> q : jdbc.queryForList(
                "SELECT seq_value FROM facture_sequence WHERE center_id = ? ORDER BY seq_year", centre)) {
            c.add(ligne(q, s));
        }
        ArrayNode d = racine.putArray("absences");
        d.add(compter("absence_patient", centre));
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(racine));
    }

    /** Les événements de clôture et de réouverture publiés depuis le début du test. */
    protected void goldenEvenements(String name) {
        Scrubber s = new Scrubber();
        ArrayNode arr = mapper.createArrayNode();
        for (Object e : evenements.recus()) {
            ObjectNode o = mapper.createObjectNode();
            if (e instanceof PeriodeDeFacturationCloturee c) {
                o.put("evenement", "PeriodeDeFacturationCloturee");
                o.put("centre", s.scrubString(c.centre().toString()));
                o.put("mois", c.mois().toString());
                o.put("cloturePar", s.scrubString(c.cloturePar().toString()));
                o.put("cloturee", s.scrubString(c.cloturee().toString()));
                o.put("seancesEligiblesRestantes", c.seancesEligiblesRestantes());
            } else if (e instanceof PeriodeDeFacturationRouverte r) {
                o.put("evenement", "PeriodeDeFacturationRouverte");
                o.put("centre", s.scrubString(r.centre().toString()));
                o.put("mois", r.mois().toString());
                o.put("rouvertPar", s.scrubString(r.rouvertPar().toString()));
                o.put("rouverte", s.scrubString(r.rouverte().toString()));
                o.put("motif", r.motif());
            }
            arr.add(o);
        }
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
    }

    private ObjectNode ligne(Map<String, Object> row, Scrubber s) {
        ObjectNode o = mapper.createObjectNode();
        row.forEach((k, v) -> o.put(k.toLowerCase(), v == null ? null : s.scrubString(String.valueOf(v))));
        return o;
    }

    protected record Resp(int status, String contentType, String raw) {
    }

    /**
     * Normalise : identifiants connus -> alias, autres UUID -> {@code <UUID_GENERE_n>}, horodatages -> {@code <HORODATAGE>},
     * jour et année d'exécution -> alias, clés triées, messages techniques non verrouillés.
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
            String resultat = sb.toString();
            for (Map.Entry<String, String> texte : textes.entrySet()) {
                resultat = resultat.replace(texte.getKey(), texte.getValue());
            }
            return resultat;
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
