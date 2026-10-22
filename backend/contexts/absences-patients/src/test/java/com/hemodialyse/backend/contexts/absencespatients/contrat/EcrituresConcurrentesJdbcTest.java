package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.AbsencesJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

/**
 * Contrat de concurrence des écritures d'absences sur H2 (mode PostgreSQL, vraies transactions). Variante PostgreSQL 16 :
 * {@code EcrituresConcurrentesPostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:absences-contrat-concurrence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EcrituresConcurrentesJdbcTest extends EcrituresConcurrentesContrat {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void creerLaTableEtViderLesLignes() {
        new AbsencesJdbcContratDeTable(jdbc).preparer();
    }

    @Override
    protected com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository absences() {
        return new AbsencesJdbc(jdbc);
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    /** Table et nettoyage communs avec le contrat JDBC, hors héritage. */
    private static final class AbsencesJdbcContratDeTable extends AbsencesJdbcContrat {
        private final JdbcTemplate jdbc;

        AbsencesJdbcContratDeTable(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        void preparer() {
            creerLaTable();
            viderLesLignesDeTest();
        }

        @Override
        protected JdbcTemplate jdbc() {
            return jdbc;
        }
    }
}
