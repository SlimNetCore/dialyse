package com.hemodialyse.backend.contexts.dossiermedical.configuration;

import com.hemodialyse.backend.contexts.dossiermedical.application.AntecedentService;
import com.hemodialyse.backend.contexts.dossiermedical.application.AntecedentUseCases;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AntecedentRepository;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AntecedentsJdbc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Composition racine de la slice DM5 (antécédents).
 * Assemble l'adaptateur JDBC, le use case et le générateur d'identifiants en beans Spring.
 */
@Configuration
public class AntecedentsConfiguration {

    @Bean
    public AntecedentRepository antecedentRepository(JdbcTemplate jdbc) {
        return new AntecedentsJdbc(jdbc);
    }

    @Bean
    public AntecedentUseCases antecedentUseCases(AntecedentRepository r) {
        return new AntecedentService(r, UUID::randomUUID);
    }
}
