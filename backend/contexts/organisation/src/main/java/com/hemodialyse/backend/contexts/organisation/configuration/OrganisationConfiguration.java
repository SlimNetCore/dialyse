package com.hemodialyse.backend.contexts.organisation.configuration;

import com.hemodialyse.backend.contexts.organisation.application.OrganisationService;
import com.hemodialyse.backend.contexts.organisation.application.OrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.domain.CentreRepository;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauRepository;
import com.hemodialyse.backend.contexts.organisation.domain.SalleRepository;
import com.hemodialyse.backend.contexts.organisation.domain.SocieteRepository;
import com.hemodialyse.backend.contexts.organisation.application.ReferentielsOrganisationService;
import com.hemodialyse.backend.contexts.organisation.application.ReferentielsOrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.domain.MachineRepository;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinRepository;
import com.hemodialyse.backend.contexts.organisation.domain.ProtocoleRepository;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.CentresJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.CreneauxJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.MachinesJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.MedecinsJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.ProtocolesJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.SallesJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.SocietesJdbc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class OrganisationConfiguration {
    @Bean
    public SocieteRepository societeRepository(JdbcTemplate jdbc) {
        return new SocietesJdbc(jdbc);
    }

    @Bean
    public CentreRepository centreRepository(JdbcTemplate jdbc) {
        return new CentresJdbc(jdbc);
    }

    @Bean
    public SalleRepository salleRepository(JdbcTemplate jdbc) {
        return new SallesJdbc(jdbc);
    }

    @Bean
    public CreneauRepository creneauRepository(JdbcTemplate jdbc) {
        return new CreneauxJdbc(jdbc);
    }

    @Bean
    public MedecinRepository medecinRepository(JdbcTemplate jdbc) {
        return new MedecinsJdbc(jdbc);
    }

    @Bean
    public MachineRepository machineRepository(JdbcTemplate jdbc) {
        return new MachinesJdbc(jdbc);
    }

    @Bean
    public ProtocoleRepository protocoleRepository(JdbcTemplate jdbc) {
        return new ProtocolesJdbc(jdbc);
    }

    @Bean
    public OrganisationUseCases organisationUseCases(SocieteRepository s, CentreRepository c, SalleRepository sl, CreneauRepository cr) {
        return new OrganisationService(s, c, sl, cr);
    }

    @Bean
    public ReferentielsOrganisationUseCases referentielsOrganisationUseCases(MedecinRepository m, MachineRepository ma, ProtocoleRepository p) {
        return new ReferentielsOrganisationService(m, ma, p);
    }
}
