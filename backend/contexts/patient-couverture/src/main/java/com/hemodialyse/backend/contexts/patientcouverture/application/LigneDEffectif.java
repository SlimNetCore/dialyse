package com.hemodialyse.backend.contexts.patientcouverture.application;

import java.time.LocalDate;
import java.util.UUID;

/** Ligne brute d'un patient du centre servant aux synthèses mensuelles (l'état est le texte de la colonne, peut être nul). */
public record LigneDEffectif(UUID id, String codePatient, String nom, String prenom, String sexe, LocalDate dateNaissance,
                             Boolean sousKt, String etatPatient, LocalDate dateEvenementEtat, LocalDate dateAdmission) {
}
