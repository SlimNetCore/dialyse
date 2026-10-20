package com.hemodialyse.backend.characterization.facturation.validation;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationValideeEvent;
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
 * Harnais du filet de caractérisation de la slice facturation-f3-validation-emission : {@code POST /api/v1/facturation/validate}
 * observé en HTTP (MockMvc, sécurité réelle + RoleScopeFilter, H2), état SQL (factures, lignes, séquence, séances),
 * événement {@code FacturationValideeEvent} réellement publié et écritures de ventes produites par la Comptabilité (son
 * écouteur de production). Aucun mock du code caractérisé.
 * <p>
 * Non-déterminisme maîtrisé sans seam : la validation date la facture, le taux de TVA et l'année de la séquence du jour
 * d'exécution. Le centre A porte donc un type de TVA ouvert depuis 2000 et une séquence posée pour l'année en cours ;
 * l'année et la date du jour sont remplacées dans les goldens ({@code <ANNEE>}, {@code <AUJOURD_HUI>}) ; les périodes
 * facturées sont fixes (janvier 2026, passé). Identifiants fixes remplacés par des alias, autres UUID et horodatages
 * normalisés, clés triées.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class ValidationCharacterizationSupport {

    protected static final String BASE = "/api/v1/facturation";
    protected static final UUID CENTRE_A = UUID.fromString("99998000-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99998000-0000-0000-0000-00000000000b");
    protected static final UUID FORFAIT_HD = UUID.fromString("99998000-0000-0000-0000-0000000f0001");
    protected static final UUID FORFAIT_PREMIUM = UUID.fromString("99998000-0000-0000-0000-0000000f0002");
    protected static final UUID FORFAIT_B = UUID.fromString("99998000-0000-0000-0000-0000000f00b1");
    protected static final UUID P_AMRANI = UUID.fromString("99998000-0000-0000-0000-00000000a001");
    protected static final UUID P_BELKACEM = UUID.fromString("99998000-0000-0000-0000-00000000a002");
    protected static final UUID P_CHIKHI = UUID.fromString("99998000-0000-0000-0000-00000000a003");
    protected static final UUID P_CENTRE_B = UUID.fromString("99998000-0000-0000-0000-00000000b001");
    protected static final UUID CNAS = UUID.fromString("99998000-0000-0000-0000-000000000c01");
    protected static final UUID AGENCE = UUID.fromString("99998000-0000-0000-0000-000000000a61");
    protected static final String JANVIER = "2026-01";
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "facturation", "validation");
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
    protected EvenementsFacturationValideeEnregistres evenements;
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

    /**
     * Corps de validation par mois.
     */
    protected static String mois(UUID centre, String mois, boolean regroupement) {
        return json("{'centerId':'" + centre + "','userId':'u1','month':'" + mois + "','regroupementMultiForfait':"
                + regroupement + "}");
    }

    protected static String dates(UUID centre, String debut, String fin, boolean regroupement) {
        return json("{'centerId':'" + centre + "','userId':'u1','periodStart':'" + debut + "','periodEnd':'" + fin
                + "','regroupementMultiForfait':" + regroupement + "}");
    }

    protected static int anneeCourante() {
        return LocalDate.now().getYear();
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
    void setUpValidation() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        cleanupValidation();
        evenements.oublier();
        alias.clear();
        textes.clear();
        nommer(CENTRE_A, "<CENTRE_A>");
        nommer(CENTRE_B, "<CENTRE_B>");
        nommer(FORFAIT_HD, "<FORFAIT_HD>");
        nommer(FORFAIT_PREMIUM, "<FORFAIT_PREMIUM>");
        nommer(FORFAIT_B, "<FORFAIT_B>");
        nommer(P_AMRANI, "<P_AMRANI>");
        nommer(P_BELKACEM, "<P_BELKACEM>");
        nommer(P_CHIKHI, "<P_CHIKHI>");
        nommer(P_CENTRE_B, "<P_CENTRE_B>");
        nommer(CNAS, "<CNAS>");
        nommer(AGENCE, "<AGENCE>");
        // l'année seule n'est remplacée que dans un numéro de facture (les périodes fixes de 2026 doivent rester lisibles)
        nommerTexte(LocalDate.now().toString(), "<AUJOURD_HUI>");
        nommerTexte("FAC-" + anneeCourante() + "-", "FAC-<ANNEE>-");
        referentiel();
    }

    @AfterEach
    void cleanupValidation() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
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
            jdbc.update("DELETE FROM centre_payeur WHERE center_id = ?", centre);
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
     * Forfaits, patients et PEC des deux centres, paramètres du centre A (modèle {@code FAC-{YYYY}-{SEQ}}), séquence courante
     * 41 pour l'année en cours, TVA 9 % depuis 2000. Trois patients de A : Amrani (CNAS), Belkacem et Chikhi (sans payeur).
     */
    private void referentiel() {
        forfait(FORFAIT_HD, CENTRE_A, "F-HD", "Forfait HD", "3500.00");
        forfait(FORFAIT_PREMIUM, CENTRE_A, "F-HDP", "Forfait HD Premium", "5200.00");
        forfait(FORFAIT_B, CENTRE_B, "F-B", "Forfait du centre B", "1000.00");
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, 'CNAS-16', 'CNAS Alger')",
                CNAS, CENTRE_A, AGENCE);
        patient(P_AMRANI, CENTRE_A, "SIM-001", "Amrani", "Lina", CNAS);
        patient(P_BELKACEM, CENTRE_A, "SIM-002", "Belkacem", "Omar", null);
        patient(P_CHIKHI, CENTRE_A, "SIM-003", "Chikhi", "Nadia", null);
        patient(P_CENTRE_B, CENTRE_B, "SIM-B01", "Zerrouki", "Rania", null);
        pec(P_AMRANI, CENTRE_A, FORFAIT_HD);
        pec(P_BELKACEM, CENTRE_A, FORFAIT_HD);
        pec(P_CHIKHI, CENTRE_A, FORFAIT_PREMIUM);
        pec(P_CENTRE_B, CENTRE_B, FORFAIT_B);
        parametres(CENTRE_A, "FAC-{YYYY}-{SEQ}");
        sequence(CENTRE_A, anneeCourante(), 41);
        tva(CENTRE_A, "9.00");
    }

    protected void parametres(UUID centre, String modele) {
        jdbc.update("DELETE FROM facturation_settings WHERE center_id = ?", centre);
        jdbc.update("INSERT INTO facturation_settings (center_id, code_format, regroupement_multi_forfait, updated_at, updated_by) "
                + "VALUES (?, ?, TRUE, CURRENT_TIMESTAMP, 'fixture')", centre, modele);
    }

    protected void sequence(UUID centre, int annee, int valeur) {
        jdbc.update("DELETE FROM facture_sequence WHERE center_id = ? AND seq_year = ?", centre, annee);
        jdbc.update("INSERT INTO facture_sequence (center_id, seq_year, seq_value) VALUES (?, ?, ?)", centre, annee, valeur);
    }

    protected void tva(UUID centre, String taux) {
        tva(centre, taux, "2000-01-01", null);
    }

    protected void tva(UUID centre, String taux, String debut, String fin) {
        jdbc.update("INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere, date_debut_validite, "
                        + "date_fin_validite, actif, created_by) VALUES (?, ?, 'TVA', ?, 'HEMODIALYSE', FALSE, ?, ?, TRUE, 'fixture')",
                UUID.randomUUID(), centre, new BigDecimal(taux), date(debut), date(fin));
    }

    protected void forfait(UUID id, UUID centre, String code, String libelle, String prix) {
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, ?, ?, ?)", id, centre, code, libelle,
                new BigDecimal(prix));
    }

    protected void patient(UUID id, UUID centre, String code, String nom, String prenom, UUID payeur) {
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, centre_payeur_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'F', ?, ?, 'NON_VACANCIER', 'PERMANENT', ?, ?)",
                id, centre, code, nom, prenom, Date.valueOf(LocalDate.of(2025, 1, 10)), "ASS-" + code, payeur,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Prise en charge effective couvrant 2025-2027 avec ce forfait.
     */
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

    protected void surcharge(UUID seance, UUID forfait, String libelle, String prix) {
        jdbc.update("UPDATE seances SET forfait_override_id = ?, forfait_override_nom = ?, forfait_override_prix = ? WHERE id = ?",
                forfait, libelle, new BigDecimal(prix), seance);
    }

    /**
     * Facture déjà émise (fixture), pour provoquer un doublon de numéro ou une période déjà facturée.
     */
    protected UUID factureExistante(UUID centre, UUID patient, String numero, String debut, String fin) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, patient_full_name, period_start, "
                        + "period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc) "
                        + "VALUES (?, ?, ?, ?, 'Existante', ?, ?, ?, 9.00, 100.00, 9.00, 109.00)",
                id, centre, patient, numero, date(debut), date(fin), date(fin));
        return id;
    }

    // ------------------------------------------------------------------ requêtes

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

    protected Resp valider(String corps) throws Exception {
        return doPost("/validate", corps, adminA());
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

    protected void goldenNode(String name, JsonNode node) {
        Scrubber s = new Scrubber();
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(s.scrub(node)));
    }

    /**
     * Événements {@code FacturationValideeEvent} publiés depuis le début du test.
     */
    protected void goldenEvenements(String name) {
        ArrayNode arr = mapper.createArrayNode();
        for (FacturationValideeEvent e : evenements.recus()) {
            ObjectNode o = mapper.createObjectNode();
            o.put("centre", e.centre().toString());
            o.put("debut", String.valueOf(e.debut()));
            o.put("fin", String.valueOf(e.fin()));
            o.put("seancesFacturees", e.seancesFacturees());
            o.put("totalTtc", String.valueOf(e.totalTtc()));
            ArrayNode factures = o.putArray("factures");
            for (FacturationValideeEvent.FactureEmise f : e.factures()) {
                ObjectNode facture = mapper.createObjectNode();
                facture.put("id", f.id().toString());
                facture.put("numero", f.numero());
                facture.put("totalHt", String.valueOf(f.totalHt()));
                facture.put("totalTva", String.valueOf(f.totalTva()));
                facture.put("totalTtc", String.valueOf(f.totalTtc()));
                factures.add(facture);
            }
            o.put("survenueLe", String.valueOf(e.survenueLe()));
            arr.add(o);
        }
        goldenNode(name, arr);
    }

    /**
     * Ce que la validation a écrit pour le centre : factures (avec leurs instantanés), lignes, séquence, séances.
     * Les factures sont nommées {@code <FACTURE_n>} par ordre de numéro.
     */
    protected void goldenEtat(String name, UUID centre) {
        List<Map<String, Object>> factures = jdbc.queryForList("SELECT id, patient_id, numero_facture, patient_code, "
                + "patient_full_name, patient_status_snapshot, numero_immatriculation_snapshot, centre_payeur_id_snapshot, "
                + "agence_id_snapshot, period_start, period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc, "
                + "(created_at IS NULL) AS created_at_nul FROM factures WHERE center_id = ? "
                + "ORDER BY numero_facture, patient_full_name", centre);
        nommerLesFactures(centre);
        Scrubber s = new Scrubber();
        ObjectNode racine = mapper.createObjectNode();
        ArrayNode a = racine.putArray("factures");
        factures.forEach(f -> a.add(ligne(f, s)));
        List<ObjectNode> lignes = new ArrayList<>();
        for (Map<String, Object> l : jdbc.queryForList("SELECT facture_id, forfait_id, forfait_label, unit_price_ht, "
                + "seance_count, line_ht FROM facture_lignes WHERE center_id = ?", centre)) {
            lignes.add(ligne(l, s));
        }
        lignes.sort(Comparator.comparing((ObjectNode o) -> o.path("facture_id").asString())
                .thenComparing(o -> o.path("forfait_label").asString()));
        ArrayNode b = racine.putArray("lignes");
        lignes.forEach(b::add);
        ArrayNode c = racine.putArray("sequences");
        for (Map<String, Object> q : jdbc.queryForList(
                "SELECT seq_year, seq_value FROM facture_sequence WHERE center_id = ? ORDER BY seq_year", centre)) {
            c.add(ligne(q, s));
        }
        List<ObjectNode> seances = new ArrayList<>();
        for (Map<String, Object> q : jdbc.queryForList(
                "SELECT id, patient_id, date_seance, statut, facture_id FROM seances WHERE center_id = ?", centre)) {
            seances.add(ligne(q, s));
        }
        seances.sort(Comparator.comparing((ObjectNode o) -> o.path("date_seance").asString())
                .thenComparing(o -> o.path("id").asString()));
        ArrayNode d = racine.putArray("seances");
        seances.forEach(d::add);
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(racine));
    }

    /**
     * Écritures de ventes que la Comptabilité a produites pour le centre (par son écouteur de production).
     */
    protected void goldenEcritures(String name, UUID centre) {
        nommerLesFactures(centre);
        Scrubber s = new Scrubber();
        ArrayNode arr = mapper.createArrayNode();
        List<ObjectNode> ecritures = new ArrayList<>();
        for (Map<String, Object> e : jdbc.queryForList("SELECT e.id, e.source_id, e.journal_code, e.numero_piece, "
                + "e.date_ecriture, e.date_piece, e.statut, e.libelle, "
                + "(SELECT COALESCE(SUM(l.montant_debit), 0) FROM lignes_ecriture l WHERE l.ecriture_id = e.id) AS total_debit, "
                + "(SELECT COALESCE(SUM(l.montant_credit), 0) FROM lignes_ecriture l WHERE l.ecriture_id = e.id) AS total_credit, "
                + "(SELECT COUNT(*) FROM lignes_ecriture l WHERE l.ecriture_id = e.id) AS nb_lignes "
                + "FROM ecritures_comptables e WHERE e.center_id = ?", centre)) {
            e.remove("id");
            ecritures.add(ligne(e, s));
        }
        ecritures.sort(Comparator.comparing((ObjectNode o) -> o.path("numero_piece").asString())
                .thenComparing(o -> o.path("source_id").asString()));
        ecritures.forEach(arr::add);
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
    }

    /**
     * Donne aux factures du centre sans alias connu le nom {@code <FACTURE_n>} (rang par numéro, puis nom du patient).
     */
    private void nommerLesFactures(UUID centre) {
        int n = 0;
        for (Map<String, Object> f : jdbc.queryForList("SELECT id FROM factures WHERE center_id = ? "
                + "ORDER BY numero_facture, patient_full_name", centre)) {
            n++;
            UUID id = (UUID) f.get("id");
            if (!alias.containsKey(id)) {
                nommer(id, "<FACTURE_" + n + ">");
            }
        }
    }

    private ObjectNode ligne(Map<String, Object> row, Scrubber s) {
        ObjectNode o = mapper.createObjectNode();
        List<Map.Entry<String, Object>> entrees = new ArrayList<>(row.entrySet());
        entrees.sort(Map.Entry.comparingByKey(Comparator.comparing(String::toLowerCase)));
        for (Map.Entry<String, Object> e : entrees) {
            String valeur = e.getValue() == null ? null : s.scrubString(String.valueOf(e.getValue()));
            if (e.getKey().equalsIgnoreCase("seq_year") && String.valueOf(anneeCourante()).equals(valeur)) {
                valeur = "<ANNEE>";
            }
            o.put(e.getKey().toLowerCase(), valeur);
        }
        return o;
    }

    protected record Resp(int status, String contentType, String raw) {
    }

    /**
     * Normalise : identifiants connus -> alias, autres UUID -> {@code <UUID_GENERE_n>}, horodatages -> {@code <HORODATAGE>},
     * année et jour d'exécution -> alias, clés triées, messages techniques non verrouillés.
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
            // le jour d'abord (plus long), puis l'année seule
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
