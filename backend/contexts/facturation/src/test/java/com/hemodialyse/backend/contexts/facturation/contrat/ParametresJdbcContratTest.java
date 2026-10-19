package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturationRepository;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.ParametresDeFacturationJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrat des paramètres de facturation exécuté contre l'adaptateur JDBC sur H2 (mode PostgreSQL). La table
 * {@code facturation_settings}, tenue par la migration V1, est créée ici avec ses colonnes. Variante PostgreSQL 16 :
 * {@code ParametresPostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:facturation-contrat-parametres;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ParametresJdbcContratTest extends ParametresDeFacturationContrat {

    @Autowired
    private JdbcTemplate jdbc;

    private ParametresDeFacturationRepository adaptateur;

    @BeforeEach
    void creerLaTableEtLAdaptateur() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS facturation_settings (center_id UUID NOT NULL PRIMARY KEY, "
                + "tva_rate NUMERIC(5, 2) DEFAULT 0.00, code_format VARCHAR(120) NOT NULL, "
                + "regroupement_multi_forfait BOOLEAN DEFAULT TRUE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE, "
                + "updated_by VARCHAR(120))");
        jdbc.update("DELETE FROM facturation_settings");
        adaptateur = new ParametresDeFacturationJdbc(jdbc);
    }

    @Override
    protected ParametresDeFacturationRepository repository() {
        return adaptateur;
    }
}
