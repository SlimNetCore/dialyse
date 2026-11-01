package com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hemodialyse.backend.contexts.comptabilite.application.stock.ComptabiliserLeStock.Echec;

import java.util.List;

/**
 * {@code echecs} n'apparaît que s'il y en a : sans échec, la réponse est celle du legacy.
 */
public record SynchronisationReponse(int receptions, int joursSorties, int inventaires, int complements,
                                     int ignorees,
                                     @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Echec> echecs) {
}
