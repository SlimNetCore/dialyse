package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.examen.entity.LigneDemandeExamen;

import java.util.UUID;

public record LigneDemandeExamenResponse(
        UUID id,
        String codeSystem,
        String code,
        String codeDisplay,
        String libelle,
        String commentaire
) {

    public static LigneDemandeExamenResponse from(LigneDemandeExamen l) {
        var analyte = l.getAnalyte();
        return new LigneDemandeExamenResponse(
                l.getId(),
                analyte == null ? null : analyte.system().name(),
                analyte == null ? null : analyte.code(),
                analyte == null ? null : analyte.display(),
                l.getLibelle(),
                l.getCommentaire());
    }
}
