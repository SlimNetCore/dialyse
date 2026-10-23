package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.util.List;
import java.util.UUID;

/**
 * Événement publié par le Stock : les articles d'une tâche de recalcul du PMP ont changé d'état.
 *
 * @param etat {@code STARTED}, {@code COMPLETED} ou {@code FAILED}
 */
public record VerrousDeRecalculModifies(UUID centre, List<UUID> articles, String etat) {
}

