package com.hemodialyse.backend;

import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Donne a chaque contexte Spring de test sa propre base H2 en memoire.
 *
 * <p>Sans cela, tous les contextes mis en cache partagent {@code jdbc:h2:mem:hemodialyse} (DB_CLOSE_DELAY=-1) et
 * {@code db/seed.sql} est rejoue sur une base deja modifiee par d'autres classes : les ids calcules par ROW_NUMBER()
 * du seed collisionnent selon l'ordre d'execution. Enregistre via {@code META-INF/spring.factories}.
 * Les tests qui fixent deja leur propre URL ne sont pas touches.
 */
public class BaseH2UniqueParContexte implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String URL_PARTAGEE = "jdbc:h2:mem:hemodialyse;";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        String url = environment.getProperty("spring.datasource.url");
        if (url == null || !url.startsWith(URL_PARTAGEE)) {
            return;
        }
        String urlIsolee = "jdbc:h2:mem:hemodialyse-" + UUID.randomUUID() + url.substring(URL_PARTAGEE.length() - 1);
        environment.getPropertySources().addFirst(new MapPropertySource("baseH2UniqueParContexte",
                Map.of("spring.datasource.url", urlIsolee, "jakarta.persistence.jdbc.url", urlIsolee)));
    }
}
