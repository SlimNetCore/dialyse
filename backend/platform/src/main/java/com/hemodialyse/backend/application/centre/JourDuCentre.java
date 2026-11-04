package com.hemodialyse.backend.application.centre;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * « Aujourd'hui » dans le fuseau d'un centre (PC4, décision du 2026-11-04, KB-PAT-47) : le fuseau est
 * {@code centers.fuseau_horaire} ; absent ou illisible, le fuseau par défaut ({@code app.planification.fuseau-par-defaut},
 * Africa/Algiers). Jamais le jour de la JVM ni d'UTC. Utilisé par le tableau de bord (échéance des attestations et des PEC) et par
 * la création d'une séance sans date.
 */
@Component
public class JourDuCentre {

    private final JdbcTemplate jdbc;
    private final Clock horloge;
    private final ZoneId fuseauParDefaut;

    @org.springframework.beans.factory.annotation.Autowired
    public JourDuCentre(JdbcTemplate jdbc,
                        @Value("${app.planification.fuseau-par-defaut:Africa/Algiers}") String fuseauParDefaut) {
        this(jdbc, Clock.systemUTC(), ZoneId.of(fuseauParDefaut));
    }

    JourDuCentre(JdbcTemplate jdbc, Clock horloge, ZoneId fuseauParDefaut) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.fuseauParDefaut = fuseauParDefaut;
    }

    public LocalDate aujourdhui(UUID centre) {
        return LocalDate.now(horloge.withZone(fuseauDe(centre)));
    }

    ZoneId fuseauDe(UUID centre) {
        String identifiant = centre == null ? null
                : jdbc.queryForList("SELECT fuseau_horaire FROM centers WHERE id = ?", String.class, centre)
                .stream().filter(f -> f != null && !f.isBlank()).findFirst().orElse(null);
        if (identifiant == null) return fuseauParDefaut;
        try {
            return ZoneId.of(identifiant.trim());
        } catch (DateTimeException e) {
            return fuseauParDefaut;
        }
    }
}
