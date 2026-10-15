package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.domain.journal.JournalRepository;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.JournalComptableSpringData;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.JournalRepositoryJpa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrat des journaux sur un PostgreSQL 16 RÉEL (même suite que {@link JournalContrat}). Base JETABLE uniquement.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${comptabilite.pg16.url}",
        "spring.datasource.username=${comptabilite.pg16.user:comptabilite_test}",
        "spring.datasource.password=${comptabilite.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "comptabilite.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JournalPostgresContractIT extends JournalContrat {

    @Autowired
    private JournalComptableSpringData donnees;

    private JournalRepository adaptateur;

    @BeforeEach
    void creerLAdaptateurEtViderLaTable() {
        adaptateur = new JournalRepositoryJpa(donnees);
        donnees.deleteAll();
    }

    @Override
    protected JournalRepository repository() {
        return adaptateur;
    }
}
