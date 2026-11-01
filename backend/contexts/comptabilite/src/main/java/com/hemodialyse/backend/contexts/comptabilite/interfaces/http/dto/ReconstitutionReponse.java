package com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hemodialyse.backend.contexts.comptabilite.application.vente.ComptabiliserLesVentesEtReglements.Echec;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code echecs} n'apparaît que s'il y en a : la réponse sans refus est celle du legacy.
 */
public record ReconstitutionReponse(int facturesProcessed, int reglementsProcessed, LocalDate from, LocalDate to,
                                    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Echec> echecs) {
}
