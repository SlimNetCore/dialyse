package com.hemodialyse.backend.contexts.gmao.application.intervention;

import java.util.List;
import java.util.function.Function;

/** Page d'interventions (même forme que le contrat observé : items, total, page, size). */
public record PageDInterventions<T>(List<T> items, long total, int page, int size) {

    <R> PageDInterventions<R> convertir(Function<T, R> conversion) {
        return new PageDInterventions<>(items.stream().map(conversion).toList(), total, page, size);
    }
}
