package com.hemodialyse.backend.contexts.stock.infrastructure.acl.legacycentres;

import com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Jour métier d'un centre : le jour de son fuseau horaire ({@code centers.fuseau_horaire}), UTC par défaut ou si le fuseau est
 * illisible ; l'instant reste en UTC.
 */
class HorlogeDesCentresLegacyTest {

    /**
     * 23 h 30 UTC : déjà le lendemain à Alger (UTC+1), encore la veille à Montréal (UTC−4 en octobre).
     */
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-10-23T23:30:00Z"), ZoneOffset.UTC);

    private JdbcTemplate jdbc;
    private HorlogeDesCentres horloge;

    @BeforeEach
    void preparer() {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE centers (id UUID PRIMARY KEY, fuseau_horaire VARCHAR(50))");
        horloge = new HorlogeDesCentresLegacy(jdbc, HORLOGE);
    }

    private CentreId centre(String fuseau) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO centers (id, fuseau_horaire) VALUES (?, ?)", id, fuseau);
        return new CentreId(id);
    }

    @Test
    void le_jour_d_un_centre_est_celui_de_son_fuseau() {
        assertEquals(LocalDate.of(2026, 10, 24), horloge.aujourdHui(centre("Africa/Algiers")));
        assertEquals(LocalDate.of(2026, 10, 23), horloge.aujourdHui(centre("America/Montreal")));
    }

    @Test
    void sans_fuseau_ou_avec_un_fuseau_illisible_le_jour_est_celui_d_utc() {
        assertEquals(LocalDate.of(2026, 10, 23), horloge.aujourdHui(centre(null)));
        assertEquals(LocalDate.of(2026, 10, 23), horloge.aujourdHui(centre("  ")));
        assertEquals(LocalDate.of(2026, 10, 23), horloge.aujourdHui(centre("Nulle/Part")));
        assertEquals(LocalDate.of(2026, 10, 23), horloge.aujourdHui(new CentreId(UUID.randomUUID())));
    }

    @Test
    void l_instant_reste_en_utc() {
        assertEquals(Instant.parse("2026-10-23T23:30:00Z"), horloge.maintenant().toInstant());
        assertEquals(ZoneOffset.UTC, horloge.maintenant().getOffset());
    }

    @Test
    void l_horloge_uniforme_donne_le_meme_jour_a_tous_les_centres() {
        HorlogeDesCentres uniforme = HorlogeDesCentres.uniforme(HORLOGE);
        assertEquals(LocalDate.of(2026, 10, 23), uniforme.aujourdHui(new CentreId(UUID.randomUUID())));
    }
}

