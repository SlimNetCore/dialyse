package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;

/** Lecture d'un patient (DTO de lecture : l'interface la traduit en JSON, jamais l'agrégat). */
public record FicheDuPatient(PatientId id, CentreId centre, String code, boolean vacancier, Instant creeLe, Fiche fiche) {
}
