package com.hemodialyse.backend.contexts.gmao.application.equipement;

import java.util.List;

/** Page d'équipements d'un centre (même forme que le contrat observé : items, total, page, size). */
public record PageDEquipements(List<EquipementVue> items, long total, int page, int size) {
}
