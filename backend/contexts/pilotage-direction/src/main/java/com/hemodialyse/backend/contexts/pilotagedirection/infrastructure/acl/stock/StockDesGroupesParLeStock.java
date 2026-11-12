package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.pilotagedirection.application.StockDesGroupes;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.GroupesDArticlesDesCentres;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ValorisationDuStock;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ValorisationsDuStock;
import org.springframework.cache.annotation.Cacheable;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ACL : les groupes d'articles ({@link GroupesDArticlesDesCentres}, S2) et leur valorisation ({@link ValorisationsDuStock}) lus dans
 * {@code stock.v1}. Le rejeu de l'historique est coûteux : la valorisation est mise en cache (cache {@value #CACHE}, TTL
 * {@code app.cache.ttl.stock-valorisation}), la clé incluant toujours le centre (AGENTS.md §2 et §6) ; le cache est vidé à chaque
 * écriture de mouvement de stock (événement publié {@code MouvementsDeStockModifies}, relayé par la plateforme), le TTL ne servant que de
 * filet. Classe non finale : le cache passe par le proxy Spring (anciennement {@code ValorisationArticlesService} de la plateforme).
 */
public class StockDesGroupesParLeStock implements StockDesGroupes {

    /** Nom du cache de la valorisation, vidé par la plateforme à chaque mouvement de stock. */
    public static final String CACHE = "stock.valorisation.articles";

    private final GroupesDArticlesDesCentres groupes;
    private final ValorisationsDuStock valorisations;

    public StockDesGroupesParLeStock(GroupesDArticlesDesCentres groupes, ValorisationsDuStock valorisations) {
        this.groupes = groupes;
        this.valorisations = valorisations;
    }

    @Override
    public List<GroupeDArticles> groupes(Collection<UUID> centres) {
        return groupes.groupes(centres).stream()
                .map(g -> new GroupeDArticles(g.id(), g.centre(), g.nom(), g.cle(), g.articles()))
                .toList();
    }

    @Override
    @Cacheable(cacheNames = CACHE,
            key = "#centre + ':' + #debut.toInstant() + ':' + #finExclue.toInstant() + ':' + #articles.hashCode()")
    public Map<UUID, Valorisation> valoriser(UUID centre, Set<UUID> articles, OffsetDateTime debut, OffsetDateTime finExclue) {
        Map<UUID, Valorisation> traduites = new HashMap<>();
        valorisations.valoriser(centre, articles, debut, finExclue).forEach((article, v) -> traduites.put(article, traduire(v)));
        return traduites;
    }

    static Valorisation traduire(ValorisationDuStock v) {
        return new Valorisation(v.valeurDebut(), v.entrees(), v.sorties(), v.autresVariations(), v.valeurFin());
    }
}
