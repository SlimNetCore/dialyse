package com.hemodialyse.backend.infrastructure.web.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Vue agrégée du suivi de l'anémie : dernières valeurs biologiques, évaluation KDIGO, courbe
 * récente, dernière prescription EPO/fer et historique des administrations réelles.
 */
public record SuiviAnemieResponse(
        LocalDate dateDernierBilan,
        List<EvaluationCibleResponse> evaluations,
        List<PointBiologiqueResponse> courbe,
        PrescriptionMedicaleResponse prescriptionActive,
        List<AdministrationTraitementResponse> administrationsRecentes
) {
}
