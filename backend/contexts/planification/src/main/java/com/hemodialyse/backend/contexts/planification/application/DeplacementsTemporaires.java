package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.DeplacementTemporaire;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant (lecture) : les déplacements temporaires du centre sur une période. Les écrire et les refuser quand leur cible est
 * incohérente est l'affaire de P8 ; ici ils sont lus tels qu'enregistrés (KB-PLN-73). Borné au centre.
 */
public interface DeplacementsTemporaires {

    List<DeplacementTemporaire> duCentre(CentreId centre, LocalDate du, LocalDate au);
}
