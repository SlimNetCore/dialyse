package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AdmettreRequest(UUID centreId, String nom, String prenom, String sexe, LocalDate dateNaissance,
                              String numeroAssurance, LocalDate dateAdmission, UUID auteurId) {
}
