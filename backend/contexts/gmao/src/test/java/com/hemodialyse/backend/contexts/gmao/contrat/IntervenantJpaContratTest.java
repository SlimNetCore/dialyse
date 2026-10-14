package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.intervenant.LectureIntervenants;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantRepository;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.IntervenantRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.IntervenantSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LectureIntervenantsJpa;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrat des intervenants exécuté contre l'adapter JPA sur H2 (mode PostgreSQL). Chaque appel de port s'exécute dans sa
 * propre transaction. La variante PostgreSQL 16 réelle est {@code IntervenantPostgresContractIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gmao-contrat-intervenant;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IntervenantJpaContratTest extends IntervenantContrat {

    @Autowired
    private IntervenantSpringData donnees;

    private IntervenantRepository repository;
    private LectureIntervenants lecture;

    @BeforeEach
    void creerLesAdaptersEtViderLaTable() {
        repository = new IntervenantRepositoryJpa(donnees);
        lecture = new LectureIntervenantsJpa(donnees);
        donnees.deleteAll();
    }

    @Override
    protected IntervenantRepository repository() {
        return repository;
    }

    @Override
    protected LectureIntervenants lecture() {
        return lecture;
    }
}
