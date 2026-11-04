package com.hemodialyse.backend.infrastructure.acl.centres;

import com.hemodialyse.backend.application.centre.JourDuCentre;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * ACL vers les centres (Organisation, encore lue en SQL) : le fuseau d'un centre est {@code centers.fuseau_horaire} ; centre
 * inconnu, fuseau absent, vide ou illisible : le fuseau par défaut ({@code app.planification.fuseau-par-defaut}, Africa/Algiers).
 * LECTURE DIRECTE TRANSITOIRE de la table {@code centers} : à remplacer par {@code organisation.v1} (jalon O4), au plus tard avec
 * PC5 ; inscrite, nommée et datée, dans la liste blanche de {@code TableDeLOrganisationTest}.
 */
@Component
public class JourDuCentreLegacy implements JourDuCentre {

    private final Function<UUID, Optional<String>> fuseaux;
    private final Clock horloge;
    private final ZoneId fuseauParDefaut;

    @Autowired
    public JourDuCentreLegacy(JdbcTemplate jdbc,
                              @Value("${app.planification.fuseau-par-defaut:Africa/Algiers}") String fuseauParDefaut) {
        this(centre -> jdbc.queryForList("SELECT fuseau_horaire FROM centers WHERE id = ?", String.class, centre)
                .stream().filter(f -> f != null && !f.isBlank()).findFirst(), Clock.systemUTC(), ZoneId.of(fuseauParDefaut));
    }

    /** Pour les tests : les fuseaux et l'horloge sont fournis. */
    public JourDuCentreLegacy(Function<UUID, Optional<String>> fuseaux, Clock horloge, ZoneId fuseauParDefaut) {
        this.fuseaux = fuseaux;
        this.horloge = horloge;
        this.fuseauParDefaut = fuseauParDefaut;
    }

    @Override
    public LocalDate aujourdhui(UUID centre) {
        return LocalDate.now(horloge.withZone(fuseauDe(centre)));
    }

    ZoneId fuseauDe(UUID centre) {
        if (centre == null) return fuseauParDefaut;
        String identifiant = fuseaux.apply(centre).orElse(null);
        if (identifiant == null) return fuseauParDefaut;
        try {
            return ZoneId.of(identifiant.trim());
        } catch (DateTimeException e) {
            return fuseauParDefaut;
        }
    }
}
