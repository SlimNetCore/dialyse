package com.hemodialyse.backend.infrastructure.web.dto.request;

public record UpdateAssureRequest(
        String nom, String prenom, String sexe, String dateNaissance,
        String telPersonnel, String telMobile, String telBureau,
        String adresse, String groupeSanguin) {
}

