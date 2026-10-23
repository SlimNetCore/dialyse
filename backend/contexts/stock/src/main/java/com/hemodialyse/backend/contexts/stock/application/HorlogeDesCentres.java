package com.hemodialyse.backend.contexts.stock.application;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Port sortant : l'heure du Stock. Les instants (horodatages) sont en UTC pour tout le serveur ; le <b>jour métier</b> d'un centre
 * (réception sans date, sortie du jour, fenêtre de tendance, seuil de péremption) est le jour de son fuseau horaire, pas le jour UTC.
 */
public interface HorlogeDesCentres {

    /**
     * Horloge à fuseau unique (tests, centres sans fuseau propre) : le jour est celui de l'horloge donnée.
     */
    static HorlogeDesCentres uniforme(Clock horloge) {
        return new HorlogeDesCentres() {
            @Override
            public OffsetDateTime maintenant() {
                return OffsetDateTime.now(horloge);
            }

            @Override
            public LocalDate aujourdHui(CentreId centre) {
                return LocalDate.now(horloge);
            }
        };
    }

    /**
     * L'instant présent, en UTC.
     */
    OffsetDateTime maintenant();

    /**
     * Le jour d'aujourd'hui dans le fuseau du centre.
     */
    LocalDate aujourdHui(CentreId centre);
}