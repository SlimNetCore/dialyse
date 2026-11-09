package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyorganisation;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * « Aujourd'hui » dans le fuseau d'un centre (SE7f, ADR 0100) : repris de {@code JourDuCentreLegacy} (plateforme, PC4), que le fournisseur
 * {@code JourDuCentreFourniLegacy} enveloppait. Le fuseau est {@code centers.fuseau_horaire} ; centre inconnu, fuseau absent, vide ou
 * illisible : le fuseau par défaut de la plateforme ({@code app.planification.fuseau-par-defaut}). Lecture transitoire de la table des
 * centres (Organisation), inscrite dans {@code TableDeLOrganisationTest} ; à remplacer par {@code organisation.v1} (jalon O4).
 */
public class JourDuCentreJdbc implements Function<UUID, LocalDate> {

    private final Function<UUID, Optional<String>> fuseaux;
    private final Clock horloge;
    private final ZoneId fuseauParDefaut;

    public JourDuCentreJdbc(JdbcTemplate jdbc, Clock horloge, ZoneId fuseauParDefaut) {
        this(centre -> jdbc.queryForList("SELECT fuseau_horaire FROM centers WHERE id = ?", String.class, centre)
                .stream().filter(f -> f != null && !f.isBlank()).findFirst(), horloge, fuseauParDefaut);
    }

    /** Pour les tests : les fuseaux et l'horloge sont fournis. */
    public JourDuCentreJdbc(Function<UUID, Optional<String>> fuseaux, Clock horloge, ZoneId fuseauParDefaut) {
        this.fuseaux = fuseaux;
        this.horloge = horloge;
        this.fuseauParDefaut = fuseauParDefaut;
    }

    @Override
    public LocalDate apply(UUID centre) {
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
