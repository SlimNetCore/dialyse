package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.application.plan.LecturePlan;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteRepository;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.CompteComptableSpringData;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.CompteRepositoryJpa;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrat du plan comptable exécuté contre l'adaptateur JPA sur H2 (mode PostgreSQL). Chaque appel de port s'exécute dans sa
 * propre transaction. La variante PostgreSQL 16 réelle est {@code ComptePostgresContractIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:comptabilite-contrat-compte;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CompteJpaContratTest extends CompteContrat {

    @Autowired
    private CompteComptableSpringData donnees;

    private CompteRepositoryJpa adaptateur;

    @BeforeEach
    void creerLAdaptateurEtViderLaTable() {
        adaptateur = new CompteRepositoryJpa(donnees);
        donnees.deleteAll();
    }

    @Override
    protected CompteRepository repository() {
        return adaptateur;
    }

    @Override
    protected LecturePlan lecture() {
        return adaptateur;
    }
}
