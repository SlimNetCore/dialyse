package com.hemodialyse.backend.contexts.dossiermedical.configuration;

import com.hemodialyse.backend.contexts.dossiermedical.application.ObservationBiologiqueService;
import com.hemodialyse.backend.contexts.dossiermedical.application.ObservationBiologiqueUseCases;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologiqueRepository;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.ObservationsBiologiquesJdbc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Composition racine du contexte dossier-médical (slice DM6 observations biologiques).
 * Assemble l'adaptateur et les use cases en beans Spring.
 */
@Configuration
public class ObservationsBiologiquesConfiguration {

    @Bean
    public ObservationBiologiqueRepository observationBiologiqueRepository(JdbcTemplate jdbc) {
        return new ObservationsBiologiquesJdbc(jdbc);
    }

    @Bean
    public ObservationBiologiqueUseCases observationBiologiqueUseCases(ObservationBiologiqueRepository r) {
        return new ObservationBiologiqueService(r, UUID::randomUUID);
    }
}
