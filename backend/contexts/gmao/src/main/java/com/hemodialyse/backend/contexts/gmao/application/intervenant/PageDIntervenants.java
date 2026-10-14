package com.hemodialyse.backend.contexts.gmao.application.intervenant;

import java.util.List;

/** Page d'intervenants d'un centre (même forme que le contrat observé : items, total, page, size). */
public record PageDIntervenants(List<IntervenantVue> items, long total, int page, int size) {
}
