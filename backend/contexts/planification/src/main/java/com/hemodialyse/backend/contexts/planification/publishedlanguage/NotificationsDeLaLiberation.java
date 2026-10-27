package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port consommé par la Planification (slice P7), fourni par le legacy (centre de notifications : journal durable et diffusion temps
 * réel). Clé déterministe (type, centre, jour du centre) : une seule alerte par centre et par jour, même si la nuit est rejouée ou si
 * deux instances du serveur travaillent ensemble.
 */
public interface NotificationsDeLaLiberation {

    /**
     * Des patients du centre n'ont pas pu être libérés cette nuit (administrateur) ; les autres l'ont été, la prochaine nuit réessaie.
     *
     * @return vrai si l'alerte du jour a été émise par cet appel, faux si elle l'était déjà
     */
    boolean liberationEnEchec(UUID centre, LocalDate jour, int nbPatients);
}
