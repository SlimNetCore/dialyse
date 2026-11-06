package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.List;

/** Une page de l'historique, du plus récent au plus ancien ; {@code total} compte tous les points de l'historique. */
public record PageDeConstantes(List<ConstantesDeSeance> items, long total, int page, int size) {
    public PageDeConstantes {
        items = List.copyOf(items);
    }
}
