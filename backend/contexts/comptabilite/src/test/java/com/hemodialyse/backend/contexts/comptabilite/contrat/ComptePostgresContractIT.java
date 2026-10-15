package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.application.plan.LecturePlan;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteRepository;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.CompteComptableSpringData;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.CompteRepositoryJpa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Contrat du plan comptable sur un PostgreSQL 16 RÉEL (même suite que {@link CompteContrat}). Base JETABLE uniquement (voir
 * backend/scripts/comptabilite-pg16-rejeu.sh) : le test vide la table.
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
class ComptePostgresContractIT extends CompteContrat {

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
