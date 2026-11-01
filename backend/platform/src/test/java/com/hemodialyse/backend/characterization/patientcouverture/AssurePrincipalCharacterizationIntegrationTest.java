package com.hemodialyse.backend.characterization.patientcouverture;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AssurePrincipalCharacterizationIntegrationTest {

    protected static final String BASE = "/api/v1/patients";
    protected static final UUID CENTRE_A = UUID.fromString("99995200-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99995200-0000-0000-0000-00000000000b");
    protected static final UUID ADMIN_A = UUID.fromString("99995200-0000-0000-0000-0000000000f1");
    protected static final UUID ADMIN_B = UUID.fromString("99995200-0000-0000-0000-0000000000f2");
    protected static final UUID ASSURE_A = UUID.fromString("99995200-0000-0000-0000-000000000a01");
    protected static final UUID ASSURE_B = UUID.fromString("99995200-0000-0000-0000-000000000a02");
    protected static final UUID PATIENT_A = UUID.fromString("99995200-0000-0000-0000-000000000101");
    protected static final UUID PATIENT_B = UUID.fromString("99995200-0000-0000-0000-000000000102");
    private static final java.nio.file.Path GOLDENS = java.nio.file.Path.of("src", "test", "resources", "characterization", "patientcouverture", "assure");
    private static final java.util.regex.Pattern UUID_RE =
            java.util.regex.Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private final ObjectMapper mapper = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;
    protected MockMvc mvc;

    protected static RequestPostProcessor admin(UUID centre, UUID userId) {
        return user(UserPrincipal.create(userId.toString(), centre.toString(), "admin", "", List.of("ADMIN"), true));
    }

    protected static RequestPostProcessor adminA() {
        return admin(CENTRE_A, ADMIN_A);
    }

    protected static RequestPostProcessor adminB() {
        return admin(CENTRE_B, ADMIN_B);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        jdbc.update("INSERT INTO assure (numero_assurance, center_id, nom, prenom, sexe, created_at) VALUES (?,?,?,?,?,?)",
                "AS-A", CENTRE_A, "Dupont", "Jean", "M", OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.update("INSERT INTO assure (numero_assurance, center_id, nom, prenom, sexe, created_at) VALUES (?,?,?,?,?,?)",
                "AS-B", CENTRE_B, "Martin", "Paul", "M", OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient, etat_patient, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                PATIENT_A, CENTRE_A, "P-A", "Patient", "A", "F", LocalDate.of(2026, 1, 10), "ASS-P-A", "NON_VACANCIER", "PERMANENT", OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient, etat_patient, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                PATIENT_B, CENTRE_B, "P-B", "Patient", "B", "F", LocalDate.of(2026, 1, 10), "ASS-P-B", "NON_VACANCIER", "PERMANENT", OffsetDateTime.now(ZoneOffset.UTC));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM assure_patient WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM assure WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM patients WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
    }

    protected Resp send(MockHttpServletRequestBuilder req) throws Exception {
        MockHttpServletResponse r = mvc.perform(req).andReturn().getResponse();
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    protected void golden(String name, Resp r) {
        String raw = r.raw;
        java.util.Map<String, String> aliases = new java.util.LinkedHashMap<>();
        aliases.put(CENTRE_A.toString(), "<CENTRE_A>");
        aliases.put(CENTRE_B.toString(), "<CENTRE_B>");
        aliases.put(PATIENT_A.toString(), "<PATIENT_A>");
        aliases.put(PATIENT_B.toString(), "<PATIENT_B>");
        aliases.put(ASSURE_A.toString(), "<ASSURE_A>");
        aliases.put(ASSURE_B.toString(), "<ASSURE_B>");
        int[] unknown = {0};
        java.util.regex.Matcher m = UUID_RE.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String u = m.group();
            String a = aliases.computeIfAbsent(u, k -> "<UUID_GENERE_" + (++unknown[0]) + ">");
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(a));
        }
        m.appendTail(sb);
        GoldenMaster.verify(GOLDENS, name, sb.toString());
    }

    @Test
    void listAssures_isole_par_centre() throws Exception {
        Resp a = send(get(BASE + "/assures").param("centerId", CENTRE_A.toString()).with(adminA()));
        assertEquals(200, a.status);
        golden("list_assures_centre_A", a);
        Resp b = send(get(BASE + "/assures").param("centerId", CENTRE_B.toString()).with(adminB()));
        assertEquals(200, b.status);
        golden("list_assures_centre_B", b);
    }

    @Test
    void affecter_assure_principal() throws Exception {
        String body = "{}";
        Resp r = send(post(BASE + "/" + PATIENT_A + "/assures/AS-A/affecter").param("centerId", CENTRE_A.toString()).with(adminA()));
        assertEquals(200, r.status);
        golden("affecter_assure", r);
        Resp hist = send(get(BASE + "/" + PATIENT_A + "/assures/history").param("centerId", CENTRE_A.toString()).with(adminA()));
        assertEquals(200, hist.status);
    }

    @Test
    void update_assure() throws Exception {
        String json = "{\"nom\":\"Dupont2\",\"prenom\":\"Jean2\",\"sexe\":\"M\",\"telPersonnel\":null,\"adresse\":\"rue\",\"groupeSanguin\":null}";
        Resp r = send(put(BASE + "/assures/AS-A").param("centerId", CENTRE_A.toString()).contentType(MediaType.APPLICATION_JSON).content(json).with(adminA()));
        assertEquals(200, r.status);
        golden("update_assure", r);
    }

    @Test
    void update_assignment() throws Exception {
        // affect first
        send(post(BASE + "/" + PATIENT_A + "/assures/AS-A/affecter").param("centerId", CENTRE_A.toString()).with(adminA()));
        // find assignment id via history
        Resp hist = send(get(BASE + "/" + PATIENT_A + "/assures/history").param("centerId", CENTRE_A.toString()).with(adminA()));
        String assignmentId = mapper.readTree(hist.raw).get(0).get("id").asString();
        String json = "{\"dateDebutAffectation\":\"2026-02-01\",\"dateFinAffectation\":null}";
        Resp r = send(put(BASE + "/" + PATIENT_A + "/assures/assignments/" + assignmentId).param("centerId", CENTRE_A.toString()).contentType(MediaType.APPLICATION_JSON).content(json).with(adminA()));
        assertEquals(200, r.status);
        golden("update_assignment", r);
    }

    @Test
    void cross_centre_refuse() throws Exception {
        Resp r = send(get(BASE + "/assures").param("centerId", CENTRE_A.toString()).with(adminB()));
        golden("list_assures_cross_centre_adminB", r);
    }

    protected record Resp(int status, String contentType, String raw) {
    }
}
