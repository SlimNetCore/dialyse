package com.hemodialyse.backend.characterization.documents.doc4c;

import com.hemodialyse.backend.application.audit.AuditWriterService;
import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.AuditRequestFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Filet de DOC4c (règles d'impression des types sensibles, ADR 0166) : la matrice rôle x type x voie de l'impression générique
 * ({@code POST /api/v1/documents/print} et {@code /print/{modeleId}}) pour l'ordonnance (KB-MED-18), la synthèse mensuelle de facturation
 * (KB-FAC-52/60) et un type ordinaire (KB-DOC1-3), la garde de centre, l'ordre « garde avant toute lecture » et l'audit des refus.
 * Point d'observation : HTTP (MockMvc, sécurité réelle, JDBC réel sur H2 {@code test}). Aucun mock du code caractérisé.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class GardesDesTypesSensiblesCharacterizationIntegrationTest {

    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "documents", "doc4c");
    private static final UUID SOCIETE = UUID.fromString("99998166-0000-0000-0000-000000000001");
    private static final UUID CENTRE_D = UUID.fromString("99998166-0000-0000-0000-00000000000d");
    private static final UUID CENTRE_E = UUID.fromString("99998166-0000-0000-0000-00000000000e");
    private static final UUID UTILISATEUR = UUID.fromString("99998166-0000-0000-0000-0000000000f1");

    private static final Pattern UUID_RE = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HORODATAGE = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}(:?\\d{2})?|Z)?");

    private static final List<String> TYPES = List.of("ORDONNANCE", "SYNTHESE_FACTURATION_MENSUELLE", "LISTE_PATIENTS");
    private static final Map<String, String> JRXML = Map.of("ORDONNANCE", "reports/ordonnance.jrxml",
            "SYNTHESE_FACTURATION_MENSUELLE", "reports/synthese_mensuelle_facturation.jrxml", "LISTE_PATIENTS", "reports/liste_patients.jrxml");
    private static final List<List<String>> PROFILS = List.of(List.of("ADMIN"), List.of("SECRETAIRE"), List.of("MEDECIN"), List.of("INFIRMIER"),
            List.of("DIRECTION"), List.of("SUPERADMIN"), List.of("SOCIETE_ADMIN"), List.of("MEDECIN", "INFIRMIER"), List.of("ADMIN", "INFIRMIER"),
            List.of("SECRETAIRE", "MEDECIN"), List.of("ROLE_INCONNU"), List.of());

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuditRequestFilter filtreDAudit;
    @Autowired
    private AuditWriterService ecrivainDAudit;

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private MockMvc mvc;
    private String scenario = "?";

    @BeforeEach
    void monter(TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        nettoyer();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOCIETE, "ZT-D4C", "Societe Gardes");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", CENTRE_D, "ZT-D4C-D", "Centre D", SOCIETE);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", CENTRE_E, "ZT-D4C-E", "Centre E", SOCIETE);
        for (UUID centre : List.of(CENTRE_D, CENTRE_E)) {
            for (String type : TYPES) modele(centre, type, "PDF");
        }
    }

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM audit_log WHERE center_id IN (?, ?)", CENTRE_D, CENTRE_E);
        jdbc.update("DELETE FROM modele_document_version WHERE modele_id IN (SELECT id FROM modele_document WHERE center_id IN (?, ?))", CENTRE_D, CENTRE_E);
        jdbc.update("DELETE FROM modele_document WHERE center_id IN (?, ?)", CENTRE_D, CENTRE_E);
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-D4C%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-D4C%'");
    }

    private static UUID idDuModele(UUID centre, String type) {
        return UUID.nameUUIDFromBytes(("doc4c-" + centre + "-" + type).getBytes(StandardCharsets.UTF_8));
    }

    private void modele(UUID centre, String type, String format) {
        jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, "
                        + "description, active, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)", idDuModele(centre, type), centre, type + "-4C", "Modele " + type,
                type, JRXML.get(type), format, "fictif", true, OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    private static RequestPostProcessor qui(UUID centre, List<String> roles) {
        return user(UserPrincipal.create(UTILISATEUR.toString(), centre == null ? null : centre.toString(), "u-4c", "", roles, true));
    }

    private static String nom(List<String> roles) {
        return roles.isEmpty() ? "aucun role" : String.join("+", roles);
    }

    private String corps(UUID centre, String type, String formatOverride) {
        return "{" + (centre == null ? "" : "\"centerId\":\"" + centre + "\",") + (type == null ? "" : "\"typeDocument\":\"" + type + "\",")
                + (formatOverride == null ? "" : "\"formatOverride\":\"" + formatOverride + "\",") + "\"params\":{}}";
    }

    private Map<String, Object> envoyer(String chemin, String json, RequestPostProcessor qui) throws Exception {
        var requete = MockMvcRequestBuilders.post(chemin);
        if (json != null) requete.contentType(MediaType.APPLICATION_JSON).content(json);
        if (qui != null) requete.with(qui);
        return vue(mvc.perform(requete).andReturn().getResponse());
    }

    private Map<String, Object> vue(MockHttpServletResponse r) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.getStatus());
        String type = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        m.put("contentType", type);
        String texte = new String(r.getContentAsByteArray(), StandardCharsets.UTF_8);
        if (type != null && type.contains("json") && !texte.isEmpty()) {
            tools.jackson.databind.JsonNode arbre = mapper.readTree(masquer(texte));
            m.put("code", arbre.path("code").asString(null));
            m.put("detail", arbre.path("detail").asString(null));
        } else if (type != null && type.startsWith("text/") && !texte.isEmpty()) {
            m.put("texte", masquer(texte));
        }
        return m;
    }

    private String masquer(String texte) {
        Matcher m = UUID_RE.matcher(texte);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String id = m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(id.equals(CENTRE_D.toString()) ? "<CENTRE_D>" : id.equals(CENTRE_E.toString()) ? "<CENTRE_E>"
                    : id.equals(UTILISATEUR.toString()) ? "<UTILISATEUR>" : "<UUID>"));
        }
        m.appendTail(sb);
        return HORODATAGE.matcher(sb.toString()).replaceAll("<HORODATAGE>");
    }

    private void golden(String nom, Object valeur) {
        GoldenMaster.verify(GOLDENS.resolve(scenario), nom, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(valeur));
    }

    @Test
    void la_matrice_role_x_type_par_la_voie_du_type() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        for (String type : TYPES) {
            Map<String, Object> parProfil = new LinkedHashMap<>();
            for (List<String> roles : PROFILS) {
                parProfil.put(nom(roles), envoyer("/api/v1/documents/print", corps(CENTRE_D, type, "EXCEL"), qui(CENTRE_D, roles)));
            }
            parProfil.put("anonyme", envoyer("/api/v1/documents/print", corps(CENTRE_D, type, "EXCEL"), null));
            e.put(type, parProfil);
        }
        golden("matrice", e);
    }

    @Test
    void la_matrice_role_x_type_par_la_voie_du_modele() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        for (String type : TYPES) {
            Map<String, Object> parProfil = new LinkedHashMap<>();
            for (List<String> roles : PROFILS) {
                parProfil.put(nom(roles), envoyer("/api/v1/documents/print/" + idDuModele(CENTRE_D, type), corps(CENTRE_D, null, "EXCEL"),
                        qui(CENTRE_D, roles)));
            }
            parProfil.put("anonyme", envoyer("/api/v1/documents/print/" + idDuModele(CENTRE_D, type), corps(CENTRE_D, null, "EXCEL"), null));
            e.put(type, parProfil);
        }
        e.put("modele inconnu (MEDECIN)", envoyer("/api/v1/documents/print/" + UUID.nameUUIDFromBytes("inconnu".getBytes(StandardCharsets.UTF_8)),
                corps(CENTRE_D, null, "EXCEL"), qui(CENTRE_D, List.of("MEDECIN"))));
        e.put("modele inconnu (INFIRMIER)", envoyer("/api/v1/documents/print/" + UUID.nameUUIDFromBytes("inconnu".getBytes(StandardCharsets.UTF_8)),
                corps(CENTRE_D, null, "EXCEL"), qui(CENTRE_D, List.of("INFIRMIER"))));
        golden("matrice_modele", e);
    }

    @Test
    void la_garde_de_centre_et_l_ordre_des_gardes() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        for (String type : TYPES) {
            String role = type.equals("ORDONNANCE") ? "MEDECIN" : type.equals("LISTE_PATIENTS") ? "INFIRMIER" : "SECRETAIRE";
            e.put(type + " : session E, centre D dans le corps (" + role + ")",
                    envoyer("/api/v1/documents/print", corps(CENTRE_D, type, "EXCEL"), qui(CENTRE_E, List.of(role))));
            e.put(type + " : session sans centre (" + role + ")", envoyer("/api/v1/documents/print", corps(CENTRE_D, type, "EXCEL"), qui(null, List.of(role))));
            e.put(type + " : session sans centre, corps sans centre (" + role + ")",
                    envoyer("/api/v1/documents/print", corps(null, type, "EXCEL"), qui(null, List.of(role))));
            e.put(type + " : SUPERADMIN, centre D dans le corps", envoyer("/api/v1/documents/print", corps(CENTRE_D, type, "EXCEL"),
                    qui(CENTRE_E, List.of("SUPERADMIN", role))));
            e.put(type + " : par le modele, session E, centre D dans le corps (" + role + ")",
                    envoyer("/api/v1/documents/print/" + idDuModele(CENTRE_D, type), corps(CENTRE_D, null, "EXCEL"), qui(CENTRE_E, List.of(role))));
        }
        // 403 avant 400 : un profil insuffisant est refusé même si la demande est incomplète ou illisible
        e.put("ORDONNANCE, INFIRMIER, corps sans centre", envoyer("/api/v1/documents/print", corps(null, "ORDONNANCE", null), qui(CENTRE_D, List.of("INFIRMIER"))));
        e.put("ORDONNANCE, INFIRMIER, centre illisible", envoyer("/api/v1/documents/print",
                "{\"centerId\":\"pas-un-uuid\",\"typeDocument\":\"ORDONNANCE\",\"params\":{}}", qui(CENTRE_D, List.of("INFIRMIER"))));
        e.put("ORDONNANCE, INFIRMIER, params illisibles", envoyer("/api/v1/documents/print",
                "{\"typeDocument\":\"ORDONNANCE\",\"params\":\"x\"}", qui(CENTRE_D, List.of("INFIRMIER"))));
        e.put("SYNTHESE, MEDECIN, params illisibles", envoyer("/api/v1/documents/print",
                "{\"typeDocument\":\"SYNTHESE_FACTURATION_MENSUELLE\",\"params\":\"x\"}", qui(CENTRE_D, List.of("MEDECIN"))));
        e.put("ordonnance en minuscules et espaces, INFIRMIER", envoyer("/api/v1/documents/print", corps(CENTRE_D, "  ordonnance ", "EXCEL"),
                qui(CENTRE_D, List.of("INFIRMIER"))));
        e.put("synthese en minuscules, MEDECIN", envoyer("/api/v1/documents/print", corps(CENTRE_D, "synthese_facturation_mensuelle", "EXCEL"),
                qui(CENTRE_D, List.of("MEDECIN"))));
        e.put("ORDONNANCE, MEDECIN, corps illisible", envoyer("/api/v1/documents/print", "{pas du json", qui(CENTRE_D, List.of("MEDECIN"))));
        e.put("ORDONNANCE, MEDECIN, sans corps", envoyer("/api/v1/documents/print", null, qui(CENTRE_D, List.of("MEDECIN"))));
        golden("gardes", e);
    }

    @Test
    void les_totaux_du_serveur_ne_viennent_pas_du_client() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        String[] periodes = {"\"PERIOD_START\":\"2026-02-01\",\"PERIOD_END\":\"2026-02-28\"", "\"PERIOD_START\":\"2026-03-01\",\"PERIOD_END\":\"2026-02-01\"",
                "\"PERIOD_START\":\"illisible\",\"PERIOD_END\":\"2026-02-28\"", "\"PERIOD_START\":\"2026-02-01\"", "",
                "\"PERIOD_START\":\"2026-02-01\",\"PERIOD_END\":\"2026-02-28\",\"TOTAL_GLOBAL\":\"999999\""};
        int i = 0;
        for (String periode : periodes) {
            String json = "{\"centerId\":\"" + CENTRE_D + "\",\"typeDocument\":\"SYNTHESE_FACTURATION_MENSUELLE\",\"formatOverride\":\"EXCEL\",\"params\":{" + periode + "}}";
            e.put("voie du type, periode " + ++i, envoyer("/api/v1/documents/print", json, qui(CENTRE_D, List.of("ADMIN"))));
            e.put("voie du modele, periode " + i, envoyer("/api/v1/documents/print/" + idDuModele(CENTRE_D, "SYNTHESE_FACTURATION_MENSUELLE"), json,
                    qui(CENTRE_D, List.of("ADMIN"))));
        }
        golden("totaux", e);
    }

    @Test
    void l_audit_des_impressions_refusees_et_acceptees() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(filtreDAudit).build();
        ecrivainDAudit.flush();
        jdbc.update("DELETE FROM audit_log WHERE center_id IN (?, ?)", CENTRE_D, CENTRE_E);
        envoyer("/api/v1/documents/print", corps(CENTRE_D, "ORDONNANCE", "EXCEL"), qui(CENTRE_D, List.of("INFIRMIER")));
        envoyer("/api/v1/documents/print", corps(CENTRE_D, "ORDONNANCE", "EXCEL"), qui(CENTRE_D, List.of("MEDECIN")));
        envoyer("/api/v1/documents/print", corps(CENTRE_D, "SYNTHESE_FACTURATION_MENSUELLE", "EXCEL"), qui(CENTRE_D, List.of("MEDECIN")));
        envoyer("/api/v1/documents/print", corps(CENTRE_D, "LISTE_PATIENTS", "EXCEL"), qui(CENTRE_D, List.of("INFIRMIER")));
        envoyer("/api/v1/documents/print/" + idDuModele(CENTRE_D, "SYNTHESE_FACTURATION_MENSUELLE"), corps(CENTRE_D, null, "EXCEL"),
                qui(CENTRE_D, List.of("INFIRMIER")));
        ecrivainDAudit.flush();
        List<Map<String, Object>> journal = new ArrayList<>(jdbc.queryForList("SELECT action_code, entity_type, entity_id, http_method, "
                + "route_template, status_code, COUNT(*) AS nombre FROM audit_log WHERE center_id IN (?, ?) GROUP BY action_code, entity_type, "
                + "entity_id, http_method, route_template, status_code", CENTRE_D, CENTRE_E));
        journal.sort(java.util.Comparator.comparing((Map<String, Object> l) -> String.valueOf(l.get("route_template")))
                .thenComparing(l -> String.valueOf(l.get("status_code"))).thenComparing(l -> String.valueOf(l.get("action_code"))));
        List<Map<String, Object>> vues = new ArrayList<>();
        for (Map<String, Object> ligne : journal) {
            Map<String, Object> m = new LinkedHashMap<>();
            ligne.forEach((k, v) -> m.put(k, v instanceof String s ? masquer(s) : v));
            vues.add(m);
        }
        golden("journal", vues);
    }
}
