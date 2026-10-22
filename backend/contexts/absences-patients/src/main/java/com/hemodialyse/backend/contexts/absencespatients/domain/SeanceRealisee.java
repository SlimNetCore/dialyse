package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;

import java.time.LocalDate;

/**
 * Séance réalisée d'un patient un jour donné (fait lu chez Séance : validée, signée ou facturée). Un patient, un jour.
 */
public record SeanceRealisee(PatientId patient, LocalDate date) {
}
