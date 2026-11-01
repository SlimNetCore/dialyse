package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.dto;

import java.time.LocalDate;

public record ModifierRequest(String nom, String prenom, String sexe, LocalDate dateNaissance, String telPersonnel,
                              String telMobile, String telBureau, String adresse, String groupeSanguin,
                              String typeAssure) {
}
