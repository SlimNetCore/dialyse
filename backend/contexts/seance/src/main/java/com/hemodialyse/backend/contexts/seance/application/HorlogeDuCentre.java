package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * « Aujourd'hui » et « maintenant » de la Séance. Le jour est celui du FUSEAU DU CENTRE (Africa/Algiers par défaut, la table des
 * centres n'a pas de fuseau), pas celui de la JVM (KB-SEA-01 : le scan lisait la date de la JVM, la création sans date celle du
 * centre). Écart décidé, à confirmer avec les métiers.
 */
public class HorlogeDuCentre {
    private final Clock horloge;
    private final ZoneId fuseau;

    public HorlogeDuCentre(Clock horloge, ZoneId fuseau) {
        this.horloge = horloge;
        this.fuseau = fuseau;
    }

    public static HorlogeDuCentre enUtc(Clock horloge) {
        return new HorlogeDuCentre(horloge, ZoneOffset.UTC);
    }

    public LocalDate aujourdhui(CentreId centre) {
        return LocalDate.now(horloge.withZone(fuseau));
    }

    public Instant maintenant() {
        return Instant.now(horloge);
    }
}
