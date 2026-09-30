package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reprise des données : lot par centre, vérification puis import des assurés, patients et affectations,
 * rejeu sans doublon, correspondances de valeurs, cloisonnement par centre, droits et annulation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MigrationIntegrationTest {

    private static final UUID CENTRE_A = UUID.fromString("99996000-0000-0000-0000-0000000000a1");
    private static final UUID CENTRE_B = UUID.fromString("99996000-0000-0000-0000-0000000000b1");
    private static final String BASE = "/api/v1/admin/migration";
    private static final String PATIENTS_CSV_HEADER = "Identifiant d'origine;N° d'assurance;Nom;Prénom;Sexe;Date de naissance;"
            + "Date d'admission;État;Qualité;N° d'assurance de l'assuré;Salle;Jours de dialyse\n";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    private MockMvc mockMvc;

    private static UserPrincipal principal(String role, UUID centerId) {
        return UserPrincipal.create(UUID.randomUUID().toString(), centerId.toString(), "zt-mig-" + role, "", List.of(role), true);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return user(principal("ADMIN", CENTRE_A));
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "import.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'S1', 'Salle 1')", UUID.randomUUID(), CENTRE_A);
    }

    @AfterEach
    void cleanup() {
        for (UUID center : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM assure_patient WHERE center_id = ?", center);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", center);
            jdbc.update("DELETE FROM assure WHERE center_id = ?", center);
            jdbc.update("DELETE FROM salle WHERE center_id = ?", center);
            for (String table : List.of("migration_entity_run", "migration_id_map", "migration_value_map", "migration_batch")) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", center);
            }
        }
    }

    @Test
    void fullMigrationFlow() throws Exception {
        String batchId = openBatch();

        // Assurés.
        mockMvc.perform(multipart(BASE + "/batches/{id}/import/assures", batchId)
                        .file(csv("legacy_id;numero_assurance;nom;prenom\nA1;ZT900;BENALI;Ahmed\n"))
                        .param("dryRun", "false").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.created").value(1));

        // Patients : vérification d'abord (valeur inconnue « D »), correspondance, puis import.
        String patients = PATIENTS_CSV_HEADER
                + "P1;ZT111;BENALI;Karim;Homme;15/03/1962;02/01/2019;Actif;Fils;ZT900;S1;Lun, Mer, Ven\n"
                + "P2;ZT222;KACI;Nadia;F;1970-05-20;2020-06-01;D;Assuré;;;Mar Jeu Sam\n";
        mockMvc.perform(multipart(BASE + "/batches/{id}/import/patients", batchId).file(csv(patients)).with(admin()))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0].row").value(3))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_VALUE"));

        mockMvc.perform(put(BASE + "/value-mappings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"column\":\"etatPatient\",\"source\":\"D\",\"target\":\"DECEDE\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/value-mappings").with(admin()))
                .andExpect(jsonPath("$[0].target").value("DECEDE"));

        mockMvc.perform(multipart(BASE + "/batches/{id}/import/patients", batchId).file(csv(patients)).with(admin()))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.applied").value(false));
        assertThat(count("patients")).isZero();

        mockMvc.perform(multipart(BASE + "/batches/{id}/import/patients", batchId).file(csv(patients))
                        .param("dryRun", "false").with(admin()))
                .andExpect(jsonPath("$.applied").value(true));
        assertThat(count("patients")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT assure_numero_assurance FROM patients WHERE center_id = ? AND numero_assurance = 'ZT111'",
                String.class, CENTRE_A)).isEqualTo("ZT900");
        assertThat(jdbc.queryForObject("SELECT etat_patient FROM patients WHERE center_id = ? AND numero_assurance = 'ZT222'",
                String.class, CENTRE_A)).isEqualTo("DECEDE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE center_id = ? AND salle_id IS NOT NULL AND jour_lundi = TRUE",
                Long.class, CENTRE_A)).isEqualTo(1);

        // Rejeu : mise à jour, aucun doublon.
        mockMvc.perform(multipart(BASE + "/batches/{id}/import/patients", batchId).file(csv(patients))
                        .param("dryRun", "false").with(admin()))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(2));
        assertThat(count("patients")).isEqualTo(2);

        // Affectations (historique assuré ↔ patient).
        mockMvc.perform(multipart(BASE + "/batches/{id}/import/affectations", batchId)
                        .file(csv("Patient;N° d'assurance de l'assuré;Date de début;Principale\nP1;ZT900;02/01/2019;oui\nP9;ZT900;;non\n"))
                        .param("dryRun", "false").with(admin()))
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("PATIENT_NOT_MIGRATED"));

        // Historique du lot.
        mockMvc.perform(get(BASE + "/batches/{id}", batchId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.status").value("EN_COURS"))
                .andExpect(jsonPath("$.runs[*].entity").value(hasItem("patients")));

        // Annulation : les données créées disparaissent.
        mockMvc.perform(post(BASE + "/batches/{id}/cancel", batchId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANNULE"));
        assertThat(count("patients")).isZero();
        assertThat(count("assure")).isZero();
    }

    @Test
    void oneOpenBatchPerCenterAndCenterIsolation() throws Exception {
        String batchId = openBatch();
        mockMvc.perform(post(BASE + "/batches").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\":\"Deuxième lot\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MIGRATION_BATCH_ALREADY_OPEN"));

        mockMvc.perform(get(BASE + "/batches").param("centerId", CENTRE_B.toString()).with(admin()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/batches/{id}", batchId).with(user(principal("ADMIN", CENTRE_B))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MIGRATION_BATCH_NOT_FOUND"));
        mockMvc.perform(get(BASE + "/batches").with(user(principal("INFIRMIER", CENTRE_A))))
                .andExpect(status().isForbidden());
    }

    @Test
    void closedBatchIsFrozen() throws Exception {
        String batchId = openBatch();
        mockMvc.perform(post(BASE + "/batches/{id}/close", batchId).with(admin()))
                .andExpect(jsonPath("$.status").value("TERMINE"));

        mockMvc.perform(multipart(BASE + "/batches/{id}/import/assures", batchId)
                        .file(csv("legacy_id;numero_assurance;nom\nA1;ZT900;BENALI\n")).with(admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MIGRATION_BATCH_CLOSED"));
    }

    @Test
    void describesEntitiesAndServesTemplates() throws Exception {
        mockMvc.perform(get(BASE + "/entities").with(admin()))
                .andExpect(jsonPath("$[0].slug").value("assures"))
                .andExpect(jsonPath("$[1].slug").value("patients"))
                .andExpect(jsonPath("$[2].slug").value("affectations"));
        mockMvc.perform(get(BASE + "/entities/patients/template").with(admin()))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/entities/patients/template").param("format", "csv").with(admin()))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("Identifiant d'origine;Code patient;N° d'assurance"));
    }

    private String openBatch() throws Exception {
        String body = mockMvc.perform(post(BASE + "/batches").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\":\"Reprise AncienLogiciel\",\"sourceSystem\":\"AncienLogiciel\",\"dateDebutReprise\":\"2022-01-01\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private long count(String table) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id = ?", Long.class, CENTRE_A);
        return n == null ? 0 : n;
    }
}

