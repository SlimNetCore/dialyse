package com.hemodialyse.backend.contexts.seance.infrastructure.acl;

import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyorganisation.CentresARappelerJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** ACL des centres sur H2 (mode PostgreSQL) : tous les centres de la table {@code centers}, sans filtre, lecture seule. */
class CentresARappelerJdbcTest {

    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:seance-centres-a-rappeler;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE IF NOT EXISTS centers (id UUID PRIMARY KEY, name VARCHAR(255))");
        jdbc.update("DELETE FROM centers");
    }

    @Test
    void should_list_every_centre_of_the_solution() {
        UUID a = new UUID(0, 0xA);
        UUID b = new UUID(0, 0xB);
        jdbc.update("INSERT INTO centers (id, name) VALUES (?, 'A')", a);
        jdbc.update("INSERT INTO centers (id, name) VALUES (?, 'B')", b);

        assertThat(new CentresARappelerJdbc(jdbc).tous()).containsExactlyInAnyOrder(new CentreId(a), new CentreId(b));
    }

    @Test
    void should_list_nothing_when_there_is_no_centre_and_never_write() {
        assertThat(new CentresARappelerJdbc(jdbc).tous()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM centers", Integer.class)).isZero();
    }
}
