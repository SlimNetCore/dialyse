package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceSuivie;
import com.hemodialyse.backend.contexts.absencespatients.domain.SeanceRealisee;

import java.util.List;

/**
 * Absences non annulées et séances réalisées de la semaine : ce qui colore les séances du planning.
 */
public record SuiviDeLaSemaine(List<AbsenceSuivie> absences, List<SeanceRealisee> seancesRealisees) {
}
