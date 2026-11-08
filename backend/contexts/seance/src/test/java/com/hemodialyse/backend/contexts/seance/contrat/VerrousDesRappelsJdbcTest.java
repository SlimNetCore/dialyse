package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.VerrousDesRappelsJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

/** Verrou du rappel sur H2 : sans effet (H2 n'a pas de verrou consultatif). Variante PostgreSQL 16 : {@code VerrousDesRappelsPostgresContractIT}. */
class VerrousDesRappelsJdbcTest {

    @Test
    void should_do_nothing_and_never_fail_on_h2() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:seance-verrous-rappels;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        var verrous = new VerrousDesRappelsJdbc(jdbc);

        assertThatCode(() -> {
            verrous.verrouiller(new CentreId(new UUID(0, 0xA)), LocalDate.of(2026, 6, 15));
            verrous.verrouiller(new CentreId(new UUID(0, 0xA)), LocalDate.of(2026, 6, 15));
        }).doesNotThrowAnyException();
    }
}
