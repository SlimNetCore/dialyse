package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.ordonnance.entity.LigneOrdonnance;

import java.util.UUID;

public record LigneOrdonnanceResponse(
        UUID id,
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

    public static LigneOrdonnanceResponse from(LigneOrdonnance l) {
        var medicament = l.getMedicament();
        return new LigneOrdonnanceResponse(
                l.getId(),
                medicament == null ? null : medicament.system().name(),
                medicament == null ? null : medicament.code(),
                medicament == null ? null : medicament.display(),
                l.getLibelle(),
                l.getPosologie(),
                l.getVoie(),
                l.getDureeJours(),
                l.getQuantite(),
                l.getInstructions());
    }
}
