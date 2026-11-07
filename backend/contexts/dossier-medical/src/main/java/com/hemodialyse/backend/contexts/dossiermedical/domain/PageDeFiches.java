package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.List;

/** Une page de fiches d'un patient (antécédents, allergies, abords vasculaires). */
public record PageDeFiches<T>(List<T> items, long total, int page, int taille) {
}
