package com.hemodialyse.backend.contexts.stock.domain.lectures;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.UUID;

/**
 * Mouvement de stock d'un article tel que le lit le rejeu du PMP (table {@code stock_movements}) : entrée, sortie,
 * ajustement (valorisé s'il porte un prix) ou inventaire.
 */
public record MouvementDeStock(UUID id, UUID articleId, UUID lotId, UUID seanceId, Type type, BigDecimal quantite,
                               BigDecimal prixUnitaire, OffsetDateTime creeLe, OffsetDateTime saisiLe) {

    /**
     * Ordre du rejeu : date, heure de saisie, puis à date égale les entrées avant les sorties (KB-STK-01).
     */
    public static final Comparator<MouvementDeStock> CHRONOLOGIE = Comparator
            .comparing(MouvementDeStock::creeLe, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(MouvementDeStock::saisiLe, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparingInt(MouvementDeStock::rangDansLaJournee);

    private int rangDansLaJournee() {
        return estUneEntree() ? 0 : 1;
    }

    /**
     * Une entrée ajoute de la valeur : entrée, inventaire, ajustement avec prix.
     */
    public boolean estUneEntree() {
        return type == Type.ENTREE || type == Type.RETOUR || type == Type.INVENTAIRE || (type == Type.AJUSTEMENT && prixUnitaire != null);
    }

    public enum Type {
        ENTREE, SORTIE, AJUSTEMENT, INVENTAIRE, RETOUR
    }
}

