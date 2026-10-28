package com.hemodialyse.backend.contexts.stock.infrastructure.acl.legacycentres;

import com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * ACL vers les centres (contexte Identité et accès, encore legacy) : le fuseau d'un centre est {@code centers.fuseau_horaire}
 * (identifiant IANA, par exemple {@code Africa/Algiers}) ; absent ou illisible, c'est UTC (comportement historique). L'horloge
 * fournit l'instant, toujours en UTC.
 */
public class HorlogeDesCentresLegacy implements HorlogeDesCentres {

    private final JdbcTemplate jdbc;
    private final Clock horloge;

    public HorlogeDesCentresLegacy(JdbcTemplate jdbc, Clock horloge) {
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    @Override
    public OffsetDateTime maintenant() {
        return OffsetDateTime.now(horloge).withOffsetSameInstant(ZoneOffset.UTC);
    }

    @Override
    public LocalDate aujourdHui(CentreId centre) {
        return LocalDate.now(horloge.withZone(fuseauDe(centre)));
    }

    private ZoneId fuseauDe(CentreId centre) {
        String identifiant = jdbc.queryForList("SELECT fuseau_horaire FROM centers WHERE id = ?", String.class, centre.valeur())
                .stream().filter(f -> f != null && !f.isBlank()).findFirst().orElse(null);
        if (identifiant == null) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(identifiant.trim());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }
}

