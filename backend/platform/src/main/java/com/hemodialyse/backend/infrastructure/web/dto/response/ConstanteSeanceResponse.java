package com.hemodialyse.backend.infrastructure.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un point de la vue longitudinale des constantes du patient — agrège, pour une séance donnée,
 * les mesures déjà saisies par l'infirmier dans le volet paramédical. Lecture seule : aucune
 * saisie n'est dupliquée ici (source unique de vérité).
 */
public record ConstanteSeanceResponse(
        UUID seanceId,
        LocalDate dateSeance,
        BigDecimal poidsAvantKg,
        BigDecimal poidsApresKg,
        String taAvant,
        String taApres,
        Integer debitSangMlMin,
        BigDecimal ultrafiltrationMl,
        Integer dureeMinutes
) {
}
