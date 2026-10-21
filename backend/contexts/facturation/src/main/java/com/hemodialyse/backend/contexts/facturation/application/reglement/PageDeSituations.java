package com.hemodialyse.backend.contexts.facturation.application.reglement;

import java.util.List;

/**
 * Une page de factures avec leur situation ; mêmes noms de champs JSON que le legacy ({@code items, total, page, size}).
 */
public record PageDeSituations(List<LigneDeSituation> items, long total, int page, int size) {
}
