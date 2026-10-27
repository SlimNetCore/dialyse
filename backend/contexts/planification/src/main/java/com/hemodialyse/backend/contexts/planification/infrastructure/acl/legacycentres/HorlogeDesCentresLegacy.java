package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacycentres;

import com.hemodialyse.backend.contexts.planification.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * ACL vers les centres (Identité et accès, encore legacy) : le fuseau d'un centre est {@code centers.fuseau_horaire} (identifiant
 * IANA) ; absent ou illisible, le fuseau par défaut du module ({@code Africa/Algiers}, ADR 0054). « Aujourd'hui » est le jour de ce
 * fuseau, jamais celui d'UTC (KB-PLN-32).
 */
public class HorlogeDesCentresLegacy implements HorlogeDesCentres {

    private final JdbcTemplate jdbc;
    private final Clock horloge;
    private final ZoneId fuseauParDefaut;

    public HorlogeDesCentresLegacy(JdbcTemplate jdbc, Clock horloge, ZoneId fuseauParDefaut) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.fuseauParDefaut = fuseauParDefaut;
    }

    @Override
    public LocalDate aujourdhui(CentreId centre) {
        return LocalDate.now(horloge.withZone(fuseauDe(centre)));
    }

    @Override
    public LocalDate jourDe(CentreId centre, java.time.Instant instant) {
        return instant.atZone(fuseauDe(centre)).toLocalDate();
    }

    ZoneId fuseauDe(CentreId centre) {
        String identifiant = jdbc.queryForList("SELECT fuseau_horaire FROM centers WHERE id = ?", String.class, centre.valeur())
                .stream().filter(f -> f != null && !f.isBlank()).findFirst().orElse(null);
        if (identifiant == null) {
            return fuseauParDefaut;
        }
        try {
            return ZoneId.of(identifiant.trim());
        } catch (DateTimeException e) {
            return fuseauParDefaut;
        }
    }
}
