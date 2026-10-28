package com.hemodialyse.backend.contexts.stock.infrastructure.acl.legacycentres;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ACL vers les centres : seuls les centres actifs sont parcourus, par identifiant.
 */
class CentresActifsLegacyTest {

    @Test
    void les_centres_inactifs_sont_exclus_et_les_actifs_sont_ordonnes_par_identifiant() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE centers (id UUID PRIMARY KEY, actif BOOLEAN NOT NULL)");
        UUID premier = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID inactif = UUID.fromString("00000000-0000-0000-0000-000000000003");
        jdbc.update("INSERT INTO centers (id, actif) VALUES (?, TRUE)", second);
        jdbc.update("INSERT INTO centers (id, actif) VALUES (?, FALSE)", inactif);
        jdbc.update("INSERT INTO centers (id, actif) VALUES (?, TRUE)", premier);

        assertEquals(List.of(new CentreId(premier), new CentreId(second)), new CentresActifsLegacy(jdbc).liste());
    }
}
