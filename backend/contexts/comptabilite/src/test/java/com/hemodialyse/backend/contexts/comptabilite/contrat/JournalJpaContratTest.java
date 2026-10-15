package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.domain.journal.JournalRepository;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.JournalComptableSpringData;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.JournalRepositoryJpa;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrat des journaux exécuté contre l'adaptateur JPA sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code JournalPostgresContractIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:comptabilite-contrat-journal;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JournalJpaContratTest extends JournalContrat {

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
