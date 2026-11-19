package com.hemodialyse.backend.contexts.stock.configuration;

import com.hemodialyse.backend.contexts.stock.application.consultation.bons.ConsulterLesBonsParPage;
import com.hemodialyse.backend.contexts.stock.application.consultation.fournisseurs.ConsulterLesFournisseursParPage;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages.BonsParPageJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages.FournisseursParPageJdbc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Câblage des listes paginées des bons et des fournisseurs (slice {@code pagination-serveur}, RG-STK-087 à 089) : lectures SQL
 * paginées et cas d'usage de consultation. {@code StockContexteConfiguration} n'est pas modifiée.
 */
@Configuration(proxyBeanMethods = false)
public class PagesDesBonsConfiguration {

    @Bean
    ConsulterLesBonsParPage stockConsulterLesBonsParPage(JdbcTemplate jdbc) {
        return new ConsulterLesBonsParPage(new BonsParPageJdbc(jdbc));
    }

    @Bean
    ConsulterLesFournisseursParPage stockConsulterLesFournisseursParPage(JdbcTemplate jdbc) {
        return new ConsulterLesFournisseursParPage(new FournisseursParPageJdbc(jdbc));
    }
}
