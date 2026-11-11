package com.hemodialyse.backend.characterization.migration;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Harnais du filet S6a (ADR 0133) : l'outil de reprise historique des patients, assures, affectations et soldes d'ouverture
 * ({@code /api/v1/admin/migration/**}) observe en HTTP (MockMvc, Spring complet, securite reelle, H2) ET par l'etat des tables
 * ({@code patients}, {@code assure}, {@code assure_patient}, {@code migration_id_map}, {@code factures}...). Aucun mock du
 * code caracterise, aucun seam.
 * <p>
 * Non-determinisme : tous les identifiants de fixture sont fixes et NOMMES ; les identifiants generes (patients, affectations,
 * factures) sont nommes par cle metier ({@code <PATIENT_ZP111>}) ; tout autre UUID devient {@code <UUID_GENERE_n>} ; les
 * horodatages deviennent {@code <HORODATAGE>} ; les cles d'objets sont triees (les parametres d'anomalie sortent d'une
 * {@code Map.of}). Les dates de fixture sont des constantes passees ou lointaines, independantes du jour d'execution.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class HistoriquePatientSupport {

    protected static final String BASE = "/api/v1/admin/migration";
    protected static final UUID CENTRE_A = UUID.fromString("99997800-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99997800-0000-0000-0000-00000000000b");
    protected static final UUID ADMIN_A = UUID.fromString("99997800-0000-0000-0000-0000000000f1");
    protected static final UUID ADMIN_B = UUID.fromString("99997800-0000-0000-0000-0000000000f2");
    protected static final UUID TECH = UUID.fromString("99997800-0000-0000-0000-0000000000f3");
    protected static final UUID SUPER = UUID.fromString("99997800-0000-0000-0000-0000000000f4");
    protected static final UUID SALLE_1 = UUID.fromString("99997800-0000-0000-0000-0000000005a1");
    protected static final UUID SALLE_2 = UUID.fromString("99997800-0000-0000-0000-0000000005a2");
    protected static final UUID SALLE_B = UUID.fromString("99997800-0000-0000-0000-0000000005b1");
    protected static final UUID CRENEAU_1 = UUID.fromString("99997800-0000-0000-0000-0000000006a1");
    protected static final UUID TRANSPORTEUR_1 = UUID.fromString("99997800-0000-0000-0000-0000000007a1");
    protected static final UUID MEDECIN_1 = UUID.fromString("99997800-0000-0000-0000-0000000008a1");
    protected static final UUID PAYEUR_1 = UUID.fromString("99997800-0000-0000-0000-0000000009a1");
    protected static final UUID AGENCE_1 = UUID.fromString("99997800-0000-0000-0000-000000000aa1");
    protected static final UUID CAISSE_1 = UUID.fromString("99997800-0000-0000-0000-000000000ca1");

    protected static final String ENTETE_ASSURES =
            "legacyId;numeroAssurance;nom;prenom;sexe;dateNaissance;telPersonnel;telMobile;adresse;groupeSanguin";
    protected static final String ENTETE_PATIENTS =
            "legacyId;numeroAssurance;nom;prenom;sexe;dateNaissance;dateAdmission;etatPatient;qualiteAssure;assureNumeroAssurance";

    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "migration", "historique");
    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HORODATAGE_RE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}(:?\\d{2})?|Z)?");

    /** Code patient attribue a la creation par {@code Patient.creer} : derive d'un identifiant aleatoire. */
    private static final Pattern CODE_GENERE_RE = Pattern.compile("PAT-[0-9A-F]{8}\\b");

    protected final ObjectMapper mapper = JsonMapper.builder().build();
    private final Map<UUID, String> alias = new LinkedHashMap<>();
    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected CacheManager caches;
    @Autowired
    protected com.hemodialyse.backend.infrastructure.security.RoleScopeFilter roleScopeFilter;
    protected MockMvc mvc;

    // ------------------------------------------------------------------ identites

    protected static RequestPostProcessor qui(UUID centre, UUID utilisateur, String role) {
        return user(UserPrincipal.create(utilisateur.toString(), centre.toString(), "u-" + utilisateur, "", List.of(role), true));
    }

    protected static RequestPostProcessor adminA() {
        return qui(CENTRE_A, ADMIN_A, "ADMIN");
    }

    protected static RequestPostProcessor adminB() {
        return qui(CENTRE_B, ADMIN_B, "ADMIN");
    }

    protected static String json(String corps) {
        return corps.replace('\'', '"');
    }

    protected static String fichier(String entete, String... lignes) {
        return entete + "\n" + String.join("\n", lignes) + "\n";
    }

    // ------------------------------------------------------------------ cycle de vie

    @BeforeEach
    void setUpHistorique() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        nettoyerHistorique();
        alias.clear();
        nommer(CENTRE_A, "<CENTRE_A>");
        nommer(CENTRE_B, "<CENTRE_B>");
        nommer(SALLE_1, "<SALLE_S1>");
        nommer(SALLE_2, "<SALLE_S2>");
        nommer(SALLE_B, "<SALLE_B>");
        nommer(CRENEAU_1, "<CRENEAU_CR1>");
        nommer(TRANSPORTEUR_1, "<TRANSPORTEUR_1>");
        nommer(MEDECIN_1, "<MEDECIN_1>");
        nommer(PAYEUR_1, "<PAYEUR_1>");
        nommer(ADMIN_A, "<ADMIN_A>");
        nommer(ADMIN_B, "<ADMIN_B>");
        nommer(TECH, "<TECHNICIEN>");
        nommer(SUPER, "<SUPERADMIN>");
    }

    @AfterEach
    void nettoyerHistorique() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            for (String table : List.of("facture_reglements", "facture_lignes", "factures", "facture_sequence", "prise_en_charge",
                    "assure_patient", "patients", "assure", "salle", "position_creneau", "transporteur", "medecin",
                    "centre_payeur", "agence", "caisse_assurance", "migration_entity_run", "migration_id_map",
                    "migration_value_map", "migration_batch")) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
        viderLesCaches();
    }

    /** Les fixtures SQL contournent les adaptateurs : les caches de l'application sont vides avant chaque lecture. */
    protected void viderLesCaches() {
        caches.getCacheNames().forEach(n -> {
            var c = caches.getCache(n);
            if (c != null) c.clear();
        });
    }

    protected void nommer(UUID id, String nom) {
        alias.put(id, nom);
    }

    // ------------------------------------------------------------------ fixtures

    /** Referentiels du centre A (salles, creneau, transporteur, medecin, centre payeur) et une salle du centre B. */
    protected void referentiels() {
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, capacite) VALUES (?, ?, 'S1', 'Salle 1', 10)", SALLE_1, CENTRE_A);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, capacite) VALUES (?, ?, 'S2', 'Salle 2', 10)", SALLE_2, CENTRE_A);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, capacite) VALUES (?, ?, 'SB', 'Salle B', 10)", SALLE_B, CENTRE_B);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'CR1', 'Matin')", CRENEAU_1, CENTRE_A);
        jdbc.update("INSERT INTO transporteur (id, center_id, nom) VALUES (?, ?, 'Ambulances Rouiba')", TRANSPORTEUR_1, CENTRE_A);
        jdbc.update("INSERT INTO medecin (id, center_id, nom, prenom, specialite) VALUES (?, ?, 'OUARET', 'Mustapha', 'Nephrologue')",
                MEDECIN_1, CENTRE_A);
        jdbc.update("INSERT INTO caisse_assurance (id, center_id, code, nom, type_caisse) VALUES (?, ?, 'CNAS', 'CNAS', 'STANDARD')",
                CAISSE_1, CENTRE_A);
        jdbc.update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, 'AG-1', 'Agence 1')",
                AGENCE_1, CENTRE_A, CAISSE_1);
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, 'CP-01', 'Payeur 1')",
                PAYEUR_1, CENTRE_A, AGENCE_1);
        viderLesCaches();
    }

    // ------------------------------------------------------------------ requetes

    protected record Resp(int status, String contentType, String raw) {
    }

    protected Resp envoyer(MockHttpServletRequestBuilder requete, RequestPostProcessor who) throws Exception {
        return reponse(mvc.perform(requete.with(who)).andReturn().getResponse());
    }

    protected Resp envoyerJson(MockHttpServletRequestBuilder requete, RequestPostProcessor who, String corps) throws Exception {
        return reponse(mvc.perform(requete.with(who).contentType(MediaType.APPLICATION_JSON).content(json(corps)))
                .andReturn().getResponse());
    }

    private static Resp reponse(MockHttpServletResponse r) throws Exception {
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(StandardCharsets.UTF_8));
    }

    protected UUID ouvrirLot(RequestPostProcessor who) throws Exception {
        Resp r = envoyerJson(post(BASE + "/batches"), who, "{'libelle':'Reprise S6a','sourceSystem':'AncienLogiciel'}");
        if (r.status != 201) throw new IllegalStateException("ouverture du lot : " + r.status + " " + r.raw);
        UUID lot = UUID.fromString(mapper.readTree(r.raw).path("id").asString());
        nommer(lot, "<LOT>");
        return lot;
    }

    protected Resp importer(UUID lot, String entite, String contenu, boolean dryRun) throws Exception {
        return importer(lot, entite, contenu, dryRun, adminA(), null);
    }

    protected Resp importer(UUID lot, String entite, String contenu, boolean dryRun, RequestPostProcessor who, UUID centre)
            throws Exception {
        return importerFichier(lot, entite, "import.csv", contenu.getBytes(StandardCharsets.UTF_8), dryRun, who, centre);
    }

    protected Resp importerFichier(UUID lot, String entite, String nomFichier, byte[] octets, boolean dryRun,
                                   RequestPostProcessor who, UUID centre) throws Exception {
        var requete = multipart(BASE + "/batches/{id}/import/{entity}", lot, entite)
                .file(new MockMultipartFile("file", nomFichier, "text/csv", octets))
                .param("dryRun", String.valueOf(dryRun));
        if (centre != null) requete.param("centerId", centre.toString());
        return reponse(mvc.perform(requete.with(who)).andReturn().getResponse());
    }

    protected Resp annuler(UUID lot, RequestPostProcessor who) throws Exception {
        return envoyer(post(BASE + "/batches/{id}/cancel", lot), who);
    }

    /** Importe sans verifier le resultat (preparation d'un scenario) ; echoue si l'import n'est pas applique. */
    protected void importerOk(UUID lot, String entite, String contenu) throws Exception {
        Resp r = importer(lot, entite, contenu, false);
        JsonNode corps = mapper.readTree(r.raw);
        if (r.status != 200 || !corps.path("applied").asBoolean()) {
            throw new IllegalStateException("preparation " + entite + " : " + r.status + " " + r.raw);
        }
    }

    // ------------------------------------------------------------------ etat des tables

    /** Instantane des tables de la reprise pour un centre : tout ce que l'outil peut ecrire ou supprimer. */
    protected ObjectNode etat(UUID centre) {
        nommerLesIdentifiants();
        ObjectNode o = mapper.createObjectNode();
        ajouter(o, "assures", lignes("SELECT numero_assurance, nom, prenom, sexe, date_naissance, tel_personnel, tel_mobile, "
                + "tel_bureau, adresse, groupe_sanguin, created_at FROM assure WHERE center_id = ? ORDER BY numero_assurance", centre));
        ArrayNode patients = lignes("SELECT * FROM patients WHERE center_id = ? ORDER BY numero_assurance, nom", centre,
                "id", "center_id", "photo_base64", "pieces_jointes_json", "assure_history_json");
        // Lisibilite : les booleens a faux et un nombre d'enfants a 0 (valeurs par defaut ecrites par l'outil) ne sont pas listes.
        patients.forEach(p -> {
            ObjectNode patient = (ObjectNode) p;
            List.copyOf(patient.propertyNames()).forEach(k -> {
                if (patient.get(k).isBoolean() && !patient.get(k).asBoolean()) patient.remove(k);
            });
            if ("0".equals(patient.path("nombre_enfants").asString())) patient.remove("nombre_enfants");
        });
        ajouter(o, "patients", patients);
        ajouter(o, "affectations", lignes("SELECT COALESCE(p.numero_assurance, '<ORPHELIN>') AS patient, "
                + "a.numero_assurance AS assure, a.is_primary, a.date_debut_affectation, a.date_fin_affectation, a.date_affectation "
                + "FROM assure_patient a LEFT JOIN patients p ON p.id = a.patient_id WHERE a.center_id = ? "
                + "ORDER BY patient, a.numero_assurance, a.date_debut_affectation, a.is_primary", centre));
        ajouter(o, "correspondances", lignes("SELECT entity, legacy_id, operation, target_id FROM migration_id_map "
                + "WHERE center_id = ? ORDER BY entity, legacy_id", centre));
        return o;
    }

    /**
     * Instantane reduit pour les scenarios longs : affectations, correspondances des seules entites citees et colonnes citees des
     * patients (le reste des patients est verrouille par ailleurs).
     */
    protected ObjectNode etatCompact(UUID centre, List<String> entites, String colonnesPatient) {
        nommerLesIdentifiants();
        ObjectNode o = mapper.createObjectNode();
        ajouter(o, "patients", lignes("SELECT " + colonnesPatient + " FROM patients WHERE center_id = ? ORDER BY numero_assurance",
                centre));
        ajouter(o, "affectations", lignes("SELECT COALESCE(p.numero_assurance, '<ORPHELIN>') AS patient, "
                + "a.numero_assurance AS assure, a.is_primary, a.date_debut_affectation, a.date_fin_affectation "
                + "FROM assure_patient a LEFT JOIN patients p ON p.id = a.patient_id WHERE a.center_id = ? "
                + "ORDER BY patient, a.numero_assurance, a.date_debut_affectation, a.is_primary", centre));
        ajouter(o, "correspondances", lignes("SELECT entity, legacy_id, operation, target_id FROM migration_id_map "
                + "WHERE center_id = ? AND entity IN ('" + String.join("','", entites) + "') ORDER BY entity, legacy_id", centre));
        return o;
    }

    /** Lots du centre (libelle, statut). */
    protected ArrayNode lots(UUID centre) {
        return lignes("SELECT libelle, status FROM migration_batch WHERE center_id = ? ORDER BY libelle, status", centre);
    }

    /** Les tables vides sont omises de l'instantane (leur absence signifie « aucune ligne »). */
    private static void ajouter(ObjectNode o, String cle, ArrayNode lignes) {
        if (!lignes.isEmpty()) o.set(cle, lignes);
    }

    protected ObjectNode etat() {
        return etat(CENTRE_A);
    }

    /** Journal des executions (verifications et imports) du centre, sans instant ni identifiant. */
    protected ArrayNode executions(UUID centre) {
        return lignes("SELECT entity, file_name, dry_run, applied, total_rows, created_count, updated_count, error_count, "
                + "warning_count, executed_by FROM migration_entity_run WHERE center_id = ? "
                + "ORDER BY entity, dry_run, applied, total_rows, created_count, updated_count, error_count, warning_count", centre);
    }

    protected ObjectNode etatFactures(UUID centre) {
        nommerLesIdentifiants();
        ObjectNode o = mapper.createObjectNode();
        o.set("factures", lignes("SELECT numero_facture, patient_code, patient_full_name, patient_status_snapshot, "
                + "numero_immatriculation_snapshot, period_start, period_end, date_facturation, tva_rate, total_ht, total_tva, "
                + "total_ttc FROM factures WHERE center_id = ? ORDER BY numero_facture", centre));
        o.set("lignes", lignes("SELECT f.numero_facture, l.forfait_label, l.unit_price_ht, l.seance_count, l.line_ht "
                + "FROM facture_lignes l JOIN factures f ON f.id = l.facture_id WHERE l.center_id = ? ORDER BY f.numero_facture",
                centre));
        o.set("reglements", lignes("SELECT f.numero_facture, r.montant, r.date_reglement, r.saisi_par, r.mode_reglement "
                + "FROM facture_reglements r JOIN factures f ON f.id = r.facture_id WHERE r.center_id = ? "
                + "ORDER BY f.numero_facture, r.date_reglement, r.montant", centre));
        return o;
    }

    /** Nomme les identifiants generes par cle metier (patients, affectations, factures). */
    protected void nommerLesIdentifiants() {
        for (Map<String, Object> p : jdbc.queryForList("SELECT id, center_id, numero_assurance FROM patients")) {
            UUID centre = (UUID) p.get("CENTER_ID");
            if (!CENTRE_A.equals(centre) && !CENTRE_B.equals(centre)) continue;
            nommer((UUID) p.get("ID"), "<PATIENT_" + (CENTRE_B.equals(centre) ? "B_" : "") + p.get("NUMERO_ASSURANCE") + ">");
        }
        for (Map<String, Object> f : jdbc.queryForList("SELECT id, center_id, numero_facture FROM factures")) {
            UUID centre = (UUID) f.get("CENTER_ID");
            if (!CENTRE_A.equals(centre) && !CENTRE_B.equals(centre)) continue;
            nommer((UUID) f.get("ID"), "<FACTURE_" + f.get("NUMERO_FACTURE") + ">");
        }
        for (Map<String, Object> a : jdbc.queryForList("SELECT a.id, a.center_id, p.numero_assurance AS pn, a.numero_assurance AS an, "
                + "a.date_debut_affectation AS d, a.is_primary AS pr FROM assure_patient a LEFT JOIN patients p ON p.id = a.patient_id")) {
            UUID centre = (UUID) a.get("CENTER_ID");
            if (!CENTRE_A.equals(centre) && !CENTRE_B.equals(centre)) continue;
            nommer((UUID) a.get("ID"), "<AFFECTATION_" + a.get("PN") + "_" + a.get("AN") + "_" + a.get("D") + "_" + a.get("PR") + ">");
        }
    }

    /** Lignes SQL en JSON ; les valeurs nulles sont omises ; les colonnes exclues sont citees apres les arguments. */
    protected ArrayNode lignes(String sql, Object... argsEtExclusions) {
        List<Object> args = new ArrayList<>();
        List<String> exclues = new ArrayList<>();
        for (Object o : argsEtExclusions) {
            if (o instanceof String s && !args.isEmpty()) exclues.add(s.toUpperCase());
            else args.add(o);
        }
        ArrayNode a = mapper.createArrayNode();
        for (Map<String, Object> row : jdbc.queryForList(sql, args.toArray())) {
            ObjectNode o = mapper.createObjectNode();
            row.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                Object v = e.getValue();
                if (v == null || exclues.contains(e.getKey())) return;
                String cle = e.getKey().toLowerCase();
                if (v instanceof BigDecimal b) o.put(cle, b.toPlainString());
                else if (v instanceof Boolean b) o.put(cle, b);
                else o.put(cle, String.valueOf(v));
            });
            a.add(o);
        }
        return a;
    }

    // ------------------------------------------------------------------ golden

    /** Reponse HTTP : statut et corps (JSON, sinon texte). */
    protected ObjectNode rendu(Resp r) {
        ObjectNode n = mapper.createObjectNode();
        n.put("http", r.status);
        if (r.raw == null || r.raw.isBlank()) {
            n.putNull("corps");
        } else {
            try {
                n.set("corps", mapper.readTree(r.raw));
            } catch (RuntimeException e) {
                n.put("texte", r.raw);
            }
        }
        return n;
    }

    protected void golden(String name, ObjectNode racine) {
        GoldenMaster.verify(GOLDENS, name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(new Scrubber().scrub(racine)));
    }

    /** Identifiants connus -> alias, autres UUID -> {@code <UUID_GENERE_n>}, horodatages -> {@code <HORODATAGE>}, cles triees. */
    private final class Scrubber {
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private int inconnus = 0;

        Scrubber() {
            alias.forEach((id, nom) -> aliases.put(id.toString(), nom));
        }

        String scrubString(String raw) {
            String s = HORODATAGE_RE.matcher(raw).replaceAll("<HORODATAGE>");
            s = CODE_GENERE_RE.matcher(s).replaceAll("<CODE_PATIENT_GENERE>");
            Matcher m = UUID_RE.matcher(s);
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
    }
}
