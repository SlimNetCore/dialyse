package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
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
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fiche article complète : création/modification, unicité du code par centre, liste paginée filtrée, isolation
 * multi-centre, et conversion de la dose prescrite (UI, mg) en quantité de stock lors d'une administration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ArticleFicheRestControllerIntegrationTest {

    private static final UUID CENTER_A = UUID.fromString("77777000-0000-0000-0000-000000000001");
    private static final UUID CENTER_B = UUID.fromString("77777000-0000-0000-0000-000000000002");
    private static final UUID PATIENT_ID = UUID.fromString("77777000-0000-0000-0000-000000000101");
    private static final String BASE = "/api/v1/stock/referentiel/articles";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    private static String body(UUID center, String code, String dosage, String uniteDosage) {
        return """
                {"centerId":"%s","code":"%s","libelle":"Époétine alfa 4000 UI","dci":"Époétine alfa",
                 "formeGalenique":"Solution injectable","unite":"seringue","uniteAchat":"boîte","coefficientAchat":6,
                 "dosageParUnite":%s,"uniteDosage":%s,"prixAchat":2500,"seuilAlerte":10,"stockMax":100,
                 "gereParLot":true,"peremptionObligatoire":true,"conditionConservation":"REFRIGERE",
                 "produitDangereux":false,"dechetDasri":true,"typeTraitementAnemie":"EPO"}
                """.formatted(center, code, dosage, uniteDosage);
    }

    private static String administration(String articleId, String dose, String unite) {
        return """
                {"centerId":"%s","prescriptionMedicaleId":null,"typeTraitement":"EPO","molecule":"Époétine",
                 "dose":%s,"uniteDose":%s,"voie":"IV","dateAdministration":"2026-01-01","seanceId":null,
                 "administrePar":"infirmier-1","administree":true,"motifNonAdministration":null,
                 "articleId":"%s","quantiteArticle":999}
                """.formatted(CENTER_A, dose, unite, articleId);
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, "
                        + "numero_assurance, type_patient, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PATIENT_ID, CENTER_A, "PAT-ART-001", "Nom", "Prenom", "F", LocalDate.of(2026, 7, 1), "ASS-ART-001",
                "NON_VACANCIER", OffsetDateTime.now(ZoneOffset.UTC));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM administrations_anemie WHERE patient_id = ?", PATIENT_ID);
        jdbc.update("DELETE FROM patients WHERE id = ?", PATIENT_ID);
        jdbc.update("DELETE FROM articles WHERE center_id IN (?, ?)", CENTER_A, CENTER_B);
    }

    private String creer(UUID center, String code, String dosage, String uniteDosage, String role, UUID userCenter)
            throws Exception {
        var result = mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(body(center, code, dosage, uniteDosage))
                        .with(user(principal("pharmacien", userCenter, role))))
                .andReturn().getResponse();
        return result.getContentAsString();
    }

    @Test
    void cree_la_fiche_complete_et_la_relit() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(body(CENTER_A, "EPO-4000", "4000", "\"UI\""))
                        .with(user(principal("pharmacien", CENTER_A, "PHARMACIEN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dci").value("Époétine alfa"))
                .andExpect(jsonPath("$.dosageParUnite").value(4000))
                .andExpect(jsonPath("$.uniteDosage").value("UI"))
                .andExpect(jsonPath("$.conditionConservation").value("REFRIGERE"))
                .andExpect(jsonPath("$.dechetDasri").value(true))
                .andExpect(jsonPath("$.stockQuantity").value(0))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void refuse_un_code_deja_utilise_dans_le_centre_mais_pas_dans_un_autre() throws Exception {
        creer(CENTER_A, "EPO-4000", "4000", "\"UI\"", "ADMIN", CENTER_A);

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(body(CENTER_A, "epo-4000", "4000", "\"UI\""))
                        .with(user(principal("admin", CENTER_A, "ADMIN"))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(body(CENTER_B, "EPO-4000", "4000", "\"UI\""))
                        .with(user(principal("admin", CENTER_B, "ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void refuse_un_dosage_sans_unite() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(body(CENTER_A, "EPO-4000", "4000", "null"))
                        .with(user(principal("admin", CENTER_A, "ADMIN"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void modifie_la_fiche_sans_toucher_au_stock_et_garde_l_unicite_du_code() throws Exception {
        String created = creer(CENTER_A, "EPO-4000", "4000", "\"UI\"", "ADMIN", CENTER_A);
        String id = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        creer(CENTER_A, "EPO-2000", "2000", "\"UI\"", "ADMIN", CENTER_A);

        mockMvc.perform(put(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(CENTER_A, "EPO-4000", "2000", "\"UI\""))
                        .with(user(principal("admin", CENTER_A, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dosageParUnite").value(2000));

        mockMvc.perform(put(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(CENTER_A, "EPO-2000", "2000", "\"UI\""))
                        .with(user(principal("admin", CENTER_A, "ADMIN"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void un_autre_centre_ne_peut_ni_lire_ni_modifier_la_fiche() throws Exception {
        String created = creer(CENTER_A, "EPO-4000", "4000", "\"UI\"", "ADMIN", CENTER_A);
        String id = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get(BASE + "/{id}", id).param("centerId", CENTER_A.toString())
                        .with(user(principal("admin", CENTER_B, "ADMIN"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(CENTER_A, "EPO-4000", "4000", "\"UI\""))
                        .with(user(principal("admin", CENTER_B, "ADMIN"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/{id}", id).param("centerId", CENTER_B.toString())
                        .with(user(principal("admin", CENTER_B, "ADMIN"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void liste_paginee_filtree_par_code_dci_et_etat_et_isolee_par_centre() throws Exception {
        creer(CENTER_A, "EPO-4000", "4000", "\"UI\"", "ADMIN", CENTER_A);
        String second = creer(CENTER_A, "FER-100", "100", "\"mg\"", "ADMIN", CENTER_A);
        creer(CENTER_B, "EPO-B", "4000", "\"UI\"", "ADMIN", CENTER_B);
        String id = second.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get(BASE + "/page").param("centerId", CENTER_A.toString()).param("size", "1")
                        .with(user(principal("admin", CENTER_A, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value("EPO-4000"));

        mockMvc.perform(get(BASE + "/page").param("centerId", CENTER_A.toString()).param("q", "FER-1")
                        .with(user(principal("admin", CENTER_A, "ADMIN"))))
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get(BASE + "/page").param("centerId", CENTER_A.toString()).param("q", "introuvable")
                        .with(user(principal("admin", CENTER_A, "ADMIN"))))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get(BASE + "/page").param("centerId", CENTER_A.toString()).param("q", "alfa")
                        .with(user(principal("admin", CENTER_A, "ADMIN"))))
                .andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(patch(BASE + "/{id}/active", id).param("centerId", CENTER_A.toString())
                        .param("active", "false").with(user(principal("admin", CENTER_A, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get(BASE + "/page").param("centerId", CENTER_A.toString()).param("active", "false")
                        .with(user(principal("admin", CENTER_A, "ADMIN"))))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].code").value("FER-100"));
    }

    @Test
    void l_infirmier_ne_gere_pas_les_fiches() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(body(CENTER_A, "EPO-4000", "4000", "\"UI\""))
                        .with(user(principal("infirmier", CENTER_A, "INFIRMIER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/page").param("centerId", CENTER_A.toString())
                        .with(user(principal("infirmier", CENTER_A, "INFIRMIER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void l_administration_deduit_la_quantite_de_stock_de_la_dose_prescrite() throws Exception {
        String created = creer(CENTER_A, "EPO-4000", "4000", "\"UI\"", "ADMIN", CENTER_A);
        String articleId = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/patients/{id}/administrations-anemie", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(administration(articleId, "8000", "\"UI\""))
                        .with(user(principal("infirmier", CENTER_A, "INFIRMIER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantiteArticle").value(2.0));
    }

    @Test
    void l_administration_refuse_une_unite_de_dose_incompatible_avec_l_article() throws Exception {
        String created = creer(CENTER_A, "EPO-4000", "4000", "\"UI\"", "ADMIN", CENTER_A);
        String articleId = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/patients/{id}/administrations-anemie", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(administration(articleId, "100", "\"mg\""))
                        .with(user(principal("infirmier", CENTER_A, "INFIRMIER"))))
                .andExpect(status().isUnprocessableEntity());
    }

    private UserPrincipal principal(String username, UUID centerId, String role) {
        return UserPrincipal.create(
                UUID.randomUUID().toString(), centerId.toString(), username, "", List.of(role), true);
    }
}
