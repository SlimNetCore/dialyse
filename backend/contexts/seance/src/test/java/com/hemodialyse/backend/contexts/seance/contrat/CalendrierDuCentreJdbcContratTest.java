package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.CalendrierDuCentreJdbc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Contrat du calendrier du centre sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code CalendrierDuCentrePostgresContractIT}. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:seance-calendrier;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CalendrierDuCentreJdbcContratTest extends CalendrierDuCentreContrat {
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS center_holiday (id UUID PRIMARY KEY, center_id UUID NOT NULL, day_date DATE NOT NULL, "
                + "label VARCHAR(255), UNIQUE (center_id, day_date))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS center_closure_day (id UUID PRIMARY KEY, center_id UUID NOT NULL, day_date DATE NOT NULL, "
                + "reason VARCHAR(255), UNIQUE (center_id, day_date))");
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    private void vider() {
        jdbc.update("DELETE FROM center_holiday WHERE center_id IN (?, ?)", A.valeur(), B.valeur());
        jdbc.update("DELETE FROM center_closure_day WHERE center_id IN (?, ?)", A.valeur(), B.valeur());
    }

    @Override
    protected CalendrierDuCentre calendrier() {
        return new CalendrierDuCentreJdbc(jdbc);
    }
}
