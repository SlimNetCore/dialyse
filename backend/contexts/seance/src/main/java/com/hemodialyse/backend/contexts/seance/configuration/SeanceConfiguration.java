package com.hemodialyse.backend.contexts.seance.configuration;

import com.hemodialyse.backend.contexts.seance.application.DerogationRegularisationService;
import com.hemodialyse.backend.contexts.seance.application.DerogationRegularisationUseCases;
import com.hemodialyse.backend.contexts.seance.application.SeanceService;
import com.hemodialyse.backend.contexts.seance.application.SeanceUseCases;
import com.hemodialyse.backend.contexts.seance.application.VerrouillageFacturation;
import com.hemodialyse.backend.contexts.seance.interfaces.evenements.VerrouillageFacturationEcouteur;
import com.hemodialyse.backend.contexts.seance.domain.DerogationRegularisationRepository;
import com.hemodialyse.backend.contexts.seance.domain.PecValidationPort;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRepository;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.DerogationsRegularisationJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.PecValidationJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.SeancesJdbc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

@Configuration
public class SeanceConfiguration {
    @Bean
    public SeanceRepository seanceRepository(JdbcTemplate jdbc) {
        return new SeancesJdbc(jdbc);
    }

    @Bean
    public PecValidationPort pecValidationPort(JdbcTemplate jdbc) {
        return new PecValidationJdbc(jdbc);
    }

    @Bean
    public SeanceUseCases seanceUseCases(SeanceRepository r, PecValidationPort p) {
        return new SeanceService(r, p, UUID::randomUUID);
    }

    @Bean
    public VerrouillageFacturation verrouillageFacturation(SeanceRepository r) {
        return new VerrouillageFacturation(r);
    }

    @Bean
    public VerrouillageFacturationEcouteur verrouillageFacturationEcouteur(VerrouillageFacturation v) {
        return new VerrouillageFacturationEcouteur(v);
    }

    @Bean
    public DerogationRegularisationRepository derogationRegularisationRepository(JdbcTemplate jdbc) {
        return new DerogationsRegularisationJdbc(jdbc);
    }

    @Bean
    public DerogationRegularisationUseCases derogationRegularisationUseCases(
            DerogationRegularisationRepository r, SeanceRepository s) {
        return new DerogationRegularisationService(r, s, UUID::randomUUID);
    }
}