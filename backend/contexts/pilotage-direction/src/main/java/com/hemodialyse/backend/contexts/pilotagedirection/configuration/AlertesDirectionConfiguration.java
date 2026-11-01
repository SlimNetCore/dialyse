package com.hemodialyse.backend.contexts.pilotagedirection.configuration;

import com.hemodialyse.backend.contexts.pilotagedirection.application.AlerteDirectionService;
import com.hemodialyse.backend.contexts.pilotagedirection.application.AlerteDirectionUseCases;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence.AlertesDirectionJdbc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Composition racine du contexte pilotage-direction (slice PD3 alertes).
 * Assemble l'adaptateur, les use cases et l'éditeur d'événements publiés en beans Spring.
 */
@Configuration
public class AlertesDirectionConfiguration {

    @Bean
    public AlerteDirectionRepository alerteDirectionRepository(JdbcTemplate jdbc) {
        return new AlertesDirectionJdbc(jdbc);
    }

    @Bean
    public AlerteDirectionUseCases alerteDirectionUseCases(AlerteDirectionRepository r) {
        return new AlerteDirectionService(r, UUID::randomUUID, evenementGenerationNonPublie());
    }

    /**
     * Éditeur d'événements publié par défaut : no-op. Les notifications direction consomment
     * l'événement {@link com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlerteDirectionGeneree}
     * via leur propre adaptateur d'entrée (ACL).
     */
    @Bean
    public Consumer<com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlerteDirectionGeneree> evenementGenerationNonPublie() {
        return evenement -> {
            // no-op : l'événement est disponible via le langage publié ;
            // la publication effective est gérée par l'ACL du contexte consommateur.
        };
    }
}
