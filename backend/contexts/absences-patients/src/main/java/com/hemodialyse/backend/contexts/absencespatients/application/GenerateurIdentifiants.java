package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;

/**
 * Port sortant : identité d'une nouvelle absence (le domaine ne tire pas d'identifiant, ADR 0028).
 */
public interface GenerateurIdentifiants {

    AbsenceId nouvelleAbsence();
}
