package com.hemodialyse.backend.contexts.stock.infrastructure.acl.centres;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.contrat.CentresFournisSurJdbcDeTest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Centres actifs du Stock : seuls les centres actifs sont parcourus, par identifiant. Reprend, assertions inchangées, les tests de
 * {@code CentresActifsLegacy} (seule la construction de l'objet testé change, O4 lot B, ADR 0108).
 */
class CentresActifsFournisTest {

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

        assertEquals(List.of(new CentreId(premier), new CentreId(second)),
                new CentresActifsFournis(new CentresFournisSurJdbcDeTest(jdbc)).liste());
    }

    /** KB-ORG-09 corrigé (ADR 0115) : le Stock a la même définition que les autres, un centre sans état est actif. */
    @Test
    void un_centre_dont_l_etat_n_est_pas_renseigne_est_actif_pour_le_stock_comme_pour_les_autres() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE centers (id UUID PRIMARY KEY, actif BOOLEAN)");
        UUID actif = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID sansEtat = UUID.fromString("00000000-0000-0000-0000-000000000002");
        jdbc.update("INSERT INTO centers (id, actif) VALUES (?, TRUE)", actif);
        jdbc.update("INSERT INTO centers (id, actif) VALUES (?, NULL)", sansEtat);

        assertEquals(List.of(new CentreId(actif), new CentreId(sansEtat)),
                new CentresActifsFournis(new CentresFournisSurJdbcDeTest(jdbc)).liste());
    }
}
