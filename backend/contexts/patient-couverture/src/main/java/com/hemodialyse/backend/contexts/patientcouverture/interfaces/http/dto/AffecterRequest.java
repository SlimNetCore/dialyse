package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AffecterRequest(UUID patientId, UUID centreId, String numeroAssurance, String nom, String prenom,
                              String sexe, LocalDate dateNaissance, String telPersonnel, String telMobile,
                              String telBureau, String adresse, String groupeSanguin, String typeAssure,
                              UUID auteurId) {
}
