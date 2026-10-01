package com.hemodialyse.backend.infrastructure.web.dto.response.gmao;

import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;

import java.util.UUID;

/**
 * Vérification de disponibilité d'un générateur avant affectation d'un patient — avertissement
 * non bloquant (le rapprochement reste à la décision du personnel, cf. module GMAO v2).
 */
public record DisponibilitePatientResponse(
        boolean disponible,
        StatutEquipement statut,
        UUID interventionEnCoursId
) {
}
