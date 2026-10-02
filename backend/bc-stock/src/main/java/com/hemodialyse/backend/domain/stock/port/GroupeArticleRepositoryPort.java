package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.stock.model.GroupeArticle;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des groupes d'articles (toujours bornés à un centre — AGENTS.md §2).
 */
public interface GroupeArticleRepositoryPort {

    GroupeArticle save(GroupeArticle groupe);

    Optional<GroupeArticle> findById(UUID centerId, UUID id);

    /**
     * Page de groupes du centre, triés par nom (pagination obligatoire — AGENTS.md §9).
     */
    PagedResult<GroupeArticle> findPaged(UUID centerId, int page, int size);

    /**
     * Un autre groupe du centre porte-t-il déjà ce nom (clé insensible à la casse / aux accents) ?
     */
    boolean existsByCle(UUID centerId, String cle, UUID excludeId);

    void delete(UUID centerId, UUID id);

    /**
     * Tous les groupes des centres donnés (agrégat de la direction : borné par le nombre de centres de la
     * société, jamais par un identifiant fourni par le client).
     */
    List<GroupeArticle> findByCenters(Collection<UUID> centerIds);
}
