package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.SeanceRealisee;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant vers Séance : les séances réalisées (validées, signées ou facturées) du centre sur une période, avec la place figée à
 * leur validation (RG-SEA-049) et le nom du patient. Triées par date. Borné au centre ; lecture seule.
 */
public interface SeancesRealisees {

    List<SeanceRealisee> duCentre(CentreId centre, LocalDate du, LocalDate au);
}
