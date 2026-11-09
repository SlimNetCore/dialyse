package com.hemodialyse.backend.contexts.stock.infrastructure.acl.centres;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CentresFournis;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Horloge du Stock : le jour d'un centre est celui de son fuseau, tel que la plateforme le fournit ({@link CentresFournis}) ; centre
 * inconnu, fuseau absent ou illisible, c'est UTC (comportement historique du Stock). L'instant est toujours en UTC. Remplace
 * {@code HorlogeDesCentresLegacy} (SQL direct).
 */
public class HorlogeDesCentresFournie implements HorlogeDesCentres {

    private final CentresFournis centres;
    private final Clock horloge;

    public HorlogeDesCentresFournie(CentresFournis centres, Clock horloge) {
        this.centres = centres;
        this.horloge = horloge;
    }

    @Override
    public OffsetDateTime maintenant() {
        return OffsetDateTime.now(horloge).withOffsetSameInstant(ZoneOffset.UTC);
    }

    @Override
    public LocalDate aujourdHui(CentreId centre) {
        return LocalDate.now(horloge.withZone(centres.fuseauHoraireDe(centre.valeur()).orElse(ZoneOffset.UTC)));
    }
}
