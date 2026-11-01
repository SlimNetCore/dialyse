package com.hemodialyse.backend.contexts.pilotagedirection.configuration;

import com.hemodialyse.backend.contexts.pilotagedirection.application.InstantaneMensuelService;
import com.hemodialyse.backend.contexts.pilotagedirection.application.InstantaneMensuelUseCases;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence.InstantanesMensuelsJdbc;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.InstantaneMensuelCapture;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Composition racine du contexte pilotage-direction (slice PD2 instantanés mensuels).
 * Assemble l'adaptateur, les use cases et l'éditeur d'événements publiés en beans Spring.
 */
@Configuration
public class InstantanesMensuelsConfiguration {

    @Bean
    public InstantaneMensuelRepository instantaneMensuelRepository(JdbcTemplate jdbc) {
        return new InstantanesMensuelsJdbc(jdbc);
    }

    @Bean
    public InstantaneMensuelUseCases instantaneMensuelUseCases(InstantaneMensuelRepository r) {
        return new InstantaneMensuelService(r, UUID::randomUUID, evenementCaptureNonPublie());
    }

    /**
     * Éditeur d'événements publié par défaut : no-op. Le reporting consomme l'événement
     * {@link InstantaneMensuelCapture} via son propre adaptateur d'entrée (ACL).
     */
    @Bean
    public Consumer<InstantaneMensuelCapture> evenementCaptureNonPublie() {
        return evenement -> {
            // no-op : l'événement est disponible via le langage publié ;
            // la publication effective est gérée par l'ACL du contexte consommateur.
        };
    }
}
