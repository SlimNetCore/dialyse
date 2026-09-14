package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.greffe.entity.DecisionRcp;

import java.time.LocalDate;
import java.util.UUID;

public record DecisionRcpResponse(
        UUID id,
        LocalDate dateReunion,
        String avis,
        String compteRendu,
        LocalDate prochaineDateRevue
) {

    public static DecisionRcpResponse from(DecisionRcp d) {
        return new DecisionRcpResponse(d.getId(), d.getDateReunion(), d.getAvis().name(), d.getCompteRendu(),
                d.getProchaineDateRevue());
    }
}
