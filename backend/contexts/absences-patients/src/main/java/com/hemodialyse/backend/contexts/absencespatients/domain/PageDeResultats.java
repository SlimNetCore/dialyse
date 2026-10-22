package com.hemodialyse.backend.contexts.absencespatients.domain;

import java.util.List;

/**
 * Une page de résultats et le total sans pagination ; {@code taille} est la taille effective (après plafonnement).
 */
public record PageDeResultats<T>(List<T> elements, long total, int page, int taille) {
}
