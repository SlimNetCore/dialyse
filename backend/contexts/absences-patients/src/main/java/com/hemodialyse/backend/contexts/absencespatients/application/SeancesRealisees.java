package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.SeanceRealisee;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant : séances réalisées (validées, signées ou facturées), faits lus chez Séance. Une séance seulement créée ou
 * « absente » (exclue de facturation) n'est pas réalisée.
 */
public interface SeancesRealisees {

    /**
     * Une ligne par patient et par jour entre ces deux dates incluses, du plus ancien au plus récent.
     */
    List<SeanceRealisee> duCentre(CentreId centre, LocalDate du, LocalDate au);

    /**
     * Jours (du plus ancien au plus récent, sans doublon) où ce patient a une séance réalisée entre ces deux dates incluses.
     */
    List<LocalDate> joursDuPatient(CentreId centre, PatientId patient, LocalDate du, LocalDate au);
}
