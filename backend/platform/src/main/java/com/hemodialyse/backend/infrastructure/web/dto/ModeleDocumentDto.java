package com.hemodialyse.backend.infrastructure.web.dto;

import java.util.UUID;

public record ModeleDocumentDto(
        UUID id, UUID centerId, String code, String libelle,
        String typeDocument, String cheminJrxml, String formatImpression,
        String description, boolean active) {
}

