package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.MappingComptableRepositoryJpa;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.MappingComptableSpringData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Contrat du mapping sur un PostgreSQL 16 RÉEL (même suite que {@link MappingContrat}). Base JETABLE uniquement : les lignes
 * de test sont supprimées avant chaque test.
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
class MappingPostgresContractIT extends MappingContrat {

    @Autowired
    private MappingComptableSpringData donnees;
    @Autowired
    private JdbcTemplate jdbc;

    private MappingComptableRepository adaptateur;

    @BeforeEach
    void creerLAdaptateurEtViderLesLignesDeTest() {
        adaptateur = new MappingComptableRepositoryJpa(donnees);
        jdbc.update("DELETE FROM mapping_comptable WHERE center_id IN (?, ?)", CENTRE_A.valeur(), CENTRE_B.valeur());
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
