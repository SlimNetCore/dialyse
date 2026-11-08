package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Set;

/**
 * Port sortant (lecture) : les jours non ouvrés du centre dans le mois (jours fériés et fermetures exceptionnelles de son calendrier),
 * dédoublonnés. Aucune séance n'est prévue ces jours-là.
 */
public interface JoursBloques {

    Set<LocalDate> du(CentreId centre, MoisDuTableauDeBord mois);
}
