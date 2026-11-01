package com.hemodialyse.backend.contexts.pilotagedirection.configuration;

import com.hemodialyse.backend.contexts.pilotagedirection.application.TableauDeBordService;
import com.hemodialyse.backend.contexts.pilotagedirection.application.TableauDeBordUseCases;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBordRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence.TableauxDeBordJdbc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Composition racine du contexte pilotage-direction (slice PD1 tableau de bord).
 * Assemble l'adaptateur et les use cases en beans Spring.
 */
@Configuration
public class TableauxDeBordConfiguration {

    @Bean
    public TableauDeBordRepository tableauDeBordRepository(JdbcTemplate jdbc) {
        return new TableauxDeBordJdbc(jdbc);
    }

    @Bean
    public TableauDeBordUseCases tableauDeBordUseCases(TableauDeBordRepository r) {
        return new TableauDeBordService(r);
    }
}
