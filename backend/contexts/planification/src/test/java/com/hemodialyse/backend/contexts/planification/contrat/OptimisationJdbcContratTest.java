package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.CalendriersDesPropositions;
import com.hemodialyse.backend.contexts.planification.domain.DeplacementsTemporairesRepository;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.CalendriersDesPropositionsJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.DeplacementsTemporairesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.ExecutionsJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrat de la persistance de l'optimisation sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code OptimisationJdbcPostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:planification-contrat-optimisation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OptimisationJdbcContratTest extends OptimisationContrat {

    @Autowired
    private JdbcTemplate jdbc;

    private JsonMapper mapperDuContrat;
    private DeplacementsTemporairesRepository deplacements;
    private CalendriersDesPropositions calendriers;

    @BeforeEach
    void composer() {
        JsonMapper mapper = JsonMapper.builder().build();
        mapperDuContrat = mapper;
        deplacements = new DeplacementsTemporairesJdbc(jdbc);
        calendriers = new CalendriersDesPropositionsJdbc(jdbc, mapper);
    }

    @Override
    ExecutionsRepository executionsDeLInstance(String instance) {
        return new ExecutionsJdbc(jdbc, mapperDuContrat, instance);
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
        SchemaDeLOptimisationH2.creer(jdbc);
        SchemaDeLOptimisationH2.vider(jdbc, com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.CENTRE.valeur(),
                AUTRE_CENTRE.valeur());
    }
}
