package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Résultat d'une admission ou d'une modification : ce que l'interface rend ({@code id}, {@code centerId}, {@code typePatient}, {@code codePatient}). */
public record PatientEnregistre(PatientId id, CentreId centre, boolean vacancier, String code) {
}
