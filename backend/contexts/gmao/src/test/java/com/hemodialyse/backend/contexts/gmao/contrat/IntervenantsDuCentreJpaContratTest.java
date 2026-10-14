package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.intervention.IntervenantsDuCentre;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantRepository;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.IntervenantRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.IntervenantSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.IntervenantsDuCentreJpa;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Contrat du port des intervenants du centre exécuté contre l'adapter JPA sur H2 (mode PostgreSQL). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gmao-contrat-intervenants-du-centre;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IntervenantsDuCentreJpaContratTest extends IntervenantsDuCentreContrat {

    @Autowired
    private IntervenantSpringData donnees;

    @BeforeEach
    void viderLaTable() {
        donnees.deleteAll();
    }

    @Override
    protected IntervenantRepository intervenants() {
        return new IntervenantRepositoryJpa(donnees);
    }

    @Override
    protected IntervenantsDuCentre port() {
        return new IntervenantsDuCentreJpa(donnees);
    }
}
