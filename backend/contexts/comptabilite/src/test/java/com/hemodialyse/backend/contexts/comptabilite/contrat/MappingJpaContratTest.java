package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.MappingComptableRepositoryJpa;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.MappingComptableSpringData;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Contrat du mapping exécuté contre l'adaptateur JPA sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code MappingPostgresContractIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:comptabilite-contrat-mapping;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MappingJpaContratTest extends MappingContrat {

    @Autowired
    private MappingComptableSpringData donnees;
    @Autowired
    private JdbcTemplate jdbc;

    private MappingComptableRepository adaptateur;

    @BeforeEach
    void creerLAdaptateurEtViderLaTable() {
        adaptateur = new MappingComptableRepositoryJpa(donnees);
        donnees.deleteAll();
    }

    @Override
    protected MappingComptableRepository repository() {
        return adaptateur;
    }

    @Override
    protected void ligneHeritee(CentreId centre) {
        jdbc.update("INSERT INTO mapping_comptable (id, center_id, compte_ventes, compte_client_patient, compte_client_autre, "
                        + "compte_banque, compte_caisse) VALUES (?, ?, '7060', '411100', '411500', '512', '530')", UUID.randomUUID(),
                centre.valeur());
    }
}
