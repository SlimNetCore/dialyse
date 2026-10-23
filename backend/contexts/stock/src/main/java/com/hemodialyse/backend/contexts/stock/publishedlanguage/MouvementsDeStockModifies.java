package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.util.UUID;

/**
 * Événement publié par le Stock : des mouvements de stock ont changé pour un centre (réception validée, sortie). La plateforme
 * le relaie au temps réel du centre et vide le cache de valorisation.
 *
 * @param mouvement {@code ENTREE} ou {@code SORTIE}
 */
public record MouvementsDeStockModifies(UUID centre, String mouvement, String reference, int nombreArticles) {
}

