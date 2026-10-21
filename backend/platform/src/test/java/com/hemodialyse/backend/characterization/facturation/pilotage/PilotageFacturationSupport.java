package com.hemodialyse.backend.characterization.facturation.pilotage;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.RoleScopeFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Harnais du filet de caractérisation de la slice facturation-f5-consultation-pilotage : tableau de bord de
 * facturation ({@code GET /api/v1/facturation/dashboard}), synthèse mensuelle imprimée
 * ({@code POST /api/v1/facturation/synthese/print}) et part financière des lectures Direction ({@code /api/v1/direction/*}),
 * observés en HTTP (MockMvc, Spring complet, sécurité réelle + {@code RoleScopeFilter}, H2). Aucun mock du code
 * caractérisé, aucun seam.
 * <p>
 * Non-déterminisme maîtrisé sans seam : toutes les dates des fixtures sont fixes (2026) et indépendantes du jour
 * d'exécution ; les identifiants de fixture sont déterministes et NOMMÉS (alias {@code <FACTURE_n>}, {@code <CENTRE_A>}…) ;
 * le seul alias de date est {@code <AUJOURD_HUI>} (jour d'exécution, utilisé par les défauts de période de la Direction) ;
 * les horodatages (instant de génération) sont remplacés par {@code <HORODATAGE>}. Le PDF de la synthèse ne porte aucun
 * horodatage d'édition : son texte extrait est comparé tel quel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class PilotageFacturationSupport {

    protected static final String FACTURATION = "/api/v1/facturation";
    protected static final String DIRECTION = "/api/v1/direction";
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "facturation", "pilotage");
    private static final Map<UUID, String> NOMS = new ConcurrentHashMap<>();

    protected static final UUID SOC_1 = nom("SOCIETE_1");
    protected static final UUID SOC_2 = nom("SOCIETE_2");
    protected static final UUID CENTRE_A = nom("CENTRE_A");
    protected static final UUID CENTRE_B = nom("CENTRE_B");
    protected static final UUID CENTRE_C = nom("CENTRE_AUTRE_SOCIETE");
    protected static final UUID CENTRE_VIDE = nom("CENTRE_VIDE");
    protected static final UUID DIRECTEUR = nom("DIRECTEUR");
    protected static final UUID DIRECTEUR_2 = nom("DIRECTEUR_2");
    protected static final UUID CAISSE_1 = nom("CAISSE_1");
    protected static final UUID CAISSE_2 = nom("CAISSE_2");
    protected static final UUID CAISSE_B = nom("CAISSE_B");
    protected static final UUID AGENCE_1 = nom("AGENCE_1");
    protected static final UUID AGENCE_2 = nom("AGENCE_2");
    protected static final UUID AGENCE_B = nom("AGENCE_B");
    protected static final UUID P_AMRANI = nom("P_AMRANI");
    protected static final UUID P_BELKACEM = nom("P_BELKACEM");
    protected static final UUID P_CHIKHI = nom("P_CHIKHI");
    protected static final UUID P_CENTRE_B = nom("P_CENTRE_B");

    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HORODATAGE_RE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}(:?\\d{2})?|Z)?");

    protected final ObjectMapper mapper = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private final Map<UUID, String> alias = new LinkedHashMap<>();
    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected RoleScopeFilter roleScopeFilter;
    protected MockMvc mvc;
    private int compteurFactures;
    private int compteurLignes;
    private int compteurSeances;
    private int compteurPaiements;

    protected static UUID nom(String label) {
        UUID u = UUID.nameUUIDFromBytes(("f5:" + label).getBytes(StandardCharsets.UTF_8));
        NOMS.put(u, "<" + label + ">");
        return u;
    }

    // ------------------------------------------------------------------ identités

    protected static RequestPostProcessor as(UUID centre, String... roles) {
        return user(UserPrincipal.create(UUID.randomUUID().toString(), centre.toString(), "u", "", List.of(roles), true));
    }

    protected static RequestPostProcessor adminA() {
        return as(CENTRE_A, "ADMIN");
    }

    protected static RequestPostProcessor adminB() {
        return as(CENTRE_B, "ADMIN");
    }

    /** Compte direction rattaché à la société 1 (le périmètre vient du jeton, revérifié en base). */
    protected static RequestPostProcessor direction() {
        return user(new UserPrincipal(DIRECTEUR.toString(), null, "u-direction", "",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, SOC_1.toString()));
    }

    protected abstract String theme();

    // ------------------------------------------------------------------ cycle

    @BeforeEach
    void setUpPilotage() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        cleanupPilotage();
        alias.clear();
        compteurFactures = 0;
        compteurLignes = 0;
        compteurSeances = 0;
        compteurPaiements = 0;
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC_1, "ZT-F5-S1", "Société Pilotage Un");
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC_2, "ZT-F5-S2", "Société Pilotage Deux");
        centre(CENTRE_A, "ZT-F5-A", "Centre Alpha", SOC_1);
        centre(CENTRE_B, "ZT-F5-B", "Centre Beta", SOC_1);
        centre(CENTRE_VIDE, "ZT-F5-V", "Centre Vide", SOC_1);
        centre(CENTRE_C, "ZT-F5-C", "Centre Autre Société", SOC_2);
        utilisateur(DIRECTEUR, "zt-f5-direction", "Directeur Un");
        jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", DIRECTEUR, SOC_1);
        caisse(CAISSE_1, CENTRE_A, "CNAS", "CNAS Centre 1");
        caisse(CAISSE_2, CENTRE_A, "CASNOS", "CASNOS Centre 1");
        caisse(CAISSE_B, CENTRE_B, "CNAS-B", "CNAS du centre B");
        agence(AGENCE_1, CENTRE_A, CAISSE_1, "AG-01", "Agence Alger");
        agence(AGENCE_2, CENTRE_A, CAISSE_2, "AG-02", "Agence Oran");
        agence(AGENCE_B, CENTRE_B, CAISSE_B, "AG-B", "Agence du centre B");
        patient(P_AMRANI, CENTRE_A, "PIL-001", "Amrani", "Lina");
        patient(P_BELKACEM, CENTRE_A, "PIL-002", "Belkacem", "Omar");
        patient(P_CHIKHI, CENTRE_A, "PIL-003", "Chikhi", "Nadia");
        patient(P_CENTRE_B, CENTRE_B, "PIL-B01", "Zerrouki", "Rania");
    }

    @AfterEach
    void cleanupPilotage() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B, CENTRE_C, CENTRE_VIDE)) {
            jdbc.update("DELETE FROM modele_document WHERE center_id = ?", c);
            jdbc.update("DELETE FROM lignes_ecriture WHERE ecriture_id IN "
                    + "(SELECT id FROM ecritures_comptables WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM ecritures_comptables WHERE center_id = ?", c);
            jdbc.update("DELETE FROM comptes_payeurs WHERE center_id = ?", c);
            jdbc.update("DELETE FROM periodes_comptables WHERE center_id = ?", c);
            jdbc.update("DELETE FROM absence_patient WHERE center_id = ?", c);
            jdbc.update("DELETE FROM seances WHERE center_id = ?", c);
            jdbc.update("DELETE FROM facture_reglements WHERE center_id = ?", c);
            jdbc.update("DELETE FROM facture_lignes WHERE center_id = ?", c);
            jdbc.update("DELETE FROM factures WHERE center_id = ?", c);
            jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", c);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", c);
            jdbc.update("DELETE FROM centre_payeur WHERE center_id = ?", c);
            jdbc.update("DELETE FROM forfait WHERE center_id = ?", c);
            jdbc.update("DELETE FROM tva_types WHERE center_id = ?", c);
            jdbc.update("DELETE FROM facturation_settings WHERE center_id = ?", c);
            jdbc.update("DELETE FROM facture_sequence WHERE center_id = ?", c);
            jdbc.update("DELETE FROM agence WHERE center_id = ?", c);
            jdbc.update("DELETE FROM caisse_assurance WHERE center_id = ?", c);
        }
        jdbc.update("DELETE FROM app_user_societe WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE 'zt-f5-%')");
        jdbc.update("DELETE FROM app_user WHERE username LIKE 'zt-f5-%'");
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-F5-%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-F5-%'");
    }

    protected void nommer(UUID id, String nomAlias) {
        alias.put(id, nomAlias);
    }

    // ------------------------------------------------------------------ fixtures SQL

    protected void centre(UUID id, String code, String name, UUID societe) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", id, code, name, societe);
    }

    protected void utilisateur(UUID id, String username, String nomComplet) {
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)",
                id, username, "x", null, nomComplet);
    }

    protected void caisse(UUID id, UUID centre, String code, String nom) {
        jdbc.update("INSERT INTO caisse_assurance (id, center_id, code, nom, type_caisse) VALUES (?, ?, ?, ?, 'STANDARD')",
                id, centre, code, nom);
    }

    protected void agence(UUID id, UUID centre, UUID caisse, String code, String nom) {
        jdbc.update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, ?, ?)", id, centre, caisse, code, nom);
    }

    protected void patient(UUID id, UUID centre, String code, String nom, String prenom) {
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, created_at) VALUES (?, ?, ?, ?, ?, 'F', ?, ?, 'NON_VACANCIER', 'PERMANENT', ?)",
                id, centre, code, nom, prenom, Date.valueOf(LocalDate.of(2025, 1, 10)), "ASS-" + code,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    protected static Date date(String jour) {
        return jour == null ? null : Date.valueOf(LocalDate.parse(jour));
    }

    /**
     * Facture émise (fixture SQL) ; identifiant dérivé du rang (alias {@code <FACTURE_n>}). {@code statutSnapshot} peut être
     * nul ou blanc (situation patient figée sur la facture), {@code agence} peut être nul.
     */
    protected UUID facture(UUID centre, UUID patient, String numero, String dateFacturation, String tvaRate, String ht,
                           String tva, String ttc, UUID agence, String statutSnapshot) {
        compteurFactures++;
        UUID id = UUID.nameUUIDFromBytes(("f5:facture:" + compteurFactures).getBytes(StandardCharsets.UTF_8));
        nommer(id, "<FACTURE_" + compteurFactures + ">");
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, patient_code, patient_full_name, "
                        + "patient_status_snapshot, numero_immatriculation_snapshot, agence_id_snapshot, period_start, period_end, "
                        + "date_facturation, tva_rate, total_ht, total_tva, total_ttc, created_at) "
                        + "VALUES (?, ?, ?, ?, 'PAT', ?, ?, 'ASS-SNAP', ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, centre, patient, numero, "Snapshot " + numero, statutSnapshot, agence, date("2026-01-01"), date("2026-01-31"),
                date(dateFacturation), new BigDecimal(tvaRate), new BigDecimal(ht), new BigDecimal(tva), new BigDecimal(ttc),
                Timestamp.valueOf("2026-01-31 10:00:00"));
        return id;
    }

    /** Facture sans TVA (TTC = HT). */
    protected UUID factureSansTva(UUID centre, UUID patient, String numero, String dateFacturation, String ht, UUID agence, String statut) {
        return facture(centre, patient, numero, dateFacturation, "0.00", ht, "0.00", ht, agence, statut);
    }

    protected UUID ligne(UUID facture, UUID centre, String forfait, String prixUnitaire, int seances, String ligneHt) {
        compteurLignes++;
        UUID id = UUID.nameUUIDFromBytes(("f5:ligne:" + compteurLignes).getBytes(StandardCharsets.UTF_8));
        nommer(id, "<LIGNE_" + compteurLignes + ">");
        jdbc.update("INSERT INTO facture_lignes (id, facture_id, center_id, forfait_id, forfait_label, unit_price_ht, "
                        + "seance_count, line_ht) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, facture, centre, UUID.nameUUIDFromBytes(("f5:forfait:" + forfait).getBytes(StandardCharsets.UTF_8)),
                forfait, new BigDecimal(prixUnitaire), seances, new BigDecimal(ligneHt));
        return id;
    }

    protected UUID seance(UUID patient, UUID centre, String jour, String statut, UUID facture) {
        compteurSeances++;
        UUID id = UUID.nameUUIDFromBytes(("f5:seance:" + compteurSeances).getBytes(StandardCharsets.UTF_8));
        nommer(id, "<SEANCE_" + compteurSeances + ">");
        Timestamp t = Timestamp.valueOf("2026-01-15 08:00:00");
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, facture_id, created_at, validated_at, "
                + "signed_infirmier_at, signed_medecin_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, patient, centre, date(jour), statut, facture, t, t, t, t);
        return id;
    }

    protected UUID paiement(UUID facture, UUID centre, String montant, String jour) {
        compteurPaiements++;
        UUID id = UUID.nameUUIDFromBytes(("f5:paiement:" + compteurPaiements).getBytes(StandardCharsets.UTF_8));
        nommer(id, "<PAIEMENT_" + compteurPaiements + ">");
        jdbc.update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement, saisi_par, "
                        + "code_reglement, created_at) VALUES (?, ?, ?, ?, ?, 'u', ?, ?)",
                id, facture, centre, new BigDecimal(montant), date(jour), "REG-F5-" + compteurPaiements,
                Timestamp.valueOf("2026-03-01 09:00:00"));
        return id;
    }

    // ------------------------------------------------------------------ requêtes

    protected Resp doGet(String path, RequestPostProcessor who) throws Exception {
        return reponse(mvc.perform(get(java.net.URI.create(path)).with(who)).andReturn().getResponse());
    }

    protected Resp doGetAnonyme(String path) throws Exception {
        return reponse(mvc.perform(get(java.net.URI.create(path))).andReturn().getResponse());
    }

    protected Resp doPost(String path, String corps, RequestPostProcessor who) throws Exception {
        return reponse(mvc.perform(post(java.net.URI.create(path)).contentType(MediaType.APPLICATION_JSON)
                .content(corps).with(who)).andReturn().getResponse());
    }

    protected Resp doPostAnonyme(String path, String corps) throws Exception {
        return reponse(mvc.perform(post(java.net.URI.create(path)).contentType(MediaType.APPLICATION_JSON)
                .content(corps)).andReturn().getResponse());
    }

    protected Resp dashboard(UUID centre, String mois, RequestPostProcessor who) throws Exception {
        return doGet(FACTURATION + "/dashboard?centerId=" + centre + (mois == null ? "" : "&month=" + mois), who);
    }

    protected String corpsSynthese(UUID centre, String debut, String fin, String format) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("'centerId':").append(centre == null ? "null" : "'" + centre + "'");
        sb.append(",'periodStart':").append(debut == null ? "null" : "'" + debut + "'");
        sb.append(",'periodEnd':").append(fin == null ? "null" : "'" + fin + "'");
        if (format != null) sb.append(",'format':'").append(format).append("'");
        return json(sb.append("}").toString());
    }

    protected Resp synthese(UUID centre, String debut, String fin, String format, RequestPostProcessor who) throws Exception {
        return doPost(FACTURATION + "/synthese/print", corpsSynthese(centre, debut, fin, format), who);
    }

    protected static String json(String corps) {
        return corps.replace('\'', '"');
    }

    private static Resp reponse(MockHttpServletResponse r) throws Exception {
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(StandardCharsets.UTF_8), r.getContentAsByteArray(),
                r.getHeader("Content-Disposition"));
    }

    protected JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    // ------------------------------------------------------------------ PDF

    /** Texte d'un PDF (extraction PDFBox), fins de ligne normalisées. */
    protected static String texteDuPdf(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc).replace("\r\n", "\n");
        }
    }

    protected static int pagesDuPdf(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    /** Espaces insécables et fines remplacés par une espace simple. */
    protected static String espacesSimples(String s) {
        return s.replaceAll("[\\u00a0\\u202f]", " ");
    }

    /**
     * Golden d'une synthèse imprimée : statut, type, en-tête de disposition, signature, nombre de pages et texte extrait.
     */
    protected void goldenPdf(String name, Resp r) throws Exception {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status);
        o.put("contentType", r.contentType);
        o.put("contentDisposition", r.disposition);
        o.put("signaturePdf", r.octets.length > 4 && new String(r.octets, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"));
        o.put("pages", pagesDuPdf(r.octets));
        o.put("texte", new Scrubber().scrubString(espacesSimples(texteDuPdf(r.octets))));
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(o));
    }

    // ------------------------------------------------------------------ golden

    protected void golden(String name, Resp r) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, new Scrubber().render(r));
    }

    protected void goldenNode(String name, JsonNode node) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), name,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(new Scrubber().scrub(node)));
    }

    protected void goldenTexte(String name, Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status);
        o.put("contentType", r.contentType);
        o.put("contentDisposition", r.disposition);
        o.put("contenu", new Scrubber().scrubString(r.raw));
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(o));
    }

    /** Sous-ensemble d'une réponse JSON : les champs listés (dans l'ordre), les autres sont ignorés. */
    protected JsonNode projeter(JsonNode source, String... champs) {
        ObjectNode o = mapper.createObjectNode();
        for (String c : champs) {
            o.set(c, source.path(c));
        }
        return o;
    }

    protected record Resp(int status, String contentType, String raw, byte[] octets, String disposition) {
    }

    /**
     * Normalise : identifiants nommés -> alias, autres UUID -> {@code <UUID_GENERE_n>}, horodatages -> {@code <HORODATAGE>},
     * jour d'exécution -> {@code <AUJOURD_HUI>}, clés triées, messages techniques non verrouillés.
     */
    protected final class Scrubber {
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private int inconnus = 0;

        Scrubber() {
            NOMS.forEach((id, nomAlias) -> aliases.put(id.toString(), nomAlias));
            alias.forEach((id, nomAlias) -> aliases.put(id.toString(), nomAlias));
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
