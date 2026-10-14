package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.piecejointe.InterventionsHotes;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.InterventionHote;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.InterventionSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.InterventionsHotesJpa;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/** Contrat des interventions hôtes exécuté contre l'adapter JPA sur H2 : les lignes sont posées en SQL, comme le ferait G4. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gmao-contrat-hote;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InterventionsHotesJpaContratTest extends InterventionsHotesContrat {

    @Autowired
    private InterventionSpringData interventions;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private InterventionsHotesJpa hotes;

    @BeforeEach
    void creerLAdapterEtViderLaTable() {
        hotes = new InterventionsHotesJpa(interventions);
        interventions.deleteAll();
    }

    @Override
    protected InterventionsHotes hotes() {
        return hotes;
    }

    @Test
    void should_make_a_second_transaction_wait_for_the_lock_of_the_first() throws Exception {
        InterventionHote hote = new InterventionHote(new InterventionId(UUID.randomUUID()), CENTRE, StatutIntervention.PLANIFIEE, false);
        semer(hote);

        VerrouDeLIntervention.verifier(hotes, new TransactionTemplate(transactionManager), hote.id());
    }

    @Override
    protected void semer(InterventionHote hote) {
        Instant t = Instant.parse("2026-03-10T08:00:00Z");
        jdbc.update("INSERT INTO gmao_interventions (id, equipement_id, centre_id, type, statut, date_debut, description, "
                        + "date_creation, cree_par, deleted_at) VALUES (?, ?, ?, 'CURATIVE', ?, ?, 'Panne', ?, ?, ?)",
                hote.id().valeur(), UUID.randomUUID(), hote.centre().valeur(), hote.statut().name(), java.sql.Timestamp.from(t),
                java.sql.Timestamp.from(t), UUID.randomUUID(), hote.supprimee() ? java.sql.Timestamp.from(t) : null);
    }
}
