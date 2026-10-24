package com.hemodialyse.backend.application.stock;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.ValorisationDuStock;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ValorisationsDuStock;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Valorisation historique du stock d'un ensemble d'articles d'un centre sur une période, lue dans le langage publié du contexte
 * Stock ({@link ValorisationsDuStock}). Le rejeu de l'historique est coûteux : le résultat est mis en cache (cache
 * {@code stock.valorisation.articles}, TTL {@code app.cache.ttl.stock-valorisation}). La clé inclut toujours le centre
 * (AGENTS.md §2 et §6) ; le cache est vidé à chaque écriture de mouvement de stock (évènement publié
 * {@code MouvementsDeStockModifies}, relayé par {@code StockEvenementsListener}), le TTL ne servant que de filet de sécurité.
 * Bean distinct de ses appelants : le cache passe par le proxy Spring.
 */
@Service
public class ValorisationArticlesService {

    public static final String CACHE = "stock.valorisation.articles";

    private final ValorisationsDuStock valorisations;

    public ValorisationArticlesService(ValorisationsDuStock valorisations) {
        this.valorisations = valorisations;
    }

    /**
     * @return la valorisation de chaque article demandé (zéro pour un article sans mouvement)
     */
    @Cacheable(cacheNames = CACHE,
            key = "#centerId + ':' + #debut.toInstant() + ':' + #finExclue.toInstant() + ':' + #articleIds.hashCode()")
    public Map<UUID, ValorisationDuStock> valoriser(UUID centerId, Set<UUID> articleIds, OffsetDateTime debut,
                                                    OffsetDateTime finExclue) {
        return valorisations.valoriser(centerId, articleIds, debut, finExclue);
    }
}
