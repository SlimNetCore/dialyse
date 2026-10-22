package com.hemodialyse.backend.contexts.absencespatients.domain;

import java.time.LocalDate;

/**
 * Ce que l'agrégat ne peut pas savoir seul pour accepter un rattrapage (RG-ABS-021) : lu à la demande, dans l'ordre des
 * contrôles.
 */
public interface FaitsDuRattrapage {

    /** Le patient a une séance réalisée ce jour-là. */
    boolean seanceRealisee(LocalDate jour);

    /** Cette séance rattrape déjà une autre absence rattrapée du patient. */
    boolean dejaUtilisee(LocalDate jour);
}
