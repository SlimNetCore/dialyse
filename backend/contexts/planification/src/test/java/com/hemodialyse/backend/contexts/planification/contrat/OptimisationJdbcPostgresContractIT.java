package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.CalendriersDesPropositions;
import com.hemodialyse.backend.contexts.planification.domain.DeplacementsTemporairesRepository;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;
import com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.CalendriersDesPropositionsJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.DeplacementsTemporairesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.ExecutionsJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrat de la persistance de l'optimisation sur un PostgreSQL 16 RÉEL (schéma Flyway V1..Vn) : mêmes tests que H2. Base JETABLE
 * uniquement : {@code -Dplanification.pg16.url=jdbc:postgresql://…} (script {@code planification-p8-pg16-rejeu.sh}).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${planification.pg16.url}",
        "spring.datasource.username=${planification.pg16.user:planification_test}",
        "spring.datasource.password=${planification.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "planification.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OptimisationJdbcPostgresContractIT extends OptimisationContrat {

    @Autowired
    private JdbcTemplate jdbc;

    private ExecutionsRepository executions;
    private DeplacementsTemporairesRepository deplacements;
    private CalendriersDesPropositions calendriers;

    @BeforeEach
    void composer() {
        JsonMapper mapper = JsonMapper.builder().build();
        executions = new ExecutionsJdbc(jdbc, mapper);
        deplacements = new DeplacementsTemporairesJdbc(jdbc);
        calendriers = new CalendriersDesPropositionsJdbc(jdbc, mapper);
    }

    @Override
    ExecutionsRepository executions() {
        return executions;
    }

    @Override
    DeplacementsTemporairesRepository deplacements() {
        return deplacements;
    }

    @Override
    CalendriersDesPropositions calendriers() {
        return calendriers;
    }

    @Override
    void nettoyer() {
        SchemaDeLOptimisationH2.vider(jdbc, JeuDePlaces.CENTRE.valeur(), AUTRE_CENTRE.valeur());
    }
}
