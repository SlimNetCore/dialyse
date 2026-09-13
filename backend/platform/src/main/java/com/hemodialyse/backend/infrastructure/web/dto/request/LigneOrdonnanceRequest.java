package com.hemodialyse.backend.infrastructure.web.dto.request;

public record LigneOrdonnanceRequest(
        String codeSystem,
        String code,
        String codeDisplay,
        String libelle,
        String posologie,
        String voie,
        Integer dureeJours,
        Integer quantite,
        String instructions
) {
}
