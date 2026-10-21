package com.hemodialyse.backend.characterization.facturation.reglements;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
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
 * Harnais du filet de caractérisation de la slice facturation-f4-reglements : routes {@code /api/v1/reglements}
 * observées en HTTP (MockMvc, Spring complet, sécurité réelle + RoleScopeFilter, H2), état SQL ({@code facture_reglements}),
 * événement {@code FactureReglementRecordedEvent} réellement publié et écritures de règlement produites par la
 * Comptabilité (son écouteur de production). Aucun mock du code caractérisé.
 * <p>
 * Non-déterminisme maîtrisé sans seam : les factures sont posées en SQL avec des dates fixes (2026), la date d'un
 * règlement sans date est le jour d'exécution (remplacé par {@code <AUJOURD_HUI>}), les codes {@code REG-XXXXXXXX} et
 * identifiants de règlement sont aléatoires (remplacés par {@code <CODE_REGLEMENT>} / {@code <UUID_GENERE_n>}) ; les
 * règlements dont l'ordre importe sont posés en SQL avec identifiants, codes et horodatages fixes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class ReglementsCharacterizationSupport {

    protected static final String BASE = "/api/v1/reglements";
    protected static final UUID CENTRE_A = UUID.fromString("99997000-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99997000-0000-0000-0000-00000000000b");
    protected static final UUID CAISSE_1 = UUID.fromString("99997000-0000-0000-0000-000000000c01");
    protected static final UUID CAISSE_2 = UUID.fromString("99997000-0000-0000-0000-000000000c02");
    protected static final UUID CAISSE_B = UUID.fromString("99997000-0000-0000-0000-000000000cb1");
    protected static final UUID AGENCE_1 = UUID.fromString("99997000-0000-0000-0000-000000000a01");
    protected static final UUID AGENCE_2 = UUID.fromString("99997000-0000-0000-0000-000000000a02");
    protected static final UUID AGENCE_B = UUID.fromString("99997000-0000-0000-0000-000000000ab1");
    protected static final UUID PAYEUR_1 = UUID.fromString("99997000-0000-0000-0000-0000000000e1");
    protected static final UUID PAYEUR_2 = UUID.fromString("99997000-0000-0000-0000-0000000000e2");
    protected static final UUID PAYEUR_B = UUID.fromString("99997000-0000-0000-0000-0000000000eb");
    protected static final UUID P_AMRANI = UUID.fromString("99997000-0000-0000-0000-00000000a001");
    protected static final UUID P_BELKACEM = UUID.fromString("99997000-0000-0000-0000-00000000a002");
    protected static final UUID P_CHIKHI = UUID.fromString("99997000-0000-0000-0000-00000000a003");
    protected static final UUID P_CENTRE_B = UUID.fromString("99997000-0000-0000-0000-00000000b001");
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "facturation", "reglements");
    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HORODATAGE_RE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}(:?\\d{2})?|Z)?");
    private static final Pattern CODE_RE = Pattern.compile("REG-[0-9A-F]{8}\\b");
    protected final ObjectMapper mapper = JsonMapper.builder().build();
    private final Map<UUID, String> alias = new LinkedHashMap<>();
    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected com.hemodialyse.backend.infrastructure.security.RoleScopeFilter roleScopeFilter;
    @Autowired
    protected ReglementsEnregistresObserves evenements;
    protected MockMvc mvc;
    private int compteurFactures = 0;
    private int compteurPaiements = 0;

    protected static RequestPostProcessor as(UUID centre, String... roles) {
        return user(UserPrincipal.create(UUID.randomUUID().toString(), centre.toString(), "u", "", List.of(roles), true));
    }

    protected static RequestPostProcessor adminA() {
        return as(CENTRE_A, "ADMIN");
    }

    protected static RequestPostProcessor secretaireA() {
        return as(CENTRE_A, "SECRETAIRE");
    }

    protected static String json(String corps) {
        return corps.replace('\'', '"');
    }

    /**
     * Corps d'enregistrement d'un règlement.
     */
    protected static String corps(UUID centre, String montant, String date, String userId) {
        StringBuilder sb = new StringBuilder("{'centerId':'" + centre + "','montant':" + montant);
        if (date != null) sb.append(",'dateReglement':'").append(date).append("'");
        if (userId != null) sb.append(",'userId':'").append(userId).append("'");
        return json(sb.append("}").toString());
    }

    private static Date date(String jour) {
        return jour == null ? null : Date.valueOf(LocalDate.parse(jour));
    }

    private static Resp reponse(MockHttpServletResponse r) throws Exception {
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(StandardCharsets.UTF_8), r.getContentAsByteArray(),
                r.getHeader("Content-Disposition"));
    }

    protected abstract String theme();

    // ------------------------------------------------------------------ données

    @BeforeEach
    void setUpReglements() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        cleanupReglements();
        evenements.oublier();
        alias.clear();
        compteurFactures = 0;
        compteurPaiements = 0;
        nommer(CENTRE_A, "<CENTRE_A>");
        nommer(CENTRE_B, "<CENTRE_B>");
        nommer(CAISSE_1, "<CAISSE_1>");
        nommer(CAISSE_2, "<CAISSE_2>");
        nommer(CAISSE_B, "<CAISSE_B>");
        nommer(AGENCE_1, "<AGENCE_1>");
        nommer(AGENCE_2, "<AGENCE_2>");
        nommer(AGENCE_B, "<AGENCE_B>");
        nommer(PAYEUR_1, "<PAYEUR_1>");
        nommer(PAYEUR_2, "<PAYEUR_2>");
        nommer(PAYEUR_B, "<PAYEUR_B>");
        nommer(P_AMRANI, "<P_AMRANI>");
        nommer(P_BELKACEM, "<P_BELKACEM>");
        nommer(P_CHIKHI, "<P_CHIKHI>");
        nommer(P_CENTRE_B, "<P_CENTRE_B>");
        referentiel();
    }

    @AfterEach
    void cleanupReglements() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM lignes_ecriture WHERE ecriture_id IN "
                    + "(SELECT id FROM ecritures_comptables WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM ecritures_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM comptes_payeurs WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM periodes_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM facture_reglements WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM facture_lignes WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM factures WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM centre_payeur WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM agence WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM caisse_assurance WHERE center_id = ?", centre);
        }
    }

    protected void nommer(UUID id, String nom) {
        alias.put(id, nom);
    }

    /**
     * Deux caisses, agences et centres payeurs au centre A (Amrani -> payeur 1, Belkacem -> payeur 2, Chikhi sans
     * payeur), un jeu au centre B.
     */
    private void referentiel() {
        caisse(CAISSE_1, CENTRE_A, "CNAS", "CNAS Centre 1");
        caisse(CAISSE_2, CENTRE_A, "CASNOS", "CASNOS Centre 1");
        caisse(CAISSE_B, CENTRE_B, "CNAS-B", "CNAS du centre B");
        agence(AGENCE_1, CENTRE_A, CAISSE_1, "AG-01", "Agence Alger");
        agence(AGENCE_2, CENTRE_A, CAISSE_2, "AG-02", "Agence Oran");
        agence(AGENCE_B, CENTRE_B, CAISSE_B, "AG-B", "Agence du centre B");
        payeur(PAYEUR_1, CENTRE_A, AGENCE_1, "CP-01", "Centre payeur Alger");
        payeur(PAYEUR_2, CENTRE_A, AGENCE_2, "CP-02", "Centre payeur Oran");
        payeur(PAYEUR_B, CENTRE_B, AGENCE_B, "CP-B", "Centre payeur B");
        patient(P_AMRANI, CENTRE_A, "REG-001", "Amrani", "Lina", PAYEUR_1);
        patient(P_BELKACEM, CENTRE_A, "REG-002", "Belkacem", "Omar", PAYEUR_2);
        patient(P_CHIKHI, CENTRE_A, "REG-003", "Chikhi", "Nadia", null);
        patient(P_CENTRE_B, CENTRE_B, "REG-B01", "Zerrouki", "Rania", PAYEUR_B);
    }

    private void caisse(UUID id, UUID centre, String code, String nom) {
        jdbc.update("INSERT INTO caisse_assurance (id, center_id, code, nom, type_caisse) VALUES (?, ?, ?, ?, 'STANDARD')",
                id, centre, code, nom);
    }

    private void agence(UUID id, UUID centre, UUID caisse, String code, String nom) {
        jdbc.update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, ?, ?)", id, centre, caisse, code, nom);
    }

    private void payeur(UUID id, UUID centre, UUID agence, String code, String nom) {
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, ?, ?)", id, centre, agence, code, nom);
    }

    private void patient(UUID id, UUID centre, String code, String nom, String prenom, UUID payeur) {
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, centre_payeur_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'F', ?, ?, 'NON_VACANCIER', 'PERMANENT', ?, ?)",
                id, centre, code, nom, prenom, Date.valueOf(LocalDate.of(2025, 1, 10)), "ASS-" + code, payeur,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Facture émise (fixture SQL) : identifiant dérivé du rang (alias {@code <FACTURE_n>}), instantanés du patient.
     * Payeur et agence en instantané ({@code payeur}, {@code agence}) peuvent être nuls.
     */
    protected UUID facture(UUID centre, UUID patient, String numero, String dateFacturation, String ttc, UUID payeur, UUID agence) {
        compteurFactures++;
        UUID id = UUID.fromString(String.format("99997000-0000-0000-0000-0000000f%04d", compteurFactures));
        nommer(id, "<FACTURE_" + compteurFactures + ">");
        BigDecimal total = new BigDecimal(ttc);
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, patient_code, patient_full_name, "
                        + "numero_immatriculation_snapshot, centre_payeur_id_snapshot, agence_id_snapshot, period_start, "
                        + "period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc, created_at) "
                        + "VALUES (?, ?, ?, ?, 'PAT', ?, 'ASS-SNAP', ?, ?, ?, ?, ?, 0.00, ?, 0.00, ?, CURRENT_TIMESTAMP)",
                id, centre, patient, numero, "Snapshot " + numero, payeur, agence, date("2025-12-01"), date("2025-12-31"),
                date(dateFacturation), total, total);
        return id;
    }

    /**
     * Facture de 1000,00 du patient Amrani (payeur 1, agence 1) émise le 2026-01-31, numéro {@code FAC-0001}.
     */
    protected UUID factureMille() {
        return facture(CENTRE_A, P_AMRANI, "FAC-0001", "2026-01-31", "1000.00", PAYEUR_1, AGENCE_1);
    }

    /**
     * Règlement posé en SQL (identifiant {@code <PAIEMENT_n>} fixe, code et horodatage de création fixes).
     */
    protected UUID paiement(UUID facture, UUID centre, String montant, String jour, String par, String code, String creeLe) {
        compteurPaiements++;
        UUID id = UUID.fromString(String.format("99997000-0000-0000-0000-0000000d%04d", compteurPaiements));
        nommer(id, "<PAIEMENT_" + compteurPaiements + ">");
        jdbc.update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement, saisi_par, "
                        + "code_reglement, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, facture, centre, new BigDecimal(montant), date(jour), par, code, Timestamp.valueOf(creeLe));
        return id;
    }

    // ------------------------------------------------------------------ requêtes

    protected Resp doPost(String path, String corps, RequestPostProcessor who) throws Exception {
        return reponse(mvc.perform(post(java.net.URI.create(BASE + path)).contentType(MediaType.APPLICATION_JSON)
                .content(corps).with(who)).andReturn().getResponse());
    }

    protected Resp doPostAnonyme(String path, String corps) throws Exception {
        return reponse(mvc.perform(post(java.net.URI.create(BASE + path)).contentType(MediaType.APPLICATION_JSON)
                .content(corps)).andReturn().getResponse());
    }

    protected Resp doGet(String path, RequestPostProcessor who) throws Exception {
        return reponse(mvc.perform(get(java.net.URI.create(BASE + path)).with(who)).andReturn().getResponse());
    }

    protected Resp doGetAnonyme(String path) throws Exception {
        return reponse(mvc.perform(get(java.net.URI.create(BASE + path))).andReturn().getResponse());
    }

    protected Resp enregistrer(UUID facture, String corps) throws Exception {
        return doPost("/" + facture + "/paiements", corps, adminA());
    }

    /**
     * Enregistre un règlement du centre A (montant en texte, ex. {@code 250.00}) avec la date donnée.
     */
    protected Resp regler(UUID facture, String montant, String jour) throws Exception {
        return enregistrer(facture, corps(CENTRE_A, montant, jour, "caissier"));
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

    protected void goldenTexte(String name, String statutEtEntetes, String texte) {
        Scrubber s = new Scrubber();
        ObjectNode o = mapper.createObjectNode();
        o.put("entetes", statutEtEntetes);
        o.put("contenu", s.scrubString(texte));
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(o));
    }

    /**
     * Contenu d'un classeur Excel : feuilles, lignes, cellules (type + valeur).
     */
    protected JsonNode contenuExcel(byte[] octets) throws Exception {
        ObjectNode racine = mapper.createObjectNode();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(octets))) {
            ArrayNode feuilles = racine.putArray("feuilles");
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                ObjectNode f = feuilles.addObject();
                f.put("nom", sheet.getSheetName());
                ArrayNode lignes = f.putArray("lignes");
                for (Row row : sheet) {
                    ArrayNode cellules = lignes.addArray();
                    for (Cell c : row) {
                        cellules.add(c.getCellType() == CellType.NUMERIC ? "N:" + c.getNumericCellValue() : "S:" + c.getStringCellValue());
                    }
                }
            }
        }
        return racine;
    }

    /**
     * Événements {@code FactureReglementRecordedEvent} publiés depuis le début du test.
     */
    protected void goldenEvenements(String name) {
        ArrayNode arr = mapper.createArrayNode();
        for (var e : evenements.recus()) {
            ObjectNode o = mapper.createObjectNode();
            o.put("factureId", e.factureId().toString());
            o.put("centerId", e.centerId().toString());
            o.put("montant", String.valueOf(e.montant()));
            o.put("dateReglement", String.valueOf(e.dateReglement()));
            o.put("userId", e.userId());
            if (e.mode() != null) {
                // F4 : le mode n'existait pas ; omis quand il est absent pour que les goldens d'avant F4 restent valables
                o.put("mode", e.mode());
            }
            arr.add(o);
        }
        goldenNode(name, arr);
    }

    /**
     * Règlements en base pour le centre (tous les champs), triés par date, montant, code.
     */
    protected void goldenReglements(String name, UUID centre) {
        Scrubber s = new Scrubber();
        List<ObjectNode> lignes = new ArrayList<>();
        for (Map<String, Object> l : jdbc.queryForList("SELECT facture_id, montant, date_reglement, saisi_par, code_reglement, "
                + "(created_at IS NULL) AS created_at_nul FROM facture_reglements WHERE center_id = ?", centre)) {
            lignes.add(ligne(l, s));
        }
        lignes.sort(Comparator.comparing((ObjectNode o) -> o.path("facture_id").asString())
                .thenComparing(o -> o.path("date_reglement").asString())
                .thenComparing(o -> o.path("montant").asString()));
        ArrayNode arr = mapper.createArrayNode();
        lignes.forEach(arr::add);
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
    }

    /**
     * Écritures de règlement (et de ventes) que la Comptabilité a produites pour le centre, avec leurs lignes.
     */
    protected void goldenEcritures(String name, UUID centre) {
        Scrubber s = new Scrubber();
        List<ObjectNode> ecritures = new ArrayList<>();
        // tri avant normalisation : les identifiants générés reçoivent leur alias dans l'ordre des pièces
        for (Map<String, Object> e : jdbc.queryForList("SELECT e.id, e.source_id, e.journal_code, e.numero_piece, "
                + "e.date_ecriture, e.date_piece, e.statut, e.libelle FROM ecritures_comptables e WHERE e.center_id = ? "
                + "ORDER BY e.numero_piece, e.libelle", centre)) {
            UUID id = (UUID) e.remove("id");
            ObjectNode o = ligne(e, s);
            ArrayNode lignes = o.putArray("lignes");
            for (Map<String, Object> l : jdbc.queryForList("SELECT compte_scf, libelle_ligne, montant_debit, montant_credit, "
                    + "tiers_id FROM lignes_ecriture WHERE ecriture_id = ? ORDER BY compte_scf, montant_debit, montant_credit", id)) {
                lignes.add(ligne(l, s));
            }
            ecritures.add(o);
        }
        ecritures.sort(Comparator.comparing((ObjectNode o) -> o.path("numero_piece").asString())
                .thenComparing(o -> o.path("libelle").asString()));
        ArrayNode arr = mapper.createArrayNode();
        ecritures.forEach(arr::add);
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
    }

    private ObjectNode ligne(Map<String, Object> row, Scrubber s) {
        ObjectNode o = mapper.createObjectNode();
        List<Map.Entry<String, Object>> entrees = new ArrayList<>(row.entrySet());
        entrees.sort(Map.Entry.comparingByKey(Comparator.comparing(String::toLowerCase)));
        for (Map.Entry<String, Object> e : entrees) {
            o.put(e.getKey().toLowerCase(), e.getValue() == null ? null : s.scrubString(String.valueOf(e.getValue())));
        }
        return o;
    }

    protected record Resp(int status, String contentType, String raw, byte[] octets, String disposition) {
    }

    /**
     * Normalise : identifiants connus -> alias, autres UUID -> {@code <UUID_GENERE_n>}, horodatages -> {@code <HORODATAGE>},
     * jour d'exécution -> {@code <AUJOURD_HUI>}, codes aléatoires de règlement -> {@code <CODE_REGLEMENT>}, clés triées,
     * messages techniques non verrouillés.
     */
    private final class Scrubber {
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private int inconnus = 0;

        Scrubber() {
            alias.forEach((id, nom) -> aliases.put(id.toString(), nom));
        }

        String scrubString(String raw) {
            String sansHeure = HORODATAGE_RE.matcher(raw).replaceAll("<HORODATAGE>");
            sansHeure = CODE_RE.matcher(sansHeure).replaceAll("<CODE_REGLEMENT>");
            Matcher m = UUID_RE.matcher(sansHeure);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String a = aliases.computeIfAbsent(m.group(), k -> "<UUID_GENERE_" + (++inconnus) + ">");
                m.appendReplacement(sb, Matcher.quoteReplacement(a));
            }
            m.appendTail(sb);
            return sb.toString().replace(LocalDate.now().toString(), "<AUJOURD_HUI>");
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
