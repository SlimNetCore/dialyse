package com.hemodialyse.backend.application.stock;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.port.StockMovementHistoryPort;
import com.hemodialyse.backend.domain.stock.service.ValorisationStockCalculator;
import com.hemodialyse.backend.domain.stock.service.ValorisationStockCalculator.Valorisation;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Valorisation historique du stock d'un ensemble d'articles d'un centre sur une période. Le rejeu de
 * l'historique des mouvements est coûteux : le résultat est mis en cache (cache {@code stock.valorisation.articles},
 * TTL {@code app.cache.ttl.stock-valorisation}). La clé inclut toujours le centre (AGENTS.md §2 et §6) ; le cache
 * est vidé à chaque écriture de mouvement de stock ({@code StockMovementRepositoryAdapter}), le TTL ne servant que
 * de filet de sécurité. Bean distinct de ses appelants : le cache passe par le proxy Spring.
 */
@Service
public class ValorisationArticlesService {

    public static final String CACHE = "stock.valorisation.articles";

    private final StockMovementHistoryPort historique;

    public ValorisationArticlesService(StockMovementHistoryPort historique) {
        this.historique = historique;
    }

    /**
     * @return la valorisation de chaque article demandé (zéro pour un article sans mouvement)
     */
    @Cacheable(cacheNames = CACHE,
            key = "#centerId + ':' + #debut.toInstant() + ':' + #finExclue.toInstant() + ':' + #articleIds.hashCode()")
    public Map<UUID, Valorisation> valoriser(
            UUID centerId, Set<UUID> articleIds, OffsetDateTime debut, OffsetDateTime finExclue) {
        Map<UUID, List<StockMovement>> mouvements = historique.mouvementsParArticle(CenterId.of(centerId), articleIds);
        Map<UUID, Valorisation> valeurs = new HashMap<>();
        for (UUID articleId : articleIds) {
            valeurs.put(articleId, ValorisationStockCalculator.calculer(
                    mouvements.getOrDefault(articleId, List.of()), debut, finExclue));
        }
        return Map.copyOf(valeurs);
    }
}
