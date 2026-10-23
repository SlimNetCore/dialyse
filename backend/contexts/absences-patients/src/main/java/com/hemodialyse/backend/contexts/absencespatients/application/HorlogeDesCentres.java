package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * « Aujourd'hui » d'un centre : la date du jour dans le fuseau du centre, pas en UTC (décision 7 : une déclaration du jour entre
 * minuit et 1 h n'est plus refusée). Les instants restent absolus.
 */
public class HorlogeDesCentres {

    private final Clock horloge;
    private final FuseauxDesCentres fuseaux;

    public HorlogeDesCentres(Clock horloge, FuseauxDesCentres fuseaux) {
        this.horloge = horloge;
        this.fuseaux = fuseaux;
    }

    /** Tous les centres en UTC (tests). */
    public static HorlogeDesCentres enUtc(Clock horloge) {
        return new HorlogeDesCentres(horloge, centre -> ZoneOffset.UTC);
    }

    public LocalDate aujourdhui(CentreId centre) {
        return LocalDate.now(horloge.withZone(fuseaux.de(centre)));
    }

    public Instant maintenant() {
        return Instant.now(horloge);
    }
}
