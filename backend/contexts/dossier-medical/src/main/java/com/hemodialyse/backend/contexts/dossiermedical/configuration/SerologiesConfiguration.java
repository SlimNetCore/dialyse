package com.hemodialyse.backend.contexts.dossiermedical.configuration;

import com.hemodialyse.backend.contexts.dossiermedical.application.SerologieService;
import com.hemodialyse.backend.contexts.dossiermedical.application.SerologieUseCases;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieRepository;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.SerologiesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.SerologiePositiveDetectee;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Composition racine du contexte dossier-médical (slice DM4 sérologie).
 * Assemble les adaptateurs, les use cases et l'éditeur d'événements publiés en beans Spring.
 */
@Configuration
public class SerologiesConfiguration {

    @Bean
    public SerologieRepository serologieRepository(JdbcTemplate jdbc) {
        return new SerologiesJdbc(jdbc);
    }

    @Bean
    public SerologieUseCases serologieUseCases(SerologieRepository r) {
        return new SerologieService(r, UUID::randomUUID, evenementNonPublie());
    }

    /**
     * Éditeur d'événements publié par défaut : no-op. Le contexte Planification consomme
     * l'événement {@link SerologiePositiveDetectee} via son propre adaptateur d'entrée (ACL).
     * Ce bean peut être remplacé par une implémentation qui publie sur l'outbox / le bus d'événements.
     */
    @Bean
    public Consumer<SerologiePositiveDetectee> evenementNonPublie() {
        return evenement -> {
            // no-op : l'événement est disponible via le langage publié ;
            // la publication effective est gérée par l'ACL du contexte consommateur.
        };
    }
}
