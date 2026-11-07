package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Function;

/**
 * « Aujourd'hui » et « maintenant » de la Séance. Le jour est celui du FUSEAU DU CENTRE ({@code centers.fuseau_horaire}, fuseau par
 * défaut de la plateforme si absent), pas celui de la JVM (KB-SEA-01 : le scan lisait la date de la JVM, la création sans date celle du
 * centre, PC4). Le fuseau du centre est fourni par le langage publié {@code JourDuCentreFourni} ; les tests fournissent un fuseau fixe.
 */
public class HorlogeDuCentre {
    private final Clock horloge;
    private final Function<UUID, LocalDate> jourDuCentre;

    /** Fuseau unique pour tous les centres (tests). */
    public HorlogeDuCentre(Clock horloge, ZoneId fuseau) {
        this(horloge, centre -> LocalDate.now(horloge.withZone(fuseau)));
    }

    /** Jour calculé par centre (production : fuseau de chaque centre). */
    public HorlogeDuCentre(Clock horloge, Function<UUID, LocalDate> jourDuCentre) {
        this.horloge = horloge;
        this.jourDuCentre = jourDuCentre;
    }

    public static HorlogeDuCentre enUtc(Clock horloge) {
        return new HorlogeDuCentre(horloge, ZoneOffset.UTC);
    }

    public LocalDate aujourdhui(CentreId centre) {
        return jourDuCentre.apply(centre.valeur());
    }

    public Instant maintenant() {
        return Instant.now(horloge);
    }
}
