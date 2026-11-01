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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PecCharacterizationIntegrationTest {

    protected static final String BASE = "/api/v1/pec";
    protected static final UUID CENTRE_A = UUID.fromString("99995300-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99995300-0000-0000-0000-00000000000b");
    protected static final UUID ADMIN_A = UUID.fromString("99995300-0000-0000-0000-0000000000f1");
    protected static final UUID ADMIN_B = UUID.fromString("99995300-0000-0000-0000-0000000000f2");
    protected static final UUID PATIENT_A = UUID.fromString("99995300-0000-0000-0000-000000000101");
    protected static final UUID PATIENT_B = UUID.fromString("99995300-0000-0000-0000-000000000102");
    private static final java.nio.file.Path GOLDENS = java.nio.file.Path.of("src", "test", "resources", "characterization", "patientcouverture", "pec");
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
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient, etat_patient, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                PATIENT_A, CENTRE_A, "P-A", "Patient", "A", "F", LocalDate.of(2026, 1, 10), "ASS-P-A", "VACANCIER", "PERMANENT", OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient, etat_patient, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                PATIENT_B, CENTRE_B, "P-B", "Patient", "B", "F", LocalDate.of(2026, 1, 10), "ASS-P-B", "VACANCIER", "PERMANENT", OffsetDateTime.now(ZoneOffset.UTC));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM prise_en_charge WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM patients WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
    }

    protected Resp send(MockHttpServletRequestBuilder req) throws Exception {
        MockHttpServletResponse r = mvc.perform(req).andReturn().getResponse();
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    protected void golden(String name, Resp r) {
        String raw = r.raw;
        // Normalize non-deterministic timestamps
        raw = raw.replaceAll("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z?", "<TIMESTAMP>");
        java.util.Map<String, String> aliases = new java.util.LinkedHashMap<>();
        aliases.put(CENTRE_A.toString(), "<CENTRE_A>");
        aliases.put(CENTRE_B.toString(), "<CENTRE_B>");
        aliases.put(PATIENT_A.toString(), "<PATIENT_A>");
        aliases.put(PATIENT_B.toString(), "<PATIENT_B>");
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
    void create_list_isole_par_centre() throws Exception {
        String bodyA = "{\"patientId\":\"" + PATIENT_A + "\",\"centerId\":\"" + CENTRE_A + "\",\"userId\":\"" + ADMIN_A + "\",\"dateDebutDemande\":\"2026-01-01\",\"dateFinDemande\":\"2026-12-31\",\"forfaitDemandeId\":null}";
        Resp a = send(post(BASE).contentType(MediaType.APPLICATION_JSON).content(bodyA).with(adminA()));
        assertEquals(200, a.status);
        golden("create_pec_centre_A", a);
        String bodyB = "{\"patientId\":\"" + PATIENT_B + "\",\"centerId\":\"" + CENTRE_B + "\",\"userId\":\"" + ADMIN_B + "\",\"dateDebutDemande\":\"2026-01-01\",\"dateFinDemande\":\"2026-12-31\",\"forfaitDemandeId\":null}";
        Resp b = send(post(BASE).contentType(MediaType.APPLICATION_JSON).content(bodyB).with(adminB()));
        assertEquals(200, b.status);
        golden("create_pec_centre_B", b);

        Resp la = send(get(BASE).param("centerId", CENTRE_A.toString()).with(adminA()));
        assertEquals(200, la.status);
        golden("list_pec_centre_A", la);
        Resp lb = send(get(BASE).param("centerId", CENTRE_B.toString()).with(adminB()));
        assertEquals(200, lb.status);
        golden("list_pec_centre_B", lb);
    }

    @Test
    void list_by_patient() throws Exception {
        String bodyA = "{\"patientId\":\"" + PATIENT_A + "\",\"centerId\":\"" + CENTRE_A + "\",\"userId\":\"" + ADMIN_A + "\",\"dateDebutDemande\":\"2026-01-01\",\"dateFinDemande\":\"2026-12-31\",\"forfaitDemandeId\":null}";
        send(post(BASE).contentType(MediaType.APPLICATION_JSON).content(bodyA).with(adminA()));
        Resp r = send(get(BASE + "/patient/" + PATIENT_A).param("centerId", CENTRE_A.toString()).with(adminA()));
        assertEquals(200, r.status);
        golden("list_pec_by_patient", r);
    }

    @Test
    void cycle_valider_cloturer() throws Exception {
        String bodyA = "{\"patientId\":\"" + PATIENT_A + "\",\"centerId\":\"" + CENTRE_A + "\",\"userId\":\"" + ADMIN_A + "\",\"dateDebutDemande\":\"2026-01-01\",\"dateFinDemande\":\"2026-12-31\",\"forfaitDemandeId\":null}";
        Resp created = send(post(BASE).contentType(MediaType.APPLICATION_JSON).content(bodyA).with(adminA()));
        assertEquals(200, created.status);
        String pecId = mapper.readTree(created.raw).get("id").asString();
        // validate
        String vbody = "{\"centerId\":\"" + CENTRE_A + "\",\"userId\":\"" + ADMIN_A + "\",\"dateDebutEffectif\":\"2026-01-01\",\"dateFinEffectif\":\"2026-12-31\",\"forfaitEffectifId\":null}";
        Resp v = send(post(BASE + "/" + pecId + "/validate").contentType(MediaType.APPLICATION_JSON).content(vbody).with(adminA()));
        assertEquals(200, v.status);
        golden("validate_pec", v);
        // session-allowed
        Resp sa = send(get(BASE + "/" + pecId + "/session-allowed").param("centerId", CENTRE_A.toString()).param("userId", ADMIN_A.toString()).with(adminA()));
        assertEquals(200, sa.status);
        golden("session_allowed", sa);
        // close
        String cbody = "{\"centerId\":\"" + CENTRE_A + "\"}";
        Resp c = send(post(BASE + "/" + pecId + "/close").contentType(MediaType.APPLICATION_JSON).content(cbody).with(adminA()));
        assertEquals(200, c.status);
        golden("close_pec", c);
    }

    @Test
    void delete_pec() throws Exception {
        String bodyA = "{\"patientId\":\"" + PATIENT_A + "\",\"centerId\":\"" + CENTRE_A + "\",\"userId\":\"" + ADMIN_A + "\",\"dateDebutDemande\":\"2026-01-01\",\"dateFinDemande\":\"2026-12-31\",\"forfaitDemandeId\":null}";
        Resp created = send(post(BASE).contentType(MediaType.APPLICATION_JSON).content(bodyA).with(adminA()));
        String pecId = mapper.readTree(created.raw).get("id").asString();
        Resp d = send(delete(BASE + "/" + pecId).param("centerId", CENTRE_A.toString()).param("patientId", PATIENT_A.toString()).with(adminA()));
        assertEquals(200, d.status);
        golden("delete_pec", d);
    }

    @Test
    void cross_centre_refuse() throws Exception {
        Resp r = send(get(BASE).param("centerId", CENTRE_A.toString()).with(adminB()));
        golden("list_pec_cross_centre_adminB", r);
    }

    protected record Resp(int status, String contentType, String raw) {
    }
}
