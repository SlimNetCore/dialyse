package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.piecejointe.InterventionsHotes;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.InterventionHote;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.InterventionSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.InterventionsHotesJpa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Contrat PostgreSQL 16 réel du port {@code InterventionsHotes} : la MÊME suite que le double en mémoire et l'adapter sur H2, plus la
 * preuve que le verrou {@code SELECT ... FOR UPDATE} fait attendre une seconde transaction (quota de 50 sous concurrence,
 * KB-GMAO-58). Les clés étrangères du schéma Flyway réel (centre, utilisateur, équipement) sont satisfaites par
 * {@link ReferentielPostgres}. Base JETABLE uniquement (voir backend/scripts/gmao-pg16-rejeu.sh).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${gmao.pg16.url}",
        "spring.datasource.username=${gmao.pg16.user:gmao_test}",
        "spring.datasource.password=${gmao.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "gmao.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InterventionsHotesPostgresContractIT extends InterventionsHotesContrat {

    @Autowired
    private InterventionSpringData interventions;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private InterventionsHotesJpa hotes;

    @BeforeEach
    void creerLAdapterEtViderLesTables() {
        hotes = new InterventionsHotesJpa(interventions);
        jdbc.execute("TRUNCATE gmao_interventions, gmao_lignes_cout_intervention, gmao_rectifications_intervention, gmao_taches_intervention CASCADE");
    }

    @Override
    protected InterventionsHotes hotes() {
        return hotes;
    }

    @Override
    protected void semer(InterventionHote hote) {
        UUID admin = UUID.fromString("99997600-0000-0000-0000-0000000000f1");
        UUID equipement = UUID.fromString("99997600-0000-0000-0000-0000000000e1");
        ReferentielPostgres.centre(jdbc, hote.centre().valeur());
        ReferentielPostgres.utilisateur(jdbc, admin);
        ReferentielPostgres.equipement(jdbc, equipement, hote.centre().valeur(), admin);
        Timestamp t = Timestamp.from(Instant.parse("2026-03-10T08:00:00Z"));
        jdbc.update("INSERT INTO gmao_interventions (id, equipement_id, centre_id, type, statut, date_debut, description, "
                        + "date_creation, cree_par, deleted_at) VALUES (?, ?, ?, 'CURATIVE', ?, ?, 'Panne', ?, ?, ?)",
                hote.id().valeur(), equipement, hote.centre().valeur(), hote.statut().name(), t, t, admin,
                hote.supprimee() ? t : null);
    }

    @Test
    void should_make_a_second_transaction_wait_for_the_lock_of_the_first_on_a_real_postgresql() throws Exception {
        InterventionHote hote = new InterventionHote(new InterventionId(UUID.randomUUID()), CENTRE, StatutIntervention.PLANIFIEE, false);
        semer(hote);

        VerrouDeLIntervention.verifier(hotes, new TransactionTemplate(transactionManager), hote.id());
    }
}
