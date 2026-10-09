package com.hemodialyse.backend.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Le profil {@code prod} ferme ce qui n'a de sens qu'en développement et laisse Flyway seul maître du schéma. Ce test
 * lit le fichier pour qu'un retrait accidentel d'une de ces lignes casse la CI, et vérifie que le défaut reste celui du
 * développement local (H2, seed de démo, outils de dev ouverts).
 */
class ProductionProfileConfigTest {

    private static Properties charger(String fichier) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(fichier));
        return yaml.getObject();
    }

    @Test
    void the_prod_profile_locks_the_schema_and_closes_the_development_tools() {
        Properties prod = charger("application-prod.yml");

        assertEquals("${HIBERNATE_DDL_AUTO:validate}", prod.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("never", prod.getProperty("spring.sql.init.mode"));
        assertEquals("false", prod.getProperty("spring.h2.console.enabled"));
        assertEquals("false", prod.getProperty("springdoc.swagger-ui.enabled"));
        assertEquals("false", prod.getProperty("springdoc.api-docs.enabled"));
        assertEquals("false", prod.getProperty("app.dev-tools.enabled"));
        assertEquals("${STOCK_DEMO_DATA:false}", prod.getProperty("app.stock.demo-data"));
    }

    @Test
    void the_default_configuration_stays_the_local_development_one() {
        Properties defaut = charger("application.yml");

        assertEquals("update", defaut.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("always", defaut.getProperty("spring.sql.init.mode"));
        assertEquals("true", defaut.getProperty("spring.h2.console.enabled"));
        assertEquals("${DEV_TOOLS_ENABLED:true}", defaut.getProperty("app.dev-tools.enabled"));
        assertEquals("${STOCK_DEMO_DATA:true}", defaut.getProperty("app.stock.demo-data"));
    }
}
