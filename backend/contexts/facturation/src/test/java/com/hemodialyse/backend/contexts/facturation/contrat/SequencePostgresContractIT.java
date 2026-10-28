package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SequenceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.SequenceDeFacturationJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrat de la séquence sur un PostgreSQL 16 RÉEL (table {@code facture_sequence} du schéma Flyway). Base JETABLE uniquement :
 * {@code -Dfacturation.pg16.url=jdbc:postgresql://…}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${facturation.pg16.url}",
        "spring.datasource.username=${facturation.pg16.user:facturation_test}",
        "spring.datasource.password=${facturation.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "facturation.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SequencePostgresContractIT extends SequenceContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void viderLesLignesDeTest() {
        jdbc.update("DELETE FROM facture_sequence WHERE center_id IN (?, ?)", CENTRE_A.valeur(), CENTRE_B.valeur());
    }

    @Override
    protected SequenceDeFacturation sequence() {
        return new SequenceDeFacturationJdbc(jdbc);
    }

    @Override
    protected void poser(CentreId centre, int annee, int valeur) {
        jdbc.update("INSERT INTO facture_sequence (center_id, seq_year, seq_value) VALUES (?, ?, ?)", centre.valeur(), annee, valeur);
    }
}
