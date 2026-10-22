package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;

/**
 * Port sortant : l'origine d'une absence lue chez la Facturation (séance exclue de la facturation, ADR 0031).
 */
public interface ExclusionsDeSeances {

    /**
     * Vrai si cette absence a été déclarée par l'exclusion d'une séance qui y est encore liée.
     */
    boolean absenceIssueDUneExclusion(CentreId centre, AbsenceId absence);
}
