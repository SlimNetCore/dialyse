package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.intervention.LectureInterventions;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.InterventionRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.InterventionSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LectureInterventionsJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LigneDeCoutSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.RectificationSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.TacheSpringData;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrat des interventions exécuté contre l'adapter JPA sur H2 (mode PostgreSQL). Chaque appel de port s'exécute dans sa
 * propre transaction. La variante PostgreSQL 16 réelle est {@code InterventionPostgresContractIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gmao-contrat-intervention;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InterventionJpaContratTest extends InterventionContrat {

    @Autowired
    private InterventionSpringData interventions;
    @Autowired
    private LigneDeCoutSpringData lignes;
    @Autowired
    private RectificationSpringData rectifications;
    @Autowired
    private TacheSpringData taches;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private InterventionRepositoryJpa repository;
    private LectureInterventionsJpa lecture;

    @BeforeEach
    void creerLesAdaptersEtViderLesTables() {
        repository = new InterventionRepositoryJpa(interventions, lignes, rectifications, taches);
        lecture = new LectureInterventionsJpa(interventions, repository);
        taches.deleteAll();
        rectifications.deleteAll();
        lignes.deleteAll();
        interventions.deleteAll();
    }

    @Override
    protected InterventionRepository repository() {
        return repository;
    }

    @Override
    protected LectureInterventions lecture() {
        return lecture;
    }

    @org.junit.jupiter.api.Test
    void should_make_a_second_transaction_wait_for_the_lock_taken_to_load_for_modification() throws Exception {
        com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention intervention = planifier(CENTRE_A, EQUIPEMENT_1, T0);
        repository.enregistrer(intervention);

        VerrouDeLIntervention.verifier(id -> repository.chargerPourModification(id),
                new org.springframework.transaction.support.TransactionTemplate(transactionManager), intervention.id());
    }

    @Override
    protected void supprimerLogiquement(InterventionId id) {
        jdbc.update("UPDATE gmao_interventions SET deleted_at = ? WHERE id = ?",
                java.sql.Timestamp.from(java.time.Instant.parse("2026-02-01T00:00:00Z")), id.valeur());
    }
}
