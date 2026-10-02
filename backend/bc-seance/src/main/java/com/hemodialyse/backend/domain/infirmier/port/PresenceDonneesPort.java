package com.hemodialyse.backend.domain.infirmier.port;

import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port de lecture des données du planning de présence d'un centre sur une période (toujours borné au centre).
 */
public interface PresenceDonneesPort {

    /**
     * @param du premier jour de la période (inclus)
     * @param au dernier jour de la période (inclus) : absences, remplacements et fermetures sont limités à la période
     */
    DonneesPresence charger(UUID centerId, LocalDate du, LocalDate au);
}
