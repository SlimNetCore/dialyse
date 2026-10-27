package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port consommé par la Planification (ADR 0053, slice P4), fourni par le legacy (centre de notifications : journal durable et diffusion
 * temps réel). Chaque alerte a une clé déterministe (type, centre, jour du centre) : déjà émise ce jour-là, elle n'est ni renvoyée ni
 * dupliquée, même si deux instances du serveur contrôlent ensemble (KB-PLN-46).
 */
public interface NotificationsDeLaPresence {

    /**
     * Des cases sont en sous-effectif dans les jours à venir (administration, secrétariat, médecin).
     *
     * @return vrai si l'alerte du jour a été émise par cet appel, faux si elle l'était déjà
     */
    boolean sousEffectif(UUID centre, LocalDate jour, int nbCases, LocalDate premiereDate);

    /**
     * Des infirmiers sont prévus au-delà de l'effectif requis (administrateur) : cases, vacations en trop, heures correspondantes.
     *
     * @return vrai si l'alerte du jour a été émise par cet appel, faux si elle l'était déjà
     */
    boolean sureffectif(UUID centre, LocalDate jour, int nbCases, int nbVacations, int heures, LocalDate premiereDate);
}
