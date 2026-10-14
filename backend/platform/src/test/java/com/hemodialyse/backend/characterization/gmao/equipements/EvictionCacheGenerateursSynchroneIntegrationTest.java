package com.hemodialyse.backend.characterization.gmao.equipements;

import com.hemodialyse.backend.domain.referential.port.ReferentialRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Le cache {@code ref.generateurs} est évincé de façon synchrone, avant que la réponse HTTP ne parte (point validé n°1 de
 * G3) : on observe le cache immédiatement après la réponse, sans attendre l'écouteur asynchrone de Planification.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class EvictionCacheGenerateursSynchroneIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99996301-0000-0000-0000-00000000000a");
    private static final UUID ADMIN = UUID.fromString("99996301-0000-0000-0000-0000000000f1");
    private static final UUID GENERATEUR = UUID.fromString("99996301-0000-0000-0000-000000000101");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CacheManager caches;
    @Autowired
    private ReferentialRepositoryPort referentiel;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        nettoyer();
        jdbc.update("INSERT INTO gmao_equipements (id, code, designation, type, centre_id, statut, date_installation, "
                        + "date_creation, cree_par) VALUES (?, 'G-SYNC', 'Generateur', 'GENERATEUR_DIALYSE', ?, 'EN_SERVICE', ?, ?, ?)",
                GENERATEUR, CENTRE, OffsetDateTime.of(2020, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 1, 1, 9, 0, 0, 0, ZoneOffset.UTC), ADMIN);
    }

    @AfterEach
    void nettoyer() {
        await().atMost(Duration.ofSeconds(10)).until(
                () -> jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class) == 0);
        jdbc.update("DELETE FROM gmao_equipement_statut_historique WHERE centre_id = ?", CENTRE);
        jdbc.update("DELETE FROM gmao_equipements WHERE centre_id = ?", CENTRE);
        jdbc.update("DELETE FROM notification_evenement WHERE center_id = ?", CENTRE);
    }

    @Test
    void apres_la_reponse_de_la_mise_hors_service_le_cache_ne_contient_plus_l_ancien_statut() throws Exception {
        referentiel.findGenerateurs(new CenterId(CENTRE));
        assertNotNull(caches.getCache("ref.generateurs").get(CENTRE.toString()), "cache amorcé");

        mvc.perform(post("/api/v1/gmao/equipements/" + GENERATEUR + "/hors-service")
                        .with(user(UserPrincipal.create(ADMIN.toString(), CENTRE.toString(), "admin", "", List.of("ADMIN"), true)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"raison\":\"panne\"}"))
                .andReturn();

        // aucune attente : l'écouteur asynchrone n'est pas sollicité
        assertNull(caches.getCache("ref.generateurs").get(CENTRE.toString()));
    }
}
