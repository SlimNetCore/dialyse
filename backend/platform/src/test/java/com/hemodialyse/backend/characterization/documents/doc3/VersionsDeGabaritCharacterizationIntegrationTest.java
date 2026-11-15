package com.hemodialyse.backend.characterization.documents.doc3;

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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
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
 * Filet de DOC3 (versions de gabarit par centre, ADR 0163) : les cinq routes {@code /api/v1/documents/modeles/{modeleId}/...}
 * (historique, source, téléversement, activation, réinitialisation) et la suppression des versions avec le modèle. Point
 * d'observation : HTTP (MockMvc, sécurité réelle, JDBC réel sur H2 {@code test}). Aucun mock du code caractérisé.
 * <p>
 * Normalisation : identifiants et horodatages masqués ; le contenu d'un gabarit téléchargé est résumé (présence de la racine
 * {@code jasperReport}, marge haute) et jamais recopié. Données fictives uniquement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class VersionsDeGabaritCharacterizationIntegrationTest {

    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "documents", "doc3");

    private static final UUID CENTRE_D = UUID.fromString("99998160-0000-0000-0000-00000000000d");
    private static final UUID CENTRE_E = UUID.fromString("99998160-0000-0000-0000-00000000000e");
    private static final UUID ATTESTATION_D = UUID.fromString("99998160-0000-0000-0000-000000000a01");
    private static final UUID PERSONNALISE_D = UUID.fromString("99998160-0000-0000-0000-000000000a02");
    private static final UUID CHEMIN_DOUTEUX_D = UUID.fromString("99998160-0000-0000-0000-000000000a03");
    private static final UUID CODE_ETRANGE_D = UUID.fromString("99998160-0000-0000-0000-000000000a04");
    private static final UUID ATTESTATION_E = UUID.fromString("99998160-0000-0000-0000-000000000b01");
    private static final UUID INCONNU = UUID.fromString("99998160-0000-0000-0000-000000000f0f");
    private static final UUID UTILISATEUR = UUID.fromString("99998160-0000-0000-0000-0000000000f1");

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
        modele(ATTESTATION_D, CENTRE_D, "ATT-D", "ATTESTATION", "reports/attestation.jrxml");
        modele(PERSONNALISE_D, CENTRE_D, "PERSO-D", "CUSTOM", "reports/inconnu.jrxml");
        modele(CHEMIN_DOUTEUX_D, CENTRE_D, "CHEMIN-D", "CUSTOM", "../../etc/passwd");
        modele(CODE_ETRANGE_D, CENTRE_D, "Rapport été/2026 à", "ATTESTATION", "reports/attestation.jrxml");
        modele(ATTESTATION_E, CENTRE_E, "ATT-E", "ATTESTATION", "reports/attestation.jrxml");
    }

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM modele_document_version WHERE center_id IN (?, ?)", CENTRE_D, CENTRE_E);
        jdbc.update("DELETE FROM modele_document WHERE center_id IN (?, ?)", CENTRE_D, CENTRE_E);
        jdbc.update("DELETE FROM audit_log WHERE center_id IN (?, ?)", CENTRE_D, CENTRE_E);
    }

    // ------------------------------------------------------------------ décor et identités

    private void modele(UUID id, UUID centre, String code, String type, String chemin) {
        jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, "
                        + "description, active, created_at) VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                id, centre, code, "Modèle fictif", type, chemin, "PDF", null, true);
    }

    private static RequestPostProcessor qui(UUID centre, String role) {
        return user(UserPrincipal.create(UTILISATEUR.toString(), centre == null ? null : centre.toString(), "u-" + role.toLowerCase(), "",
                List.of(role), true));
    }

    private static RequestPostProcessor adminD() {
        return qui(CENTRE_D, "ADMIN");
    }

    private static RequestPostProcessor adminE() {
        return qui(CENTRE_E, "ADMIN");
    }

    private static RequestPostProcessor superadminD() {
        return qui(CENTRE_D, "SUPERADMIN");
    }

    // ------------------------------------------------------------------ requêtes

    private static String racine(UUID modele) {
        return "/api/v1/documents/modeles/" + modele;
    }

    private static AbstractMockHttpServletRequestBuilder<?>avecCentre(AbstractMockHttpServletRequestBuilder<?>b, UUID centre) {
        return centre == null ? b : b.param("centerId", centre.toString());
    }

    private AbstractMockHttpServletRequestBuilder<?>versions(UUID modele, UUID centre) {
        return avecCentre(MockMvcRequestBuilders.get(racine(modele) + "/versions"), centre);
    }

    private AbstractMockHttpServletRequestBuilder<?>source(UUID modele, UUID centre) {
        return avecCentre(MockMvcRequestBuilders.get(racine(modele) + "/source"), centre);
    }

    private AbstractMockHttpServletRequestBuilder<?>activer(UUID modele, UUID centre, Object version) {
        return avecCentre(MockMvcRequestBuilders.post(racine(modele) + "/versions/" + version + "/activate"), centre);
    }

    private AbstractMockHttpServletRequestBuilder<?>reinitialiser(UUID modele, UUID centre) {
        return avecCentre(MockMvcRequestBuilders.post(racine(modele) + "/reset"), centre);
    }

    private AbstractMockHttpServletRequestBuilder<?>televerser(UUID modele, UUID centre, String nom, byte[] octets, String commentaire) {
        AbstractMockHttpServletRequestBuilder<?>b = MockMvcRequestBuilders.multipart(racine(modele) + "/versions")
                .file(new MockMultipartFile("file", nom, "application/xml", octets));
        if (commentaire != null) b.param("commentaire", commentaire);
        return avecCentre(b, centre);
    }

    private AbstractMockHttpServletRequestBuilder<?>televerserTexte(UUID modele, UUID centre, String contenu) {
        return televerser(modele, centre, "modele.jrxml", contenu.getBytes(StandardCharsets.UTF_8), null);
    }

    private static String original() throws Exception {
        try (var in = new ClassPathResource("reports/attestation.jrxml").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String marge(int valeur) throws Exception {
        return original().replace("topMargin=\"40\"", "topMargin=\"" + valeur + "\"");
    }

    // ------------------------------------------------------------------ observation

    private String masquer(String texte) {
        Matcher m = UUID_RE.matcher(texte);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(alias(m.group())));
        m.appendTail(sb);
        String sansDates = HORODATAGE.matcher(sb.toString()).replaceAll("<HORODATAGE>");
        // empreinte et taille dépendent du fichier livré (gabarit d'origine) : vérifiées à part par empreinteConforme()
        return TAILLE.matcher(EMPREINTE.matcher(sansDates).replaceAll("<SHA256>")).replaceAll("\"tailleOctets\":\"<TAILLE>\"");
    }

    private static final Pattern EMPREINTE = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern TAILLE = Pattern.compile("\"tailleOctets\"\\s*:\\s*\\d+");

    /** L'empreinte SHA-256 et la taille stockées sont celles des octets téléversés. */
    private boolean empreinteConforme(UUID modele, int version, byte[] octets) throws Exception {
        Map<String, Object> ligne = jdbc.queryForMap("SELECT sha256, taille_octets FROM modele_document_version WHERE modele_id = ? AND version = ?",
                modele, version);
        String attendue = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(octets));
        return attendue.equals(ligne.get("sha256")) && ((Number) ligne.get("taille_octets")).intValue() == octets.length;
    }

    private static String alias(String uuid) {
        if (uuid.equals(CENTRE_D.toString())) return "<CENTRE_D>";
        if (uuid.equals(CENTRE_E.toString())) return "<CENTRE_E>";
        if (uuid.equals(ATTESTATION_D.toString())) return "<ATTESTATION_D>";
        if (uuid.equals(PERSONNALISE_D.toString())) return "<PERSONNALISE_D>";
        if (uuid.equals(CHEMIN_DOUTEUX_D.toString())) return "<CHEMIN_DOUTEUX_D>";
        if (uuid.equals(CODE_ETRANGE_D.toString())) return "<CODE_ETRANGE_D>";
        if (uuid.equals(ATTESTATION_E.toString())) return "<ATTESTATION_E>";
        if (uuid.equals(INCONNU.toString())) return "<INCONNU>";
        if (uuid.equals(UTILISATEUR.toString())) return "<UTILISATEUR>";
        return "<UUID>";
    }

    /** Statut, type, en-têtes utiles et corps (JSON analysé, XML résumé, texte masqué) d'une réponse. */
    private Map<String, Object> appel(AbstractMockHttpServletRequestBuilder<?>requete, RequestPostProcessor qui) throws Exception {
        if (qui != null) requete.with(qui);
        return vue(mvc.perform(requete).andReturn().getResponse());
    }

    private Map<String, Object> vue(MockHttpServletResponse r) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.getStatus());
        String type = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        m.put("contentType", type);
        for (String entete : List.of("Content-Disposition", "X-Content-Type-Options", "Allow", "WWW-Authenticate")) {
            if (r.getHeader(entete) != null) m.put(entete, masquer(r.getHeader(entete)));
        }
        String corps = new String(r.getContentAsByteArray(), StandardCharsets.UTF_8);
        if (corps.isEmpty()) return m;
        if (type != null && type.contains("json")) {
            m.put("corps", mapper.readTree(masquer(corps)));
        } else if (type != null && type.contains("xml")) {
            Matcher marge = Pattern.compile("topMargin=\"(\\d+)\"").matcher(corps);
            Map<String, Object> resume = new LinkedHashMap<>();
            resume.put("racine jasperReport", corps.contains("<jasperReport"));
            resume.put("marge haute", marge.find() ? marge.group(1) : null);
            m.put("gabarit", resume);
        } else {
            m.put("corps", masquer(corps));
        }
        return m;
    }

    /** Les versions stockées de tous les modèles de test, dans un ordre stable. */
    private List<Map<String, Object>> etat() {
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (Map<String, Object> ligne : jdbc.queryForList("SELECT modele_id, center_id, version, actif, taille_octets, sha256, "
                + "commentaire, uploaded_by FROM modele_document_version WHERE center_id IN (?, ?) ORDER BY center_id, modele_id, version",
                CENTRE_D, CENTRE_E)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("modele", alias(String.valueOf(ligne.get("modele_id"))));
            m.put("centre", alias(String.valueOf(ligne.get("center_id"))));
            m.put("version", ligne.get("version"));
            m.put("actif", ligne.get("actif"));
            m.put("tailleOctets", "<TAILLE>");
            m.put("sha256", "<SHA256>");
            m.put("commentaire", ligne.get("commentaire"));
            m.put("deposePar", ligne.get("uploaded_by"));
            lignes.add(m);
        }
        return lignes;
    }

    private void golden(String nom, Object valeur) {
        Path dossier = GOLDENS.resolve(scenario);
        String retenu = java.nio.file.Files.exists(dossier.resolve(nom + "_nouveau_code.approved.json")) ? nom + "_nouveau_code" : nom;
        GoldenMaster.verify(dossier, retenu, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(valeur));
    }

    // ------------------------------------------------------------------ scénarios

    @Test
    void le_cycle_complet_televerser_telecharger_activer_reinitialiser() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 source du gabarit d'origine", appel(source(ATTESTATION_D, CENTRE_D), adminD()));
        e.put("02 historique vide", appel(versions(ATTESTATION_D, CENTRE_D), adminD()));
        e.put("03 version 1 (commentaire)", appel(televerser(ATTESTATION_D, CENTRE_D, "attestation.jrxml",
                marge(36).getBytes(StandardCharsets.UTF_8), "Marge élargie"), adminD()));
        e.put("03b empreinte et taille stockees = celles des octets envoyes", empreinteConforme(ATTESTATION_D, 1, marge(36).getBytes(StandardCharsets.UTF_8)));
        e.put("04 source = version active", appel(source(ATTESTATION_D, CENTRE_D), adminD()));
        e.put("05 version 2", appel(televerserTexte(ATTESTATION_D, CENTRE_D, marge(34)), adminD()));
        e.put("06 activation de la version 1", appel(activer(ATTESTATION_D, CENTRE_D, 1), adminD()));
        e.put("07 historique (2 versions, la 1 active)", appel(versions(ATTESTATION_D, CENTRE_D), adminD()));
        e.put("08 source de la version 2 (inactive)", appel(source(ATTESTATION_D, CENTRE_D).param("version", "2"), adminD()));
        e.put("09 reinitialisation", appel(reinitialiser(ATTESTATION_D, CENTRE_D), adminD()));
        e.put("10 source = gabarit d'origine", appel(source(ATTESTATION_D, CENTRE_D), adminD()));
        e.put("11 historique conserve, aucune active", appel(versions(ATTESTATION_D, CENTRE_D), adminD()));
        e.put("12 version suivante apres reinitialisation", appel(televerserTexte(ATTESTATION_D, CENTRE_D, marge(32)), adminD()));
        e.put("13 etat stocke", etat());
        golden("cycle", e);
    }

    @Test
    void l_historique_est_pagine_et_ses_bornes_sont_corrigees() throws Exception {
        for (int v = 0; v < 3; v++) {
            mvc.perform(televerserTexte(ATTESTATION_D, CENTRE_D, marge(30 + v)).with(adminD()));
        }
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 defaut", appel(versions(ATTESTATION_D, CENTRE_D), adminD()));
        e.put("02 taille 2 page 0", appel(versions(ATTESTATION_D, CENTRE_D).param("size", "2"), adminD()));
        e.put("03 taille 2 page 1", appel(versions(ATTESTATION_D, CENTRE_D).param("size", "2").param("page", "1"), adminD()));
        e.put("04 page au-dela de la fin", appel(versions(ATTESTATION_D, CENTRE_D).param("page", "9"), adminD()));
        e.put("05 taille 0 (ramenee a 1)", appel(versions(ATTESTATION_D, CENTRE_D).param("size", "0"), adminD()));
        e.put("06 taille negative (ramenee a 1)", appel(versions(ATTESTATION_D, CENTRE_D).param("size", "-5"), adminD()));
        e.put("07 taille 1000 (ramenee a 100)", appel(versions(ATTESTATION_D, CENTRE_D).param("size", "1000"), adminD()));
        e.put("08 page negative (ramenee a 0)", appel(versions(ATTESTATION_D, CENTRE_D).param("page", "-3"), adminD()));
        e.put("09 taille illisible", appel(versions(ATTESTATION_D, CENTRE_D).param("size", "abc"), adminD()));
        e.put("10 page illisible", appel(versions(ATTESTATION_D, CENTRE_D).param("page", "1.5"), adminD()));
        e.put("11 taille vide", appel(versions(ATTESTATION_D, CENTRE_D).param("size", ""), adminD()));
        e.put("12 modele sans version", appel(versions(PERSONNALISE_D, CENTRE_D), adminD()));
        golden("pagination", e);
    }

    @Test
    void la_source_selon_la_version_l_origine_et_le_modele() throws Exception {
        mvc.perform(televerserTexte(ATTESTATION_D, CENTRE_D, marge(36)).with(adminD()));
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 version active", appel(source(ATTESTATION_D, CENTRE_D), adminD()));
        e.put("02 origine forcee", appel(source(ATTESTATION_D, CENTRE_D).param("origine", "true"), adminD()));
        e.put("03 origine=false", appel(source(ATTESTATION_D, CENTRE_D).param("origine", "false"), adminD()));
        e.put("04 version 1", appel(source(ATTESTATION_D, CENTRE_D).param("version", "1"), adminD()));
        e.put("05 version 1 et origine (la version l'emporte)", appel(source(ATTESTATION_D, CENTRE_D).param("version", "1").param("origine", "true"), adminD()));
        e.put("06 version inexistante", appel(source(ATTESTATION_D, CENTRE_D).param("version", "7"), adminD()));
        e.put("07 version 0", appel(source(ATTESTATION_D, CENTRE_D).param("version", "0"), adminD()));
        e.put("08 version illisible", appel(source(ATTESTATION_D, CENTRE_D).param("version", "abc"), adminD()));
        e.put("09 origine illisible", appel(source(ATTESTATION_D, CENTRE_D).param("origine", "peut-etre"), adminD()));
        e.put("10 modele inconnu", appel(source(INCONNU, CENTRE_D), adminD()));
        e.put("11 modele sans gabarit livre", appel(source(PERSONNALISE_D, CENTRE_D), adminD()));
        e.put("12 chemin de gabarit douteux", appel(source(CHEMIN_DOUTEUX_D, CENTRE_D), adminD()));
        e.put("13 nom de fichier d'un code etrange", appel(source(CODE_ETRANGE_D, CENTRE_D), adminD()));
        e.put("14 nom de fichier d'une version", appel(source(CODE_ETRANGE_D, CENTRE_D).param("version", "1"), adminD()));
        e.put("15 sans centerId", appel(source(ATTESTATION_D, null), adminD()));
        golden("source", e);
    }

    @Test
    void les_refus_de_contenu_du_televersement_sont_des_422_avec_leurs_anomalies() throws Exception {
        // DOC5h (ADR 0200) : l'attestation livrée n'a plus de requête ; le fichier déposé en porte une (golden inchangé : QUERY_MODIFIED).
        String requete = original().replace("<field name=\"NOM\"",
                "<queryString><![CDATA[SELECT username, password_hash FROM app_user]]></queryString><field name=\"NOM\"");
        String expression = original().replaceFirst("(?s)<textFieldExpression>.*?</textFieldExpression>",
                "<textFieldExpression><![CDATA[Runtime.getRuntime().exec(\"calc\")]]></textFieldExpression>");
        String taille = original() + " ".repeat(com.hemodialyse.backend.contexts.documents.publishedlanguage.JrxmlSecurityValidator.MAX_BYTES);
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 extension", appel(televerser(ATTESTATION_D, CENTRE_D, "x.exe", new byte[]{1}, null), adminD()));
        e.put("02 sans nom de fichier", appel(televerser(ATTESTATION_D, CENTRE_D, "", marge(36).getBytes(StandardCharsets.UTF_8), null), adminD()));
        e.put("03 fichier vide", appel(televerser(ATTESTATION_D, CENTRE_D, "x.jrxml", new byte[0], null), adminD()));
        e.put("04 modele sans gabarit livre", appel(televerserTexte(PERSONNALISE_D, CENTRE_D, original()), adminD()));
        e.put("05 chemin de gabarit douteux", appel(televerserTexte(CHEMIN_DOUTEUX_D, CENTRE_D, original()), adminD()));
        e.put("06 encodage non UTF-8", appel(televerser(ATTESTATION_D, CENTRE_D, "x.jrxml", new byte[]{'<', (byte) 0xE9, (byte) 0xFF, '>'}, null), adminD()));
        e.put("07 requete SQL modifiee", appel(televerserTexte(ATTESTATION_D, CENTRE_D, requete), adminD()));
        e.put("08 expression de code", appel(televerserTexte(ATTESTATION_D, CENTRE_D, expression), adminD()));
        e.put("09 pas du XML", appel(televerserTexte(ATTESTATION_D, CENTRE_D, "ceci n'est pas du XML"), adminD()));
        e.put("10 XML d'une autre racine", appel(televerserTexte(ATTESTATION_D, CENTRE_D, "<a><b/></a>"), adminD()));
        e.put("11 DOCTYPE et entite externe", appel(televerserTexte(ATTESTATION_D, CENTRE_D,
                "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>"), adminD()));
        e.put("12 dimension de page illisible", appel(televerserTexte(ATTESTATION_D, CENTRE_D,
                original().replace("topMargin=\"40\"", "topMargin=\"beaucoup\"")), adminD()));
        e.put("13 trop gros (512 Ko + 1 octet ou plus)", appel(televerserTexte(ATTESTATION_D, CENTRE_D, taille), adminD()));
        e.put("14 casse de l'extension acceptee", appel(televerser(ATTESTATION_D, CENTRE_D, "X.JRXML", marge(31).getBytes(StandardCharsets.UTF_8), null), adminD()));
        e.put("15 nom avec chemin accepte", appel(televerser(ATTESTATION_D, CENTRE_D, "a/b/c.jrxml", marge(32).getBytes(StandardCharsets.UTF_8), null), adminD()));
        e.put("16 etat stocke (seules les versions acceptees)", etat());
        golden("refus_de_contenu", e);
    }

    @Test
    void la_borne_de_512_ko_est_inclusive() throws Exception {
        int borne = com.hemodialyse.backend.contexts.documents.publishedlanguage.JrxmlSecurityValidator.MAX_BYTES;
        byte[] base = original().getBytes(StandardCharsets.UTF_8);
        byte[] exact = new byte[borne];
        System.arraycopy(base, 0, exact, 0, base.length);
        java.util.Arrays.fill(exact, base.length, borne, (byte) ' ');
        byte[] depasse = new byte[borne + 1];
        System.arraycopy(exact, 0, depasse, 0, borne);
        depasse[borne] = ' ';
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 exactement 512 Ko", appel(televerser(ATTESTATION_D, CENTRE_D, "x.jrxml", exact, null), adminD()));
        e.put("02 512 Ko + 1 octet", appel(televerser(ATTESTATION_D, CENTRE_D, "x.jrxml", depasse, null), adminD()));
        e.put("03 etat stocke", etat());
        golden("borne", e);
    }

    @Test
    void les_formes_illisibles_de_la_requete_de_televersement() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 corps JSON au lieu de multipart", appel(MockMvcRequestBuilders.post(racine(ATTESTATION_D) + "/versions")
                .param("centerId", CENTRE_D.toString()).contentType(MediaType.APPLICATION_JSON).content("{}"), adminD()));
        e.put("02 sans corps", appel(MockMvcRequestBuilders.post(racine(ATTESTATION_D) + "/versions")
                .param("centerId", CENTRE_D.toString()), adminD()));
        e.put("03 multipart sans la partie file", appel(MockMvcRequestBuilders.multipart(racine(ATTESTATION_D) + "/versions")
                .param("centerId", CENTRE_D.toString()), adminD()));
        e.put("04 partie nommee autrement", appel(MockMvcRequestBuilders.multipart(racine(ATTESTATION_D) + "/versions")
                .file(new MockMultipartFile("fichier", "x.jrxml", "application/xml", marge(36).getBytes(StandardCharsets.UTF_8)))
                .param("centerId", CENTRE_D.toString()), adminD()));
        e.put("05 centerId illisible", appel(MockMvcRequestBuilders.multipart(racine(ATTESTATION_D) + "/versions")
                .file(new MockMultipartFile("file", "x.jrxml", "application/xml", marge(36).getBytes(StandardCharsets.UTF_8)))
                .param("centerId", "pas-un-uuid"), adminD()));
        e.put("06 identifiant de modele illisible", appel(MockMvcRequestBuilders.multipart("/api/v1/documents/modeles/pas-un-uuid/versions")
                .file(new MockMultipartFile("file", "x.jrxml", "application/xml", marge(36).getBytes(StandardCharsets.UTF_8)))
                .param("centerId", CENTRE_D.toString()), adminD()));
        e.put("07 etat stocke", etat());
        golden("formes_illisibles_du_televersement", e);
    }

    @Test
    void le_commentaire_est_nettoye_et_borne_a_500_caracteres() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 espaces autour", appel(televerser(ATTESTATION_D, CENTRE_D, "x.jrxml", marge(31).getBytes(StandardCharsets.UTF_8), "   Marge   "), adminD()));
        e.put("02 blanc", appel(televerser(ATTESTATION_D, CENTRE_D, "x.jrxml", marge(32).getBytes(StandardCharsets.UTF_8), "    "), adminD()));
        e.put("03 absent", appel(televerser(ATTESTATION_D, CENTRE_D, "x.jrxml", marge(33).getBytes(StandardCharsets.UTF_8), null), adminD()));
        String long600 = "é".repeat(600);
        Map<String, Object> tronque = appel(televerser(ATTESTATION_D, CENTRE_D, "x.jrxml", marge(34).getBytes(StandardCharsets.UTF_8), long600), adminD());
        e.put("04 600 caracteres: statut", tronque.get("statut"));
        e.put("05 etat stocke (longueur du commentaire)", etat().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("version", l.get("version"));
            m.put("longueur", l.get("commentaire") == null ? null : String.valueOf(l.get("commentaire")).length());
            m.put("commentaire", String.valueOf(l.get("commentaire")).length() > 50 ? "<long>" : l.get("commentaire"));
            return m;
        }).toList());
        golden("commentaire", e);
    }

    @Test
    void l_activation_d_une_version() throws Exception {
        mvc.perform(televerserTexte(ATTESTATION_D, CENTRE_D, marge(31)).with(adminD()));
        mvc.perform(televerserTexte(ATTESTATION_D, CENTRE_D, marge(32)).with(adminD()));
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 version 1", appel(activer(ATTESTATION_D, CENTRE_D, 1), adminD()));
        e.put("02 etat (une seule active)", etat());
        e.put("03 deja active (idempotente)", appel(activer(ATTESTATION_D, CENTRE_D, 1), adminD()));
        e.put("04 version inexistante", appel(activer(ATTESTATION_D, CENTRE_D, 9), adminD()));
        e.put("05 version 0", appel(activer(ATTESTATION_D, CENTRE_D, 0), adminD()));
        e.put("06 version negative", appel(activer(ATTESTATION_D, CENTRE_D, -1), adminD()));
        e.put("07 version illisible", appel(activer(ATTESTATION_D, CENTRE_D, "abc"), adminD()));
        e.put("08 version hors des entiers", appel(activer(ATTESTATION_D, CENTRE_D, "99999999999"), adminD()));
        e.put("09 modele inconnu", appel(activer(INCONNU, CENTRE_D, 1), adminD()));
        e.put("10 apres reinitialisation", appel(reinitialiser(ATTESTATION_D, CENTRE_D), adminD()));
        e.put("11 reactivation apres reinitialisation", appel(activer(ATTESTATION_D, CENTRE_D, 2), adminD()));
        e.put("12 etat final", etat());
        golden("activation", e);
    }

    @Test
    void la_reinitialisation_conserve_l_historique() throws Exception {
        mvc.perform(televerserTexte(ATTESTATION_D, CENTRE_D, marge(31)).with(adminD()));
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 reinitialisation", appel(reinitialiser(ATTESTATION_D, CENTRE_D), adminD()));
        e.put("02 etat (version conservee, inactive)", etat());
        e.put("03 reinitialisation repetee", appel(reinitialiser(ATTESTATION_D, CENTRE_D), adminD()));
        e.put("04 modele sans version", appel(reinitialiser(PERSONNALISE_D, CENTRE_D), adminD()));
        e.put("05 modele sans gabarit livre mais connu", appel(reinitialiser(CHEMIN_DOUTEUX_D, CENTRE_D), adminD()));
        e.put("06 modele inconnu", appel(reinitialiser(INCONNU, CENTRE_D), adminD()));
        golden("reinitialisation", e);
    }

    @Test
    void chaque_centre_ne_voit_que_ses_modeles() throws Exception {
        mvc.perform(televerserTexte(ATTESTATION_E, CENTRE_E, marge(31)).with(adminE()));
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 admin D, modele de E, centre D (introuvable)", List.of(
                appel(versions(ATTESTATION_E, CENTRE_D), adminD()),
                appel(source(ATTESTATION_E, CENTRE_D), adminD()),
                appel(televerserTexte(ATTESTATION_E, CENTRE_D, marge(35)), adminD()),
                appel(activer(ATTESTATION_E, CENTRE_D, 1), adminD()),
                appel(reinitialiser(ATTESTATION_E, CENTRE_D), adminD())));
        e.put("02 admin D, modele de E, sans centerId (introuvable)", List.of(
                appel(versions(ATTESTATION_E, null), adminD()),
                appel(source(ATTESTATION_E, null), adminD()),
                appel(televerserTexte(ATTESTATION_E, null, marge(35)), adminD()),
                appel(activer(ATTESTATION_E, null, 1), adminD()),
                appel(reinitialiser(ATTESTATION_E, null), adminD())));
        e.put("03 admin D, centre E demande (403)", List.of(
                appel(versions(ATTESTATION_E, CENTRE_E), adminD()),
                appel(source(ATTESTATION_E, CENTRE_E), adminD()),
                appel(televerserTexte(ATTESTATION_E, CENTRE_E, marge(35)), adminD()),
                appel(activer(ATTESTATION_E, CENTRE_E, 1), adminD()),
                appel(reinitialiser(ATTESTATION_E, CENTRE_E), adminD())));
        e.put("04 superadmin D, centre E demande (transverse)", List.of(
                appel(versions(ATTESTATION_E, CENTRE_E), superadminD()),
                appel(source(ATTESTATION_E, CENTRE_E), superadminD()),
                appel(televerserTexte(ATTESTATION_E, CENTRE_E, marge(35)), superadminD()),
                appel(activer(ATTESTATION_E, CENTRE_E, 1), superadminD()),
                appel(reinitialiser(ATTESTATION_E, CENTRE_E), superadminD())));
        e.put("05 superadmin D, modele de E, centre D (introuvable)", appel(versions(ATTESTATION_E, CENTRE_D), superadminD()));
        e.put("06 superadmin sans centre de session ni centerId", appel(versions(ATTESTATION_D, null), qui(null, "SUPERADMIN")));
        e.put("07 admin sans centre de session", appel(versions(ATTESTATION_D, null), qui(null, "ADMIN")));
        e.put("08 etat stocke", etat());
        golden("isolation_par_centre", e);
    }

    @Test
    void les_profils_non_administrateurs_et_l_absence_de_session() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        for (String role : List.of("MEDECIN", "INFIRMIER", "SECRETAIRE", "SOCIETE_ADMIN")) {
            RequestPostProcessor qui = qui(CENTRE_D, role);
            e.put("profil " + role, List.of(
                    appel(versions(ATTESTATION_D, CENTRE_D), qui),
                    appel(source(ATTESTATION_D, CENTRE_D), qui),
                    appel(televerserTexte(ATTESTATION_D, CENTRE_D, marge(36)), qui),
                    appel(activer(ATTESTATION_D, CENTRE_D, 1), qui),
                    appel(reinitialiser(ATTESTATION_D, CENTRE_D), qui)));
        }
        e.put("sans session", List.of(
                appel(versions(ATTESTATION_D, CENTRE_D), null),
                appel(source(ATTESTATION_D, CENTRE_D), null),
                appel(televerserTexte(ATTESTATION_D, CENTRE_D, marge(36)), null),
                appel(activer(ATTESTATION_D, CENTRE_D, 1), null),
                appel(reinitialiser(ATTESTATION_D, CENTRE_D), null)));
        e.put("etat stocke", etat());
        golden("profils_et_session", e);
    }

    @Test
    void l_autorisation_precede_la_lecture_de_la_requete() throws Exception {
        RequestPostProcessor medecin = qui(CENTRE_D, "MEDECIN");
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 medecin, identifiant de modele illisible", appel(MockMvcRequestBuilders.get("/api/v1/documents/modeles/pas-un-uuid/versions"), medecin));
        e.put("02 medecin, taille illisible", appel(versions(ATTESTATION_D, CENTRE_D).param("size", "abc"), medecin));
        e.put("03 medecin, version illisible", appel(activer(ATTESTATION_D, CENTRE_D, "abc"), medecin));
        e.put("04 medecin, corps JSON au lieu de multipart", appel(MockMvcRequestBuilders.post(racine(ATTESTATION_D) + "/versions")
                .contentType(MediaType.APPLICATION_JSON).content("{}"), medecin));
        e.put("05 medecin, sans la partie file", appel(MockMvcRequestBuilders.multipart(racine(ATTESTATION_D) + "/versions"), medecin));
        e.put("06 medecin, centerId illisible", appel(source(ATTESTATION_D, null).param("centerId", "pas-un-uuid"), medecin));
        e.put("07 sans session, version illisible", appel(activer(ATTESTATION_D, CENTRE_D, "abc"), null));
        e.put("08 admin d'un autre centre, version illisible", appel(activer(ATTESTATION_D, CENTRE_D, "abc"), adminE()));
        e.put("09 admin d'un autre centre, sans la partie file", appel(MockMvcRequestBuilders.multipart(racine(ATTESTATION_D) + "/versions")
                .param("centerId", CENTRE_D.toString()), adminE()));
        golden("autorisation_avant_lecture", e);
    }

    @Test
    void les_methodes_et_les_types_non_pris_en_charge() throws Exception {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 DELETE sur l'historique", appel(MockMvcRequestBuilders.delete(racine(ATTESTATION_D) + "/versions"), adminD()));
        e.put("02 PUT sur l'historique", appel(MockMvcRequestBuilders.put(racine(ATTESTATION_D) + "/versions"), adminD()));
        e.put("03 GET sur l'activation", appel(MockMvcRequestBuilders.get(racine(ATTESTATION_D) + "/versions/1/activate"), adminD()));
        e.put("04 GET sur la reinitialisation", appel(MockMvcRequestBuilders.get(racine(ATTESTATION_D) + "/reset"), adminD()));
        e.put("05 POST sur la source", appel(MockMvcRequestBuilders.post(racine(ATTESTATION_D) + "/source"), adminD()));
        e.put("06 DELETE sur la source", appel(MockMvcRequestBuilders.delete(racine(ATTESTATION_D) + "/source"), adminD()));
        e.put("07 source en JSON demandé", appel(source(ATTESTATION_D, CENTRE_D).accept(MediaType.APPLICATION_JSON), adminD()));
        e.put("08 historique en XML demandé", appel(versions(ATTESTATION_D, CENTRE_D).accept(MediaType.APPLICATION_XML), adminD()));
        e.put("09 source acceptant tout", appel(source(ATTESTATION_D, CENTRE_D).accept(MediaType.ALL), adminD()));
        e.put("10 DELETE sur l'historique, medecin", appel(MockMvcRequestBuilders.delete(racine(ATTESTATION_D) + "/versions"), qui(CENTRE_D, "MEDECIN")));
        e.put("11 GET sur la reinitialisation, sans session", appel(MockMvcRequestBuilders.get(racine(ATTESTATION_D) + "/reset"), null));
        e.put("12 chemin inconnu sous le modele", appel(MockMvcRequestBuilders.get(racine(ATTESTATION_D) + "/inconnu"), adminD()));
        golden("methodes_et_types", e);
    }

    @Test
    void la_suppression_d_un_modele_supprime_ses_versions() throws Exception {
        mvc.perform(televerserTexte(ATTESTATION_D, CENTRE_D, marge(31)).with(adminD()));
        mvc.perform(televerserTexte(ATTESTATION_E, CENTRE_E, marge(31)).with(adminE()));
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("01 admin D supprime le modele de E avec centerId D", appel(
                MockMvcRequestBuilders.delete("/api/v1/documents/modeles/" + ATTESTATION_E).param("centerId", CENTRE_D.toString()), adminD()));
        e.put("02 etat apres (rien ne bouge)", etat());
        e.put("03 modeles restants", jdbc.queryForList("SELECT code FROM modele_document WHERE center_id IN (?, ?) ORDER BY code",
                CENTRE_D, CENTRE_E).stream().map(l -> l.get("code")).toList());
        e.put("04 admin D supprime son modele", appel(
                MockMvcRequestBuilders.delete("/api/v1/documents/modeles/" + ATTESTATION_D).param("centerId", CENTRE_D.toString()), adminD()));
        e.put("05 etat apres (versions de D retirees)", etat());
        e.put("06 modeles restants", jdbc.queryForList("SELECT code FROM modele_document WHERE center_id IN (?, ?) ORDER BY code",
                CENTRE_D, CENTRE_E).stream().map(l -> l.get("code")).toList());
        golden("suppression_du_modele", e);
    }

    @Test
    void l_audit_des_ecritures_et_des_lectures_du_gabarit() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(filtreDAudit).build();
        ecrivainDAudit.flush();
        jdbc.update("DELETE FROM audit_log WHERE center_id IN (?, ?)", CENTRE_D, CENTRE_E);
        mvc.perform(televerserTexte(ATTESTATION_D, CENTRE_D, marge(31)).with(adminD()));
        mvc.perform(televerserTexte(ATTESTATION_D, CENTRE_D, "pas du XML").with(adminD()));
        mvc.perform(televerserTexte(ATTESTATION_D, CENTRE_D, marge(32)).with(adminD()));
        mvc.perform(activer(ATTESTATION_D, CENTRE_D, 1).with(adminD()));
        mvc.perform(activer(ATTESTATION_D, CENTRE_D, 9).with(adminD()));
        mvc.perform(reinitialiser(ATTESTATION_D, CENTRE_D).with(adminD()));
        mvc.perform(reinitialiser(INCONNU, CENTRE_D).with(adminD()));
        mvc.perform(source(ATTESTATION_D, CENTRE_D).with(adminD()));
        mvc.perform(versions(ATTESTATION_D, CENTRE_D).with(adminD()));
        mvc.perform(versions(ATTESTATION_D, CENTRE_D).with(qui(CENTRE_D, "MEDECIN")));
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
