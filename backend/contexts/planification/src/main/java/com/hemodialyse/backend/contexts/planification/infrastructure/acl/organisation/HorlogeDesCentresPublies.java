package com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.planification.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * ACL vers le langage publié d'Organisation (organisation.v1, O4 lot A, ADR 0107) : le fuseau d'un centre est celui de sa fiche (identifiant
 * IANA) ; centre inconnu, fuseau absent ou illisible, le fuseau par défaut du module ({@code Africa/Algiers}, ADR 0054), choix de la
 * Planification. « Aujourd'hui » est le jour de ce fuseau, jamais celui d'UTC (KB-PLN-32). Remplace {@code HorlogeDesCentresLegacy}.
 */
public class HorlogeDesCentresPublies implements HorlogeDesCentres {

    private final AnnuaireDesCentres centres;
    private final Clock horloge;
    private final ZoneId fuseauParDefaut;

    public HorlogeDesCentresPublies(AnnuaireDesCentres centres, Clock horloge, ZoneId fuseauParDefaut) {
        this.centres = centres;
        this.horloge = horloge;
        this.fuseauParDefaut = fuseauParDefaut;
    }

    @Override
    public LocalDate aujourdhui(CentreId centre) {
        return LocalDate.now(horloge.withZone(fuseauDe(centre)));
    }

    @Override
    public LocalDate jourDe(CentreId centre, Instant instant) {
        return instant.atZone(fuseauDe(centre)).toLocalDate();
    }

    ZoneId fuseauDe(CentreId centre) {
        return centres.fuseauHoraireDe(centre.valeur()).orElse(fuseauParDefaut);
    }
}
