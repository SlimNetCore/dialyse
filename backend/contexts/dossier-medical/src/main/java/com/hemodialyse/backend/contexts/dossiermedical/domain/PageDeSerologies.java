package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.List;

/** Une page de sérologies d'un patient. */
public record PageDeSerologies(List<Serologie> items, long total, int page, int taille) {
}
