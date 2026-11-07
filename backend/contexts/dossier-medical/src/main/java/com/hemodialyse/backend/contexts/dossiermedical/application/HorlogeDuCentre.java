package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * « Aujourd'hui » et « maintenant » du dossier médical. Le jour est celui du FUSEAU DU CENTRE (Africa/Algiers par défaut, la
 * table des centres n'a pas de fuseau), pas celui de la JVM (KB-MED-15 : la production force UTC, la date par défaut d'une
 * ordonnance entre 00:00 et 01:00 à Alger était celle de la veille). Écart décidé, à confirmer avec les métiers.
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
