package com.hemodialyse.backend.contexts.facturation.contrat;

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
 * Contrat du forfait appliqué sur un PostgreSQL 16 RÉEL (schéma Flyway V1..Vn) : {@code ForfaitsDuCentreParLeCatalogue} (RF2 ; type {@code numeric} du prix)
 * et la politique de forfait avec le vrai {@code CouvertureDesPatientsJdbc} (revue SE7c2, constat m1). Rejoué par
 * {@code backend/scripts/seance-se7c2-pg16-rejeu.sh}. Base JETABLE uniquement : {@code -Dfacturation.pg16.url=jdbc:postgresql://…}, mot de
 * passe par la variable d'environnement {@code FACTURATION_PG16_PASSWORD}.
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
class ForfaitAppliquePostgresContractIT extends ForfaitAppliqueJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void viderLesLignes() {
        viderLesLignesDeTest();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
