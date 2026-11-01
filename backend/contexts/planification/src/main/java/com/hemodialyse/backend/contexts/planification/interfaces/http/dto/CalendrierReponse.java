package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.CaseDuCalendrier;

import java.time.LocalDate;
import java.util.List;

/**
 * @param semaines semaines (dates de début) disponibles ; vide si la proposition n'a pas de calendrier
 * @param semaine  semaine des {@code cases}
 */
public record CalendrierReponse(List<LocalDate> semaines, LocalDate semaine, List<CaseDuCalendrier> cases) {
}
