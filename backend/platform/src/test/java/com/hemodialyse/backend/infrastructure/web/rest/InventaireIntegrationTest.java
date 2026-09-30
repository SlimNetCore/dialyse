package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.SortieRequestItem;
import com.hemodialyse.backend.domain.stock.port.BonSortieUseCase;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inventaire de stock sur la vraie base (H2) : gel des mouvements pendant l'inventaire (bon de sortie refusé),
 * clôture (stock de départ, mouvements clôturés, recalcul), période clôturée, droits et cloisonnement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class InventaireIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99998000-0000-0000-0000-0000000000a1");
    private static final UUID AUTRE = UUID.fromString("99998000-0000-0000-0000-0000000000b1");
    private static final String BASE = "/api/v1/stock/inventaires";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BonSortieUseCase bonsSortie;
    private MockMvc mockMvc;
    private UUID articleId;
    private UUID lotId;

    private static RequestPostProcessor as(String role) {
        return user(principal(role, CENTRE));
    }

    private static UserPrincipal principal(String role, UUID center) {
        return UserPrincipal.create(UUID.randomUUID().toString(), center.toString(), "zt-inv-" + role, "", List.of(role), true);
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        articleId = UUID.randomUUID();
        lotId = UUID.randomUUID();
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, "
                        + "gere_par_lot, active, created_at) VALUES (?, ?, 'ZT-DIAL', 'Dialyseur ZT', 'U', 8, 0, 1500, TRUE, TRUE, CURRENT_TIMESTAMP)",
                articleId, CENTRE);
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, date_peremption, quantite_initiale, quantite_restante, "
                        + "pmp, created_at) VALUES (?, ?, ?, 'ZT-A1', ?, 10, 8, 1500, CURRENT_TIMESTAMP)", lotId, CENTRE, articleId,
                java.sql.Date.valueOf(LocalDate.now().plusYears(1)));
        OffsetDateTime past = LocalDate.now().minusDays(10).atStartOfDay().atOffset(ZoneOffset.UTC);
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, lot_id, mouvement_type, quantite, prix_unitaire, "
                + "created_by, created_at) VALUES (?, ?, ?, ?, 'ENTREE', 10, 1500, 'zt', ?)", UUID.randomUUID(), CENTRE, articleId, lotId, past);
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, lot_id, mouvement_type, quantite, prix_unitaire, "
                        + "created_by, created_at) VALUES (?, ?, ?, ?, 'SORTIE', 2, 1500, 'zt', ?)", UUID.randomUUID(), CENTRE, articleId, lotId,
                past.plusDays(1));
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("inventaire_lignes", "inventaires", "stock_movements", "bon_sortie_lignes", "bons_sortie", "lots",
                "articles")) {
            try {
                jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE, AUTRE);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // table sans center_id ou absente : ignorée
            }
        }
        jdbc.update("DELETE FROM app_settings WHERE center_id IN (?, ?)", CENTRE, AUTRE);
    }

    @Test
    void fullInventoryCycle() throws Exception {
        String body = mockMvc.perform(post(BASE).with(as("PHARMACIEN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commentaire\":\"Inventaire annuel\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_COURS"))
                .andExpect(jsonPath("$.lignes").value(1))
                .andExpect(jsonPath("$.details[0].quantiteTheorique").value(8))
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll("^\\{\"id\":\"([^\"]+)\".*", "$1");
        String ligneId = body.replaceAll(".*\"details\":\\[\\{\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get(BASE + "/etat").with(as("INFIRMIER")))
                .andExpect(jsonPath("$.mouvementsBloques").value(true));

        // Gel : un bon de sortie est refusé tant que l'inventaire est ouvert.
        assertThatThrownBy(() -> bonsSortie.create(CenterId.of(CENTRE), null, null, "P1", LocalDate.now(),
                List.of(new SortieRequestItem(articleId, lotId, BigDecimal.ONE)), "zt"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("STOCK_INVENTORY_IN_PROGRESS"));
        assertThat(jdbc.queryForObject("SELECT quantite_restante FROM lots WHERE id = ?", BigDecimal.class, lotId))
                .isEqualByComparingTo("8");

        // Comptage avec écart : la clôture exige un motif.
        mockMvc.perform(put(BASE + "/{id}/lignes/{ligne}", id, ligneId).with(as("INFIRMIER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantite\":7}"))
                .andExpect(jsonPath("$.ecarts").value(1))
                .andExpect(jsonPath("$.valeurEcarts").value(-1500.0));
        mockMvc.perform(post(BASE + "/{id}/cloturer", id).with(as("PHARMACIEN")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVENTORY_GAP_WITHOUT_REASON"));
        mockMvc.perform(put(BASE + "/{id}/lignes/{ligne}", id, ligneId).with(as("INFIRMIER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantite\":7,\"motif\":\"Casse\"}"))
                .andExpect(jsonPath("$.ecartsSansMotif").value(0));

        mockMvc.perform(post(BASE + "/{id}/cloturer", id).with(as("INFIRMIER"))).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/{id}/cloturer", id).with(as("PHARMACIEN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("CLOTURE"));

        // Stock de départ : lot et article à 7 ; anciens mouvements clôturés ; un mouvement INVENTAIRE.
        assertThat(jdbc.queryForObject("SELECT quantite_restante FROM lots WHERE id = ?", BigDecimal.class, lotId)).isEqualByComparingTo("7");
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM articles WHERE id = ?", BigDecimal.class, articleId)).isEqualByComparingTo("7");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE center_id = ? AND inventaire_id IS NOT NULL",
                Long.class, CENTRE)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE center_id = ? AND mouvement_type = 'INVENTAIRE' "
                + "AND inventaire_id IS NULL", Long.class, CENTRE)).isEqualTo(1);

        // Période clôturée : une sortie datée du jour de l'inventaire est refusée…
        assertThatThrownBy(() -> bonsSortie.create(CenterId.of(CENTRE), null, null, "P1", LocalDate.now(),
                List.of(new SortieRequestItem(articleId, lotId, BigDecimal.ONE)), "zt"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("STOCK_PERIOD_CLOSED"));
        // … une sortie postérieure est acceptée et recalculée à partir du stock de départ.
        bonsSortie.create(CenterId.of(CENTRE), null, null, "P1", LocalDate.now().plusDays(1),
                List.of(new SortieRequestItem(articleId, lotId, new BigDecimal("2"))), "zt");
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM articles WHERE id = ?", BigDecimal.class, articleId)).isEqualByComparingTo("5");

        mockMvc.perform(get(BASE).with(as("INFIRMIER")))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].ecarts").value(1));
    }

    @Test
    void cancellingReleasesTheFreezeAndOtherCentersAreIsolated() throws Exception {
        String body = mockMvc.perform(post(BASE).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = body.replaceAll("^\\{\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get(BASE + "/{id}", id).with(user(principal("ADMIN", AUTRE))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVENTORY_NOT_FOUND"));
        mockMvc.perform(get(BASE + "/etat").with(user(principal("ADMIN", AUTRE))))
                .andExpect(jsonPath("$.mouvementsBloques").value(false));
        mockMvc.perform(post(BASE).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVENTORY_ALREADY_IN_PROGRESS"));

        mockMvc.perform(post(BASE + "/{id}/annuler", id).with(as("ADMIN")))
                .andExpect(jsonPath("$.statut").value("ANNULE"));
        bonsSortie.create(CenterId.of(CENTRE), null, null, "P1", LocalDate.now(),
                List.of(new SortieRequestItem(articleId, lotId, BigDecimal.ONE)), "zt");
        assertThat(jdbc.queryForObject("SELECT quantite_restante FROM lots WHERE id = ?", BigDecimal.class, lotId)).isEqualByComparingTo("7");
    }

    @Test
    void countSheetIsDownloadable() throws Exception {
        String body = mockMvc.perform(post(BASE).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll("^\\{\"id\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(get(BASE + "/{id}/feuille-comptage", id).with(as("INFIRMIER")))
                .andExpect(status().isOk());
    }
}

