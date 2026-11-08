package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Corps de {@code POST /api/v1/seances/calendar/holiday} et {@code /calendar/closure} : mêmes champs JSON que le legacy ({@code centerId},
 * {@code dayDate}, {@code labelOrReason}). Aucune contrainte de bean : un champ absent ou illisible est un 400 {@code BAD_REQUEST}, rendu
 * APRÈS le contrôle des droits (403 avant 400, KB-SEA-57 et 58 corrigés : le legacy répondait 500). Les champs inconnus sont ignorés.
 */
public record JourDuCalendrierDemande(UUID centerId, LocalDate dayDate, String labelOrReason) {
}
