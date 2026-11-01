package com.hemodialyse.backend.contexts.dossiermedical.configuration;

import com.hemodialyse.backend.contexts.dossiermedical.application.DemandeExamenService;
import com.hemodialyse.backend.contexts.dossiermedical.application.DemandeExamenUseCases;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenRepository;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.DemandesExamenJdbc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Composition racine du contexte dossier-m\u00e9dical (slice DM3 examens).
 * Assemble les adaptateurs et les use cases en beans Spring.
 */
@Configuration
public class DemandesExamenConfiguration {

    @Bean
    public DemandeExamenRepository demandeExamenRepository(JdbcTemplate jdbc) {
        return new DemandesExamenJdbc(jdbc);
    }

    @Bean
    public DemandeExamenUseCases demandeExamenUseCases(DemandeExamenRepository r) {
        return new DemandeExamenService(r, UUID::randomUUID);
    }
}
