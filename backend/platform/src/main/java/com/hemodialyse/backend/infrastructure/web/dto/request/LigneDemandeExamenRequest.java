package com.hemodialyse.backend.infrastructure.web.dto.request;

public record LigneDemandeExamenRequest(
        String codeSystem,
        String code,
        String codeDisplay,
        String libelle,
        String commentaire
) {
}
