package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.List;

/** Une page d'ordonnances d'un patient. */
public record PageDOrdonnances(List<Ordonnance> items, long total, int page, int taille) {
}
