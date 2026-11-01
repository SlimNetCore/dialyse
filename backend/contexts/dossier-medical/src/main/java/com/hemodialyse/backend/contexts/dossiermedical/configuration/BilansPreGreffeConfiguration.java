package com.hemodialyse.backend.contexts.dossiermedical.configuration;

import com.hemodialyse.backend.contexts.dossiermedical.application.BilanPreGreffeService;
import com.hemodialyse.backend.contexts.dossiermedical.application.BilanPreGreffeUseCases;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeRepository;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.BilansPreGreffeJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.BilanPreGreffeComplete;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Composition racine du contexte dossier-médical (slice DM7 bilan pré-greffe).
 * Assemble l'adaptateur, les use cases et l'éditeur d'événements publiés en beans Spring.
 */
@Configuration
public class BilansPreGreffeConfiguration {

    @Bean
    public BilanPreGreffeRepository bilanPreGreffeRepository(JdbcTemplate jdbc) {
        return new BilansPreGreffeJdbc(jdbc);
    }

    @Bean
    public BilanPreGreffeUseCases bilanPreGreffeUseCases(BilanPreGreffeRepository r) {
        return new BilanPreGreffeService(r, UUID::randomUUID, evenementBilanNonPublie());
    }

    /**
     * Éditeur d'événements publié par défaut : no-op. Le contexte Patient &amp; couverture
     * consomme l'événement {@link BilanPreGreffeComplete} via son propre adaptateur d'entrée (ACL).
     */
    @Bean
    public Consumer<BilanPreGreffeComplete> evenementBilanNonPublie() {
        return evenement -> {
            // no-op : l'événement est disponible via le langage publié ;
            // la publication effective est gérée par l'ACL du contexte consommateur.
        };
    }
}
