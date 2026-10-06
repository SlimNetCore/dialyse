package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suppression d'une séance par l'administrateur sur la base H2 : stock restitué, volets et bons supprimés, trace au
 * journal ; refus pour un autre rôle, une séance d'un autre centre, une séance facturée ou sans motif.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SeanceSuppressionIntegrationTest {

    private static final UUID C1 = UUID.fromString("99999200-0000-0000-0000-0000000000c1");
    private static final UUID C2 = UUID.fromString("99999200-0000-0000-0000-0000000000c2");
    private static final LocalDate LUNDI = LocalDate.of(2026, 10, 5);

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SeanceUseCase seances;

    private MockMvc mockMvc;
    private UUID article;
    private UUID lot;

    private static RequestPostProcessor as(UUID center, String role) {
        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID().toString(), center.toString(), "u-" + role, "",
                List.of(role), true);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        article = UUID.randomUUID();
        lot = UUID.randomUUID();
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, "
                        + "gere_par_lot, active, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                article, C1, "ZT-SUP-A", "Dialyseur", "u", new BigDecimal("10"), BigDecimal.ONE, BigDecimal.TEN, true, true,
                OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, quantite_initiale, quantite_restante, pmp, "
                        + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", lot, C1, article, "ZT-SUP-L1", new BigDecimal("10"),
                new BigDecimal("10"), BigDecimal.TEN, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM bons_sortie_lignes WHERE bon_sortie_id IN (SELECT id FROM bons_sortie WHERE center_id IN (?, ?))",
                C1, C2);
        for (String table : List.of("bons_sortie", "stock_movements", "volet_paramedical", "volet_medical",
                "seance_suppression", "seances", "lots", "articles")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", C1, C2);
        }
    }

    private UUID seance(UUID centre, String statut) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, hors_planning) "
                + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)", id, UUID.randomUUID(), centre, LUNDI, statut);
        return id;
    }

    private BigDecimal restant() {
        return jdbc.queryForObject("SELECT quantite_restante FROM lots WHERE id = ?", BigDecimal.class, lot);
    }

    private long compter(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void the_admin_deletes_a_validated_session_and_its_consumables_return_to_stock() throws Exception {
        UUID id = seance(C1, "VALIDEE");
        seances.addConsommableSeance(CenterId.of(C1), id, article, new BigDecimal("3"), "inf-01");
        jdbc.update("INSERT INTO volet_paramedical (id, seance_id, center_id, poids_avant_kg) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), id, C1, new BigDecimal("72.5"));
        assertThat(restant()).isEqualByComparingTo("7");

        mockMvc.perform(delete("/api/v1/seances/{id}", id).with(as(C1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Séance saisie en double\"}"))
                .andExpect(status().isNoContent());

        assertThat(compter("SELECT COUNT(*) FROM seances WHERE id = ?", id)).isZero();
        assertThat(compter("SELECT COUNT(*) FROM volet_paramedical WHERE seance_id = ?", id)).isZero();
        assertThat(compter("SELECT COUNT(*) FROM bons_sortie WHERE seance_id = ?", id)).isZero();
        assertThat(compter("SELECT COUNT(*) FROM stock_movements WHERE seance_id = ?", id)).isZero();
        assertThat(restant()).as("le stock consommé est restitué").isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT motif FROM seance_suppression WHERE seance_id = ? AND center_id = ?",
                String.class, id, C1)).isEqualTo("Séance saisie en double");
        assertThat(jdbc.queryForObject("SELECT statut FROM seance_suppression WHERE seance_id = ?", String.class, id))
                .isEqualTo("VALIDEE");
    }

    @Test
    void only_the_admin_of_the_center_deletes_an_unbilled_session_with_a_reason() throws Exception {
        UUID id = seance(C1, "CREE");
        UUID autreCentre = seance(C2, "CREE");
        UUID facturee = seance(C1, "FACTUREE");
        String motif = "{\"motif\":\"Erreur de patient\"}";

        mockMvc.perform(delete("/api/v1/seances/{id}", id).with(as(C1, "INFIRMIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(motif))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/seances/{id}", autreCentre).with(as(C1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(motif))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SEANCE_INTROUVABLE"));
        mockMvc.perform(delete("/api/v1/seances/{id}", facturee).with(as(C1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(motif))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SEANCE_FACTUREE_NON_SUPPRIMABLE"));
        mockMvc.perform(delete("/api/v1/seances/{id}", id).with(as(C1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\" \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SEANCE_SUPPRESSION_MOTIF_INVALIDE"));

        assertThat(compter("SELECT COUNT(*) FROM seances WHERE id IN (?, ?, ?)", id, autreCentre, facturee)).isEqualTo(3);
        assertThat(compter("SELECT COUNT(*) FROM seance_suppression WHERE center_id IN (?, ?)", C1, C2)).isZero();
    }
}
