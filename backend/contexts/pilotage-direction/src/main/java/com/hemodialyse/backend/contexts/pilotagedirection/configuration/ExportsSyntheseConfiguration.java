package com.hemodialyse.backend.contexts.pilotagedirection.configuration;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ExportSyntheseService;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ExportSyntheseUseCases;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSyntheseRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBordRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence.ExportsSyntheseJdbc;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.ExportSyntheseGenere;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Composition racine du contexte pilotage-direction (slice PD4 export synthèse).
 * Assemble l'adaptateur, les use cases et l'éditeur d'événements publiés en beans Spring.
 */
@Configuration
public class ExportsSyntheseConfiguration {

    @Bean
    public ExportSyntheseRepository exportSyntheseRepository(JdbcTemplate jdbc) {
        return new ExportsSyntheseJdbc(jdbc);
    }

    @Bean
    public ExportSyntheseUseCases exportSyntheseUseCases(ExportSyntheseRepository exports,
                                                         TableauDeBordRepository tableaux,
                                                         AlerteDirectionRepository alertes,
                                                         InstantaneMensuelRepository instantanes) {
        return new ExportSyntheseService(exports, tableaux, alertes, instantanes,
                UUID::randomUUID, evenementExportNonPublie());
    }

    /**
     * Éditeur d'événements publié par défaut : no-op. Le reporting consomme l'événement
     * {@link ExportSyntheseGenere} via son propre adaptateur d'entrée (ACL).
     */
    @Bean
    public Consumer<ExportSyntheseGenere> evenementExportNonPublie() {
        return evenement -> {
            // no-op : l'événement est disponible via le langage publié ;
            // la publication effective est gérée par l'ACL du contexte consommateur.
        };
    }
}
