package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.VoletsMedicaux;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.VoletsMedicauxJdbc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Contrat des volets médicaux sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code VoletsMedicauxPostgresContractIT}. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:seance-volets;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VoletsMedicauxJdbcContratTest extends VoletsMedicauxContrat {
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS volet_medical (id UUID PRIMARY KEY, ajustements_therapeutiques TEXT, center_id UUID NOT NULL, "
                + "conclusion_medicale TEXT, created_at TIMESTAMP WITH TIME ZONE, examen_clinique TEXT, prescription TEXT, "
                + "resultats_biologiques TEXT, seance_id UUID NOT NULL UNIQUE, tolerance_seance TEXT, updated_at TIMESTAMP WITH TIME ZONE)");
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    private void vider() {
        jdbc.update("DELETE FROM volet_medical WHERE center_id IN (?, ?)", A.valeur(), B.valeur());
    }

    @Override
    protected VoletsMedicaux volets() {
        return new VoletsMedicauxJdbc(jdbc);
    }
}
