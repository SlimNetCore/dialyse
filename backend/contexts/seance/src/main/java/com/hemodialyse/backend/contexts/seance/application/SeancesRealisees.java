package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant (lecture) : les séances RÉALISÉES d'un centre dans un mois, c'est-à-dire validées, signées ou facturées. Les séances
 * créées, en brouillon ou absentes ne sont pas des présences. Bornes du mois incluses.
 */
public interface SeancesRealisees {

    /** Nombre de séances réalisées du mois. */
    long nombre(CentreId centre, MoisDuTableauDeBord mois);

    /** Les séances réalisées du mois (sans ordre garanti). */
    List<SeanceRealisee> du(CentreId centre, MoisDuTableauDeBord mois);

    record SeanceRealisee(PatientId patient, LocalDate jour, String statut) {
    }
}
