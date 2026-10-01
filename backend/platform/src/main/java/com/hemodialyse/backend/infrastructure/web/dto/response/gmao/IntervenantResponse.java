package com.hemodialyse.backend.infrastructure.web.dto.response.gmao;

import com.hemodialyse.backend.domain.gmao.model.Intervenant;
import com.hemodialyse.backend.domain.gmao.model.TypeIntervenant;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO de réponse pour un intervenant GMAO.
 */
public record IntervenantResponse(
        UUID id,
        UUID centreId,
        String nom,
        TypeIntervenant type,
        String telephone,
        String email,
        BigDecimal tarifHoraireDefaut,
        boolean actif
) {
    public IntervenantResponse(Intervenant intervenant) {
        this(intervenant.id(), intervenant.centreId(), intervenant.nom(), intervenant.type(),
                intervenant.telephone(), intervenant.email(), intervenant.tarifHoraireDefaut(), intervenant.actif());
    }
}
