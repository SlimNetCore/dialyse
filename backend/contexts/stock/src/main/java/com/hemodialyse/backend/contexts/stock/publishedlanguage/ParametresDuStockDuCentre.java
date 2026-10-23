package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié du Stock : le paramétrage d'un centre utile aux autres contextes (bascule de la sortie pour séances, ADR 0045).
 */
public interface ParametresDuStockDuCentre {
    /**
     * Vrai si la séance du centre consomme sur une sortie pour séances (désactivé par défaut : sortie immédiate FEFO).
     */
    boolean sortiePourSeancesActivee(UUID centre);
}