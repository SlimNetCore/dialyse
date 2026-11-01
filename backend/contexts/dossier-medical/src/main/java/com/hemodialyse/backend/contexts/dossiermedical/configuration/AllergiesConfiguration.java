package com.hemodialyse.backend.contexts.dossiermedical.configuration;

import com.hemodialyse.backend.contexts.dossiermedical.application.AllergieService;
import com.hemodialyse.backend.contexts.dossiermedical.application.AllergieUseCases;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieRepository;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AllergiesJdbc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Composition racine de la slice DM5 (allergies).
 * Assemble l'adaptateur JDBC, le use case et le générateur d'identifiants en beans Spring.
 */
@Configuration
public class AllergiesConfiguration {

    @Bean
    public AllergieRepository allergieRepository(JdbcTemplate jdbc) {
        return new AllergiesJdbc(jdbc);
    }

    @Bean
    public AllergieUseCases allergieUseCases(AllergieRepository r) {
        return new AllergieService(r, UUID::randomUUID);
    }
}
