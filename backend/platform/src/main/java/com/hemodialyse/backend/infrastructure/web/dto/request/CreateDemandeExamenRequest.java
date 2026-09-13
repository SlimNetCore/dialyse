package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateDemandeExamenRequest(
        UUID centerId,
        String prescripteurId,
        LocalDate dateDemande,
        String categorie,
        boolean urgent,
        String motif,
        List<LigneDemandeExamenRequest> lignes
) {
}
