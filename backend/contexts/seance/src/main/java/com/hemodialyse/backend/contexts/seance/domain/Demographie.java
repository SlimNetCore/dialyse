package com.hemodialyse.backend.contexts.seance.domain;

import java.time.LocalDate;

/** Ce que les répartitions du tableau de bord lisent d'un patient : son sexe (brut) et sa date de naissance. */
public record Demographie(PatientId patient, String sexe, LocalDate naissance) {
}
