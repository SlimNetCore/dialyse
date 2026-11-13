package com.hemodialyse.backend.characterization.documents.doc4a;

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
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Filet de DOC4a (gestion des modèles de document, ADR 0164) : les cinq routes {@code /api/v1/documents/modeles} (liste, recherche,
 * création, modification, suppression). Point d'observation : HTTP (MockMvc, sécurité réelle, JDBC réel sur H2 {@code test}). Aucun mock du
 * code caractérisé. Identifiants masqués (alias), horodatages masqués. Données fictives uniquement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class GestionDesModelesCharacterizationIntegrationTest {

    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "documents", "doc4a");

    private static final UUID CENTRE_D = UUID.fromString("99998164-0000-0000-0000-00000000000d");
    private static final UUID CENTRE_E = UUID.fromString("99998164-0000-0000-0000-00000000000e");
    private static final UUID ATTESTATION_D = UUID.fromString("99998164-0000-0000-0000-000000000a01");
    private static final UUID BETA_D = UUID.fromString("99998164-0000-0000-0000-000000000a02");
    private static final UUID CHARLIE_D = UUID.fromString("99998164-0000-0000-0000-000000000a03");
    private static final UUID ATTESTATION_E = UUID.fromString("99998164-0000-0000-0000-000000000b01");
    private static final UUID NOUVEAU = UUID.fromString("99998164-0000-0000-0000-000000000c01");
    private static final UUID INCONNU = UUID.fromString("99998164-0000-0000-0000-000000000f0f");
    private static final UUID UTILISATEUR = UUID.fromString("99998164-0000-0000-0000-0000000000f1");

    private static final Pattern UUID_RE = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HORODATAGE = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}(:?\\d{2})?|Z)?");

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
        modele(ATTESTATION_D, CENTRE_D, "ATT-D", "Attestation", "ATTESTATION", "reports/attestation.jrxml", true);
        modele(BETA_D, CENTRE_D, "BETA-D", "Beta", "CUSTOM", "reports/beta.jrxml", true);
        modele(CHARLIE_D, CENTRE_D, "CHARLIE-D", "Charlie", "FICHE_PATIENT", "reports/charlie.jrxml", false);
        modele(ATTESTATION_E, CENTRE_E, "ATT-E", "Attestation E", "ATTESTATION", "reports/attestation.jrxml", true);
    }

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM modele_document_version WHERE center_id IN (?, ?)", CENTRE_D, CENTRE_E);
        jdbc.update("DELETE FROM modele_document WHERE center_id IN (?, ?)", CENTRE_D, CENTRE_E);
        jdbc.update("DELETE FROM audit_log WHERE center_id IN (?, ?)", CENTRE_D, CENTRE_E);
    }

    // ------------------------------------------------------------------ décor et identités

    private void modele(UUID id, UUID centre, String code, String libelle, String type, String chemin, boolean actif) {
        jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, "
                        + "description, active, created_at) VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                id, centre, code, libelle, type, chemin, "PDF", null, actif);
    }

    private static RequestPostProcessor qui(UUID centre, String role) {
        return user(UserPrincipal.create(UTILISATEUR.toString(), centre == null ? null : centre.toString(), "u-" + role.toLowerCase(), "",
                List.of(role), true));
    }

    private static RequestPostProcessor adminD() {
        return qui(CENTRE_D, "ADMIN");
    }

    private static RequestPostProcessor superadminD() {
        return qui(CENTRE_D, "SUPERADMIN");
    }

    // ------------------------------------------------------------------ requêtes

    private static final String RACINE = "/api/v1/documents/modeles";

    private static AbstractMockHttpServletRequestBuilder<?> liste(UUID centre) {
        var b = MockMvcRequestBuilders.get(RACINE);
        return centre == null ? b : b.param("centerId", centre.toString());
    }

    private static AbstractMockHttpServletRequestBuilder<?> json(AbstractMockHttpServletRequestBuilder<?> b, String corps) {
        return b.contentType(MediaType.APPLICATION_JSON).content(corps);
    }

    private AbstractMockHttpServletRequestBuilder<?> recherche(Map<String, Object> corps) {
        return json(MockMvcRequestBuilders.post(RACINE + "/search"), ecrire(corps));
    }

    private AbstractMockHttpServletRequestBuilder<?> creation(Map<String, Object> corps) {
        return json(MockMvcRequestBuilders.post(RACINE), ecrire(corps));
    }

    private AbstractMockHttpServletRequestBuilder<?> modification(Object id, Map<String, Object> corps) {
        return json(MockMvcRequestBuilders.put(RACINE + "/" + id), ecrire(corps));
    }

    private static AbstractMockHttpServletRequestBuilder<?> suppression(Object id, UUID centre) {
        var b = MockMvcRequestBuilders.delete(RACINE + "/" + id);
        return centre == null ? b : b.param("centerId", centre.toString());
    }

    private String ecrire(Map<String, Object> corps) {
        return mapper.writeValueAsString(corps);
    }

    /** Un corps complet de modèle ; les surcharges remplacent (ou ajoutent) des champs, une valeur {@code null} est envoyée telle quelle. */
    private static Map<String, Object> corps(UUID centre, String code, String chemin, Object... surcharges) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centerId", centre == null ? null : centre.toString());
        m.put("code", code);
        m.put("libelle", "Libelle " + code);
        m.put("typeDocument", "CUSTOM");
        m.put("cheminJrxml", chemin);
        m.put("formatImpression", "PDF");
        m.put("description", "Description");
        m.put("active", true);
        for (int i = 0; i < surcharges.length; i += 2) m.put((String) surcharges[i], surcharges[i + 1]);
        return m;
    }

    private static Map<String, Object> recherche(UUID centre, String type, Integer page, Integer taille) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centerId", centre == null ? null : centre.toString());
        if (type != null) m.put("typeDocument", type);
        if (page != null) m.put("page", page);
        if (taille != null) m.put("size", taille);
        return m;
    }

    // ------------------------------------------------------------------ observation

    private String masquer(String texte) {
        Matcher m = UUID_RE.matcher(texte);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(alias(m.group())));
        m.appendTail(sb);
        return HORODATAGE.matcher(sb.toString()).replaceAll("<HORODATAGE>");
    }

    private static String alias(String uuid) {
        if (uuid.equals(CENTRE_D.toString())) return "<CENTRE_D>";
        if (uuid.equals(CENTRE_E.toString())) return "<CENTRE_E>";
        if (uuid.equals(ATTESTATION_D.toString())) return "<ATTESTATION_D>";
        if (uuid.equals(BETA_D.toString())) return "<BETA_D>";
        if (uuid.equals(CHARLIE_D.toString())) return "<CHARLIE_D>";
        if (uuid.equals(ATTESTATION_E.toString())) return "<ATTESTATION_E>";
        if (uuid.equals(NOUVEAU.toString())) return "<NOUVEAU>";
        if (uuid.equals(INCONNU.toString())) return "<INCONNU>";
        if (uuid.equals(UTILISATEUR.toString())) return "<UTILISATEUR>";
        return "<UUID>";
    }

    private Map<String, Object> appel(AbstractMockHttpServletRequestBuilder<?> requete, RequestPostProcessor qui) throws Exception {
        if (qui != null) requete.with(qui);
        return vue(mvc.perform(requete).andReturn().getResponse());
    }

    private Map<String, Object> vue(MockHttpServletResponse r) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.getStatus());
        String type = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        m.put("contentType", type);
        for (String entete : List.of("Allow", "WWW-Authenticate")) {
            if (r.getHeader(entete) != null) m.put(entete, masquer(r.getHeader(entete)));
        }
        String corps = new String(r.getContentAsByteArray(), StandardCharsets.UTF_8);
        if (corps.isEmpty()) return m;
        if (type != null && type.contains("json")) {
            tools.jackson.databind.JsonNode arbre = mapper.readTree(masquer(corps));
            // Le message du SGBD (texte propre à H2, numéros de clé instables) n'est pas un comportement observable stable.
            if (arbre instanceof tools.jackson.databind.node.ObjectNode objet && arbre.path("code").asString("").equals("DATA_ACCESS_ERROR")) {
                objet.put("detail", "<message SGBD>");
            }
            m.put("corps", arbre);
        } else if (type != null && type.contains("xml")) {
            // Les noms de colonnes sont rendus en capitales par H2 et en minuscules par PostgreSQL (production) : normalisés en minuscules.
            m.put("corps", masquer(MAJUSCULES_XML.matcher(corps).replaceAll(t -> t.group().toLowerCase())));
        } else {
            m.put("corps", masquer(corps));
        }
        return m;
    }

    private static final Pattern MAJUSCULES_XML = Pattern.compile("</?[A-Z][A-Z_]*/?>");
    private static final Pattern CLE_EN_MAJUSCULES = Pattern.compile("\"([A-Z][A-Z_]*)\"(\\s*:)");

    /** Normalise les noms de colonnes (capitales sous H2, minuscules sous PostgreSQL en production) en minuscules. */
    private static String clesEnMinuscules(String json) {
        return CLE_EN_MAJUSCULES.matcher(json).replaceAll(r -> "\"" + r.group(1).toLowerCase() + "\"" + r.group(2));
    }

    /** Les modèles de test stockés, dans un ordre stable. */
    private List<Map<String, Object>> etat() {
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (Map<String, Object> ligne : jdbc.queryForList("SELECT id, center_id, code, libelle, type_document, chemin_jrxml, "
                + "format_impression, description, active FROM modele_document WHERE center_id IN (?, ?) ORDER BY center_id, code", CENTRE_D, CENTRE_E)) {
            Map<String, Object> m = new LinkedHashMap<>();
            ligne.forEach((k, v) -> m.put(k, v instanceof UUID u ? alias(u.toString()) : v));
            lignes.add(m);
        }
        return lignes;
    }

    private void golden(String nom, Object valeur) {
        Path dossier = GOLDENS.resolve(scenario);
        String retenu = java.nio.file.Files.exists(dossier.resolve(nom + "_nouveau_code.approved.json")) ? nom + "_nouveau_code" : nom;
        GoldenMaster.verify(dossier, retenu, clesEnMinuscules(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(valeur)));
    }

    // ------------------------------------------------------------------ scénarios

    @Test
    void la_liste_des_modeles_du_centre() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 centre D", appel(liste(CENTRE_D), adminD()));
        e.put("02 filtre ATTESTATION", appel(liste(CENTRE_D).param("typeDocument", "ATTESTATION"), adminD()));
        e.put("03 filtre en minuscules", appel(liste(CENTRE_D).param("typeDocument", "attestation"), adminD()));
        e.put("04 type inconnu", appel(liste(CENTRE_D).param("typeDocument", "INCONNU"), adminD()));
        e.put("05 type vide", appel(liste(CENTRE_D).param("typeDocument", ""), adminD()));
        e.put("06 sans centerId", appel(liste(null), adminD()));
        e.put("07 centerId illisible", appel(MockMvcRequestBuilders.get(RACINE).param("centerId", "pas-un-uuid"), adminD()));
        e.put("08 centre E demande par un admin de D", appel(liste(CENTRE_E), adminD()));
        e.put("09 superadmin de D, centre E", appel(liste(CENTRE_E), superadminD()));
        e.put("10 superadmin sans centre de session", appel(liste(CENTRE_E), qui(null, "SUPERADMIN")));
        e.put("11 admin sans centre de session", appel(liste(CENTRE_D), qui(null, "ADMIN")));
        e.put("12 centre sans modele", appel(liste(INCONNU), superadminD()));
        for (String role : List.of("MEDECIN", "INFIRMIER", "SECRETAIRE", "SOCIETE_ADMIN")) {
            e.put("profil " + role, appel(liste(CENTRE_D), qui(CENTRE_D, role)));
        }
        e.put("13 sans session", appel(liste(CENTRE_D), null));
        e.put("14 Accept XML", appel(liste(CENTRE_D).accept(MediaType.APPLICATION_XML), adminD()));
        e.put("15 Accept texte", appel(liste(CENTRE_D).accept(MediaType.TEXT_PLAIN), adminD()));
        e.put("16 verbe HEAD", appel(MockMvcRequestBuilders.head(RACINE).param("centerId", CENTRE_D.toString()), adminD()));
        golden("liste", e);
    }

    @Test
    void la_recherche_filtre_et_pagine() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 defaut (page 0 taille 10)", appel(recherche(recherche(CENTRE_D, null, 0, 10)), adminD()));
        e.put("02 filtre ATTESTATION", appel(recherche(recherche(CENTRE_D, "ATTESTATION", 0, 10)), adminD()));
        e.put("03 taille 2 page 0", appel(recherche(recherche(CENTRE_D, null, 0, 2)), adminD()));
        e.put("04 taille 2 page 1", appel(recherche(recherche(CENTRE_D, null, 1, 2)), adminD()));
        e.put("05 page au-dela de la fin", appel(recherche(recherche(CENTRE_D, null, 9, 2)), adminD()));
        e.put("06 taille 200", appel(recherche(recherche(CENTRE_D, null, 0, 200)), adminD()));
        e.put("07 taille 201", appel(recherche(recherche(CENTRE_D, null, 0, 201)), adminD()));
        e.put("08 taille 0", appel(recherche(recherche(CENTRE_D, null, 0, 0)), adminD()));
        e.put("09 taille absente", appel(recherche(recherche(CENTRE_D, null, 0, null)), adminD()));
        e.put("10 page absente", appel(recherche(recherche(CENTRE_D, null, null, 5)), adminD()));
        e.put("11 page negative", appel(recherche(recherche(CENTRE_D, null, -1, 5)), adminD()));
        e.put("12 centerId absent", appel(recherche(recherche(null, null, 0, 5)), adminD()));
        e.put("13 centre E par un admin de D", appel(recherche(recherche(CENTRE_E, null, 0, 5)), adminD()));
        e.put("14 centre E par un admin de D, taille invalide", appel(recherche(recherche(CENTRE_E, null, 0, 0)), adminD()));
        e.put("15 superadmin, centre E", appel(recherche(recherche(CENTRE_E, null, 0, 5)), superadminD()));
        e.put("16 taille illisible", appel(recherche(Map.of("centerId", CENTRE_D.toString(), "size", "abc")), adminD()));
        e.put("17 centerId illisible", appel(recherche(Map.of("centerId", "pas-un-uuid", "size", 5)), adminD()));
        e.put("18 champ inconnu ignore", appel(recherche(recherche(CENTRE_D, null, 0, 5).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new))), adminD()));
        e.put("19 profil MEDECIN", appel(recherche(recherche(CENTRE_D, null, 0, 5)), qui(CENTRE_D, "MEDECIN")));
        e.put("20 sans session", appel(recherche(recherche(CENTRE_D, null, 0, 5)), null));
        golden("recherche", e);
    }

    @Test
    void la_creation_d_un_modele() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 nominal sans identifiant", appel(creation(corps(CENTRE_D, "NOUVEAU-1", "reports/nouveau.jrxml")), adminD()));
        e.put("02 nominal avec identifiant", appel(creation(corps(CENTRE_D, "NOUVEAU-2", "reports/nouveau_2.jrxml", "id", NOUVEAU.toString())), adminD()));
        e.put("03 doublon de code dans le centre", appel(creation(corps(CENTRE_D, "NOUVEAU-1", "reports/nouveau.jrxml")), adminD()));
        e.put("04 meme code dans un autre centre", appel(creation(corps(CENTRE_E, "NOUVEAU-1", "reports/nouveau.jrxml")), superadminD()));
        e.put("05 identifiant deja pris", appel(creation(corps(CENTRE_D, "NOUVEAU-3", "reports/nouveau.jrxml", "id", ATTESTATION_D.toString())), adminD()));
        e.put("06 sans centerId (centre de session)", appel(creation(corps(null, "NOUVEAU-4", "reports/nouveau.jrxml")), adminD()));
        e.put("07 centre E par un admin de D", appel(creation(corps(CENTRE_E, "NOUVEAU-5", "reports/nouveau.jrxml")), adminD()));
        e.put("08 etat apres", etat());
        Map<String, Object> chemins = new LinkedHashMap<>();
        int n = 0;
        for (String chemin : new String[]{"../../etc/passwd", "reports/../secret.jrxml", "reports/a/b.jrxml", "reports/a.jrxml.exe", "reports/.jrxml",
                "reports/é.jrxml", "/etc/reports/a.jrxml", "reports/a b.jrxml", "REPORTS/a.jrxml", "reports/A-b_9.jrxml", "reports/a.JRXML", "",
                "   ", "reports/a.jrxml\n", "classpath:reports/a.jrxml"}) {
            chemins.put(String.format("%02d '%s'", ++n, chemin.replace("\n", "\\n")),
                    appel(creation(corps(CENTRE_D, "CH-" + n, chemin)), adminD()));
        }
        chemins.put("16 chemin absent", appel(creation(corps(CENTRE_D, "CH-16", null)), adminD()));
        chemins.put("17 etat apres (seuls les chemins valides)", etat());
        e.put("09 chemins de gabarit", chemins);
        golden("creation", e);
    }

    @Test
    void les_champs_obligatoires_et_les_bornes_a_la_creation() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 code absent", appel(creation(corps(CENTRE_D, null, "reports/x.jrxml")), adminD()));
        e.put("02 libelle absent", appel(creation(corps(CENTRE_D, "L-2", "reports/x.jrxml", "libelle", null)), adminD()));
        e.put("03 type absent", appel(creation(corps(CENTRE_D, "L-3", "reports/x.jrxml", "typeDocument", null)), adminD()));
        e.put("04 format absent", appel(creation(corps(CENTRE_D, "L-4", "reports/x.jrxml", "formatImpression", null)), adminD()));
        e.put("05 description absente", appel(creation(corps(CENTRE_D, "L-5", "reports/x.jrxml", "description", null)), adminD()));
        e.put("06 active absent", appel(creation(corps(CENTRE_D, "L-6", "reports/x.jrxml", "active", null)), adminD()));
        e.put("07 code de 50 caracteres", appel(creation(corps(CENTRE_D, "C".repeat(50), "reports/x.jrxml")), adminD()));
        e.put("08 code de 51 caracteres", appel(creation(corps(CENTRE_D, "C".repeat(51), "reports/x.jrxml")), adminD()));
        e.put("09 libelle de 256 caracteres", appel(creation(corps(CENTRE_D, "L-9", "reports/x.jrxml", "libelle", "l".repeat(256))), adminD()));
        e.put("10 type de 51 caracteres", appel(creation(corps(CENTRE_D, "L-10", "reports/x.jrxml", "typeDocument", "T".repeat(51))), adminD()));
        e.put("11 format de 21 caracteres", appel(creation(corps(CENTRE_D, "L-11", "reports/x.jrxml", "formatImpression", "F".repeat(21))), adminD()));
        e.put("12 description de 501 caracteres", appel(creation(corps(CENTRE_D, "L-12", "reports/x.jrxml", "description", "d".repeat(501))), adminD()));
        e.put("13 format libre", appel(creation(corps(CENTRE_D, "L-13", "reports/x.jrxml", "formatImpression", "WORD")), adminD()));
        e.put("14 type libre en minuscules", appel(creation(corps(CENTRE_D, "L-14", "reports/x.jrxml", "typeDocument", "ordonnance")), adminD()));
        e.put("15 code vide", appel(creation(corps(CENTRE_D, "", "reports/x.jrxml")), adminD()));
        e.put("16 code blanc", appel(creation(corps(CENTRE_D, "   ", "reports/x.jrxml")), adminD()));
        e.put("17 active en texte", appel(creation(corps(CENTRE_D, "L-17", "reports/x.jrxml", "active", "oui")), adminD()));
        e.put("18 identifiant illisible", appel(creation(corps(CENTRE_D, "L-18", "reports/x.jrxml", "id", "pas-un-uuid")), adminD()));
        e.put("19 centerId illisible", appel(creation(corps(CENTRE_D, "L-19", "reports/x.jrxml", "centerId", "pas-un-uuid")), adminD()));
        e.put("20 etat apres", etat());
        golden("champs_obligatoires", e);
    }

    @Test
    void la_modification_d_un_modele() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 nominal", appel(modification(BETA_D, corps(CENTRE_D, "BETA-2", "reports/beta2.jrxml", "libelle", "Beta bis", "active", false)), adminD()));
        e.put("02 etat apres", etat());
        e.put("03 doublon de code dans le centre", appel(modification(BETA_D, corps(CENTRE_D, "ATT-D", "reports/beta2.jrxml")), adminD()));
        e.put("04 modele inconnu (aucune ligne touchee)", appel(modification(INCONNU, corps(CENTRE_D, "X-4", "reports/x.jrxml")), adminD()));
        e.put("05 modele d'un autre centre, centre D", appel(modification(ATTESTATION_E, corps(CENTRE_D, "X-5", "reports/x.jrxml")), adminD()));
        e.put("06 centre E par un admin de D", appel(modification(ATTESTATION_E, corps(CENTRE_E, "X-6", "reports/x.jrxml")), adminD()));
        e.put("07 superadmin, centre E", appel(modification(ATTESTATION_E, corps(CENTRE_E, "ATT-E2", "reports/e2.jrxml")), superadminD()));
        e.put("08 sans centerId (centre de session)", appel(modification(CHARLIE_D, corps(null, "CHARLIE-2", "reports/charlie2.jrxml", "active", true)), adminD()));
        e.put("09 chemin invalide", appel(modification(BETA_D, corps(CENTRE_D, "BETA-3", "../etc/passwd")), adminD()));
        e.put("10 chemin absent", appel(modification(BETA_D, corps(CENTRE_D, "BETA-3", null)), adminD()));
        e.put("11 code absent", appel(modification(BETA_D, corps(CENTRE_D, null, "reports/x.jrxml")), adminD()));
        e.put("12 identifiant de chemin illisible", appel(MockMvcRequestBuilders.put(RACINE + "/pas-un-uuid")
                .contentType(MediaType.APPLICATION_JSON).content(ecrire(corps(CENTRE_D, "X-12", "reports/x.jrxml"))), adminD()));
        e.put("13 l'identifiant du corps est ignore", appel(modification(BETA_D, corps(CENTRE_D, "BETA-4", "reports/beta4.jrxml", "id", ATTESTATION_D.toString())), adminD()));
        e.put("14 etat final", etat());
        golden("modification", e);
    }

    @Test
    void la_suppression_d_un_modele() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 modele de D par D", appel(suppression(BETA_D, CENTRE_D), adminD()));
        e.put("02 deja supprime", appel(suppression(BETA_D, CENTRE_D), adminD()));
        e.put("03 modele inconnu", appel(suppression(INCONNU, CENTRE_D), adminD()));
        e.put("04 modele de E avec centerId D (rien ne bouge)", appel(suppression(ATTESTATION_E, CENTRE_D), adminD()));
        e.put("05 centre E par un admin de D", appel(suppression(ATTESTATION_E, CENTRE_E), adminD()));
        e.put("06 sans centerId", appel(suppression(CHARLIE_D, null), adminD()));
        e.put("07 centerId illisible", appel(MockMvcRequestBuilders.delete(RACINE + "/" + CHARLIE_D).param("centerId", "pas-un-uuid"), adminD()));
        e.put("08 identifiant illisible", appel(MockMvcRequestBuilders.delete(RACINE + "/pas-un-uuid").param("centerId", CENTRE_D.toString()), adminD()));
        e.put("09 etat apres", etat());
        e.put("10 superadmin supprime le modele de E", appel(suppression(ATTESTATION_E, CENTRE_E), superadminD()));
        e.put("11 etat final", etat());
        golden("suppression", e);
    }

    @Test
    void un_modele_supprime_ou_desactive_n_est_plus_imprimable() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        Map<String, Object> impression = new LinkedHashMap<>();
        impression.put("centerId", CENTRE_D.toString());
        impression.put("typeDocument", "ATTESTATION");
        e.put("01 suppression de l'attestation", appel(suppression(ATTESTATION_D, CENTRE_D), adminD()));
        e.put("02 impression par type apres suppression", appel(json(MockMvcRequestBuilders.post("/api/v1/documents/print"), ecrire(impression)), adminD()));
        Map<String, Object> parModele = new LinkedHashMap<>();
        parModele.put("centerId", CENTRE_D.toString());
        e.put("03 impression par identifiant apres suppression", appel(json(MockMvcRequestBuilders.post("/api/v1/documents/print/" + ATTESTATION_D),
                ecrire(parModele)), adminD()));
        Map<String, Object> pourCharlie = new LinkedHashMap<>();
        pourCharlie.put("centerId", CENTRE_D.toString());
        pourCharlie.put("typeDocument", "FICHE_PATIENT");
        e.put("04 impression par type d'un modele desactive", appel(json(MockMvcRequestBuilders.post("/api/v1/documents/print"), ecrire(pourCharlie)), adminD()));
        golden("impression_apres_gestion", e);
    }

    @Test
    void les_profils_non_administrateurs_et_l_absence_de_session() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        for (String role : List.of("MEDECIN", "INFIRMIER", "SECRETAIRE", "SOCIETE_ADMIN")) {
            RequestPostProcessor qui = qui(CENTRE_D, role);
            e.put("profil " + role, List.of(
                    appel(creation(corps(CENTRE_D, "P-" + role, "reports/p.jrxml")), qui),
                    appel(modification(BETA_D, corps(CENTRE_D, "P-" + role, "reports/p.jrxml")), qui),
                    appel(suppression(BETA_D, CENTRE_D), qui)));
        }
        e.put("sans session", List.of(
                appel(creation(corps(CENTRE_D, "P-S", "reports/p.jrxml")), null),
                appel(modification(BETA_D, corps(CENTRE_D, "P-S", "reports/p.jrxml")), null),
                appel(suppression(BETA_D, CENTRE_D), null)));
        e.put("etat stocke", etat());
        golden("profils_et_session", e);
    }

    @Test
    void les_formes_illisibles_de_la_requete() throws Exception {
        String valide = ecrire(corps(CENTRE_D, "F-1", "reports/f.jrxml"));
        RequestPostProcessor medecin = qui(CENTRE_D, "MEDECIN");
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 creation, JSON mal forme", appel(json(MockMvcRequestBuilders.post(RACINE), "{pas du json"), adminD()));
        e.put("02 creation, sans corps", appel(MockMvcRequestBuilders.post(RACINE).contentType(MediaType.APPLICATION_JSON), adminD()));
        e.put("03 creation, corps texte", appel(MockMvcRequestBuilders.post(RACINE).contentType(MediaType.TEXT_PLAIN).content(valide), adminD()));
        e.put("04 creation, sans Content-Type", appel(MockMvcRequestBuilders.post(RACINE).content(valide), adminD()));
        e.put("05 creation, corps XML", appel(MockMvcRequestBuilders.post(RACINE).contentType(MediaType.APPLICATION_XML).content("<a/>"), adminD()));
        e.put("06 creation, tableau JSON", appel(json(MockMvcRequestBuilders.post(RACINE), "[]"), adminD()));
        e.put("07 creation, JSON null", appel(json(MockMvcRequestBuilders.post(RACINE), "null"), adminD()));
        e.put("08 creation, objet vide", appel(json(MockMvcRequestBuilders.post(RACINE), "{}"), adminD()));
        e.put("09 creation, Accept XML", appel(json(MockMvcRequestBuilders.post(RACINE), ecrire(corps(CENTRE_D, "F-9", "reports/f.jrxml")))
                .accept(MediaType.APPLICATION_XML), adminD()));
        e.put("10 modification, JSON mal forme", appel(json(MockMvcRequestBuilders.put(RACINE + "/" + BETA_D), "{pas du json"), adminD()));
        e.put("11 modification, sans corps", appel(MockMvcRequestBuilders.put(RACINE + "/" + BETA_D).contentType(MediaType.APPLICATION_JSON), adminD()));
        e.put("12 modification, corps texte", appel(MockMvcRequestBuilders.put(RACINE + "/" + BETA_D).contentType(MediaType.TEXT_PLAIN).content(valide), adminD()));
        e.put("13 recherche, JSON mal forme", appel(json(MockMvcRequestBuilders.post(RACINE + "/search"), "{pas du json"), adminD()));
        e.put("14 recherche, sans corps", appel(MockMvcRequestBuilders.post(RACINE + "/search").contentType(MediaType.APPLICATION_JSON), adminD()));
        e.put("15 recherche, corps texte", appel(MockMvcRequestBuilders.post(RACINE + "/search").contentType(MediaType.TEXT_PLAIN).content("x"), adminD()));
        e.put("16 PATCH sur la collection", appel(json(MockMvcRequestBuilders.patch(RACINE), valide), adminD()));
        e.put("17 PUT sur la collection", appel(json(MockMvcRequestBuilders.put(RACINE), valide), adminD()));
        e.put("18 DELETE sur la collection", appel(MockMvcRequestBuilders.delete(RACINE).param("centerId", CENTRE_D.toString()), adminD()));
        e.put("19 POST sur un modele", appel(json(MockMvcRequestBuilders.post(RACINE + "/" + BETA_D), valide), adminD()));
        e.put("20 GET sur un modele", appel(MockMvcRequestBuilders.get(RACINE + "/" + BETA_D).param("centerId", CENTRE_D.toString()), adminD()));
        e.put("21 PATCH sur un modele", appel(json(MockMvcRequestBuilders.patch(RACINE + "/" + BETA_D), valide), adminD()));
        e.put("22 GET sur la recherche", appel(MockMvcRequestBuilders.get(RACINE + "/search").param("centerId", CENTRE_D.toString()), adminD()));
        e.put("23 PUT sur la recherche", appel(json(MockMvcRequestBuilders.put(RACINE + "/search"), valide), adminD()));
        e.put("24 medecin, creation en JSON mal forme", appel(json(MockMvcRequestBuilders.post(RACINE), "{pas du json"), medecin));
        e.put("25 medecin, creation en corps texte", appel(MockMvcRequestBuilders.post(RACINE).contentType(MediaType.TEXT_PLAIN).content(valide), medecin));
        e.put("26 medecin, modification en JSON mal forme", appel(json(MockMvcRequestBuilders.put(RACINE + "/" + BETA_D), "{pas du json"), medecin));
        e.put("27 medecin, modification avec identifiant illisible", appel(MockMvcRequestBuilders.put(RACINE + "/pas-un-uuid")
                .contentType(MediaType.APPLICATION_JSON).content(valide), medecin));
        e.put("28 medecin, suppression sans centerId", appel(suppression(BETA_D, null), medecin));
        e.put("29 medecin, suppression avec centerId illisible", appel(MockMvcRequestBuilders.delete(RACINE + "/" + BETA_D).param("centerId", "pas-un-uuid"), medecin));
        e.put("30 sans session, creation en JSON mal forme", appel(json(MockMvcRequestBuilders.post(RACINE), "{pas du json"), null));
        e.put("31 admin d'un autre centre, creation en corps texte", appel(MockMvcRequestBuilders.post(RACINE).contentType(MediaType.TEXT_PLAIN).content(valide),
                qui(CENTRE_E, "ADMIN")));
        e.put("32 etat stocke (rien d'ecrit)", etat());
        golden("formes_illisibles", e);
    }

    @Test
    void l_audit_des_ecritures_et_des_lectures_des_modeles() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(filtreDAudit).build();
        ecrivainDAudit.flush();
        jdbc.update("DELETE FROM audit_log WHERE center_id IN (?, ?)", CENTRE_D, CENTRE_E);
        mvc.perform(creation(corps(CENTRE_D, "AUD-1", "reports/a.jrxml", "id", NOUVEAU.toString())).with(adminD()));
        mvc.perform(creation(corps(CENTRE_D, "AUD-1", "reports/a.jrxml")).with(adminD()));
        mvc.perform(creation(corps(CENTRE_D, "AUD-2", "../a")).with(adminD()));
        mvc.perform(modification(NOUVEAU, corps(CENTRE_D, "AUD-1B", "reports/b.jrxml")).with(adminD()));
        mvc.perform(modification(NOUVEAU, corps(CENTRE_D, "AUD-1B", "reports/b.jrxml")).with(qui(CENTRE_D, "MEDECIN")));
        mvc.perform(suppression(NOUVEAU, CENTRE_D).with(adminD()));
        mvc.perform(suppression(INCONNU, CENTRE_D).with(adminD()));
        mvc.perform(liste(CENTRE_D).with(adminD()));
        mvc.perform(recherche(recherche(CENTRE_D, null, 0, 5)).with(adminD()));
        ecrivainDAudit.flush();
        List<Map<String, Object>> journal = new ArrayList<>(jdbc.queryForList("SELECT action_code, entity_type, entity_id, http_method, "
                + "route_template, status_code, COUNT(*) AS nombre FROM audit_log WHERE center_id IN (?, ?) GROUP BY action_code, entity_type, "
                + "entity_id, http_method, route_template, status_code", CENTRE_D, CENTRE_E));
        journal.sort(java.util.Comparator.comparing((Map<String, Object> l) -> String.valueOf(l.get("route_template")))
                .thenComparing(l -> String.valueOf(l.get("http_method")))
                .thenComparing(l -> String.valueOf(l.get("status_code")))
                .thenComparing(l -> String.valueOf(l.get("entity_id"))));
        List<Map<String, Object>> vues = new ArrayList<>();
        for (Map<String, Object> ligne : journal) {
            Map<String, Object> m = new LinkedHashMap<>();
            ligne.forEach((k, v) -> m.put(k, v instanceof String s ? masquer(s) : v));
            vues.add(m);
        }
        golden("journal", vues);
    }
}
