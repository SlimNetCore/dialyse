package com.hemodialyse.backend.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * La console H2 et Swagger ne sont publics qu'en développement local ({@code app.dev-tools.enabled=true}). Désactivés,
 * comme sous le profil {@code prod}, aucun chemin n'est ouvert et ils retombent sur l'authentification obligatoire ;
 * le comportement HTTP réel sous le profil {@code prod} est contrôlé par le workflow {@code flyway.yml} sur PostgreSQL.
 * (Pas de second contexte Spring ici : il rejouerait le seed sur la base H2 partagée des autres tests.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class DevToolsExposureIntegrationTest {

    private static final String[] CHEMINS = {"/h2-console/", "/swagger-ui.html", "/v3/api-docs"};

    @Autowired
    private WebApplicationContext context;

    @Test
    void by_default_the_development_tools_are_not_blocked_by_authentication() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        for (String chemin : CHEMINS) {
            int statut = mockMvc.perform(get(chemin)).andReturn().getResponse().getStatus();
            assertNotEquals(401, statut, chemin);
            assertNotEquals(403, statut, chemin);
        }
    }

    @Test
    void disabled_development_tools_open_no_public_path() {
        SecurityConfig config = new SecurityConfig(null, null);

        ReflectionTestUtils.setField(config, "devToolsEnabled", false);
        assertEquals(0, config.devToolsPaths().length);

        ReflectionTestUtils.setField(config, "devToolsEnabled", true);
        assertArrayEquals(new String[]{"/h2-console/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                "/v3/api-docs.yaml"}, config.devToolsPaths());
    }
}
