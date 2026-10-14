package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.LecturePlans;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceRepository;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LecturePlansJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PlanMaintenanceRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PlanMaintenanceSpringData;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Contrat exécuté contre l'adapter JPA sur H2 (mode PostgreSQL). Chaque appel de port s'exécute dans sa propre
 * transaction (aucune transaction de test englobante), comme en production hors use case. La variante PostgreSQL 16
 * (Testcontainers, ADR 0016) est à ajouter dès que Docker est disponible : mêmes tests, autre source de données.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gmao-contrat;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlanMaintenanceJpaContratTest extends PlanMaintenanceContrat {

    @Autowired
    private PlanMaintenanceSpringData donnees;
    @Autowired
    private JdbcTemplate jdbc;

    private PlanMaintenanceRepository repository;
    private LecturePlans lecture;

    @BeforeEach
    void creerLesAdaptersEtViderLaTable() {
        repository = new PlanMaintenanceRepositoryJpa(donnees);
        lecture = new LecturePlansJpa(donnees);
        donnees.deleteAll();
    }

    @Override
    protected PlanMaintenanceRepository repository() {
        return repository;
    }

    @Override
    protected LecturePlans lecture() {
        return lecture;
    }

    @Override
    protected void supprimerLogiquement(PlanMaintenanceId id) {
        jdbc.update("UPDATE gmao_plans_maintenance SET deleted_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-03-02T00:00:00Z")), id.valeur());
    }
}
