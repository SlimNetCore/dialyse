package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Langage publié du Stock : valorisation historique d'articles d'un centre sur une période (lue par la direction et le
 * tableau de bord). Aucun cache ici : l'appelant met le résultat en cache et le vide sur {@link MouvementsDeStockModifies}.
 */
public interface ValorisationsDuStock {

    /**
     * @return la valorisation de chaque article demandé (zéro pour un article sans mouvement)
     */
    Map<UUID, ValorisationDuStock> valoriser(UUID centre, Set<UUID> articles, OffsetDateTime debut, OffsetDateTime finExclue);
}
