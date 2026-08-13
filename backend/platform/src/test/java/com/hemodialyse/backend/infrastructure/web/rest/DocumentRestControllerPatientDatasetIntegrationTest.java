package com.hemodialyse.backend.infrastructure.web.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class DocumentRestControllerPatientDatasetIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("99990000-0000-0000-0000-000000000001");
    private static final UUID PATIENT_ID = UUID.fromString("99990000-0000-0000-0000-000000000002");
    private static final UUID MODELE_ID = UUID.fromString("99990000-0000-0000-0000-000000000003");
    private static final UUID AGENCE_ID = UUID.fromString("99990000-0000-0000-0000-000000000004");
    private static final UUID CENTRE_PAYEUR_ID = UUID.fromString("99990000-0000-0000-0000-000000000005");
    private static final UUID MEDECIN_ID = UUID.fromString("99990000-0000-0000-0000-000000000006");
    private static final UUID SALLE_ID = UUID.fromString("99990000-0000-0000-0000-000000000007");
    private static final UUID POSITION_ID = UUID.fromString("99990000-0000-0000-0000-000000000008");
    private static final UUID TRANSPORTEUR_ALLER_ID = UUID.fromString("99990000-0000-0000-0000-000000000009");
    private static final UUID TRANSPORTEUR_RETOUR_ID = UUID.fromString("99990000-0000-0000-0000-000000000010");
    private static final UUID CATEGORIE_TRANSPORT_ID = UUID.fromString("99990000-0000-0000-0000-000000000011");
    private static final UUID GENERATEUR_ID = UUID.fromString("99990000-0000-0000-0000-000000000012");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM patients WHERE id = ?", PATIENT_ID);
        jdbc.update("DELETE FROM modele_document WHERE id = ?", MODELE_ID);
        jdbc.update("DELETE FROM generateur WHERE id = ?", GENERATEUR_ID);
        jdbc.update("DELETE FROM categorie_transport WHERE id = ?", CATEGORIE_TRANSPORT_ID);
        jdbc.update("DELETE FROM transporteur WHERE id IN (?, ?)", TRANSPORTEUR_ALLER_ID, TRANSPORTEUR_RETOUR_ID);
        jdbc.update("DELETE FROM position_creneau WHERE id = ?", POSITION_ID);
        jdbc.update("DELETE FROM salle WHERE id = ?", SALLE_ID);
        jdbc.update("DELETE FROM medecin WHERE id = ?", MEDECIN_ID);
        jdbc.update("DELETE FROM centre_payeur WHERE id = ?", CENTRE_PAYEUR_ID);
        jdbc.update("DELETE FROM agence WHERE id = ?", AGENCE_ID);
    }

    @Test
    void print_should_export_complete_patient_dataset_for_html() throws Exception {
        cleanup();
        seedReferentials();
        seedModele();
        seedPatient();

        String payload = """
                {
                  "centerId": "%s",
                  "typeDocument": "LISTE_PATIENTS",
                  "formatOverride": "HTML",
                  "params": {}
                }
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/documents/print")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<title>Report</title>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<iframe src='data:application/pdf;base64,")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("width='100%'")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("min-height:95vh")));
    }

    private void seedModele() {
        jdbc.update(
                "INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description, active, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                MODELE_ID,
                CENTER_ID,
                "LISTE_PATIENTS",
                "Liste patients complete",
                "LISTE_PATIENTS",
                "reports/liste_patients.jrxml",
                "EXCEL",
                "Export complet patients",
                true,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private void seedReferentials() {
        jdbc.update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, ?, ?)",
                AGENCE_ID, CENTER_ID, UUID.randomUUID(), "AG-T", "Agence Test");
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom, adresse) VALUES (?, ?, ?, ?, ?, ?)",
                CENTRE_PAYEUR_ID, CENTER_ID, AGENCE_ID, "CP-T", "Centre Payeur Test", "Adresse CP");
        jdbc.update("INSERT INTO medecin (id, center_id, nom, prenom, specialite) VALUES (?, ?, ?, ?, ?)",
                MEDECIN_ID, CENTER_ID, "Medecin", "Reference", "Nephrologie");
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, ?, ?)",
                SALLE_ID, CENTER_ID, "S-01", "Salle Test");
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, ?, ?)",
                POSITION_ID, CENTER_ID, "POS-01", "Position Test");
        jdbc.update("INSERT INTO transporteur (id, center_id, nom, telephone) VALUES (?, ?, ?, ?)",
                TRANSPORTEUR_ALLER_ID, CENTER_ID, "Transport Aller", "055000001");
        jdbc.update("INSERT INTO transporteur (id, center_id, nom, telephone) VALUES (?, ?, ?, ?)",
                TRANSPORTEUR_RETOUR_ID, CENTER_ID, "Transport Retour", "055000002");
        jdbc.update("INSERT INTO categorie_transport (id, center_id, libelle) VALUES (?, ?, ?)",
                CATEGORIE_TRANSPORT_ID, CENTER_ID, "Ambulance");
        jdbc.update("INSERT INTO generateur (id, salle_id, center_id, numero, marque, modele, etat) VALUES (?, ?, ?, ?, ?, ?, ?)",
                GENERATEUR_ID, SALLE_ID, CENTER_ID, "GEN-99", "Brand", "Modele", "FONCTIONNEL");
    }

    private void seedPatient() {
        jdbc.update(
                """
                        INSERT INTO patients (
                            id, center_id, code_patient, civilite, nom, prenom, sexe, groupe_sanguin,
                            date_admission, date_naissance, numero_assurance, type_patient, etat_patient,
                            qualite_assure, observation, sous_kt, epo_enabled, epo_date, photo_base64,
                            centre_payeur_id, medecin_traitant_id, salle_id, position_id, transporteur_aller_id,
                            transporteur_retour_id, categorie_transport_id, generateur_id,
                            jour_lundi, jour_mercredi, jour_vendredi,
                            assure_nom, assure_prenom, assure_numero_assurance, assure_sexe,
                            assure_date_naissance, assure_tel_mobile, assure_adresse, assure_groupe_sanguin,
                            assure_history_json, pieces_jointes_json, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                PATIENT_ID,
                CENTER_ID,
                "PAT-999",
                "M.",
                "Patient",
                "Test",
                "M",
                "O+",
                LocalDate.of(2026, 1, 2),
                LocalDate.of(1980, 6, 15),
                "ASS-999",
                "PERMANENT",
                "ACTIF",
                "ASSURE",
                "Observation Test",
                true,
                true,
                LocalDate.of(2026, 4, 1),
                "abc123",
                CENTRE_PAYEUR_ID,
                MEDECIN_ID,
                SALLE_ID,
                POSITION_ID,
                TRANSPORTEUR_ALLER_ID,
                TRANSPORTEUR_RETOUR_ID,
                CATEGORIE_TRANSPORT_ID,
                GENERATEUR_ID,
                true,
                true,
                true,
                "Assure",
                "Principal",
                "ASSURE-999",
                "F",
                LocalDate.of(1985, 8, 20),
                "066333333",
                "Adresse Assure",
                "A+",
                "{\"history\":true}",
                "[{\"name\":\"piece-a.pdf\"}]",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}



