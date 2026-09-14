package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateDonneurVivantRequest(
        UUID centerId,
        String nom,
        String prenom,
        LocalDate dateNaissance,
        String lienParente,
        String telephone,
        String groupeSanguin,
        String typageHla,
        String statutBilan,
        String crossmatchResultat,
        LocalDate dateCrossmatch,
        String bilanRealise,
        String contreIndications,
        String decisionFinale,
        LocalDate dateDecision
) {
}
